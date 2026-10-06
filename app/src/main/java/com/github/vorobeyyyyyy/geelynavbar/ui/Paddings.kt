package com.github.vorobeyyyyyy.geelynavbar.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.vorobeyyyyyy.geelynavbar.Panel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(16.dp)

/** Деление линейки — 1 dp отступа: палец проходит столько, чтобы сдвинуть на единицу точно. */
private val TICK = 14.dp

/** Медленнее этого (делений в секунду) отпущенная линейка не катится, а встаёт на ближайшее деление. */
private const val FLING_MIN = 12f

/** Удержание «−»/«+»: через сколько пойдёт повтор, с какого шага начнёт и до какого разгонится. */
private const val REPEAT_AFTER_MS = 400L
private const val REPEAT_FIRST_MS = 110L
private const val REPEAT_FASTEST_MS = 30L

/**
 * Отступы колонки — точно, в отличие от ручек на копии панели: сверху и снизу рядом, у каждого число,
 * «−»/«+» (удержание — повтор с разгоном) и линейка. Карточка отступа, который трогали последним, подсвечена,
 * как и его ручка на копии.
 */
@Composable
internal fun PaddingInspector(editor: EditorState) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Edge.entries.forEach { edge ->
            PaddingCard(
                edge,
                editor.padding(edge),
                focused = editor.padEdge == edge,
                onFocus = { editor.padEdge = edge },
                onChange = { editor.setPadding(edge, it) },
                // Шаг — от текущего значения в редакторе: повтор может шагнуть дважды до перекомпоновки
                onNudge = { d -> editor.setPadding(edge, editor.padding(edge) + d) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun PaddingCard(
    edge: Edge,
    value: Int,
    focused: Boolean,
    onFocus: () -> Unit,
    onChange: (Int) -> Unit,
    onNudge: (Int) -> Unit,
    modifier: Modifier,
) {
    val range = Panel.PADDING_RANGE
    val bg by animateColorAsState(if (focused) Palette.AccentContainer else Palette.SurfaceHigh, Motion.change(), label = "padBg")
    val ring by animateColorAsState(if (focused) Palette.Accent else Color.Transparent, Motion.change(), label = "padRing")
    val head by animateColorAsState(if (focused) Palette.Accent else Palette.TextDim, Motion.change(), label = "padHead")
    val focus by rememberUpdatedState(onFocus)
    Column(
        modifier.clip(CardShape).background(bg).border(2.dp, ring, CardShape)
            // Любое касание карточки — фокус на её отступ; само касание идёт дальше, к кнопкам и линейке
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false); focus() } }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(if (edge == Edge.TOP) Glyph.PAD_TOP else Glyph.PAD_BOTTOM, head, Modifier.size(26.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (edge == Edge.TOP) "Сверху" else "Снизу", color = head, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(Glyph.MINUS, "Меньше", enabled = value > range.first) { onNudge(-1) }
            Row(Modifier.width(150.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                RollingNumber(value, MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"))
                Text("dp", Modifier.padding(start = 4.dp, bottom = 8.dp), color = Palette.TextDim, style = MaterialTheme.typography.titleMedium)
            }
            StepButton(Glyph.PLUS, "Больше", enabled = value < range.last) { onNudge(1) }
        }
        Spacer(Modifier.weight(1f))
        Ruler(value, onChange, Modifier.fillMaxWidth().height(76.dp))
    }
}

/**
 * Число, у которого меняются только изменившиеся разряды, и каждый прокатывается, как на счётчике: больше — новая
 * цифра выезжает снизу, меньше — сверху. Старая уходит целиком за край, поэтому цифры не накладываются.
 */
@Composable
private fun RollingNumber(value: Int, style: TextStyle) {
    val previous = remember { intArrayOf(value) }
    val up = value >= previous[0]
    SideEffect { previous[0] = value }
    val digits = value.toString()
    Row(Modifier.clipToBounds()) {
        digits.forEachIndexed { i, ch ->
            // Разряд считаем справа: при 9 → 10 единицы остаются единицами
            key(digits.length - i) {
                AnimatedContent(
                    ch,
                    transitionSpec = {
                        val spec = tween<IntOffset>(Motion.SHORT, easing = Motion.Emphasized)
                        if (up) slideInVertically(spec) { it } togetherWith slideOutVertically(spec) { -it }
                        else slideInVertically(spec) { -it } togetherWith slideOutVertically(spec) { it }
                    },
                    label = "digit",
                ) { Text(it.toString(), style = style, color = Palette.Text) }
            }
        }
    }
}

/** Круглая «−»/«+»: нажатие — шаг 1, удержание — шаги сами, всё чаще. */
@Composable
private fun StepButton(glyph: Glyph, description: String, enabled: Boolean, onStep: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, Motion.press(), label = "stepPress")
    val alpha by animateFloatAsState(if (enabled) 1f else 0.3f, Motion.change(), label = "stepAlpha")
    val step by rememberUpdatedState(onStep)
    val canStep by rememberUpdatedState(enabled)
    // Шагали повтором — отпускание уже не шаг
    val repeated = remember { booleanArrayOf(false) }
    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        repeated[0] = false
        delay(REPEAT_AFTER_MS)
        var period = REPEAT_FIRST_MS
        while (canStep) {
            repeated[0] = true
            step()
            delay(period)
            period = (period * 0.85f).toLong().coerceAtLeast(REPEAT_FASTEST_MS)
        }
    }
    Box(
        Modifier.size(64.dp).scale(scale).clip(CircleShape).background(Palette.SurfaceHighest)
            .clickable(interaction, ripple(), enabled = enabled, onClickLabel = description) { if (!repeated[0]) step() },
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, Palette.Text.copy(alpha = alpha), Modifier.size(26.dp)) }
}

