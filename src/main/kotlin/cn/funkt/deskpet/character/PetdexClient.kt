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

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.io.HttpRequests
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Petdex 在线形象库客户端：拉取清单、下载精灵图到本地缓存。
 *
 * 仅信任 [ASSET_HOST] 上的 https 资源（与 Petdex 官方约定一致），避免任意地址下载。
 * 形象数据来源：https://petdex.dev （社区形象库）。
 */
object PetdexClient {

    const val MANIFEST_URL = "https://petdex.dev/api/manifest"
    const val SOURCE_HOME = "https://petdex.dev"
    private const val ASSET_HOST = "assets.petdex.dev"
    private const val CONNECT_TIMEOUT = 10_000
    private const val READ_TIMEOUT = 60_000

    data class Pet(
        val slug: String,
        val displayName: String,
        val kind: String,
        val submittedBy: String,
        val spritesheetUrl: String,
        val petJsonUrl: String,
        val zipUrl: String,
    )

    /** 拉取并解析清单（网络调用，需在后台线程执行）；成功后落盘缓存，供下次秒开 */
    fun fetchManifest(indicator: ProgressIndicator? = null): List<Pet> {
        val text = HttpRequests.request(MANIFEST_URL)
            .accept("application/json")
            .connectTimeout(CONNECT_TIMEOUT)
            .readTimeout(CONNECT_TIMEOUT)
            .readString(indicator)
        val pets = parseManifest(text)
        runCatching { manifestFile().writeText(text) }
        return pets
    }

    /** 读取上次落盘的清单缓存（无网络，瞬时）；没有或损坏返回 null */
    fun cachedManifest(): List<Pet>? {
        val f = manifestFile()
        if (!f.isFile) return null
        val text = runCatching { f.readText() }.getOrNull() ?: return null
        return runCatching { parseManifest(text) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun parseManifest(text: String): List<Pet> {
        val root = JsonParser.parseString(text).asJsonObject
        val arr = root.getAsJsonArray("pets") ?: return emptyList()
        return arr.mapNotNull { runCatching { normalize(it.asJsonObject) }.getOrNull() }.filterNotNull()
    }

    private fun manifestFile(): File = cacheDir().resolve("manifest.json").toFile()

    private fun normalize(o: JsonObject): Pet? {
        val slug = o.str("slug")
        val display = o.str("displayName").ifBlank { o.str("name") }.ifBlank { slug }
        val sprite = o.str("spritesheetUrl").ifBlank { o.str("spriteUrl") }
        if (slug.isBlank() || display.isBlank() || !isAllowed(sprite)) return null
        return Pet(
            slug = slug,
            displayName = display,
            kind = o.str("kind"),
            submittedBy = o.str("submittedBy"),
            spritesheetUrl = sprite,
            petJsonUrl = o.str("petJsonUrl").takeIf { isAllowed(it) } ?: "",
            zipUrl = o.str("zipUrl").takeIf { isAllowed(it) } ?: "",
        )
    }

    /** 该形象在本地缓存中的确定路径（无需先下载即可推算，便于先建形象再异步取图） */
    fun cacheFileFor(pet: Pet): File =
        cacheDir().resolve("${sanitize(pet.slug)}.${extOf(pet.spritesheetUrl)}").toFile()

    /** 下载精灵图到缓存目录，返回本地文件（网络调用，需在后台线程执行）。已存在则直接复用，避免重复下载。 */
    fun download(pet: Pet, indicator: ProgressIndicator? = null): File {
        require(isAllowed(pet.spritesheetUrl)) { "非法的形象地址：${pet.spritesheetUrl}" }
        val file = cacheFileFor(pet)
        if (file.isFile && file.length() > 0) return file
        HttpRequests.request(pet.spritesheetUrl)
            .connectTimeout(CONNECT_TIMEOUT)
            .readTimeout(READ_TIMEOUT)
            .saveToFile(file, indicator)
        return file
    }

    /** 由 Petdex 条目构造形象（filePath 指向其确定的缓存路径，可在下载完成前先行构造） */
    fun toCharacter(pet: Pet, file: File = cacheFileFor(pet)): PetCharacter = PetCharacter(
        id = "petdex:${pet.slug}",
        displayName = pet.displayName,
        source = CharacterSource.PETDEX,
        submittedBy = pet.submittedBy,
        filePath = file.absolutePath,
        spritesheetUrl = pet.spritesheetUrl,
    )

    fun isAllowed(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return runCatching {
            val u = URI(url).toURL()
            u.protocol == "https" && u.host == ASSET_HOST
        }.getOrDefault(false)
    }

    private fun cacheDir(): Path {
        val dir = Paths.get(PathManager.getSystemPath(), "ideDeskPet", "pets")
        Files.createDirectories(dir)
        return dir
    }

    private fun extOf(url: String): String {
        val path = runCatching { URI(url).path ?: "" }.getOrDefault("")
        val ext = path.substringAfterLast('.', "").lowercase()
        return if (ext.length in 1..5) ext else "webp"
    }

    private fun sanitize(s: String): String = s.replace(Regex("[^A-Za-z0-9_-]"), "_").take(60).ifBlank { "pet" }

    private fun JsonObject.str(key: String): String {
        val e = get(key) ?: return ""
        return if (e.isJsonNull) "" else runCatching { e.asString }.getOrDefault("")
    }
}
