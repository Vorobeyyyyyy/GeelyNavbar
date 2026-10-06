package com.github.vorobeyyyyyy.geelynavbar.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Свои значки интерфейса: в material-icons-core их нет, а тянуть extended ради пяти штук незачем. */
internal enum class Glyph { TAP, HOLD, LOOK, NONE, PLUS, MINUS, GRIP, LAUNCH, PRESETS, HALF_LEFT, HALF_RIGHT, PAD_TOP, PAD_BOTTOM }

@Composable
internal fun GlyphIcon(glyph: Glyph, color: Color, modifier: Modifier = Modifier.size(24.dp)) {
    // Отдельный слой: значки вырезают зазоры (BlendMode.Clear) только в себе
    Canvas(modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) { drawGlyph(glyph, color) }
}

private fun DrawScope.drawGlyph(g: Glyph, c: Color) {
    val s = size.minDimension
    val stroke = s * 0.09f
    fun p(x: Float, y: Float) = Offset(center.x + (x - 0.5f) * s, center.y + (y - 0.5f) * s)
    when (g) {
        // Касание: точка и круг-«волна»
        Glyph.TAP -> {
            drawCircle(c, s * 0.13f)
            drawCircle(c, s * 0.36f, style = Stroke(stroke))
        }
        // Удержание: точка и круг, который заполняется, как таймер
        Glyph.HOLD -> {
            drawCircle(c, s * 0.13f)
            drawCircle(c.copy(alpha = c.alpha * 0.3f), s * 0.36f, style = Stroke(stroke))
            drawArc(c, -90f, 250f, false, p(0.14f, 0.14f), Size(s * 0.72f, s * 0.72f), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        // Вид: картинка — рамка, горы, солнце
        Glyph.LOOK -> {
            drawRoundRect(c, p(0.12f, 0.2f), Size(s * 0.76f, s * 0.6f), CornerRadius(s * 0.1f), style = Stroke(stroke))
            val m = Path().apply {
                moveTo(p(0.24f, 0.7f).x, p(0.24f, 0.7f).y)
                lineTo(p(0.42f, 0.46f).x, p(0.42f, 0.46f).y)
                lineTo(p(0.56f, 0.62f).x, p(0.56f, 0.62f).y)
                lineTo(p(0.65f, 0.52f).x, p(0.65f, 0.52f).y)
                lineTo(p(0.77f, 0.7f).x, p(0.77f, 0.7f).y)
                close()
            }
            drawPath(m, c)
            drawCircle(c, s * 0.06f, p(0.64f, 0.35f))
        }
        Glyph.NONE -> {
            drawCircle(c, s * 0.34f, style = Stroke(stroke))
            drawLine(c, p(0.26f, 0.74f), p(0.74f, 0.26f), stroke, StrokeCap.Round)
        }
        Glyph.PLUS -> {
            drawLine(c, p(0.5f, 0.2f), p(0.5f, 0.8f), stroke * 1.2f, StrokeCap.Round)
            drawLine(c, p(0.2f, 0.5f), p(0.8f, 0.5f), stroke * 1.2f, StrokeCap.Round)
        }
        Glyph.MINUS -> drawLine(c, p(0.2f, 0.5f), p(0.8f, 0.5f), stroke * 1.2f, StrokeCap.Round)
        // Ручка перетаскивания: 2×3 точки
        Glyph.GRIP -> for (x in listOf(0.38f, 0.62f)) for (y in listOf(0.28f, 0.5f, 0.72f)) drawCircle(c, s * 0.07f, p(x, y))
        // Пресеты: стопка наборов — задний контуром, передний закрашен, между ними зазор
        Glyph.PRESETS -> {
            val r = CornerRadius(s * 0.12f)
            drawRoundRect(c, p(0.3f, 0.12f), Size(s * 0.58f, s * 0.58f), r, style = Stroke(stroke))
            drawRoundRect(Color.Black, p(0.12f - 0.07f, 0.3f - 0.07f), Size(s * 0.72f, s * 0.72f), CornerRadius(s * 0.18f), blendMode = BlendMode.Clear)
            drawRoundRect(c, p(0.12f, 0.3f), Size(s * 0.58f, s * 0.58f), r)
        }
        // Запуск приложения: плитка с плюсом
        Glyph.LAUNCH -> {
            drawRoundRect(c, p(0.14f, 0.14f), Size(s * 0.72f, s * 0.72f), CornerRadius(s * 0.2f), style = Stroke(stroke))
            drawLine(c, p(0.5f, 0.34f), p(0.5f, 0.66f), stroke, StrokeCap.Round)
            drawLine(c, p(0.34f, 0.5f), p(0.66f, 0.5f), stroke, StrokeCap.Round)
        }
        // Половина сплита: экран контуром, нужная половина закрашена
        Glyph.HALF_LEFT, Glyph.HALF_RIGHT -> {
            drawRoundRect(c, p(0.08f, 0.22f), Size(s * 0.84f, s * 0.56f), CornerRadius(s * 0.1f), style = Stroke(stroke))
            val x = if (g == Glyph.HALF_LEFT) 0.16f else 0.54f
            drawRoundRect(c, p(x, 0.32f), Size(s * 0.3f, s * 0.36f), CornerRadius(s * 0.04f))
        }
        // Отступ: колонка панели контуром, пустое место сверху или снизу закрашено, между ними — пункты точками
        Glyph.PAD_TOP, Glyph.PAD_BOTTOM -> {
            val top = g == Glyph.PAD_TOP
            drawRoundRect(c, p(0.3f, 0.06f), Size(s * 0.4f, s * 0.88f), CornerRadius(s * 0.1f), style = Stroke(stroke))
            drawRoundRect(c, p(0.3f, if (top) 0.06f else 0.68f), Size(s * 0.4f, s * 0.26f), CornerRadius(s * 0.1f))
            for (y in if (top) listOf(0.45f, 0.6f, 0.75f) else listOf(0.25f, 0.4f, 0.55f)) drawCircle(c, s * 0.045f, p(0.5f, y))
        }
    }
}

/** Солнце, которое перетекает в луну: превью панели днём или ночью. */
@Composable
internal fun SunMoon(night: Boolean, color: Color, modifier: Modifier = Modifier.size(28.dp)) {
    val t by animateFloatAsState(if (night) 1f else 0f, Motion.enter(400), label = "sunMoon")
    Canvas(modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val s = size.minDimension
        val r = s * (0.2f + 0.12f * t)
        // Лучи прячутся, поворачиваясь
        rotate(t * 90f) {
            for (i in 0 until 8) rotate(i * 45f) {
                drawLine(
                    color.copy(alpha = 1f - t),
                    Offset(center.x, center.y - s * 0.33f),
                    Offset(center.x, center.y - s * 0.46f),
                    s * 0.08f,
                    StrokeCap.Round,
                )
            }
        }
        drawCircle(color, r)
        // Тень, вырезающая серп
        drawCircle(Color.Black, r * 0.86f * t, center + Offset(r * 0.5f, -r * 0.42f), blendMode = BlendMode.DstOut)
    }
}

/** «Поменять местами» — значок swap_horiz из Material. */
internal val SwapIcon: ImageVector = ImageVector.Builder("swap", 24.dp, 24.dp, 24f, 24f).addPath(
    addPathNodes("M6.99,11L3,15l3.99,4v-3H14v-2H6.99v-3zM21,9l-3.99,-4v3H10v2h7.01v3L21,9z"),
    fill = SolidColor(Color.Black),
).build()

/** «Вернуть как было» — значок undo из Material. */
internal val UndoIcon: ImageVector = ImageVector.Builder("undo", 24.dp, 24.dp, 24f, 24f).addPath(
    addPathNodes("M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z"),
    fill = SolidColor(Color.Black),
).build()
