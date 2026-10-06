package com.github.vorobeyyyyyy.geelynavbar.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

object Palette {
    val Bg = Color(0xFF0D1115)
    val SurfaceLow = Color(0xFF12171C)
    val Surface = Color(0xFF171D23)
    val SurfaceHigh = Color(0xFF1F272F)
    val SurfaceHighest = Color(0xFF29333C)
    val Outline = Color(0xFF34404B)
    val Accent = Color(0xFF3DD6C3)
    val OnAccent = Color(0xFF00201C)
    val AccentContainer = Color(0xFF113B36)
    val Text = Color(0xFFE8EEF2)
    val TextDim = Color(0xFF8E9BA7)
    val Good = Color(0xFF4ADE80)
    val Warn = Color(0xFFFBBF24)
    val Bad = Color(0xFFF87171)
    val BadContainer = Color(0xFF3A1D20)

    /** Фон штатной панели на ГУ днём и ночью — для превью. */
    val PanelDay = Color(0xFFE2EAF3)
    val PanelNight = Color(0xFF14171C)
}

/** Движение по токенам Material 3: появление — 300 мс с замедлением, уход — 200 мс с разгоном, за пальцем — пружины. */
object Motion {
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    const val SHORT = 150
    const val MEDIUM = 300
    const val EXIT = 200

    fun <T> enter(ms: Int = MEDIUM): FiniteAnimationSpec<T> = tween(ms, easing = EmphasizedDecelerate)

    fun <T> exit(ms: Int = EXIT): FiniteAnimationSpec<T> = tween(ms, easing = EmphasizedAccelerate)

    /** Смена состояния на месте: цвет, выделение. */
    fun <T> change(ms: Int = SHORT): FiniteAnimationSpec<T> = tween(ms, easing = Emphasized)

    /** Перестановки и всё, что тянут пальцем: пружина не дёргается, если её перебили на полпути. */
    fun <T> move(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

    /** Отклик на нажатие. */
    fun <T> press(): AnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)

    /** Выезд из-под другого элемента: пружина чуть проносит по инерции и возвращает. */
    fun <T> emerge(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.72f, stiffness = 280f)

    /** Шаг, с которым трогаются элементы группы, чтобы ехали цепочкой, а не разом. */
    const val STAGGER = 45L

    /**
     * Смена «через ноль» (M3 fade through): старое гаснет за 90 мс и только потом за 210 мс проявляется новое.
     * При перекрёстном затухании два текста какое-то время видны поверх друг друга.
     */
    const val THROUGH_OUT = 90
    const val THROUGH_IN = 210

    fun throughIn(): EnterTransition = fadeIn(tween(THROUGH_IN, delayMillis = THROUGH_OUT, easing = EmphasizedDecelerate))

    fun throughOut(): ExitTransition = fadeOut(tween(THROUGH_OUT, easing = EmphasizedAccelerate))

    fun throughScaleIn(initialScale: Float): EnterTransition =
        scaleIn(tween(THROUGH_IN, delayMillis = THROUGH_OUT, easing = EmphasizedDecelerate), initialScale)
}

/** Текст или значок сменяется «через ноль», а контейнер плавно подстраивает размер под новый. */
internal fun <S> AnimatedContentTransitionScope<S>.fadeThrough(): ContentTransform =
    Motion.throughIn() togetherWith Motion.throughOut() using SizeTransform(clip = false) { _, _ -> tween(Motion.MEDIUM, easing = Motion.Emphasized) }

private val colors = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Palette.OnAccent,
    primaryContainer = Palette.AccentContainer,
    onPrimaryContainer = Palette.Accent,
    background = Palette.Bg,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextDim,
    surfaceContainerLowest = Palette.Bg,
    surfaceContainerLow = Palette.SurfaceLow,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    surfaceContainerHighest = Palette.SurfaceHighest,
    inverseSurface = Palette.Text,
    inverseOnSurface = Palette.Bg,
    inversePrimary = Palette.AccentContainer,
    outline = Palette.Outline,
    outlineVariant = Palette.Outline,
    error = Palette.Bad,
)

/** Подписи в машине: не мельче ~16 sp расчётных (≈ 23 sp на ГУ после масштаба). */
private val typography = Typography().run {
    copy(
        labelLarge = labelLarge.copy(fontSize = 15.sp, lineHeight = 20.sp),
        labelMedium = labelMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
    )
}

/** Расчётная площадь, как в gps-tuner: 1920×720 при 240 dpi без статус-бара. */
private const val DESIGN_WIDTH_DP = 1210f
private const val DESIGN_HEIGHT_DP = 456f

@Composable
fun NavbarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography) {
        // Текст и значки без явного цвета — светлые на тёмном
        CompositionLocalProvider(LocalContentColor provides Palette.Text) { ScaledForScreen(content) }
    }
}

/** На ГУ 160 dpi (окно ≈1760×670 dp) стандартные размеры мелкие — увеличиваем до расчётной площади. */
@Composable
fun ScaledForScreen(content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val cfg = LocalConfiguration.current
    val scale = minOf(cfg.screenWidthDp / DESIGN_WIDTH_DP, cfg.screenHeightDp / DESIGN_HEIGHT_DP).coerceIn(1f, 1.6f)
    CompositionLocalProvider(LocalDensity provides Density(base.density * scale, base.fontScale), content = content)
}
