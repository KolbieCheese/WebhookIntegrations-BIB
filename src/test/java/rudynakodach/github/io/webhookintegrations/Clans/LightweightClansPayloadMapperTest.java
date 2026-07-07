package rudynakodach.github.io.webhookintegrations.Clans;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

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

    @Test
    void snapshotPayloadUsesClanSyncShapeForEveryClan() {
        ClansWebhookConfig config = new ClansWebhookConfig(true, "https://example.com/webhook", "secret", false, false, 0, true, true, 5000, 5000, 0, 1);
        LightweightClansPayloadMapper mapper = new LightweightClansPayloadMapper();

        String body = mapper.createSnapshotPayload(
                Instant.parse("2026-03-31T22:15:00Z"),
                List.of(
                        LightweightClansTestSupport.clanSnapshot(),
                        LightweightClansTestSupport.renamedClanSnapshot()
                ),
                config
        ).body();

        assertTrue(body.contains("\"event\":\"clan.snapshot\""));
        assertTrue(body.contains("\"occurredAt\":\"2026-03-31T22:15:00Z\""));
        assertTrue(body.contains("\"clans\":["));
        assertTrue(body.contains("\"name\":\"Crimson Knights\""));
        assertTrue(body.contains("\"name\":\"Crimson Empire\""));
        assertTrue(body.contains("\"members\":["));
        assertTrue(body.contains("\"banner\":"));
    }

    @Test
    void snapshotPayloadHonorsClanSyncExportToggles() {
        ClansWebhookConfig config = new ClansWebhookConfig(true, "https://example.com/webhook", "secret", false, false, 0, false, false, 5000, 5000, 0, 1);
        LightweightClansPayloadMapper mapper = new LightweightClansPayloadMapper();

        String body = mapper.createSnapshotPayload(
                Instant.parse("2026-03-31T22:15:00Z"),
                List.of(LightweightClansTestSupport.clanSnapshot()),
                config
        ).body();

        assertFalse(body.contains("\"members\":"));
        assertFalse(body.contains("\"banner\":"));
        assertTrue(body.contains("\"memberCount\":2"));
    }
}
