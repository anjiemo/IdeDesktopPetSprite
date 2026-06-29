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
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.util.xmlb.annotations.XMap

/**
 * 应用级形象库与选择：
 * - 默认形象（全局）
 * - 按项目覆盖（projectKey → 形象 id）
 * - 已添加形象库（本地导入 + 从 Petdex 下载的）
 *
 * 内置形象不入库，以 [PetCharacter.BUILTIN] 隐式存在。
 */
@Service(Service.Level.APP)
@State(name = "IdeDeskPetCharacters", storages = [Storage("ideDeskPet.xml")])
class PetCharacterStore : PersistentStateComponent<PetCharacterStore.State> {

    class Bean {
        var id: String = ""
        var displayName: String = ""
        var source: String = CharacterSource.PETDEX.name
        var submittedBy: String = ""
        var filePath: String = ""
        var spritesheetUrl: String = ""
    }

    class State {
        /** 空 = 使用内置形象 */
        var defaultId: String = ""

        @get:XCollection(style = XCollection.Style.v2)
        var library: MutableList<Bean> = ArrayList()

        @get:XMap(entryTagName = "project", keyAttributeName = "key", valueAttributeName = "characterId")
        var projectIds: MutableMap<String, String> = LinkedHashMap()
    }

    private var st = State()
    override fun getState(): State = st
    override fun loadState(s: State) {
        st = s
    }

    // ---- 查询 ----

    /** 按 id 找形象；id 为空 / 内置 id 时返回内置；库中找不到返回 null */
    fun find(id: String?): PetCharacter? {
        if (id.isNullOrBlank() || id == PetCharacter.BUILTIN.id) return PetCharacter.BUILTIN
        return st.library.firstOrNull { it.id == id }?.toCharacter()
    }

    fun defaultCharacter(): PetCharacter = find(st.defaultId) ?: PetCharacter.BUILTIN

    /** 某项目实际使用的形象：项目覆盖优先，否则默认 */
    fun characterFor(projectKey: String): PetCharacter =
        find(st.projectIds[projectKey]) ?: defaultCharacter()

    /** 该项目是否设置了独立形象（未设置则跟随默认） */
    fun hasProjectOverride(projectKey: String): Boolean = st.projectIds.containsKey(projectKey)

    /** 内置 + 已添加 */
    fun library(): List<PetCharacter> =
        buildList {
            add(PetCharacter.BUILTIN)
            st.library.forEach { add(it.toCharacter()) }
        }

    // ---- 变更 ----

    /** 加入 / 更新库（内置忽略） */
    fun addOrUpdate(c: PetCharacter) {
        if (c.isBuiltin) return
        val existing = st.library.firstOrNull { it.id == c.id }
        if (existing != null) existing.copyFrom(c) else st.library.add(Bean().apply { copyFrom(c) })
    }

    fun remove(id: String) {
        if (id == PetCharacter.BUILTIN.id) return
        st.library.removeAll { it.id == id }
        if (st.defaultId == id) st.defaultId = ""
        st.projectIds.entries.removeAll { it.value == id }
    }

    fun setDefault(c: PetCharacter) {
        addOrUpdate(c)
        st.defaultId = if (c.isBuiltin) "" else c.id
    }

    /** 设置某项目形象；c 为 null 表示清除覆盖（跟随默认） */
    fun setForProject(projectKey: String, c: PetCharacter?) {
        if (c == null) {
            st.projectIds.remove(projectKey)
        } else {
            addOrUpdate(c)
            st.projectIds[projectKey] = c.id
        }
    }

    private fun Bean.toCharacter(): PetCharacter = PetCharacter(
        id = id,
        displayName = displayName.ifBlank { id },
        source = runCatching { CharacterSource.valueOf(source) }.getOrDefault(CharacterSource.PETDEX),
        submittedBy = submittedBy,
        filePath = filePath.ifBlank { null },
        spritesheetUrl = spritesheetUrl.ifBlank { null },
    )

    private fun Bean.copyFrom(c: PetCharacter) {
        id = c.id
        displayName = c.displayName
        source = c.source.name
        submittedBy = c.submittedBy
        filePath = c.filePath ?: ""
        spritesheetUrl = c.spritesheetUrl ?: ""
    }

    companion object {
        fun getInstance(): PetCharacterStore =
            ApplicationManager.getApplication().getService(PetCharacterStore::class.java)
    }
}
