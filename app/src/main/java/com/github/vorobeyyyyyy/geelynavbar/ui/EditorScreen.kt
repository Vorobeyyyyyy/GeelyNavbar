package com.github.vorobeyyyyyy.geelynavbar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.github.vorobeyyyyyy.geelynavbar.NavAction
import com.github.vorobeyyyyyy.geelynavbar.PanelItem
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Ширина колонки к высоте у превью: на ГУ 120×720 dp плюс рамка под ручки отступов. */
private const val PANEL_ASPECT = 0.17f

/** Сколько висит «Вернуть». */
private const val UNDO_MS = 3000L

/**
 * Экран настройки — «список и подробности»: слева копия панели (рядом с настоящей на ГУ) и лоток,
 * справа — выбранный пункт. Глобальное (вкл/выкл, статус, применить) — отдельной строкой сверху.
 */
@Composable
internal fun EditorScreen(
    editor: EditorState,
    status: HookStatus,
    applying: Boolean,
    prefsShared: Boolean,
    labels: Labels,
    images: Images,
    presets: PresetStore,
    initialNight: Boolean,
    showHint: Boolean,
    onHintShown: () -> Unit,
    onApply: () -> Unit,
    onPing: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val drag = remember { DragState(editor, scope) }
    var night by remember { mutableStateOf(initialNight) }
    var picking by remember { mutableStateOf<Pick?>(null) }
    var statusOpen by remember { mutableStateOf(false) }
    var presetsOpen by remember { mutableStateOf(false) }

    // Вместо «Вы уверены?» — сразу делаем и даём вернуть. 3 с: у Material только 4 и 10 — показываем бессрочно
    // и снимаем сами (отмена showSnackbar убирает снэкбар)
    fun undoable(message: String, undo: () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = withTimeoutOrNull(UNDO_MS) {
                snackbar.showSnackbar(message, actionLabel = "Вернуть", duration = SnackbarDuration.Indefinite)
            }
            if (r == SnackbarResult.ActionPerformed) undo()
        }
    }
    drag.onLift = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    drag.onTick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    drag.onRemoved = { before -> undoable("Убрано с панели") { editor.restore(before) } }
    BackHandler(presetsOpen) { presetsOpen = false }

    LaunchedEffect(Unit) {
        if (showHint) {
            onHintShown()
            snackbar.showSnackbar("Удерживайте пункт на панели, чтобы перенести или убрать", duration = SnackbarDuration.Long)
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.Bg)) {
        Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Слева — панель и лоток: здесь удержание берёт пункт в руку
            var origin by remember { mutableStateOf(Offset.Zero) }
            Row(
                Modifier.fillMaxHeight()
                    .onGloballyPositioned { origin = it.positionInRoot() }
                    .pointerInput(drag) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { drag.start(it + origin) },
                            onDrag = { change, delta ->
                                if (drag.session != null) {
                                    change.consume()
                                    drag.dragBy(delta)
                                }
                            },
                            onDragEnd = { drag.release() },
                            onDragCancel = { drag.release(cancel = true) },
                        )
                    },
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Выбрали или добавили пункт — значит, будут его настраивать: пресеты уступают место
                PanelReplica(editor, drag, images, night, onSelect = { presetsOpen = false }, Modifier.fillMaxHeight().aspectRatio(PANEL_ASPECT))
                Tray(
                    editor, drag, images, night,
                    onAdded = { presetsOpen = false },
                    onToggleNight = { night = !night },
                    Modifier.fillMaxHeight().width(96.dp),
                )
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    MasterSwitch(editor.enabled) { editor.enabled = it }
                    Spacer(Modifier.width(12.dp))
                    PresetsButton(presetsOpen) { presetsOpen = !presetsOpen }
                    Spacer(Modifier.weight(1f))
                    StatusChip(status, prefsShared) { statusOpen = true }
                    AnimatedVisibility(
                        editor.dirty && !applying,
                        enter = fadeIn(Motion.enter()) + expandHorizontally(Motion.enter()) + scaleIn(Motion.enter(), 0.6f),
                        exit = fadeOut(Motion.exit()) + shrinkHorizontally(Motion.exit()) + scaleOut(Motion.exit(), 0.6f),
                    ) {
                        RoundIconButton(onClick = { editor.load(editor.saved) }, modifier = Modifier.padding(start = 12.dp)) {
                            Icon(UndoIcon, "Отменить изменения", tint = Palette.Text)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    ApplyButton(editor.dirty, applying, onApply)
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Inspector(
                        editor, labels, images, night,
                        onPickApp = { picking = it },
                        onRemove = { id ->
                            val before = editor.snapshot()
                            editor.remove(id)
                            undoable("Убрано с панели") { editor.restore(before) }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    SlideInFromEnd(presetsOpen) {
                        PresetPanel(
                            presets, editor, images, night,
                            onClose = { presetsOpen = false },
                            onApply = { p ->
                                val before = editor.snapshot()
                                editor.loadPanel(p.panel)
                                undoable("Пресет «${p.name}»") { editor.restore(before) }
                            },
                            onDelete = { p ->
                                val at = presets.remove(p)
                                undoable("Пресет «${p.name}» удалён") { presets.insert(at, p) }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        DragGhost(drag, images, night)

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomEnd).padding(28.dp).widthIn(max = 640.dp)) { data ->
            Snackbar(
                data,
                shape = RoundedCornerShape(16.dp),
                containerColor = Palette.SurfaceHighest,
                contentColor = Palette.Text,
                actionColor = Palette.Accent,
            )
        }

        Overlay(statusOpen, { statusOpen = false }, Modifier.padding(top = 84.dp, end = 16.dp).widthIn(max = 520.dp), Alignment.TopEnd, scrim = 0.3f) {
            StatusDetails(status, prefsShared, onPing = { onPing() })
        }

        val target = editor.selected
        val pick = picking
        val current = pick?.let { (target?.item as? PanelItem.Button)?.app(it)?.pkg }
        val title = when (pick?.side) {
            Side.LEFT -> "Слева"
            Side.RIGHT -> "Справа"
            null -> "Приложение"
        }
        AppPicker(pick != null, labels, images, current, title, onDismiss = { picking = null }) { app ->
            val id = target?.id
            if (id != null && pick != null) {
                editor.update(id) { (it as? PanelItem.Button)?.withApp(pick, NavAction.LaunchApp(app.pkg, app.cls)) ?: it }
            }
            picking = null
        }
    }
}

/** Главный выключатель: настроенная панель или заводская. Крупный и отдельно от настроек пункта. */
@Composable
private fun MasterSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val bg by animateColorAsState(if (on) Palette.AccentContainer else Palette.SurfaceHigh, Motion.change(), label = "masterBg")
    Row(
        Modifier.height(56.dp).clip(CircleShape).background(bg).clickable { onChange(!on) }.padding(start = 12.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(
            checked = on,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.OnAccent,
                checkedTrackColor = Palette.Accent,
                uncheckedThumbColor = Palette.TextDim,
                uncheckedTrackColor = Palette.SurfaceHighest,
                uncheckedBorderColor = Palette.Outline,
            ),
        )
        Spacer(Modifier.width(12.dp))
        // Подпись — состояние, как у главных переключателей настроек Android 9 на ГУ
        AnimatedContent(on, transitionSpec = { fadeThrough() }, label = "masterText") {
            Text(if (it) "Включено" else "Выключено", style = MaterialTheme.typography.titleMedium)
        }
    }
}

private class StatusView(val color: Color, val short: String, val details: String)

private fun statusView(s: HookStatus, prefsShared: Boolean): StatusView {
    val v = when (s) {
        HookStatus.Waiting -> StatusView(Palette.TextDim, "Проверка…", "Жду ответа модуля из SystemUI…")
        HookStatus.NoAnswer -> StatusView(
            Palette.Bad,
            "Нет связи",
            "Модуль не отвечает. Включите его в Vector (скоуп com.android.systemui) и перезапустите SystemUI.",
        )
        is HookStatus.Alive -> when {
            s.error != null -> StatusView(Palette.Bad, "Ошибка", "Панель не собралась, оставлена заводская:\n${s.error}")
            !s.prefsOk -> StatusView(Palette.Warn, "Нет настроек", "Модуль ${s.version} работает, но не видит настройки.")
            s.applied < 0 -> StatusView(Palette.Warn, "Ждёт панель", "Модуль ${s.version} загружен, панель ещё не создана.")
            s.applied == 0 -> StatusView(Palette.Good, "Работает", "Модуль ${s.version} работает, на экране заводская панель.")
            else -> StatusView(Palette.Good, "Работает", "Модуль ${s.version} работает, пунктов на панели — ${s.applied}.")
        }
    }
    if (prefsShared) return v
    return StatusView(Palette.Warn, "Нет в Vector", v.details + "\n\nМодуль не включён в Vector: хук не увидит настройки.")
}

@Composable
private fun StatusChip(status: HookStatus, prefsShared: Boolean, onClick: () -> Unit) {
    val v = statusView(status, prefsShared)
    val color by animateColorAsState(v.color, Motion.change(), label = "statusColor")
    Row(
        Modifier.height(56.dp).clip(CircleShape).background(Palette.SurfaceHigh).clickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseDot(color, pulsing = status == HookStatus.Waiting)
        Spacer(Modifier.width(10.dp))
        AnimatedContent(v.short, transitionSpec = { fadeThrough() }, label = "statusText") {
            Text(it, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Точка статуса; пока ждём ответа — дышит. */
@Composable
private fun PulseDot(color: Color, pulsing: Boolean) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(1f, 0.3f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulseAlpha")
    Box(Modifier.size(12.dp).graphicsLayer { alpha = if (pulsing) a else 1f }.background(color, CircleShape))
}

@Composable
private fun StatusDetails(status: HookStatus, prefsShared: Boolean, onPing: () -> Unit) {
    val v = statusView(status, prefsShared)
    Column(Modifier.clip(RoundedCornerShape(20.dp)).background(Palette.SurfaceHigh).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulseDot(v.color, pulsing = status == HookStatus.Waiting)
            Spacer(Modifier.width(10.dp))
            Text(v.short, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(10.dp))
        Text(v.details, color = Palette.TextDim, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.height(56.dp).clip(CircleShape).background(Palette.SurfaceHighest).clickable(onClick = onPing).padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Проверить", color = Palette.Accent, style = MaterialTheme.typography.titleMedium) }
    }
}

/** «Применить» всегда на месте и показывает, что с настройками: есть что применить → применяю → применено. */
@Composable
private fun ApplyButton(dirty: Boolean, applying: Boolean, onClick: () -> Unit) {
    val state = when {
        applying -> 1
        dirty -> 0
        else -> 2
    }
    val active = state != 2
    val bg by animateColorAsState(if (active) Palette.Accent else Palette.SurfaceHigh, Motion.change(300), label = "applyBg")
    val fg by animateColorAsState(if (active) Palette.OnAccent else Palette.TextDim, Motion.change(300), label = "applyFg")
    Box(
        Modifier.height(56.dp).clip(CircleShape).background(bg).clickable(enabled = state == 0, onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            state,
            transitionSpec = { fadeThrough() },
            label = "apply",
        ) { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (s == 1) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = fg, strokeWidth = 3.dp)
                } else {
                    Icon(Icons.Filled.Check, null, tint = fg, modifier = Modifier.alpha(if (s == 2) 0.7f else 1f))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    when (s) {
                        0 -> "Применить"
                        1 -> "Применяю"
                        else -> "Применено"
                    },
                    color = fg,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/** Слой, который въезжает справа поверх соседнего (пресеты поверх настроек пункта). */
@Composable
private fun SlideInFromEnd(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible,
        enter = slideInHorizontally(Motion.enter()) { it / 4 } + fadeIn(Motion.enter()),
        exit = slideOutHorizontally(Motion.exit()) { it / 4 } + fadeOut(Motion.exit()),
    ) { content() }
}

/** Пресеты — переключатель: открыты — подсвечен, как выключатель «Своя панель». */
@Composable
private fun PresetsButton(open: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (open) Palette.AccentContainer else Palette.SurfaceHigh, Motion.change(), label = "presetsBg")
    val fg by animateColorAsState(if (open) Palette.Accent else Palette.Text, Motion.change(), label = "presetsFg")
    Row(
        Modifier.height(56.dp).clip(CircleShape).background(bg).clickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.PRESETS, fg, Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text("Пресеты", color = fg, style = MaterialTheme.typography.titleMedium)
    }
}
