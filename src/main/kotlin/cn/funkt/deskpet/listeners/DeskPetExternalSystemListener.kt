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
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType

/**
 * 监听外部系统（Gradle）任务：
 * - RESOLVE_PROJECT  → Gradle Sync（同步）
 * - EXECUTE_TASK     → Gradle 任务执行（构建 / assemble 等）
 * 该监听器是应用级单例，通过 task id 反查所属项目并路由到对应 PetController。
 * 这里覆盖的是单参回调签名（2024.2 接口的实际方法）。在更高版本平台上，
 * 新增的 (projectPath, id) 重载会通过默认方法链回调到这些单参方法，故新旧通用。
 */
@Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
class DeskPetExternalSystemListener : ExternalSystemTaskNotificationListener {

    private fun keyOf(id: ExternalSystemTaskId): String? = when (id.type) {
        ExternalSystemTaskType.RESOLVE_PROJECT -> PetController.KEY_SYNC
        ExternalSystemTaskType.EXECUTE_TASK -> PetController.KEY_GRADLE
        else -> null
    }

    private fun controllerOf(id: ExternalSystemTaskId): PetController? {
        val project = id.findProject() ?: return null
        if (project.isDisposed) return null
        return runCatching { project.service<PetController>() }.getOrNull()
    }

    override fun onStart(id: ExternalSystemTaskId, workingDir: String?) {
        val key = keyOf(id) ?: return
        controllerOf(id)?.onStart(key)
    }

    override fun onSuccess(id: ExternalSystemTaskId) {
        val key = keyOf(id) ?: return
        controllerOf(id)?.onFinish(key, true)
    }

    override fun onFailure(id: ExternalSystemTaskId, e: Exception) {
        val key = keyOf(id) ?: return
        controllerOf(id)?.onFinish(key, false)
    }

    override fun onCancel(id: ExternalSystemTaskId) {
        val key = keyOf(id) ?: return
        controllerOf(id)?.onFinish(key, false)
    }
}
