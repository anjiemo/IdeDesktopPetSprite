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

package cn.funkt.deskpet

import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.Timer

/**
 * 负责绘制宠物当前帧 + 底部状态徽标，并用 Swing Timer 逐帧驱动动画。
 * 所有方法均在 EDT 调用。
 */
class PetComponent(scale: Double) : JComponent() {

    var scale: Double = scale
        set(value) {
            field = value
            updateSize()
            repaint()
        }

    /**
     * 当前精灵图（形象）。切换形象时替换并重绘。
     */
    var sheet: SpriteSheet = PetSprite.builtin
        set(value) {
            field = value
            repaint()
        }

    private var state: PetState = PetState.IDLE
    private var frameIndex = 0

    private val timer = Timer(state.frameMs) {
        frameIndex = (frameIndex + 1) % state.frames.size
        repaint()
    }

    init {
        isOpaque = false
        updateSize()
        timer.start()
    }

    fun setState(newState: PetState) {
        if (newState == state) return
        state = newState
        frameIndex = 0
        timer.delay = newState.frameMs
        timer.restart()
        repaint()
    }

    fun stop() = timer.stop()

    private val petW get() = (PetSprite.FRAME_W * scale).toInt()
    private val petH get() = (PetSprite.FRAME_H * scale).toInt()

    private fun updateSize() {
        val w = petW + PAD * 2
        val h = petH + BADGE_H + PAD
        val dim = Dimension(w, h)
        preferredSize = dim
        size = dim
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )
            g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON,
            )

            val col = state.frames[frameIndex.coerceIn(0, state.frames.size - 1)]
            val frame = sheet.frame(col, state.row)
            g2.drawImage(frame, PAD, 0, petW, petH, null)

            drawBadge(g2)
        } finally {
            g2.dispose()
        }
    }

    private fun drawBadge(g2: Graphics2D) {
        g2.font = BADGE_FONT
        val fm = g2.fontMetrics
        val text = state.label
        val tw = fm.stringWidth(text)
        val bw = INNER_PAD * 2 + DOT + GAP + tw
        val bh = BADGE_H
        val bx = (width - bw) / 2
        val by = height - bh

        // 半透明深色药丸背景
        g2.color = Color(0, 0, 0, 150)
        g2.fillRoundRect(bx, by, bw, bh, bh, bh)

        // 状态色圆点
        g2.color = state.badgeColor
        g2.fillOval(bx + INNER_PAD, by + (bh - DOT) / 2, DOT, DOT)

        // 文字
        g2.color = Color.WHITE
        val tx = bx + INNER_PAD + DOT + GAP
        val ty = by + (bh - fm.height) / 2 + fm.ascent
        g2.drawString(text, tx, ty)
    }

    private companion object {
        const val PAD = 6
        const val BADGE_H = 22
        const val INNER_PAD = 10
        const val DOT = 8
        const val GAP = 6
        val BADGE_FONT = Font("Microsoft YaHei", Font.BOLD, 12)
    }
}
