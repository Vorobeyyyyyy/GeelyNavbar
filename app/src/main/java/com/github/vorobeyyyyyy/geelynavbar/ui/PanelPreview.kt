package com.github.vorobeyyyyyy.geelynavbar.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.vorobeyyyyyy.geelynavbar.IconRef
import com.github.vorobeyyyyyy.geelynavbar.Icons as NavIcons
import com.github.vorobeyyyyyy.geelynavbar.NavAction
import com.github.vorobeyyyyyy.geelynavbar.Panel
import com.github.vorobeyyyyyy.geelynavbar.PanelItem
import kotlin.math.roundToInt

/** Высота колонки панели на ГУ, dp: отступы в превью — в её масштабе. */
private const val PANEL_HEIGHT_DP = 720f

/**
 * Поле над и под превью: туда выходит ручка отступа, когда он 0. Сама панель в превью — без рамки, ровно колонка
 * 720 dp, поэтому штриховка отступа начинается с самого её края.
 */
private val HANDLE_MARGIN = 12.dp

private val CellShape = RoundedCornerShape(12.dp)
private val PanelShape = RoundedCornerShape(18.dp)

internal fun panelBg(night: Boolean) = if (night) Palette.PanelNight else Palette.PanelDay

internal fun panelFg(night: Boolean) = Color(if (night) NavIcons.NIGHT_TINT else NavIcons.DAY_TINT)

/** Картинка пункта так, как её нарисует хук. */
internal fun itemRef(item: PanelItem): IconRef = when (item) {
    is PanelItem.Button -> NavIcons.ref(item)
    is PanelItem.HvacWidget -> HVAC_REF
}

/** Виджет климата в превью — штатная картинка «20.0°» плагина (на эмуляторе — своя снежинка). */
internal val HVAC_REF = IconRef.Stock("ic_nav_hvac", "nb_climate")

/**
 * Копия панели — главный элемент: здесь выбирают пункт (нажатие), переставляют и уносят его (удержание),
 * тянут ручки отступов. Пропорции и отступы — как у колонки 720 dp на ГУ.
 */
@Composable
internal fun PanelReplica(editor: EditorState, drag: DragState, images: Images, night: Boolean, onSelect: () -> Unit, modifier: Modifier) {
    val bg by animateColorAsState(panelBg(night), Motion.enter(), label = "panelBg")
    val dim by animateFloatAsState(if (editor.enabled) 1f else 0.5f, Motion.enter(), label = "panelDim")
    // Ночью фон панели почти как фон экрана — обводка держит границу
    val edge by animateColorAsState(if (night) Palette.Outline else Color.Transparent, Motion.enter(), label = "panelEdge")
    BoxWithConstraints(modifier) {
        val k = (maxHeight - HANDLE_MARGIN * 2) / PANEL_HEIGHT_DP
        val top = k * editor.paddingTop
        val bottom = k * editor.paddingBottom
        val fg = panelFg(night)

        // Пустое место отступа — тоже вход в его настройку
        fun focusPadding(edge: Edge) {
            editor.padEdge = edge
            onSelect()
        }
        Box(
            Modifier.fillMaxSize().padding(vertical = HANDLE_MARGIN).clip(PanelShape).background(bg)
                .pointerInput(top, bottom) {
                    detectTapGestures { at ->
                        when {
                            at.y < top.toPx() -> focusPadding(Edge.TOP)
                            at.y > size.height - bottom.toPx() -> focusPadding(Edge.BOTTOM)
                        }
                    }
                }
                // Отступы колонки — штриховкой от самого края: видно, что это пустое место панели
                .drawBehind {
                    val hatch = fg.copy(alpha = 0.08f)
                    val step = 8.dp.toPx()
                    fun zone(y0: Float, y1: Float) {
                        if (y1 - y0 < 1f) return
                        var x = -size.height
                        while (x < size.width) {
                            drawLine(hatch, Offset(x, y1), Offset(x + (y1 - y0), y0), 2.dp.toPx())
                            x += step
                        }
                    }
                    zone(0f, top.toPx())
                    zone(size.height - bottom.toPx(), size.height)
                }
                .border(1.dp, edge, PanelShape),
        ) {
            SlotColumn(
                drag.entries(),
                key = { it.id },
                modifier = Modifier.fillMaxSize().padding(top = top, bottom = bottom).alpha(dim)
                    .onGloballyPositioned { drag.panelRect = it.boundsInRoot() },
            ) { slot -> PanelCell(slot, editor, drag, images, night, onSelect) }
        }

        // Ручки — на границе отступа; при нуле — на самой кромке панели, наполовину в поле над ней
        PaddingHandle(
            editor.paddingTop, k, fromTop = true, focused = editor.padEdge == Edge.TOP,
            Modifier.align(Alignment.TopCenter).offset(y = HANDLE_MARGIN + top - HANDLE_TOUCH / 2),
            blocked = { drag.blocked[0] = it },
            onFocus = { focusPadding(Edge.TOP) },
        ) { editor.paddingTop = it }
        PaddingHandle(
            editor.paddingBottom, k, fromTop = false, focused = editor.padEdge == Edge.BOTTOM,
            Modifier.align(Alignment.BottomCenter).offset(y = -(HANDLE_MARGIN + bottom) + HANDLE_TOUCH / 2),
            blocked = { drag.blocked[1] = it },
            onFocus = { focusPadding(Edge.BOTTOM) },
        ) { editor.paddingBottom = it }
    }
}

