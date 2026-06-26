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

import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * 精灵图加载与裁帧。
 *
 * gel-slime.png 为 1536×1872，即 8 列 × 9 行，每帧 192×208。
 * 整张图只加载一次，按 (col,row) 裁出子图。
 */
object PetSprite {
    const val FRAME_W = 192
    const val FRAME_H = 208
    const val COLS = 8
    const val ROWS = 9

    private const val RESOURCE = "/pets/gel-slime.png"

    val sheet: BufferedImage by lazy {
        val stream = PetSprite::class.java.getResourceAsStream(RESOURCE)
            ?: error("缺少精灵图资源: $RESOURCE")
        stream.use { ImageIO.read(it) }
    }

    /** 取出第 row 行、第 col 列的一帧（越界自动夹紧） */
    fun frame(col: Int, row: Int): BufferedImage {
        val c = col.coerceIn(0, COLS - 1)
        val r = row.coerceIn(0, ROWS - 1)
        return sheet.getSubimage(c * FRAME_W, r * FRAME_H, FRAME_W, FRAME_H)
    }
}
