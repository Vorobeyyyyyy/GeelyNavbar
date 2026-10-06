package com.github.vorobeyyyyyy.geelynavbar.ui

import com.github.vorobeyyyyyy.geelynavbar.NavAction
import com.github.vorobeyyyyyy.geelynavbar.NavIcon
import com.github.vorobeyyyyyy.geelynavbar.PanelItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PickTest {
    private val navi = NavAction.LaunchApp("ru.yandex.yandexnavi")
    private val music = NavAction.LaunchApp("ru.yandex.music", "ru.yandex.music.Main")

    @Test
    fun appGoesWherePicked() {
        val b = PanelItem.Button(NavAction.Home, NavAction.Back)
        assertEquals(b.copy(tap = navi), b.withApp(Pick(Tab.TAP), navi))
        assertEquals(b.copy(long = navi), b.withApp(Pick(Tab.LONG), navi))
        assertEquals(b.copy(icon = NavIcon.App(music.pkg, music.cls)), b.withApp(Pick(Tab.LOOK), music))
        // Сторона сплита у другого действия — сплит с одной этой стороной
        assertEquals(b.copy(long = NavAction.Split(right = music)), b.withApp(Pick(Tab.LONG, Side.RIGHT), music))
    }

    @Test
    fun splitKeepsOtherSide() {
        val b = PanelItem.Button(NavAction.Split(left = navi))
        val both = b.withApp(Pick(Tab.TAP, Side.RIGHT), music)
        assertEquals(NavAction.Split(navi, music), both.tap)
        assertEquals(NavAction.Split(music, music), both.withApp(Pick(Tab.TAP, Side.LEFT), music).tap)
    }

    @Test
    fun currentAppForPick() {
        val b = PanelItem.Button(NavAction.Split(left = navi), music, NavIcon.App("a.b"))
        assertEquals(navi, b.app(Pick(Tab.TAP, Side.LEFT)))
        assertNull(b.app(Pick(Tab.TAP, Side.RIGHT)))
        assertNull(b.app(Pick(Tab.TAP)))
        assertEquals(music, b.app(Pick(Tab.LONG)))
        assertNull(b.app(Pick(Tab.LONG, Side.LEFT)))
        assertEquals(NavAction.LaunchApp("a.b"), b.app(Pick(Tab.LOOK)))
    }
}
