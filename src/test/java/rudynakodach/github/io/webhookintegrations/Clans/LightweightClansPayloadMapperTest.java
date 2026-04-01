package rudynakodach.github.io.webhookintegrations.Clans;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightweightClansPayloadMapperTest {

    @Test
    void includeMembersFalseOmitsMemberListFromPayload() {
        ClansWebhookConfig config = new ClansWebhookConfig(true, "https://example.com/webhook", "secret", false, false, 0, false, true, 5000, 5000, 0, 1);
        LightweightClansPayloadMapper mapper = new LightweightClansPayloadMapper();

        String body = mapper.createClanPayload(
                "clan.created",
                Instant.parse("2026-03-31T21:00:00Z"),
                LightweightClansTestSupport.clanSnapshot(),
                null,
                config
        ).body();

        assertFalse(body.contains("\"members\":"));
        assertTrue(body.contains("\"memberCount\":2"));
    }

    @Test
    void includeBannerFalseOmitsBannerFromPayload() {
        ClansWebhookConfig config = new ClansWebhookConfig(true, "https://example.com/webhook", "secret", false, false, 0, true, false, 5000, 5000, 0, 1);
        LightweightClansPayloadMapper mapper = new LightweightClansPayloadMapper();

        String body = mapper.createClanPayload(
                "clan.created",
                Instant.parse("2026-03-31T21:00:00Z"),
                LightweightClansTestSupport.clanSnapshot(),
                null,
                config
        ).body();

        assertFalse(body.contains("\"banner\":"));
        assertTrue(body.contains("\"members\":["));
    }
}
