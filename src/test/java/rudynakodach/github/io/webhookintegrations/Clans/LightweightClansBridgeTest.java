package rudynakodach.github.io.webhookintegrations.Clans;

import io.github.maste.customclans.api.LightweightClansApi;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LightweightClansBridgeTest {

    @Test
    void missingApiDisablesOnlyClansIntegrationPath() {
        ClansWebhookConfig config = new ClansWebhookConfig(true, "https://example.com/webhook", "secret", true, true, true, 5000, 5000, 0, 1);
        LightweightClansServiceResolver resolver = mock(LightweightClansServiceResolver.class);
        PluginManager pluginManager = mock(PluginManager.class);
        LightweightClansWebhookSender sender = new LightweightClansWebhookSender(
                LightweightClansTestSupport.pluginWithConfig(LightweightClansTestSupport.pluginConfig()),
                config,
                new LightweightClansTestSupport.RecordingScheduler(),
                new LightweightClansTestSupport.RecordingTransport(),
                new LightweightClansWebhookSigner()
        );

        when(resolver.resolve()).thenReturn(Optional.empty());

        LightweightClansBridge bridge = new LightweightClansBridge(
                LightweightClansTestSupport.pluginWithConfig(LightweightClansTestSupport.pluginConfig()),
                config,
                resolver,
                pluginManager,
                new LightweightClansPayloadMapper(),
                sender
        );

        bridge.enable();

        assertFalse(bridge.isActive());
        verify(pluginManager, never()).registerEvents(any(), any());
    }

    @Test
    void startupFullSyncSendsOnePayloadPerClan() {
        ClansWebhookConfig config = new ClansWebhookConfig(true, "https://example.com/webhook", "secret", true, true, true, 5000, 5000, 0, 1);
        LightweightClansServiceResolver resolver = mock(LightweightClansServiceResolver.class);
        PluginManager pluginManager = mock(PluginManager.class);
        LightweightClansApi api = mock(LightweightClansApi.class);
        LightweightClansTestSupport.RecordingScheduler scheduler = new LightweightClansTestSupport.RecordingScheduler();
        LightweightClansTestSupport.RecordingTransport transport = new LightweightClansTestSupport.RecordingTransport();

        when(resolver.resolve()).thenReturn(Optional.of(api));
        when(api.getAllClansAsync()).thenReturn(CompletableFuture.completedFuture(List.of(
                LightweightClansTestSupport.clanSnapshot(),
                LightweightClansTestSupport.renamedClanSnapshot()
        )));

        LightweightClansBridge bridge = new LightweightClansBridge(
                LightweightClansTestSupport.pluginWithConfig(LightweightClansTestSupport.pluginConfig()),
                config,
                resolver,
                pluginManager,
                new LightweightClansPayloadMapper(),
                new LightweightClansWebhookSender(
                        LightweightClansTestSupport.pluginWithConfig(LightweightClansTestSupport.pluginConfig()),
                        config,
                        scheduler,
                        transport,
                        new LightweightClansWebhookSigner()
                )
        );

        bridge.enable();

        assertTrue(bridge.isActive());
        verify(pluginManager).registerEvents(any(), any());
        assertEquals(2, scheduler.queuedTaskCount());

        scheduler.runAll();

        assertEquals(2, transport.requests().size());
        assertTrue(transport.requests().getFirst().body().contains("\"event\":\"clan.sync\""));
        assertTrue(transport.requests().get(1).body().contains("\"event\":\"clan.sync\""));
    }
}
