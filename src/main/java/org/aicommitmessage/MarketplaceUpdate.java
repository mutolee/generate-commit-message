package org.aicommitmessage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.util.text.VersionComparatorUtil;

/** Marketplace 兼容更新响应，保留版本、插件编号和更新详情编号。 */
record MarketplaceUpdate(String version, long pluginId, long updateId) {
    static final String PLUGIN_ID = "org.aicommitmessage";
    /** 构造仅含插件标识与 IDE 构建号的查询。 */
    static String requestBody(String build) {
        JsonObject body = new JsonObject();
        body.addProperty("build", build); // 服务端按产品和构建号筛选兼容更新。
        JsonArray ids = new JsonArray();
        ids.add(PLUGIN_ID); // 不传递项目路径、代码或 AI 服务配置。
        body.add("pluginXMLIds", ids);
        return body.toString();
    }
    /** 从兼容更新结果中选择最新版本，空结果表示暂无兼容版本。 */
    static MarketplaceUpdate parse(String json) {
        try {
            JsonElement root = JsonParser.parseString(json); // 解析兼容查询的数组响应。
            if (!root.isJsonArray()) throw new IllegalArgumentException("Expected update array");
            MarketplaceUpdate latest = null;
            for (JsonElement element : root.getAsJsonArray()) {
                JsonObject item = element.getAsJsonObject(); // 严格检查身份，避免错误响应指向其他插件。
                if (!PLUGIN_ID.equals(item.get("pluginXmlId").getAsString())) {
                    throw new IllegalArgumentException("Unexpected plugin ID");
                }
                String version = item.get("version").getAsString().trim();
                long id = item.get("pluginId").getAsLong();
                if (version.isEmpty() || id <= 0) throw new IllegalArgumentException("Invalid update");
                long updateId = item.has("id") && !item.get("id").isJsonNull() ? item.get("id").getAsLong() : 0;
                MarketplaceUpdate candidate = new MarketplaceUpdate(version, id, updateId);
                if (latest == null || candidate.isNewerThan(latest.version())) latest = candidate;
            }
            return latest;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Marketplace update response", exception);
        }
    }
    /** 校验详情属于选中的版本后，提取对应发布说明的简短摘要。 */
    String parseNotes(String json) {
        try {
            JsonObject details = JsonParser.parseString(json).getAsJsonObject(); // 解析指定更新 ID 的详情。
            if (details.get("id").getAsLong() != updateId
                    || details.get("pluginId").getAsLong() != pluginId
                    || !version.equals(details.get("version").getAsString())) {
                throw new IllegalArgumentException("Unexpected update details");
            }
            JsonElement notes = details.get("notes");
            return notes == null || notes.isJsonNull() ? "" : UpdateNotesSummary.summarize(notes.getAsString());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid update details", exception);
        }
    }

    /** 使用平台版本规则判断是否高于已安装版本。 */
    boolean isNewerThan(String installed) {
        return VersionComparatorUtil.compare(version, installed) > 0; // 按数字段比较 1.0.9 与 1.0.10。
    }
}
