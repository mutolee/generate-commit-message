package org.aicommitmessage;

import java.util.Locale;

/** 保留原有协议异常消息；设置页可通过 getLocalizedMessage 获取 IDE 语言的描述。 */
final class LocalizedApiException extends IllegalStateException {
    private final String key;
    private final String detail;

    LocalizedApiException(String key, String detail) {
        this(key, detail, null);
    }

    LocalizedApiException(String key, String detail, Throwable cause) {
        super(SettingsMessages.message(Locale.CHINESE, key, detail), cause);
        this.key = key;
        this.detail = detail;
    }

    @Override
    public String getLocalizedMessage() {
        return SettingsMessages.message(key, detail);
    }
}
