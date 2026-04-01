package rudynakodach.github.io.webhookintegrations.Clans;

import io.github.maste.customclans.api.model.BannerPatternSnapshot;
import io.github.maste.customclans.api.model.ClanBannerSnapshot;
import io.github.maste.customclans.api.model.ClanMemberSnapshot;
import io.github.maste.customclans.api.model.ClanSnapshot;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LightweightClansPayloadMapper {

    public WebhookPayload createClanPayload(
            String eventName,
            Instant occurredAt,
            ClanSnapshot clanSnapshot,
            Collection<String> changedFields,
            ClansWebhookConfig config
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", eventName);
        payload.put("occurredAt", occurredAt.toString());

        if (changedFields != null && !changedFields.isEmpty()) {
            payload.put("changedFields", changedFields.stream().sorted().toList());
        }

        payload.put("clan", toClanObject(clanSnapshot, config));

        return new WebhookPayload(
                eventName,
                occurredAt.toString(),
                JsonSerializer.toJson(payload),
                clanSnapshot.id(),
                clanSnapshot.name()
        );
    }

    public WebhookPayload createDeletePayload(String eventName, Instant occurredAt, ClanSnapshot clanSnapshot) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", eventName);
        payload.put("occurredAt", occurredAt.toString());

        LinkedHashMap<String, Object> clan = new LinkedHashMap<>();
        clan.put("id", clanSnapshot.id());
        clan.put("name", clanSnapshot.name());
        clan.put("normalizedName", clanSnapshot.normalizedName());

        payload.put("clan", clan);

        return new WebhookPayload(
                eventName,
                occurredAt.toString(),
                JsonSerializer.toJson(payload),
                clanSnapshot.id(),
                clanSnapshot.name()
        );
    }

    private Map<String, Object> toClanObject(ClanSnapshot clanSnapshot, ClansWebhookConfig config) {
        LinkedHashMap<String, Object> clan = new LinkedHashMap<>();
        clan.put("id", clanSnapshot.id());
        clan.put("name", clanSnapshot.name());
        clan.put("normalizedName", clanSnapshot.normalizedName());
        clan.put("tag", clanSnapshot.tag());
        clan.put("tagColor", clanSnapshot.tagColor());
        clan.put("description", clanSnapshot.description());
        clan.put("presidentUuid", clanSnapshot.presidentUuid().toString());
        clan.put("presidentName", clanSnapshot.presidentName());
        clan.put("memberCount", clanSnapshot.memberCount());

        if (config.includeMembers()) {
            clan.put("members", clanSnapshot.members().stream().map(this::toMemberObject).toList());
        }

        if (config.includeBanner()) {
            clan.put("banner", toBannerObject(clanSnapshot.banner()));
        }

        clan.put("createdAt", clanSnapshot.createdAt().toString());
        clan.put("updatedAt", clanSnapshot.updatedAt() == null ? null : clanSnapshot.updatedAt().toString());
        return clan;
    }

    private Map<String, Object> toMemberObject(ClanMemberSnapshot memberSnapshot) {
        LinkedHashMap<String, Object> member = new LinkedHashMap<>();
        member.put("playerUuid", memberSnapshot.playerUuid().toString());
        member.put("lastKnownName", memberSnapshot.lastKnownName());
        member.put("role", memberSnapshot.role().name());
        member.put("joinedAt", memberSnapshot.joinedAt().toString());
        return member;
    }

    private Map<String, Object> toBannerObject(ClanBannerSnapshot bannerSnapshot) {
        if (bannerSnapshot == null) {
            return null;
        }

        LinkedHashMap<String, Object> banner = new LinkedHashMap<>();
        banner.put("baseMaterial", bannerSnapshot.baseMaterial());
        banner.put("baseColor", bannerSnapshot.baseColor());
        banner.put("patterns", bannerSnapshot.patterns().stream().map(this::toPatternObject).toList());
        return banner;
    }

    private Map<String, Object> toPatternObject(BannerPatternSnapshot patternSnapshot) {
        LinkedHashMap<String, Object> pattern = new LinkedHashMap<>();
        pattern.put("patternId", patternSnapshot.patternId());
        pattern.put("colorId", patternSnapshot.colorId());
        return pattern;
    }

    public record WebhookPayload(
            String eventName,
            String occurredAt,
            String body,
            long clanId,
            String clanName
    ) {
    }

    private static final class JsonSerializer {
        private JsonSerializer() {
        }

        public static String toJson(Object value) {
            StringBuilder builder = new StringBuilder();
            append(builder, value);
            return builder.toString();
        }

        private static void append(StringBuilder builder, Object value) {
            if (value == null) {
                builder.append("null");
                return;
            }

            if (value instanceof String stringValue) {
                appendString(builder, stringValue);
                return;
            }

            if (value instanceof Number || value instanceof Boolean) {
                builder.append(value);
                return;
            }

            if (value instanceof Map<?, ?> map) {
                builder.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!first) {
                        builder.append(',');
                    }
                    first = false;
                    appendString(builder, String.valueOf(entry.getKey()));
                    builder.append(':');
                    append(builder, entry.getValue());
                }
                builder.append('}');
                return;
            }

            if (value instanceof Collection<?> collection) {
                builder.append('[');
                boolean first = true;
                for (Object element : collection) {
                    if (!first) {
                        builder.append(',');
                    }
                    first = false;
                    append(builder, element);
                }
                builder.append(']');
                return;
            }

            if (value instanceof Object[] array) {
                append(builder, List.of(array));
                return;
            }

            throw new IllegalArgumentException("Unsupported JSON value type: " + value.getClass().getName());
        }

        private static void appendString(StringBuilder builder, String value) {
            builder.append('"');

            for (int index = 0; index < value.length(); index++) {
                char current = value.charAt(index);
                switch (current) {
                    case '"':
                        builder.append("\\\"");
                        break;
                    case '\\':
                        builder.append("\\\\");
                        break;
                    case '\b':
                        builder.append("\\b");
                        break;
                    case '\f':
                        builder.append("\\f");
                        break;
                    case '\n':
                        builder.append("\\n");
                        break;
                    case '\r':
                        builder.append("\\r");
                        break;
                    case '\t':
                        builder.append("\\t");
                        break;
                    default:
                        if (current <= 0x1F) {
                            builder.append(String.format("\\u%04x", (int) current));
                        } else {
                            builder.append(current);
                        }
                        break;
                }
            }

            builder.append('"');
        }
    }
}
