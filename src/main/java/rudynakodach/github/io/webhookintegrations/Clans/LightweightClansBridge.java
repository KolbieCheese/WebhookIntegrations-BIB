package rudynakodach.github.io.webhookintegrations.Clans;

import io.github.maste.customclans.api.LightweightClansApi;
import io.github.maste.customclans.api.model.ClanSnapshot;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class LightweightClansBridge {
    private final JavaPlugin plugin;
    private final ClansWebhookConfig config;
    private final LightweightClansServiceResolver serviceResolver;
    private final PluginManager pluginManager;
    private final LightweightClansPayloadMapper payloadMapper;
    private final LightweightClansWebhookSender webhookSender;
    private final FullSyncScheduler fullSyncScheduler;
    private final LightweightClansListener listener;
    private final AtomicBoolean fullSyncInProgress = new AtomicBoolean(false);

    private ScheduledTask periodicFullSyncTask;
    private volatile boolean active;

    public LightweightClansBridge(JavaPlugin plugin) {
        this(
                plugin,
                ClansWebhookConfig.from(plugin),
                new LightweightClansServiceResolver(plugin),
                plugin.getServer().getPluginManager(),
                new LightweightClansPayloadMapper(),
                null,
                null
        );
    }

    LightweightClansBridge(
            JavaPlugin plugin,
            ClansWebhookConfig config,
            LightweightClansServiceResolver serviceResolver,
            PluginManager pluginManager,
            LightweightClansPayloadMapper payloadMapper,
            LightweightClansWebhookSender webhookSender
    ) {
        this(
                plugin,
                config,
                serviceResolver,
                pluginManager,
                payloadMapper,
                webhookSender,
                null
        );
    }

    LightweightClansBridge(
            JavaPlugin plugin,
            ClansWebhookConfig config,
            LightweightClansServiceResolver serviceResolver,
            PluginManager pluginManager,
            LightweightClansPayloadMapper payloadMapper,
            LightweightClansWebhookSender webhookSender,
            FullSyncScheduler fullSyncScheduler
    ) {
        this.plugin = plugin;
        this.config = config;
        this.serviceResolver = serviceResolver;
        this.pluginManager = pluginManager;
        this.payloadMapper = payloadMapper;
        this.webhookSender = webhookSender == null ? new LightweightClansWebhookSender(plugin, config) : webhookSender;
        this.fullSyncScheduler = fullSyncScheduler == null ? new BukkitFullSyncScheduler(plugin) : fullSyncScheduler;
        this.listener = new LightweightClansListener(this);
    }

    public void enable() {
        if (active) {
            return;
        }

        if (!plugin.getConfig().getBoolean("isEnabled", true)) {
            plugin.getLogger().log(
                    Level.INFO,
                    "Skipping Lightweight Clans webhook integration because WebhookIntegrations is globally disabled (isEnabled=false)."
            );
            return;
        }

        if (!config.enabled()) {
            plugin.getLogger().log(
                    Level.INFO,
                    "Skipping Lightweight Clans webhook integration because clansWebhook.enabled=false."
            );
            return;
        }

        if (!config.hasEndpoint()) {
            plugin.getLogger().log(Level.WARNING, "clansWebhook.endpoint is blank; Lightweight Clans webhook integration is disabled.");
            return;
        }

        Optional<LightweightClansApi> api = serviceResolver.resolve();

        if (api.isEmpty()) {
            plugin.getLogger().log(Level.WARNING, "Lightweight Clans API was not found in Bukkit ServicesManager; clans webhook integration is disabled.");
            return;
        }

        pluginManager.registerEvents(listener, plugin);
        active = true;

        plugin.getLogger().log(
                Level.INFO,
                "Lightweight Clans webhook integration enabled (startupSync={0}, periodicSync={1}, periodicSeconds={2}).",
                new Object[]{config.fullSyncOnStartup(), config.hasPeriodicFullSync(), config.periodicFullSyncSeconds()}
        );

        if (config.fullSyncOnStartup()) {
            queueFullSync(api.get(), "startup");
        }

        if (config.hasPeriodicFullSync()) {
            schedulePeriodicFullSync(api.get());
        }
    }

    public boolean isActive() {
        return active;
    }

    public BridgeStatus describeStatus() {
        return new BridgeStatus(
                plugin.getConfig().getBoolean("isEnabled", true),
                config.enabled(),
                config.hasEndpoint(),
                serviceResolver.resolve().isPresent(),
                active,
                config.fullSyncOnStartup(),
                config.hasPeriodicFullSync(),
                config.periodicFullSyncSeconds()
        );
    }

    public ManualSyncResult queueManualFullSync() {
        if (!plugin.getConfig().getBoolean("isEnabled", true)) {
            return ManualSyncResult.GLOBALLY_DISABLED;
        }

        if (!config.enabled()) {
            return ManualSyncResult.CLANS_WEBHOOK_DISABLED;
        }

        if (!config.hasEndpoint()) {
            return ManualSyncResult.MISSING_ENDPOINT;
        }

        Optional<LightweightClansApi> api = serviceResolver.resolve();
        if (api.isEmpty()) {
            return ManualSyncResult.API_UNAVAILABLE;
        }

        return queueFullSync(api.get(), "manual")
                ? ManualSyncResult.QUEUED
                : ManualSyncResult.ALREADY_RUNNING;
    }

    public void disable() {
        if (periodicFullSyncTask != null) {
            periodicFullSyncTask.cancel();
            periodicFullSyncTask = null;
        }

        HandlerList.unregisterAll(listener);
        fullSyncInProgress.set(false);
        active = false;
    }

    void sendClanSnapshot(String eventName, ClanSnapshot clanSnapshot, Collection<String> changedFields) {
        webhookSender.sendAsync(payloadMapper.createClanPayload(
                eventName,
                Instant.now(),
                clanSnapshot,
                changedFields,
                config
        ));
    }

    void sendDeletedClan(String eventName, ClanSnapshot clanSnapshot) {
        webhookSender.sendAsync(payloadMapper.createDeletePayload(eventName, Instant.now(), clanSnapshot));
    }

    private void schedulePeriodicFullSync(LightweightClansApi api) {
        periodicFullSyncTask = fullSyncScheduler.runRepeatingAsync(
                () -> queueFullSync(api, "periodic"),
                config.periodicFullSyncTicks(),
                config.periodicFullSyncTicks()
        );

        plugin.getLogger().log(
                Level.INFO,
                "Scheduled Lightweight Clans periodic full sync every {0} second(s).",
                config.periodicFullSyncSeconds()
        );
    }

    private boolean queueFullSync(LightweightClansApi api, String trigger) {
        if (!fullSyncInProgress.compareAndSet(false, true)) {
            plugin.getLogger().log(
                    Level.FINE,
                    "Skipping Lightweight Clans {0} full sync because a previous full sync is still in progress.",
                    trigger
            );
            return false;
        }

        plugin.getLogger().log(
                Level.INFO,
                "Starting Lightweight Clans {0} full sync for clans webhook integration.",
                trigger
        );

        api.getAllClansAsync().whenComplete((clans, throwable) -> {
            try {
                if (!active && !"manual".equals(trigger)) {
                    return;
                }

                if (throwable != null) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load Lightweight Clans data for " + trigger + " sync: " + throwable.getMessage());
                    return;
                }

                List<ClanSnapshot> allClans = clans == null ? List.of() : clans;
                plugin.getLogger().log(
                        Level.INFO,
                        "Queueing Lightweight Clans {0} sync for {1} clan(s).",
                        new Object[]{trigger, allClans.size()}
                );

                for (ClanSnapshot clanSnapshot : allClans) {
                    sendClanSnapshot("clan.sync", clanSnapshot, null);
                }
            } finally {
                fullSyncInProgress.set(false);
            }
        });

        return true;
    }

    public record BridgeStatus(
            boolean masterEnabled,
            boolean clansWebhookEnabled,
            boolean endpointConfigured,
            boolean apiAvailable,
            boolean active,
            boolean fullSyncOnStartup,
            boolean periodicFullSyncEnabled,
            int periodicFullSyncSeconds
    ) {
    }

    public enum ManualSyncResult {
        QUEUED,
        ALREADY_RUNNING,
        GLOBALLY_DISABLED,
        CLANS_WEBHOOK_DISABLED,
        MISSING_ENDPOINT,
        API_UNAVAILABLE
    }

    interface FullSyncScheduler {
        ScheduledTask runRepeatingAsync(Runnable task, long initialDelayTicks, long periodTicks);
    }

    interface ScheduledTask {
        void cancel();
    }

    private static final class BukkitFullSyncScheduler implements FullSyncScheduler {
        private final JavaPlugin plugin;

        private BukkitFullSyncScheduler(JavaPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public ScheduledTask runRepeatingAsync(Runnable task, long initialDelayTicks, long periodTicks) {
            BukkitTask bukkitTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                    plugin,
                    task,
                    initialDelayTicks,
                    periodTicks
            );
            return bukkitTask::cancel;
        }
    }
}