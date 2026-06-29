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

import cn.funkt.deskpet.PetState
import cn.funkt.deskpet.SpriteSheet
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.Timer
import kotlin.math.min

/**
 * 形象预览：循环播放「待机」行，给切换形象时一个直观预览。
 */
class SpritePreviewComponent(box: Dimension = Dimension(150, 160)) : JComponent() {

    var sheet: SpriteSheet? = null
        set(value) {
            field = value
            frame = 0
            repaint()
        }

    private var frame = 0
    private val anim = PetState.IDLE
    private val timer = Timer(160) {
        if (sheet != null) {
            frame = (frame + 1) % anim.frames.size
            repaint()
        }
    }

    init {
        preferredSize = box
        isOpaque = false
        timer.start()
    }

    fun stop() = timer.stop()

    override fun paintComponent(g: Graphics) {
        val s = sheet ?: return
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val col = anim.frames[frame.coerceIn(0, anim.frames.size - 1)]
            val img = s.frame(col, anim.row)
            val scale = min(width.toDouble() / SpriteSheet.FRAME_W, height.toDouble() / SpriteSheet.FRAME_H)
            val w = (SpriteSheet.FRAME_W * scale).toInt()
            val h = (SpriteSheet.FRAME_H * scale).toInt()
            g2.drawImage(img, (width - w) / 2, (height - h) / 2, w, h, null)
        } finally {
            g2.dispose()
        }
    }
}
