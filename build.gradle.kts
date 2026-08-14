// Gradle 的插件声明区，作用类似 Maven 的 <build><plugins>。
// 本文件使用 Kotlin DSL（.gradle.kts），相比 Groovy DSL 具有更完整的类型检查和 IDE 补全。
plugins {
    // Java 基础插件：提供 compileJava、test、jar 等标准 Java 构建任务。
    // 可以理解为 Maven 默认生命周期以及 maven-compiler-plugin、maven-jar-plugin 等基础能力的集合。
    id("java")

    // IntelliJ Platform 官方 Gradle 插件：负责下载 IDEA SDK、校验插件配置、启动沙箱 IDEA，
    // 以及将插件打包成可安装的 ZIP。2.4.0 是构建插件版本，不是目标 IDEA 版本。
    id("org.jetbrains.intellij.platform") version "2.4.0"
}

// 对应 Maven 的 <groupId>，用于标识项目所属的组织或命名空间。
group = "org.aicommitmessage"

// 对应 Maven 的 <version>，同时会成为生成插件包的版本号。
// 发布新版本时应按照项目的版本策略递增此值。
version = "1.0.0"

// 项目依赖的下载仓库，作用类似 Maven 的 <repositories>。
repositories {
    // Maven 中央仓库，供普通 Java 依赖使用。
    mavenCentral()

    // IntelliJ Platform 插件预设的仓库集合，用于下载 IDEA SDK 及相关平台组件。
    intellijPlatform {
        defaultRepositories()
    }
}

// 项目依赖声明，作用类似 Maven 的 <dependencies>。
dependencies {
    intellijPlatform {
        // 使用 IntelliJ IDEA Community Edition 2024.3 作为插件的编译和测试平台。
        // 它相当于插件开发使用的 SDK，不会作为普通业务依赖打进插件包。
        intellijIdeaCommunity("2024.3")

        // IntelliJ 官方字节码插桩工具。
        // buildPlugin 在打包前会执行 instrumentCode；显式声明该依赖可确保插桩器、
        // instrument-util 等工具使用与目标 IDEA SDK 匹配的版本，而不是依赖 IDE 的本地缓存。
        instrumentationTools()
    }
}

// IntelliJ Platform 插件的专用元数据配置。
intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // 插件支持的最低 IntelliJ Platform 构建号。
            // 243 对应 2024.3 系列，低于该版本的 IDEA 不能安装此插件。
            sinceBuild = "243"
        }

        // 插件版本更新说明，会写入最终生成的插件元数据。
        changeNotes = "Generate commit messages from staged Git changes."
    }
}

// Java Toolchain 类似 Maven 中 maven-compiler-plugin 的 release/source/target 配置。
// 这里要求使用 JDK 21 编译；本机构建环境必须能提供 JDK 21。
// Toolchain 控制编译所用 JDK，但 Gradle 自身仍需要可用的 Java 环境才能启动。
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// 本插件没有使用 IntelliJ GUI Designer 表单，也没有依赖需要 IDEA 字节码插桩的 API。
// 关闭 instrumentCode 可以绕过 IntelliJ 2024.3 插桩器在部分 Windows JDK 安装上的
// “<JDK>\\Packages does not exist” 路径兼容问题；Java 编译和最终插件 ZIP 打包仍会正常执行。
tasks.named("instrumentCode") {
    enabled = false
}
