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

package cn.funkt.deskpet.listeners

import cn.funkt.deskpet.PetController
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.FileUtil

/** Gradle 事件共享分发逻辑（新版 / 旧版监听器共用）。 */
internal object DeskPetExternalSystemBridge {

    private fun keyOf(id: ExternalSystemTaskId): String? = when (id.type) {
        ExternalSystemTaskType.RESOLVE_PROJECT -> PetController.KEY_SYNC
        ExternalSystemTaskType.EXECUTE_TASK -> PetController.KEY_GRADLE
        else -> null
    }

    private fun controllerOf(projectPath: String, id: ExternalSystemTaskId): PetController? {
        val project = resolveProject(projectPath, id) ?: return null
        if (project.isDisposed) return null
        return runCatching { project.service<PetController>() }.getOrNull()
    }

    private fun resolveProject(projectPath: String, id: ExternalSystemTaskId): Project? {
        if (projectPath.isNotBlank()) {
            ProjectManager.getInstance().openProjects.firstOrNull { p ->
                !p.isDisposed && p.basePath != null && FileUtil.pathsEqual(p.basePath, projectPath)
            }?.let { return it }
        }
        val ideProjectId = id.ideProjectId
        return ProjectManager.getInstance().openProjects.firstOrNull { p ->
            !p.isDisposed && ExternalSystemTaskId.getProjectId(p) == ideProjectId
        }
    }

    fun dispatchStart(projectPath: String, id: ExternalSystemTaskId) {
        val key = keyOf(id) ?: return
        controllerOf(projectPath, id)?.onStart(key)
    }

    fun dispatchFinish(projectPath: String, id: ExternalSystemTaskId, success: Boolean) {
        val key = keyOf(id) ?: return
        controllerOf(projectPath, id)?.onFinish(key, success)
    }
}
