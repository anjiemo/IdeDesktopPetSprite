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

import cn.funkt.deskpet.PetSprite
import cn.funkt.deskpet.SpriteSheet
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.io.File
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

/**
 * 切换形象对话框：在线形象库（Petdex，网格预览）/ 本地上传 / 已添加（含内置，网格预览）。
 * 网格缩略图按视口懒加载、并发预取（不再点击后才下载），且只解码小图避免卡顿；
 * 选中后通过 [result] 返回所选形象。
 */
class CharacterPickerDialog(
    private val project: Project?,
    private val current: PetCharacter,
) : DialogWrapper(project, true) {

    var result: PetCharacter? = null
        private set

    private val store get() = PetCharacterStore.getInstance()

    private val tabs = JBTabbedPane()
    private val preview = SpritePreviewComponent()
    private val selectedNameLabel = JBLabel()

    /** 右侧动态预览用的整张精灵图缓存（LRU，最多保留几张，避免大图越积越多） */
    private val previewCache = object : LinkedHashMap<String, SpriteSheet>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SpriteSheet>): Boolean =
            size > PREVIEW_CACHE_MAX
    }
    /** slug → Pet，便于选中 Petdex 形象时按需下载整图供预览 / 应用 */
    private val petByCharId = HashMap<String, PetdexClient.Pet>()

    // 各 tab 当前选择
    private var petdexSelected: PetCharacter? = null
    private var localSelected: PetCharacter? = null
    private var librarySelected: PetCharacter? = null
    private var selected: PetCharacter? = null
    private var previewToken = 0

    // Petdex
    private val petdexSearch = SearchTextField()
    private val petdexKind = ComboBox<String>()
    private val petdexStatus = JBLabel(" ")
    private val petdexGrid = PetGridView(
        onSelect = { item ->
            petdexSelected = item?.character
            onSelectionChanged()
        },
        onActivate = { item ->
            petdexSelected = item.character
            onSelectionChanged()
            doOKAction()
        },
    )
    private var petdexAllPets: List<PetdexClient.Pet> = emptyList()
    private var petdexLoaded = false
    private var petdexToken = 0

    // 本地
    private val localNameField = JBTextField()
    private val localStatus = JBLabel(" ")
    private var pendingLocalPath: String? = null
    private var pendingLocalSheet: SpriteSheet? = null
    private var localToken = 0

    // 已添加 / 内置
    private val libraryGrid = PetGridView(
        onSelect = { item ->
            librarySelected = item?.character
            updateRemoveBtn()
            onSelectionChanged()
        },
        onActivate = { item ->
            librarySelected = item.character
            onSelectionChanged()
            doOKAction()
        },
    )
    private val libraryRemoveBtn = JButton("从库中删除")

    init {
        title = "选择形象"
        setOKButtonText("使用此形象")
        init()
        reloadLibrary()
        loadPetdex(force = false)
        refreshSelection()
    }

    override fun createCenterPanel(): JComponent {
        tabs.addTab("在线形象库（Petdex）", buildPetdexPanel())
        tabs.addTab("本地上传", buildLocalPanel())
        tabs.addTab("已添加 / 内置", buildLibraryPanel())
        tabs.selectedIndex = 0
        tabs.addChangeListener {
            if (tabs.selectedIndex == 0 && !petdexLoaded) loadPetdex(force = false)
            refreshSelection()
        }

        val right = JPanel(BorderLayout()).apply {
            preferredSize = JBUI.size(190, 480)
            border = JBUI.Borders.emptyLeft(12)
            add(JBLabel("预览").apply { border = JBUI.Borders.emptyBottom(6) }, BorderLayout.NORTH)
            add(preview, BorderLayout.CENTER)
            add(selectedNameLabel.apply { border = JBUI.Borders.emptyTop(6) }, BorderLayout.SOUTH)
        }

        return JPanel(BorderLayout()).apply {
            preferredSize = JBUI.size(780, 520)
            add(tabs, BorderLayout.CENTER)
            add(right, BorderLayout.EAST)
        }
    }

    // ---------------- Petdex ----------------

    private fun buildPetdexPanel(): JComponent {
        petdexSearch.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = refilterPetdex()
        })
        petdexKind.model = DefaultComboBoxModel(arrayOf(ALL_KINDS))
        petdexKind.preferredSize = Dimension(150, petdexKind.preferredSize.height)
        petdexKind.addActionListener { refilterPetdex() }

        val refreshBtn = JButton("刷新").apply { addActionListener { loadPetdex(force = true) } }
        val east = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            add(petdexKind)
            add(refreshBtn)
        }
        val top = JPanel(BorderLayout(6, 0)).apply {
            add(petdexSearch, BorderLayout.CENTER)
            add(east, BorderLayout.EAST)
        }
        return JPanel(BorderLayout(0, 6)).apply {
            border = JBUI.Borders.empty(8)
            add(top, BorderLayout.NORTH)
            add(petdexGrid.component, BorderLayout.CENTER)
            add(petdexStatus, BorderLayout.SOUTH)
        }
    }

    private fun loadPetdex(force: Boolean) {
        // 先用上次落盘的清单立即铺出网格（二次打开秒开），再后台拉取最新并替换
        if (!force && !petdexLoaded) {
            PetdexClient.cachedManifest()?.let { cached ->
                petdexLoaded = true
                petdexAllPets = cached
                rebuildKindOptions(cached)
                refilterPetdex(resetScroll = true)
            }
        }
        petdexStatus.text = if (petdexLoaded) "正在刷新 Petdex 形象库…" else "正在加载 Petdex 形象库…"
        val token = ++petdexToken
        ApplicationManager.getApplication().executeOnPooledThread {
            val res = runCatching { PetdexClient.fetchManifest() }
            ApplicationManager.getApplication().invokeLater({
                if (token != petdexToken) return@invokeLater
                res.onSuccess { pets ->
                    val changed = !petdexLoaded || pets != petdexAllPets
                    petdexLoaded = true
                    petdexAllPets = pets
                    if (changed) {
                        rebuildKindOptions(pets)
                        refilterPetdex(resetScroll = false)
                    } else {
                        petdexStatus.text = "共 ${petdexAllPets.size} 个形象 · 来源 petdex.dev"
                    }
                }.onFailure {
                    if (!petdexLoaded) petdexStatus.text = "加载失败：${it.message ?: "网络不可用"}（可点击刷新重试）"
                    else petdexStatus.text = "已显示缓存（刷新失败：${it.message ?: "网络不可用"}）"
                }
            }, ModalityState.any())
        }
    }

    private fun rebuildKindOptions(pets: List<PetdexClient.Pet>) {
        val prev = petdexKind.selectedItem as? String
        val kinds = pets.mapNotNull { it.kind.ifBlank { null } }.distinct().sorted()
        petdexKind.model = DefaultComboBoxModel((listOf(ALL_KINDS) + kinds).toTypedArray())
        petdexKind.selectedItem = if (prev != null && (prev == ALL_KINDS || kinds.contains(prev))) prev else ALL_KINDS
    }

    private fun refilterPetdex(resetScroll: Boolean = true) {
        val q = petdexSearch.text.trim().lowercase()
        val kind = (petdexKind.selectedItem as? String)?.takeIf { it != ALL_KINDS }.orEmpty()
        val filtered = petdexAllPets.filter { pet ->
            (kind.isEmpty() || pet.kind == kind) &&
                (q.isEmpty() || "${pet.displayName} ${pet.slug} ${pet.kind} ${pet.submittedBy}".lowercase().contains(q))
        }
        filtered.forEach { petByCharId["petdex:${it.slug}"] = it }
        petdexGrid.setItems(filtered.map { it.toGridItem() }, resetScroll)
        petdexStatus.text = when {
            petdexAllPets.isEmpty() -> "暂无可用形象"
            filtered.isEmpty() -> "没有匹配的形象"
            else -> "共 ${filtered.size} 个形象 · 来源 petdex.dev"
        }
        petdexSelected?.let { petdexGrid.selectById(it.id) }
    }

    private fun PetdexClient.Pet.toGridItem(): PetGridView.Item {
        val pet = this
        val id = "petdex:${pet.slug}"
        return PetGridView.Item(
            id = id,
            title = pet.displayName,
            subtitle = if (pet.submittedBy.isNotBlank()) "@${pet.submittedBy}" else pet.kind.ifBlank { "Petdex" },
            character = PetdexClient.toCharacter(pet),
            loadThumb = { indicator -> PetThumbnails.fromFile(id, PetdexClient.download(pet, indicator)) },
        )
    }

    // ---------------- 本地 ----------------

    private fun buildLocalPanel(): JComponent {
        val chooseBtn = JButton("选择精灵图文件…").apply { addActionListener { chooseLocalFile() } }
        localNameField.emptyText.text = "形象名称"
        localNameField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = rebuildLocalSelected()
        })

        val form = JPanel()
        form.layout = javax.swing.BoxLayout(form, javax.swing.BoxLayout.Y_AXIS)
        form.add(JBLabel("支持 PNG / WebP / GIF 网格精灵图；每帧 192×208，列行数按图片尺寸自动识别。").apply {
            foreground = com.intellij.ui.JBColor.GRAY
            alignmentX = 0f
        })
        form.add(javax.swing.Box.createVerticalStrut(10))
        form.add(rowOf("名称：", localNameField))
        form.add(javax.swing.Box.createVerticalStrut(8))
        form.add(rowOf("", chooseBtn))
        form.add(javax.swing.Box.createVerticalStrut(8))
        form.add(localStatus.apply { alignmentX = 0f })

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(12)
            add(form, BorderLayout.NORTH)
        }
    }

    private fun rowOf(label: String, field: JComponent): JComponent =
        JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            alignmentX = 0f
            if (label.isNotEmpty()) add(JBLabel(label))
            add(field)
        }

    private fun chooseLocalFile() {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor().withFileFilter {
            it.extension?.lowercase() in setOf("png", "webp", "gif")
        }
        descriptor.title = "选择精灵图"
        val vf = FileChooser.chooseFile(descriptor, project, null) ?: return
        val path = vf.path
        localStatus.text = "正在读取…"
        val token = ++localToken
        ApplicationManager.getApplication().executeOnPooledThread {
            val res = runCatching {
                val img = SpriteLoader.readImage(File(path)) ?: error("不是有效的精灵图（至少需 192×208）")
                val dest = SpriteLoader.importLocalFile(File(path))
                dest to SpriteSheet(img)
            }
            ApplicationManager.getApplication().invokeLater({
                if (token != localToken) return@invokeLater
                res.onSuccess { (dest, sheet) ->
                    pendingLocalPath = dest.absolutePath
                    pendingLocalSheet = sheet
                    if (localNameField.text.isBlank()) localNameField.text = vf.nameWithoutExtension
                    localStatus.text = "已读取：网格 ${sheet.cols}×${sheet.rows}"
                    rebuildLocalSelected()
                }.onFailure {
                    pendingLocalPath = null
                    pendingLocalSheet = null
                    localStatus.text = "读取失败：${it.message ?: "未知错误"}"
                    rebuildLocalSelected()
                }
            }, ModalityState.any())
        }
    }

    private fun rebuildLocalSelected() {
        val path = pendingLocalPath
        val sheet = pendingLocalSheet
        if (path == null || sheet == null) {
            localSelected = null
            refreshSelection()
            return
        }
        val id = "local:${File(path).name}"
        val name = localNameField.text.ifBlank { File(path).nameWithoutExtension }
        localSelected = PetCharacter(id, name, CharacterSource.LOCAL, filePath = path)
        previewCache[id] = sheet
        refreshSelection()
    }

    // ---------------- 已添加 / 内置 ----------------

    private fun buildLibraryPanel(): JComponent {
        libraryRemoveBtn.addActionListener { removeSelectedLibrary() }
        val buttons = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { add(libraryRemoveBtn) }
        return JPanel(BorderLayout(0, 6)).apply {
            border = JBUI.Borders.empty(8)
            add(libraryGrid.component, BorderLayout.CENTER)
            add(buttons, BorderLayout.SOUTH)
        }
    }

    private fun reloadLibrary() {
        libraryGrid.setItems(store.library().map { c ->
            PetGridView.Item(
                id = c.id,
                title = c.displayName,
                subtitle = c.subtitle,
                character = c,
                loadThumb = { _ ->
                    if (c.isBuiltin) {
                        PetThumbnails.fromSheet(c.id, PetSprite.builtin)
                    } else {
                        c.filePath?.let {
                            PetThumbnails.fromFile(c.id, File(it))
                        }
                    }
                },
            )
        })
        libraryGrid.selectById(current.id)
        updateRemoveBtn()
    }

    private fun updateRemoveBtn() {
        libraryRemoveBtn.isEnabled = librarySelected != null && librarySelected?.isBuiltin == false
    }

    private fun removeSelectedLibrary() {
        val c = librarySelected ?: return
        if (c.isBuiltin) return
        store.remove(c.id)
        SpriteLoader.invalidate(c.id)
        PetThumbnails.invalidate(c.id)
        previewCache.remove(c.id)
        librarySelected = null
        reloadLibrary()
        refreshSelection()
    }

    // ---------------- 选择汇总 ----------------

    /** 选择变化：刷新右侧预览，并按需在后台解码整图（仅被选中的形象才解码大图） */
    private fun onSelectionChanged() {
        refreshSelection()
        selected?.let { ensurePreviewSheet(it) }
    }

    private fun ensurePreviewSheet(c: PetCharacter) {
        if (previewCache.containsKey(c.id)) return
        val token = ++previewToken
        ApplicationManager.getApplication().executeOnPooledThread {
            val sheet = runCatching { loadFullSheet(c) }.getOrNull()
            ApplicationManager.getApplication().invokeLater({
                if (token != previewToken || sheet == null) return@invokeLater
                previewCache[c.id] = sheet
                if (selected?.id == c.id) refreshSelection()
            }, ModalityState.any())
        }
    }

    /** 解码整张精灵图（Petdex 缺文件时按需下载）；不写入全局缓存，仅供本对话框预览。失败返回 null */
    private fun loadFullSheet(c: PetCharacter): SpriteSheet? {
        if (c.isBuiltin) return PetSprite.builtin
        val pet = petByCharId[c.id]
        if (pet != null) {
            val file = runCatching { PetdexClient.download(pet) }.getOrNull() ?: return null
            return readSheet(file)
        }
        return c.filePath?.let { readSheet(File(it)) }
    }

    private fun readSheet(file: File): SpriteSheet? =
        SpriteLoader.readImage(file)?.let { SpriteSheet(it) }

    private fun activeSelection(): PetCharacter? = when (tabs.selectedIndex) {
        0 -> petdexSelected
        1 -> localSelected
        else -> librarySelected
    }

    private fun refreshSelection() {
        selected = activeSelection()
        setOKActionEnabled(selected != null)
        val c = selected
        preview.sheet = c?.let { previewCache[it.id] }
        selectedNameLabel.text = c?.let { "${it.displayName} · ${it.subtitle}" } ?: "未选择"
    }

    override fun doOKAction() {
        val c = selected ?: return
        // 预置已解码好的整图，应用形象时无需再次下载 / 解码
        previewCache[c.id]?.let { SpriteLoader.prime(c.id, it) }
        result = c
        super.doOKAction()
    }

    override fun dispose() {
        preview.stop()
        petdexGrid.dispose()
        libraryGrid.dispose()
        super.dispose()
    }

    companion object {
        private const val ALL_KINDS = "全部来源"
        private const val PREVIEW_CACHE_MAX = 5
    }
}
