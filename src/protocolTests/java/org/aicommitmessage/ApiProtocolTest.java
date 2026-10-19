package org.aicommitmessage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 使用真实协议样例验证请求、普通响应和 SSE，独立运行且不需要 API 密钥。 */
public final class ApiProtocolTest {
    /** 执行协议回归用例；断言失败时通过非零退出码使构建失败。 */
    public static void main(String[] args) {
        defaultsAndRequests();
        plainResponses();
        streamingResponses();
        streamingChat();
        failures();
        System.out.println("API protocol regression tests passed.");
    }

    /** 验证旧配置回退、稳定格式标识以及不同协议的请求字段。 */
    private static void defaultsAndRequests() {
        CommitMessageSettings.State settings = new CommitMessageSettings.State();
        equal(ApiFormat.OPENAI_CHAT_COMPLETIONS, settings.getApiFormat());
        settings.apiFormat = null;
        equal(ApiFormat.OPENAI_CHAT_COMPLETIONS, settings.getApiFormat());
        settings.apiFormat = "openai-responses";
        equal(ApiFormat.OPENAI_RESPONSES, settings.getApiFormat());
        equal(ApiFormat.OPENAI_CHAT_COMPLETIONS, ApiFormat.fromId(null));
        equal(ApiFormat.OPENAI_CHAT_COMPLETIONS, ApiFormat.fromId(""));
        equal(ApiFormat.OPENAI_RESPONSES, ApiFormat.fromId("openai-responses"));
        String prompt = "修复\t路径 C:\\tmp\n\"引用\" 🐛";
        JsonObject chat = JsonParser.parseString(ApiFormat.OPENAI_CHAT_COMPLETIONS
                .createRequest("model", prompt, 0.2, true)).getAsJsonObject();
        equal(prompt, chat.getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString());
        equal(true, chat.get("stream").getAsBoolean());
        JsonObject responses = JsonParser.parseString(ApiFormat.OPENAI_RESPONSES
                .createRequest("model", prompt, 0.2, false)).getAsJsonObject();
        equal(prompt, responses.get("input").getAsString());
        equal(false, responses.get("stream").getAsBoolean());
        equal(false, responses.has("messages"));
        equal(false, responses.has("thinking"));
        equal(false, responses.has("temperature"));
        equal(false, responses.get("store").getAsBoolean());
    }

    /** 验证原生 output 数组解析，且推理和工具输出不会混入提交信息。 */
    private static void plainResponses() {
        String body = """
                {"status":"completed","output":[
                  {"type":"reasoning","summary":[{"type":"summary_text","text":"secret"}]},
                  {"type":"function_call","arguments":"{}"},
                  {"type":"message","role":"assistant","content":[
                    {"type":"output_text","text":"feat: \\u2728 新增"},
                    {"type":"output_text","text":"\\n\\n- 测试"}]}]}
                """;
        equal("feat: ✨ 新增\n\n- 测试", ApiFormat.OPENAI_RESPONSES.readResponse(body));
        equal("feat: ✨ 新增\n\n- 测试", read(ApiFormat.OPENAI_RESPONSES, body, new ArrayList<>()));
        equal("OK", ApiFormat.OPENAI_CHAT_COMPLETIONS.readResponse(
                "{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}"));
    }

    /** 验证文本增量、完成快照去重、多行 SSE 数据和完成后立即停止读取。 */
    private static void streamingResponses() {
        List<String> deltas = new ArrayList<>();
        String sse = """
                : keepalive

                event: response.created
                data: {"type":"response.created","response":{"output":[]}}

                data: {"type":"response.reasoning_summary_text.delta","delta":"secret"}

                event: response.output_text.delta
                data: {"type":"response.output_text.delta",
                data: "delta":"feat: "}

                data: {"type":"response.output_text.delta","delta":"新增"}

                data: {"type":"response.output_text.done","text":"feat: 新增"}

                data: {"type":"response.completed","response":{"status":"completed","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"feat: 新增"}]}]}}

                data: invalid-after-completion
                """;
        equal("feat: 新增", read(ApiFormat.OPENAI_RESPONSES, sse, deltas));
        equal(List.of("feat: ", "新增"), deltas);
        equal("OK", read(ApiFormat.OPENAI_RESPONSES,
                "data: {\"type\":\"response.completed\",\"response\":{\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"OK\"}]}]}}", new ArrayList<>()));
    }

