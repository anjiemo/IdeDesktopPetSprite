/*
 * Copyright (c) 2026 anjiemo. All Rights Reserved.
 * 版权所有 (c) 2026 anjiemo，保留一切权利。
 *
 * 本文件为作者私有工具，仅供作者本人使用；未经作者书面许可，
 * 任何个人或组织不得复制、使用、修改、反编译或再分发其全部或部分内容。
 * 本文件【不适用】本仓库的开源许可（LICENSE，Apache-2.0），亦不随插件分发。
 *
 * This file is the author's PROPRIETARY tool and is NOT licensed to anyone.
 * No permission is granted to copy, use, modify, reverse-engineer, or
 * distribute it, in whole or in part. It is NOT covered by the repository's
 * open-source LICENSE (Apache-2.0) and is not part of the distributed plugin.
 */

import java.awt.*
import java.awt.geom.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Java2D 精灵图绘制工具。
 * 输出 8 列 × 9 行、每帧 192×208 的角色精灵图（总图 1536×1872）：
 * 每行一组动作帧，行内 8 帧循环播放。
 */

private const val COLS = 8
private const val ROWS = 9
private const val FW = 192
private const val FH = 208

// 主体配色
private val BODY_TOP = Color(0x86, 0xF0, 0xD2)
private val BODY_BOT = Color(0x2E, 0xB0, 0x95)
private val BODY_TOP_DIM = Color(0xAE, 0xCB, 0xC2)
private val BODY_BOT_DIM = Color(0x6C, 0x8E, 0x86)
private val RIM = Color(0x1C, 0x77, 0x64, 140)
private val RIM_DIM = Color(0x5A, 0x73, 0x6C, 130)
private val EYE = Color(0x16, 0x33, 0x2D)
private val BLUSH = Color(0xFF, 0x9C, 0x9C, 70)
private val SHADOW = Color(0x10, 0x40, 0x36, 55)
private val WHITE = Color(255, 255, 255, 235)
private val SPARK = Color(0xFF, 0xEC, 0x9B)
private val SWEAT = Color(0x8F, 0xD6, 0xF5)
private val BANG = Color(0xFF, 0xC4, 0x4D)
private val TONGUE = Color(0xFF, 0x8E, 0x9B)

