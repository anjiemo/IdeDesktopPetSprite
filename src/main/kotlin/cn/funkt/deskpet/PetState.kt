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

import java.awt.Color

/**
 * 宠物状态。row / frames 对应网格精灵图规范：
 * 每一行是一种动作，行内按 frames 序列逐帧循环播放。
 *
 * 行语义：0=idle 待机，1=wave 招手，2=run 奔跑，
 * 3=failed 失败，4=review 思考，5=jump 跳跃 ……
 */
enum class PetState(
    val row: Int,
    val frames: IntArray,
    val frameMs: Int,
    val label: String,
    val badgeColor: Color,
) {
    /** 空闲待机 */
    IDLE(0, intArrayOf(0, 1, 2, 3, 4, 3, 2, 1), 170, "摸鱼中", Color(0x8A93A6)),

    /** Gradle Sync 同步中 —— 用「思考」行 */
    SYNCING(4, intArrayOf(0, 1, 2, 1), 150, "Sync 同步中", Color(0x3B82F6)),

    /** 建立索引中（IDE dumb mode）—— 用「转头张望」行，像在扫描文件 */
    INDEXING(1, intArrayOf(0, 1, 2, 3, 4, 3, 2, 1), 150, "Index 索引中", Color(0x8B5CF6)),

    /** 构建 / 编译中 —— 用「奔跑」行 */
    BUILDING(2, intArrayOf(0, 1, 2, 3, 4, 5, 6, 7), 120, "Build 构建中", Color(0xF59E0B)),

    /** 运行 / 调试中 —— 同「奔跑」行但更快，绿色徽标区分 */
    RUNNING(2, intArrayOf(0, 1, 2, 3, 4, 5, 6, 7), 90, "Run 运行中", Color(0x22C55E)),

    /** 成功完成 —— 用「跳跃」行庆祝（短暂闪现后回到 IDLE） */
    SUCCESS(5, intArrayOf(0, 1, 2, 3, 2, 1), 130, "完成 √", Color(0x16A34A)),

    /** 失败 / 报错 —— 用「失败」行（短暂闪现后回到 IDLE） */
    ERROR(3, intArrayOf(0, 1, 2, 1), 220, "出错 ✗", Color(0xEF4444)),
}
