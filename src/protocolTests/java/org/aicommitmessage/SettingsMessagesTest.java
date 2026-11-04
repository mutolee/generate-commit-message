package org.aicommitmessage;

import java.util.Locale;
import java.util.ResourceBundle;

/** 验证 IDE 区域语言匹配与英文兜底，不需要启动 IDE。 */
public final class SettingsMessagesTest {
    public static void main(String[] args) {
        equal("生成提交信息", SettingsMessages.message(Locale.CHINESE, "action.generate"));
        equal("Generate Commit Message", SettingsMessages.message(Locale.ENGLISH, "action.generate"));
        equal("コミットメッセージを生成", SettingsMessages.message(Locale.JAPANESE, "action.generate"));
        equal("Generate Commit Message", SettingsMessages.message(Locale.FRENCH, "action.generate"));
        equal("Stop Generating Commit Message", SettingsMessages.message(Locale.ENGLISH, "action.stop"));
        equal("Stopping generation…", SettingsMessages.message(Locale.ENGLISH, "action.stopping"));
        equal("检测连接", SettingsMessages.message(Locale.SIMPLIFIED_CHINESE, "connection.test"));
        equal("检测连接", SettingsMessages.message(Locale.TRADITIONAL_CHINESE, "connection.test"));
        equal("接続テスト", SettingsMessages.message(Locale.JAPAN, "connection.test"));
        equal("Test Connection", SettingsMessages.message(Locale.US, "connection.test"));
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.CHINESE);
            for (Locale locale : new Locale[]{Locale.GERMAN, Locale.FRENCH, Locale.ROOT}) {
                equal("Test Connection", SettingsMessages.message(locale, "connection.test"));
            }
        } finally {
            Locale.setDefault(previous);
        }
        equal("Test succeeded (1234 ms)", SettingsMessages.message(Locale.ENGLISH, "connection.success", 1234));
        var english = ResourceBundle.getBundle("messages.SettingsBundle", Locale.ROOT);
        for (String suffix : new String[]{"zh", "ja"}) {
            var translated = ResourceBundle.getBundle("messages.SettingsBundle_" + suffix, Locale.ROOT);
            equal(english.keySet(), translated.keySet());
            for (String key : english.keySet()) {
                if (translated.getString(key).isBlank()) throw new AssertionError("Empty translation: " + key);
            }
        }
        System.out.println("Settings localization tests passed.");
    }

    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
