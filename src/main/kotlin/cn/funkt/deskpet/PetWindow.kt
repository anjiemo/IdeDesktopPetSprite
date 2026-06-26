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
 * 仅在 EDT 上构造与操作。
 */
class PetWindow(@Suppress("unused") private val project: Project) : JWindow() {

    private val props = PropertiesComponent.getInstance()
    private val pet = PetComponent(loadScale())
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
        listOf("小" to 0.45, "中" to 0.62, "大" to 0.85).forEach { (name, s) ->
            menu.add(JMenuItem("尺寸：$name").apply {
                addActionListener {
                    pet.scale = s
                    props.setValue(KEY_SCALE, s.toString())
                    pack()
                    keepOnScreen()
                    saveLocation()
                }
            })
        }
        menu.addSeparator()
        menu.add(JMenuItem("隐藏宠物（重开项目恢复）").apply {
            addActionListener { isVisible = false }
        })
        menu.show(pet, e.x, e.y)
    }

    private fun loadScale(): Double = props.getValue(KEY_SCALE)?.toDoubleOrNull() ?: 0.62

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
        const val KEY_SCALE = "deskpet.scale"
    }
}
