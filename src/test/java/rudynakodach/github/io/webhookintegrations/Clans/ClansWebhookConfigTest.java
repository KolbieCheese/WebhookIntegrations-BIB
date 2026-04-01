package rudynakodach.github.io.webhookintegrations.Clans;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClansWebhookConfigTest {

    @Test
    void defaultsUseTwoHourPeriodicFullSyncCadence() {
        ClansWebhookConfig config = ClansWebhookConfig.defaults();

        assertEquals(ClansWebhookConfig.DEFAULT_PERIODIC_FULL_SYNC_SECONDS, config.periodicFullSyncSeconds());
        assertFalse(config.hasPeriodicFullSync());
    }

    @Test
    void enabledPeriodicFullSyncClampsShortIntervalsToTwoHours() {
        YamlConfiguration configuration = LightweightClansTestSupport.pluginConfig();
        configuration.set("clansWebhook.enabled", true);
        configuration.set("clansWebhook.endpoint", "https://example.com/webhook");
        configuration.set("clansWebhook.secret", "secret");
        configuration.set("clansWebhook.periodicFullSyncEnabled", true);
        configuration.set("clansWebhook.periodicFullSyncSeconds", 60);

        ClansWebhookConfig config = ClansWebhookConfig.from(LightweightClansTestSupport.pluginWithConfig(configuration));

        assertTrue(config.hasPeriodicFullSync());
        assertEquals(ClansWebhookConfig.MIN_PERIODIC_FULL_SYNC_SECONDS, config.periodicFullSyncSeconds());
        assertEquals(144000L, config.periodicFullSyncTicks());
    }

    @Test
    void enabledPeriodicFullSyncKeepsLongerIntervals() {
        YamlConfiguration configuration = LightweightClansTestSupport.pluginConfig();
        configuration.set("clansWebhook.enabled", true);
        configuration.set("clansWebhook.endpoint", "https://example.com/webhook");
        configuration.set("clansWebhook.secret", "secret");
        configuration.set("clansWebhook.periodicFullSyncEnabled", true);
        configuration.set("clansWebhook.periodicFullSyncSeconds", 14400);

        ClansWebhookConfig config = ClansWebhookConfig.from(LightweightClansTestSupport.pluginWithConfig(configuration));

        assertEquals(14400, config.periodicFullSyncSeconds());
        assertEquals(288000L, config.periodicFullSyncTicks());
    }
}
