package com.github.vorobeyyyyyy.geelynavbar.ui

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.vorobeyyyyyy.geelynavbar.NavConfig
import com.github.vorobeyyyyyy.geelynavbar.Protocol

class MainActivity : ComponentActivity() {
    private lateinit var prefs: SharedPreferences
    /** false — Vector (LSPosed) не включил модуль: настройки пишутся, но хук их не увидит. */
    private var prefsShared = false

    private lateinit var editor: EditorState
    private var status by mutableStateOf<HookStatus>(HookStatus.Waiting)
    /** Нажали «Применить» и ждём, что хук пересобрал панель. */
    private var applying by mutableStateOf(false)

    private val main = Handler(Looper.getMainLooper())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            status = HookStatus.Alive(
                i.getStringExtra(Protocol.EXTRA_VERSION) ?: "?",
                i.getBooleanExtra(Protocol.EXTRA_PREFS_OK, false),
                i.getIntExtra(Protocol.EXTRA_APPLIED, -1),
                i.getStringExtra(Protocol.EXTRA_ERROR),
            )
            applying = false
        }
    }

    @SuppressLint("WorldReadableFiles")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // С LSPosed (xposedminversion ≥ 93) MODE_WORLD_READABLE кладёт файл туда, где его читает XSharedPreferences хука
        prefs = try {
            @Suppress("DEPRECATION")
            getSharedPreferences(Protocol.PREFS, Context.MODE_WORLD_READABLE).also { prefsShared = true }
        } catch (_: SecurityException) {
            getSharedPreferences(Protocol.PREFS, Context.MODE_PRIVATE)
        }
        editor = EditorState(NavConfig.fromJson(prefs.getString(Protocol.KEY_CONFIG, null)) ?: NavConfig.DEFAULT)
        // Своё, не для хука: подсказка про перетаскивание (один раз) и свои пресеты
        val ui = getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val labels = Labels(packageManager)
        val images = Images(this)
        val presets = PresetStore(ui)
        setContent {
            NavbarTheme {
                EditorScreen(
                    editor, status, applying, prefsShared, labels, images, presets,
                    initialNight = night,
                    showHint = !ui.getBoolean(KEY_HINT, false),
                    onHintShown = { ui.edit().putBoolean(KEY_HINT, true).apply() },
                    onApply = ::save,
                    onPing = { send(Protocol.ACTION_PING) },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(receiver, IntentFilter(Protocol.ACTION_PONG))
        send(Protocol.ACTION_PING)
    }

    override fun onStop() {
        unregisterReceiver(receiver)
        main.removeCallbacksAndMessages(null)
        applying = false
        super.onStop()
    }

    private fun save() {
        val c = editor.current()
        prefs.edit().putString(Protocol.KEY_CONFIG, c.toJson()).commit()
        editor.markSaved(c)
        applying = true
        send(Protocol.ACTION_RELOAD)
    }

    private fun send(action: String) {
        status = HookStatus.Waiting
        sendBroadcast(Intent(action).setPackage(Protocol.HOST_PKG))
        main.removeCallbacksAndMessages(null)
        main.postDelayed({
            if (status == HookStatus.Waiting) status = HookStatus.NoAnswer
            applying = false
        }, 2000)
    }

    private companion object {
        const val UI_PREFS = "ui"
        const val KEY_HINT = "drag_hint_shown"
    }
}
