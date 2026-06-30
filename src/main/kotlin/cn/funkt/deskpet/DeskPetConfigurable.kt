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

import cn.funkt.deskpet.character.CharacterPickerDialog
import cn.funkt.deskpet.character.PetCharacterStore
import cn.funkt.deskpet.character.SpriteLoader
import cn.funkt.deskpet.character.SpritePreviewComponent
import cn.funkt.deskpet.character.PetThumbnails
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * Settings → Tools → Desktop Pet Sprite（桌面宠物）配置页。
 */
class DeskPetConfigurable : Configurable {

    private val settings get() = DeskPetSettings.getInstance()
    private val charStore get() = PetCharacterStore.getInstance()

    private val enabled = JBCheckBox("启用桌面宠物")
    private val sizeCombo = ComboBox(arrayOf("小", "中", "大"))
    private val reactSync = JBCheckBox("Gradle 同步")
    private val reactIndex = JBCheckBox("建立索引")
    private val reactBuild = JBCheckBox("构建 / 编译")
    private val reactRun = JBCheckBox("运行 / 调试")

    // 形象
    private val defaultCharLabel = JBLabel()
    private val projectCharLabel = JBLabel()
    private val projectPreview = SpritePreviewComponent(Dimension(72, 78))
    private val setProjectCharBtn = JButton("为所选项目设置形象…")
    private val followDefaultBtn = JButton("跟随默认形象")
    private val clearCacheBtn = JButton("清除下载及缩略图缓存")

    /**
     * item = 项目 key；勾选 = 显示（未被永久关闭）
     */
    private val projectList = CheckBoxList<String>()

    override fun getDisplayName(): String = DISPLAY_NAME

    companion object {
        /**
         * 设置页左侧列表显示名；以 D 开头便于 Tools 分组内字母序靠前，配合 plugin.xml groupWeight。
         */
        const val DISPLAY_NAME = "Desktop Pet Sprite（桌面宠物）"
    }

    override fun createComponent(): JComponent {
        projectList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        projectList.addListSelectionListener { if (!it.valueIsAdjusting) refreshProjectChar() }

        val showSel = JButton("显示所选").apply { addActionListener { setSelected(true) } }
        val hideSel = JButton("隐藏所选").apply { addActionListener { setSelected(false) } }
        val showAll = JButton("全部显示").apply { addActionListener { setAll(true) } }
        val hideAll = JButton("全部隐藏").apply { addActionListener { setAll(false) } }
        val buttons = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            add(showSel)
            add(hideSel)
            add(showAll)
            add(hideAll)
        }

        val states = JPanel(FlowLayout(FlowLayout.LEFT, 12, 0)).apply {
            isOpaque = false
            add(reactSync)
            add(reactIndex)
            add(reactBuild)
            add(reactRun)
        }

