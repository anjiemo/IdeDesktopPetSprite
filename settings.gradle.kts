pluginManagement {
    repositories {
        // 阿里云镜像优先，绕开国内不稳的 plugins.gradle.org
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "IdeDesktopPetSprite"
