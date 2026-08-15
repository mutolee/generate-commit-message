package org.aicommitmessage;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/**
 * 提供 AI Commit Message服务的设置页面，并允许用户定制提交信息提示词。
 *
 * @author 杨林恩
 */
public final class CommitMessageConfigurable implements Configurable {
    private JPanel panel;
    private JBTextField endpoint;
    private JBTextField model;
    private JBPasswordField apiKey;
    private JComboBox<String> language;
    private JBTextArea promptTemplate;
    private JPanel serviceForm;
    private JButton testConnectionButton;
    private JBLabel connectionStatus;
    private JBTextArea connectionFailureDetail;
    private JBScrollPane connectionFailureContainer;

    private static final Color SUCCESS_COLOR = new JBColor(0x218739, 0x62C370);
    private static final Color FAILURE_COLOR = new JBColor(0xC42B1C, 0xFF6B68);

    /**
     * 创建包含服务连接和提示词模板配置的设置页面。
     *
     * @return 设置页面根组件
     */
    @Override
    public JComponent createComponent() {
        if (panel != null) {
            return panel;
        }

        // 使用 IntelliJ 原生组件和表单构建器，使控件间距、字体及主题保持一致。
        endpoint = new JBTextField();
        model = new JBTextField();
        apiKey = new JBPasswordField();
        language = new JComboBox<>(new String[]{"中文", "英文", "日文"});
        promptTemplate = new JBTextArea(9, 0);
        promptTemplate.setColumns(0);
        promptTemplate.setLineWrap(true);
        promptTemplate.setWrapStyleWord(true);
        promptTemplate.setMargin(JBUI.insets(8, 10));

        testConnectionButton = new JButton("检测连接");
        // 使用 JLabel 让单行检测结果沿按钮基线自然居中；失败详情通过 HTML 宽度自动换行。
        connectionStatus = new JBLabel("尚未检测");
        connectionStatus.setMinimumSize(new Dimension(0, JBUI.scale(24)));
        // 不限制高度，让 HTML 换行后的多行异常完整显示，避免上下内容被裁切。
        connectionStatus.setMaximumSize(new Dimension(JBUI.scale(420), Integer.MAX_VALUE));
        connectionStatus.setForeground(UIUtil.getContextHelpForeground());
        connectionStatus.setAlignmentY(Component.CENTER_ALIGNMENT);
        testConnectionButton.setAlignmentY(Component.CENTER_ALIGNMENT);
        connectionFailureDetail = new JBTextArea(3, 0);
        connectionFailureDetail.setEditable(false);
        connectionFailureDetail.setFocusable(false);
        connectionFailureDetail.setColumns(0);
        connectionFailureDetail.setLineWrap(true);
        // 对 URL、JSON 等没有空格的异常内容也按字符断行，避免撑宽页面。
        connectionFailureDetail.setWrapStyleWord(false);
        connectionFailureDetail.setOpaque(false);
        connectionFailureDetail.setBorder(null);
        connectionFailureDetail.setMargin(JBUI.insets(0));
        connectionFailureDetail.setAutoscrolls(false);
        connectionFailureDetail.setMinimumSize(new Dimension(0, 0));
        connectionFailureDetail.setForeground(FAILURE_COLOR);
        connectionFailureDetail.setAlignmentX(Component.LEFT_ALIGNMENT);
        connectionFailureDetail.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        // 与提示词文本框一样交给 Viewport 管理宽度，禁止异常内容撑宽外层设置页面。
        connectionFailureContainer = new JBScrollPane(connectionFailureDetail);
        connectionFailureContainer.setBorder(null);
        connectionFailureContainer.setViewportBorder(null);
        connectionFailureContainer.setOpaque(false);
        connectionFailureContainer.getViewport().setOpaque(false);
        connectionFailureContainer.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        connectionFailureContainer.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        connectionFailureContainer.setPreferredSize(new Dimension(0, JBUI.scale(54)));
        connectionFailureContainer.setMinimumSize(new Dimension(0, JBUI.scale(54)));
        connectionFailureContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, JBUI.scale(54)));
        connectionFailureContainer.setVisible(false);

        JPanel connectionTopRow = new JPanel();
        connectionTopRow.setLayout(new BoxLayout(connectionTopRow, BoxLayout.X_AXIS));
        connectionTopRow.setBorder(JBUI.Borders.empty());
        connectionTopRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        connectionTopRow.setMinimumSize(new Dimension(0, connectionTopRow.getPreferredSize().height));
        connectionTopRow.add(testConnectionButton);
        connectionTopRow.add(Box.createHorizontalStrut(JBUI.scale(10)));
        connectionTopRow.add(connectionStatus);

        JPanel connectionTestPanel = new JPanel();
        connectionTestPanel.setLayout(new BoxLayout(connectionTestPanel, BoxLayout.Y_AXIS));
        connectionTestPanel.setBorder(JBUI.Borders.empty());
        connectionTestPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        connectionTestPanel.setMinimumSize(new Dimension(0, connectionTestPanel.getPreferredSize().height));
        connectionTestPanel.add(connectionTopRow);
        testConnectionButton.addActionListener(event -> testConnection());

        serviceForm = new JPanel(new GridBagLayout());
        addFormRow(serviceForm, "接口地址：", endpoint, 0);
        addFormRow(serviceForm, "模型：", model, 1);
        addFormRow(serviceForm, "API 密钥：", apiKey, 2);
        addFormRow(serviceForm, "提交信息语言：", language, 3);
        addFormRow(serviceForm, "连接检测：", connectionTestPanel, 4);
        addDetailRow(serviceForm, connectionFailureContainer, 5);
        serviceForm.setAlignmentX(Component.LEFT_ALIGNMENT);
        serviceForm.setMinimumSize(new Dimension(0, 0));
        serviceForm.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JBScrollPane promptScrollPane = new JBScrollPane(promptTemplate);
        promptScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        promptScrollPane.setPreferredSize(new Dimension(0, JBUI.scale(190)));
        promptScrollPane.setMinimumSize(new Dimension(0, JBUI.scale(150)));
        promptScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, JBUI.scale(240)));
        promptScrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton restorePrompt = new JButton("恢复默认提示词");
        // 仅恢复编辑框中的默认值，用户仍需点击 Apply 才会保存。
        restorePrompt.addActionListener(event -> promptTemplate.setText(CommitMessageSettings.DEFAULT_PROMPT_TEMPLATE));

        JPanel promptActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        promptActions.add(restorePrompt);
        promptActions.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setMinimumSize(new Dimension(0, 0));
        content.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        content.add(createPageHeader());
        content.add(Box.createVerticalStrut(JBUI.scale(22)));
        content.add(createSectionTitle("AI 服务"));
        content.add(Box.createVerticalStrut(JBUI.scale(8)));
        content.add(serviceForm);
        content.add(Box.createVerticalStrut(JBUI.scale(20)));
        content.add(createSectionTitle("提示词模板"));
        content.add(Box.createVerticalStrut(JBUI.scale(6)));
        content.add(createHint("变量：{{language}}（语言），{{diff}}（Git 差异）。省略差异变量时会自动追加。"));
        content.add(Box.createVerticalStrut(JBUI.scale(8)));
        content.add(promptScrollPane);
        content.add(Box.createVerticalStrut(JBUI.scale(8)));
        content.add(promptActions);

        panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(12, 16));
        panel.setMinimumSize(new Dimension(0, 0));
        // IntelliJ 设置窗口已经提供外层滚动容器，这里不再嵌套页面级滚动条。
        panel.add(content, BorderLayout.NORTH);
        reset();
        return panel;
    }

    /**
     * 创建配置页面的品牌标题和功能简介。
     *
     * @return 配置页面头部组件
     */
    private static JComponent createPageHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        JBLabel title = new JBLabel("AI Commit Message");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + JBUI.scale(6)));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(JBUI.scale(5)));
        header.add(createHint("连接兼容 OpenAI 的服务，根据代码变更生成清晰、规范的提交信息。"));
        return header;
    }

    /**
     * 向服务配置表单添加一行可随窗口宽度伸缩的输入组件。
     *
     * @param form      表单容器
     * @param labelText 字段名称
     * @param field     输入或操作组件
     * @param row       表单行号
     */
    private static void addFormRow(JPanel form, String labelText, JComponent field, int row) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.LINE_END;
        labelConstraints.insets = JBUI.insets(0, 0, 10, 12);
        form.add(new JBLabel(labelText), labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.anchor = GridBagConstraints.LINE_START;
        fieldConstraints.insets = JBUI.insetsBottom(10);
        field.setMinimumSize(new Dimension(0, field.getPreferredSize().height));
        form.add(field, fieldConstraints);
    }

    /**
     * 添加不带标签、但与表单输入框左边缘对齐的详情行。
     *
     * @param form  表单容器
     * @param detail 详情组件
     * @param row   表单行号
     */
    private static void addDetailRow(JPanel form, JComponent detail, int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 1;
        constraints.gridy = row;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.LINE_START;
        constraints.insets = JBUI.insetsBottom(10);
        form.add(detail, constraints);
    }

    /**
     * 创建设置分区标题。
     *
     * @param text 标题文本
     * @return 左对齐的分区标题组件
     */
    private static JComponent createSectionTitle(String text) {
        JBLabel label = new JBLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() + JBUI.scale(2)));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /**
     * 使用页面中尚未保存的接口地址、模型和密钥发送最小请求以检测服务连接。
     */
    private void testConnection() {
        String configuredEndpoint = endpoint.getText().trim();
        String configuredModel = model.getText().trim();
        String configuredApiKey = new String(apiKey.getPassword()).trim();
        if (configuredEndpoint.isEmpty() || configuredModel.isEmpty() || configuredApiKey.isEmpty()) {
            showConnectionFailure("请完整填写接口地址、模型和 API 密钥。", 0);
            return;
        }

        testConnectionButton.setEnabled(false);
        connectionStatus.setText("检测中…");
        connectionStatus.setForeground(UIUtil.getContextHelpForeground());
        connectionStatus.setToolTipText(null);
        connectionFailureContainer.setVisible(false);
        long startNanos = System.nanoTime();
        try {
            String body = "{\"model\":\"" + json(configuredModel)
                    + "\",\"messages\":[{\"role\":\"user\",\"content\":\"仅回复 OK\"}],"
                    + "\"temperature\":0,\"thinking\":{\"type\":\"disabled\"},\"stream\":false}";
            HttpRequest request = HttpRequest.newBuilder(URI.create(configuredEndpoint))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + configuredApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            // 异步检测连接，避免网络请求阻塞 IntelliJ 设置窗口。
            HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .whenComplete((response, error) -> SwingUtilities.invokeLater(() -> {
                        testConnectionButton.setEnabled(true);
                        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
                        if (error != null) {
                            Throwable cause = error.getCause() == null ? error : error.getCause();
                            showConnectionFailure(StringUtil.notNullize(cause.getMessage(), cause.toString()), elapsedMillis);
                        } else if (response.statusCode() / 100 != 2) {
                            String responseBody = response.body().length() > 500
                                    ? response.body().substring(0, 500) + "…"
                                    : response.body();
                            showConnectionFailure("AI 服务返回 HTTP " + response.statusCode() + "：" + responseBody, elapsedMillis);
                        } else {
                            connectionStatus.setText("检测成功（" + elapsedMillis + " ms）");
                            connectionStatus.setForeground(SUCCESS_COLOR);
                            connectionStatus.setToolTipText(null);
                            connectionFailureContainer.setVisible(false);
                            serviceForm.revalidate();
                        }
                    }));
        } catch (Exception exception) {
            testConnectionButton.setEnabled(true);
            showConnectionFailure(Objects.toString(exception.getMessage(), "未知配置异常"),
                    (System.nanoTime() - startNanos) / 1_000_000);
        }
    }

    /**
     * 在配置页面标记连接检测失败并展示具体异常。
     *
     * @param message       失败原因
     * @param elapsedMillis 检测耗时（毫秒）
     */
    private void showConnectionFailure(String message, long elapsedMillis) {
        String detail = StringUtil.notNullize(message, "未知异常");
        if (detail.isBlank()) {
            detail = "未知异常";
        }
        String inlineDetail = detail.length() > 500 ? detail.substring(0, 500) + "…" : detail;
        connectionStatus.setText("");
        connectionFailureDetail.setText("检测失败（" + elapsedMillis + " ms）：" + inlineDetail);
        connectionFailureDetail.setCaretPosition(0);
        connectionFailureContainer.setVisible(true);
        serviceForm.revalidate();
    }

    /**
     * 将字符串转义为 JSON 字符串内容。
     *
     * @param value 原始字符串
     * @return 完成 JSON 转义的字符串
     */
    private static String json(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    /**
     * 创建支持 IntelliJ 主题颜色的辅助说明。
     *
     * @param text 提示内容
     * @return 左对齐的辅助说明组件
     */
    private static JComponent createHint(String text) {
        JBLabel label = new JBLabel(text);
        label.setForeground(UIUtil.getContextHelpForeground());
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /**
     * 判断页面中的服务配置或提示词模板是否已被修改。
     *
     * @return 任一配置发生变化时返回 {@code true}
     */
    @Override
    public boolean isModified() {
        CommitMessageSettings.State state = CommitMessageSettings.getInstance().getState();
        return !endpoint.getText().equals(state.endpoint)
                || !model.getText().equals(state.model)
                || !new String(apiKey.getPassword()).equals(state.apiKey)
                || !Objects.equals(language.getSelectedItem(), toChineseLanguageLabel(state.language))
                || !promptTemplate.getText().equals(state.getPromptTemplate());
    }

    /**
     * 保存服务连接、消息语言和提示词模板配置。
     */
    @Override
    public void apply() {
        CommitMessageSettings.State state = CommitMessageSettings.getInstance().getState();
        state.endpoint = endpoint.getText().trim();
        state.model = model.getText().trim();
        state.apiKey = new String(apiKey.getPassword()).trim();
        state.language = (String) language.getSelectedItem();
        String configuredPrompt = promptTemplate.getText().trim();
        state.promptTemplate = configuredPrompt.isEmpty()
                ? CommitMessageSettings.DEFAULT_PROMPT_TEMPLATE
                : configuredPrompt;
        promptTemplate.setText(state.promptTemplate);
    }

    /**
     * 将设置页面恢复为当前已经保存的配置值。
     */
    @Override
    public void reset() {
        CommitMessageSettings.State state = CommitMessageSettings.getInstance().getState();
        endpoint.setText(state.endpoint);
        model.setText(state.model);
        apiKey.setText(state.apiKey);
        language.setSelectedItem(toChineseLanguageLabel(state.language));
        promptTemplate.setText(state.getPromptTemplate());
        promptTemplate.setCaretPosition(0);
    }

    /**
     * 返回设置页面在 IntelliJ 设置树中显示的名称。
     *
     * @return 设置页面名称
     */
    @Override
    public String getDisplayName() {
        return "AI Commit Message";
    }

    /**
     * 将旧版本保存的语言名称转换为中文显示名称。
     *
     * @param savedLanguage 已保存的语言名称
     * @return 用于配置页面显示的中文语言名称
     */
    private static String toChineseLanguageLabel(String savedLanguage) {
        if ("English".equals(savedLanguage)) {
            return "英文";
        }
        if ("日本語".equals(savedLanguage)) {
            return "日文";
        }
        return savedLanguage == null || savedLanguage.isBlank() ? "中文" : savedLanguage;
    }
}
