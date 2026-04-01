package rudynakodach.github.io.webhookintegrations.Clans;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LightweightClansWebhookSenderTest {

    @Test
    void failedDeliveriesAreRetried() {
        ClansWebhookConfig config = new ClansWebhookConfig(true, "https://example.com/webhook", "secret", false, true, true, 5000, 5000, 2, 1);
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
}
