package org.aicommitmessage;

import com.google.gson.JsonObject;

import java.util.Iterator;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** 读取普通 JSON 或 SSE 响应；协议负责内容解析，本类负责事件分帧与正文累积。 */
final class ApiResponseReader {
    private final ApiFormat format;
    private final Consumer<String> onDelta;
    private final StringBuilder message = new StringBuilder();
    private boolean completed;

    /** 保存本次响应使用的协议和界面增量回调。 */
    private ApiResponseReader(ApiFormat format, Consumer<String> onDelta) {
        this.format = format;
        this.onDelta = onDelta;
    }

    /** 从逐行响应中读取完整正文；调用方负责关闭响应流。 */
    static String read(ApiFormat format, Stream<String> lines, Consumer<String> onDelta) {
        ApiResponseReader reader = new ApiResponseReader(format, onDelta);
        Iterator<String> iterator = lines.iterator();
        String first = "";
        while (iterator.hasNext() && first.isBlank()) {
            first = iterator.next(); // 跳过响应开头的空行。
        }
        if (first.stripLeading().startsWith("{")) {
            StringBuilder body = new StringBuilder(first);
            iterator.forEachRemaining(line -> body.append('\n').append(line));
            String text = format.readResponse(body.toString()); // 兼容忽略 stream 参数的服务。
            onDelta.accept(text);
            return text;
        }

        StringBuilder eventData = new StringBuilder();
        reader.line(first, eventData);
        while (!reader.completed && iterator.hasNext()) {
            reader.line(iterator.next(), eventData); // 按空行分帧，支持一帧中的多个 data 行。
        }
        if (!reader.completed && !eventData.isEmpty()) {
            reader.event(eventData.toString()); // 处理末尾未带空行的最后一帧。
        }
        if (format.requiresCompletionEvent() && !reader.completed) {
            throw new IllegalStateException("AI 响应流在完成前中断，请重试。");
        }
        return ApiFormat.requireText(reader.message.toString());
    }

    /** 处理 SSE 数据行，忽略注释、事件名和重连字段。 */
    private void line(String line, StringBuilder eventData) {
        if (line.isEmpty()) {
            if (!eventData.isEmpty()) {
                event(eventData.toString()); // 仅在完整事件到达时解析 JSON。
                eventData.setLength(0);
            }
        } else if (line.startsWith("data:")) {
            String data = line.substring(5);
            if (data.startsWith(" ")) {
                data = data.substring(1); // SSE 规范仅移除冒号后的一个可选空格。
            }
            if (!eventData.isEmpty()) {
                eventData.append('\n');
            }
            eventData.append(data);
        }
    }

    /** 将完整事件交给协议解析器，并防止完成快照重复写入。 */
    private void event(String data) {
        if (data.isBlank()) {
            return;
        }
        if ("[DONE]".equals(data.trim())) {
            if (format.requiresCompletionEvent()) {
                throw new IllegalStateException("AI 响应流缺少完成事件，请检查 API 格式。");
            }
            completed = true;
            return;
        }
        JsonObject event = ApiFormat.parseObject(data);
        ApiFormat.checkError(event); // 即使 HTTP 为 200，也要传播流内错误。
        ApiFormat.StreamUpdate update = format.streamUpdate(event);
        append(update.delta());
        if (update.fullText() != null) {
            String fullText = update.fullText();
            if (fullText.startsWith(message.toString())) {
                append(fullText.substring(message.length())); // 只补齐尚未收到的正文，不重复输出。
            } else if (!fullText.isBlank()) {
                message.setLength(0);
                message.append(fullText); // 完成结果由调用方最终写回提交框。
            }
        }
        completed = update.completed();
    }

    /** 累积非空文本并通知界面展示增量。 */
    private void append(String delta) {
        if (!delta.isEmpty()) {
            message.append(delta);
            onDelta.accept(delta);
        }
    }
}
