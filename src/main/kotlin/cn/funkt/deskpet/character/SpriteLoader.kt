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

import cn.funkt.deskpet.PetSprite
import cn.funkt.deskpet.SpriteSheet
import com.intellij.openapi.application.PathManager
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

/**
 * 把 [PetCharacter] 解析成可绘制的 [SpriteSheet]。
 * 内置直接走资源；本地 / Petdex 走文件（支持 png / webp）。加载结果按 id 缓存。
 */
object SpriteLoader {

    private val cache = ConcurrentHashMap<String, SpriteSheet>()

    /** 解析为精灵图；任何失败都回退到内置形象，保证宠物始终有图可画 */
    fun load(c: PetCharacter): SpriteSheet {
        if (c.isBuiltin) return PetSprite.builtin
        cache[c.id]?.let { return it }
        val sheet = readFile(c.filePath)?.let { SpriteSheet(it) }
        if (sheet != null) {
            cache[c.id] = sheet
            return sheet
        }
        return PetSprite.builtin
    }

    /** 读取任意精灵图文件（用于导入校验 / 预览），失败返回 null */
    fun readFile(path: String?): BufferedImage? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.isFile) return null
        return readImage(file)
    }

    fun readImage(file: File): BufferedImage? {
        WebpSupport.ensureRegistered()
        val img = runCatching { ImageIO.read(file) }.getOrNull() ?: return null
        return if (SpriteSheet.isValid(img)) img else null
    }

    /** 预置已解码好的精灵图（如选择器已下载/解码完成），后续 [load] 直接命中，避免重复解码 */
    fun prime(id: String, sheet: SpriteSheet) {
        cache[id] = sheet
    }

    fun invalidate(id: String) {
        cache.remove(id)
    }

    fun clearCache() {
        cache.clear()
        runCatching { cacheDir().deleteRecursively() }
    }

    /** 形象精灵图缓存目录（Petdex 下载 / 本地导入副本都放这里） */
    fun cacheDir(): File = File(PathManager.getSystemPath(), "ideDeskPet/pets").apply { mkdirs() }

    /** 把本地选中的精灵图复制进缓存目录，避免用户移动/删除原文件后失效 */
    fun importLocalFile(src: File): File {
        val ext = src.extension.lowercase().ifBlank { "png" }
        val dest = File(cacheDir(), "local-${src.absolutePath.hashCode().toUInt()}-${src.length()}.$ext")
        src.copyTo(dest, overwrite = true)
        return dest
    }
}
