package com.github.vorobeyyyyyy.geelynavbar.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.vorobeyyyyyy.geelynavbar.NavAction
import com.github.vorobeyyyyyy.geelynavbar.NavConfig
import com.github.vorobeyyyyyy.geelynavbar.NavIcon
import com.github.vorobeyyyyyy.geelynavbar.Panel
import com.github.vorobeyyyyyy.geelynavbar.PanelItem

/** Пункт панели в редакторе. По постоянному [id] анимации и перетаскивание узнают пункт после перестановок. */
internal data class Slot(val id: Long, val item: PanelItem)

/** Вкладки инспектора кнопки: нажатие, удержание, вид (иконка и масштаб). */
internal enum class Tab { TAP, LONG, LOOK }

internal enum class Side { LEFT, RIGHT }

/** Отступ колонки: сверху или снизу. */
internal enum class Edge { TOP, BOTTOM }

/** Для чего выбирают приложение: действие или иконка вкладки [tab], а у сплита — его сторона [side]. */
internal data class Pick(val tab: Tab, val side: Side? = null)

/** Какое приложение сейчас выбрано для [pick] — его подсветит выбор приложения. */
internal fun PanelItem.Button.app(pick: Pick): NavAction.LaunchApp? = when (pick.tab) {
    Tab.TAP -> tap.app(pick.side)
    Tab.LONG -> long.app(pick.side)
    Tab.LOOK -> (icon as? NavIcon.App)?.let { NavAction.LaunchApp(it.pkg, it.cls) }
}

/** Кнопка с приложением [app], выбранным для [pick]. */
internal fun PanelItem.Button.withApp(pick: Pick, app: NavAction.LaunchApp): PanelItem.Button = when (pick.tab) {
    Tab.TAP -> copy(tap = tap.withApp(pick.side, app))
    Tab.LONG -> copy(long = long.withApp(pick.side, app))
    Tab.LOOK -> copy(icon = NavIcon.App(app.pkg, app.cls))
}

private fun NavAction.app(side: Side?): NavAction.LaunchApp? = when (side) {
    null -> this as? NavAction.LaunchApp
    Side.LEFT -> (this as? NavAction.Split)?.left
    Side.RIGHT -> (this as? NavAction.Split)?.right
}

private fun NavAction.withApp(side: Side?, app: NavAction.LaunchApp): NavAction {
    val split = this as? NavAction.Split ?: NavAction.Split()
    return when (side) {
        null -> app
        Side.LEFT -> split.copy(left = app)
        Side.RIGHT -> split.copy(right = app)
    }
}

internal sealed interface HookStatus {
    data object Waiting : HookStatus
    data object NoAnswer : HookStatus
    data class Alive(val version: String, val prefsOk: Boolean, val applied: Int, val error: String?) : HookStatus
}

/** Как было до удаления или сброса — для «Вернуть». */
internal class Snapshot(val slots: List<Slot>, val paddingTop: Int, val paddingBottom: Int, val selectedId: Long?)

/** Редактируемая панель: всё, что меняется на экране до «Применить». */
@Stable
internal class EditorState(config: NavConfig) {
    private var nextId = 0L

    val slots = mutableStateListOf<Slot>()
    var enabled by mutableStateOf(config.enabled)
    var paddingTop by mutableIntStateOf(0)
    var paddingBottom by mutableIntStateOf(0)
    var selectedId by mutableStateOf<Long?>(null)
    var tab by mutableStateOf(Tab.TAP)

    /** Справа открыты отступы, а не пункт; какой из двух в фокусе. null — открыт пункт [selectedId]. */
    var padEdge by mutableStateOf<Edge?>(null)

    /** Что сейчас записано в настройки — с ним сравниваем, есть ли что применять. */
    var saved by mutableStateOf(config)
        private set

    init {
        load(config)
    }

    val selected: Slot? get() = slots.firstOrNull { it.id == selectedId }
    val full get() = slots.size >= Panel.MAX_ITEMS
    val hasHvac get() = slots.any { it.item is PanelItem.HvacWidget }
    val dirty get() = current() != saved

    fun current() = NavConfig(enabled, Panel(slots.map { it.item }, paddingTop, paddingBottom))

    fun newSlot(item: PanelItem) = Slot(nextId++, item)

    fun padding(edge: Edge) = if (edge == Edge.TOP) paddingTop else paddingBottom

    fun setPadding(edge: Edge, value: Int) {
        val v = value.coerceIn(Panel.PADDING_RANGE)
        if (edge == Edge.TOP) paddingTop = v else paddingBottom = v
    }

    /** Открыть пункт (вместо отступов, если были открыты они). */
    fun select(id: Long) {
        selectedId = id
        padEdge = null
    }

    fun indexOf(id: Long) = slots.indexOfFirst { it.id == id }

    /** Показать [config] как сохранённый; выделение остаётся на том же месте списка. */
    fun load(config: NavConfig) {
        val at = selectedId?.let(::indexOf)?.takeIf { it >= 0 } ?: 0
        saved = config
        enabled = config.enabled
        paddingTop = config.panel.paddingTop
        paddingBottom = config.panel.paddingBottom
        slots.clear()
        slots.addAll(config.panel.items.map(::newSlot))
        selectedId = slots.getOrNull(at.coerceAtMost(slots.lastIndex))?.id
    }

    fun markSaved(config: NavConfig) {
        saved = config
    }

    /** Новый пункт сразу выделен и открыт на «Нажатии» — следующий шаг очевиден: выбрать действие. */
    fun insert(at: Int, slot: Slot) {
        if (full) return
        slots.add(at.coerceIn(0, slots.size), slot)
        select(slot.id)
        tab = Tab.TAP
    }

    fun add(item: PanelItem) = insert(slots.size, newSlot(item))

    fun move(id: Long, to: Int) {
        val from = indexOf(id)
        if (from < 0 || from == to) return
        val s = slots.removeAt(from)
        slots.add(to.coerceIn(0, slots.size), s)
    }

    fun remove(id: Long) {
        val i = indexOf(id)
        if (i < 0) return
        slots.removeAt(i)
        if (selectedId == id) selectedId = slots.getOrNull(minOf(i, slots.lastIndex))?.id
    }

    fun update(id: Long, f: (PanelItem) -> PanelItem) {
        val i = indexOf(id)
        if (i >= 0) slots[i] = slots[i].copy(item = f(slots[i].item))
    }

    fun snapshot() = Snapshot(slots.toList(), paddingTop, paddingBottom, selectedId)

    fun restore(s: Snapshot) {
        slots.clear()
        slots.addAll(s.slots)
        paddingTop = s.paddingTop
        paddingBottom = s.paddingBottom
        selectedId = s.selectedId
    }

    /** Раскладка из пресета; вкл/выкл своей панели не трогаем. */
    fun loadPanel(panel: Panel) {
        slots.clear()
        slots.addAll(panel.items.map(::newSlot))
        paddingTop = panel.paddingTop
        paddingBottom = panel.paddingBottom
        selectedId = slots.firstOrNull()?.id
    }
}
