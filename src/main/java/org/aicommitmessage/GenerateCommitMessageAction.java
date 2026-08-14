package org.aicommitmessage;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.vcs.commit.CommitWorkflowUi;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 从 IDEA 提交界面勾选的变更生成提交信息，支持写入提交框以及中途取消任务。
 *
 * @author 杨林恩
 */
public final class GenerateCommitMessageAction extends AnAction {
    private static final Icon GENERATE_ICON = IconLoader.getIcon("/icons/commitMessage.svg", GenerateCommitMessageAction.class);
    private static final Icon STOP_ICON = IconLoader.getIcon("/icons/stopGeneration.svg", GenerateCommitMessageAction.class);
    private static final ConcurrentMap<Project, GenerationControl> RUNNING_GENERATIONS = new ConcurrentHashMap<>();

    /**
     * 处理用户点击生成按钮的操作。
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }

        GenerationControl runningGeneration = RUNNING_GENERATIONS.get(project);
        if (runningGeneration != null) {
            // 再次点击红色停止图标时，同时取消进度指示器并中断网络请求线程。
            runningGeneration.cancel();
            event.getPresentation().setText("正在停止生成任务");
            return;
        }

        GenerationControl generationControl = new GenerationControl();
        RUNNING_GENERATIONS.put(project, generationControl);
        event.getPresentation().setIcon(STOP_ICON);
        event.getPresentation().setText("停止生成提交信息");

        // 在事件线程中读取提交界面的勾选状态，避免后台线程访问 Swing UI 状态。
        CommitWorkflowUi workflowUi = event.getData(VcsDataKeys.COMMIT_WORKFLOW_UI);
        List<Change> includedChanges = workflowUi == null ? List.of() : List.copyOf(workflowUi.getIncludedChanges());
        List<FilePath> includedUnversionedFiles = workflowUi == null ? List.of() : List.copyOf(workflowUi.getIncludedUnversionedFiles());
        boolean hasCommitUiContext = workflowUi != null;

        // 将耗时的 Git 和网络请求放入后台任务，避免阻塞 IDEA 界面。
        new Task.Backgroundable(project, "正在生成提交信息", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                generationControl.start(indicator);
                try {
                    if (generationControl.isCancelled()) {
                        return;
                    }
                    // 优先读取 Commit 窗口中勾选的变更，非 Commit 上下文才回退到 Git 暂存区。
                    String diff = hasCommitUiContext
                            ? includedDiff(project, includedChanges, includedUnversionedFiles)
                            : stagedDiff(project);
                    if (diff.isBlank()) {
                        throw new IllegalStateException(hasCommitUiContext
                                ? "提交工具窗口中没有选中的变更。"
                                : "没有找到已暂存的变更，请先暂存文件再生成提交信息。");
                    }
                    indicator.setText("正在调用 AI 服务…");
                    // 调用模型生成提交信息。
                    String message = request(diff, CommitMessageSettings.getInstance().getState());
                    if (generationControl.isCancelled()) {
                        return;
                    }
                    SwingUtilities.invokeLater(() -> {
                        if (generationControl.isCancelled()) {
                            return;
                        }
                        if (workflowUi != null) {
                            // 直接写入当前提交窗口的提交信息编辑框，省去手动粘贴步骤。
                            workflowUi.getCommitMessageUi().setText(message);
                            showNotification(project, "提交信息已写入提交框", message, NotificationType.INFORMATION);
                        } else {
                            // 非提交窗口上下文没有可写入的编辑框，保留复制到剪贴板的回退行为。
                            CopyPasteManager.getInstance().setContents(new StringSelection(message));
                            showNotification(project, "提交信息已复制到剪贴板", message, NotificationType.INFORMATION);
                        }
                    });
                } catch (Exception ex) {
                    if (!generationControl.isCancelled()) {
                        SwingUtilities.invokeLater(() -> showNotification(project, "生成提交信息失败", ex.getMessage(), NotificationType.ERROR));
                    }
                } finally {
                    RUNNING_GENERATIONS.remove(project, generationControl);
                    SwingUtilities.invokeLater(() -> {
                        event.getPresentation().setIcon(GENERATE_ICON);
                        event.getPresentation().setText("生成提交信息");
                    });
                }
            }
        }.queue();
    }

    /**
     * 根据当前项目的生成状态切换生成图标和红色停止图标。
     *
     * @param event Action 更新事件
     */
    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        boolean running = project != null && RUNNING_GENERATIONS.containsKey(project);
        event.getPresentation().setIcon(running ? STOP_ICON : GENERATE_ICON);
        event.getPresentation().setText(running ? "停止生成提交信息" : "生成提交信息");
        event.getPresentation().setEnabled(project != null);
    }

    /**
     * 控制单个项目的提交信息生成任务，并提供可跨线程调用的取消能力。
     *
     * @author 杨林恩
     */
    private static final class GenerationControl {
        private volatile ProgressIndicator indicator;
        private volatile Thread workerThread;
        private volatile boolean cancelled;

        /**
         * 记录任务使用的进度指示器和工作线程。
         *
         * @param progressIndicator 后台任务进度指示器
         */
        private void start(ProgressIndicator progressIndicator) {
            indicator = progressIndicator;
            workerThread = Thread.currentThread();
            if (cancelled) {
                cancel();
            }
        }

        /**
         * 取消进度指示器并中断正在执行 Git 或网络请求的工作线程。
         */
        private void cancel() {
            cancelled = true;
            ProgressIndicator currentIndicator = indicator;
            if (currentIndicator != null) {
                currentIndicator.cancel();
            }
            Thread currentWorker = workerThread;
            if (currentWorker != null) {
                currentWorker.interrupt();
            }
        }

        /**
         * 判断当前生成任务是否已请求取消。
         *
         * @return 已请求取消时返回 {@code true}
         */
        private boolean isCancelled() {
            return cancelled;
        }
    }

    /**
     * 获取 Commit 工具窗口中已勾选文件的差异内容。
     *
     * @param project                  当前 IDEA 项目
     * @param includedChanges          已勾选的版本控制变更
     * @param includedUnversionedFiles 已勾选的未版本控制文件
     * @return 可用于生成提交信息的差异文本
     * @throws Exception Git 命令执行或文件内容读取失败时抛出
     */
    private static String includedDiff(Project project,
                                       List<Change> includedChanges,
                                       List<FilePath> includedUnversionedFiles) throws Exception {
        String basePath = project.getBasePath();
        if (basePath == null) {
            throw new IllegalStateException("无法获取项目根目录。");
        }

        Path projectRoot = Path.of(basePath).toAbsolutePath().normalize();
        Set<String> relativePaths = new LinkedHashSet<>();

        // 将 IDEA Change 对象转换为 Git 可以识别的项目相对路径。
        for (Change change : includedChanges) {
            ContentRevision revision = change.getAfterRevision() != null
                    ? change.getAfterRevision()
                    : change.getBeforeRevision();
            if (revision != null) {
                addRelativePath(projectRoot, revision.getFile(), relativePaths);
            }
        }

        StringBuilder diff = new StringBuilder();
        if (!relativePaths.isEmpty()) {
            // HEAD 到工作区的差异同时覆盖 IDEA 非暂存提交模式和 Git Staging Area 模式。
            GeneralCommandLine command = new GeneralCommandLine("git", "diff", "HEAD", "--no-ext-diff", "--")
                    .withWorkDirectory(basePath)
                    .withCharset(StandardCharsets.UTF_8);
            command.addParameters(List.copyOf(relativePaths));
            ProcessOutput output = new CapturingProcessHandler(command).runProcess(30_000);
            if (output.getExitCode() != 0) {
                throw new IllegalStateException(StringUtil.notNullize(output.getStderr(), "获取 Git 差异失败"));
            }
            diff.append(output.getStdout());
        }

        // Git diff 不会展示未跟踪文件，因此以新增文件补丁形式附加已勾选内容。
        for (FilePath filePath : includedUnversionedFiles) {
            appendUnversionedFile(projectRoot, filePath, diff);
        }
        return diff.toString();
    }

    /**
     * 将文件路径转换为项目相对路径并加入去重集合。
     *
     * @param projectRoot   项目根目录
     * @param filePath      IDEA 文件路径
     * @param relativePaths 接收项目相对路径的集合
     */
    private static void addRelativePath(Path projectRoot, FilePath filePath, Set<String> relativePaths) {
        Path absolutePath = Path.of(filePath.getPath()).toAbsolutePath().normalize();
        if (absolutePath.startsWith(projectRoot)) {
            // Git 路径参数统一使用正斜杠，兼容 Windows 环境。
            relativePaths.add(projectRoot.relativize(absolutePath).toString().replace('\\', '/'));
        }
    }

    /**
     * 将已勾选的未版本控制文件追加为新增文件形式的文本补丁。
     *
     * @param projectRoot 项目根目录
     * @param filePath    未版本控制文件路径
     * @param diff        接收补丁文本的缓冲区
     * @throws Exception 文件读取失败时抛出
     */
    private static void appendUnversionedFile(Path projectRoot, FilePath filePath, StringBuilder diff) throws Exception {
        Path absolutePath = Path.of(filePath.getPath()).toAbsolutePath().normalize();
        if (!absolutePath.startsWith(projectRoot) || !java.nio.file.Files.isRegularFile(absolutePath)) {
            return;
        }

        String relativePath = projectRoot.relativize(absolutePath).toString().replace('\\', '/');
        String content = java.nio.file.Files.readString(absolutePath, StandardCharsets.UTF_8);
        diff.append("diff --git a/").append(relativePath).append(" b/").append(relativePath).append('\n')
                .append("new file mode 100644\n")
                .append("--- /dev/null\n")
                .append("+++ b/").append(relativePath).append('\n')
                .append("@@ -0,0 +1,").append(content.lines().count()).append(" @@\n");
        content.lines().forEach(line -> diff.append('+').append(line).append('\n'));
    }

    /**
     * 获取 Git 暂存区差异，供不在 Commit 工具窗口中触发 Action 时使用。
     *
     * @param project 当前 IDEA 项目
     * @return Git 暂存区差异文本
     * @throws Exception Git 命令执行失败时抛出
     */
    private static String stagedDiff(Project project) throws Exception {
        GeneralCommandLine command = new GeneralCommandLine("git", "diff", "--cached", "--no-ext-diff")
                .withWorkDirectory(project.getBasePath())
                .withCharset(StandardCharsets.UTF_8);
        ProcessOutput output = new CapturingProcessHandler(command).runProcess(30_000);
        if (output.getExitCode() != 0) {
            throw new IllegalStateException(StringUtil.notNullize(output.getStderr(), "获取 Git 差异失败"));
        }
        return output.getStdout();
    }

    /**
     * 根据用户配置的提示词模板调用兼容 OpenAI 的聊天补全接口。
     *
     * @param diff     用于生成提交信息的 Git 差异
     * @param settings AI 服务及提示词配置
     * @return 模型生成的提交信息
     * @throws Exception 请求发送、响应解析或配置校验失败时抛出
     */
    private static String request(String diff, CommitMessageSettings.State settings) throws Exception {
        if (settings.apiKey == null || settings.apiKey.isBlank()) {
            throw new IllegalStateException("请先在“设置 | 工具 | AI Commit Message”中配置 API 密钥。");
        }
        // 替换模板变量，让用户可以控制生成规则，同时由插件注入语言和实际差异。
        String promptTemplate = settings.getPromptTemplate();
        String prompt = promptTemplate
                .replace("{{language}}", settings.language)
                .replace("{{diff}}", diff);
        if (!promptTemplate.contains("{{diff}}")) {
            // 用户省略差异变量时仍自动附加差异，避免向模型发送缺少上下文的请求。
            prompt += "\n\n代码差异：\n" + diff;
        }
        String body = "{\"model\":\"" + json(settings.model) + "\",\"messages\":[{\"role\":\"user\",\"content\":\"" + json(prompt) + "\"}],\"temperature\":0.2}";
        HttpRequest req = HttpRequest.newBuilder(URI.create(settings.endpoint)).timeout(Duration.ofSeconds(60)).header("Authorization", "Bearer " + settings.apiKey).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("AI 服务返回 HTTP " + response.statusCode() + "：" + response.body());
        }
        Matcher m = Pattern.compile("\\\"content\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(response.body());
        if (!m.find()) {
            throw new IllegalStateException("AI 响应中没有找到提交信息。");
        }
        return unescape(m.group(1)).trim();
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String unescape(String value) {
        return value.replace("\\n", "\n").replace("\\r", "\r").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static void showNotification(Project project, String title, String content, NotificationType type) {
        NotificationGroupManager.getInstance().getNotificationGroup("AI Commit Message").createNotification(title, content, type).notify(project);
    }
}
