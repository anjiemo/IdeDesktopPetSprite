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
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
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
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.Timer
import kotlin.math.min
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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
    private val multiSelect: Boolean = false,
    private val onSelect: (Item?) -> Unit,
    private val onMultiSelect: (List<Item>) -> Unit = {},
    private val onActivate: (Item) -> Unit,
) {

    /** 一个网格项：[loadThumb] 在后台线程执行（必要时下载 + 解码缩略图），失败返回 null */
    data class Item(
        val id: String,
        val title: String,
        val subtitle: String,
        val character: PetCharacter,
        val loadThumb: (ProgressIndicator?) -> BufferedImage?,
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val semaphore = Semaphore(8)

    private var cells: List<Cell> = emptyList()
    private var selectedCell: Cell? = null
    private val selectedCells: LinkedHashSet<Cell> = linkedSetOf()
    private var generation = 0

    /** 当前选中的所有 Item（仅多选模式下有效） */
    val selectedItems: List<Item> get() = selectedCells.map { it.item }

    // 滚动防抖：连续滚动时不反复入队，停下后再统一加载可视区域
    private val debounce = Timer(120) { loadVisible() }.apply { isRepeats = false }

    init {
        scroll.viewport.addChangeListener { debounce.restart() }
        scroll.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) = debounce.restart()
        })
    }

    private var allItems: List<Item> = emptyList()
    private var displayedCount = 120

    /** setItems 用的专属令牌，保证快速多次触发时只有最后一次的结果生效 */
    private var setItemsToken = 0

    fun setItems(items: List<Item>, resetScroll: Boolean = true) {
        generation++
        scope.coroutineContext.cancelChildren()
        selectedCell = null
        selectedCells.clear()
        allItems = items
        displayedCount = if (resetScroll) 120 else displayedCount.coerceAtLeast(120)

        val toDisplay = allItems.take(displayedCount)
        cells = toDisplay.map { Cell(it) }
        grid.removeAll()
        cells.forEach { grid.add(it) }
        grid.revalidate()
        grid.repaint()
        if (resetScroll) {
            scroll.verticalScrollBar.value = 0
        }
        debounce.restart()
    }

    fun selectById(id: String) {
        select(cells.firstOrNull { it.item.id == id }, toggleOrRange = false)
    }

    fun clearSelection() {
        selectedCells.toList().forEach { setActive(it, false) }
        selectedCells.clear()
        selectedCell = null
        select(null, toggleOrRange = false)
    }

    val selected: Item? get() = selectedCell?.item

    fun dispose() {
        debounce.stop()
        scope.cancel()
    }

    // ---------------- 内部 ----------------

    private fun select(cell: Cell?, toggleOrRange: Boolean = false, rangeAnchor: Cell? = null) {
        if (!multiSelect || !toggleOrRange) {
            // 单选模式，或没有修饰键：清空之前所有选中
            selectedCells.toList().forEach { setActive(it, false) }
            selectedCells.clear()
        }

        if (cell == null) {
            selectedCell = null
            onSelect(null)
            if (multiSelect) onMultiSelect(emptyList())
            return
        }

        if (multiSelect && toggleOrRange && rangeAnchor != null) {
            // Shift 点击：从锚点到当前格子范围全选
            val anchorIdx = cells.indexOf(rangeAnchor)
            val cellIdx = cells.indexOf(cell)
            val range = if (anchorIdx <= cellIdx) cells.subList(anchorIdx, cellIdx + 1)
            else cells.subList(cellIdx, anchorIdx + 1)
            range.forEach {
                setActive(it, true)
                selectedCells.add(it)
            }
        } else if (multiSelect && toggleOrRange) {
            // Ctrl 点击：切换单个格子
            if (selectedCells.contains(cell)) {
                setActive(cell, false)
                selectedCells.remove(cell)
            } else {
                setActive(cell, true)
                selectedCells.add(cell)
            }
        } else {
            // 普通点击（单选或多选模式无修饰键）
            setActive(cell, true)
            selectedCells.add(cell)
        }

        selectedCell = selectedCells.lastOrNull() ?: cell
        selectedCell?.let {
            it.scrollRectToVisible(Rectangle(0, 0, it.width, it.height))
            ensureLoaded(it)
        }
        onSelect(selectedCell?.item)
        if (multiSelect) onMultiSelect(selectedItems)
    }

    private fun setActive(cell: Cell, active: Boolean) {
        cell.active = active
        cell.repaint()
    }

    private fun loadVisible() {
        if (cells.isEmpty() || grid.width == 0) return

        // 检查是否滚动接近底部，如果是，且还有未展示的项，则追加展示更多单元格
        val scrollBar = scroll.verticalScrollBar
        if (displayedCount < allItems.size && scrollBar.value + scrollBar.visibleAmount >= scrollBar.maximum - 400) {
            displayedCount = min(allItems.size, displayedCount + 60)
            val toDisplay = allItems.take(displayedCount)
            val currentSize = cells.size
            val newItems = toDisplay.drop(currentSize)
            val newCells = newItems.map { Cell(it) }
            cells = cells + newCells
            newCells.forEach { grid.add(it) }
            grid.revalidate()
            grid.repaint()
            return
        }

        val view = scroll.viewport.viewRect
        // 仅加载可视区域，外加上下各半屏预取，滚动更顺滑而不浪费
        val expanded = Rectangle(view.x, view.y - view.height / 2, view.width, view.height * 2)
        for (cell in cells) {
            val inViewport = cell.bounds.intersects(expanded)
            if (inViewport) {
                ensureLoaded(cell)
            } else {
                // 如果已经有协程在运行，但在视口之外，则取消加载
                cell.job?.let {
                    if (it.isActive) {
                        it.cancel()
                    }
                }
            }
        }
    }

    private fun ensureLoaded(cell: Cell) {
        if (cell.thumb != null || cell.failed) return
        if (cell.job?.isActive == true) return
        val id = cell.item.id
        PetThumbnails.cached(id)?.let {
            cell.thumb = it
            return
        }
        val gen = generation
        val indicator = EmptyProgressIndicator()
        cell.job = scope.launch {
            val currentJob = coroutineContext[Job]!!
            val reg = currentJob.invokeOnCompletion {
                indicator.cancel()
            }
            var img: BufferedImage? = null
            try {
                img = semaphore.withPermit {
                    withContext(Dispatchers.IO) {
                        cell.item.loadThumb(indicator)
                    }
                }
                ApplicationManager.getApplication().invokeLater({
                    if (gen == generation) {
                        if (img != null) {
                            cell.thumb = img
                            if (cell === selectedCell) {
                                onSelect(cell.item)
                            }
                        } else {
                            cell.markFailed()
                        }
                    }
                }, ModalityState.any())
            } catch (e: CancellationException) {
                // 协程被取消，静默退出
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater({
                    if (gen == generation) cell.markFailed()
                }, ModalityState.any())
            } finally {
                reg.dispose()
                ApplicationManager.getApplication().invokeLater({
                    if (cell.job === currentJob) {
                        cell.job = null
                    }
                }, ModalityState.any())
            }
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

        var failed = false
        var job: Job? = null

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
                override fun mousePressed(e: MouseEvent) {
                    if (multiSelect) {
                        val isCtrl = e.isControlDown || e.isMetaDown
                        val isShift = e.isShiftDown
                        when {
                            isShift -> select(this@Cell, toggleOrRange = true, rangeAnchor = selectedCell ?: this@Cell)
                            isCtrl -> select(this@Cell, toggleOrRange = true)
                            else -> select(this@Cell, toggleOrRange = false)
                        }
                    } else {
                        select(this@Cell, toggleOrRange = false)
                    }
                }

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
