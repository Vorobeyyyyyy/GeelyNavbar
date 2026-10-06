package com.github.vorobeyyyyyy.geelynavbar.ui

import android.content.SharedPreferences
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.vorobeyyyyyy.geelynavbar.NavAction
import com.github.vorobeyyyyyy.geelynavbar.NavConfig
import com.github.vorobeyyyyyy.geelynavbar.NavIcon
import com.github.vorobeyyyyyy.geelynavbar.Panel
import com.github.vorobeyyyyyy.geelynavbar.PanelItem
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Готовая раскладка панели: пункты и отступы колонки. Встроенные не удаляются и не переименовываются. */
internal data class Preset(val id: String, val name: String, val panel: Panel, val builtIn: Boolean = false)

/**
 * Свои пресеты — в настройках приложения (хуку они не нужны), новые первыми. Раскладка хранится тем же JSON,
 * что и конфиг ([NavConfig]), так что разбор такой же прощающий.
 */
internal class PresetStore(private val prefs: SharedPreferences) {
    val user = mutableStateListOf<Preset>()
    private var next = 1

    init {
        load()
    }

    /** Сохранить [panel] как «Набор N» — имя можно сменить потом. */
    fun save(panel: Panel): Preset {
        val p = Preset("u$next", "Набор $next", panel)
        next++
        user.add(0, p)
        persist()
        return p
    }

    /** Убрать; возвращает место — для «Вернуть». */
    fun remove(p: Preset): Int {
        val i = user.indexOfFirst { it.id == p.id }
        if (i >= 0) {
            user.removeAt(i)
            persist()
        }
        return i
    }

    fun insert(at: Int, p: Preset) {
        user.add(at.coerceIn(0, user.size), p)
        persist()
    }

    fun rename(p: Preset, name: String) {
        val i = user.indexOfFirst { it.id == p.id }
        val n = name.trim()
        if (i < 0 || n.isEmpty() || n == user[i].name) return
        user[i] = user[i].copy(name = n)
        persist()
    }

    private fun load() {
        val raw = prefs.getString(KEY, null) ?: return
        val root = try {
            JSONObject(raw)
        } catch (_: JSONException) {
            return
        }
        next = root.optInt("next", 1)
        val arr = root.optJSONArray("presets") ?: return
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val panel = NavConfig.fromJson(o.optJSONObject("panel")?.toString())?.panel ?: continue
            user += Preset(o.optString("id", "u${next++}"), o.optString("name", "Набор"), panel)
        }
    }

    private fun persist() {
        val arr = JSONArray(user.map {
            JSONObject().put("id", it.id).put("name", it.name).put("panel", JSONObject(NavConfig(true, it.panel).toJson()))
        })
        prefs.edit().putString(KEY, JSONObject().put("next", next).put("presets", arr).toString()).apply()
    }

    companion object {
        private const val KEY = "presets"
        private val NAVI = NavAction.LaunchApp("ru.yandex.yandexnavi")

        /**
         * Как мод панели SX11 из сети (стоит на ГУ) — заводская плюс Карта и Назад: Карта (Яндекс Навигатор, долгое — сплит),
         * Авто, климат, Приложения (долгое — недавние), Домой, Назад. Отступы — по замерам на ГУ. В сплите навигатор
         * слева, правую половину выбирает пользователь: у мода её выбирала прошивка.
         */
        private val MOD = Panel(
            listOf(
                PanelItem.Button(NAVI, NavAction.Split(left = NAVI), NavIcon.Named("nb_map")),
                PanelItem.Button(NavAction.CarSettings),
                PanelItem.HvacWidget(),
                PanelItem.Button(NavAction.AppPane, NavAction.Recents),
                PanelItem.Button(NavAction.Home),
                PanelItem.Button(NavAction.Back),
            ),
            paddingTop = 23,
            paddingBottom = 34,
        )

        val BUILT_IN = listOf(
            Preset("stock", "Заводской", Panel.STOCK, builtIn = true),
            Preset("mod", "Расширенный", MOD, builtIn = true),
        )
    }
}

