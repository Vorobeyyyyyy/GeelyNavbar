package com.github.vorobeyyyyyy.geelynavbar.hook

import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import com.github.vorobeyyyyyy.geelynavbar.NavAction
import com.github.vorobeyyyyyy.geelynavbar.Protocol
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.Executors

/**
 * Действия кнопок. Выполняются в процессе SystemUI (uid systemui): можно инжектить клавиши, раскладывать окна
 * в сплит и открывать неэкспортированные activity SystemUI — так же делает мод панели SX11 из сети.
 *
 * Штатные переходы — private-методы самого `NavigationBarView` ([nav]), вызываются рефлексией, поведение как в стоке.
 * Перед действием закрываются штатные окна (климат, настройки авто, панель приложений) — порядок как в моде SX11.
 */
internal class Actions(private val nav: Any, private val ctx: Context) {
    private val bg = Executors.newSingleThreadExecutor()
    private val hvac = HvacLink(ctx)
    private val splitScreen = SplitScreen(ctx)
    private var last = 0L

    fun run(a: NavAction) {
        if (a == NavAction.None) return
        // Как NoDoubleClickListener стока: одно действие за 400 мс на всю панель
        val now = SystemClock.uptimeMillis()
        if (now - last < DEBOUNCE_MS) return
        last = now
        Log.i(TAG, "действие $a")
        try {
            stock("dismissDrivingMode")
            when (a) {
                NavAction.Home -> {
                    // Как обычная клавиша «Домой»: рабочий стол выбирает Android — штатный, AutoEasy или другой по умолчанию.
                    // Штатный gotoHome() открывал только ecarx.launcher3
                    key(KeyEvent.KEYCODE_HOME)
                    stock("closeHavc")
                }
                NavAction.AppPane -> {
                    stock("closeCarSetting")
                    stock("closeHavc")
                    stock("gotoAppPane")
                }
                NavAction.CarSettings -> {
                    stock("closeHavc")
                    stock("closeAppPane")
                    stock("gotoCar")
                }
                NavAction.Climate -> {
                    stock("closeCarSetting")
                    stock("closeAppPane")
                    hvac.openMain()
                }
                NavAction.Back -> {
                    closeWindows()
                    key(KeyEvent.KEYCODE_BACK)
                }
                NavAction.Recents -> {
                    closeWindows()
                    recents()
                }
                is NavAction.Split -> {
                    closeWindows()
                    split(a)
                }
                is NavAction.LaunchApp -> {
                    closeWindows()
                    intent(a)?.let(ctx::startActivity)
                }
                NavAction.None -> Unit
            }
        } catch (t: Throwable) {
            Log.e(TAG, "действие $a", t)
        }
    }

    /** Флаг ecarx `enable_split_from_ui`, как в моде SX11, не трогаем: какие приложения он раскладывает — неизвестно. */
    private fun split(a: NavAction.Split) {
        val left = a.left?.let(::intent)
        val right = a.right?.let(::intent)
        if (left == null || right == null) {
            Log.w(TAG, "сплит: выбраны не оба приложения — $a")
            return
        }
        bg.execute {
            try {
                if (!splitScreen.show(left, right)) Log.w(TAG, "сплит не собрался: $a")
            } catch (t: Throwable) {
                Log.e(TAG, "сплит $a", (t as? InvocationTargetException)?.cause ?: t)
            }
        }
    }

    private fun closeWindows() {
        stock("closeHavc")
        stock("closeCarSetting")
        stock("closeAppPane")
    }

    /** Ошибка одного штатного шага (например, closeHavc без try/catch в стоке) не должна срывать само действие. */
    private fun stock(method: String) {
        try {
            XposedHelpers.callMethod(nav, method)
        } catch (t: Throwable) {
            Log.w(TAG, "$method: ${t.cause ?: t}")
        }
    }

    /** sendKeyDownUpSync нельзя звать из главного потока. */
    private fun key(code: Int) {
        bg.execute {
            try {
                Instrumentation().sendKeyDownUpSync(code)
            } catch (t: Throwable) {
                Log.e(TAG, "клавиша $code", t)
            }
        }
    }

    private fun recents() {
        try {
            ctx.startActivity(
                Intent().setComponent(ComponentName(Protocol.HOST_PKG, RECENTS_ACTIVITY))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "RecentsActivity: $t — пробую клавишу APP_SWITCH")
            key(KeyEvent.KEYCODE_APP_SWITCH)
        }
    }

    /** Запуск, как с рабочего стола; null — приложение не установлено или не запускается. */
    private fun intent(a: NavAction.LaunchApp): Intent? {
        val intent = if (a.cls != null) {
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(a.pkg, a.cls)
        } else {
            ctx.packageManager.getLaunchIntentForPackage(a.pkg)
        }
        if (intent == null) {
            Log.w(TAG, "${a.pkg} не установлен или не запускается")
            return null
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    private companion object {
        const val TAG = "GeelyNavbar"
        const val DEBOUNCE_MS = 400L
        const val RECENTS_ACTIVITY = "com.android.systemui.recents.RecentsActivity"
    }
}

/**
 * Связь с климатом ecarx: `ecarx.hvac.app.IOpenHvacAidlInterface.openHvacMain()` (транзакция 1),
 * как в `hvac/HvacLeftBar` плагина. Пишем Parcel сами — классы AIDL не нужны.
 */
private class HvacLink(private val ctx: Context) {
    private var binder: IBinder? = null
    private var bound = false
    private var pending = false

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            binder = service
            if (pending) {
                pending = false
                openMain(service)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            binder = null
        }
    }

    fun openMain() {
        val b = binder
        if (b != null && b.isBinderAlive) {
            openMain(b)
            return
        }
        // Связь ещё не установлена или сервис упал: откроем, когда система (пере)подключит
        pending = true
        if (!bound) {
            bound = ctx.bindService(Intent(SERVICE_ACTION).setPackage(HVAC_PKG), conn, Context.BIND_AUTO_CREATE)
            if (!bound) Log.w("GeelyNavbar", "климат: bindService не удался")
        }
    }

    private fun openMain(b: IBinder) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            b.transact(TRANSACTION_OPEN_MAIN, data, reply, 0)
            reply.readException()
        } catch (t: Throwable) {
            Log.e("GeelyNavbar", "климат: openHvacMain", t)
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private companion object {
        const val HVAC_PKG = "ecarx.hvac.app"
        const val SERVICE_ACTION = "ecarx.hvac.app.HvacAppService"
        const val DESCRIPTOR = "ecarx.hvac.app.IOpenHvacAidlInterface"
        const val TRANSACTION_OPEN_MAIN = 1
    }
}
