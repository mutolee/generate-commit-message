package org.aicommitmessage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** 定义 API 格式及其请求、响应策略，新增协议时无需修改配置界面和生成流程。 */
enum ApiFormat {
    OPENAI_CHAT_COMPLETIONS("openai-chat-completions", "OpenAI Chat Completions",
            "https://api.openai.com/v1/chat/completions", null) {
        /** 复用现有聊天补全请求，保持第三方服务的参数兼容性。 */
        @Override
        String createRequest(String model, String prompt, double temperature, boolean stream) {
            return ChatCompletionRequest.userMessage(model, prompt, temperature, stream).toJson();
        }

        /** 从第一个候选消息中提取正文。 */
        @Override
        String responseText(JsonObject response) {
            JsonObject choice = firstChoice(response); // 只使用默认候选，避免拼接多份答案。
            checkChatFinishReason(choice); // 普通响应和流式响应使用相同的完整性校验。
            return chatText(object(choice, "message"));
        }

        /** 提取聊天增量，忽略角色、usage 和工具调用等非正文事件。 */
        @Override
        StreamUpdate streamUpdate(JsonObject event) {
            JsonObject choice = firstChoice(event);
            checkChatFinishReason(choice);
            return new StreamUpdate(chatText(object(choice, "delta")), null, false);
        }
    },
    OPENAI_RESPONSES("openai-responses", "OpenAI Responses", "https://api.openai.com/v1/responses", "Codex Desktop") {
        /** 使用 Responses 原生 input；不携带旧协议扩展或模型可能不支持的采样参数。 */
        @Override
        String createRequest(String model, String prompt, double temperature, boolean stream) {
            JsonObject request = new JsonObject();
            request.addProperty("model", model); // 将模型保留在请求体中。
            request.addProperty("input", prompt); // 单轮纯文本输入无需构造历史消息。
            request.addProperty("stream", stream);
            request.addProperty("store", false); // 单次提交生成不需要保存服务端响应状态。
            return request.toString();
        }

        /** 遍历原生输出数组，仅拼接 assistant 消息的 output_text 内容。 */
        @Override
        String responseText(JsonObject response) {
            checkResponseStatus(response); // 错误、截断和仍在进行中的响应不能视为成功。
            StringBuilder text = new StringBuilder();
            for (JsonElement element : array(response, "output")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject item = element.getAsJsonObject();
                String role = string(item, "role");
                if (!"message".equals(string(item, "type")) || (!role.isEmpty() && !"assistant".equals(role))) {
                    continue; // 排除推理摘要、工具参数和非助手消息。
                }
                for (JsonElement part : array(item, "content")) {
                    if (!part.isJsonObject()) {
                        continue;
                    }
                    JsonObject content = part.getAsJsonObject();
                    if ("refusal".equals(string(content, "type"))) {
                        throw new IllegalStateException("AI 服务拒绝生成：" + string(content, "refusal"));
                    }
                    if ("output_text".equals(string(content, "type"))) {
                        text.append(string(content, "text")); // 不采集任何其他内容类型。
                    }
                }
            }
            return text.toString();
        }

        /** 处理 Responses 语义事件；完成快照与文本增量分开，避免重复追加。 */
        @Override
        StreamUpdate streamUpdate(JsonObject event) {
            return switch (string(event, "type")) {
                case "response.output_text.delta" -> new StreamUpdate(string(event, "delta"), null, false);
                case "response.completed" -> {
                    String text = responseText(object(event, "response"));
                    requireText(text); // 完成快照必须含有效正文，不能保留先前的残缺增量冒充成功。
                    yield new StreamUpdate("", text, true);
                }
                case "response.failed", "response.incomplete" -> {
                    JsonObject response = object(event, "response");
                    checkError(response); // 优先保留服务返回的详细失败原因。
                    throw new IllegalStateException("AI 响应未完成：" + string(event, "type") + " "
                            + string(object(response, "incomplete_details"), "reason"));
                }
                case "response.refusal.done" -> throw new IllegalStateException("AI 服务拒绝生成：" + string(event, "refusal"));
                default -> new StreamUpdate("", null, false); // 忽略元数据、推理和重复的 text.done 事件。
            };
        }

        /** Responses 必须收到完成事件，防止断开的流被当作完整提交信息。 */
        @Override
        boolean requiresCompletionEvent() {
            return true;
        }
    };

    private final String id;
    private final String label;
    private final String exampleEndpoint;
    private final String userAgent;

    /** 保存持久化标识、显示名称、完整地址示例和可选客户端标识。 */
    ApiFormat(String id, String label, String exampleEndpoint, String userAgent) {
        this.id = id;
        this.label = label;
        this.exampleEndpoint = exampleEndpoint;
        this.userAgent = userAgent;
    }

    /** 构建统一 POST 请求，确保连接检测与正式生成使用一致的请求头和传输协议。 */
    HttpRequest createHttpRequest(String endpoint, String apiKey, String body, Duration timeout) {
        URI uri = URI.create(endpoint);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json");
        if ("http".equalsIgnoreCase(uri.getScheme())) {
            builder.version(HttpClient.Version.HTTP_1_1); // 避免部分明文 HTTP 网关将 h2c 升级请求误判为非法方法。
        }
        if (userAgent != null) {
            builder.header("User-Agent", userAgent); // 兼容按客户端标识放行的 Responses 网关。
        }
        return builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
    }

