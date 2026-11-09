package org.aicommitmessage;

import com.google.gson.JsonParser;
import com.intellij.util.xmlb.XmlSerializer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** 验证每日节流、并发去重、忽略恢复以及真实 Marketplace 响应结构。 */
public final class UpdateCheckTest {
    /** 执行不需要网络或 IDE 的回归测试。 */
    public static void main(String[] args) throws Exception {
        long now = 1_800_000_000_000L;
        UpdateCheckPolicy policy = new UpdateCheckPolicy();
        check(policy.begin(false, now), "首次自动检查必须执行");
        check(!policy.begin(false, now), "多个项目不能同时请求");
        check(!policy.begin(true, now), "手动检查不能重复正在进行的请求");
        policy.finish(); // 失败同样消耗当日资格，避免离线时不断重试。
        check(!policy.begin(false, now + 86_399_999), "不足 24 小时不能自动重试");
        check(policy.begin(true, now + 1), "手动检查绕过每日限制");
        policy.finish();
        UpdateCheckPolicy restored = new UpdateCheckPolicy();
        restored.restore(policy.snapshot()); // 模拟重启后恢复检查记录。
        check(!restored.begin(false, now + 100), "重启不能重置当日检查次数");
        check(restored.begin(false, now + 86_400_000), "满 24 小时可再次检查");
        restored.finish();
        check(restored.begin(false, now - 100), "系统时间回拨后不永久禁用检查");
        restored.finish();
        check(policy.claimNotification("1.0.11", false), "首次发现新版本需要提醒");
        check(!policy.claimNotification("1.0.11", false), "多项目不能重复提示同一版本");
        policy.ignore("1.0.12");
        // 经过平台真实 XML 序列化再恢复，验证忽略记录可以跨重启保存。
        restored.restore(XmlSerializer.deserialize(XmlSerializer.serialize(policy.snapshot()), UpdateCheckPolicy.State.class));
        check(!restored.claimNotification("1.0.12", false), "忽略版本必须在重启后生效");
        check(restored.claimNotification("1.0.12", true), "手动检查仍展示已忽略版本");
        check(restored.claimNotification("1.0.13", false), "忽略不影响后续新版本");
        UpdateCheckPolicy concurrent = new UpdateCheckPolicy();
        AtomicInteger accepted = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        Thread[] threads = new Thread[12];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try { start.await(); } catch (InterruptedException e) { throw new AssertionError(e); }
                if (concurrent.begin(false, now)) accepted.incrementAndGet();
            });
            threads[i].start();
        }
        start.countDown();
        for (Thread thread : threads) thread.join();
        check(accepted.get() == 1, "并发打开项目只能发起一次请求");
        var body = JsonParser.parseString(MarketplaceUpdate.requestBody("IC-243.21565.193")).getAsJsonObject();
        check(body.get("build").getAsString().equals("IC-243.21565.193"), "请求必须带 IDE 兼容构建号");
        check(body.getAsJsonArray("pluginXMLIds").get(0).getAsString().equals("org.aicommitmessage"), "请求必须限定本插件");
        MarketplaceUpdate update = MarketplaceUpdate.parse("""
                [{"id":1212775,"pluginId":34183,"version":"1.0.10","pluginXmlId":"org.aicommitmessage"},
                 {"pluginId":34183,"version":"1.0.9","pluginXmlId":"org.aicommitmessage"}]
                """);
        check(update.version().equals("1.0.10") && update.pluginId() == 34183, "版本比较不能使用字典顺序");
        check(update.isNewerThan("1.0.9"), "正确识别新版本");
        check(!update.isNewerThan("1.0.10") && !update.isNewerThan("1.0.11"), "相同或旧版本不提示更新");
        check(MarketplaceUpdate.parse("[]") == null, "无兼容更新属于正常结果");
        for (String invalid : new String[]{"{}", "null", "<html>error</html>",
                "[{\"pluginXmlId\":\"other\",\"version\":\"9\",\"pluginId\":1}]",
                "[{\"pluginXmlId\":\"org.aicommitmessage\",\"version\":\"\",\"pluginId\":34183}]"}) {
            try {
                MarketplaceUpdate.parse(invalid);
                throw new AssertionError("损坏响应不能被解释为没有更新: " + invalid);
            } catch (IllegalArgumentException expected) { /* 预期拒绝无效数据。 */ }
        }
        check(UpdateNotesSummary.summarize("""
                <h2>1.0.11</h2><ul><li>支持<b>更新摘要</b> &amp; 设置跳转。</li>
                <li>修复通知显示。</li><li>第三条不展示。</li></ul>
                <h2>1.0.10</h2><ul><li>旧版功能不展示。</li></ul>
                """).equals("支持更新摘要 & 设置跳转。\n修复通知显示。"), "摘要只展示最新版本前两条，解码 HTML 实体");
        check(UpdateNotesSummary.summarize("<p>修复问题。</p><p>改善体验。</p>")
                .equals("修复问题。\n改善体验。"), "支持段落形式的更新说明");
        check(UpdateNotesSummary.summarize("<h2>1.0.11</h2><p>本次修复。</p><h2>1.0.10</h2><p>旧版功能。</p>")
                .equals("本次修复。"), "不足两条时也不混入历史版本内容");
        check(UpdateNotesSummary.summarize("<p>查看<a href='https://example.com'>详情</a><img src='https://example.com/image'></p>")
                .equals("查看详情"), "摘要不保留远端链接或图片标签");
        check(UpdateNotesSummary.summarize("普通文本更新说明").equals("普通文本更新说明"), "支持纯文本说明");
        check(UpdateNotesSummary.summarize(null).isEmpty()
                && UpdateNotesSummary.summarize("<h2>1.0.11</h2><p>&nbsp;</p>").isEmpty(), "缺失或空白说明不展示摘要");
        String shortened = UpdateNotesSummary.summarize("更新😀".repeat(100));
        check(shortened.codePointCount(0, shortened.length()) <= 100 && shortened.endsWith("…"), "长说明截断且不切断 Unicode 字符");
        String details = """
                {"id":1212775,"pluginId":34183,"version":"1.0.10","notes":"<h2>1.0.10</h2><ul><li>改进语言支持。</li></ul>"}
                """;
        check(update.parseNotes(details).equals("改进语言支持。"), "从选中版本的详情读取 notes 字段");
        check(update.parseNotes("{\"id\":1212775,\"pluginId\":34183,\"version\":\"1.0.10\",\"notes\":null}").isEmpty(),
                "没有发布说明时返回空摘要");
        for (String wrongDetails : new String[]{details.replace("1212775", "1212776"),
                details.replace("34183", "34184"), details.replace("1.0.10", "1.0.9"), "{}"}) {
            try {
                update.parseNotes(wrongDetails);
                throw new AssertionError("不能展示不匹配的更新详情");
            } catch (IllegalArgumentException expected) { /* 预期忽略错误的更新说明。 */ }
        }
        System.out.println("Update check regression tests passed.");
    }

    /** 断言用户可观察的行为，失败时使测试任务失败。 */
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
}
