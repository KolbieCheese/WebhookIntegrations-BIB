package rudynakodach.github.io.webhookintegrations.forge;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Forge configuration is independent of Bukkit's YAML and plugin services. */
public final class WebhookConfig {
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public boolean enabled = true;
    public Map<String, String> webhooks = new LinkedHashMap<>(Map.of("main", ""));
    public boolean preventUsernameMarkdownFormatting = true;
    public boolean preventMessageMarkdownFormatting = false;
    public boolean removeColorCoding = false;
    public Map<String, String> censoring = new LinkedHashMap<>(Map.of("@everyone", "everyone", "@here", "here"));
    public Map<String, EventMessage> events = defaults();
    public Map<String, JsonObject> templates = new LinkedHashMap<>();

    public static final class EventMessage {
        public boolean announce = true;
        public String target = "main";
        public boolean requireOperator = false;
        public JsonObject message;
        EventMessage() { this(""); }
        EventMessage(String description) {
            message = new JsonObject();
            message.addProperty("content", description);
        }
    }

    private static Map<String, EventMessage> defaults() {
        Map<String, EventMessage> result = new LinkedHashMap<>();
        result.put("onServerStart", new EventMessage("Server started."));
        result.put("onServerStop", new EventMessage("Server stopped."));
        result.put("onPlayerJoin", new EventMessage("**$player$** joined the game."));
        result.put("onPlayerQuit", new EventMessage("**$player$** left the game."));
        result.put("onPlayerChat", new EventMessage("**$player$**: $message$"));
        result.put("onPlayerDeath", new EventMessage("$deathMessage$"));
        result.put("onPlayerAdvancement", new EventMessage("**$player$** has made the advancement **$advancement$**."));
        EventMessage count = new EventMessage("Players online: **$playersOnline$** / $maxPlayers$");
        count.announce = false;
        result.put("onPlayerCountChange", count);
        return result;
    }

    public static WebhookConfig load(Path path) throws IOException {
        if (!Files.exists(path)) {
            WebhookConfig config = new WebhookConfig();
            config.save(path);
            return config;
        }
        try (var reader = Files.newBufferedReader(path)) {
            WebhookConfig config = GSON.fromJson(reader, WebhookConfig.class);
            if (config == null || config.webhooks == null || config.events == null || config.censoring == null || config.templates == null)
                throw new IllegalArgumentException("Configuration maps must not be null");
            config.webhooks.forEach((name, url) -> { if (url == null) throw new IllegalArgumentException("Null webhook URL"); validateUrl(url); });
            config.events.forEach((name, event) -> {
                if (event == null || event.target == null || event.message == null)
                    throw new IllegalArgumentException("Invalid event: " + name);
            });
            config.censoring.forEach((key, value) -> { if (value == null) throw new IllegalArgumentException("Null censor replacement"); });
            config.templates.forEach((key, value) -> { if (value == null) throw new IllegalArgumentException("Null template"); });
            return config;
        } catch (RuntimeException e) {
            throw new IOException("Invalid webhookintegrations.json: " + e.getMessage(), e);
        }
    }

    public void save(Path path) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(this));
        try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
    }

    public static void validateUrl(String url) {
        if (url.isBlank()) return;
        var uri = java.net.URI.create(url);
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null)
            throw new IllegalArgumentException("Webhook must be an HTTP(S) URL without user information");
    }

    String clean(String text, boolean markdown) {
        for (var entry : censoring.entrySet()) text = text.replace(entry.getKey(), entry.getValue());
        if (removeColorCoding) text = text.replaceAll("[&§][0-9a-fk-orA-FK-OR]|&?#[0-9a-fA-F]{6}", "");
        if (markdown) text = text.replaceAll("([\\\\*_~`|>])", "\\\\$1");
        return text;
    }
}
