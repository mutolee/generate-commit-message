package org.aicommitmessage;

import com.intellij.openapi.options.Configurable;

import javax.swing.*;
import java.awt.*;

/**
 * 提供 AI 提交信息服务的设置页面。
 *
 * @author 杨林恩
 */
public final class CommitMessageConfigurable implements Configurable {
    private JPanel panel;
    private JTextField endpoint;
    private JTextField model;
    private JPasswordField apiKey;
    private JComboBox<String> language;

    /**
     * 创建设置页面组件。
     */
    @Override
    public JComponent createComponent() {
        if (panel != null) {
            return panel;
        }
        panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 4, 4, 4);
        c.weightx = 1;
        endpoint = new JTextField();
        model = new JTextField();
        apiKey = new JPasswordField();
        language = new JComboBox<>(new String[]{"English", "中文", "日本語"});
        add("API endpoint", endpoint, c, 0);
        add("Model", model, c, 1);
        add("API key", apiKey, c, 2);
        add("Message language", language, c, 3);
        return panel;
    }

    private void add(String label, JComponent field, GridBagConstraints c, int row) {
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        panel.add(new JLabel(label + ":"), c);
        c.gridx = 1;
        c.weightx = 1;
        panel.add(field, c);
    }

    /**
     * 判断设置是否已被修改。
     */
    @Override
    public boolean isModified() {
        CommitMessageSettings.State s = CommitMessageSettings.getInstance().getState();
        return !endpoint.getText().equals(s.endpoint) || !model.getText().equals(s.model) || !new String(apiKey.getPassword()).equals(s.apiKey) || !language.getSelectedItem().equals(s.language);
    }

    /**
     * 保存设置。
     */
    @Override
    public void apply() {
        CommitMessageSettings.State s = CommitMessageSettings.getInstance().getState();
        s.endpoint = endpoint.getText().trim();
        s.model = model.getText().trim();
        s.apiKey = new String(apiKey.getPassword()).trim();
        s.language = (String) language.getSelectedItem();
    }

    /**
     * 重置设置页面为已保存值。
     */
    @Override
    public void reset() {
        CommitMessageSettings.State s = CommitMessageSettings.getInstance().getState();
        endpoint.setText(s.endpoint);
        model.setText(s.model);
        apiKey.setText(s.apiKey);
        language.setSelectedItem(s.language);
    }

    /**
     * 返回设置页面名称。
     */
    @Override
    public String getDisplayName() {
        return "AI Commit Message";
    }
}
