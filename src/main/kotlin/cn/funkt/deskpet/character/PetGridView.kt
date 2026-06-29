/*
 * Copyright 2026 anjiemo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.funkt.deskpet.character

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.Timer
import kotlin.math.min

/**
 * 形象网格（复刻 Vibe Pet 选择器）：多列网格缩略图，按视口懒加载、并发预取，
 * 不再「点哪个才下载哪个」。
 *
 * 性能要点：网格只持有**小缩略图**（[PetThumbnails]，约 96×104），绝不缓存整张大精灵图，
 * 避免内存暴涨 / GC 抖动导致的卡顿与 IDE 无响应；滚动经防抖、且只加载可视区域附近的格子；
 * 整张精灵图仅在用户「选中」某个形象时（供右侧动态预览 / 应用）才解码，至多一两张。
 */
class PetGridView(
    private val columns: Int = 5,
    private val onSelect: (Item?) -> Unit,
    private val onActivate: (Item) -> Unit,
) {

    /** 一个网格项：[loadThumb] 在后台线程执行（必要时下载 + 解码缩略图），失败返回 null */
    data class Item(
        val id: String,
        val title: String,
        val subtitle: String,
        val character: PetCharacter,
        val loadThumb: () -> BufferedImage?,
    )

    private val grid = object : JPanel(GridLayout(0, columns, GAP, GAP)), Scrollable {
        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
        override fun getScrollableUnitIncrement(r: Rectangle, orientation: Int, direction: Int) = 28
        override fun getScrollableBlockIncrement(r: Rectangle, orientation: Int, direction: Int) = r.height
        override fun getScrollableTracksViewportWidth() = true
        override fun getScrollableTracksViewportHeight() = false
    }.apply { border = JBUI.Borders.empty(2) }

    private val scroll = JBScrollPane(grid).apply {
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        border = JBUI.Borders.empty()
        viewport.background = grid.background
        verticalScrollBar.unitIncrement = 28
    }

    val component: JComponent get() = scroll

    // 限制并发，避免一次性大量下载 / 解码把 CPU、网络打满
    private val pool = Executors.newFixedThreadPool(4, ThreadFactory { r ->
        Thread(r, "deskpet-grid-loader").apply { isDaemon = true }
    })
    private val loading: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    private var cells: List<Cell> = emptyList()
    private var selectedCell: Cell? = null
    private var generation = 0

    // 滚动防抖：连续滚动时不反复入队，停下后再统一加载可视区域
    private val debounce = Timer(120) { loadVisible() }.apply { isRepeats = false }

    init {
        scroll.viewport.addChangeListener { debounce.restart() }
        scroll.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) = debounce.restart()
        })
    }

    fun setItems(items: List<Item>) {
        generation++
        loading.clear()
        selectedCell = null
        cells = items.map { Cell(it) }
        grid.removeAll()
        cells.forEach { grid.add(it) }
        grid.revalidate()
        grid.repaint()
        scroll.verticalScrollBar.value = 0
        debounce.restart()
    }

    fun selectById(id: String) {
        select(cells.firstOrNull { it.item.id == id })
    }

    fun clearSelection() = select(null)

    val selected: Item? get() = selectedCell?.item

    fun dispose() {
        debounce.stop()
        pool.shutdownNow()
    }

    // ---------------- 内部 ----------------

    private fun select(cell: Cell?) {
        if (cell === selectedCell) return
        selectedCell?.let { it.active = false; it.repaint() }
        selectedCell = cell
        cell?.let {
            it.active = true
            it.repaint()
            it.scrollRectToVisible(Rectangle(0, 0, it.width, it.height))
            ensureLoaded(it)
        }
        onSelect(cell?.item)
    }

    private fun loadVisible() {
        if (cells.isEmpty() || grid.width == 0) return
        val view = scroll.viewport.viewRect
        // 仅加载可视区域，外加上下各半屏预取，滚动更顺滑而不浪费
        val expanded = Rectangle(view.x, view.y - view.height / 2, view.width, view.height * 2)
        for (cell in cells) {
            if (cell.bounds.intersects(expanded)) ensureLoaded(cell)
        }
    }

    private fun ensureLoaded(cell: Cell) {
        if (cell.thumb != null) return
        val id = cell.item.id
        PetThumbnails.cached(id)?.let { cell.thumb = it; return }
        if (!loading.add(id)) return
        val gen = generation
        pool.submit {
            val img = runCatching { cell.item.loadThumb() }.getOrNull()
            ApplicationManager.getApplication().invokeLater({
                loading.remove(id)
                if (gen != generation) return@invokeLater
                if (img != null) cell.thumb = img else cell.markFailed()
            }, ModalityState.any())
        }
    }

    private inner class Cell(val item: Item) : JPanel(BorderLayout(0, 6)) {

        var thumb: BufferedImage? = null
            set(value) {
                field = value
                thumbView.repaint()
            }

        var active: Boolean = false
            set(value) {
                field = value
                border = if (value) ACTIVE_BORDER else IDLE_BORDER
            }

        private var failed = false

        private val thumbView = object : JComponent() {
            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                try {
                    val img = thumb
                    if (img != null) {
                        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                        val scale = min(width.toDouble() / img.width, height.toDouble() / img.height)
                        val w = (img.width * scale).toInt()
                        val h = (img.height * scale).toInt()
                        g2.drawImage(img, (width - w) / 2, (height - h) / 2, w, h, null)
                    } else {
                        g2.color = JBColor.GRAY
                        g2.font = JBFont.label()
                        val tip = if (failed) "加载失败" else "…"
                        val fm = g2.fontMetrics
                        g2.drawString(tip, (width - fm.stringWidth(tip)) / 2, (height + fm.ascent - fm.descent) / 2)
                    }
                } finally {
                    g2.dispose()
                }
            }
        }

        private val titleLabel = JBLabel(item.title, SwingConstants.CENTER).apply { font = JBFont.label().asBold() }
        private val subtitleLabel = JBLabel(item.subtitle, SwingConstants.CENTER).apply {
            font = JBFont.small()
            foreground = JBColor.GRAY
        }

        init {
            isOpaque = false
            border = IDLE_BORDER
            preferredSize = Dimension(112, 150)
            add(thumbView, BorderLayout.CENTER)
            val labels = JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(titleLabel.alignCenter())
                add(subtitleLabel.alignCenter())
            }
            add(labels, BorderLayout.SOUTH)
            toolTipText = buildString {
                append(item.title)
                if (item.subtitle.isNotBlank()) append(" · ").append(item.subtitle)
            }
            val mouse = object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) = select(this@Cell)
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) onActivate(item)
                }
            }
            attachRecursively(this, mouse)
        }

        fun markFailed() {
            failed = true
            thumbView.repaint()
        }

        private fun JBLabel.alignCenter(): JBLabel = apply { alignmentX = Component.CENTER_ALIGNMENT }
    }

    private fun attachRecursively(c: Component, mouse: MouseAdapter) {
        c.addMouseListener(mouse)
        if (c is java.awt.Container) c.components.forEach { attachRecursively(it, mouse) }
    }

    companion object {
        private const val GAP = 10
        private val ACTIVE_BORDER = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor(0x3574F0, 0x3574F0), 2, 2, 2, 2),
            JBUI.Borders.empty(8, 6),
        )
        private val IDLE_BORDER = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor(0xE0E0E0, 0x4A4A4A), 1, 1, 1, 1),
            JBUI.Borders.empty(9, 7),
        )
    }
}
