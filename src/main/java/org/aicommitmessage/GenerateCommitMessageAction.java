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

/**
 * 从 IDEA 提交界面勾选的变更调用兼容 OpenAI 的模型生成提交信息，并复制到剪贴板。
 *
 * @author 杨林恩
 */
public final class GenerateCommitMessageAction extends AnAction {
    /**
     * 处理用户点击生成按钮的操作。
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }

        // 在事件线程中读取提交界面的勾选状态，避免后台线程访问 Swing UI 状态。
        CommitWorkflowUi workflowUi = event.getData(VcsDataKeys.COMMIT_WORKFLOW_UI);
        List<Change> includedChanges = workflowUi == null ? List.of() : List.copyOf(workflowUi.getIncludedChanges());
        List<FilePath> includedUnversionedFiles = workflowUi == null ? List.of() : List.copyOf(workflowUi.getIncludedUnversionedFiles());
        boolean hasCommitUiContext = workflowUi != null;

        // 将耗时的 Git 和网络请求放入后台任务，避免阻塞 IDEA 界面。
        new Task.Backgroundable(project, "Generating commit message", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    // 优先读取 Commit 窗口中勾选的变更，非 Commit 上下文才回退到 Git 暂存区。
                    String diff = hasCommitUiContext
                            ? includedDiff(project, includedChanges, includedUnversionedFiles)
                            : stagedDiff(project);
                    if (diff.isBlank()) {
                        throw new IllegalStateException(hasCommitUiContext
                                ? "No changes are included in the Commit tool window."
                                : "No staged changes found. Stage files before generating a message.");
                    }
                    indicator.setText("Calling AI service…");
                    // 调用模型生成提交信息。
                    String message = request(diff, CommitMessageSettings.getInstance().getState());
                    // 将结果写入系统剪贴板，便于粘贴到提交信息编辑器。
                    CopyPasteManager.getInstance().setContents(new StringSelection(message));
                    SwingUtilities.invokeLater(() -> showNotification(project, "Commit message copied to clipboard", message, NotificationType.INFORMATION));
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> showNotification(project, "Could not generate commit message", ex.getMessage(), NotificationType.ERROR));
                }
            }
        }.queue();
    }

    /**
     * 获取 Commit 工具窗口中已勾选文件的差异内容。
     *
     * @param project 当前 IDEA 项目
     * @param includedChanges 已勾选的版本控制变更
     * @param includedUnversionedFiles 已勾选的未版本控制文件
     * @return 可用于生成提交信息的差异文本
     * @throws Exception Git 命令执行或文件内容读取失败时抛出
     */
    private static String includedDiff(Project project,
                                       List<Change> includedChanges,
                                       List<FilePath> includedUnversionedFiles) throws Exception {
        String basePath = project.getBasePath();
        if (basePath == null) {
            throw new IllegalStateException("Project base path is unavailable.");
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
                throw new IllegalStateException(StringUtil.notNullize(output.getStderr(), "Git diff failed"));
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
     * @param projectRoot 项目根目录
     * @param filePath IDEA 文件路径
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
     * @param filePath 未版本控制文件路径
     * @param diff 接收补丁文本的缓冲区
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
            throw new IllegalStateException(StringUtil.notNullize(output.getStderr(), "Git diff failed"));
        }
        return output.getStdout();
    }

    private static String request(String diff, CommitMessageSettings.State settings) throws Exception {
        if (settings.apiKey == null || settings.apiKey.isBlank()) {
            throw new IllegalStateException("Configure an API key in Settings | Tools | AI Commit Message.");
        }
        String prompt = "Generate one concise Git commit message from this staged diff. Follow Conventional Commits (type(scope): description), imperative mood, no markdown, no quotes. Reply with only the message. Language: " + settings.language + "\n\nDIFF:\n" + diff;
        String body = "{\"model\":\"" + json(settings.model) + "\",\"messages\":[{\"role\":\"user\",\"content\":\"" + json(prompt) + "\"}],\"temperature\":0.2}";
        HttpRequest req = HttpRequest.newBuilder(URI.create(settings.endpoint)).timeout(Duration.ofSeconds(60)).header("Authorization", "Bearer " + settings.apiKey).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("AI service returned HTTP " + response.statusCode() + ": " + response.body());
        }
        Matcher m = Pattern.compile("\\\"content\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(response.body());
        if (!m.find()) {
            throw new IllegalStateException("AI response did not contain a message.");
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
