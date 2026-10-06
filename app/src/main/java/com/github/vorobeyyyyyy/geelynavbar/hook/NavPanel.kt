package com.github.vorobeyyyyyy.geelynavbar.hook

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.github.vorobeyyyyyy.geelynavbar.IconLoader
import com.github.vorobeyyyyyy.geelynavbar.Icons
import com.github.vorobeyyyyyy.geelynavbar.NavAction
import com.github.vorobeyyyyyy.geelynavbar.Panel
import com.github.vorobeyyyyyy.geelynavbar.PanelItem
import com.github.vorobeyyyyyy.geelynavbar.ScaledDrawable

/** Штатные view панели; достаются рефлексией из `NavigationBarView` в [PanelHook]. */
internal class StockViews(
    /** Сам `NavigationBarView`. */
    val root: FrameLayout,
    /** `mNavContent`: FrameLayout 120×720 dp с кнопками. */
    val navContent: FrameLayout,
    /** `hvacBar`: в него `HvacController` кладёт виджет климата. */
    val hvacBar: FrameLayout,
)

/**
 * Своя колонка кнопок поверх штатной разметки. От Xposed не зависит — тестируется на эмуляторе.
 *
 * Штатно в `mNavContent` три кнопки на фиксированных отступах, поверх лежит `hvacBar`. Мы снимаем из `mNavContent`
 * всё, что там есть (в моде SX11 из сети там ещё «Карта» и «Назад»), и кладём вертикальный LinearLayout, где пункты
 * делят высоту поровну, а `hvacBar` переносится на своё место в списке.
 * Штатное дерево трогаем только после того, как своя колонка полностью собрана; [restore] возвращает его как было.
 */
