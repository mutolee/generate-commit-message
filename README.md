# AI Commit Message Generator for IntelliJ

一款面向 IntelliJ IDEA 的 AI Git 提交信息生成插件。它可以读取 Commit 工具窗口中勾选的代码变更，调用兼容 OpenAI Chat Completions API 的模型，生成符合 Conventional Commits 规范的提交信息，并直接写入 Commit Message 输入框。

## 功能特性

- 在 IntelliJ IDEA 的 Commit 工具栏中一键生成提交信息
- 优先读取 Commit 工具窗口中实际勾选的文件
- 支持已跟踪文件和未跟踪文件
- 非 Commit 窗口场景下自动回退到 Git 暂存区差异
- 生成结果直接写入 Commit Message 输入框
- 支持 Conventional Commits 标题和多条变更说明
- 为提交标题和每个变更列表项自动添加对应的 emoji 表情
- 支持中文、英文和日文提交信息
- 支持自定义提示词模板
- 支持 OpenAI 及兼容 Chat Completions API 的第三方服务
- 支持在设置页面检测接口地址、模型和 API 密钥
- 生成过程中 Action 会切换为红色停止图标，再次点击可取消任务
- 自动适配 IntelliJ Light/Dark 主题图标

## 生成效果

默认提示词会生成类似下面的提交信息：

```text
chore(build): 🔧 更新插件构建配置并优化注释

- 📦 升级 IntelliJ Platform Gradle Plugin 版本
- 🗑️ 移除不再需要的构建依赖声明
- 🔧 优化任务配置和兼容性说明
- 📝 调整 Java 源码中的注释格式
```

## 环境要求

| 项目 | 要求 |
| --- | --- |
| IntelliJ IDEA | 2024.3 或更高版本 |
| IntelliJ Platform Build | 243+ |
| Git 插件 | 启用 IDE 内置 Git4Idea 插件 |
| 构建 JDK | JDK 21 |
| 构建工具 | Gradle |

## 安装

### 从 ZIP 安装

1. 下载或构建插件 ZIP 安装包。
2. 打开 IntelliJ IDEA。
3. 进入 `设置 | 插件`。
4. 点击齿轮按钮，选择“从磁盘安装插件”。
5. 选择 `ai-commit-message-<version>.zip`。
6. 根据提示重启 IntelliJ IDEA。

## 配置

安装完成后，进入：

```text
设置 | 工具 | AI Commit Message
```

填写以下配置：

| 配置项 | 说明 | 示例 |
| --- | --- | --- |
| 接口地址 | 兼容 OpenAI Chat Completions API 的完整地址 | `https://api.openai.com/v1/chat/completions` |
| 模型 | 服务支持的模型名称 | `gpt-4o-mini` |
| API 密钥 | 调用 AI 服务使用的密钥 | `sk-...` |
| 提交信息语言 | 生成结果使用的语言 | 中文、英文、日文 |
| 提示词模板 | 控制提交信息格式和生成规则 | 参见下方模板变量 |

填写接口地址、模型和 API 密钥后，可以点击“检测连接”。

- 检测成功时会显示绿色状态和请求耗时
- 检测失败时会在按钮下方显示具体错误信息
- 检测使用的是设置页面中当前填写的内容，不需要先保存配置

## 使用方法

1. 打开 IntelliJ IDEA 的 Commit 工具窗口。
2. 勾选本次准备提交的文件。
3. 点击 Commit 工具栏中的黑色或白色 `M` 图标。
4. 等待 AI 生成提交信息。
5. 生成结果会直接写入 Commit Message 输入框。
6. 检查并按需修改内容后提交代码。

生成过程中，`M` 图标会变成红色停止图标。再次点击该图标，可以取消当前生成任务。

如果 Action 不是从 Commit 工具窗口触发，插件会读取 Git 暂存区差异，并将生成结果复制到剪贴板。

## 提示词模板

提示词模板支持以下变量：

| 变量 | 说明 |
| --- | --- |
| `{{language}}` | 当前选择的提交信息语言 |
| `{{diff}}` | 本次提交对应的 Git Diff |

默认模板要求模型输出：