@Composable
private fun PanelCell(slot: Slot, editor: EditorState, drag: DragState, images: Images, night: Boolean, onSelect: () -> Unit) {
    // Место, куда упадёт взятый пункт: пустая рамка
    if (drag.session?.slot?.id == slot.id) {
        val c = if (drag.hover == Hover.Remove) Palette.Bad else Palette.Accent
        Box(Modifier.fillMaxSize().padding(4.dp).drawBehind {
            drawRoundRect(
                c,
                cornerRadius = CornerRadius(10.dp.toPx()),
                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
            )
            drawRoundRect(c.copy(alpha = 0.1f), cornerRadius = CornerRadius(10.dp.toPx()))
        })
        return
    }
    // Пока справа открыты отступы, пункт не выделяем: подсвечена ручка отступа
    val selected = editor.selectedId == slot.id && editor.padEdge == null
    val ring by animateColorAsState(if (selected) Palette.Accent else Color.Transparent, Motion.change(), label = "ring")
    val fill by animateColorAsState(if (selected) Palette.Accent.copy(alpha = 0.14f) else Color.Transparent, Motion.change(), label = "fill")
    Box(
        Modifier.fillMaxSize().padding(4.dp).clip(CellShape).background(fill).border(2.dp, ring, CellShape)
            .clickable {
                editor.select(slot.id)
                onSelect()
            },
    ) {
        ItemPicture(slot.item, images, night, Modifier.fillMaxSize())
        // Ручка — подсказка, что выбранный пункт можно взять и перенести
        AnimatedVisibility(selected, Modifier.align(Alignment.CenterStart), enter = fadeIn(Motion.enter()), exit = fadeOut(Motion.exit())) {
            GlyphIcon(Glyph.GRIP, Palette.Accent.copy(alpha = 0.8f), Modifier.size(width = 10.dp, height = 18.dp))
        }
    }
}

