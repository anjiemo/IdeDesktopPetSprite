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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

/**
 * 应用级持久化设置：启用开关、默认尺寸、按状态响应开关、被「永久关闭」的项目列表。
 */
@Service(Service.Level.APP)
@State(name = "IdeDeskPetSettings", storages = [Storage("ideDeskPet.xml")])
class DeskPetSettings : PersistentStateComponent<DeskPetSettings.State> {

    class State {
        var enabled: Boolean = true
        var size: String = "M"                 // S / M / L
        var reactSync: Boolean = true
        var reactIndex: Boolean = true
        var reactBuild: Boolean = true
        var reactRun: Boolean = true

        /** 被「永久关闭」的项目 key（basePath，缺省回退 name） */
        var hiddenKeys: MutableList<String> = ArrayList()
    }

    private var st = State()
    override fun getState(): State = st
    override fun loadState(s: State) {
        st = s
    }

    var enabled: Boolean
        get() = st.enabled
        set(v) {
            st.enabled = v
        }

    var size: String
        get() = st.size
        set(v) {
            st.size = v
        }

    /** 默认尺寸对应的缩放系数 */
    val scale: Double
        get() = when (st.size) {
            "S" -> 0.45
            "L" -> 0.85
            else -> 0.62
        }

    var reactSync: Boolean
        get() = st.reactSync
        set(v) {
            st.reactSync = v
        }
    var reactIndex: Boolean
        get() = st.reactIndex
        set(v) {
            st.reactIndex = v
        }
    var reactBuild: Boolean
        get() = st.reactBuild
        set(v) {
            st.reactBuild = v
        }
    var reactRun: Boolean
        get() = st.reactRun
        set(v) {
            st.reactRun = v
        }

    /** 某类活动是否响应 */
    fun reactsTo(key: String): Boolean = when (key) {
        PetController.KEY_SYNC -> st.reactSync
        PetController.KEY_INDEX -> st.reactIndex
        PetController.KEY_COMPILE, PetController.KEY_GRADLE -> st.reactBuild
        PetController.KEY_RUN -> st.reactRun
        else -> true
    }

    fun isHidden(key: String): Boolean = st.hiddenKeys.contains(key)

    fun setHidden(key: String, hidden: Boolean) {
        st.hiddenKeys.remove(key)
        if (hidden) st.hiddenKeys.add(key)
    }

    fun hiddenKeys(): List<String> = st.hiddenKeys.toList()

    companion object {
        fun getInstance(): DeskPetSettings =
            ApplicationManager.getApplication().getService(DeskPetSettings::class.java)

        /** 项目唯一键：优先 basePath，回退 name */
        fun keyOf(project: Project): String = project.basePath ?: project.name

        /** 由 key 推断展示名（已关闭项目时用） */
        fun displayName(key: String): String {
            val norm = key.trimEnd('/', '\\')
            val seg = norm.substringAfterLast('/').substringAfterLast('\\')
            return seg.ifEmpty { key }
        }
    }
}
