package com.github.vorobeyyyyyy.geelynavbar

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavConfigTest {
    private fun button(tap: String, long: String = """{"type":"none"}""", icon: String = """{"type":"auto"}""") =
        """{"type":"button","tap":$tap,"long":$long,"icon":$icon}"""

    private fun config(vararg items: String, enabled: Boolean = true) =
        """{"version":1,"enabled":$enabled,"items":[${items.joinToString(",")}]}"""

    @Test
    fun roundTripAllKinds() {
        val c = NavConfig(
            true,
            Panel(
                NavAction.fixed.map { PanelItem.Button(it) } + listOf(
                    PanelItem.Button(NavAction.LaunchApp("ru.yandex.yandexnavi"), NavAction.Split(NavAction.LaunchApp("ru.yandex.yandexnavi"), NavAction.LaunchApp("a.b", "a.b.C"))),
                    PanelItem.HvacWidget(scale = 85),
                    PanelItem.Button(NavAction.LaunchApp("ru.yandex.music", "ru.yandex.music.main.MainScreenActivity"), icon = NavIcon.Named("nb_car")),
                    PanelItem.Button(NavAction.None, icon = NavIcon.App("a.b", "a.b.C")),
                    PanelItem.Button(NavAction.Home, icon = NavIcon.App("a.b"), scale = 135),
                ),
            ),
        )
        assertEquals(c, NavConfig.fromJson(c.toJson()))
        assertEquals(NavConfig.DEFAULT, NavConfig.fromJson(NavConfig.DEFAULT.toJson()))
        val padded = NavConfig(false, Panel(listOf(PanelItem.Button(NavAction.Home)), paddingTop = 23, paddingBottom = 34))
        assertEquals(padded, NavConfig.fromJson(padded.toJson()))
    }

    @Test
    fun formatIsReadable() {
        val navi = NavAction.LaunchApp("ru.yandex.yandexnavi")
        val c = NavConfig(true, Panel(listOf(PanelItem.Button(navi, NavAction.Split(navi, NavAction.LaunchApp("a.b", "a.b.C"))), PanelItem.HvacWidget())))
        val root = JSONObject(c.toJson())
        assertEquals(1, root.getInt("version"))
        assertTrue(root.getBoolean("enabled"))
        val items = root.getJSONArray("items")
        val b = items.getJSONObject(0)
        assertEquals("button", b.getString("type"))
        assertEquals("app", b.getJSONObject("tap").getString("type"))
        assertEquals("ru.yandex.yandexnavi", b.getJSONObject("tap").getString("package"))
        // Нет активности — нет и ключа
        assertFalse(b.getJSONObject("tap").has("activity"))
        val split = b.getJSONObject("long")
        assertEquals("split", split.getString("type"))
        assertEquals("app", split.getJSONObject("left").getString("type"))
        assertEquals("ru.yandex.yandexnavi", split.getJSONObject("left").getString("package"))
        assertEquals("a.b.C", split.getJSONObject("right").getString("activity"))
        assertEquals("auto", b.getJSONObject("icon").getString("type"))
        assertEquals("hvac", items.getJSONObject(1).getString("type"))
    }

    @Test
    fun noConfigOrNotJsonIsNull() {
        listOf(null, "", "   ", "мусор", "BTN|HOME", "[]", """{"version":1}""", """{"items":"не массив"}""")
            .forEach { assertNull(it, NavConfig.fromJson(it)) }
    }

    @Test
    fun brokenValuesFallBack() {
        val c = NavConfig.fromJson(
            config(
                button("""{"type":"teleport"}"""),
                button("""{"type":"app"}""", long = """{"type":"back"}"""),
                button("""{"type":"app","package":""}""", icon = """{"type":"named"}"""),
                button("""{"type":"home"}""", long = "42", icon = """{"type":"app","package":null}"""),
                """{"type":"button"}""",
                """{"type":"rocket"}""",
                "17",
            ),
        )!!
        assertEquals(
            listOf(
                PanelItem.Button(NavAction.None),
                PanelItem.Button(NavAction.None, NavAction.Back),
                PanelItem.Button(NavAction.None),
                PanelItem.Button(NavAction.Home),
                PanelItem.Button(NavAction.None),
            ),
            c.panel.items,
        )
        assertEquals(NavAction.LaunchApp("x", null), NavConfig.fromJson(config(button("""{"type":"app","package":"x","activity":null}""")))!!.panel.items.single().let { (it as PanelItem.Button).tap })
    }

    @Test
    fun splitSides() {
        fun splitOf(long: String) = (NavConfig.fromJson(config(button("""{"type":"none"}""", long = long)))!!.panel.items.single() as PanelItem.Button).long
        val a = NavAction.LaunchApp("a.b")
        // Старый сплит (флаг ecarx) — без сторон: приложения выбирают заново
        assertEquals(NavAction.Split(), splitOf("""{"type":"split"}"""))
        assertEquals(NavAction.Split(left = a), splitOf("""{"type":"split","left":{"type":"app","package":"a.b"}}"""))
        assertEquals(
            NavAction.Split(),
            splitOf("""{"type":"split","left":{"type":"home"},"right":"a.b"}"""),
        )
        assertEquals(NavAction.Split(right = a), splitOf("""{"type":"split","left":{"type":"app","package":""},"right":{"type":"app","package":"a.b"}}"""))
        // Не выбранная сторона в JSON не пишется
        val half = NavConfig(true, Panel(listOf(PanelItem.Button(NavAction.Split(left = a)))))
        assertFalse(JSONObject(half.toJson()).getJSONArray("items").getJSONObject(0).getJSONObject("tap").has("right"))
        assertEquals(half, NavConfig.fromJson(half.toJson()))
    }

    @Test
    fun scaleParsedAndClamped() {
        fun scaleOf(v: String) = (NavConfig.fromJson(config("""{"type":"button","tap":{"type":"home"},"scale":$v}"""))!!.panel.items.single() as PanelItem.Button).scale
        assertEquals(120, scaleOf("120"))
        assertEquals(30, scaleOf("5"))
        assertEquals(200, scaleOf("900"))
        assertEquals(100, scaleOf("\"big\""))
        assertEquals(100, scaleOf("null"))
        // Без ключа (конфиг до появления масштаба) — 100
        assertEquals(100, (NavConfig.fromJson(config(button("""{"type":"home"}""")))!!.panel.items.single() as PanelItem.Button).scale)
        assertEquals(PanelItem.HvacWidget(140), NavConfig.fromJson(config("""{"type":"hvac","scale":140}"""))!!.panel.items.single())
        assertEquals(PanelItem.HvacWidget(), NavConfig.fromJson(config("""{"type":"hvac"}"""))!!.panel.items.single())
        assertEquals(110, JSONObject(NavConfig(true, Panel(listOf(PanelItem.Button(NavAction.Back, scale = 110)))).toJson()).getJSONArray("items").getJSONObject(0).getInt("scale"))
    }

    @Test
    fun paddingsParsedAndClamped() {
        fun panelOf(extra: String) = NavConfig.fromJson("""{"enabled":true,$extra"items":[]}""")!!.panel
        assertEquals(0 to 0, panelOf("").let { it.paddingTop to it.paddingBottom })
        assertEquals(23 to 34, panelOf(""""paddingTop":23,"paddingBottom":34,""").let { it.paddingTop to it.paddingBottom })
        assertEquals(0 to 300, panelOf(""""paddingTop":-5,"paddingBottom":9999,""").let { it.paddingTop to it.paddingBottom })
        assertEquals(0 to 0, panelOf(""""paddingTop":"много","paddingBottom":null,""").let { it.paddingTop to it.paddingBottom })
    }

    @Test
    fun enabledDefaultsToOff() {
        assertFalse(NavConfig.fromJson("""{"items":[{"type":"hvac"}]}""")!!.enabled)
        assertFalse(NavConfig.fromJson(config("""{"type":"hvac"}""", enabled = false))!!.enabled)
    }

    @Test
    fun emptyListIsValidPanel() {
        val c = NavConfig.fromJson(config())!!
        assertTrue(c.enabled)
        assertEquals(emptyList<PanelItem>(), c.panel.items)
    }

    @Test
    fun singleHvacAndLimit() {
        val items = listOf("""{"type":"hvac"}""", button("""{"type":"home"}"""), """{"type":"hvac"}""") +
            List(20) { button("""{"type":"back"}""") }
        val p = NavConfig.fromJson(config(*items.toTypedArray()))!!.panel
        assertEquals(1, p.items.count { it is PanelItem.HvacWidget })
        assertEquals(Panel.MAX_ITEMS, p.items.size)
    }

    @Test
    fun autoIcons() {
        assertEquals(IconRef.Stock("ic_nav_home", "nb_home"), Icons.ref(PanelItem.Button(NavAction.Home)))
        assertEquals(IconRef.Own("nb_back_mod"), Icons.ref(PanelItem.Button(NavAction.Back)))
        assertEquals(IconRef.Own("nb_map"), Icons.ref(PanelItem.Button(NavAction.LaunchApp("ru.yandex.yandexnavi"), icon = NavIcon.Named("nb_map"))))
        assertEquals(IconRef.App("a.b", null), Icons.ref(PanelItem.Button(NavAction.LaunchApp("a.b"))))
        // Нажатие пустое — иконка по долгому
        assertEquals(IconRef.Own("nb_split"), Icons.ref(PanelItem.Button(NavAction.None, NavAction.Split())))
        assertEquals(IconRef.Own("nb_climate"), Icons.ref(PanelItem.Button(NavAction.Back, icon = NavIcon.Named("nb_climate"))))
    }
}
