package org.aicommitmessage;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/** Tools 中的插件子菜单，与状态栏共用菜单操作。 */
public final class CommitMessageMenuGroup extends DefaultActionGroup {
    /** 平台通过无参构造器创建分组；全局注册的操作不保存项目引用。 */
    public CommitMessageMenuGroup() {
        // 在二级菜单内部显示原生分组标题，状态栏继续使用自己的弹窗标题。
        add(Separator.create("Ai Commit Message"));
        addAll(createPopupActions(null, () -> true).getChildren((AnActionEvent) null));
    }

    /**
     * 创建共用操作；状态栏传入所属项目与组件有效性检查，Tools 从事件获取当前项目。
     * 横幅和弹窗布局仍由状态栏组件管理。
     */
    static @NotNull DefaultActionGroup createPopupActions(@Nullable Project popupProject,
                                                         @NotNull BooleanSupplier isAvailable) {
        DefaultActionGroup actions = new DefaultActionGroup();
        actions.add(new DumbAwareAction("Setting", "打开 AI Commit Message 设置", AllIcons.General.Settings) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                Project project = popupProject != null ? popupProject : event.getProject();
                if (isAvailable.getAsBoolean() && (project == null || !project.isDisposed())) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, CommitMessageConfigurable.class);
                }
            }
        });
        actions.add(new DumbAwareAction(SettingsMessages.message("update.check"), null, AllIcons.Actions.Refresh) {
            /** 手动检查复用应用级服务，两个菜单行为保持一致。 */
            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                Project project = popupProject != null ? popupProject : event.getProject();
                if (isAvailable.getAsBoolean() && (project == null || !project.isDisposed())) {
                    PluginUpdateService.getInstance().check(true, project); // 在当前项目显示原生后台进度。
                }
            }
        });
        DumbAwareAction github = new DumbAwareAction(
                "Github", "打开插件的 GitHub 仓库", AllIcons.Vcs.Vendors.Github) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                BrowserUtil.browse("https://github.com/mutolee/generate-commit-message");
            }
        };
        // Tools 原生子菜单把作者并入文字，状态栏弹窗保留独立作者副标题。
        github.addTextOverride(ActionPlaces.MAIN_MENU, "Github (Author: mutolee)");
        github.getTemplatePresentation().putClientProperty(ActionUtil.SECONDARY_TEXT, "Author: mutolee");
        actions.add(github);
        return actions;
    }
}
