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
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.imageio.ImageReadParam
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 形象缩略图：只解码「待机」首帧那一格（带下采样），并缓存到内存 + 磁盘。
 *
 * 关键点——绝不为了一个小缩略图把整张大精灵图（约 1536×1872 ≈ 11MB）解码进内存。
 * 网格里上百个形象若各留一张整图会占用约 1GB，导致 GC 抖动、滚动卡顿乃至 IDE 无响应。
 * 这里每个缩略图只有约 96×104，磁盘缓存让二次打开瞬时完成。
 */
object PetThumbnails {

    /** 下采样倍率：源帧 192×208，取 1/2 → 约 96×104，足够清晰且开销低 */
    private const val SAMPLE = 2

    private val mem = ConcurrentHashMap<String, BufferedImage>()

    /** 内存命中（用于 EDT 上同步取图，避免闪烁） */
    fun cached(id: String): BufferedImage? = mem[id]

    /** 从精灵图文件取缩略图（内存 → 磁盘 → 区域解码）；失败返回 null。需在后台线程调用。 */
    fun fromFile(id: String, file: File): BufferedImage? {
        mem[id]?.let { return it }
        diskRead(id)?.let { mem[id] = it; return it }
        val img = decodeIdleThumb(file) ?: return null
        mem[id] = img
        runCatching { diskWrite(id, img) }
        return img
    }

    /** 从已在内存的整张精灵图取缩略图（内置形象用，无需读盘） */
    fun fromSheet(id: String, sheet: SpriteSheet): BufferedImage {
        mem[id]?.let { return it }
        val row = PetState.IDLE.row.coerceIn(0, sheet.rows - 1)
        val img = scaleDown(sheet.frame(0, row))
        mem[id] = img
        return img
    }

    fun invalidate(id: String) {
        mem.remove(id)
        runCatching { diskFile(id).delete() }
    }

    // ---- 解码 ----

    private fun decodeIdleThumb(file: File): BufferedImage? =
        runCatching { regionDecode(file) }.getOrNull()
            ?: runCatching { fullDecodeCrop(file) }.getOrNull()

    /** 只解码待机帧所在区域（解码器支持 sourceRegion / subsampling 时开销最小） */
    private fun regionDecode(file: File): BufferedImage? {
        WebpSupport.ensureRegistered()
        ImageIO.createImageInputStream(file)?.use { iis ->
            val readers = ImageIO.getImageReaders(iis)
            if (!readers.hasNext()) return null
            val reader = readers.next()
            try {
                reader.setInput(iis, true, true)
                val w = reader.getWidth(0)
                val h = reader.getHeight(0)
                if (w < SpriteSheet.FRAME_W || h < SpriteSheet.FRAME_H) return null
                val rows = max(1, (h.toDouble() / SpriteSheet.FRAME_H).roundToInt())
                val rowY = PetState.IDLE.row.coerceIn(0, rows - 1) * SpriteSheet.FRAME_H
                val rect = Rectangle(0, rowY, min(SpriteSheet.FRAME_W, w), min(SpriteSheet.FRAME_H, h - rowY))
                val param: ImageReadParam = reader.defaultReadParam
                param.sourceRegion = rect
                
                val img = reader.read(0, param) ?: return null
                
                // 如果 ImageReader 忽略了 sourceRegion（例如某些 WebP 解码器），则在此处手动裁剪
                val cropped = if (img.width > rect.width || img.height > rect.height) {
                    val cx = if (img.width == w) rect.x else 0
                    val cy = if (img.height == h) rect.y else 0
                    img.getSubimage(cx, cy, min(rect.width, img.width - cx), min(rect.height, img.height - cy))
                } else {
                    img
                }
                
                return scaleDown(cropped)
            } finally {
                reader.dispose()
            }
        }
        return null
    }

    /** 兜底：整张解码后裁剪 + 缩小（局部变量随后即可被回收，不长期占内存） */
    private fun fullDecodeCrop(file: File): BufferedImage? {
        val full = SpriteLoader.readImage(file) ?: return null
        val sheet = SpriteSheet(full)
        return scaleDown(sheet.frame(0, PetState.IDLE.row.coerceIn(0, sheet.rows - 1)))
    }

    private fun scaleDown(frame: BufferedImage): BufferedImage {
        val w = max(1, frame.width / SAMPLE)
        val h = max(1, frame.height / SAMPLE)
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.drawImage(frame, 0, 0, w, h, null)
        } finally {
            g.dispose()
        }
        return out
    }

    // ---- 磁盘缓存 ----

    private fun thumbDir(): File = File(SpriteLoader.cacheDir(), "thumbs").apply { mkdirs() }
    private fun diskFile(id: String): File = File(thumbDir(), sanitize(id) + ".png")

    private fun diskRead(id: String): BufferedImage? {
        val f = diskFile(id)
        if (!f.isFile) return null
        val img = runCatching { ImageIO.read(f) }.getOrNull() ?: return null
        // 自愈机制：如果缓存图片尺寸大于目标缩略图尺寸（96x104），
        // 说明这是旧的或损坏的缓存（例如整张精灵图）。删除并强制重新生成。
        if (img.width > 96 || img.height > 104) {
            runCatching { f.delete() }
            return null
        }
        return img
    }

    private fun diskWrite(id: String, img: BufferedImage) {
        ImageIO.write(img, "png", diskFile(id))
    }

    private fun sanitize(s: String): String = s.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80)
}
