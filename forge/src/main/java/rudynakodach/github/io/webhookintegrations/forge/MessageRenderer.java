package rudynakodach.github.io.webhookintegrations.forge;

import com.google.gson.*;
import java.util.Map;
import java.util.regex.Pattern;

public final class MessageRenderer {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$([A-Za-z][A-Za-z0-9]*)\\$");
    private MessageRenderer() {}

    /** Replace only original string values: player input cannot inject JSON or expand more placeholders. */
    public static JsonElement render(JsonElement template, Map<String, String> values) {
        if (template.isJsonObject()) {
            JsonObject result = new JsonObject();
            template.getAsJsonObject().entrySet().forEach(e -> result.add(e.getKey(), render(e.getValue(), values)));
            return result;
        }
        if (template.isJsonArray()) {
            JsonArray result = new JsonArray();
            template.getAsJsonArray().forEach(e -> result.add(render(e, values)));
            return result;
        }
        if (template.isJsonPrimitive() && template.getAsJsonPrimitive().isString()) {
            var matcher = PLACEHOLDER.matcher(template.getAsString());
            return new JsonPrimitive(matcher.replaceAll(m -> java.util.regex.Matcher.quoteReplacement(values.getOrDefault(m.group(1), m.group()))));
        }
        return template.deepCopy();
    }

    public static String payload(JsonObject template, Map<String, String> values) {
        JsonObject result = render(template, values).getAsJsonObject();
        // Never turn untrusted chat content into Discord user/role/everyone notifications.
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray());
        result.add("allowed_mentions", mentions);
        return result.toString();
    }
}
