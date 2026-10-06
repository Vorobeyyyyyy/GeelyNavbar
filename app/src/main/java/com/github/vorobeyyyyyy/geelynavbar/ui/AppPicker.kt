package com.github.vorobeyyyyyy.geelynavbar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.vorobeyyyyyy.geelynavbar.IconRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Слой поверх экрана: затемнение (нажатие по нему или «Назад» — закрыть) и карточка, которая вырастает
 * из центра. Свой, а не Dialog: окно диалога сбросило бы масштаб под ГУ и не дало бы так анимировать.
 */
@Composable
internal fun Overlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    scrim: Float = 0.6f,
    content: @Composable () -> Unit,
) {
    BackHandler(visible, onDismiss)
    AnimatedVisibility(visible, enter = fadeIn(Motion.enter(200)), exit = fadeOut(Motion.exit(150))) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = alignment,
        ) {
            Box(
                modifier
                    .animateEnterExit(enter = scaleIn(Motion.enter(), 0.92f), exit = scaleOut(Motion.exit(), 0.96f))
                    // Нажатия внутри карточки её не закрывают
                    .pointerInput(Unit) { detectTapGestures { } },
            ) { content() }
        }
    }
}

/** Выбор приложения — сеткой иконок, как на рабочем столе. */
@Composable
internal fun AppPicker(
    visible: Boolean,
    labels: Labels,
    images: Images,
    selected: String?,
    title: String,
    onDismiss: () -> Unit,
    onPick: (AppEntry) -> Unit,
) {
    Overlay(visible, onDismiss, Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 20.dp)) {
        val apps by produceState<List<AppEntry>?>(null) { value = withContext(Dispatchers.IO) { labels.launchableApps() } }
        Column(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(Palette.SurfaceLow).padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).padding(start = 8.dp))
                RoundIconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Закрыть", tint = Palette.Text) }
            }
            Spacer(Modifier.height(12.dp))
            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.Accent) }
            } else {
                LazyVerticalGrid(
                    GridCells.Adaptive(128.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list, key = { it.pkg + "/" + it.cls }) { app ->
                        AppTile(app, images, app.pkg == selected) { onPick(app) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppTile(app: AppEntry, images: Images, selected: Boolean, onClick: () -> Unit) {
    val pic by produceState<Images.Picture?>(null, app) {
        value = withContext(Dispatchers.IO) { images.get(IconRef.App(app.pkg, app.cls), night = false) }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (selected) Palette.AccentContainer else Color.Transparent)
            .clickable(onClick = onClick).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp)) {
            Crossfade(pic, animationSpec = Motion.change(), label = "appIcon") { p ->
                if (p != null) Image(p.bitmap, null, Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            app.label,
            color = if (selected) Palette.Accent else Palette.Text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            minLines = 2,
        )
    }
}
