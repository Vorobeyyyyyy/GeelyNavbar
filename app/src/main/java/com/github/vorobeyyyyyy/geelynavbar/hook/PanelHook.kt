package com.github.vorobeyyyyyy.geelynavbar.hook

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.widget.FrameLayout
import android.widget.ImageView
import com.github.vorobeyyyyyy.geelynavbar.BuildConfig
import com.github.vorobeyyyyyy.geelynavbar.IconLoader
import com.github.vorobeyyyyyy.geelynavbar.NavConfig
import com.github.vorobeyyyyyy.geelynavbar.Panel
import com.github.vorobeyyyyyy.geelynavbar.Protocol
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.Collections
import java.util.WeakHashMap

/**
 * Хуки на `NavigationBarView` штатного плагина. Панель создаётся один раз за жизнь процесса SystemUI (синглтон
 * `NavigationBar`), поэтому и [NavPanel] один; пересобирается по RELOAD из приложения без перезапуска SystemUI.
 */
internal object PanelHook {
    private const val NAV_VIEW = "com.ecarx.systemui.plugin.navigationbar.NavigationBarView"
    private const val ALPHA_IMAGE_VIEW = "com.ecarx.systemui.plugin.utils.AlphaImageView"

    private val hooked = Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    private val prefs by lazy { XSharedPreferences(Protocol.MODULE_PKG, Protocol.PREFS) }
    private var panel: NavPanel? = null
    private var listening = false

    fun install(cl: ClassLoader, via: String) {
        synchronized(hooked) { if (!hooked.add(cl)) return }
        val nav = XposedHelpers.findClassIfExists(NAV_VIEW, cl)
        if (nav == null) {
            XposedBridge.log("GeelyNavbar: нет $NAV_VIEW (через $via) — прошивка не та, ничего не делаю")
            return
        }
        try {
            XposedHelpers.findAndHookMethod(nav, "onFinishInflate", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) = attach(param.thisObject as FrameLayout, cl)
            })
            XposedBridge.log("GeelyNavbar ${BuildConfig.VERSION_NAME}: хук на панели (через $via)")
        } catch (t: Throwable) {
            XposedBridge.log("GeelyNavbar: хук не поставлен, панель штатная")
            XposedBridge.log(t)
            return
        }
        // День/ночь: сток заново ставит свои иконки, мы — свои. Без этого хука иконки просто не сменят тему
        try {
            XposedHelpers.findAndHookMethod(nav, "onUIModeChanged", Int::class.javaPrimitiveType, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    panel?.takeIf { it.root === param.thisObject }?.refreshIcons()
                }
            })
        } catch (t: Throwable) {
            XposedBridge.log("GeelyNavbar: без смены иконок день/ночь: $t")
        }
    }

    private fun attach(root: FrameLayout, cl: ClassLoader) {
        try {
            // Application самого SystemUI (у PluginContext getApplicationContext() возвращает сам PluginContext)
            val host = XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.app.ActivityThread", null), "currentApplication",
            ) as? Application ?: root.context
            val stock = StockViews(
                root,
                XposedHelpers.getObjectField(root, "mNavContent") as FrameLayout,
                XposedHelpers.getObjectField(root, "hvacBar") as FrameLayout,
            )
            val alphaImageView = XposedHelpers.findClassIfExists(ALPHA_IMAGE_VIEW, cl)
            val actions = Actions(root, host)
            val p = NavPanel(
                stock,
                IconLoader(root.context.resources, moduleResources(host), host.packageManager),
                newButton = { c ->
                    runCatching { XposedHelpers.newInstance(alphaImageView, c) as ImageView }.getOrNull() ?: PressAlphaImageView(c)
                },
                run = actions::run,
            )
            panel = p
            apply(p)
            listen(host)
        } catch (t: Throwable) {
            XposedBridge.log("GeelyNavbar: панель не подхвачена, остаётся штатной")
            XposedBridge.log(t)
        }
    }

    private fun apply(p: NavPanel) {
        p.apply(readPanel())
        p.lastError?.let {
            XposedBridge.log("GeelyNavbar: своя панель не собралась, оставлена штатная")
            XposedBridge.log(it)
        }
    }

    private fun moduleResources(host: Context): Resources = try {
        host.createPackageContext(Protocol.MODULE_PKG, 0).resources
    } catch (t: Throwable) {
        XposedBridge.log("GeelyNavbar: ресурсы модуля недоступны, свои иконки не покажутся: $t")
        Resources.getSystem()
    }

    /** null — своя панель выключена в приложении, настроек нет, они битые или не читаются: панель штатная. */
    private fun readPanel(): Panel? {
        if (prefs.hasFileChanged()) prefs.reload()
        return NavConfig.fromJson(prefs.getString(Protocol.KEY_CONFIG, null))?.takeIf { it.enabled }?.panel
    }

    /** Приёмник на контексте хоста: приложение шлёт с `setPackage(com.android.systemui)`. */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun listen(host: Context) {
        if (listening) return
        listening = true
        host.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.action == Protocol.ACTION_RELOAD) {
                    prefs.reload()
                    panel?.let(::apply)
                }
                pong(c)
            }
        }, IntentFilter().apply {
            addAction(Protocol.ACTION_PING)
            addAction(Protocol.ACTION_RELOAD)
        })
        // Иконки приложений: панель создаётся до разблокировки пользователя, а приложения могут обновиться или
        // появиться позже — перерисовываем
        val refresh = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                panel?.refreshIcons()
            }
        }
        host.registerReceiver(refresh, IntentFilter(Intent.ACTION_USER_UNLOCKED))
        host.registerReceiver(refresh, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
    }

    private fun pong(c: Context) {
        val p = panel
        c.sendBroadcast(
            Intent(Protocol.ACTION_PONG).setPackage(Protocol.MODULE_PKG)
                .putExtra(Protocol.EXTRA_VERSION, BuildConfig.VERSION_NAME)
                .putExtra(Protocol.EXTRA_PREFS_OK, prefs.file.canRead())
                .putExtra(Protocol.EXTRA_APPLIED, p?.appliedItems ?: -1)
                .putExtra(Protocol.EXTRA_ERROR, p?.lastError?.toString()),
        )
    }
}
