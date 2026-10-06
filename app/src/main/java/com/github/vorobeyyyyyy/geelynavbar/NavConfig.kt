package com.github.vorobeyyyyyy.geelynavbar

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Что делает кнопка. */
sealed interface NavAction {
    /** Ничего: кнопка-заглушка, а для долгого нажатия — долгое не перехватывается. */
    data object None : NavAction

    /** Клавиша «Домой»: рабочий стол по умолчанию (штатный ecarx.launcher3 или выбранный пользователем). */
    data object Home : NavAction

    /** Штатная панель приложений ecarx (открыть/закрыть). */
    data object AppPane : NavAction

    /** Штатные настройки автомобиля (ecarx.settings). */
    data object CarSettings : NavAction

    /** Главное окно климата (ecarx.hvac.app). */
    data object Climate : NavAction

    data object Back : NavAction

    /** Штатные «Недавние» SystemUI. */
    data object Recents : NavAction

    /** Два приложения рядом: [left] — в левой половине экрана, [right] — в правой. Пока не выбраны оба — ничего. */
    data class Split(val left: LaunchApp? = null, val right: LaunchApp? = null) : NavAction

    data class LaunchApp(val pkg: String, val cls: String? = null) : NavAction

    companion object {
        val fixed = listOf(Back, Home, AppPane, CarSettings, Climate, Recents, Split())
    }
}

/** Картинка кнопки: [Auto] — по действию, [Named] — из набора ([Icons.choices]), [App] — иконка приложения. */
sealed interface NavIcon {
    data object Auto : NavIcon

    data class Named(val name: String) : NavIcon

    data class App(val pkg: String, val cls: String? = null) : NavIcon
}

sealed interface PanelItem {
    /** Масштаб в процентах; ячейка в колонке остаётся того же размера. */
    val scale: Int

    data class Button(
        val tap: NavAction,
        val long: NavAction = NavAction.None,
        val icon: NavIcon = NavIcon.Auto,
        /** Масштабируется картинка; кнопка и её зона нажатия — нет. */
        override val scale: Int = SCALE_DEFAULT,
    ) : PanelItem

    /**
     * Штатный виджет климата (температура, вентилятор) — его можно передвинуть, убрать или увеличить/уменьшить.
     * Масштабируется сам view виджета вместе с зоной нажатия, обрезка — по его ячейке.
     */
    data class HvacWidget(override val scale: Int = SCALE_DEFAULT) : PanelItem

    companion object {
        const val SCALE_DEFAULT = 100
        const val SCALE_STEP = 5
        val SCALE_RANGE = 30..200
    }
}

/**
 * Раскладка панели сверху вниз. Пункты делят поровну высоту колонки за вычетом отступов [paddingTop] и
 * [paddingBottom] (dp) — ими раскладка подгоняется под штатную.
 */
data class Panel(
    val items: List<PanelItem>,
    val paddingTop: Int = 0,
    val paddingBottom: Int = 0,
) {
    companion object {
        /** Отступы колонки, dp: больше половины панели (720 dp) смысла не имеет. */
        val PADDING_RANGE = 0..300

        /** Больше в 720 dp не помещается: пункт становится ниже 60 dp. */
        const val MAX_ITEMS = 12

        /** Как в стоке: Авто, климат, Приложения, Домой. */
        val STOCK = Panel(
            listOf(
                PanelItem.Button(NavAction.CarSettings),
                PanelItem.HvacWidget(),
                PanelItem.Button(NavAction.AppPane),
                PanelItem.Button(NavAction.Home),
            ),
        )
    }
}

/**
 * Все настройки модуля. Хранятся одним JSON в настройках приложения (ключ [Protocol.KEY_CONFIG]):
 * ```
 * {
 *   "version": 1,
 *   "enabled": true,
 *   "paddingTop": 23,
 *   "paddingBottom": 34,
 *   "items": [
 *     {"type": "button", "tap": {"type": "app", "package": "ru.yandex.yandexnavi"},
 *      "long": {"type": "split", "left": {"type": "app", "package": "ru.yandex.yandexnavi"}, "right": {"type": "app", "package": "ru.yandex.music"}},
 *      "icon": {"type": "auto"}},
 *     {"type": "hvac", "scale": 100},
 *     {"type": "button", "tap": {"type": "back"}, "long": {"type": "none"}, "icon": {"type": "named", "name": "nb_back"}, "scale": 110}
 *   ]
 * }
 * ```
 * `paddingTop`/`paddingBottom` — отступы колонки в dp (0–300, по умолчанию 0).
 * Действия: `none`, `back`, `home`, `apps`, `car`, `climate`, `recents`, `app` (+ `package`, необязательный `activity`),
 * `split` (+ `left`, `right` — оба в виде действия `app`; нет ключа — сторона не выбрана).
 * Иконки: `auto`, `named` (+ `name`), `app` (+ `package`, `activity`). `scale` у кнопки и у `hvac` — масштаб в % (30–200, по умолчанию 100).
 *
 * Разбор прощающий — ошибка в одном месте не должна ломать панель: неизвестное действие — `none`, неизвестная
 * иконка — `auto`, масштаб вне 30–200 приводится к границе, не число — 100, непонятный пункт пропускается, второй виджет климата отбрасывается, лишние пункты сверх
 * [Panel.MAX_ITEMS] обрезаются. Если не JSON или нет `items` — настроек нет, панель остаётся штатной.
 */
