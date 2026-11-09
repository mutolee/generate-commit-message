package org.aicommitmessage;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/** 项目打开后触发应用级检查；网络访问由服务放入后台线程。 */
public final class PluginUpdateStartupActivity implements StartupActivity.DumbAware {
    /** 跳过无界面和测试运行，其他项目共用每日检查资格。 */
    @Override
    public void runActivity(@NotNull Project project) {
        var application = ApplicationManager.getApplication();
        if (!project.isDisposed() && !application.isHeadlessEnvironment() && !application.isUnitTestMode()) {
            PluginUpdateService.getInstance().check(false); // 不阻塞项目打开或索引过程。
        }
    }
}
