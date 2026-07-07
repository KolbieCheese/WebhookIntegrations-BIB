package rudynakodach.github.io.webhookintegrations.Clans;

import io.github.maste.customclans.api.LightweightClansApi;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

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
        ClansWebhookConfig config = new ClansWebhookConfig(true, "https://example.com/webhook", "secret", true, false, 0, true, true, 5000, 5000, 0, 1);
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
    void startupFullSyncSendsAuthoritativeSnapshotBeforeCompatibilitySyncs() {
        ClansWebhookConfig config = new ClansWebhookConfig(true, "https://example.com/webhook", "secret", true, false, 0, true, true, 5000, 5000, 0, 1);
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
        assertEquals(3, scheduler.queuedTaskCount());

        scheduler.runAll();

        assertEquals(3, transport.requests().size());
        assertTrue(transport.requests().getFirst().body().contains("\"event\":\"clan.snapshot\""));
        assertTrue(transport.requests().getFirst().body().contains("\"clans\":["));
        assertTrue(transport.requests().getFirst().body().contains("\"name\":\"Crimson Knights\""));
        assertTrue(transport.requests().getFirst().body().contains("\"name\":\"Crimson Empire\""));
        assertTrue(transport.requests().get(1).body().contains("\"event\":\"clan.sync\""));
        assertTrue(transport.requests().get(2).body().contains("\"event\":\"clan.sync\""));
    }

    @Test
    void startupFullSyncSendsEmptySnapshotWithSignedSnapshotHeaders() {
        ClansWebhookConfig config = new ClansWebhookConfig(true, "https://example.com/webhook", "secret", true, false, 0, true, true, 5000, 5000, 0, 1);
        LightweightClansServiceResolver resolver = mock(LightweightClansServiceResolver.class);
        PluginManager pluginManager = mock(PluginManager.class);
        LightweightClansApi api = mock(LightweightClansApi.class);
        LightweightClansTestSupport.RecordingScheduler scheduler = new LightweightClansTestSupport.RecordingScheduler();
        LightweightClansTestSupport.RecordingTransport transport = new LightweightClansTestSupport.RecordingTransport();

        when(resolver.resolve()).thenReturn(Optional.of(api));
        when(api.getAllClansAsync()).thenReturn(CompletableFuture.completedFuture(List.of()));

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
                        new LightweightClansWebhookSigner(),
                        () -> java.time.Instant.parse("2026-03-31T22:00:00Z")
                )
        );

        bridge.enable();

        assertEquals(1, scheduler.queuedTaskCount());
        scheduler.runAll();

        assertEquals(1, transport.requests().size());
        LightweightClansTestSupport.Request snapshotRequest = transport.requests().getFirst();
        assertTrue(snapshotRequest.body().contains("\"event\":\"clan.snapshot\""));
        assertTrue(snapshotRequest.body().contains("\"clans\":[]"));
        assertEquals("lightweight-clans", snapshotRequest.headers().get("X-Webhook-Source"));
        assertEquals("clan.snapshot", snapshotRequest.headers().get("X-Webhook-Event"));
        assertEquals("2026-03-31T22:00:00Z", snapshotRequest.headers().get("X-Webhook-Timestamp"));
        assertEquals(
                new LightweightClansWebhookSigner().sign("secret", "2026-03-31T22:00:00Z", snapshotRequest.body()),
                snapshotRequest.headers().get("X-Webhook-Signature")
        );
    }

    @Test
    void periodicFullSyncQueuesClanSnapshotsOnConfiguredInterval() {
        ClansWebhookConfig config = new ClansWebhookConfig(
                true,
                "https://example.com/webhook",
                "secret",
                false,
                true,
                ClansWebhookConfig.DEFAULT_PERIODIC_FULL_SYNC_SECONDS,
                true,
                true,
                5000,
                5000,
                0,
                1
        );
        LightweightClansServiceResolver resolver = mock(LightweightClansServiceResolver.class);
        PluginManager pluginManager = mock(PluginManager.class);
        LightweightClansApi api = mock(LightweightClansApi.class);
        LightweightClansTestSupport.RecordingScheduler deliveryScheduler = new LightweightClansTestSupport.RecordingScheduler();
        LightweightClansTestSupport.RecordingTransport transport = new LightweightClansTestSupport.RecordingTransport();
        LightweightClansTestSupport.RecordingFullSyncScheduler fullSyncScheduler = new LightweightClansTestSupport.RecordingFullSyncScheduler();

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
                        deliveryScheduler,
                        transport,
                        new LightweightClansWebhookSigner()
                ),
                fullSyncScheduler
        );

        bridge.enable();

        assertTrue(bridge.isActive());
        assertEquals(List.of(144000L), fullSyncScheduler.initialDelays());
        assertEquals(List.of(144000L), fullSyncScheduler.periods());
        assertEquals(1, fullSyncScheduler.queuedTaskCount());

        fullSyncScheduler.runNext();
        assertEquals(3, deliveryScheduler.queuedTaskCount());

        deliveryScheduler.runAll();

        assertEquals(3, transport.requests().size());
        assertTrue(transport.requests().getFirst().body().contains("\"event\":\"clan.snapshot\""));
        assertTrue(transport.requests().get(1).body().contains("\"event\":\"clan.sync\""));
        assertTrue(transport.requests().get(2).body().contains("\"event\":\"clan.sync\""));

        bridge.disable();
        assertEquals(1, fullSyncScheduler.cancelledTaskCount());
    }

    @Test
    void periodicFullSyncRequiresExplicitOptIn() {
        ClansWebhookConfig config = new ClansWebhookConfig(
                true,
                "https://example.com/webhook",
                "secret",
                false,
                false,
                ClansWebhookConfig.DEFAULT_PERIODIC_FULL_SYNC_SECONDS,
                true,
                true,
                5000,
                5000,
                0,
                1
        );
        LightweightClansServiceResolver resolver = mock(LightweightClansServiceResolver.class);
        PluginManager pluginManager = mock(PluginManager.class);
        LightweightClansApi api = mock(LightweightClansApi.class);
        LightweightClansTestSupport.RecordingFullSyncScheduler fullSyncScheduler = new LightweightClansTestSupport.RecordingFullSyncScheduler();

        when(resolver.resolve()).thenReturn(Optional.of(api));

        LightweightClansBridge bridge = new LightweightClansBridge(
                LightweightClansTestSupport.pluginWithConfig(LightweightClansTestSupport.pluginConfig()),
                config,
                resolver,
                pluginManager,
                new LightweightClansPayloadMapper(),
                new LightweightClansWebhookSender(
                        LightweightClansTestSupport.pluginWithConfig(LightweightClansTestSupport.pluginConfig()),
                        config,
                        new LightweightClansTestSupport.RecordingScheduler(),
                        new LightweightClansTestSupport.RecordingTransport(),
                        new LightweightClansWebhookSigner()
                ),
                fullSyncScheduler
        );

        bridge.enable();

        assertTrue(bridge.isActive());
        assertEquals(0, fullSyncScheduler.queuedTaskCount());
    }

    @Test
    void manualFullSyncQueuesClanSnapshotsImmediately() {
        ClansWebhookConfig config = new ClansWebhookConfig(
                true,
                "https://example.com/webhook",
                "secret",
                false,
                false,
                0,
                true,
                true,
                5000,
                5000,
                0,
                1
        );
        LightweightClansServiceResolver resolver = mock(LightweightClansServiceResolver.class);
        PluginManager pluginManager = mock(PluginManager.class);
        LightweightClansApi api = mock(LightweightClansApi.class);
        LightweightClansTestSupport.RecordingScheduler deliveryScheduler = new LightweightClansTestSupport.RecordingScheduler();
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
                        deliveryScheduler,
                        transport,
                        new LightweightClansWebhookSigner()
                )
        );

        bridge.enable();

        assertEquals(LightweightClansBridge.ManualSyncResult.QUEUED, bridge.queueManualFullSync());
        assertEquals(3, deliveryScheduler.queuedTaskCount());

        deliveryScheduler.runAll();

        assertEquals(3, transport.requests().size());
        assertTrue(transport.requests().getFirst().body().contains("\"event\":\"clan.snapshot\""));
        assertTrue(transport.requests().get(1).body().contains("\"event\":\"clan.sync\""));
    }

    @Test
    void manualFullSyncCanRecoverAfterApiBecomesAvailableLater() {
        ClansWebhookConfig config = new ClansWebhookConfig(true, "https://example.com/webhook", "secret", false, false, 0, true, true, 5000, 5000, 0, 1);
        LightweightClansServiceResolver resolver = mock(LightweightClansServiceResolver.class);
        PluginManager pluginManager = mock(PluginManager.class);
        LightweightClansApi api = mock(LightweightClansApi.class);
        LightweightClansTestSupport.RecordingScheduler deliveryScheduler = new LightweightClansTestSupport.RecordingScheduler();
        LightweightClansTestSupport.RecordingTransport transport = new LightweightClansTestSupport.RecordingTransport();
        AtomicBoolean apiAvailable = new AtomicBoolean(false);

        when(resolver.resolve()).thenAnswer(invocation -> apiAvailable.get() ? Optional.of(api) : Optional.empty());
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
                        deliveryScheduler,
                        transport,
                        new LightweightClansWebhookSigner()
                )
        );

        bridge.enable();
        assertFalse(bridge.isActive());

        apiAvailable.set(true);
        assertEquals(LightweightClansBridge.ManualSyncResult.QUEUED, bridge.queueManualFullSync());

        deliveryScheduler.runAll();

        assertEquals(3, transport.requests().size());
        assertTrue(transport.requests().getFirst().body().contains("\"event\":\"clan.snapshot\""));
    }
}
