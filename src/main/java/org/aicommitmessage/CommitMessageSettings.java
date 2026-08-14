package org.aicommitmessage;

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
    private static final String LEGACY_ENGLISH_PROMPT_TEMPLATE = """
            Generate one concise Git commit message from the following diff.
            Follow Conventional Commits using the format: type(scope): description.
            Use imperative mood. Do not use Markdown or quotation marks.
            Reply with only the commit message in {{language}}.

            DIFF:
            {{diff}}""";
    private static final String LEGACY_CHINESE_PROMPT_TEMPLATE = """
            请根据下面的代码差异生成一条简洁的 Git 提交信息。
            请遵循 Conventional Commits 格式：type(scope): description。
            使用祈使语气，不要使用 Markdown 或引号。
            只返回提交信息，语言为：{{language}}。

            代码差异：
            {{diff}}""";

    /** 默认提示词模板，使用语言和 Git 差异变量生成 Conventional Commit 信息。 */
    public static final String DEFAULT_PROMPT_TEMPLATE = """
            请根据下面的代码差异生成 Git 提交信息，并严格使用以下格式：

            type(scope): 简洁的提交摘要

            - 具体变更一
            - 具体变更二
            - 具体变更三

            要求：
            1. 第一行遵循 Conventional Commits，格式必须为 type(scope): description。
            2. 第一行后空一行，再使用“- ”开头逐条列出主要变更。
            3. 根据实际差异生成 2 至 6 条变更说明，不要编造代码中不存在的内容。
            4. 不要使用代码块、标题、引号或额外解释。
            5. 只返回提交信息，语言为：{{language}}。

            代码差异：
            {{diff}}""";

    /**
     * 可序列化的设置数据。
     *
     * @author 杨林恩
     */
    public static class State {
        public String endpoint = "https://api.openai.com/v1/chat/completions";
        public String model = "gpt-4o-mini";
        public String apiKey = "";
        public String language = "中文";
        public String promptTemplate = DEFAULT_PROMPT_TEMPLATE;

        /**
         * 获取用户配置的提示词模板，兼容尚未保存该字段的旧版本配置。
         *
         * @return 非空的提示词模板
         */
        public String getPromptTemplate() {
            if (promptTemplate == null || promptTemplate.isBlank()
                    || LEGACY_ENGLISH_PROMPT_TEMPLATE.equals(promptTemplate)
                    || LEGACY_CHINESE_PROMPT_TEMPLATE.equals(promptTemplate)) {
                return DEFAULT_PROMPT_TEMPLATE;
            }
            return promptTemplate;
        }
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
