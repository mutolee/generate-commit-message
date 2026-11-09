package org.aicommitmessage;

import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.impl.NotificationFullContent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.Future;

/** 应用级更新检查服务：后台访问 Marketplace，统一保存节流和忽略记录。 */
@Service(Service.Level.APP)
@State(name = "AICommitMessageUpdates", storages = @Storage(StoragePathMacros.NON_ROAMABLE_FILE))
public final class PluginUpdateService implements PersistentStateComponent<UpdateCheckPolicy.State>, Disposable {
    // 此接口与最低支持的 2024.3 平台兼容；查询返回默认发布通道的兼容更新。
    private static final String ENDPOINT = "https://plugins.jetbrains.com/api/search/compatibleUpdates";
    private static final Logger LOG = Logger.getInstance(PluginUpdateService.class);
    private final UpdateCheckPolicy policy = new UpdateCheckPolicy();
    private volatile boolean disposed;
    private Future<?> request;
    private volatile ProgressIndicator manualIndicator;
    private Notification updateNotification;

    /** 获取所有项目共享的服务实例。 */
    static PluginUpdateService getInstance() {
        return ApplicationManager.getApplication().getService(PluginUpdateService.class);
    }

    /** 在没有项目上下文时发起检查，供自动检查入口使用。 */
    void check(boolean manual) {
        check(manual, null); // 自动检查继续使用安静的后台线程。
    }

