package org.aicommitmessage;

import java.util.List;

/**
 * 表示兼容 OpenAI 聊天补全接口的请求体，统一承载模型、消息及生成参数。
 *
 * @param model       模型名称
 * @param messages    对话消息列表
 * @param temperature 生成温度
 * @param thinking    思考模式配置
 * @param stream      是否启用流式响应
 * @author 杨林恩
 */
record ChatCompletionRequest(
        String model,
        List<ChatMessage> messages,
        double temperature,
        ThinkingConfig thinking,
        boolean stream
) {
    /**
     * 创建仅包含一条用户消息的聊天补全请求。
     *
     * @param model       模型名称
     * @param content     用户消息内容
     * @param temperature 生成温度
     * @param stream      是否启用流式响应
     * @return 聊天补全请求对象
     */
    static ChatCompletionRequest userMessage(String model,
                                             String content,
                                             double temperature,
                                             boolean stream) {
        return new ChatCompletionRequest(
                model,
                List.of(new ChatMessage("user", content)),
                temperature,
                new ThinkingConfig("disabled"),
                stream
        );
    }

    /**
     * 表示聊天补全接口中的单条对话消息。
     *
     * @param role    消息角色
     * @param content 消息内容
     * @author 杨林恩
     */
    record ChatMessage(String role, String content) {
    }

    /**
     * 表示模型的思考模式配置。
     *
     * @param type 思考模式类型
     * @author 杨林恩
     */
    record ThinkingConfig(String type) {
    }
}