private val CardShape = RoundedCornerShape(20.dp)
private val CARD_WIDTH = 148.dp
private val MINI_WIDTH = 72.dp
private const val MINI_ZOOM = 1.7f

/**
 * Пресеты — на месте настроек пункта, копия панели слева остаётся видна: нажали пресет — она тут же
 * перестраивается. Первая карточка — сохранить текущий набор.
 */
@Composable
internal fun PresetPanel(
    store: PresetStore,
    editor: EditorState,
    images: Images,
    night: Boolean,
    onClose: () -> Unit,
    onApply: (Preset) -> Unit,
    onDelete: (Preset) -> Unit,
    modifier: Modifier,
) {
    val current = editor.current().panel
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(Palette.Surface).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(Glyph.PRESETS, Palette.Accent, Modifier.padding(start = 8.dp).size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text("Пресеты", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            RoundIconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Закрыть", tint = Palette.Text) }
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(
            Modifier.weight(1f).fillMaxWidth(),
            state = list,
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "save") {
                val saved = (store.user + PresetStore.BUILT_IN).any { it.panel == current }
                SaveCard(current, saved, images, night) {
                    store.save(current)
                    scope.launch { list.animateScrollToItem(0) }
                }
            }
            items(store.user, key = { it.id }) { p ->
                PresetCard(
                    p, p.panel == current, images, night,
                    onApply = onApply,
                    onDelete = onDelete,
                    onRename = { store.rename(p, it) },
                    modifier = Modifier.animateItem(fadeInSpec = Motion.enter(), placementSpec = Motion.move(), fadeOutSpec = Motion.exit()),
                )
            }
            item(key = "divider") {
                Box(Modifier.fillMaxHeight().padding(vertical = 32.dp).width(1.dp).background(Palette.Outline))
            }
            items(PresetStore.BUILT_IN, key = { it.id }) { p ->
                PresetCard(p, p.panel == current, images, night, onApply = onApply, onDelete = null, onRename = null)
            }
        }
    }
}