    /** 返回稳定的持久化标识，避免显示名称变化影响旧配置。 */
    String id() {
        return id;
    }

    /** 返回所选协议的完整接口地址示例。 */
    String exampleEndpoint() {
        return exampleEndpoint;
    }

    /** 将旧版缺失的格式字段解析为 Chat Completions。 */
    static ApiFormat fromId(String id) {
        for (ApiFormat format : values()) {
            if (format.id.equals(id)) {
                return format;
            }
        }
        return OPENAI_CHAT_COMPLETIONS;
    }

    /** 在下拉框中显示用户可读的 API 格式名称。 */
    @Override
    public String toString() {
        return label;
    }

    /** 根据模型、提示词、温度和流式开关构造协议请求体。 */
    abstract String createRequest(String model, String prompt, double temperature, boolean stream);

    /** 从完整 JSON 响应中解析可展示的正文。 */
    abstract String responseText(JsonObject response);

    /** 将单个 JSON 流事件转换为增量、最终快照或完成信号。 */
    abstract StreamUpdate streamUpdate(JsonObject event);

    /** 旧版 Chat Completions 服务允许通过流结束关闭响应。 */
    boolean requiresCompletionEvent() {
        return false;
    }

    /** 解析并校验普通响应，供连接检测和不支持流式的服务共用。 */
    String readResponse(String body) {
        JsonObject response = parseObject(body); // 使用平台内置 Gson 正确处理 Unicode 和转义字符。
        checkError(response);
        return requireText(responseText(response));
    }

    /** 将 JSON 字符串解析为对象，并把格式异常转换成明确的用户提示。 */
    static JsonObject parseObject(String body) {
        try {
            JsonElement value = JsonParser.parseString(body);
            if (value.isJsonObject()) {
                return value.getAsJsonObject();
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException("AI 服务返回了无效的 JSON 响应，请检查接口地址和 API 格式。", exception);
        }
        throw new IllegalStateException("AI 服务返回的 JSON 不是对象，请检查 API 格式。");
    }

    /** 拒绝空白正文，避免连接检测误报成功或写入空提交信息。 */
    static String requireText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("AI 响应中没有找到提交信息，请检查 API 格式。");
        }
        return text.trim();
    }

    /** 识别普通响应和流事件中的服务错误。 */
    static void checkError(JsonObject value) {
        JsonElement error = value.get("error");
        if (error != null && !error.isJsonNull()) {
            String detail = error.isJsonObject() ? string(error.getAsJsonObject(), "message") : error.toString();
            throw new IllegalStateException("AI 服务错误：" + (detail.isBlank() ? error.toString() : detail));
        }
        if ("error".equals(string(value, "type"))) {
            throw new IllegalStateException("AI 服务错误：" + string(value, "message"));
        }
    }

    /** 校验 Responses 最终状态，同时保留截断原因。 */
    private static void checkResponseStatus(JsonObject response) {
        checkError(response);
        String status = string(response, "status");
        if (!status.isEmpty() && !"completed".equals(status)) {
            throw new IllegalStateException("AI 响应未完成：" + status + " "
                    + string(object(response, "incomplete_details"), "reason"));
        }
    }

    /** 读取首个聊天候选；usage 等事件没有候选时返回空对象。 */
    private static JsonObject firstChoice(JsonObject value) {
        JsonArray choices = array(value, "choices");
        return !choices.isEmpty() && choices.get(0).isJsonObject() ? choices.get(0).getAsJsonObject() : new JsonObject();
    }

    /** 防止被长度限制或内容过滤截断的聊天响应被误判为完整结果。 */
    private static void checkChatFinishReason(JsonObject choice) {
        String finishReason = string(choice, "finish_reason");
        if ("length".equals(finishReason) || "content_filter".equals(finishReason)) {
            throw new IllegalStateException("AI 响应未完成：" + finishReason);
        }
    }

    /** 读取聊天正文，兼容字符串和文本内容块，并报告拒绝响应。 */
    private static String chatText(JsonObject message) {
        String refusal = string(message, "refusal");
        if (!refusal.isEmpty()) {
            throw new IllegalStateException("AI 服务拒绝生成：" + refusal);
        }
        JsonElement content = message.get("content");
        if (content != null && content.isJsonPrimitive() && content.getAsJsonPrimitive().isString()) {
            return content.getAsString();
        }
        StringBuilder text = new StringBuilder();
        for (JsonElement part : array(message, "content")) {
            if (part.isJsonObject() && "text".equals(string(part.getAsJsonObject(), "type"))) {
                text.append(string(part.getAsJsonObject(), "text"));
            }
        }
        return text.toString();
    }

    /** 安全读取可选 JSON 对象字段。 */
    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    /** 安全读取可选 JSON 数组字段。 */
    private static JsonArray array(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    /** 安全读取可选 JSON 字符串字段，避免把 null 或对象当作正文。 */
    private static String string(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
    }

    /** 统一的流更新结果：delta 是新增正文，fullText 是最终快照，completed 表示结束。 */
    record StreamUpdate(String delta, String fullText, boolean completed) {
    }
}
