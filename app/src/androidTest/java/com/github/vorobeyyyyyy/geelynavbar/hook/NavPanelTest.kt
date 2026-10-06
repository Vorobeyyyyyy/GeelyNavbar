package com.github.vorobeyyyyyy.geelynavbar.hook

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.vorobeyyyyyy.geelynavbar.IconLoader
import com.github.vorobeyyyyyy.geelynavbar.NavAction
import com.github.vorobeyyyyyy.geelynavbar.Panel
import com.github.vorobeyyyyyy.geelynavbar.PanelItem
import com.github.vorobeyyyyyy.geelynavbar.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/* Копия штатного navigation_bar.xml: mNavContent с тремя кнопками и списком недавних, поверх — hvacBar с виджетом. */
@RunWith(AndroidJUnit4::class)
class NavPanelTest {
    private val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var root: FrameLayout
    private lateinit var navContent: FrameLayout
    private lateinit var hvacBar: FrameLayout
    private lateinit var car: ImageView
    private lateinit var apps: ImageView
    private lateinit var home: ImageView
    private lateinit var recents: View
    private lateinit var widget: View
    private val runs = mutableListOf<NavAction>()

    /** Порядок детей и их LayoutParams — по ним сверяем, что сток вернулся точно таким же. */
    private data class Tree(val root: List<Pair<View, ViewGroup.LayoutParams>>, val nav: List<Pair<View, ViewGroup.LayoutParams>>, val hvacVisibility: Int)

    private fun tree() = Tree(children(root), children(navContent), hvacBar.visibility)

    private fun children(g: ViewGroup) = (0 until g.childCount).map { g.getChildAt(it).let { v -> v to v.layoutParams } }

