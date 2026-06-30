# Copyright (c) 2026 anjiemo. All Rights Reserved.
# 版权所有 (c) 2026 anjiemo，保留一切权利。
#
# 本文件为作者私有工具，仅供作者本人使用；未经作者书面许可，
# 任何个人或组织不得复制、使用、修改、反编译或再分发其全部或部分内容。
# 本文件【不适用】本仓库的开源许可（LICENSE，Apache-2.0），亦不随插件分发。
#
# This file is the author's PROPRIETARY tool and is NOT licensed to anyone.
# No permission is granted to copy, use, modify, reverse-engineer, or
# distribute it, in whole or in part. It is NOT covered by the repository's
# open-source LICENSE (Apache-2.0) and is not part of the distributed plugin.
#
"""Generate JetBrains pluginIcon.svg (40x40 vector) from IconCanvasTool geometry."""

from __future__ import annotations

from pathlib import Path

SZ = 40
S = SZ / 512.0
OUT = Path(__file__).resolve().parents[1] / "src/main/resources/META-INF"


def pt(x: float, y: float) -> str:
    return f"{x * S:.3f}".rstrip("0").rstrip(".") + " " + f"{y * S:.3f}".rstrip("0").rstrip(".")


def star(cx: float, cy: float, size: float, fill: str) -> str:
    k = 0.30
    pts = [
        (0, -size),
        (k * size, -k * size),
        (size, 0),
        (k * size, k * size),
        (0, size),
        (-k * size, k * size),
        (-size, 0),
        (-k * size, -k * size),
    ]
    coords = " ".join(f"{pt(cx + px, cy + py)}" for px, py in pts)
    return f'<polygon points="{coords}" fill="{fill}"/>'


def build_svg() -> str:
    m = 14 * S
    arc = 124 * S
    s = (512 - 28) * S

    cx, by = 256.0, 380.0
    w, h = 258.0, 248.0

    body = " ".join(
        [
            f"M {pt(cx, by - h)}",
            f"C {pt(cx + w * 0.30, by - h)} {pt(cx + w * 0.5, by - h * 0.55)} {pt(cx + w * 0.5, by - h * 0.28)}",
            f"C {pt(cx + w * 0.5, by - h * 0.05)} {pt(cx + w * 0.38, by)} {pt(cx + w * 0.30, by)}",
            f"L {pt(cx - w * 0.30, by)}",
            f"C {pt(cx - w * 0.38, by)} {pt(cx - w * 0.5, by - h * 0.05)} {pt(cx - w * 0.5, by - h * 0.28)}",
            f"C {pt(cx - w * 0.5, by - h * 0.55)} {pt(cx - w * 0.30, by - h)} {pt(cx, by - h)}",
            "Z",
        ]
    )

    eye_y = by - h * 0.50
    dx = w * 0.185
    my = by - h * 0.27
    mw, mh = 60.0, 30.0
    shadow_cy = by + 6.0

    return f"""<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40" viewBox="0 0 40 40" role="img" aria-label="IDE Desktop Pet Sprite">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="40" y2="40" gradientUnits="userSpaceOnUse">
      <stop stop-color="#2A3246"/>
      <stop offset="1" stop-color="#0E121C"/>
    </linearGradient>
    <linearGradient id="gel" x1="20" y1="10" x2="20" y2="30" gradientUnits="userSpaceOnUse">
      <stop stop-color="#8EF3D6"/>
      <stop offset="1" stop-color="#27B498"/>
    </linearGradient>
    <radialGradient id="glow" cx="20" cy="22" r="6" gradientUnits="userSpaceOnUse">
      <stop stop-color="#6FE7CB" stop-opacity="0.22"/>
      <stop offset="1" stop-opacity="0"/>
    </radialGradient>
  </defs>
  <rect x="{m:.2f}" y="{m:.2f}" width="{s:.2f}" height="{s:.2f}" rx="{arc:.2f}" fill="url(#bg)"/>
  <g>
    <ellipse cx="20" cy="{shadow_cy * S:.2f}" rx="{w * 0.42 * S:.2f}" ry="{30 * S:.2f}" fill="#000" fill-opacity="0.22"/>
    <ellipse cx="20" cy="22" rx="6" ry="5" fill="url(#glow)"/>
    <path d="{body}" fill="url(#gel)" stroke="#106E5C" stroke-width="0.38" stroke-linejoin="round"/>
    <ellipse cx="{ (cx - w * 0.30) * S:.2f}" cy="{ (by - h * 0.80) * S:.2f}" rx="{w * 0.16 * S:.2f}" ry="{h * 0.135 * S:.2f}" fill="#FFFFFF" fill-opacity="0.37"/>
    <ellipse cx="{ (cx - w * 0.34) * S:.2f}" cy="{ (eye_y + 14) * S:.2f}" rx="{34 * S:.2f}" ry="{20 * S:.2f}" fill="#FF9C9C" fill-opacity="0.35"/>
    <ellipse cx="{ (cx + w * 0.34 - 34) * S:.2f}" cy="{ (eye_y + 14) * S:.2f}" rx="{34 * S:.2f}" ry="{20 * S:.2f}" fill="#FF9C9C" fill-opacity="0.35"/>
    <path d="M {pt(cx - dx - 19, eye_y + 7)} Q {pt(cx - dx, eye_y - 16)} {pt(cx - dx + 19, eye_y + 7)}" fill="none" stroke="#122E28" stroke-width="0.85" stroke-linecap="round"/>
    <path d="M {pt(cx + dx - 19, eye_y + 7)} Q {pt(cx + dx, eye_y - 16)} {pt(cx + dx + 19, eye_y + 7)}" fill="none" stroke="#122E28" stroke-width="0.85" stroke-linecap="round"/>
    <path d="M {pt(cx - mw / 2, my - mh * 0.5)} A {mw * S:.2f} {mh * 1.5 * S:.2f} 0 0 1 {pt(cx + mw / 2, my - mh * 0.5)} L {pt(cx + mw * 0.28, my + mh * 0.3)} A {mw * 0.56 * S:.2f} {mh * 0.8 * S:.2f} 0 0 1 {pt(cx - mw * 0.28, my + mh * 0.3)} Z" fill="#122E28"/>
    <path d="M {pt(cx - mw * 0.28, my + mh * 0.3)} A {mw * 0.56 * S:.2f} {mh * 0.8 * S:.2f} 0 0 0 {pt(cx + mw * 0.28, my + mh * 0.3)} Z" fill="#FF8E9B"/>
    {star(256 - 152, 168, 16, "#FFEC9B")}
    {star(256 + 156, 222, 22, "#6FE7CB")}
  </g>
</svg>
"""


def main() -> None:
    svg = build_svg()
    OUT.mkdir(parents=True, exist_ok=True)
    for name in ("pluginIcon.svg", "pluginIcon_dark.svg"):
        (OUT / name).write_text(svg, encoding="utf-8")
    print(f"written {OUT}/pluginIcon.svg ({len(svg.encode('utf-8'))} bytes)")


if __name__ == "__main__":
    main()
