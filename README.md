# AI Commit Message

一个 IntelliJ IDEA 插件：在 Git Commit 窗口点击 **Generate Commit Message**，插件读取 `git diff --cached`，调用兼容 OpenAI Chat Completions API 的模型生成 Conventional Commit 格式的提交信息，并自动复制到剪贴板。

## 使用

1. 在 `Settings | Tools | AI Commit Message` 填写 API endpoint、模型、API key 和语言。
2. 在 Git Commit 窗口先暂存文件。
3. 点击提交信息编辑器工具栏中的 **Generate Commit Message**（也可从 `Find Action` 搜索）。
4. 生成内容会复制到剪贴板，粘贴到提交信息框后提交。

## 构建

使用 Gradle IntelliJ Platform Plugin 2.x，运行 `buildPlugin` 生成 `build/distributions/` 下的 ZIP 安装包。
