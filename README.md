# IDE Desktop Pet Sprite（IDE桌面宠物精灵）

[![Release](https://img.shields.io/github/v/release/anjiemo/IdeDesktopPetSprite)](https://github.com/anjiemo/IdeDesktopPetSprite/releases/latest) [![License](https://img.shields.io/github/license/anjiemo/IdeDesktopPetSprite)](LICENSE) [![Downloads](https://img.shields.io/github/downloads/anjiemo/IdeDesktopPetSprite/total)](https://github.com/anjiemo/IdeDesktopPetSprite/releases) [![Stars](https://img.shields.io/github/stars/anjiemo/IdeDesktopPetSprite)](https://github.com/anjiemo/IdeDesktopPetSprite/stargazers)

一个 Android Studio / IntelliJ 插件：在 **Gradle Sync、构建/编译、运行/调试** 等不同状态下，
于桌面显示一只会随状态变化的桌面悬浮宠物，并在成功 / 失败时给出表情反馈。宠物素材可替换（默认内置一只原创史莱姆 Gel）。

## 状态映射

| IDE 事件 | 来源监听 | 宠物状态 | 精灵图行 |
|---|---|---|---|
| Gradle Sync 中 | `ExternalSystemTaskNotificationListener`（RESOLVE_PROJECT） | 思考 · `Sync 同步中` | 行 4 |
| 建立索引中 | `DumbService.DUMB_MODE`（dumb mode 进入/退出） | 张望 · `Index 索引中` | 行 1 |
| 构建 / 编译中 | `ProjectTaskListener` + Gradle EXECUTE_TASK | 奔跑 · `Build 构建中` | 行 2 |
| 运行 / 调试中 | `ExecutionListener` | 奔跑(更快) · `Run 运行中` | 行 2 |
| 成功完成 | 上述监听结束回调 | 跳跃 · `完成 ✓`（2.6s 后回待机） | 行 5 |
| 失败 / 报错 | 上述监听结束回调 | 沮丧 · `出错 ✗`（2.6s 后回待机） | 行 3 |
| 空闲 | —— | 待机 · `摸鱼中` | 行 0 |

> 多个活动叠加时按 **运行 > 构建 > 同步 > 索引 > 待机** 取最高优先级展示。
> 索引为高频活动，结束时不弹「完成 ✓」，直接静默回到当前应有状态。

## 交互

- 鼠标左键拖动可移动；位置自动记忆（多屏安全，越界自动回角落）。
- 右键菜单：重置位置 / 调整尺寸（小·中·大）/ 隐藏宠物（重开项目恢复）。
- 悬浮窗置顶、透明背景、不抢占编辑器焦点。

## 构建与安装

> 本机需 JDK 17。若 `JAVA_HOME` 未指向 17，请先设置；wrapper 已固定 Gradle 8.14.5。

```bash
# 打包插件 zip（产物在 build/distributions/IdeDesktopPetSprite-1.0.0.zip）
./gradlew buildPlugin
```

```bash
# 在沙箱 IDE 中调试运行（默认拉取 IntelliJ IDEA Community 作为运行环境）
./gradlew runIde
```

安装到你的 Android Studio：
**Settings → Plugins → ⚙ → Install Plugin from Disk… → 选择 `build/distributions/IdeDesktopPetSprite-1.0.0.zip`**，重启即可。
打开任意项目后桌面右下角即出现宠物。

### 想用本机真实 IDE（如 Android Studio）作为调试沙箱？

编译 SDK 请保持 `intellijIdeaCommunity("2024.2.5")` 不变（用本机新版 AS 当编译 SDK 会因 Kotlin 版本倒挂而编译失败）。
改用已配置好的 `runLocalIde` 任务即可——它仍以发布版 SDK 编译，但沙箱启动 `localPath` 指定的本机 IDE：

```bash
./gradlew runLocalIde
```

要换成别的 IDE，改 `build.gradle.kts` 中 `runLocalIde` 的 `localPath`：

```kotlin
intellijPlatformTesting {
    runIde {
        register("runLocalIde") {
            localPath.set(file("D:\\ProgramFiles\\Android\\StudioApps\\Android Studio 4"))
        }
    }
}
```

## 更换宠物素材

默认精灵图为一张网格精灵表：**8 列 × 9 行，每帧 192×208**，每行对应一种动作
（0=待机, 1=张望, 2=奔跑, 3=失败, 4=思考, 5=跳跃）。
替换 `src/main/resources/pets/gel-slime.png` 为任意同规格精灵图即可（如为 webp 请先转 png）。
如尺寸不同，调整 `PetSprite.kt` 中的 `FRAME_W/FRAME_H/COLS/ROWS`。

## 作者

anjiemo · <2695734816@qq.com> · https://github.com/anjiemo/

## 素材来源

默认内置精灵图为 anjiemo 原创的果冻史莱姆「Gel」（`pets/gel-slime.png`），版权归 anjiemo 所有，可自由使用、修改、再分发。
你也可以替换为自备的同规格精灵图；导入第三方素材时，其授权由该素材各自来源决定。

## 许可

Copyright © 2026 **anjiemo**（<https://github.com/anjiemo>）。

本项目（含代码与默认内置精灵图）的**版权归 anjiemo 所有**，基于 [Apache License 2.0](LICENSE) 开源：
在保留版权声明与许可声明（见 `LICENSE` 与 `NOTICE`）的前提下，允许自由使用、修改与再分发。
