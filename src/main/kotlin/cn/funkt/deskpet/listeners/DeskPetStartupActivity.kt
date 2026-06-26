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
import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskListener
import com.intellij.task.ProjectTaskManager

/**
 * 项目启动时：展示宠物，并订阅「编译/构建」与「运行/调试」两类事件。
 * Gradle Sync 由 [DeskPetExternalSystemListener] 单独处理。
 */
class DeskPetStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val controller = project.service<PetController>()
        controller.start()

        val conn = project.messageBus.connect(controller)

        // 编译 / Make / 构建（IDE 内部 ProjectTask）
        conn.subscribe(ProjectTaskListener.TOPIC, object : ProjectTaskListener {
            override fun started(context: ProjectTaskContext) {
                controller.onStart(PetController.KEY_COMPILE)
            }

            override fun finished(result: ProjectTaskManager.Result) {
                controller.onFinish(
                    PetController.KEY_COMPILE,
                    !result.hasErrors() && !result.isAborted,
                )
            }
        })

        // 建立索引（dumb mode 进入/退出）。索引高频，结束时不弹结果表情，静默回待机。
        conn.subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
            override fun enteredDumbMode() {
                controller.onStart(PetController.KEY_INDEX)
            }

            override fun exitDumbMode() {
                controller.onFinish(PetController.KEY_INDEX, success = true, flashResult = false)
            }
        })

        // 运行 / 调试（执行进程）
        conn.subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
            override fun processStarting(executorId: String, env: ExecutionEnvironment) {
                controller.onStart(PetController.KEY_RUN)
            }

            override fun processNotStarted(executorId: String, env: ExecutionEnvironment) {
                controller.onFinish(PetController.KEY_RUN, false)
            }

            override fun processTerminated(
                executorId: String,
                env: ExecutionEnvironment,
                handler: ProcessHandler,
                exitCode: Int,
            ) {
                controller.onFinish(PetController.KEY_RUN, exitCode == 0)
            }
        })
    }
}
