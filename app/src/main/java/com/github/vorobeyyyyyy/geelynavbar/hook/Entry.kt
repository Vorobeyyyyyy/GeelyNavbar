package com.github.vorobeyyyyyy.geelynavbar.hook

import android.app.Application
import android.content.Context
import android.os.UserHandle
import com.github.vorobeyyyyyy.geelynavbar.Protocol
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Точка входа LSPosed/Vector (assets/xposed_init). Скоуп — `com.android.systemui`.
 *
 * Панель рисует не SystemUI, а плагин `com.ecarx.systemui.plugin`: хост грузит его код в свой процесс через
 * `createPackageContext(плагин, INCLUDE_CODE)`. Для такого вторичного пакета Vector сам зовёт handleLoadPackage
 * с загрузчиком плагина; на случай, если не позовёт, — запасной путь через тот же `createPackageContext`.
 */
class Entry : IXposedHookLoadPackage {
    override fun handleLoadPackage(lp: XC_LoadPackage.LoadPackageParam) {
        val process = Application.getProcessName() ?: lp.processName
        if (process != Protocol.HOST_PKG) return
        when (lp.packageName) {
            Protocol.PLUGIN_PKG -> PanelHook.install(lp.classLoader, "handleLoadPackage")
            Protocol.HOST_PKG -> hookPluginContext(lp.classLoader)
        }
    }

    private fun hookPluginContext(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.ContextImpl", cl, "createPackageContextAsUser",
                String::class.java, Int::class.javaPrimitiveType, UserHandle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.args[0] != Protocol.PLUGIN_PKG) return
                        if ((param.args[1] as Int) and Context.CONTEXT_INCLUDE_CODE == 0) return
                        val ctx = param.result as? Context ?: return
                        PanelHook.install(ctx.classLoader, "createPackageContext")
                    }
                },
            )
        } catch (t: Throwable) {
            XposedBridge.log("GeelyNavbar: запасной путь недоступен: $t")
        }
    }
}