```text
type(scope): ✨ 简洁的提交摘要

- 📦 具体变更一
- 🔧 具体变更二
- 📝 具体变更三
```

如果自定义模板中没有填写 `{{diff}}`，插件会自动把 Git Diff 追加到提示词末尾，避免模型缺少代码变更上下文。

可以在设置页面点击“恢复默认提示词”，然后点击“应用”保存。

## API 兼容性

插件通过以下请求格式调用 AI 服务：

```json
{
  "model": "gpt-4o-mini",
  "messages": [
    {
      "role": "user",
      "content": "生成提交信息的提示词"
    }
  ],
  "temperature": 0.2
}
```

服务响应需要兼容 OpenAI Chat Completions API，并在响应中包含：

```json
{
  "choices": [
    {
      "message": {
        "content": "生成的提交信息"
      }
    }
  ]
}
```

## 本地构建

### 1. 准备环境

- 安装 JDK 21
- 安装可用的 Gradle
- 确认 `JAVA_HOME` 指向 JDK 21

Windows PowerShell 示例：

```powershell
$env:JAVA_HOME = "F:\Java\Jdk21"
```

### 2. 构建插件

如果项目中包含 Gradle Wrapper：

```powershell
.\gradlew.bat clean buildPlugin
```

或者使用本机 Gradle：

```bash
gradle clean buildPlugin
```

构建完成后，插件安装包位于：

```text
build/distributions/ai-commit-message-<version>.zip
```

### 3. 启动开发沙箱

```powershell
.\gradlew.bat runIde
```

该命令会启动一个独立的 IntelliJ IDEA 沙箱实例，便于调试插件，不会影响日常使用的 IDE 配置。

## 项目结构

```text
src/main/
├── java/org/aicommitmessage/
│   ├── CommitMessageConfigurable.java     # 设置页面
│   ├── CommitMessageSettings.java         # 配置持久化
│   └── GenerateCommitMessageAction.java   # Diff 读取、AI 请求和 Action 控制
└── resources/
    ├── META-INF/
    │   ├── plugin.xml                     # 插件元数据
    │   ├── pluginIcon.svg                 # Light 模式插件图标
    │   └── pluginIcon_dark.svg            # Dark 模式插件图标
    └── icons/
        ├── commitMessage.svg              # Light 模式 Action 图标
        ├── commitMessage_dark.svg         # Dark 模式 Action 图标
        ├── stopGeneration.svg             # Light 模式停止图标
        └── stopGeneration_dark.svg        # Dark 模式停止图标
```

## 隐私和安全

- Git Diff 和提示词会发送到你配置的 AI 服务，请确认该服务符合项目的数据安全要求。
- API 密钥保存在 IntelliJ IDEA 的本地非漫游配置中，不会由插件主动上传到其他服务。
- 不要把包含真实 API 密钥的 IDE 配置文件提交到 Git 仓库。
- 建议为插件使用单独创建、权限受限且可以随时撤销的 API 密钥。

## 常见问题

### 点击生成后提示没有变更

在 Commit 工具窗口中触发时，请确认至少勾选了一个文件。其他场景下触发时，请先执行 `git add` 暂存文件。

### 接口检测返回 401 或 403

检查 API 密钥是否正确、是否过期，以及当前密钥是否有权访问填写的模型。

### 接口检测返回 404

检查接口地址是否为完整的 Chat Completions 地址，例如：

```text
https://api.openai.com/v1/chat/completions
```

### 生成过程中如何停止

生成开始后，Commit 工具栏中的 `M` 图标会变成红色停止图标。再次点击即可请求取消当前任务。

### 构建时出现 `JavaVersion.parse("25")`

这是 IDEA 2024.3 内置 Gradle 插件解析新版 Java 兼容性数据时产生的问题。项目已经关闭 `buildSearchableOptions`，请执行一次干净构建：

```powershell
.\gradlew.bat clean buildPlugin
```

## 参与贡献

欢迎通过 Issue 或 Pull Request 提交以下内容：

- Bug 修复
- 新的模型服务兼容方案
- UI 和交互改进
- 提示词模板优化
- 文档完善

提交代码前，请确保：

```bash
gradle clean buildPlugin
```

可以正常执行。