data class NavConfig(val enabled: Boolean, val panel: Panel) {
    fun toJson(): String = JSONObject()
        .put("version", VERSION)
        .put("enabled", enabled)
        .put("paddingTop", panel.paddingTop)
        .put("paddingBottom", panel.paddingBottom)
        .put("items", JSONArray(panel.items.map(::itemJson)))
        .toString()

    companion object {
        const val VERSION = 1

        /** Что показать в приложении, пока ничего не сохранено. */
        val DEFAULT = NavConfig(false, Panel.STOCK)

        fun fromJson(s: String?): NavConfig? {
            if (s.isNullOrBlank()) return null
            return try {
                val root = JSONObject(s)
                val arr = root.optJSONArray("items") ?: return null
                val items = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::item) }.toMutableList()
                // Виджет климата один: второй экземпляр view взять неоткуда
                val firstHvac = items.indexOfFirst { it is PanelItem.HvacWidget }
                if (firstHvac >= 0) items.subList(firstHvac + 1, items.size).removeAll { it is PanelItem.HvacWidget }
                val panel = Panel(items.take(Panel.MAX_ITEMS), padding(root, "paddingTop"), padding(root, "paddingBottom"))
                NavConfig(root.optBoolean("enabled", false), panel)
            } catch (_: JSONException) {
                null
            }
        }

        private val actionIds = mapOf(
            NavAction.None to "none",
            NavAction.Back to "back",
            NavAction.Home to "home",
            NavAction.AppPane to "apps",
            NavAction.CarSettings to "car",
            NavAction.Climate to "climate",
            NavAction.Recents to "recents",
        )

        private fun itemJson(item: PanelItem): JSONObject = when (item) {
            is PanelItem.HvacWidget -> JSONObject().put("type", "hvac").put("scale", item.scale)
            is PanelItem.Button -> JSONObject()
                .put("type", "button")
                .put("tap", actionJson(item.tap))
                .put("long", actionJson(item.long))
                .put("icon", iconJson(item.icon))
                .put("scale", item.scale)
        }

        private fun actionJson(a: NavAction): JSONObject = when (a) {
            is NavAction.LaunchApp -> component("app", a.pkg, a.cls)
            is NavAction.Split -> JSONObject().put("type", "split")
                .putOpt("left", a.left?.let(::actionJson))
                .putOpt("right", a.right?.let(::actionJson))
            else -> JSONObject().put("type", actionIds.getValue(a))
        }

        private fun iconJson(i: NavIcon): JSONObject = when (i) {
            NavIcon.Auto -> JSONObject().put("type", "auto")
            is NavIcon.Named -> JSONObject().put("type", "named").put("name", i.name)
            is NavIcon.App -> component("app", i.pkg, i.cls)
        }

        private fun component(type: String, pkg: String, cls: String?): JSONObject {
            val o = JSONObject().put("type", type).put("package", pkg)
            if (cls != null) o.put("activity", cls)
            return o
        }

        private fun item(o: JSONObject): PanelItem? = when (o.optString("type")) {
            "hvac" -> PanelItem.HvacWidget(scale(o))
            "button" -> PanelItem.Button(
                action(o.optJSONObject("tap")),
                action(o.optJSONObject("long")),
                icon(o.optJSONObject("icon")),
                scale(o),
            )
            else -> null
        }

        private fun action(o: JSONObject?): NavAction {
            val type = o?.optString("type") ?: return NavAction.None
            if (type == "app") return o.str("package")?.let { NavAction.LaunchApp(it, o.str("activity")) } ?: NavAction.None
            // Без сторон — и старый `{"type": "split"}` (флаг ecarx), и ещё не выбранные приложения
            if (type == "split") return NavAction.Split(side(o, "left"), side(o, "right"))
            return actionIds.entries.firstOrNull { it.value == type }?.key ?: NavAction.None
        }

        private fun side(o: JSONObject, key: String) = action(o.optJSONObject(key)) as? NavAction.LaunchApp

        private fun icon(o: JSONObject?): NavIcon = when (o?.optString("type")) {
            "named" -> o.str("name")?.let(NavIcon::Named) ?: NavIcon.Auto
            "app" -> o.str("package")?.let { NavIcon.App(it, o.str("activity")) } ?: NavIcon.Auto
            else -> NavIcon.Auto
        }

        private fun padding(o: JSONObject, key: String): Int = ((o.opt(key) as? Number)?.toInt() ?: 0).coerceIn(Panel.PADDING_RANGE)

        private fun scale(o: JSONObject): Int =
            ((o.opt("scale") as? Number)?.toInt() ?: PanelItem.SCALE_DEFAULT).coerceIn(PanelItem.SCALE_RANGE)

        /** Непустая строка или null (нет ключа, JSON null, не строка, пусто). */
        private fun JSONObject.str(key: String): String? = (opt(key) as? String)?.takeIf { it.isNotBlank() }
    }
}
