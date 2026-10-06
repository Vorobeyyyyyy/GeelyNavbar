package com.github.vorobeyyyyyy.geelynavbar

import android.content.ComponentName
import android.content.res.Resources
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

/** Откуда взять картинку кнопки. */
sealed interface IconRef {
    /** PNG штатного плагина (день/ночь — его ресурсами); [fallback] — своя иконка, если плагина нет (эмулятор). */
    data class Stock(val name: String, val fallback: String) : IconRef

    /** Свой вектор `res/drawable/nb_*`, тонируется под день/ночь. */
    data class Own(val name: String) : IconRef

    data class App(val pkg: String, val cls: String?) : IconRef
}

object Icons {
    data class Choice(val name: String, val title: String)

    /** Штатные PNG плагина → своя замена с тем же смыслом. */
    private val stock = mapOf(
        "ic_nav_home" to "nb_home",
        "ic_nav_apppane" to "nb_apps",
        "ic_nav_car" to "nb_car",
        "ic_nav_hvac" to "nb_climate",
        "ic_nav_scene" to "nb_recents",
    )

    val choices = listOf(
        Choice("ic_nav_home", "Домой (штатная)"),
        Choice("ic_nav_apppane", "Приложения (штатная)"),
        Choice("ic_nav_car", "Авто (штатная)"),
        Choice("ic_nav_hvac", "Климат 20° (штатная)"),
        Choice("ic_nav_scene", "Сцены (штатная)"),
        Choice("nb_back_mod", "Назад (как в моде)"),
        Choice("nb_map", "Карта (как в моде)"),
        Choice("nb_back", "Назад (стрелка)"),
        Choice("nb_home", "Домой"),
        Choice("nb_apps", "Приложения"),
        Choice("nb_car", "Авто"),
        Choice("nb_climate", "Климат"),
        Choice("nb_recents", "Недавние"),
        Choice("nb_split", "Сплит"),
        Choice("nb_dot", "Точка"),
    )

    /** Цвет своих иконок — как у штатных PNG. */
    const val DAY_TINT = 0xFF212736.toInt()
    const val NIGHT_TINT = 0xFFDFE5F4.toInt()

    fun ref(b: PanelItem.Button): IconRef = when (val i = b.icon) {
        is NavIcon.Named -> named(i.name)
        is NavIcon.App -> IconRef.App(i.pkg, i.cls)
        NavIcon.Auto -> auto(if (b.tap != NavAction.None) b.tap else b.long)
    }

    private fun auto(a: NavAction): IconRef = when (a) {
        NavAction.Home -> named("ic_nav_home")
        NavAction.AppPane -> named("ic_nav_apppane")
        NavAction.CarSettings -> named("ic_nav_car")
        // Штатная ic_nav_hvac — это нарисованные «20.0°», для кнопки «открыть климат» вводит в заблуждение
        NavAction.Climate -> IconRef.Own("nb_climate")
        NavAction.Back -> IconRef.Own("nb_back_mod")
        NavAction.Recents -> IconRef.Own("nb_recents")
        is NavAction.Split -> IconRef.Own("nb_split")
        is NavAction.LaunchApp -> IconRef.App(a.pkg, a.cls)
        NavAction.None -> IconRef.Own("nb_dot")
    }

    private fun named(name: String): IconRef = stock[name]?.let { IconRef.Stock(name, it) } ?: IconRef.Own(name)
}

/**
 * Загрузка картинок и в хуке (ресурсы плагина — штатный контекст панели), и в приложении (превью).
 * [plugin] = null — плагина нет (эмулятор), штатные PNG заменяются своими векторами.
 */
class IconLoader(private val plugin: Resources?, private val module: Resources, private val pm: PackageManager) {
    class Loaded(val drawable: Drawable, val isApp: Boolean)

    fun load(ref: IconRef, night: Boolean): Loaded? = when (ref) {
        is IconRef.Stock -> pluginDrawable(ref.name)?.let { Loaded(it, false) } ?: own(ref.fallback, night)
        is IconRef.Own -> own(ref.name, night)
        is IconRef.App -> appIcon(ref.pkg, ref.cls)?.let { Loaded(it, true) } ?: own("nb_dot", night)
    }

    private fun pluginDrawable(name: String): Drawable? {
        val res = plugin ?: return null
        val id = res.getIdentifier(name, "drawable", Protocol.PLUGIN_PKG)
        return if (id == 0) null else runCatching { res.getDrawable(id, null) }.getOrNull()
    }

    private fun own(name: String, night: Boolean): Loaded? {
        val id = module.getIdentifier(name, "drawable", Protocol.MODULE_PKG)
        if (id == 0) return null
        val d = runCatching { module.getDrawable(id, null) }.getOrNull() ?: return null
        return Loaded(d.mutate().apply { setTint(if (night) Icons.NIGHT_TINT else Icons.DAY_TINT) }, false)
    }

    /**
     * SystemUI создаёт панель при загрузке, пока пользователь ещё не разблокирован: без явных флагов PackageManager
     * тогда не видит activity обычных приложений (не direct-boot-aware) — вместо иконки была бы точка.
     */
    private fun appIcon(pkg: String, cls: String?): Drawable? {
        if (cls != null) {
            try {
                return pm.getActivityInfo(ComponentName(pkg, cls), ANY_BOOT_STATE).loadIcon(pm)
            } catch (_: PackageManager.NameNotFoundException) {
                // Activity пропала (приложение обновилось) — возьмём иконку приложения
            }
        }
        return try {
            pm.getApplicationInfo(pkg, ANY_BOOT_STATE).loadIcon(pm)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private companion object {
        const val ANY_BOOT_STATE = PackageManager.MATCH_DIRECT_BOOT_AWARE or PackageManager.MATCH_DIRECT_BOOT_UNAWARE
    }
}

/**
 * [inner], увеличенный или уменьшенный вокруг центра. Для раскладки размер прежний: ImageView вписывает картинку
 * как обычно, а масштаб применяется уже при рисовании — кнопка и её зона нажатия не меняются.
 */
class ScaledDrawable(private val inner: Drawable, private val scale: Float) : Drawable() {
    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        canvas.scale(scale, scale, bounds.exactCenterX(), bounds.exactCenterY())
        inner.draw(canvas)
        canvas.restoreToCount(save)
    }

    override fun onBoundsChange(bounds: Rect) {
        inner.bounds = bounds
    }

    override fun getIntrinsicWidth() = inner.intrinsicWidth

    override fun getIntrinsicHeight() = inner.intrinsicHeight

    override fun setAlpha(alpha: Int) {
        inner.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        inner.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
