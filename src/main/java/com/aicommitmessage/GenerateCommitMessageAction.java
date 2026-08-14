package com.aicommitmessage;

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
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 Git 暂存区差异调用兼容 OpenAI 的模型生成提交信息，并复制到剪贴板。
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
        new Task.Backgroundable(project, "Generating commit message", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    String diff = stagedDiff(project);
                    if (diff.isBlank()) {
                        throw new IllegalStateException("No staged changes found. Stage files before generating a message.");
                    }
                    indicator.setText("Calling AI service…");
                    String message = request(diff, CommitMessageSettings.getInstance().getState());
                    CopyPasteManager.getInstance().setContents(new StringSelection(message));
                    SwingUtilities.invokeLater(() -> showNotification(project, "Commit message copied to clipboard", message, NotificationType.INFORMATION));
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> showNotification(project, "Could not generate commit message", ex.getMessage(), NotificationType.ERROR));
                }
            }
        }.queue();
    }

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