    /** 验证旧协议的转义字符、角色事件、usage 事件和结束标记。 */
    private static void streamingChat() {
        List<String> deltas = new ArrayList<>();
        String sse = """
                data: {"choices":[{"delta":{"role":"assistant","content":null}}]}

                data: {"choices":[{"delta":{"content":"fix: \\u2728\\nC:\\\\tmp\\t\\\"ok\\\""}}]}

                data: {"choices":[],"usage":{"completion_tokens":2}}

                data: [DONE]

                """;
        equal("fix: ✨\nC:\\tmp\t\"ok\"", read(ApiFormat.OPENAI_CHAT_COMPLETIONS, sse, deltas));
        equal(1, deltas.size());
    }

    /** 验证 HTTP 200 内的错误、拒绝、截断、空响应及错误格式均不能视为成功。 */
    private static void failures() {
        for (String reason : List.of("length", "content_filter")) {
            fails(reason, () -> ApiFormat.OPENAI_CHAT_COMPLETIONS.readResponse(
                    "{\"choices\":[{\"finish_reason\":\"" + reason + "\",\"message\":{\"content\":\"partial\"}}]}"));
        }
        fails("quota", () -> ApiFormat.OPENAI_RESPONSES.readResponse("{\"error\":{\"message\":\"quota\"}}"));
        fails("max_output_tokens", () -> ApiFormat.OPENAI_RESPONSES.readResponse(
                "{\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"max_output_tokens\"}}"));
        fails("拒绝", () -> ApiFormat.OPENAI_RESPONSES.readResponse(
                "{\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"refusal\",\"refusal\":\"拒绝\"}]}]}"));
        fails("提交信息", () -> ApiFormat.OPENAI_RESPONSES.readResponse("{\"output\":[]}"));
        fails("提交信息", () -> ApiFormat.OPENAI_RESPONSES.readResponse("{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}"));
        fails("JSON", () -> ApiFormat.OPENAI_RESPONSES.readResponse("<html>OK</html>"));
        fails("quota", () -> read(ApiFormat.OPENAI_RESPONSES,
                "data: {\"type\":\"response.failed\",\"response\":{\"error\":{\"message\":\"quota\"}}}\n\n", new ArrayList<>()));
        fails("bad request", () -> read(ApiFormat.OPENAI_RESPONSES,
                "data: {\"type\":\"error\",\"message\":\"bad request\"}\n\n", new ArrayList<>()));
        fails("完成", () -> read(ApiFormat.OPENAI_RESPONSES,
                "data: {\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}\n\n", new ArrayList<>()));
        fails("完成", () -> read(ApiFormat.OPENAI_RESPONSES, "data: [DONE]\n\n", new ArrayList<>()));
        fails("提交信息", () -> read(ApiFormat.OPENAI_RESPONSES,
                "data: {\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}\n\n"
                        + "data: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\",\"output\":[]}}\n\n", new ArrayList<>()));
        fails("quota", () -> read(ApiFormat.OPENAI_CHAT_COMPLETIONS,
                "data: {\"error\":{\"message\":\"quota\"}}\n\n", new ArrayList<>()));
    }

    /** 将协议样例作为逐行响应读取，并收集实际推送给界面的增量。 */
    private static String read(ApiFormat format, String body, List<String> deltas) {
        return ApiResponseReader.read(format, body.lines(), deltas::add);
    }

    /** 比较预期和实际结果，提供清晰的失败原因。 */
    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    /** 确认操作失败并保留可供用户理解的错误原因。 */
    private static void fails(String expectedMessage, Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException exception) {
            if (!exception.getMessage().contains(expectedMessage)) {
                throw new AssertionError("Unexpected error: " + exception.getMessage(), exception);
            }
            return;
        }
        throw new AssertionError("Expected failure containing " + expectedMessage);
    }
}
