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

package cn.funkt.deskpet.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.util.concurrency.AppExecutorUtil

/**
 * 统一 EDT / 后台调度，避免直接使用 ModalityState.any() 与已废弃的 Application.executeOnPooledThread。
 * 桌宠仅更新 Swing 悬浮窗，使用 [ModalityState.defaultModalityState] 即可。
 */
object DeskPetUi {

    fun runOnEdt(action: () -> Unit) {
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            action()
        } else {
            app.invokeLater(action, ModalityState.defaultModalityState())
        }
    }

    fun runInBackground(action: () -> Unit) {
        AppExecutorUtil.getAppExecutorService().execute(action)
    }
}
