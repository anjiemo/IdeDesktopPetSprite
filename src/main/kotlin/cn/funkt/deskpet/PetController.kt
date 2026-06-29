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

import cn.funkt.deskpet.character.CharacterSource
import cn.funkt.deskpet.character.PetCharacter
import cn.funkt.deskpet.character.PetCharacterStore
import cn.funkt.deskpet.character.PetdexClient
import cn.funkt.deskpet.character.SpriteLoader
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File
import javax.swing.Timer
import kotlinx.coroutines.*

/**
 * 项目级状态机：汇总 sync / 编译 / gradle 任务 / 运行 等事件，
 * 计算出当前应展示的宠物状态并推给悬浮窗。
 *
 * 是否显示、尺寸、按状态响应等取自应用级 [DeskPetSettings]。
 * 所有可变状态只在 EDT 上读写，外部事件统一经 [onUi] 切到 EDT。
 */
@Service(Service.Level.PROJECT)
class PetController(private val project: Project) : Disposable {

    @Volatile
    private var disposed = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var window: PetWindow? = null
    private val active = LinkedHashSet<String>()
    private var flashTimer: Timer? = null

    private val settings get() = DeskPetSettings.getInstance()
    private val projectKey get() = DeskPetSettings.keyOf(project)

    /** 全局启用、且该项目未被「永久关闭」，才允许显示 */
    private fun isAllowed(): Boolean = settings.enabled && !settings.isHidden(projectKey)

    /** 项目打开时调用：创建并展示待机宠物 */
    fun start() = onUi {
        if (!isAllowed()) return@onUi
        ensureWindow()
        applyState(baseState())
    }

    /** 某项活动开始 */
    fun onStart(key: String) = onUi {
        if (!settings.reactsTo(key) || !isAllowed()) return@onUi
        cancelFlash()
        active.add(key)
        applyState(baseState())
    }

    /**
     * 某项活动结束；success 决定成功/失败的短暂表情。
     * flashResult=false 时不弹成功/失败表情（用于索引这类高频活动）。
     */
    fun onFinish(key: String, success: Boolean, flashResult: Boolean = true) = onUi {
        if (!settings.reactsTo(key)) return@onUi
        if (!active.remove(key)) return@onUi
        if (active.isEmpty() && flashResult) {
            flash(if (success) PetState.SUCCESS else PetState.ERROR)
        } else {
            cancelFlash()
            applyState(baseState())
        }
    }

    /** 设置变化后实时生效（配置页 Apply 调用） */
    fun applySettings() = onUi {
        if (!isAllowed()) {
            // 总开关关闭或本项目被永久关闭 → 收起窗口
            cancelFlash()
            active.clear()
            window?.dispose()
            window = null
            return@onUi
        }
        ensureWindow()
        window?.setScale(settings.scale)
        window?.setSheet(currentSheet())
        applyState(baseState())
    }

    /** 形象切换后实时换装 */
    fun applyCharacter() = onUi {
        if (!isAllowed()) return@onUi
        ensureWindow()
        val c = PetCharacterStore.getInstance().characterFor(projectKey)
        window?.setSheet(SpriteLoader.load(c))
        applyState(baseState())

        // 如果是网络形象且本地文件不存在（说明尚未下载完就被选中使用），在后台启动下载
        if (c.source == CharacterSource.PETDEX && !c.filePath.isNullOrBlank()) {
            val file = File(c.filePath)
            if (!file.isFile || file.length() == 0L) {
                downloadActiveCharacter(c)
            }
        }
    }

    /** 解析本项目当前应使用的精灵图（形象） */
    private fun currentSheet() =
        SpriteLoader.load(PetCharacterStore.getInstance().characterFor(projectKey))

    /** 优先级：运行 > 构建/编译/Gradle 任务 > 同步 > 索引 > 待机 */
    private fun baseState(): PetState = when {
        KEY_RUN in active -> PetState.RUNNING
        KEY_COMPILE in active || KEY_GRADLE in active -> PetState.BUILDING
        KEY_SYNC in active -> PetState.SYNCING
        KEY_INDEX in active -> PetState.INDEXING
        else -> PetState.IDLE
    }

    private fun flash(state: PetState) {
        cancelFlash()
        applyState(state)
        flashTimer = Timer(FLASH_MS) {
            cancelFlash()
            applyState(baseState())
        }.apply {
            isRepeats = false
            start()
        }
    }

    private fun cancelFlash() {
        flashTimer?.stop()
        flashTimer = null
    }

    private fun ensureWindow() {
        if (disposed || !isAllowed() || window != null) return
        window = PetWindow(project).also {
            it.setSheet(currentSheet())
            it.showPet()
        }
    }

    private fun applyState(state: PetState) {
        if (disposed) return
        ensureWindow()
        window?.setState(state)
    }

    private fun onUi(block: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(
            { if (!disposed) block() },
            ModalityState.any(),
        )
    }

    private fun downloadActiveCharacter(c: PetCharacter) {
        val slug = c.id.substringAfter("petdex:")
        val url = c.spritesheetUrl ?: return
        val pet = PetdexClient.Pet(
            slug = slug,
            displayName = c.displayName,
            kind = "",
            submittedBy = c.submittedBy,
            spritesheetUrl = url,
            petJsonUrl = "",
            zipUrl = "",
        )
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    PetdexClient.download(pet)
                }
                // 下载完成后，如果在下载期间用户没有换成别的宠物，则在 UI 线程应用新形象
                ApplicationManager.getApplication().invokeLater({
                    val current = PetCharacterStore.getInstance().characterFor(projectKey)
                    if (current.id == c.id) {
                        SpriteLoader.invalidate(c.id) // 确保重新加载刚刚下载的文件
                        applyCharacter()
                    }
                }, ModalityState.any())
            } catch (e: Exception) {
                // 下载失败静默退出
            }
        }
    }

    override fun dispose() {
        disposed = true
        scope.cancel()
        cancelFlash()
        window?.dispose()
        window = null
        active.clear()
    }

    companion object {
        const val KEY_SYNC = "sync"
        const val KEY_INDEX = "index"
        const val KEY_COMPILE = "compile"
        const val KEY_GRADLE = "gradle"
        const val KEY_RUN = "run"

        private const val FLASH_MS = 2600
    }
}
