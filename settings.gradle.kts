// settings.gradle.kts 是 Gradle 的项目级入口配置，职责接近 Maven 的根 pom.xml。

// 配置 Gradle 插件本身的下载仓库，供 build.gradle.kts 中的 plugins { } 使用。
pluginManagement {
    repositories {
        // Gradle 官方插件仓库，IntelliJ Platform Gradle 插件从这里解析。
        gradlePluginPortal()

        // Maven 中央仓库，作为其他插件或其依赖的补充来源。
        mavenCentral()
    }
}

// Gradle 根项目名称，作用接近 Maven 的 <artifactId>，也会用于默认产物名称。
rootProject.name = "ai-commit-message"
