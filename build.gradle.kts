plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "cn.funkt"
version = "1.0.2"

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
        // 编译用发布版 SDK（Kotlin 2.0.x，与本工程编译器一致）；插件只用稳定平台 API，
        // 编出的 zip 可在更高版本 AS（含 2026.1/build 261）中加载。
        // 注意：不要把本机 AS 261 用作编译 SDK —— 它是 Kotlin 2.3.0 + JDK 21，
        // 与 kotlin("jvm") 2.0.21 版本倒挂，会出现 "incompatible version of Kotlin" 编译失败。
        intellijIdeaCommunity("2024.2.5")
        instrumentationTools()
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
        // id / name / vendor / description 均在 META-INF/plugin.xml 中声明
        ideaVersion {
            sinceBuild = "233"
            // 开放上限，避免被新版本 AS 拦截；可按需收紧
            untilBuild = "299.*"
        }
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
