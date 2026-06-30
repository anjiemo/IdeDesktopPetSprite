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
 * Gradle / 外部系统任务监听（2025.1+ 新版 [projectPath] API）。
 * 2024.2–2024.3 旧版入口见 [DeskPetExternalSystemListenerLegacy]。
 */
class DeskPetExternalSystemListener : ExternalSystemTaskNotificationListener {

    override fun onStart(projectPath: String, id: ExternalSystemTaskId) {
        DeskPetExternalSystemBridge.dispatchStart(projectPath, id)
    }

    override fun onSuccess(projectPath: String, id: ExternalSystemTaskId) {
        DeskPetExternalSystemBridge.dispatchFinish(projectPath, id, success = true)
    }

    override fun onFailure(projectPath: String, id: ExternalSystemTaskId, exception: Exception) {
        DeskPetExternalSystemBridge.dispatchFinish(projectPath, id, success = false)
    }

    override fun onCancel(projectPath: String, id: ExternalSystemTaskId) {
        DeskPetExternalSystemBridge.dispatchFinish(projectPath, id, success = false)
    }
}