    @Before
    fun setUp() {
        root = FrameLayout(ctx)
        navContent = FrameLayout(ctx)
        root.addView(navContent, FrameLayout.LayoutParams(120, 720))
        fun stockButton(top: Int) = ImageView(ctx).also {
            it.visibility = View.GONE
            navContent.addView(it, FrameLayout.LayoutParams(120, 126).apply { topMargin = top })
        }
        home = stockButton(510)
        apps = stockButton(370)
        car = stockButton(75)
        recents = View(ctx).also { navContent.addView(it) }
        hvacBar = FrameLayout(ctx)
        root.addView(hvacBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // Как HvacView из ecarx.hvac.app: на своё место встаёт верхним отступом
        widget = View(ctx).also { hvacBar.addView(it, FrameLayout.LayoutParams(100, 40).apply { topMargin = 270 }) }
    }

    private fun panel(newButton: (Context) -> ImageView = { PressAlphaImageView(it) }) = NavPanel(
        StockViews(root, navContent, hvacBar),
        IconLoader(null, ctx.resources, ctx.packageManager),
        newButton,
    ) { runs += it }

    private fun column(): LinearLayout = (0 until navContent.childCount).map { navContent.getChildAt(it) }.filterIsInstance<LinearLayout>().single()

    private val custom = Panel(
        listOf(
            PanelItem.Button(NavAction.Back, NavAction.Recents),
            PanelItem.HvacWidget(),
            PanelItem.Button(NavAction.LaunchApp(Protocol.MODULE_PKG)),
            PanelItem.Button(NavAction.Home),
        ),
    )

    @Test
    fun buildsColumnAndMovesHvac() {
        val p = panel()
        p.apply(custom)
        assertNull(p.lastError)
        // Штатное содержимое снято целиком, в mNavContent только своя колонка
        listOf(car, apps, home, recents).forEach { assertNull(it.parent) }
        assertEquals(1, navContent.childCount)
        val col = column()
        assertEquals(4, col.childCount)
        assertEquals(4, p.appliedItems)
        assertSame(hvacBar, col.getChildAt(1))
        assertEquals(1f, (hvacBar.layoutParams as LinearLayout.LayoutParams).weight)
        assertSame(widget, hvacBar.getChildAt(0))
        assertEquals(1, root.childCount)
        // Кнопки делят высоту поровну, иконки на месте (на эмуляторе штатные PNG заменены своими)
        listOf(0, 2, 3).forEach {
            val v = col.getChildAt(it) as ImageView
            assertEquals(1f, (v.layoutParams as LinearLayout.LayoutParams).weight)
            assertNotNull(v.drawable)
        }
        // Иконка приложения ужата отступами, штатная — нет
        assertTrue((col.getChildAt(2) as ImageView).paddingLeft > 0)
        assertEquals(0, (col.getChildAt(3) as ImageView).paddingLeft)
    }

    @Test
    fun restoreReturnsExactStockTree() {
        val stock = tree()
        val p = panel()
        p.apply(custom)
        p.apply(null)
        assertEquals(stock, tree())
        assertEquals(0, p.appliedItems)
    }

    @Test
    fun reapplyDoesNotDuplicate() {
        val p = panel()
        p.apply(custom)
        p.apply(custom)
        p.refreshIcons()
        assertEquals(1, navContent.childCount)
        assertEquals(4, column().childCount)
        p.apply(Panel(listOf(PanelItem.Button(NavAction.Home))))
        assertEquals(1, column().childCount)
    }

    @Test
    fun hvacHiddenWhenNotInListAndBack() {
        val stock = tree()
        val p = panel()
        p.apply(Panel(listOf(PanelItem.Button(NavAction.Home))))
        assertSame(root, hvacBar.parent)
        assertEquals(View.GONE, hvacBar.visibility)
        p.apply(null)
        assertEquals(stock, tree())
    }

    @Test
    fun clicksRunActions() {
        val p = panel()
        p.apply(custom)
        val col = column()
        val first = col.getChildAt(0)
        val third = col.getChildAt(2)
        assertTrue(first.performClick())
        assertTrue(first.performLongClick())
        assertTrue(third.performClick())
        // Долгого нет — не перехватываем, кнопка ведёт себя как обычная
        assertFalse(third.performLongClick())
        assertEquals(listOf(NavAction.Back, NavAction.Recents, NavAction.LaunchApp(Protocol.MODULE_PKG)), runs)
    }

    @Test
    fun modButtonsAlsoHiddenAndRestored() {
        // Мод SX11 из сети: в mNavContent ещё «Карта» сверху и «Назад» снизу
        val map = ImageView(ctx).also { navContent.addView(it, 0, FrameLayout.LayoutParams(120, 96)) }
        val back = ImageView(ctx).also { navContent.addView(it, FrameLayout.LayoutParams(120, 82)) }
        val stock = tree()
        val p = panel()
        p.apply(custom)
        listOf(map, back).forEach { assertNull(it.parent) }
        assertEquals(1, navContent.childCount)
        p.apply(null)
        assertEquals(stock, tree())
    }

    @Test
    fun hvacWidgetCenteredInSlotAndRestored() {
        val p = panel()
        p.apply(custom)
        val lp = widget.layoutParams as FrameLayout.LayoutParams
        assertEquals(0, lp.topMargin)
        assertEquals(android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL, lp.gravity)
        // Виджет, который HvacController добавит позже, тоже центруется
        val late = View(ctx).also { hvacBar.addView(it, FrameLayout.LayoutParams(100, 40).apply { topMargin = 250 }) }
        assertEquals(0, (late.layoutParams as FrameLayout.LayoutParams).topMargin)
        p.apply(null)
        assertEquals(270, (widget.layoutParams as FrameLayout.LayoutParams).topMargin)
        assertEquals(FrameLayout.LayoutParams.UNSPECIFIED_GRAVITY, (widget.layoutParams as FrameLayout.LayoutParams).gravity)
        assertEquals(250, (late.layoutParams as FrameLayout.LayoutParams).topMargin)
        // После отката новые виджеты не трогаем
        val after = View(ctx).also { hvacBar.addView(it, FrameLayout.LayoutParams(100, 40).apply { topMargin = 260 }) }
        assertEquals(260, (after.layoutParams as FrameLayout.LayoutParams).topMargin)
    }

    @Test
    fun scaleWrapsDrawableKeepingButtonSize() {
        val p = panel()
        p.apply(Panel(listOf(PanelItem.Button(NavAction.Back, scale = 150), PanelItem.Button(NavAction.Home))))
        val col = column()
        val scaled = (col.getChildAt(0) as ImageView).drawable
        assertTrue(scaled is com.github.vorobeyyyyyy.geelynavbar.ScaledDrawable)
        // Размер для раскладки прежний — кнопки по-прежнему делят высоту поровну
        val plain = (col.getChildAt(1) as ImageView).drawable
        assertFalse(plain is com.github.vorobeyyyyyy.geelynavbar.ScaledDrawable)
        assertEquals(1f, (col.getChildAt(0).layoutParams as LinearLayout.LayoutParams).weight)
        assertTrue(scaled.intrinsicWidth > 0)
    }

    @Test
    fun hvacWidgetScaledAndRestored() {
        val p = panel()
        p.apply(Panel(listOf(PanelItem.Button(NavAction.Home), PanelItem.HvacWidget(scale = 130))))
        assertEquals(1.3f, widget.scaleX)
        assertEquals(1.3f, widget.scaleY)
        // Ячейка не масштабируется — она и обрезает виджет
        assertEquals(1f, hvacBar.scaleX)
        val late = View(ctx).also { hvacBar.addView(it, FrameLayout.LayoutParams(100, 40)) }
        assertEquals(1.3f, late.scaleX)
        p.apply(null)
        listOf(widget, late).forEach {
            assertEquals(1f, it.scaleX)
            assertEquals(1f, it.scaleY)
        }
    }

    @Test
    fun paddingsApplied() {
        val p = panel()
        p.apply(Panel(listOf(PanelItem.Button(NavAction.Home)), paddingTop = 23, paddingBottom = 34))
        val d = ctx.resources.displayMetrics.density
        assertEquals((23 * d).toInt(), column().paddingTop)
        assertEquals((34 * d).toInt(), column().paddingBottom)
        p.apply(Panel(listOf(PanelItem.Button(NavAction.Home))))
        assertEquals(0, column().paddingTop)
    }

    @Test
    fun failedBuildKeepsStock() {
        val stock = tree()
        val p = panel(newButton = { throw IllegalStateException("нет AlphaImageView") })
        p.apply(custom)
        assertNotNull(p.lastError)
        assertEquals(stock, tree())
        assertEquals(0, p.appliedItems)
    }

    @Test
    fun stockButtonsShownWhileDetachedComeBackVisible() {
        val p = panel()
        p.apply(custom)
        // Сток (NavigationBarView$1.success) делает кнопки видимыми, когда климат загрузился, — даже снятые
        listOf(car, apps, home).forEach { it.visibility = View.VISIBLE }
        p.apply(null)
        listOf(car, apps, home).forEach {
            assertSame(navContent, it.parent)
            assertEquals(View.VISIBLE, it.visibility)
        }
        assertEquals(listOf<View>(home, apps, car, recents), children(navContent).map { it.first })
    }
}