fun main(args: Array<String>) {
    val img = BufferedImage(COLS * FW, ROWS * FH, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    hints(g)
    for (row in 0 until ROWS)
        for (col in 0 until COLS)
            drawFrame(g, col, row)
    g.dispose()
    val out = if (args.isNotEmpty()) args[0] else "sprite.png"
    ImageIO.write(img, "png", File(out))
    println("written: $out  (${img.width}x${img.height})")
}

private fun hints(g: Graphics2D) {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
}

private fun drawFrame(g: Graphics2D, col: Int, row: Int) {
    val ox = (col * FW).toDouble()
    val oy = (row * FH).toDouble()
    val cx = ox + FW / 2.0
    val baseY = oy + 182            // 底部基线
    val t = col.toDouble() / COLS    // 循环相位 0..1
    val tau = 2 * PI * t

    val kind = if (row <= 5) row else 0   // 6~8 复用第 0 行

    var w = 120.0
    var h = 112.0
    var leanX = 0.0
    var yOff = 0.0
    var top = BODY_TOP
    var bot = BODY_BOT
    var rim = RIM
    var blush = true

    when (kind) {
        0 -> { // 呼吸 + 偶尔眨眼
            val s = sin(tau)
            h = 112 + 4 * s
            w = 120 - 4 * s
            yOff = -3 * (0.5 + 0.5 * sin(tau))
        }
        1 -> { // 左右看 + 招手 + ！
            yOff = -2 * (0.5 + 0.5 * sin(tau))
            leanX = 3 * sin(tau)
        }
        2 -> { // 前倾 + 挤压拉伸 + 迈步
            leanX = 11.0
            val s = sin(tau)
            h = 112 + 11 * s
            w = 122 - 9 * s
            yOff = -7 * abs(sin(PI * t * 2))
        }
        3 -> { // 扁塌 + 发抖 + 暗淡配色
            top = BODY_TOP_DIM
            bot = BODY_BOT_DIM
            rim = RIM_DIM
            blush = false
            w = 130.0
            h = 94.0
            leanX = if (col % 2 == 0) -2.2 else 2.2
            yOff = 1.0
        }
        4 -> { // 微晃 + 抬眼 + loading 点
            leanX = 3 * sin(tau)
            yOff = -2 * (0.5 + 0.5 * sin(tau + 1))
        }
        5 -> { // 弹跳 + 大笑 + 闪光
            val jp = col.toDouble() / (COLS - 1) // 0..1
            if (jp < 0.13 || jp > 0.87) {
                h = 100.0
                w = 130.0
                yOff = 2.0
            } else {
                h = 122.0
                w = 108.0
                yOff = -44 * sin(PI * jp)
            }
        }
    }

    val by = baseY + yOff

    // 影子（腾空时变小变淡）
    val lift = max(0.0, -yOff)
    val shW = w * (0.92 - lift * 0.010)
    val shA = max(12.0, 55 - lift * 1.0).toInt()
    g.color = Color(SHADOW.red, SHADOW.green, SHADOW.blue, shA)
    g.fill(Ellipse2D.Double(cx - shW / 2, baseY + 4, shW, 13.0))

    // 身体
    drawBody(g, cx, by, w, h, leanX, top, bot, rim)

    // 脸部锚点
    val exC = cx + leanX * 0.6
    val eyeY = by - h * 0.50
    val dx = w * 0.185
    val lx = exC - dx
    val rx = exC + dx
    val my = by - h * 0.29

    if (blush) {
        g.color = BLUSH
        g.fill(Ellipse2D.Double(exC - w * 0.34, eyeY + 7, 15.0, 9.0))
        g.fill(Ellipse2D.Double(exC + w * 0.34 - 15, eyeY + 7, 15.0, 9.0))
    }

    when (kind) {
        0 -> {
            if (col == 4) {
                blinkEyes(g, lx, eyeY)
                blinkEyes(g, rx, eyeY)
            } else {
                eye(g, lx, eyeY, 0.0, 0.0)
                eye(g, rx, eyeY, 0.0, 0.0)
            }
            smile(g, exC, my, 11.0)
        }
        1 -> {
            val look = sin(tau) * 3.2
            eye(g, lx, eyeY, look, 0.0)
            eye(g, rx, eyeY, look, 0.0)
            mouthO(g, exC, my, 5.0)
            val hy = by - h * 0.42 + sin(tau * 2) * 6
            g.color = Color(top.red, top.green, top.blue, 235)
            g.fill(Ellipse2D.Double(exC + w * 0.50, hy, 22.0, 20.0))
            g.color = rim
            g.stroke = BasicStroke(2f)
            g.draw(Ellipse2D.Double(exC + w * 0.50, hy, 22.0, 20.0))
            val ba = (120 + 135 * (0.5 + 0.5 * sin(tau))).toInt()
            bang(g, exC + w * 0.42, by - h - 14, ba)
        }
        2 -> {
            focusEyes(g, lx, eyeY, rx)
            mouthFlat(g, exC, my, 9.0)
            val phase = col % 2 == 0
            foot(g, exC - 16, baseY - 2, phase, top, bot)
            foot(g, exC + 16, baseY - 2, !phase, top, bot)
            motionLines(g, cx - w * 0.62, by - h * 0.5, col)
        }
        3 -> {
            deadEyes(g, lx, eyeY)
            deadEyes(g, rx, eyeY)
            sadBrow(g, lx, eyeY - 11)
            sadBrow(g, rx, eyeY - 11)
            sad(g, exC, my + 2, 10.0)
            val sy = by - h * 0.55 + (col.toDouble() / COLS) * 26
            sweat(g, exC + w * 0.40, sy)
        }
        4 -> {
            eye(g, lx, eyeY, 0.0, -3.0)
            eye(g, rx, eyeY, 0.0, -3.0)
            mouthFlat(g, exC, my, 7.0)
            val active = (t * 3).toInt() % 3
            thinkDots(g, exC, by - h - 16, active)
        }
        5 -> {
            happyEyes(g, lx, eyeY)
            happyEyes(g, rx, eyeY)
            openSmile(g, exC, my, 16.0, 12.0)
            if (yOff < -6) {
                fillStar(g, exC - w * 0.66, by - h * 0.7, 7.0, SPARK)
                fillStar(g, exC + w * 0.62, by - h * 0.45, 9.0, SPARK)
                fillStar(g, exC + w * 0.20, by - h - 10, 6.0, WHITE)
            }
        }
    }
}

// ---------- 身体 ----------
private fun drawBody(
    g: Graphics2D, cx: Double, by: Double, w: Double, h: Double,
    leanX: Double, top: Color, bot: Color, rim: Color
) {
    val p = Path2D.Double()
    p.moveTo(0.0, -h)
    p.curveTo(w * 0.30, -h, w * 0.5, -h * 0.55, w * 0.5, -h * 0.28)
    p.curveTo(w * 0.5, -h * 0.05, w * 0.38, 0.0, w * 0.30, 0.0)
    p.lineTo(-w * 0.30, 0.0)
    p.curveTo(-w * 0.38, 0.0, -w * 0.5, -h * 0.05, -w * 0.5, -h * 0.28)
    p.curveTo(-w * 0.5, -h * 0.55, -w * 0.30, -h, 0.0, -h)
    p.closePath()

    val at = AffineTransform()
    at.translate(cx, by)
    if (leanX != 0.0) at.shear(-leanX / h, 0.0)
    val body = at.createTransformedShape(p)

    g.paint = GradientPaint(cx.toFloat(), (by - h).toFloat(), top, cx.toFloat(), by.toFloat(), bot)
    g.fill(body)
    g.color = rim
    g.stroke = BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.draw(body)
    g.color = Color(255, 255, 255, 90)
    g.fill(Ellipse2D.Double(cx - w * 0.30, by - h * 0.78, w * 0.30, h * 0.26))
    g.color = Color(255, 255, 255, 60)
    g.fill(Ellipse2D.Double(cx + w * 0.06, by - h * 0.66, w * 0.12, h * 0.10))
}

// ---------- 眼睛 / 嘴 ----------
private fun eye(g: Graphics2D, x: Double, y: Double, dxLook: Double, dyLook: Double) {
    g.color = EYE
    g.fill(Ellipse2D.Double(x - 7 + dxLook, y - 9 + dyLook, 14.0, 18.0))
    g.color = WHITE
    g.fill(Ellipse2D.Double(x - 4 + dxLook, y - 6 + dyLook, 5.2, 5.2))
    g.fill(Ellipse2D.Double(x + 2 + dxLook, y + 1 + dyLook, 2.6, 2.6))
}

private fun blinkEyes(g: Graphics2D, x: Double, y: Double) {
    g.color = EYE
    g.stroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.draw(QuadCurve2D.Double(x - 7, y, x, y + 5, x + 7, y))
}

private fun happyEyes(g: Graphics2D, x: Double, y: Double) {
    g.color = EYE
    g.stroke = BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.draw(QuadCurve2D.Double(x - 8, y + 3, x, y - 7, x + 8, y + 3))
}

private fun focusEyes(g: Graphics2D, lx: Double, y: Double, rx: Double) {
    g.color = EYE
    g.fill(Ellipse2D.Double(lx - 6, y - 6, 12.0, 14.0))
    g.fill(Ellipse2D.Double(rx - 6, y - 6, 12.0, 14.0))
    g.stroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.draw(Line2D.Double(lx - 8, y - 12, lx + 6, y - 8))
    g.draw(Line2D.Double(rx + 8, y - 12, rx - 6, y - 8))
    g.color = WHITE
    g.fill(Ellipse2D.Double(lx - 2, y - 3, 3.0, 3.0))
    g.fill(Ellipse2D.Double(rx - 2, y - 3, 3.0, 3.0))
}

private fun deadEyes(g: Graphics2D, x: Double, y: Double) {
    g.color = EYE
    g.stroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.draw(Line2D.Double(x - 6, y - 6, x + 6, y + 6))
    g.draw(Line2D.Double(x - 6, y + 6, x + 6, y - 6))
}

private fun sadBrow(g: Graphics2D, x: Double, y: Double) {
    g.color = EYE
    g.stroke = BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.draw(Line2D.Double(x - 8, y, x + 4, y + 4))
}

private fun smile(g: Graphics2D, cx: Double, y: Double, r: Double) {
    g.color = EYE
    g.stroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.draw(QuadCurve2D.Double(cx - r, y, cx, y + r * 0.8, cx + r, y))
}

private fun sad(g: Graphics2D, cx: Double, y: Double, r: Double) {
    g.color = EYE
    g.stroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.draw(QuadCurve2D.Double(cx - r, y + r * 0.7, cx, y - r * 0.5, cx + r, y + r * 0.7))
}

private fun mouthFlat(g: Graphics2D, cx: Double, y: Double, r: Double) {
    g.color = EYE
    g.stroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.draw(Line2D.Double(cx - r, y, cx + r, y))
}

private fun mouthO(g: Graphics2D, cx: Double, y: Double, r: Double) {
    g.color = EYE
    g.fill(Ellipse2D.Double(cx - r, y - r, r * 2, r * 2.2))
}

private fun openSmile(g: Graphics2D, cx: Double, y: Double, w: Double, h: Double) {
    g.color = EYE
    g.fill(Arc2D.Double(cx - w / 2, y - h * 0.5, w, h * 1.4, 200.0, 140.0, Arc2D.CHORD))
    g.color = TONGUE
    g.fill(Arc2D.Double(cx - w * 0.30, y + h * 0.2, w * 0.6, h * 0.7, 180.0, 180.0, Arc2D.CHORD))
}

// ---------- 装饰 ----------
private fun foot(g: Graphics2D, x: Double, y: Double, up: Boolean, top: Color, bot: Color) {
    val yy = y - if (up) 5 else 0
    g.paint = GradientPaint(x.toFloat(), yy.toFloat(), top, x.toFloat(), (yy + 12).toFloat(), bot)
    g.fill(Ellipse2D.Double(x - 9, yy, 18.0, 13.0))
}

private fun motionLines(g: Graphics2D, x: Double, y: Double, col: Int) {
    g.color = Color(0x4F, 0xC9, 0xAE, 150)
    g.stroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    for (i in 0 until 3) {
        val yy = y + (i - 1) * 12 + (col % 2) * 2
        val len = 16 + i * 6
        g.draw(Line2D.Double(x - len, yy, x, yy))
    }
}

private fun thinkDots(g: Graphics2D, cx: Double, y: Double, active: Int) {
    for (i in 0 until 3) {
        val a = if (i == active) 255 else 90
        val r = if (i == active) 5.0 else 3.6
        g.color = Color(0x46, 0xC4, 0xA8, a)
        g.fill(Ellipse2D.Double(cx - 16 + i * 16 - r / 2, y - r / 2, r, r))
    }
}

private fun sweat(g: Graphics2D, x: Double, y: Double) {
    val p = Path2D.Double()
    p.moveTo(x, y - 9)
    p.curveTo(x + 6, y - 1, x + 6, y + 5, x, y + 7)
    p.curveTo(x - 6, y + 5, x - 6, y - 1, x, y - 9)
    p.closePath()
    g.color = SWEAT
    g.fill(p)
    g.color = Color(255, 255, 255, 200)
    g.fill(Ellipse2D.Double(x - 2.5, y - 3, 3.0, 3.0))
}

private fun bang(g: Graphics2D, x: Double, y: Double, alpha: Int) {
    g.color = Color(BANG.red, BANG.green, BANG.blue, min(255, max(0, alpha)))
    g.stroke = BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.draw(Line2D.Double(x, y, x, y + 14))
    g.fill(Ellipse2D.Double(x - 2.6, y + 18, 5.2, 5.2))
}

private fun fillStar(g: Graphics2D, x: Double, y: Double, s: Double, c: Color) {
    val p = Path2D.Double()
    val k = 0.30
    val pts = arrayOf(
        doubleArrayOf(0.0, -s), doubleArrayOf(k * s, -k * s), doubleArrayOf(s, 0.0), doubleArrayOf(k * s, k * s),
        doubleArrayOf(0.0, s), doubleArrayOf(-k * s, k * s), doubleArrayOf(-s, 0.0), doubleArrayOf(-k * s, -k * s)
    )
    p.moveTo(x + pts[0][0], y + pts[0][1])
    for (i in 1 until pts.size) p.lineTo(x + pts[i][0], y + pts[i][1])
    p.closePath()
    g.color = c
    g.fill(p)
}
