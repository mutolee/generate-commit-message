package org.aicommitmessage;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/** 将 Marketplace 更新说明转换为适合通知的简短纯文本。 */
final class UpdateNotesSummary {
    /** 提取最新版本的前两条说明，限制通知长度。 */
    static String summarize(String html) {
        if (html == null || html.isBlank()) return "";
        SummaryParser parser = new SummaryParser();
        try {
            new ParserDelegator().parse(new StringReader(html), parser, true); // 使用 HTML 解析器解码实体并丢弃标签。
            parser.finishLine(); // 收集没有块级结束标签的纯文本。
            return String.join("\n", parser.lines);
        } catch (IOException exception) {
            return ""; // 摘要不可用时不影响版本更新提醒。
        }
    }

    /** 收集首个版本章节的前两段可见内容，不保留链接、图片或脚本。 */
    private static final class SummaryParser extends HTMLEditorKit.ParserCallback {
        private final List<String> lines = new ArrayList<>();
        private final StringBuilder line = new StringBuilder();
        private boolean heading;
        private boolean stopped;
        private int hiddenDepth;

        /** 跳过标题及隐藏内容，在新的版本章节前停止提取。 */
        @Override
        public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) hiddenDepth++;
            if (isHeading(tag)) {
                finishLine(); // 保存标题前已收集的说明。
                if (!lines.isEmpty()) stopped = true; // 下一版本的说明不进入当前版本摘要。
                heading = true;
            } else if (isBlock(tag)) {
                finishLine(); // 块级标签开始新的一条说明。
            }
        }

        /** 按列表项或段落收集说明，并结束标题及隐藏区域。 */
        @Override
        public void handleEndTag(HTML.Tag tag, int position) {
            if (isBlock(tag)) finishLine(); // 内联强调标签不会打断同一条说明。
            if (isHeading(tag)) heading = false;
            if ((tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) && hiddenDepth > 0) hiddenDepth--;
        }

        /** 将显式换行视为一条说明的边界。 */
        @Override
        public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            if (tag == HTML.Tag.BR) finishLine();
        }

        /** 只接收可见正文，避免标题和脚本文本进入通知。 */
        @Override
        public void handleText(char[] text, int position) {
            if (!heading && !stopped && hiddenDepth == 0 && lines.size() < 2) line.append(text);
        }

        /** 压缩空白，并按 Unicode 码点限制每条说明为一百字。 */
        private void finishLine() {
            String text = line.toString().replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
            line.setLength(0);
            if (text.isEmpty() || stopped || lines.size() >= 2) return;
            if (text.codePointCount(0, text.length()) > 100) {
                text = text.substring(0, text.offsetByCodePoints(0, 99)) + "…"; // 不截断表情等补充字符。
            }
            lines.add(text);
        }

        /** 识别发布说明中的版本标题。 */
        private static boolean isHeading(HTML.Tag tag) {
            return tag == HTML.Tag.H1 || tag == HTML.Tag.H2 || tag == HTML.Tag.H3
                    || tag == HTML.Tag.H4 || tag == HTML.Tag.H5 || tag == HTML.Tag.H6;
        }

        /** 识别独立列表项和段落。 */
        private static boolean isBlock(HTML.Tag tag) {
            return tag == HTML.Tag.LI || tag == HTML.Tag.P || tag == HTML.Tag.DIV;
        }
    }
}
