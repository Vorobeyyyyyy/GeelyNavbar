package com.github.vorobeyyyyyy.geelynavbar

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/* Тот же разбор, но на org.json из Android 9 — именно он работает в SystemUI и в приложении. */
@RunWith(AndroidJUnit4::class)
class NavConfigAndroidTest {
    @Test
    fun roundTripOnPlatformJson() {
        val c = NavConfig(
            true,
            Panel(
                NavAction.fixed.map { PanelItem.Button(it) } + listOf(
                    PanelItem.Button(NavAction.LaunchApp("ru.yandex.yandexnavi"), NavAction.Split(NavAction.LaunchApp("ru.yandex.yandexnavi"), NavAction.LaunchApp("a.b", "a.b.C"))),
                    PanelItem.HvacWidget(scale = 120),
                    PanelItem.Button(NavAction.LaunchApp("a.b", "a.b.C"), icon = NavIcon.App("c.d")),
                    PanelItem.Button(NavAction.Back, icon = NavIcon.Named("nb_back")),
                ),
            ),
        )
        assertEquals(c, NavConfig.fromJson(c.toJson()))
    }

    @Test
    fun brokenOnPlatformJson() {
        listOf(null, "", "мусор", "[]", """{"version":1}""").forEach { assertNull(it, NavConfig.fromJson(it)) }
        val c = NavConfig.fromJson(
            """{"enabled":true,"items":[{"type":"button","tap":{"type":"app","package":null},"long":{"type":"zzz"},"icon":{"type":"named","name":7}},{"type":"x"}]}""",
        )!!
        assertEquals(listOf<PanelItem>(PanelItem.Button(NavAction.None)), c.panel.items)
    }
}
