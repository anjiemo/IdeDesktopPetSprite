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

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import java.awt.Color
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JWindow
import javax.swing.SwingUtilities

/**
 * 桌面悬浮宠物窗口：无边框、置顶、透明背景、可拖拽、可调大小，位置自动记忆。
 * 尺寸取自应用级 [DeskPetSettings]。仅在 EDT 上构造与操作。
 */
class PetWindow(private val project: Project) : JWindow() {

    private val props = PropertiesComponent.getInstance()
    private val pet = PetComponent(DeskPetSettings.getInstance().scale)
    private var dragOffset: Point? = null

    init {
        isAlwaysOnTop = true
        // 不抢占编辑器焦点
        focusableWindowState = false
        // 透明背景（部分环境不支持时回退为默认背景）
        runCatching { background = Color(0, 0, 0, 0) }

        contentPane = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(pet, BorderLayout.CENTER)
        }
        pack()
        installInteractions()
    }

    fun showPet() {
        restoreLocation()
        isVisible = true
    }

    fun setState(state: PetState) = pet.setState(state)

    /** 应用尺寸（配置页 / 右键改尺寸时调用） */
    fun setScale(scale: Double) {
        pet.scale = scale
        pack()
        keepOnScreen()
    }

    private fun installInteractions() {
        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showMenu(e)
                } else {
                    dragOffset = e.point
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                if (dragOffset != null) {
                    dragOffset = null
                    saveLocation()
                }
            }

            override fun mouseDragged(e: MouseEvent) {
                val off = dragOffset ?: return
                setLocation(e.xOnScreen - off.x, e.yOnScreen - off.y)
            }
        }
        pet.addMouseListener(mouse)
        pet.addMouseMotionListener(mouse)
    }

    private fun showMenu(e: MouseEvent) {
        val menu = JPopupMenu()
        menu.add(JMenuItem("重置位置").apply {
            addActionListener {
                defaultLocation()
                saveLocation()
            }
        })
        menu.addSeparator()
        listOf("小" to "S", "中" to "M", "大" to "L").forEach { (name, code) ->
            menu.add(JMenuItem("尺寸：$name").apply {
                addActionListener {
                    val s = DeskPetSettings.getInstance()
                    s.size = code
                    setScale(s.scale)
                    saveLocation()
                }
            })
        }
        menu.addSeparator()
        // 临时隐藏：仅隐藏窗口，重开项目后恢复
        menu.add(JMenuItem("临时隐藏（重开项目恢复）").apply {
            addActionListener { this@PetWindow.isVisible = false }
        })
        // 永久关闭：写入设置，需在 Settings 中重新开启
        menu.add(JMenuItem("永久关闭（需在设置中开启）").apply {
            addActionListener {
                DeskPetSettings.getInstance().setHidden(DeskPetSettings.keyOf(project), true)
                this@PetWindow.isVisible = false
            }
        })
        menu.show(pet, e.x, e.y)
    }

    private fun restoreLocation() {
        val x = props.getInt(KEY_X, UNSET)
        val y = props.getInt(KEY_Y, UNSET)
        if (x == UNSET || y == UNSET || !centerOnSomeScreen(x, y)) {
            defaultLocation()
        } else {
            setLocation(x, y)
        }
    }

    private fun defaultLocation() {
        val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        setLocation(
            screen.x + screen.width - width - 40,
            screen.y + screen.height - height - 40,
        )
    }

    /** 调整尺寸后若超出屏幕则拉回默认角落 */
    private fun keepOnScreen() {
        if (!centerOnSomeScreen(location.x, location.y)) defaultLocation()
    }

    private fun centerOnSomeScreen(x: Int, y: Int): Boolean {
        val cx = x + width / 2
        val cy = y + height / 2
        return GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.any {
            it.defaultConfiguration.bounds.contains(cx, cy)
        }
    }

    private fun saveLocation() {
        props.setValue(KEY_X, location.x, UNSET)
        props.setValue(KEY_Y, location.y, UNSET)
    }

    override fun dispose() {
        pet.stop()
        super.dispose()
    }

    private companion object {
        const val UNSET = Int.MIN_VALUE
        const val KEY_X = "deskpet.x"
        const val KEY_Y = "deskpet.y"
    }
}
