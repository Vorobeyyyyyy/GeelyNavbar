package com.github.vorobeyyyyyy.geelynavbar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import com.github.vorobeyyyyyy.geelynavbar.NavAction
import com.github.vorobeyyyyyy.geelynavbar.PanelItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Куда упадёт пункт, если отпустить сейчас. */
internal sealed interface Hover {
    /** Над панелью: встанет на место [index]. */
    data class Panel(val index: Int) : Hover

    /** Над лотком: уйдёт с панели. */
    data object Remove : Hover

    /** Мимо: вернётся, откуда взяли. */
    data object None : Hover
}

/** Заготовки в лотке: из них на панель добавляют новые пункты. */
internal enum class TrayKind { BUTTON, HVAC }

/** Что взяли: [from] — место на панели или null, если это новый пункт из лотка; [home] — где он лежал, px. */
internal class DragSession(val slot: Slot, val from: Int?, val grab: Offset, val home: Rect)

/**
 * Перетаскивание по экрану. Все координаты — px в системе корня Compose.
 *
 * Пока палец не отпущен, сам список [EditorState.slots] не меняется: панель рисует [entries] — тот же список,
 * где взятый пункт стоит на месте, куда упадёт (пустой рамкой), или убран, если его несут в лоток. Отпустили —
 * призрак долетает до этого места, и только тогда изменение применяется.
 */
@Stable
internal class DragState(private val editor: EditorState, private val scope: CoroutineScope) {
    var session by mutableStateOf<DragSession?>(null)
        private set
    var hover by mutableStateOf<Hover>(Hover.None)
        private set

    /** Призрак долетает до места — новые перетаскивания не начинаем. */
    var landing by mutableStateOf(false)
        private set
    private var releasing = false
    private var pointer by mutableStateOf(Offset.Zero)
    private val flight = Animatable(Rect.Zero, Rect.VectorConverter)

    /** «Приподнятость» призрака: 1 — лежит, больше — взят в руку. */
    val lift = Animatable(1f)

    /** Область пунктов панели (без отступов). */
    var panelRect by mutableStateOf(Rect.Zero)

    /** Лоток целиком: пока несут пункт с панели, это корзина. */
    var trayRect by mutableStateOf(Rect.Zero)
    val trayTiles = mutableStateMapOf<TrayKind, Rect>()

    /** Где удержание не берёт пункт: ручки отступов поверх крайних пунктов. */
    val blocked = mutableStateMapOf<Int, Rect>()

    var onLift: () -> Unit = {}
    var onTick: () -> Unit = {}
    var onRemoved: (Snapshot) -> Unit = {}

    /** Где сейчас призрак. */
    val ghost: Rect
        get() = when {
            landing -> flight.value
            else -> session?.let { Rect(pointer - it.grab, it.home.size) } ?: Rect.Zero
        }

    /**
     * Пункты панели с учётом того, что несут. Всегда новый список, прочитанный из [EditorState.slots]: так
     * вызывающий подписан на сам список. Иначе после броска (список того же содержимого) колонку пропустят,
     * и следующие изменения — «Вернуть», удаление — её уже не разбудят.
     */
    fun entries(): List<Slot> {
        val all = editor.slots.toList()
        val s = session ?: return all
        val base = all.filter { it.id != s.slot.id }
        val at = when (val h = hover) {
            is Hover.Panel -> h.index
            Hover.Remove -> null
            Hover.None -> s.from
        } ?: return base
        return base.toMutableList().apply { add(at.coerceIn(0, size), s.slot) }
    }

    /** Сколько пунктов на панели, не считая того, что несут. */
    private val baseCount get() = editor.slots.size - if (session?.from != null) 1 else 0

    private fun slotRect(index: Int, count: Int): Rect {
        val h = panelRect.height / count.coerceAtLeast(1)
        return Rect(panelRect.left, panelRect.top + h * index, panelRect.right, panelRect.top + h * (index + 1))
    }

    /** Взять пункт панели или заготовку лотка под пальцем; false — брать нечего. */
    fun start(at: Offset): Boolean {
        if (session != null || blocked.values.any { it.contains(at) }) return false
        val s = pickPanel(at) ?: pickTray(at) ?: return false
        session = s
        pointer = at
        hover = hoverAt(ghost.center)
        if (s.from != null) editor.select(s.slot.id)
        onLift()
        scope.launch {
            lift.snapTo(1f)
            lift.animateTo(LIFTED, Motion.move())
        }
        return true
    }

    private fun pickPanel(at: Offset): DragSession? {
        val n = editor.slots.size
        if (n == 0 || !panelRect.contains(at)) return null
        val i = ((at.y - panelRect.top) / (panelRect.height / n)).toInt().coerceIn(0, n - 1)
        val home = slotRect(i, n)
        return DragSession(editor.slots[i], i, at - home.topLeft, home)
    }

    private fun pickTray(at: Offset): DragSession? {
        if (editor.full) return null
        val (kind, rect) = trayTiles.entries.firstOrNull { it.value.contains(at) } ?: return null
        val item = when (kind) {
            TrayKind.BUTTON -> PanelItem.Button(NavAction.None)
            TrayKind.HVAC -> if (editor.hasHvac) return null else PanelItem.HvacWidget()
        }
        return DragSession(editor.newSlot(item), null, at - rect.topLeft, rect)
    }

    fun dragBy(delta: Offset) {
        if (session == null || landing) return
        pointer += delta
        val h = hoverAt(ghost.center)
        if (h != hover) {
            if (h != Hover.None) onTick()
            hover = h
        }
    }

