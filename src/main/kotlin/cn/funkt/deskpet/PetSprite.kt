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
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 一张精灵图（网格动画表）。每帧固定 [FRAME_W]×[FRAME_H]，
 * 列数 / 行数按图片实际尺寸自动推算（与 Petdex 约定一致）。
 * 内置素材为 8 列 × 9 行；从 Petdex / 本地导入的精灵图允许任意网格。
 */
class SpriteSheet(val image: BufferedImage) {

    val cols: Int = max(1, (image.width.toDouble() / FRAME_W).roundToInt())
    val rows: Int = max(1, (image.height.toDouble() / FRAME_H).roundToInt())

    /**
     * 取出第 row 行、第 col 列的一帧（越界自动夹紧）
     */
    fun frame(col: Int, row: Int): BufferedImage {
        val c = col.coerceIn(0, cols - 1)
        val r = row.coerceIn(0, rows - 1)
        return image.getSubimage(c * FRAME_W, r * FRAME_H, FRAME_W, FRAME_H)
    }

    companion object {
        const val FRAME_W = 192
        const val FRAME_H = 208

        /**
         * 校验一张图是否能作为有效精灵图（至少能切出一帧）
         */
        fun isValid(image: BufferedImage): Boolean =
            image.width >= FRAME_W && image.height >= FRAME_H
    }
}

/**
 * 内置默认精灵图（原创史莱姆 Gel）。仅在首次使用时加载一次。
 */
object PetSprite {
    const val FRAME_W = SpriteSheet.FRAME_W
    const val FRAME_H = SpriteSheet.FRAME_H

    const val BUILTIN_RESOURCE = "/pets/gel-slime.png"

    val builtin: SpriteSheet by lazy {
        val stream = PetSprite::class.java.getResourceAsStream(BUILTIN_RESOURCE)
            ?: error("缺少内置精灵图资源: $BUILTIN_RESOURCE")
        SpriteSheet(stream.use { ImageIO.read(it) })
    }
}