/**
 * Линейка под меткой: тянуть влево — больше, вправо — меньше; брошенная докатывается и встаёт ровно на деление;
 * нажатие на деление — к нему. Деления по 1, средние — по 5, с числом — по 10; к краям гаснут, как на барабане.
 * Значение, поменянное не ею (кнопки, ручка на копии, пресет), она догоняет пружиной.
 */
@Composable
private fun Ruler(value: Int, onChange: (Int) -> Unit, modifier: Modifier) {
    val range = Panel.PADDING_RANGE
    val min = range.first.toFloat()
    val max = range.last.toFloat()
    val pos = remember { Animatable(value.toFloat()) }
    // Где линейка под пальцем; null — не тянут. Палец не анимация: двигаем без корутин, докатывает уже pos
    var held by remember { mutableStateOf<Float?>(null) }
    // Значение ведёт сама линейка — палец или бросок; тогда снаружи её не двигаем
    var leading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val change by rememberUpdatedState(onChange)
    val current by rememberUpdatedState(value)
    val tick = with(LocalDensity.current) { TICK.toPx() }

    LaunchedEffect(value) {
        if (!leading && pos.targetValue != value.toFloat()) pos.animateTo(value.toFloat(), Motion.move())
    }
    LaunchedEffect(Unit) {
        snapshotFlow { (held ?: pos.value).roundToInt() }.collect { v ->
            if (leading && v != current) {
                change(v)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    suspend fun settle(target: Float, velocity: Float) {
        leading = true
        try {
            pos.animateTo(target.roundToInt().coerceIn(range).toFloat(), spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow), velocity)
        } finally {
            leading = false
        }
    }

    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium
    Canvas(
        modifier.clip(RoundedCornerShape(14.dp)).background(Palette.Surface)
            .draggable(
                rememberDraggableState { d -> held = ((held ?: pos.value) - d / tick).coerceIn(min, max) },
                Orientation.Horizontal,
                onDragStarted = {
                    pos.stop()
                    held = pos.value
                    leading = true
                },
                onDragStopped = { velocity ->
                    pos.snapTo(held ?: pos.value)
                    held = null
                    // Куда докатился бы бросок — и на ближайшее деление оттуда. Медленно отпустили — значит, ставили
                    // точно: ровно туда, где палец, без докатывания
                    val v = -velocity / tick
                    val fling = abs(v) >= FLING_MIN
                    val target = if (fling) exponentialDecay<Float>(frictionMultiplier = 2f).calculateTargetValue(pos.value, v) else pos.value
                    settle(target, if (fling) v else 0f)
                },
            )
            .pointerInput(tick) {
                detectTapGestures { at -> scope.launch { settle(pos.value + (at.x - size.width / 2f) / tick, 0f) } }
            },
    ) {
        val cx = size.width / 2f
        val v = held ?: pos.value
        val reach = cx / tick + 1
        val first = maxOf(range.first, floor(v - reach).toInt())
        val last = minOf(range.last, ceil(v + reach).toInt())
        val top = 10.dp.toPx()
        for (i in first..last) {
            val x = cx + (i - v) * tick
            val fade = (1f - abs(x - cx) / cx).coerceIn(0f, 1f)
            val alpha = fade * fade
            val (h, w) = when {
                i % 10 == 0 -> 26.dp.toPx() to 2.5.dp.toPx()
                i % 5 == 0 -> 18.dp.toPx() to 2.dp.toPx()
                else -> 11.dp.toPx() to 1.5.dp.toPx()
            }
            drawLine(Palette.TextDim.copy(alpha = alpha), Offset(x, top), Offset(x, top + h), w, StrokeCap.Round)
            if (i % 10 == 0) {
                val text = measurer.measure("$i", labelStyle)
                drawText(
                    text,
                    color = Palette.TextDim.copy(alpha = alpha),
                    topLeft = Offset(x - text.size.width / 2f, top + 30.dp.toPx()),
                )
            }
        }
        // Метка: где сейчас значение
        drawLine(Palette.Accent, Offset(cx, top - 4.dp.toPx()), Offset(cx, top + 30.dp.toPx()), 4.dp.toPx(), StrokeCap.Round)
        drawCircle(Palette.Accent, 4.dp.toPx(), Offset(cx, top - 4.dp.toPx()))
    }
}
