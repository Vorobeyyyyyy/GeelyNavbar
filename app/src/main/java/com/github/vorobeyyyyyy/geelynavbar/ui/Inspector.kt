package com.github.vorobeyyyyyy.geelynavbar.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.github.vorobeyyyyyy.geelynavbar.IconRef
import com.github.vorobeyyyyyy.geelynavbar.Icons as NavIcons
import com.github.vorobeyyyyyy.geelynavbar.NavAction
import com.github.vorobeyyyyyy.geelynavbar.NavIcon
import com.github.vorobeyyyyyy.geelynavbar.PanelItem
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TileShape = RoundedCornerShape(16.dp)
private val TILE_HEIGHT = 96.dp

/** Страница инспектора с отступами колонки; у остальных страниц ключ — id пункта. */
private data object PaddingPage

/** Первый ряд плиток действий — частые; во втором — «Ничего», запуск приложения и сплит. */
private val FIRST_ROW = NavAction.fixed.filter { it !is NavAction.Split }

/**
 * Настройки выбранного пункта. Сверху — сводка (что делает нажатие, удержание, как выглядит), она же вкладки;
 * под ней — варианты выбранной вкладки плитками с картинками, а не списком слов.
 */
@Composable
internal fun Inspector(
    editor: EditorState,
    labels: Labels,
    images: Images,
    night: Boolean,
    onPickApp: (Pick) -> Unit,
    onRemove: (Long) -> Unit,
    modifier: Modifier,
) {
    // Отступы на панели — над первым пунктом или под последним; страница отступов въезжает с той же стороны
    val lastEdge = remember { arrayOf(Edge.TOP) }
    editor.padEdge?.let { lastEdge[0] = it }
    fun order(page: Any?): Int = when (page) {
        PaddingPage -> if (lastEdge[0] == Edge.TOP) -1 else editor.slots.size
        is Long -> editor.indexOf(page)
        else -> -1
    }
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(Palette.Surface).padding(16.dp)) {
        AnimatedContent(
            if (editor.padEdge != null) PaddingPage else editor.selectedId,
            transitionSpec = {
                // Выбрали пункт ниже — содержимое въезжает снизу, выше — сверху: как на самой панели
                val dir = if (order(targetState) >= order(initialState)) 1 else -1
                // Общая ось M3: сдвиг вместе, а прозрачность «через ноль» — старое и новое не накладываются
                (slideInVertically(Motion.enter()) { it / 10 * dir } + Motion.throughIn()) togetherWith
                    (slideOutVertically(Motion.exit()) { -it / 10 * dir } + Motion.throughOut())
            },
            label = "inspector",
        ) { page ->
            if (page == PaddingPage) {
                PaddingInspector(editor)
                return@AnimatedContent
            }
            val id = page as Long?
            // Убранный пункт ещё уезжает — показываем его последним, а не «пусто»
            val last = remember(id) { arrayOfNulls<Slot>(1) }
            val slot = editor.slots.firstOrNull { it.id == id }?.also { last[0] = it } ?: last[0]
            when (val item = slot?.item) {
                null -> EmptyInspector()
                is PanelItem.Button -> ButtonInspector(slot, item, editor, labels, images, night, onPickApp, onRemove)
                is PanelItem.HvacWidget -> HvacInspector(slot, item, editor, images, night, onRemove)
            }
        }
    }
}

@Composable
private fun ButtonInspector(
    slot: Slot,
    item: PanelItem.Button,
    editor: EditorState,
    labels: Labels,
    images: Images,
    night: Boolean,
    onPickApp: (Pick) -> Unit,
    onRemove: (Long) -> Unit,
) {
    fun set(f: (PanelItem.Button) -> PanelItem.Button) = editor.update(slot.id) { (it as? PanelItem.Button)?.let(f) ?: it }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(88.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TabCard(Tab.TAP, Glyph.TAP, "Нажатие", editor) { ActionValue(item.tap, labels, images) }
            TabCard(Tab.LONG, Glyph.HOLD, "Удержание", editor) { ActionValue(item.long, labels, images) }
            TabCard(Tab.LOOK, Glyph.LOOK, "Вид", editor) {
                // Глиф в ячейке панели — меньше половины её ширины; в миниатюре увеличиваем вдвое
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(panelBg(night))) {
                    ItemPicture(item, images, night, Modifier.fillMaxSize().graphicsLayer { scaleX = 2f; scaleY = 2f })
                }
                Spacer(Modifier.width(10.dp))
                Text("${item.scale} %", style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
            ItemTools(slot, editor, onRemove)
        }
        Spacer(Modifier.height(16.dp))
        AnimatedContent(
            editor.tab,
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(Motion.enter()) { it / 12 * dir } + Motion.throughIn()) togetherWith
                    (slideOutHorizontally(Motion.exit()) { -it / 12 * dir } + Motion.throughOut())
            },
            label = "tab",
        ) { tab ->
            when (tab) {
                Tab.TAP -> ActionGrid(item.tap, labels, images, onPickApp = { side -> onPickApp(Pick(Tab.TAP, side)) }) { a -> set { it.copy(tap = a) } }
                Tab.LONG -> ActionGrid(item.long, labels, images, onPickApp = { side -> onPickApp(Pick(Tab.LONG, side)) }) { a -> set { it.copy(long = a) } }
                Tab.LOOK -> LookPanel(item, images, night, onPickApp = { onPickApp(Pick(Tab.LOOK)) }, onIcon = { i -> set { it.copy(icon = i) } }) { s ->
                    set { it.copy(scale = s) }
                }
            }
        }
    }
}

