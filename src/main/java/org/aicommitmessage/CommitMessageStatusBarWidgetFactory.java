package org.aicommitmessage;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import org.jetbrains.annotations.NotNull;

/**
 * 为每个项目窗口创建默认启用的提交信息插件状态栏入口。
 */
public final class CommitMessageStatusBarWidgetFactory implements StatusBarWidgetFactory {
    static final String WIDGET_ID = "AICommitMessage.StatusBar";

    /** @return 与扩展注册一致的状态栏组件标识 */
    @Override
    public @NotNull String getId() {
        return WIDGET_ID;
    }

    /** @return 状态栏显示选项中的插件名称 */
    @Override
    public @NotNull String getDisplayName() {
        return "AI Commit Message";
    }

    /** @return 首次安装时默认显示入口 */
    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    /** @param project 当前项目 @return 项目仍有效时可显示入口 */
    @Override
    public boolean isAvailable(@NotNull Project project) {
        return !project.isDisposed(); // 避免为已关闭项目创建入口。
    }

    /** @param project 当前项目 @return 该项目独立的状态栏组件 */
    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
        return new CommitMessageStatusBarWidget(project); // 保留所属项目以正确打开设置窗口。
    }

    /** @param widget 待释放的状态栏组件 */
    @Override
    public void disposeWidget(@NotNull StatusBarWidget widget) {
        Disposer.dispose(widget); // 同时释放尚未关闭的菜单。
    }

    /** @param statusBar 目标状态栏 @return 允许用户在状态栏菜单中启用入口 */
    @Override
    public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
        return true;
    }
}
