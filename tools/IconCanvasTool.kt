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

/**
 * Java2D 圆角图标绘制工具。
 * 输出 512×512 的角色图标：深色圆角背景 + 辉光 + 一个角色。
 */

private const val SZ = 512

private val BG_TL = Color(0x2A, 0x32, 0x46)
private val BG_BR = Color(0x0E, 0x12, 0x1C)
private val BODY_TOP = Color(0x8E, 0xF3, 0xD6)
private val BODY_BOT = Color(0x27, 0xB4, 0x98)
private val RIM = Color(0x10, 0x6E, 0x5C)
private val EYE = Color(0x12, 0x2E, 0x28)
private val BLUSH = Color(0xFF, 0x9C, 0x9C, 90)
private val TONGUE = Color(0xFF, 0x8E, 0x9B)
private val GLOW = Color(0x6F, 0xE7, 0xCB)

fun main(args: Array<String>) {
    val img = BufferedImage(SZ, SZ, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

    // 圆角背景
    val m = 14.0
    val s = SZ - 2 * m
    val arc = 124.0
    val rr = RoundRectangle2D.Double(m, m, s, s, arc, arc)
    g.paint = GradientPaint(0f, m.toFloat(), BG_TL, SZ.toFloat(), SZ.toFloat(), BG_BR)
    g.fill(rr)

    val oldClip = g.clip
    g.clip = rr

    // 顶部柔光
    g.paint = GradientPaint(0f, m.toFloat(), Color(255, 255, 255, 26), 0f, SZ * 0.55f, Color(255, 255, 255, 0))
    g.fill(rr)

    val cx = SZ / 2.0
    val glowCy = 256.0
    // 角色背后的辉光
    g.paint = RadialGradientPaint(
        Point2D.Double(cx, glowCy), 215f,
        floatArrayOf(0f, 1f),
        arrayOf(Color(GLOW.red, GLOW.green, GLOW.blue, 120), Color(GLOW.red, GLOW.green, GLOW.blue, 0))
    )
    g.fill(Ellipse2D.Double(cx - 230, glowCy - 200, 460.0, 400.0))

    // 地面投影
    val baseY = 380.0
    val w = 258.0
    val h = 248.0
    g.color = Color(0, 0, 0, 70)
    g.fill(Ellipse2D.Double(cx - w * 0.42, baseY + 6, w * 0.84, 30.0))

    drawCharacter(g, cx, baseY, w, h)

    // 闪光点缀
    fillStar(g, cx - 152, 168.0, 16.0, Color(0xFF, 0xEC, 0x9B))
    fillStar(g, cx + 156, 222.0, 22.0, GLOW)
    fillStar(g, cx + 116, 124.0, 12.0, Color(255, 255, 255, 230))

    g.clip = oldClip
    g.dispose()

    val out = if (args.isNotEmpty()) args[0] else "icon.png"
    ImageIO.write(img, "png", File(out))
    println("written: $out  (${SZ}x${SZ})")
}

private fun drawCharacter(g: Graphics2D, cx: Double, by: Double, w: Double, h: Double) {
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
    val body = at.createTransformedShape(p)

    g.paint = GradientPaint(cx.toFloat(), (by - h).toFloat(), BODY_TOP, cx.toFloat(), by.toFloat(), BODY_BOT)
    g.fill(body)
    g.color = RIM
    g.stroke = BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.draw(body)
    g.color = Color(255, 255, 255, 95)
    g.fill(Ellipse2D.Double(cx - w * 0.30, by - h * 0.80, w * 0.32, h * 0.27))
    g.color = Color(255, 255, 255, 55)
    g.fill(Ellipse2D.Double(cx + w * 0.04, by - h * 0.66, w * 0.13, h * 0.11))

    val eyeY = by - h * 0.50
    val dx = w * 0.185
    val my = by - h * 0.27
    val lx = cx - dx
    val rx = cx + dx

    g.color = BLUSH
    g.fill(Ellipse2D.Double(cx - w * 0.34, eyeY + 14, 34.0, 20.0))
    g.fill(Ellipse2D.Double(cx + w * 0.34 - 34, eyeY + 14, 34.0, 20.0))

    g.color = EYE
    g.stroke = BasicStroke(11f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.draw(QuadCurve2D.Double(lx - 19, eyeY + 7, lx, eyeY - 16, lx + 19, eyeY + 7))
    g.draw(QuadCurve2D.Double(rx - 19, eyeY + 7, rx, eyeY - 16, rx + 19, eyeY + 7))

    val mw = 60.0
    val mh = 30.0
    g.color = EYE
    g.fill(Arc2D.Double(cx - mw / 2, my - mh * 0.5, mw, mh * 1.5, 200.0, 140.0, Arc2D.CHORD))
    g.color = TONGUE
    g.fill(Arc2D.Double(cx - mw * 0.28, my + mh * 0.3, mw * 0.56, mh * 0.8, 180.0, 180.0, Arc2D.CHORD))
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