    /** 手动检查显示所属项目的原生后台进度，并绕过每日限制。 */
    synchronized void check(boolean manual, @Nullable Project project) {
        if (disposed) return;
        if (!policy.begin(manual, System.currentTimeMillis())) {
            if (manual) showMessage("update.busy", NotificationType.INFORMATION); // 重复点击有明确反馈。
            return;
        }
        try {
            if (manual) {
                new Task.Backgroundable(project, SettingsMessages.message("update.checking"), false) {
                    private volatile boolean started;

                    /** 请求存续期间显示不确定进度，完成或失败后由平台自动收起任务。 */
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        started = true;
                        manualIndicator = indicator;
                        try {
                            if (disposed) {
                                policy.finish(); // 已卸载的插件不再发起请求。
                                return;
                            }
                            indicator.setIndeterminate(true); // 版本查询没有可计算的完成百分比。
                            performCheck(true); // 整个网络请求都包含在可见后台任务中。
                        } finally {
                            manualIndicator = null;
                        }
                    }

                    /** 项目关闭导致任务尚未开始便取消时，也释放检查资格。 */
                    @Override
                    public void onCancel() {
                        if (!started) policy.finish(); // 已开始任务的取消由 performCheck 处理。
                    }
                }.queue(); // 默认后台运行，不弹出阻塞式进度窗口。
            } else {
                request = ApplicationManager.getApplication().executeOnPooledThread(() -> performCheck(false));
            }
        } catch (RuntimeException exception) {
            policy.finish(); // 提交任务失败也必须释放并发锁。
            throw exception;
        }
    }

    /** 在线查询已安装版本对应的最新兼容版本，失败只对手动检查显示提示。 */
    private void performCheck(boolean manual) {
        try {
            ProgressManager.checkCanceled(); // 项目关闭或插件卸载后不再继续检查。
            var installed = PluginManagerCore.getPlugin(PluginId.getId(MarketplaceUpdate.PLUGIN_ID));
            if (installed == null) throw new IOException("Installed plugin descriptor unavailable");
            String currentVersion = installed.getVersion(); // 始终读取安装包版本，不硬编码构建版本。
            String body = MarketplaceUpdate.requestBody(ApplicationInfo.getInstance().getBuild().asString());
            MarketplaceUpdate latest = HttpRequests.post(ENDPOINT, HttpRequests.JSON_CONTENT_TYPE)
                    .connectTimeout(5000).readTimeout(10000).productNameAsUserAgent()
                    .connect(connection -> {
                        connection.write(body); // 使用 IDE 的代理和证书设置发送元数据查询。
                        return MarketplaceUpdate.parse(connection.readString(ProgressManager.getInstance().getProgressIndicator()));
                    });
            String summary = latest != null && latest.isNewerThan(currentVersion) ? loadUpdateSummary(latest) : ""; // 仅发现新版本时请求说明。
            ProgressManager.checkCanceled(); // 已取消任务不再排队显示结果通知。
            ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    if (disposed) return;
                    if (latest == null) {
                        if (manual) showMessage("update.unavailable", NotificationType.INFORMATION);
                    } else if (latest.isNewerThan(currentVersion)) {
                        if (policy.claimNotification(latest.version(), manual)) showUpdate(currentVersion, latest, summary);
                    } else if (manual) {
                        showMessage("update.current", NotificationType.INFORMATION);
                    }
                } finally {
                    policy.finish(); // 通知处理完成后才允许下一次请求，避免跨线程重复弹出。
                }
            });
        } catch (ProcessCanceledException exception) {
            policy.finish(); // 保留平台取消语义，不将取消当作网络错误。
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            policy.finish();
            LOG.debug("Marketplace update check failed", exception); // 自动检查不弹错误通知。
            if (manual) showMessage("update.failed", NotificationType.WARNING);
        } catch (RuntimeException exception) {
            policy.finish(); // 避免平台异常导致检查永久处于进行中。
            throw exception;
        }
    }

    /** 后台获取指定版本的发布说明；说明缺失或请求失败不阻止更新提醒。 */
    private String loadUpdateSummary(MarketplaceUpdate latest) {
        if (latest.updateId() <= 0) return "";
        try {
            return HttpRequests.request("https://plugins.jetbrains.com/api/updates/" + latest.updateId())
                    .connectTimeout(3000).readTimeout(5000).productNameAsUserAgent()
                    .connect(connection -> latest.parseNotes(connection.readString(ProgressManager.getInstance().getProgressIndicator())));
        } catch (IOException | IllegalArgumentException exception) {
            LOG.debug("Marketplace update notes unavailable", exception); // 非关键说明失败时保留版本通知。
            return "";
        }
    }

    /** 显示版本信息、简短更新说明与两个操作。仅在 UI 线程调用。 */
    private void showUpdate(String current, MarketplaceUpdate latest, String summary) {
        if (updateNotification != null) updateNotification.expire(); // 手动重复检查替换旧提醒。
        String content = SettingsMessages.message("update.available",
                StringUtil.escapeXmlEntities(current), StringUtil.escapeXmlEntities(latest.version()));
        if (!summary.isBlank()) {
            String bullets = "• " + StringUtil.escapeXmlEntities(summary).replace("\n", "<br>• "); // 远端内容始终作为纯文本渲染。
            content += "<br><br>" + SettingsMessages.message("update.notes", bullets);
        }
        Notification notification = new UpdateNotification(content);
        notification.setSuggestionType(true); // 将首个操作显示为 IDE 原生按钮。
        notification.addAction(NotificationAction.createExpiring(SettingsMessages.message("update.open"),
                (event, ignored) -> openPluginSettings(event.getProject()))); // 点击时使用当前项目，不长期保存项目引用。
        notification.addAction(NotificationAction.createSimpleExpiring(SettingsMessages.message("update.ignore"),
                () -> policy.ignore(latest.version()))); // 忽略仅影响自动检查，且跨 IDE 重启持久化。
        updateNotification = notification;
        notification.notify(null); // 应用级通知不持有已关闭的项目，也不会按项目重复创建。
    }

    /** 打开完整设置窗口的已安装插件页，并按当前插件名称筛选定位。 */
    private void openPluginSettings(@Nullable Project project) {
        if (disposed || (project != null && project.isDisposed())) return;
        var installed = PluginManagerCore.getPlugin(PluginId.getId(MarketplaceUpdate.PLUGIN_ID)); // 按插件 ID 获取实际显示名称。
        if (installed == null) return;
        ShowSettingsUtil.getInstance().showSettingsDialog(project, PluginManagerConfigurable.class,
                configurable -> {
                    configurable.openInstalledTab(installed.getName()); // 首次创建时选择已安装插件页。
                    Runnable search = configurable.enableSearch(installed.getName()); // 已创建的设置页也刷新搜索，避免沿用旧选择。
                    if (search != null) search.run(); // 筛选当前插件并显示详情。
                });
    }

    /** 将手动检查的状态反馈切回 UI 线程，卸载后不再显示。 */
    private void showMessage(String key, NotificationType type) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!disposed) createNotification(SettingsMessages.message(key), type).notify(null);
        });
    }

    /** 使用独立通知组，让用户可以单独配置更新提醒。 */
    private Notification createNotification(String content, NotificationType type) {
        return NotificationGroupManager.getInstance().getNotificationGroup("AI Commit Message Updates")
                .createNotification(SettingsMessages.message("update.title"), content, type);
    }

    /** 使用平台的完整内容标记，避免版本信息占满折叠区域后隐藏更新摘要。 */
    private static final class UpdateNotification extends Notification implements NotificationFullContent {
        private UpdateNotification(String content) {
            super("AI Commit Message Updates", SettingsMessages.message("update.title"), content,
                    NotificationType.INFORMATION);
        }
    }

    /** 保存检查时间与忽略版本的快照。 */
    @Override
    public UpdateCheckPolicy.@NotNull State getState() { return policy.snapshot(); }

    /** 加载本机检查记录，避免重启后重复自动检查。 */
    @Override
    public void loadState(UpdateCheckPolicy.@NotNull State state) { policy.restore(state); }

    /** 插件卸载时取消后台请求并移除仍可点击的更新通知。 */
    @Override
    public synchronized void dispose() {
        disposed = true;
        ProgressIndicator indicator = manualIndicator;
        if (indicator != null) indicator.cancel(); // 卸载时终止手动任务的进度上下文。
        if (request != null) request.cancel(true); // 网络还有超时上限，避免长期占用线程。
        if (updateNotification != null) updateNotification.expire();
    }
}