/** Картинка пункта с масштабом, как в хуке: вписать в ячейку, потом масштаб вокруг центра. */
@Composable
internal fun ItemPicture(item: PanelItem, images: Images, night: Boolean, modifier: Modifier) {
    val scale by animateFloatAsState(item.scale / 100f, Motion.move(), label = "scale")
    val pic = images.get(itemRef(item), night)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // Иконки приложений в хуке — с отступами по бокам 30 из 120 dp
        val appPad = maxWidth / 4
        AnimatedContent(pic, Modifier.fillMaxSize(), transitionSpec = { Motion.throughIn() togetherWith Motion.throughOut() }, label = "picture") { p ->
            if (p != null) {
                Image(
                    p.bitmap,
                    null,
                    Modifier.fillMaxSize().padding(horizontal = if (p.isApp) appPad else 0.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale },
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

private val HANDLE_TOUCH = 24.dp

/**
 * Ручка отступа колонки: тянуть по вертикали — грубо, двойное нажатие — 0. Касание открывает справа точную
 * настройку отступов ([onFocus]); пока она открыта на этом отступе ([focused]), ручка подсвечена. Пока тянут,
 * рядом видно число.
 */
@Composable
private fun PaddingHandle(
    value: Int,
    k: Dp,
    fromTop: Boolean,
    focused: Boolean,
    modifier: Modifier,
    blocked: (Rect) -> Unit,
    onFocus: () -> Unit,
    onChange: (Int) -> Unit,
) {
    val density = LocalDensity.current
    var active by remember { mutableStateOf(false) }
    var acc by remember { mutableFloatStateOf(0f) }
    // Серая видна и на светлой панели, и на тёмном поле над ней
    val color by animateColorAsState(if (active || focused) Palette.Accent else Palette.TextDim, Motion.change(), label = "handle")
    val width by animateDpAsState(if (active || focused) 44.dp else 30.dp, Motion.move(), label = "handleWidth")
    val state = rememberDraggableState { delta ->
        acc += delta / with(density) { k.toPx() } * if (fromTop) 1 else -1
        onChange(acc.roundToInt().coerceIn(Panel.PADDING_RANGE))
    }
    Box(
        modifier.fillMaxWidth().height(HANDLE_TOUCH)
            .draggable(
                state,
                Orientation.Vertical,
                onDragStarted = { active = true; acc = value.toFloat() },
                onDragStopped = { active = false },
            )
            .onGloballyPositioned { blocked(it.boundsInRoot()) }
            // Открываем сразу при касании, а не после ожидания второго нажатия
            .pointerInput(Unit) { detectTapGestures(onPress = { onFocus() }, onDoubleTap = { onChange(0) }) },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width, 5.dp).background(color, CircleShape))
        AnimatedVisibility(
            active,
            Modifier.align(if (fromTop) Alignment.BottomCenter else Alignment.TopCenter)
                .offset(y = if (fromTop) 26.dp else (-26).dp),
            enter = fadeIn(Motion.enter()) + scaleIn(Motion.enter(), 0.8f),
            exit = fadeOut(Motion.exit()) + scaleOut(Motion.exit(), 0.8f),
        ) {
            Text(
                "$value",
                Modifier.background(Palette.Accent, CircleShape).padding(horizontal = 10.dp, vertical = 2.dp),
                color = Palette.OnAccent,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * Лоток рядом с панелью: заготовки новых пунктов (нажатие — добавить в конец, удержание — нести на место).
 * Пока с панели несут пункт, лоток становится корзиной.
 */
@Composable
internal fun Tray(
    editor: EditorState,
    drag: DragState,
    images: Images,
    night: Boolean,
    onAdded: () -> Unit,
    onToggleNight: () -> Unit,
    modifier: Modifier,
) {
    val removing = drag.session?.from != null && !drag.landing || drag.landing && drag.hover == Hover.Remove
    Column(
        modifier.onGloballyPositioned { drag.trayRect = it.boundsInRoot() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            removing,
            Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = { Motion.throughIn() + Motion.throughScaleIn(0.92f) togetherWith Motion.throughOut() },
            label = "trayMode",
        ) { trash ->
            if (trash) {
                TrashZone(drag.hover == Hover.Remove)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TrayTile(TrayKind.BUTTON, "Кнопка", enabled = !editor.full, drag, night, onClick = { editor.add(PanelItem.Button(NavAction.None)); onAdded() }) {
                        GlyphIcon(Glyph.PLUS, panelFg(night), Modifier.size(28.dp))
                    }
                    AnimatedVisibility(!editor.hasHvac, enter = fadeIn(Motion.enter()) + scaleIn(Motion.enter(), 0.8f), exit = fadeOut(Motion.exit()) + scaleOut(Motion.exit(), 0.8f)) {
                        TrayTile(TrayKind.HVAC, "Климат", enabled = !editor.full, drag, night, onClick = { editor.add(PanelItem.HvacWidget()); onAdded() }) {
                            ItemPicture(PanelItem.HvacWidget(), images, night, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
        RoundIconButton(onClick = onToggleNight, modifier = Modifier.semantics { contentDescription = if (night) "Превью: ночь" else "Превью: день" }) {
            SunMoon(night, Palette.Text)
        }
    }
}

@Composable
private fun TrayTile(
    kind: TrayKind,
    label: String,
    enabled: Boolean,
    drag: DragState,
    night: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    DisposableEffect(kind) { onDispose { drag.trayTiles.remove(kind) } }
    val bg by animateColorAsState(panelBg(night), Motion.enter(), label = "tileBg")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(if (enabled) 1f else 0.4f)) {
        Box(
            Modifier.size(72.dp)
                .onGloballyPositioned { drag.trayTiles[kind] = it.boundsInRoot() }
                .clip(RoundedCornerShape(16.dp)).background(bg)
                .drawBehind {
                    drawRoundRect(
                        Palette.Accent,
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))),
                    )
                }
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { content() }
        Text(label, color = Palette.TextDim, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun TrashZone(hovered: Boolean) {
    val bg by animateColorAsState(if (hovered) Palette.Bad.copy(alpha = 0.32f) else Palette.BadContainer, Motion.change(), label = "trashBg")
    val scale by animateFloatAsState(if (hovered) 1.25f else 1f, Motion.press(), label = "trashScale")
    Column(
        Modifier.fillMaxSize().padding(bottom = 16.dp).clip(RoundedCornerShape(20.dp)).background(bg)
            .border(2.dp, Palette.Bad.copy(alpha = if (hovered) 1f else 0.4f), RoundedCornerShape(20.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Delete, null, tint = Palette.Bad, modifier = Modifier.size(36.dp).scale(scale))
        Spacer(Modifier.height(8.dp))
        Text("Убрать", color = Palette.Bad, style = MaterialTheme.typography.labelLarge)
    }
}

/** Пункт, который несут пальцем: приподнят (крупнее, с тенью), а отпущенный — долетает на место. */
@Composable
internal fun DragGhost(drag: DragState, images: Images, night: Boolean) {
    val s = drag.session ?: return
    val removing = drag.hover == Hover.Remove
    val ring by animateColorAsState(if (removing) Palette.Bad else Palette.Accent, Motion.change(), label = "ghostRing")
    Box(
        Modifier
            .layout { m, _ ->
                val r = drag.ghost
                val p = m.measure(Constraints.fixed(r.width.roundToInt().coerceAtLeast(0), r.height.roundToInt().coerceAtLeast(0)))
                layout(p.width, p.height) { p.place(IntOffset(r.left.roundToInt(), r.top.roundToInt())) }
            }
            .graphicsLayer {
                val l = drag.lift.value
                scaleX = l
                scaleY = l
                shadowElevation = 8.dp.toPx() * ((l - 1f) / 0.08f).coerceIn(0f, 1f)
                shape = CellShape
                clip = true
                alpha = if (removing) 0.75f else 1f
            }
            .background(panelBg(night))
            .border(2.dp, ring, CellShape),
    ) {
        ItemPicture(s.slot.item, images, night, Modifier.fillMaxSize())
    }
}

/** Круглая кнопка-значок 56 dp (≈ 80 dp на ГУ — палец попадает). */
@Composable
internal fun RoundIconButton(onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, Motion.press(), label = "press")
    Box(
        modifier.size(56.dp).scale(scale).clip(CircleShape).background(Palette.SurfaceHigh)
            .clickable(interaction, ripple(), enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f),
        contentAlignment = Alignment.Center,
    ) { content() }
}

