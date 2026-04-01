package rudynakodach.github.io.webhookintegrations.Clans;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LightweightClansWebhookSignerTest {

    @Test
    void generatesExpectedHmacSignature() {
        LightweightClansWebhookSigner signer = new LightweightClansWebhookSigner();

        String signature = signer.sign(
                "super-secret",
                "2026-03-31T21:15:30Z",
                "{\"event\":\"clan.created\",\"clan\":{\"id\":42}}"
        );

        assertEquals("sha256=3ab4f1c631dc00133a2ac1470ff6a3c0aa682fd2f91910930cc38085225c309e", signature);
    }
}