/** Карточка «сохранить текущий»: пунктирная, внутри — то, что сейчас на панели. Уже сохранённый набор не дублируем. */
@Composable
private fun SaveCard(current: Panel, saved: Boolean, images: Images, night: Boolean, onSave: () -> Unit) {
    val alpha by animateFloatAsState(if (saved) 0.5f else 1f, Motion.change(), label = "saveAlpha")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, Motion.press(), label = "savePress")
    Column(
        Modifier.width(CARD_WIDTH).fillMaxHeight().scale(scale).clip(CardShape)
            .drawBehind {
                drawRoundRect(
                    Palette.Accent.copy(alpha = alpha),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                )
            }
            .clickable(interaction, ripple(), enabled = !saved, onClick = onSave)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.BottomEnd) {
            MiniPanel(current, images, night, Modifier.fillMaxHeight().width(MINI_WIDTH).alpha(alpha))
            // Значок на углу мини-панели, как у аватарки: плюс — сохранить, галочка — уже есть
            AnimatedContent(
                saved,
                Modifier.offset(x = 16.dp, y = 10.dp),
                transitionSpec = { Motion.throughIn() + Motion.throughScaleIn(0.6f) togetherWith Motion.throughOut() },
                label = "saveBadge",
            ) { s ->
                Box(Modifier.size(36.dp).background(if (s) Palette.SurfaceHighest else Palette.Accent, CircleShape), contentAlignment = Alignment.Center) {
                    if (s) {
                        Icon(Icons.Filled.Check, null, tint = Palette.Accent, modifier = Modifier.size(20.dp))
                    } else {
                        GlyphIcon(Glyph.PLUS, Palette.OnAccent, Modifier.size(18.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (saved) "Сохранён" else "Сохранить текущий",
            color = Palette.Accent,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
        )
    }
}

@Composable
private fun PresetCard(
    p: Preset,
    active: Boolean,
    images: Images,
    night: Boolean,
    onApply: (Preset) -> Unit,
    onDelete: ((Preset) -> Unit)?,
    onRename: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(if (active) Palette.AccentContainer else Palette.SurfaceHigh, Motion.change(), label = "presetBg")
    val ring by animateColorAsState(if (active) Palette.Accent else Color.Transparent, Motion.change(), label = "presetRing")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, Motion.press(), label = "presetPress")
    Box(modifier.width(CARD_WIDTH).fillMaxHeight().scale(scale)) {
        Column(
            Modifier.fillMaxSize().clip(CardShape).background(bg).border(2.dp, ring, CardShape)
                .clickable(interaction, ripple()) { onApply(p) }.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MiniPanel(p.panel, images, night, Modifier.weight(1f).width(MINI_WIDTH))
            Spacer(Modifier.height(10.dp))
            PresetName(p.name, active, onRename)
        }
        if (onDelete != null) {
            Box(
                Modifier.align(Alignment.TopEnd).size(48.dp).clip(CircleShape).clickable { onDelete(p) },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(28.dp).background(Palette.SurfaceHighest, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, "Удалить", tint = Palette.TextDim, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Имя пресета; своё — с карандашом: нажатие — правка на месте, «Готово» или уход фокуса — сохранить. */
@Composable
private fun PresetName(name: String, active: Boolean, onRename: ((String) -> Unit)?) {
    var editing by remember { mutableStateOf(false) }
    val color = if (active) Palette.Accent else Palette.Text
    val style = MaterialTheme.typography.bodyLarge
    if (editing && onRename != null) {
        var value by remember { mutableStateOf(TextFieldValue(name, TextRange(0, name.length))) }
        var focused by remember { mutableStateOf(false) }
        val focus = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        fun commit() {
            if (!editing) return
            editing = false
            onRename(value.text)
            focusManager.clearFocus()
        }
        BasicTextField(
            value,
            { value = it.copy(text = it.text.take(24)) },
            Modifier.fillMaxWidth().heightIn(min = 48.dp).focusRequester(focus)
                // Аппаратный Enter забираем себе целиком и завершаем правку на отпускании: иначе отпускание уйдёт
                // тому, кто получит фокус следующим (карточке, пункту панели), и сработает как нажатие
                .onPreviewKeyEvent {
                    if (it.key != Key.Enter && it.key != Key.NumPadEnter) return@onPreviewKeyEvent false
                    if (it.type == KeyEventType.KeyUp) commit()
                    true
                }
                .onFocusChanged {
                    if (it.isFocused) focused = true else if (focused) commit()
                }
                .drawBehind {
                    val y = size.height - 1.dp.toPx()
                    drawLine(Palette.Accent, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                }
                .padding(vertical = 10.dp),
            textStyle = style.copy(color = Palette.Text, textAlign = TextAlign.Center),
            singleLine = true,
            cursorBrush = SolidColor(Palette.Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
        )
        LaunchedEffect(Unit) { focus.requestFocus() }
    } else {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                .then(if (onRename != null) Modifier.clickable { editing = true } else Modifier),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name,
                Modifier.weight(1f, fill = false),
                color = color,
                style = style,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (onRename != null) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.Edit, "Переименовать", tint = Palette.TextDim, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * Панель в миниатюре: те же пункты и отступы, что на ГУ. Глиф штатной иконки — меньше половины ячейки,
 * в миниатюре он был бы точкой, поэтому картинки увеличены в своих ячейках.
 */
@Composable
private fun MiniPanel(panel: Panel, images: Images, night: Boolean, modifier: Modifier) {
    val bg by animateColorAsState(panelBg(night), Motion.enter(), label = "miniBg")
    val edge by animateColorAsState(if (night) Palette.Outline else Color.Transparent, Motion.enter(), label = "miniEdge")
    BoxWithConstraints(modifier.clip(RoundedCornerShape(12.dp)).background(bg).border(1.dp, edge, RoundedCornerShape(12.dp))) {
        val k = maxHeight / 720f
        Column(Modifier.fillMaxSize().padding(top = k * panel.paddingTop, bottom = k * panel.paddingBottom)) {
            panel.items.forEach { item ->
                ItemPicture(
                    item, images, night,
                    Modifier.weight(1f).fillMaxWidth().clipToBounds().graphicsLayer { scaleX = MINI_ZOOM; scaleY = MINI_ZOOM },
                )
            }
        }
    }
}
