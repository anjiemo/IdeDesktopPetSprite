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

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener

/**
 * 2024.2–2024.3 平台仍通过旧签名分发 Gradle 事件；单独类承接 deprecated 入口，
 * 主监听器 [DeskPetExternalSystemListener] 仅保留 2025.1+ 新版 projectPath API。
 */
internal class DeskPetExternalSystemListenerLegacy : ExternalSystemTaskNotificationListener {

    // 2025.1+ 已由 [DeskPetExternalSystemListener] 处理；拦截默认实现以免重复分发
    override fun onStart(projectPath: String, id: ExternalSystemTaskId) = Unit

    override fun onSuccess(projectPath: String, id: ExternalSystemTaskId) = Unit

    override fun onFailure(projectPath: String, id: ExternalSystemTaskId, exception: Exception) = Unit

    override fun onCancel(projectPath: String, id: ExternalSystemTaskId) = Unit

    // 2024.2–2024.3 旧平台入口
    override fun onStart(id: ExternalSystemTaskId, workingDir: String?) {
        DeskPetExternalSystemBridge.dispatchStart(workingDir.orEmpty(), id)
    }

    override fun onSuccess(id: ExternalSystemTaskId) {
        DeskPetExternalSystemBridge.dispatchFinish("", id, success = true)
    }

    override fun onFailure(id: ExternalSystemTaskId, e: Exception) {
        DeskPetExternalSystemBridge.dispatchFinish("", id, success = false)
    }

    override fun onCancel(id: ExternalSystemTaskId) {
        DeskPetExternalSystemBridge.dispatchFinish("", id, success = false)
    }
}
