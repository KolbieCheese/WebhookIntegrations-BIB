package rudynakodach.github.io.webhookintegrations.Clans;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class LightweightClansWebhookSenderTest {

    @Test
    void failedDeliveriesAreRetried() {
        ClansWebhookConfig config = new ClansWebhookConfig(true, "https://example.com/webhook", "secret", false, 0, true, true, 5000, 5000, 2, 1);
        LightweightClansTestSupport.RecordingScheduler scheduler = new LightweightClansTestSupport.RecordingScheduler();
        LightweightClansTestSupport.RecordingTransport transport = new LightweightClansTestSupport.RecordingTransport();
        transport.addResult(LightweightClansWebhookSender.DeliveryResult.failure("HTTP 500"));
        transport.addResult(LightweightClansWebhookSender.DeliveryResult.failure("HTTP 502"));
        transport.addResult(LightweightClansWebhookSender.DeliveryResult.ok());

        LightweightClansWebhookSender sender = new LightweightClansWebhookSender(
                LightweightClansTestSupport.pluginWithConfig(LightweightClansTestSupport.pluginConfig()),
                config,
                scheduler,
                transport,
                new LightweightClansWebhookSigner()
        );

        sender.sendAsync(new LightweightClansPayloadMapper.WebhookPayload(
                "clan.created",
                "2026-03-31T21:30:00Z",
                "{\"event\":\"clan.created\"}",
                42L,
                "Crimson Knights"
        ));

        scheduler.runAll();

        assertEquals(3, transport.requests().size());
        assertEquals(2, scheduler.delays().size());
        assertEquals(20L, scheduler.delays().getFirst());
        assertEquals(20L, scheduler.delays().get(1));
    }

    @Test
    void clientErrorsAreNotRetried() {
        ClansWebhookConfig config = new ClansWebhookConfig(true, "https://example.com/webhook", "secret", false, 0, true, true, 5000, 5000, 5, 1);
        LightweightClansTestSupport.RecordingScheduler scheduler = new LightweightClansTestSupport.RecordingScheduler();
        LightweightClansTestSupport.RecordingTransport transport = new LightweightClansTestSupport.RecordingTransport();
        transport.addResult(LightweightClansWebhookSender.DeliveryResult.failure("HTTP 400", false));

        LightweightClansWebhookSender sender = new LightweightClansWebhookSender(
                LightweightClansTestSupport.pluginWithConfig(LightweightClansTestSupport.pluginConfig()),
                config,
                scheduler,
                transport,
                new LightweightClansWebhookSigner()
        );

        sender.sendAsync(new LightweightClansPayloadMapper.WebhookPayload(
                "clan.sync",
                "2026-03-31T21:30:00Z",
                "{\"event\":\"clan.sync\"}",
                42L,
                "Crimson Knights"
        ));

        scheduler.runAll();

        assertEquals(1, transport.requests().size());
        assertEquals(0, scheduler.delays().size());
    }

    @Test
    void retriesUseFreshRequestTimestampsForSigning() {
        ClansWebhookConfig config = new ClansWebhookConfig(true, "https://example.com/webhook", "secret", false, 0, true, true, 5000, 5000, 1, 1);
        LightweightClansTestSupport.RecordingScheduler scheduler = new LightweightClansTestSupport.RecordingScheduler();
        LightweightClansTestSupport.RecordingTransport transport = new LightweightClansTestSupport.RecordingTransport();
        Deque<Instant> timestamps = new ArrayDeque<>();
        timestamps.add(Instant.parse("2026-03-31T21:30:00Z"));
        timestamps.add(Instant.parse("2026-03-31T21:30:31Z"));

        transport.addResult(LightweightClansWebhookSender.DeliveryResult.failure("HTTP 500"));
        transport.addResult(LightweightClansWebhookSender.DeliveryResult.ok());

        LightweightClansWebhookSender sender = new LightweightClansWebhookSender(
                LightweightClansTestSupport.pluginWithConfig(LightweightClansTestSupport.pluginConfig()),
                config,
                scheduler,
                transport,
                new LightweightClansWebhookSigner(),
                timestamps::removeFirst
        );

        sender.sendAsync(new LightweightClansPayloadMapper.WebhookPayload(
                "clan.sync",
                "2026-03-31T21:29:45Z",
                "{\"event\":\"clan.sync\"}",
                42L,
                "Crimson Knights"
        ));

        scheduler.runAll();

        assertEquals(2, transport.requests().size());
        assertEquals("2026-03-31T21:30:00Z", transport.requests().getFirst().headers().get("X-Webhook-Timestamp"));
        assertEquals("2026-03-31T21:30:31Z", transport.requests().get(1).headers().get("X-Webhook-Timestamp"));
        assertNotEquals(
                transport.requests().getFirst().headers().get("X-Webhook-Signature"),
                transport.requests().get(1).headers().get("X-Webhook-Signature")
        );
    }
}
