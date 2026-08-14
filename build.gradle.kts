// Gradle 的插件声明区，作用类似 Maven 的 <build><plugins>。
// 本文件使用 Kotlin DSL（.gradle.kts），相比 Groovy DSL 具有更完整的类型检查和 IDE 补全。
plugins {
    // Java 基础插件：提供 compileJava、test、jar 等标准 Java 构建任务。
    // 可以理解为 Maven 默认生命周期以及 maven-compiler-plugin、maven-jar-plugin 等基础能力的集合。
    id("java")

    // IntelliJ Platform 官方 Gradle 插件：负责下载 IDEA SDK、校验插件配置、启动沙箱 IDEA，
    // 以及将插件打包成可安装的 ZIP。2.18.1 是构建插件版本，不是目标 IDEA 版本。
    id("org.jetbrains.intellij.platform") version "2.18.1"
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
    }
}

// IntelliJ Platform 插件的专用元数据配置。
intellijPlatform {
    // IDEA 2024.3 内置的 Gradle 插件无法解析新版兼容性数据中的 Java 25。
    // 本插件设置页无需生成额外的可搜索选项索引，关闭该步骤可避免构建沙箱遍历 Gradle 设置页时报错。
    buildSearchableOptions = false

    pluginConfiguration {
        ideaVersion {
            // 插件支持的最低 IntelliJ Platform 构建号。
            // 243 对应 2024.3 系列，低于该版本的 IDEA 不能安装此插件。
            sinceBuild = "243"
        }

        // 插件版本更新说明，会写入最终生成的插件元数据。
        changeNotes = "支持根据已暂存的 Git 变更生成提交信息，并可自定义提示词模板。"
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

// 当前 Windows JDK 安装缺少 IntelliJ 插桩器所需的 Packages 目录，因此暂时跳过插桩任务。
// 本插件未使用 GUI Designer 表单，禁用该任务不会影响现有 Java 代码运行。
tasks.named("instrumentCode") {
    enabled = false
}
