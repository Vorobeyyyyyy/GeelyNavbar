package com.github.vorobeyyyyyy.geelynavbar.hook

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.util.Log

/**
 * Сплит средствами Android 9 — скрытыми вызовами `IActivityManager`, которыми его собирают «Недавние» SystemUI.
 * Правое приложение открывается как обычно, поверх него — левое; задача левого уходит в левую половину, а правое,
 * лежащее следующим, система ставит во вторую. Нужны права SystemUI: MANAGE_ACTIVITY_STACKS, REAL_GET_TASKS.
 *
 * Запуск с ActivityOptions `windowingMode = 3`, как во FreeTUGA Кнопках (`com.teto.inputservice`), не годится: уже
 * открытое левое приложение Android 9 в левую половину не переносит — она остаётся чёрной, а приложение оказывается
 * справа (проверено на эмуляторе). «Кнопки руля» (`ru.knopki.wheel`) обходят это, закрывая задачу левого
 * (FLAG_ACTIVITY_CLEAR_TASK): оно открывается заново, с главного экрана.
 */
internal class SplitScreen(private val ctx: Context) {
    private val iface by lazy { Class.forName("android.app.IActivityManager") }
    private val service: Any by lazy { ActivityManager::class.java.getMethod("getService").invoke(null)!! }

    /** Перестановка окон синхронная и небыстрая — звать не из главного потока. false — сплит не собрался. */
    fun show(left: Intent, right: Intent): Boolean {
        val leftPkg = left.component?.packageName ?: left.`package` ?: return false
        // Открытый сплит сначала закрываем: иначе правое, если оно уже слева, там бы и осталось
        iface.getMethod("dismissSplitScreenMode", Boolean::class.java).invoke(service, false)
        ctx.startActivity(right)
        ctx.startActivity(left)
        val task = topTask(leftPkg)
        if (task == null) {
            Log.w(TAG, "сплит: не нашлась задача $leftPkg")
            return false
        }
        val docked = iface.getMethod(
            "setTaskWindowingModeSplitScreenPrimary",
            Int::class.java, Int::class.java, Boolean::class.java, Boolean::class.java, Rect::class.java, Boolean::class.java,
        ).invoke(service, task, CREATE_MODE_TOP_OR_LEFT, true, true, null, false) as Boolean
        if (docked) fitOtherHalf()
        return docked
    }

    /**
     * Уже открытые приложения Android переводит во вторую половину, не меняя их размера: правое остаётся во весь
     * экран и закрыто левым — обрезано (на ГУ и на эмуляторе). Повторно задаём левой половине её же границы, как при
     * перетаскивании разделителя, — система пересчитывает все задачи второй половины под оставшееся место.
     */
    private fun fitOtherHalf() {
        val info = iface.getMethod("getStackInfo", Int::class.java, Int::class.java)
            .invoke(service, WINDOWING_MODE_SPLIT_SCREEN_PRIMARY, ACTIVITY_TYPE_STANDARD) ?: return
        val bounds = info.javaClass.getField("bounds").get(info) as Rect
        val rect = Rect::class.java
        iface.getMethod("resizeDockedStack", rect, rect, rect, rect, rect).invoke(service, bounds, null, null, null, null)
    }

    /** Задачи — от последней активной: только что запущенное приложение первое. */
    @Suppress("DEPRECATION")
    private fun topTask(pkg: String): Int? = ctx.getSystemService(ActivityManager::class.java)
        .getRunningTasks(MAX_TASKS)
        .firstOrNull { it.baseActivity?.packageName == pkg || it.topActivity?.packageName == pkg }
        // taskId появился в Android 10, в 9 — только id
        ?.id

    private companion object {
        const val TAG = "GeelyNavbar"
        const val MAX_TASKS = 20

        /** `ActivityManager.SPLIT_SCREEN_CREATE_MODE_TOP_OR_LEFT`: задача — в левую (на широком экране) половину. */
        const val CREATE_MODE_TOP_OR_LEFT = 0

        /** `WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_PRIMARY`, `ACTIVITY_TYPE_STANDARD`. */
        const val WINDOWING_MODE_SPLIT_SCREEN_PRIMARY = 3
        const val ACTIVITY_TYPE_STANDARD = 1
    }
}
