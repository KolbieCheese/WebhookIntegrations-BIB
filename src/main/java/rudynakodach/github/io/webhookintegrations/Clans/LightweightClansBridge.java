package rudynakodach.github.io.webhookintegrations.Clans;

import io.github.maste.customclans.api.LightweightClansApi;
import io.github.maste.customclans.api.model.ClanSnapshot;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

public class LightweightClansBridge {
    private final JavaPlugin plugin;
    private final ClansWebhookConfig config;
    private final LightweightClansServiceResolver serviceResolver;
    private final PluginManager pluginManager;
    private final LightweightClansPayloadMapper payloadMapper;
    private final LightweightClansWebhookSender webhookSender;

    private boolean active;

    public LightweightClansBridge(JavaPlugin plugin) {
        this(
                plugin,
                ClansWebhookConfig.from(plugin),
                new LightweightClansServiceResolver(plugin),
                plugin.getServer().getPluginManager(),
                new LightweightClansPayloadMapper(),
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
        this.plugin = plugin;
        this.config = config;
        this.serviceResolver = serviceResolver;
        this.pluginManager = pluginManager;
        this.payloadMapper = payloadMapper;
        this.webhookSender = webhookSender == null ? new LightweightClansWebhookSender(plugin, config) : webhookSender;
    }

    public void enable() {
        if (!plugin.getConfig().getBoolean("isEnabled", true)) {
            return;
        }

        if (!config.enabled()) {
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

        pluginManager.registerEvents(new LightweightClansListener(this), plugin);
        active = true;

        plugin.getLogger().log(Level.INFO, "Lightweight Clans webhook integration enabled.");

        if (config.fullSyncOnStartup()) {
            startFullSync(api.get());
        }
    }

    public boolean isActive() {
        return active;
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

    private void startFullSync(LightweightClansApi api) {
        plugin.getLogger().log(Level.INFO, "Starting Lightweight Clans full sync for clans webhook integration.");

        api.getAllClansAsync().whenComplete((clans, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().log(Level.WARNING, "Failed to load Lightweight Clans data for startup sync: " + throwable.getMessage());
                return;
            }

            List<ClanSnapshot> allClans = clans == null ? List.of() : clans;
            plugin.getLogger().log(Level.INFO, "Queueing Lightweight Clans startup sync for " + allClans.size() + " clan(s).");

            for (ClanSnapshot clanSnapshot : allClans) {
                sendClanSnapshot("clan.sync", clanSnapshot, null);
            }
        });
    }
}
