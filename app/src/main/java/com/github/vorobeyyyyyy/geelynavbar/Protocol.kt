package com.github.vorobeyyyyyy.geelynavbar

/** Общие константы приложения и хука (хук живёт в процессе `com.android.systemui`). */
object Protocol {
    const val MODULE_PKG = "com.github.vorobeyyyyyy.geelynavbar"
    const val HOST_PKG = "com.android.systemui"
    /** Штатный плагин ecarx: рисует панель, но грузится в процесс SystemUI. */
    const val PLUGIN_PKG = "com.ecarx.systemui.plugin"

    const val PREFS = "navbar"
    /** Весь конфиг одним JSON ([NavConfig.toJson]). */
    const val KEY_CONFIG = "config"

    /** Приложение → хук: «ответь, если жив». */
    const val ACTION_PING = "$MODULE_PKG.PING"
    /** Приложение → хук: перечитать настройки и пересобрать панель. */
    const val ACTION_RELOAD = "$MODULE_PKG.RELOAD"
    /** Хук → приложение: ответ на PING и итог RELOAD. */
    const val ACTION_PONG = "$MODULE_PKG.PONG"

    const val EXTRA_VERSION = "version"
    const val EXTRA_PREFS_OK = "prefs_ok"
    /** Сколько пунктов своей панели сейчас на экране; 0 — панель штатная. */
    const val EXTRA_APPLIED = "applied"
    const val EXTRA_ERROR = "error"
}