    /**
     * Решает по центру призрака, а не по пальцу: соседи расступаются, когда до них дошла середина пункта.
     * Зона панели «магнитная» — до середины промежутка между панелью и лотком.
     */
    private fun hoverAt(c: Offset): Hover {
        val s = session ?: return Hover.None
        val panelEdge = if (trayRect.isEmpty) panelRect.right else (panelRect.right + trayRect.left) / 2
        return when {
            c.x < panelEdge -> {
                if (s.from == null && editor.full) return Hover.None
                val n = baseCount + 1
                Hover.Panel(((c.y - panelRect.top) / (panelRect.height / n)).toInt().coerceIn(0, n - 1))
            }
            s.from != null && c.x <= trayRect.right -> Hover.Remove
            else -> Hover.None
        }
    }

    /** Отпустили (или жест сорвался — [cancel]): призрак долетает до места, потом изменение применяется. */
    fun release(cancel: Boolean = false) {
        val s = session ?: return
        if (releasing) return
        releasing = true
        if (cancel) hover = Hover.None
        // Сразу, без диспетчера: иначе кадр с призраком на старом месте полёта
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            flight.snapTo(ghost)
            landing = true
            val from = flight.value
            val h = hover
            val target = when (h) {
                is Hover.Panel -> slotRect(h.index, baseCount + 1)
                Hover.Remove -> Rect(from.center, 0f)
                Hover.None -> s.from?.let { slotRect(it, baseCount + 1) } ?: s.home
            }
            coroutineScope {
                launch { lift.animateTo(1f, Motion.move()) }
                // Уход в корзину — коротко и с разгоном, приземление на место — пружиной
                flight.animateTo(target, if (h == Hover.Remove) Motion.exit() else Motion.move())
            }
            when (h) {
                is Hover.Panel -> if (s.from != null) {
                    editor.move(s.slot.id, h.index)
                } else {
                    editor.insert(h.index, s.slot)
                }
                Hover.Remove -> {
                    val before = editor.snapshot()
                    editor.remove(s.slot.id)
                    onRemoved(before)
                }
                Hover.None -> Unit
            }
            session = null
            hover = Hover.None
            landing = false
            releasing = false
        }
    }

    private companion object {
        const val LIFTED = 1.08f
    }
}

private class SlotAnim<T>(val key: Any, var item: T) {
    var leaving = false
    var targetTop = 0f
    var targetHeight = 0f
    lateinit var top: Animatable<Float, AnimationVector1D>
    lateinit var height: Animatable<Float, AnimationVector1D>
    val started get() = ::top.isInitialized
}

/** Порядок пунктов на экране: новые — на своих местах, ушедшие — там, где были, пока не схлопнутся. */
private class SlotHolder<T> {
    var entries: List<SlotAnim<T>> = emptyList()
    var initialized = false

    fun sync(items: List<T>, key: (T) -> Any): List<SlotAnim<T>> {
        val old = entries.associateBy { it.key }
        val keys = HashSet<Any>()
        val result = ArrayList<SlotAnim<T>>(items.size + 2)
        for (it in items) {
            val k = key(it)
            keys += k
            result += old[k]?.apply { item = it; leaving = false } ?: SlotAnim(k, it)
        }
        entries.forEachIndexed { i, a ->
            if (a.key in keys) return@forEachIndexed
            a.leaving = true
            result.add(if (i == 0) 0 else result.indexOf(entries[i - 1]) + 1, a)
        }
        entries = result
        return result
    }
}

/**
 * Колонка как на ГУ: пункты делят высоту поровну. Перестановка, появление и исчезновение — плавные:
 * у каждого пункта пружиной анимируются верх и высота. Это доли высоты колонки, поэтому когда меняется сама
 * высота (тянут ручку отступа), пункты следуют за ней сразу, без догоняния.
 */
@Composable
internal fun <T> SlotColumn(items: List<T>, key: (T) -> Any, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    val holder = remember { SlotHolder<T>() }
    // Доисчезавший пункт убирается из списка пересборкой
    var forgotten by remember { mutableIntStateOf(0) }
    val shown = holder.sync(items, key).also { forgotten }
    val first = !holder.initialized
    holder.initialized = true

    val unit = if (items.isEmpty()) 0f else 1f / items.size
    var y = 0f
    for (a in shown) {
        a.targetTop = y
        a.targetHeight = if (a.leaving) 0f else unit
        if (!a.leaving) y += unit
        if (!a.started) {
            a.top = Animatable(a.targetTop)
            a.height = Animatable(if (first) a.targetHeight else 0f)
        }
    }

    Layout(
        content = {
            for (a in shown) key(a.key) {
                LaunchedEffect(a.targetTop, a.targetHeight) {
                    launch { a.top.animateTo(a.targetTop, Motion.move()) }
                    a.height.animateTo(a.targetHeight, Motion.move())
                    if (a.leaving) {
                        holder.entries = holder.entries - a
                        forgotten++
                    }
                }
                Box(Modifier.clipToBounds()) { content(a.item) }
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val placeables = measurables.mapIndexed { i, m ->
            m.measure(Constraints.fixed(w, (shown[i].height.value * h).roundToInt().coerceAtLeast(0)))
        }
        layout(w, h) {
            placeables.forEachIndexed { i, p -> p.place(0, (shown[i].top.value * h).roundToInt()) }
        }
    }
}
