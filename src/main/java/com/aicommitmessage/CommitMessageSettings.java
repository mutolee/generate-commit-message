package com.aicommitmessage;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import org.jetbrains.annotations.NotNull;

/**
 * AI 提交信息生成器的持久化配置，保存兼容 OpenAI 的服务地址、模型和密钥。
 *
 * @author 杨林恩
 */
@Service(Service.Level.APP)
@State(name = "AICommitMessageSettings", storages = @Storage(StoragePathMacros.NON_ROAMABLE_FILE))
public final class CommitMessageSettings implements PersistentStateComponent<CommitMessageSettings.State> {
    /**
     * 可序列化的设置数据。
     *
     * @author 杨林恩
     */
    public static class State {
        public String endpoint = "https://api.openai.com/v1/chat/completions";
        public String model = "gpt-4o-mini";
        public String apiKey = "";
        public String language = "English";
    }

    private State state = new State();

    /**
     * 获取应用级配置实例。
     */
    public static CommitMessageSettings getInstance() {
        return ApplicationManager.getApplication().getService(CommitMessageSettings.class);
    }

    /**
     * 返回当前配置状态。
     */
    @Override
    public @NotNull State getState() {
        return state;
    }

    /**
     * 恢复持久化配置状态。
     */
    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }
}
