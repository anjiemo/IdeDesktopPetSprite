plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "cn.funkt"
version = "1.0.6"

repositories {
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    mavenCentral()
    intellijPlatform {
        // IntelliJ 平台 SDK 走 JetBrains 官方源（cache-redirector）
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // 编译 SDK 2025.1.7.1；Gradle 监听使用 projectPath 新版 API。
        // since 242 = 2024.2（2024.1 因平台 API 差异不在支持范围内）。
        intellijIdeaCommunity("2025.1.7.1")
    }

    // WebP 解码（纯 Java，无原生库，跨平台）。Petdex / 多数社区形象的精灵图为 .webp，
    // 而 JDK 自带 ImageIO 不支持 webp。TwelveMonkeys 为 Apache-2.0，可随插件一同分发。
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
    implementation("com.twelvemonkeys.imageio:imageio-core:3.12.0")
}

intellijPlatform {
    // 本插件没有自定义设置页，无需构建可搜索选项；关掉可加速并避免启动无头 IDE 锁文件
    buildSearchableOptions = false

    pluginConfiguration {
        // id / name / vendor 在 plugin.xml；Marketplace 描述见 DESCRIPTION.md
        description = providers.provider {
            layout.projectDirectory.file("docs/DESCRIPTION.md").asFile.readText(Charsets.UTF_8)
        }
        ideaVersion {
            sinceBuild = "242"
            // 不设 until-build，兼容 261.25134+ 等未来 AS/IDE 版本（JetBrains 2026.1 推荐做法）
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }

    // 发布到 JetBrains Marketplace：https://plugins.jetbrains.com
    // Token：https://plugins.jetbrains.com/author/me/tokens → 环境变量 PUBLISH_TOKEN
    // 或命令行：-PintellijPlatformPublishingToken=xxx
    // 发布：./gradlew publishPlugin
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
            .orElse(providers.gradleProperty("intellijPlatformPublishingToken"))
        channels = listOf("default")
    }
}

// 调试预览：./gradlew runLocalIde
// 编译仍用上面的发布版 SDK（无版本倒挂），但沙箱启动你本机 localPath 指定的 IDE
// （这里是 Android Studio build 261）。该 IDE 运行所需的 JDK 由其自带 JBR 提供，
// 与本工程 toolchain 17 互不影响。换别的 IDE 改 localPath 即可。
intellijPlatformTesting {
    runIde {
        register("runLocalIde") {
            localPath.set(file("D:\\ProgramFiles\\Android\\StudioApps\\Android Studio 4"))
        }
    }
}

kotlin {
    jvmToolchain(17)
}