        // 默认形象
        val defaultCharPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            isOpaque = false
            add(defaultCharLabel)
            add(JButton("切换默认形象…").apply { addActionListener { changeDefault() } })
        }

        val listPanel = JPanel(BorderLayout()).apply {
            add(JBScrollPane(projectList).also { it.preferredSize = JBUI.size(380, 150) }, BorderLayout.CENTER)
            add(buttons, BorderLayout.SOUTH)
        }

        // 所选项目的形象（预览 + 设置 / 跟随默认）
        setProjectCharBtn.addActionListener { setProjectCharacter() }
        followDefaultBtn.addActionListener { followDefault() }
        clearCacheBtn.addActionListener {
            val result = com.intellij.openapi.ui.Messages.showYesNoDialog(
                "确定要清除所有已下载的形象和缩略图缓存吗？这会删除本地所有已下载的在线形象，并在需要时重新下载。",
                "清除缓存",
                "清除",
                "取消",
                com.intellij.openapi.ui.Messages.getQuestionIcon()
            )
            if (result == com.intellij.openapi.ui.Messages.YES) {
                SpriteLoader.clearCache()
                PetThumbnails.clearCache()
                com.intellij.openapi.ui.Messages.showInfoMessage("缓存已清除完成！", "提示")
            }
        }
        val pcButtons = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            isOpaque = false
            add(setProjectCharBtn)
            add(followDefaultBtn)
        }
        val pcRight = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(projectCharLabel, BorderLayout.NORTH)
            add(pcButtons, BorderLayout.SOUTH)
        }
        val projectCharPanel = JPanel(BorderLayout(10, 0)).apply {
            isOpaque = false
            add(projectPreview, BorderLayout.WEST)
            add(pcRight, BorderLayout.CENTER)
        }

        val root = panel {
            row { cell(enabled) }
            row("默认尺寸：") { cell(sizeCombo) }
            row("默认形象：") { cell(defaultCharPanel) }
            row("响应状态：") { cell(states) }
            row("缓存管理：") { cell(clearCacheBtn) }
            row { label("按项目显示（勾选 = 显示该项目的宠物；取消 = 永久关闭）") }
            row { cell(listPanel).align(AlignX.FILL) }
            row { label("所选项目的形象（在上方列表中选中某项目后查看 / 设置）") }
            row { cell(projectCharPanel).align(AlignX.FILL) }
        }
        reset()
        return root
    }

    // ---------------- 形象 ----------------

    private fun selectedProjectKey(): String? {
        val idx = projectList.selectedIndex
        return if (idx < 0) null else projectList.getItemAt(idx)
    }

    private fun projectForKey(key: String): Project? =
        ProjectManager.getInstance().openProjects.firstOrNull { !it.isDefault && DeskPetSettings.keyOf(it) == key }

    private fun refreshDefaultChar() {
        val c = charStore.defaultCharacter()
        defaultCharLabel.text = "${c.displayName} · ${c.subtitle}"
    }

    private fun refreshProjectChar() {
        val key = selectedProjectKey()
        if (key == null) {
            projectCharLabel.text = "（未选择项目）"
            projectPreview.sheet = null
            setProjectCharBtn.isEnabled = false
            followDefaultBtn.isEnabled = false
            return
        }
        val c = charStore.characterFor(key)
        val override = charStore.hasProjectOverride(key)
        projectCharLabel.text = "${c.displayName} · ${c.subtitle}" + if (override) "（项目专用）" else "（跟随默认）"
        projectPreview.sheet = SpriteLoader.load(c)
        setProjectCharBtn.isEnabled = true
        followDefaultBtn.isEnabled = override
    }

    private fun changeDefault() {
        val dialog = CharacterPickerDialog(null, charStore.defaultCharacter())
        if (dialog.showAndGet()) {
            dialog.result?.let { charStore.setDefault(it) }
            refreshDefaultChar()
            refreshProjectChar()
            applyCharToAll()
        }
    }

    private fun setProjectCharacter() {
        val key = selectedProjectKey() ?: return
        val dialog = CharacterPickerDialog(projectForKey(key), charStore.characterFor(key))
        if (dialog.showAndGet()) {
            dialog.result?.let { charStore.setForProject(key, it) }
            refreshProjectChar()
            applyCharToAll()
        }
    }

    private fun followDefault() {
        val key = selectedProjectKey() ?: return
        charStore.setForProject(key, null)
        refreshProjectChar()
        applyCharToAll()
    }

    private fun applyCharToAll() {
        for (p in ProjectManager.getInstance().openProjects) {
            if (p.isDefault) continue
            p.serviceIfCreated<PetController>()?.applyCharacter()
        }
    }

    override fun disposeUIResources() {
        projectPreview.stop()
    }

    private fun setAll(visible: Boolean) {
        for (i in 0 until projectList.itemsCount) {
            projectList.getItemAt(i)?.let { projectList.setItemSelected(it, visible) }
        }
        projectList.repaint()
    }

    private fun setSelected(visible: Boolean) {
        for (i in projectList.selectedIndices) {
            projectList.getItemAt(i)?.let { projectList.setItemSelected(it, visible) }
        }
        projectList.repaint()
    }

    private fun rebuildList() {
        projectList.clear()
        val keys = LinkedHashMap<String, String>() // key -> 展示名
        for (p in ProjectManager.getInstance().openProjects) {
            if (p.isDefault) continue
            keys[DeskPetSettings.keyOf(p)] = p.name
        }
        for (k in settings.hiddenKeys()) keys.putIfAbsent(k, DeskPetSettings.displayName(k))
        for ((k, name) in keys) {
            projectList.addItem(k, name, !settings.isHidden(k)) // 勾选 = 可见
        }
    }

    private fun sizeIndex(code: String) = when (code) {
        "S" -> 0
        "L" -> 2
        else -> 1
    }

    private fun sizeCode(index: Int) = when (index) {
        0 -> "S"
        2 -> "L"
        else -> "M"
    }

    override fun isModified(): Boolean {
        if (enabled.isSelected != settings.enabled) return true
        if (sizeCode(sizeCombo.selectedIndex) != settings.size) return true
        if (reactSync.isSelected != settings.reactSync) return true
        if (reactIndex.isSelected != settings.reactIndex) return true
        if (reactBuild.isSelected != settings.reactBuild) return true
        if (reactRun.isSelected != settings.reactRun) return true
        for (i in 0 until projectList.itemsCount) {
            val key = projectList.getItemAt(i) ?: continue
            // 勾选=可见；isHidden==可见 即表示有改动
            if (settings.isHidden(key) == projectList.isItemSelected(i)) return true
        }
        return false
    }

    override fun apply() {
        settings.enabled = enabled.isSelected
        settings.size = sizeCode(sizeCombo.selectedIndex)
        settings.reactSync = reactSync.isSelected
        settings.reactIndex = reactIndex.isSelected
        settings.reactBuild = reactBuild.isSelected
        settings.reactRun = reactRun.isSelected
        for (i in 0 until projectList.itemsCount) {
            val key = projectList.getItemAt(i) ?: continue
            settings.setHidden(key, !projectList.isItemSelected(i))
        }
        // 实时生效到所有已打开的项目
        for (p in ProjectManager.getInstance().openProjects) {
            if (p.isDefault) continue
            p.serviceIfCreated<PetController>()?.applySettings()
        }
    }

    override fun reset() {
        enabled.isSelected = settings.enabled
        sizeCombo.selectedIndex = sizeIndex(settings.size)
        reactSync.isSelected = settings.reactSync
        reactIndex.isSelected = settings.reactIndex
        reactBuild.isSelected = settings.reactBuild
        reactRun.isSelected = settings.reactRun
        rebuildList()
        refreshDefaultChar()
        refreshProjectChar()
    }
}
