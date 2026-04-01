package rudynakodach.github.io.webhookintegrations.Clans;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

public record ClansWebhookConfig(
        boolean enabled,
        String endpoint,
        String secret,
        boolean fullSyncOnStartup,
        int periodicFullSyncSeconds,
        boolean includeMembers,
        boolean includeBanner,
        int connectTimeoutMillis,
        int readTimeoutMillis,
        int retryAttempts,
        int retryDelaySeconds
) {

    public ClansWebhookConfig {
        endpoint = endpoint == null ? "" : endpoint.trim();
        secret = secret == null ? "" : secret;
        periodicFullSyncSeconds = Math.max(0, periodicFullSyncSeconds);
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
                section.getInt("periodicFullSyncSeconds", 60),
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
                60,
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
        return periodicFullSyncSeconds > 0;
    }

    public long periodicFullSyncTicks() {
        return periodicFullSyncSeconds * 20L;
    }
}
