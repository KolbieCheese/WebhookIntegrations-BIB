package rudynakodach.github.io.webhookintegrations.Clans;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

public record ClansWebhookConfig(
        boolean enabled,
        String endpoint,
        String secret,
        boolean fullSyncOnStartup,
        boolean periodicFullSyncEnabled,
        int periodicFullSyncSeconds,
        boolean includeMembers,
        boolean includeBanner,
        int connectTimeoutMillis,
        int readTimeoutMillis,
        int retryAttempts,
        int retryDelaySeconds
) {
    public static final int DEFAULT_PERIODIC_FULL_SYNC_SECONDS = 7200;
    public static final int MIN_PERIODIC_FULL_SYNC_SECONDS = 7200;

    public ClansWebhookConfig {
        endpoint = endpoint == null ? "" : endpoint.trim();
        secret = secret == null ? "" : secret;
        periodicFullSyncSeconds = Math.max(0, periodicFullSyncSeconds);
        if (periodicFullSyncEnabled && periodicFullSyncSeconds > 0) {
            periodicFullSyncSeconds = Math.max(MIN_PERIODIC_FULL_SYNC_SECONDS, periodicFullSyncSeconds);
        }
        connectTimeoutMillis = Math.max(1, connectTimeoutMillis);
        readTimeoutMillis = Math.max(1, readTimeoutMillis);
        retryAttempts = Math.max(0, retryAttempts);
        retryDelaySeconds = Math.max(0, retryDelaySeconds);
    }

    public static ClansWebhookConfig from(JavaPlugin plugin) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("clansWebhook");

        if (section == null) {
            return defaults();
        }

        return new ClansWebhookConfig(
                section.getBoolean("enabled", false),
                section.getString("endpoint", "https://example.com/api/clans-webhook"),
                section.getString("secret", "replace-me"),
                section.getBoolean("fullSyncOnStartup", true),
                section.getBoolean("periodicFullSyncEnabled", false),
                section.getInt("periodicFullSyncSeconds", DEFAULT_PERIODIC_FULL_SYNC_SECONDS),
                section.getBoolean("includeMembers", true),
                section.getBoolean("includeBanner", true),
                section.getInt("connectTimeoutMillis", 5000),
                section.getInt("readTimeoutMillis", 5000),
                section.getInt("retryAttempts", 5),
                section.getInt("retryDelaySeconds", 30)
        );
    }

    public static ClansWebhookConfig defaults() {
        return new ClansWebhookConfig(
                false,
                "https://example.com/api/clans-webhook",
                "replace-me",
                true,
                false,
                DEFAULT_PERIODIC_FULL_SYNC_SECONDS,
                true,
                true,
                5000,
                5000,
                5,
                30
        );
    }

    public boolean hasEndpoint() {
        return !endpoint.isBlank();
    }

    public long retryDelayTicks() {
        return retryDelaySeconds * 20L;
    }

    public boolean hasPeriodicFullSync() {
        return periodicFullSyncEnabled && periodicFullSyncSeconds > 0;
    }

    public long periodicFullSyncTicks() {
        return periodicFullSyncSeconds * 20L;
    }
}
