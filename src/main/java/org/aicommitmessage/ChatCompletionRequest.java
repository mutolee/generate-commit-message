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
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

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
     * 将当前请求对象序列化为 JSON，不依赖额外的第三方 JSON 库。
     *
     * @return 符合 JSON 规范的请求体
     */
    String toJson() {
        StringBuilder json = new StringBuilder(256);
        json.append('{')
                .append("\"model\":");
        appendJsonString(json, model);
        json.append(",\"messages\":[");
        for (int i = 0; i < messages.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            ChatMessage message = messages.get(i);
            json.append("{\"role\":");
            appendJsonString(json, message.role());
            json.append(",\"content\":");
            appendJsonString(json, message.content());
            json.append('}');
        }
        json.append("]")
                .append(",\"temperature\":").append(temperature)
                .append(",\"thinking\":");
        if (thinking == null) {
            json.append("null");
        } else {
            json.append("{\"type\":");
            appendJsonString(json, thinking.type());
            json.append('}');
        }
        json.append(",\"stream\":").append(stream).append('}');
        return json.toString();
    }

    /**
     * 将字符串按照 JSON 规范转义并追加到目标缓冲区。
     *
     * @param json  接收 JSON 内容的缓冲区
     * @param value 待序列化的字符串
     */
    private static void appendJsonString(StringBuilder json, String value) {
        if (value == null) {
            json.append("null");
            return;
        }
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    // JSON 不允许裸控制字符；代理项也统一转义，避免 UTF-8 编码时丢失字符。
                    if (character < 0x20 || Character.isSurrogate(character)) {
                        appendUnicodeEscape(json, character);
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        json.append('"');
    }

    /**
     * 将单个 UTF-16 字符追加为 JSON Unicode 转义序列。
     *
     * @param json      接收 JSON 内容的缓冲区
     * @param character 待转义字符
     */
    private static void appendUnicodeEscape(StringBuilder json, char character) {
        json.append("\\u")
                .append(HEX_DIGITS[(character >>> 12) & 0xF])
                .append(HEX_DIGITS[(character >>> 8) & 0xF])
                .append(HEX_DIGITS[(character >>> 4) & 0xF])
                .append(HEX_DIGITS[character & 0xF]);
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
