package org.aicommitmessage;

import com.intellij.DynamicBundle;
import java.util.Locale;
import java.util.ResourceBundle;

/** 设置页使用 IDE 界面语言；不支持的语言明确回退英文，不采用操作系统语言。 */
final class SettingsMessages {
    private SettingsMessages() {}

    static String message(String key, Object... arguments) {
        return message(DynamicBundle.getLocale(), key, arguments);
    }

    static String message(Locale ideLocale, String key, Object... arguments) {
        Locale locale = switch (ideLocale.getLanguage()) {
            case "zh" -> Locale.CHINESE;
            case "ja" -> Locale.JAPANESE;
            default -> Locale.ROOT;
        };
        ResourceBundle bundle = ResourceBundle.getBundle("messages.SettingsBundle", locale,
                SettingsMessages.class.getClassLoader(),
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
        return String.format(locale, bundle.getString(key), arguments);
    }
}
