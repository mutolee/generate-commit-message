package org.aicommitmessage;

import com.intellij.ide.DataManager;
import com.intellij.ide.BrowserUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Consumer;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.awt.Component;
import java.awt.Point;
import java.awt.event.MouseEvent;

/**
 * 显示插件图标，并通过弹出菜单打开现有的 AI Commit Message 配置页。
 */
final class CommitMessageStatusBarWidget implements StatusBarWidget, StatusBarWidget.IconPresentation {
    // 复用现有图标；平台自动选取深色主题对应的 SVG。
    private static final Icon ICON = IconLoader.getIcon("/icons/commitMessage.svg", CommitMessageStatusBarWidget.class);
    private final Project project;
    private boolean disposed;

    /** @param project 入口所属的项目窗口 */
    CommitMessageStatusBarWidget(Project project) {
        this.project = project;
    }

    /** @return 与工厂和扩展注册一致的组件标识 */
    @Override
    public @NotNull String ID() {
        return CommitMessageStatusBarWidgetFactory.WIDGET_ID;
    }

    /** @return 使用纯图标形式呈现组件 */
    @Override
    public @NotNull WidgetPresentation getPresentation() {
        return this;
    }

    /** @return 支持明暗主题的插件图标 */
    @Override
    public @NotNull Icon getIcon() {
        return ICON;
    }

    /** @return 鼠标悬停时显示的插件名称 */
    @Override
    public @NotNull String getTooltipText() {
        return "AI Commit Message";
    }

    /** @return 点击图标时打开菜单的处理器 */
    @Override
    public @NotNull Consumer<MouseEvent> getClickConsumer() {
        return this::showMenu;
    }

    /** @param event 状态栏图标的鼠标点击事件 */
    private void showMenu(MouseEvent event) {
        if (disposed || project.isDisposed()) { // 项目或组件已关闭时忽略延迟到达的点击。
            return;
        }
        DefaultActionGroup actions = new DefaultActionGroup();
        actions.add(new DumbAwareAction("Setting", "打开 AI Commit Message 设置", AllIcons.General.Settings) { // 使用内置设置图标，索引期间也可操作。
            /** @param actionEvent 用户选择设置菜单的事件 */
            @Override
            public void actionPerformed(@NotNull AnActionEvent actionEvent) {
                if (!disposed && !project.isDisposed()) { // 打开设置前再次确认项目有效。
                    // 按配置类定位现有页面，直接进入 Settings | Tools | AI Commit Message。
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, CommitMessageConfigurable.class);
                }
            }
        });
        DumbAwareAction githubAction = new DumbAwareAction(
                "Github", "打开插件的 GitHub 仓库", AllIcons.Vcs.Vendors.Github) {
            /** @param actionEvent 用户选择 GitHub 菜单的事件 */
            @Override
            public void actionPerformed(@NotNull AnActionEvent actionEvent) {
                // 在系统浏览器中打开项目仓库，便于查看源码和反馈问题。
                BrowserUtil.browse("https://github.com/mutolee/generate-commit-message");
            }
        };
        // 使用平台原生副标题样式在菜单右侧显示作者，自动适配主题颜色。
        githubAction.getTemplatePresentation().putClientProperty(ActionUtil.SECONDARY_TEXT, "Author: mutolee");
        actions.add(githubAction); // 将 GitHub 入口放在设置菜单下方。
        Component component = event.getComponent(); // 将菜单锚定到被点击的状态栏图标。
        ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
                "AI Commit Message", actions, DataManager.getInstance().getDataContext(component),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true); // 使用平台原生菜单和键盘导航。
        Disposer.register(this, popup); // 插件卸载或项目关闭时自动释放弹出菜单。
        popup.setMinimumSize(JBUI.size(240, 0)); // 加宽菜单并适配 IDE 缩放，高度仍由菜单内容决定。
        int height = popup.getContent().getPreferredSize().height; // 计算高度，让菜单在状态栏上方展开。
        popup.show(new RelativePoint(component, new Point(0, -height))); // 在当前窗口显示菜单。
    }

    /** 标记组件已释放，阻止后续点击继续打开设置。 */
    @Override
    public void dispose() {
        disposed = true;
    }
}
