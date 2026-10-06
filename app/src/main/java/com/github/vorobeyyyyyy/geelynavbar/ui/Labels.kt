package com.github.vorobeyyyyyy.geelynavbar.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.github.vorobeyyyyyy.geelynavbar.IconLoader
import com.github.vorobeyyyyyy.geelynavbar.IconRef
import com.github.vorobeyyyyyy.geelynavbar.NavAction
import com.github.vorobeyyyyyy.geelynavbar.Protocol
import java.util.concurrent.ConcurrentHashMap

internal data class AppEntry(val label: String, val pkg: String, val cls: String)

/** Короткие подписи — для плиток и карточек. */
internal class Labels(private val pm: PackageManager) {
    private val apps = ConcurrentHashMap<Pair<String, String?>, String>()

    fun action(a: NavAction): String = when (a) {
        NavAction.None -> "Ничего"
        NavAction.Back -> "Назад"
        NavAction.Home -> "Домой"
        NavAction.AppPane -> "Приложения"
        NavAction.CarSettings -> "Авто"
        NavAction.Climate -> "Климат"
        NavAction.Recents -> "Недавние"
        is NavAction.Split -> "Сплит"
        is NavAction.LaunchApp -> app(a.pkg, a.cls)
    }

    fun app(pkg: String, cls: String?): String = apps.getOrPut(pkg to cls) {
        try {
            if (cls != null) {
                pm.getActivityInfo(ComponentName(pkg, cls), 0).loadLabel(pm).toString()
            } else {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }
        } catch (_: PackageManager.NameNotFoundException) {
            "$pkg ✕"
        }
    }

    fun launchableApps(): List<AppEntry> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.activityInfo.name) }
            .filter { it.pkg != Protocol.MODULE_PKG }
            .sortedBy { it.label.lowercase() }
    }
}

/**
 * Картинки превью — те же, что рисует хук ([IconLoader]): на ГУ штатные PNG плагина с нужным день/ночь,
 * на эмуляторе плагина нет — свои векторы. Кеш общий; иконки приложений грузятся и из фоновых потоков.
 */
internal class Images(private val context: Context) {
    class Picture(val bitmap: ImageBitmap, val isApp: Boolean)

    private val loaders = mapOf(false to loader(false), true to loader(true))
    private val cache = ConcurrentHashMap<Pair<IconRef, Boolean>, Picture>()

    fun get(ref: IconRef, night: Boolean): Picture? = cache[ref to night] ?: load(ref, night)?.also { cache[ref to night] = it }

    private fun load(ref: IconRef, night: Boolean): Picture? {
        val loaded = loaders.getValue(night).load(ref, night) ?: return null
        val d = loaded.drawable
        // Векторы — вдвое крупнее своего размера, адаптивные иконки приложений своего размера не имеют: в превью они крупные
        val (w, h) = if (loaded.isApp) 144 to 144 else d.intrinsicWidth.coerceAtLeast(1) * 2 to d.intrinsicHeight.coerceAtLeast(1) * 2
        return Picture(d.toBitmap(w, h).asImageBitmap(), loaded.isApp)
    }

    private fun loader(night: Boolean): IconLoader {
        val plugin: Resources? = try {
            val c = context.createPackageContext(Protocol.PLUGIN_PKG, 0)
            val cfg = Configuration(c.resources.configuration)
            cfg.uiMode = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            c.createConfigurationContext(cfg).resources
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        return IconLoader(plugin, context.resources, context.packageManager)
    }
}