internal class NavPanel(
    private val stock: StockViews,
    private val icons: IconLoader,
    /** Новая кнопка: штатный AlphaImageView (полупрозрачный при нажатии). */
    private val newButton: (Context) -> ImageView,
    private val run: (NavAction) -> Unit,
) {
    val root get() = stock.root
    /** PluginContext: ресурсы плагина, их день/ночь переключает сам сток. */
    private val ctx: Context = stock.root.context
    private val navContent = stock.navContent
    private val hvacBar = stock.hvacBar
    private val hvacIndex = stock.root.indexOfChild(hvacBar)
    private val hvacParams: ViewGroup.LayoutParams = hvacBar.layoutParams
    private val hvacVisibility = hvacBar.visibility
    /** Всё штатное содержимое `mNavContent` (кнопки, список недавних) с LayoutParams — в исходном порядке. */
    private val stockChildren = (0 until navContent.childCount).map { navContent.getChildAt(it) }.map { it to it.layoutParams }

    private val buttons = mutableListOf<Pair<ImageView, PanelItem.Button>>()
    private var column: LinearLayout? = null
    /** Как виджет климата лежал в стоке — пока он центрован (и масштабирован) в своей ячейке колонки. */
    private class StockPlacement(val topMargin: Int, val bottomMargin: Int, val gravity: Int, val scaleX: Float, val scaleY: Float)

    private val hvacChildLayout = HashMap<View, StockPlacement>()
    private var hvacScale = 1f

    private var hvacLayout = ""

    init {
        // Виджет климата приходит из ecarx.hvac.app асинхронно; его размер и отступы видны только на ГУ — пишем в лог
        hvacBar.addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
            val w = (v as ViewGroup).getChildAt(0)
            val lp = w?.layoutParams as? ViewGroup.MarginLayoutParams
            val s = "hvacBar ${r - l}×${b - t} @$l,$t в ${v.parent?.javaClass?.simpleName}; виджет " +
                if (w == null) "ещё не загружен" else "${w.javaClass.name} ${w.width}×${w.height} @${w.left},${w.top}" +
                    (lp?.let { " lp ${it.width}×${it.height} margins ${it.leftMargin},${it.topMargin},${it.rightMargin},${it.bottomMargin}" } ?: "")
            if (s != hvacLayout) {
                hvacLayout = s
                Log.i(TAG, s)
            }
        }
    }

    /** Для PONG: сколько пунктов своей панели на экране (0 — штатная). */
    val appliedItems get() = column?.childCount ?: 0
    var lastError: Throwable? = null
        private set

    /** null — штатная панель. При ошибке сборки — тоже штатная, причина в [lastError]. */
    fun apply(panel: Panel?) {
        restore()
        lastError = null
        if (panel == null) {
            Log.i(TAG, "панель штатная")
            return
        }
        try {
            build(panel)
            Log.i(TAG, "своя панель: ${panel.items.size} пунктов")
        } catch (t: Throwable) {
            lastError = t
            Log.e(TAG, "своя панель не собралась, возвращаю штатную", t)
            restore()
        }
    }

    private fun build(panel: Panel) {
        val density = ctx.resources.displayMetrics.density
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            // Пункты делят высоту, оставшуюся после отступов
            setPadding(0, (panel.paddingTop * density).toInt(), 0, (panel.paddingBottom * density).toInt())
        }
        val built = mutableListOf<Pair<ImageView, PanelItem.Button>>()
        var hvacSlot = -1
        val night = isNight()
        for (item in panel.items) {
            when (item) {
                is PanelItem.HvacWidget -> {
                    hvacSlot = col.childCount
                    hvacScale = item.scale / 100f
                }
                is PanelItem.Button -> {
                    val v = button(item)
                    setIcon(v, item, night)
                    col.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                    built += v to item
                }
            }
        }

        // Дальше меняем штатное дерево
        column = col
        buttons += built
        stockChildren.forEach { navContent.removeView(it.first) }
        if (hvacSlot >= 0) {
            stock.root.removeView(hvacBar)
            // Равная доля, как у кнопок: с wrap_content виджет на match_parent занял бы всю колонку.
            // Ширина как у mNavContent — по горизонтали виджет стоит там же, где в стоке
            col.addView(hvacBar, hvacSlot, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            // На своё место (~280 dp сверху) виджет встаёт своим отступом — в ячейке он бы уехал вниз. Центруем,
            // в том числе виджет, который HvacController добавит позже (он приходит из ecarx.hvac.app асинхронно)
            for (i in 0 until hvacBar.childCount) centerInSlot(hvacBar.getChildAt(i))
            hvacBar.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View, child: View) = centerInSlot(child)
                override fun onChildViewRemoved(parent: View, child: View) = Unit
            })
        } else {
            hvacBar.visibility = View.GONE
        }
        navContent.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    /** Вернуть штатное дерево; безопасно звать в любом состоянии, в том числе после частичной сборки. */
    fun restore() {
        column?.let { col ->
            if (hvacBar.parent === col) col.removeView(hvacBar)
            navContent.removeView(col)
        }
        column = null
        buttons.clear()
        hvacBar.setOnHierarchyChangeListener(null)
        for ((v, saved) in hvacChildLayout) {
            v.scaleX = saved.scaleX
            v.scaleY = saved.scaleY
            val lp = v.layoutParams as? FrameLayout.LayoutParams ?: continue
            lp.topMargin = saved.topMargin
            lp.bottomMargin = saved.bottomMargin
            lp.gravity = saved.gravity
            v.layoutParams = lp
        }
        hvacChildLayout.clear()
        if (hvacBar.parent == null) stock.root.addView(hvacBar, minOf(hvacIndex, stock.root.childCount), hvacParams)
        hvacBar.visibility = hvacVisibility
        // Своей колонки уже нет, так что индекс в списке и есть исходное место
        stockChildren.forEachIndexed { i, (v, lp) ->
            if (v.parent == null) navContent.addView(v, minOf(i, navContent.childCount), lp)
        }
    }

    private fun centerInSlot(v: View) {
        val lp = v.layoutParams as? FrameLayout.LayoutParams ?: return
        if (v in hvacChildLayout) return
        hvacChildLayout[v] = StockPlacement(lp.topMargin, lp.bottomMargin, lp.gravity, v.scaleX, v.scaleY)
        // Вокруг центра (pivot по умолчанию); ячейка-hvacBar обрезает и рисунок, и нажатия по своим границам
        v.scaleX = hvacScale
        v.scaleY = hvacScale
        val horizontal = if (lp.gravity == FrameLayout.LayoutParams.UNSPECIFIED_GRAVITY) Gravity.START else lp.gravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK
        lp.topMargin = 0
        lp.bottomMargin = 0
        lp.gravity = horizontal or Gravity.CENTER_VERTICAL
        v.layoutParams = lp
    }

    fun refreshIcons() {
        try {
            val night = isNight()
            buttons.forEach { (v, b) -> setIcon(v, b, night) }
        } catch (t: Throwable) {
            Log.e(TAG, "иконки день/ночь", t)
        }
    }

    private fun button(b: PanelItem.Button): ImageView {
        val v = newButton(ctx)
        v.scaleType = ImageView.ScaleType.FIT_CENTER
        v.setOnClickListener { run(b.tap) }
        if (b.long != NavAction.None) {
            v.setOnLongClickListener {
                run(b.long)
                true
            }
        }
        return v
    }

    private fun setIcon(v: ImageView, b: PanelItem.Button, night: Boolean) {
        val loaded = icons.load(Icons.ref(b), night)
        v.setImageDrawable(
            loaded?.drawable?.let { if (b.scale == PanelItem.SCALE_DEFAULT) it else ScaledDrawable(it, b.scale / 100f) },
        )
        // Штатные PNG — глиф 48 в рамке 120×126; иконку приложения ужимаем до той же визуальной величины
        val pad = if (loaded?.isApp == true) (APP_ICON_PAD_DP * ctx.resources.displayMetrics.density).toInt() else 0
        v.setPadding(pad, 0, pad, 0)
    }

    /** Сток меняет uiMode прямо в конфигурации ресурсов плагина (UIModeModel) — по ней и смотрим. */
    private fun isNight() =
        ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private companion object {
        const val TAG = "GeelyNavbar"
        const val APP_ICON_PAD_DP = 30
    }
}

/** Запасная кнопка, если штатный AlphaImageView создать не вышло: так же полупрозрачна при нажатии. */
internal class PressAlphaImageView(c: Context) : ImageView(c) {
    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        alpha = if (pressed) 0.5f else 1f
    }
}
