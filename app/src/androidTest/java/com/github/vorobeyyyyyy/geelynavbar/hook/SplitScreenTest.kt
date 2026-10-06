package com.github.vorobeyyyyyy.geelynavbar.hook

import android.content.Context
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.InvocationTargetException

/*
 * Раскладку окон проверить можно только из SystemUI. Здесь — что скрытые вызовы Android 9 есть с такими сигнатурами:
 * обычное приложение доходит до ActivityManager и получает отказ в правах, ничего не успев запустить.
 * SystemUI подписан ключом платформы, ему скрытые API открыты; обычному приложению — нет (dark greylist), поэтому
 * тесты идут только после `adb shell settings put global hidden_api_policy_p_apps 1`, иначе пропускаются.
 */
@RunWith(AndroidJUnit4::class)
class SplitScreenTest {
    private val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun hiddenApisOpen() {
        val open = runCatching { Class.forName("android.app.IActivityManager").getMethod("dismissSplitScreenMode", Boolean::class.java) }.isSuccess
        assumeTrue("скрытые API закрыты: hidden_api_policy_p_apps = 1", open)
    }

    @Test
    fun reachesActivityManagerAndNeedsSystemUiRights() {
        val settings = ctx.packageManager.getLaunchIntentForPackage("com.android.settings")!!
        val e = assertThrows(InvocationTargetException::class.java) { SplitScreen(ctx).show(settings, settings) }
        assertTrue(e.cause.toString(), e.cause is SecurityException)
        assertTrue(e.cause!!.message, e.cause!!.message!!.contains("MANAGE_ACTIVITY_STACKS"))
    }

    @Test
    fun dockCallExists() {
        val m = Class.forName("android.app.IActivityManager").getMethod(
            "setTaskWindowingModeSplitScreenPrimary",
            Int::class.java, Int::class.java, Boolean::class.java, Boolean::class.java, Rect::class.java, Boolean::class.java,
        )
        assertEquals(Boolean::class.java, m.returnType)
    }

    @Test
    fun fitCallsExist() {
        val am = Class.forName("android.app.IActivityManager")
        val info = am.getMethod("getStackInfo", Int::class.java, Int::class.java)
        assertEquals(Rect::class.java, info.returnType.getField("bounds").type)
        val rect = Rect::class.java
        am.getMethod("resizeDockedStack", rect, rect, rect, rect, rect)
    }
}