@Composable
private fun HvacInspector(slot: Slot, item: PanelItem.HvacWidget, editor: EditorState, images: Images, night: Boolean, onRemove: (Long) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(88.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.weight(1f).fillMaxHeight().clip(TileShape).background(Palette.SurfaceHigh).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)).background(panelBg(night))) {
                    ItemPicture(item, images, night, Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Климат", style = MaterialTheme.typography.titleLarge)
                    Text("заводской виджет", color = Palette.TextDim, style = MaterialTheme.typography.bodyLarge)
                }
            }
            ItemTools(slot, editor, onRemove)
        }
        Spacer(Modifier.height(28.dp))
        ScaleRow(item.scale) { s -> editor.update(slot.id) { (it as? PanelItem.HvacWidget)?.copy(scale = s) ?: it } }
    }
}

@Composable
private fun EmptyInspector() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(72.dp).background(Palette.SurfaceHigh, CircleShape), contentAlignment = Alignment.Center) {
            GlyphIcon(Glyph.PLUS, Palette.TextDim, Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Панель пуста", style = MaterialTheme.typography.titleLarge)
        Text("Добавьте кнопку из лотка слева", color = Palette.TextDim, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Карточка-вкладка: подпись и текущее значение; выбранная подсвечена. */
@Composable
private fun RowScope.TabCard(tab: Tab, glyph: Glyph, title: String, editor: EditorState, value: @Composable RowScope.() -> Unit) {
    val selected = editor.tab == tab
    val bg by animateColorAsState(if (selected) Palette.AccentContainer else Palette.SurfaceHigh, Motion.change(), label = "tabBg")
    val ring by animateColorAsState(if (selected) Palette.Accent else Color.Transparent, Motion.change(), label = "tabRing")
    val head by animateColorAsState(if (selected) Palette.Accent else Palette.TextDim, Motion.change(), label = "tabHead")
    Column(
        Modifier.weight(1f).fillMaxHeight().clip(TileShape).background(bg).border(2.dp, ring, TileShape)
            .clickable { editor.tab = tab }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(glyph, head, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = head, style = MaterialTheme.typography.labelLarge)
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = value)
    }
}

@Composable
private fun RowScope.ActionValue(a: NavAction, labels: Labels, images: Images) {
    // Значок и подпись меняются вместе, иначе на миг видно новый значок со старой подписью
    AnimatedContent(a, transitionSpec = { fadeThrough() }, label = "actionValue") { action ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { ActionIcon(action, images, 32.dp) }
            Spacer(Modifier.width(10.dp))
            Text(labels.action(action), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (action is NavAction.Split) {
                listOfNotNull(action.left, action.right).forEach {
                    Spacer(Modifier.width(8.dp))
                    ActionIcon(it, images, 28.dp)
                }
            }
        }
    }
}

/** Поднять, опустить, убрать — то же, что перетаскиванием, но нажатиями. */
@Composable
private fun ItemTools(slot: Slot, editor: EditorState, onRemove: (Long) -> Unit) {
    val i = editor.indexOf(slot.id)
    Row(Modifier.fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ToolButton(Icons.Filled.KeyboardArrowUp, "Выше", Palette.Text, enabled = i > 0) { editor.move(slot.id, i - 1) }
        ToolButton(Icons.Filled.KeyboardArrowDown, "Ниже", Palette.Text, enabled = i in 0 until editor.slots.lastIndex) { editor.move(slot.id, i + 1) }
        ToolButton(Icons.Filled.Delete, "Убрать", Palette.Bad, enabled = true) { onRemove(slot.id) }
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    enabled: Boolean,
    height: Dp = 88.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, Motion.press(), label = "toolPress")
    val alpha by animateFloatAsState(if (enabled) 1f else 0.3f, Motion.change(), label = "toolAlpha")
    Box(
        modifier.size(width = TOOL_WIDTH, height = height).scale(scale).clip(TileShape).background(Palette.SurfaceHigh)
            .clickable(interaction, ripple(), enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = tint.copy(alpha = alpha), modifier = Modifier.size(32.dp)) }
}

/**
 * Плитки действий в два ряда по [GRID_COLUMNS]. Свободное место второго ряда — половины сплита: когда он выбран,
 * они цепочкой выезжают из-под плитки «Сплит», а когда выбрали другое — заезжают обратно. [onPickApp]: null — приложение для запуска, сторона — для этой половины.
 */
@Composable
private fun ActionGrid(current: NavAction, labels: Labels, images: Images, onPickApp: (Side?) -> Unit, onPick: (NavAction) -> Unit) {
    // Последний сплит: его половины ещё уезжают, когда выбрали другое, и он же вернётся, если передумали
    val lastSplit = remember { arrayOfNulls<NavAction.Split>(1) }
    (current as? NavAction.Split)?.let { lastSplit[0] = it }

    @Composable
    fun Fixed(a: NavAction, modifier: Modifier) {
        // Сравниваем по виду: у сплита свои приложения, повторное нажатие их не сбрасывает
        val selected = a::class == current::class
        val pick = when {
            selected -> current
            a is NavAction.Split -> lastSplit[0] ?: a
            else -> a
        }
        ActionTile(labels.action(a), selected, modifier, onClick = { onPick(pick) }) { ActionIcon(a, images, 40.dp) }
    }

    BoxWithConstraints {
        // Ширина клетки общая для обоих рядов, хотя во втором плиток меньше
        val cell = (maxWidth - GRID_GAP * (GRID_COLUMNS - 1)) / GRID_COLUMNS
        Column(verticalArrangement = Arrangement.spacedBy(GRID_GAP)) {
            Row(horizontalArrangement = Arrangement.spacedBy(GRID_GAP)) { FIRST_ROW.forEach { Fixed(it, Modifier.width(cell)) } }
            Row(horizontalArrangement = Arrangement.spacedBy(GRID_GAP)) {
                Fixed(NavAction.None, Modifier.width(cell))
                // Уже выбрано приложение — его иконка и имя, нажатие — выбрать другое
                val app = current as? NavAction.LaunchApp
                ActionTile(if (app != null) labels.action(app) else "Запуск…", app != null, Modifier.width(cell), onClick = { onPickApp(null) }) {
                    if (app != null) ActionIcon(app, images, 40.dp) else GlyphIcon(Glyph.LAUNCH, Palette.Text, Modifier.size(36.dp))
                }
                // Поверх соседей: половины прячутся под этой плиткой
                Fixed(NavAction.Split(), Modifier.width(cell).zIndex(1f))
                val open = current is NavAction.Split
                val pieces = rememberStaggered(open, SPLIT_PIECES)
                val openNow by rememberUpdatedState(open)
                val shown by remember { derivedStateOf { openNow || pieces.any { it.value != 0f } } }
                Box(Modifier.weight(1f)) {
                    if (shown) {
                        val split = lastSplit[0] ?: NavAction.Split()
                        SplitSides(split, cell, pieces, labels, images, onPickApp, onSwap = { onPick(NavAction.Split(split.right, split.left)) })
                    }
                }
            }
        }
    }
}

private const val GRID_COLUMNS = 6
private val GRID_GAP = 12.dp
private val TOOL_WIDTH = 60.dp

/** Левая половина, «поменять местами», правая половина. */
private const val SPLIT_PIECES = 3

/**
 * Половины сплита рядом, как на экране. Они занимают три клетки справа от плитки «Сплит» шириной [cell] и выезжают
 * из-под неё: [pieces] — ход каждой из трёх частей, 0 — спрятана под плиткой, 1 — на месте.
 */
@Composable
private fun SplitSides(
    split: NavAction.Split,
    cell: Dp,
    pieces: List<Animatable<Float, AnimationVector1D>>,
    labels: Labels,
    images: Images,
    onPickApp: (Side) -> Unit,
    onSwap: () -> Unit,
) {
    val side = (cell * 3 - TOOL_WIDTH) / 2
    // Центр плитки «Сплит» от начала этого ряда и центры частей — отсюда путь каждой
    val origin = -GRID_GAP - cell / 2
    val centers = listOf(side / 2, side + GRID_GAP + TOOL_WIDTH / 2, side + GRID_GAP * 2 + TOOL_WIDTH + side / 2)
    fun Modifier.piece(i: Int) = emergeFrom(origin - centers[i], cell, pieces[i])
    Row(horizontalArrangement = Arrangement.spacedBy(GRID_GAP)) {
        SideTile(Side.LEFT, split.left, labels, images, Modifier.width(side).piece(0)) { onPickApp(Side.LEFT) }
        ToolButton(
            SwapIcon,
            "Поменять местами",
            Palette.Text,
            enabled = split.left != null || split.right != null,
            height = TILE_HEIGHT,
            modifier = Modifier.piece(1),
            onClick = onSwap,
        )
        SideTile(Side.RIGHT, split.right, labels, images, Modifier.width(side).piece(2)) { onPickApp(Side.RIGHT) }
    }
}

/**
 * Элемент выезжает из-под плитки шириной [fromWidth], центр которой на [offset] левее его собственного: при
 * [progress] 0 он, уменьшенный так, чтобы не выглядывать, лежит под ней, к 1 — на своём месте в полный размер.
 * Пружина проносит его чуть дальше — путь продолжается за 1, а размер нет.
 */
private fun Modifier.emergeFrom(offset: Dp, fromWidth: Dp, progress: Animatable<Float, AnimationVector1D>): Modifier = graphicsLayer {
    val p = progress.value
    translationX = offset.toPx() * (1f - p)
    val start = minOf(0.85f, fromWidth.toPx() * 0.9f / size.width)
    val s = start + (1f - start) * p.coerceAtMost(1f)
    scaleX = s
    scaleY = s
    // Проявляется сразу, пока ещё под плиткой, — снаружи видно только движение
    alpha = (p * 8f).coerceIn(0f, 1f)
}

/**
 * Ход [count] элементов, которые выезжают и возвращаются цепочкой с шагом [Motion.STAGGER]: туда первым трогается
 * последний (он дальше всех едет и ведёт), обратно — первый. Перебитый на полпути едет назад сразу, без паузы.
 */
@Composable
private fun rememberStaggered(open: Boolean, count: Int): List<Animatable<Float, AnimationVector1D>> {
    val items = remember { List(count) { Animatable(if (open) 1f else 0f) } }
    LaunchedEffect(open) {
        items.forEachIndexed { i, item ->
            launch {
                val atRest = item.value == if (open) 0f else 1f
                if (atRest) delay(Motion.STAGGER * if (open) count - 1 - i else i)
                if (open) item.animateTo(1f, Motion.emerge()) else item.animateTo(0f, Motion.exit())
            }
        }
    }
    return items
}

/** Как плитка действия: приложение половины или «+», какая половина — значком в углу. Пустая обведена: без неё сплита нет. */
@Composable
private fun SideTile(side: Side, app: NavAction.LaunchApp?, labels: Labels, images: Images, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, Motion.press(), label = "sidePress")
    val ring by animateColorAsState(if (app == null) Palette.Outline else Color.Transparent, Motion.change(), label = "sideRing")
    Box(
        modifier.height(TILE_HEIGHT).scale(scale).clip(TileShape).background(Palette.SurfaceHigh).border(2.dp, ring, TileShape)
            .clickable(interaction, ripple(), onClick = onClick),
    ) {
        GlyphIcon(if (side == Side.LEFT) Glyph.HALF_LEFT else Glyph.HALF_RIGHT, Palette.TextDim, Modifier.align(Alignment.TopStart).padding(8.dp).size(24.dp))
        AnimatedContent(app, Modifier.fillMaxSize().padding(8.dp), transitionSpec = { fadeThrough() }, label = "sideApp") { a ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    if (a != null) ActionIcon(a, images, 40.dp) else GlyphIcon(Glyph.PLUS, Palette.TextDim, Modifier.size(28.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (a != null) labels.action(a) else if (side == Side.LEFT) "Слева" else "Справа",
                    color = if (a != null) Palette.Text else Palette.TextDim,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ActionTile(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, Motion.press(), label = "tilePress")
    val bg by animateColorAsState(if (selected) Palette.AccentContainer else Palette.SurfaceHigh, Motion.change(), label = "tileBg")
    val ring by animateColorAsState(if (selected) Palette.Accent else Color.Transparent, Motion.change(), label = "tileRing")
    val text by animateColorAsState(if (selected) Palette.Accent else Palette.Text, Motion.change(), label = "tileText")
    Column(
        modifier.height(TILE_HEIGHT).scale(scale).clip(TileShape).background(bg).border(2.dp, ring, TileShape)
            .clickable(interaction, ripple(), onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Значок действия на тёмном фоне. Иконки панели нарисованы в ячейке 120×126 с глифом 48 — рисуем крупнее ячейки,
 * чтобы сам глиф был размером [size].
 */
@Composable
private fun ActionIcon(a: NavAction, images: Images, size: Dp) {
    when (a) {
        NavAction.None -> GlyphIcon(Glyph.NONE, Palette.TextDim, Modifier.size(size * 0.8f))
        is NavAction.LaunchApp -> images.get(IconRef.App(a.pkg, a.cls), night = false)?.let {
            Image(it.bitmap, null, Modifier.size(size))
        }
        else -> images.get(NavIcons.ref(PanelItem.Button(a)), night = true)?.let {
            Image(it.bitmap, null, Modifier.requiredSize(size * 2.5f), contentScale = ContentScale.Fit)
        }
    }
}

/** Вид кнопки: иконка — плитками на фоне панели (как будет на ГУ), масштаб — ползунком. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LookPanel(
    item: PanelItem.Button,
    images: Images,
    night: Boolean,
    onPickApp: () -> Unit,
    onIcon: (NavIcon) -> Unit,
    onScale: (Int) -> Unit,
) {
    Column {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IconChoice(item.icon == NavIcon.Auto, night, onClick = { onIcon(NavIcon.Auto) }, badge = "A") {
                ItemPicture(item.copy(icon = NavIcon.Auto, scale = 100), images, night, Modifier.fillMaxSize())
            }
            NavIcons.choices.forEach { c ->
                val icon = NavIcon.Named(c.name)
                IconChoice(item.icon == icon, night, onClick = { onIcon(icon) }) {
                    ItemPicture(item.copy(icon = icon, scale = 100), images, night, Modifier.fillMaxSize())
                }
            }
            IconChoice(item.icon is NavIcon.App, night, onClick = onPickApp) {
                if (item.icon is NavIcon.App) {
                    ItemPicture(item.copy(scale = 100), images, night, Modifier.fillMaxSize())
                } else {
                    GlyphIcon(Glyph.LAUNCH, panelFg(night), Modifier.size(28.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        ScaleRow(item.scale, onScale)
    }
}

@Composable
private fun IconChoice(selected: Boolean, night: Boolean, onClick: () -> Unit, badge: String? = null, content: @Composable () -> Unit) {
    val bg by animateColorAsState(panelBg(night), Motion.enter(), label = "choiceBg")
    val ring by animateColorAsState(
        when {
            selected -> Palette.Accent
            night -> Palette.Outline
            else -> Color.Transparent
        },
        Motion.change(),
        label = "choiceRing",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, Motion.press(), label = "choicePress")
    Box(
        Modifier.size(64.dp).scale(scale).clip(RoundedCornerShape(14.dp)).background(bg)
            .border(if (selected) 3.dp else 1.dp, ring, RoundedCornerShape(14.dp)).clickable(interaction, ripple(), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
        if (badge != null) {
            Text(
                badge,
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).background(Palette.Accent, CircleShape),
                color = Palette.OnAccent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        AnimatedVisibility(
            selected,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp),
            enter = scaleIn(Motion.move()) + fadeIn(Motion.enter()),
            exit = scaleOut(Motion.exit()) + fadeOut(Motion.exit()),
        ) { Box(Modifier.size(6.dp).background(Palette.Accent, CircleShape)) }
    }
}

/** Масштаб 30–200 % шагом 5: ползунок, по краям — маленькая и большая картинка; нажатие на число — 100 %. */
@Composable
private fun ScaleRow(scale: Int, onChange: (Int) -> Unit) {
    val range = PanelItem.SCALE_RANGE
    val step = PanelItem.SCALE_STEP
    Row(
        Modifier.fillMaxWidth().height(64.dp).clip(TileShape).background(Palette.SurfaceHigh).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.LOOK, Palette.TextDim, Modifier.size(18.dp))
        Slider(
            value = scale.toFloat(),
            onValueChange = { onChange(((it / step).roundToInt() * step).coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / step - 1,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            colors = SliderDefaults.colors(
                thumbColor = Palette.Accent,
                activeTrackColor = Palette.Accent,
                inactiveTrackColor = Palette.SurfaceHighest,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        GlyphIcon(Glyph.LOOK, Palette.TextDim, Modifier.size(30.dp))
        Text(
            "$scale %",
            Modifier.width(96.dp).clip(CircleShape).clickable { onChange(PanelItem.SCALE_DEFAULT) }.padding(vertical = 10.dp),
            color = if (scale == PanelItem.SCALE_DEFAULT) Palette.Text else Palette.Accent,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}
