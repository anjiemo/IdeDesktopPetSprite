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

/** 形象来源 */
enum class CharacterSource { BUILTIN, LOCAL, PETDEX }

/**
 * 一个宠物形象。内置形象只有元信息（精灵图来自资源）；
 * 本地 / Petdex 形象的精灵图落地到 [filePath]（Petdex 另保留远程 [spritesheetUrl]）。
 */
data class PetCharacter(
    val id: String,
    val displayName: String,
    val source: CharacterSource,
    val submittedBy: String = "",
    /** 本地文件 / Petdex 下载缓存的精灵图路径 */
    val filePath: String? = null,
    /** Petdex 远程精灵图地址（仅记录，便于重新下载） */
    val spritesheetUrl: String? = null,
) {
    val isBuiltin: Boolean get() = source == CharacterSource.BUILTIN

    /** 列表里展示的副标题：作者 / 来源 */
    val subtitle: String
        get() = when (source) {
            CharacterSource.BUILTIN -> "内置"
            CharacterSource.LOCAL -> "本地导入"
            CharacterSource.PETDEX -> if (submittedBy.isNotBlank()) "Petdex · @$submittedBy" else "Petdex"
        }

    companion object {
        /** 内置默认形象（原创史莱姆 Gel） */
        val BUILTIN = PetCharacter(
            id = "gel-slime",
            displayName = "Gel 史莱姆",
            source = CharacterSource.BUILTIN,
            submittedBy = "anjiemo",
        )
    }
}
