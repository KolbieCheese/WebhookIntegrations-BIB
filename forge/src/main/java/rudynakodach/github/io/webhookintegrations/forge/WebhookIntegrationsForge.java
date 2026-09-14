package rudynakodach.github.io.webhookintegrations.forge;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Mod(WebhookIntegrationsForge.MOD_ID)
public final class WebhookIntegrationsForge {
    public static final String MOD_ID = "webhookintegrations";
    private static final Logger LOG = LogUtils.getLogger();
    private final Path configPath = FMLPaths.CONFIGDIR.get().resolve("webhookintegrations.json");
    private WebhookConfig config;
    private WebhookSender sender;
    private MinecraftServer server;

    public WebhookIntegrationsForge() { MinecraftForge.EVENT_BUS.register(this); }

    @SubscribeEvent public void started(ServerStartedEvent event) {
        server = event.getServer();
        sender = new WebhookSender(LOG::warn);
        try { config = WebhookConfig.load(configPath); }
        catch (Exception e) { LOG.error("Cannot load webhookintegrations.json; fix it and run /wi reload."); return; }
        LOG.info("WebhookIntegrations Forge ready. Configuration: {}", configPath);
        emit("onServerStart", null, Map.of());
    }

    @SubscribeEvent public void stopping(ServerStoppingEvent event) {
        emit("onServerStop", null, Map.of());
        if (sender != null) sender.close();
        sender = null;
        server = null;
        config = null;
    }

    @SubscribeEvent public void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            emit("onPlayerJoin", player, Map.of());
            emit("onPlayerCountChange", null, Map.of());
        }
    }

    @SubscribeEvent public void left(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && server != null) {
            // Forge fires this before the player is removed from PlayerList.
            Map<String, String> values = Map.of("playersOnline", Integer.toString(Math.max(0, server.getPlayerCount() - 1)));
            emit("onPlayerQuit", player, values);
            emit("onPlayerCountChange", null, values);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public void chat(ServerChatEvent event) {
        if (config != null) emit("onPlayerChat", event.getPlayer(), Map.of("message", config.clean(event.getMessage().getString(), config.preventMessageMarkdownFormatting)));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public void died(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Map<String, String> extra = new HashMap<>();
            extra.put("deathMessage", event.getSource().getLocalizedDeathMessage(player).getString());
            extra.put("oldLevel", Integer.toString(player.experienceLevel));
            extra.put("oldExp", Float.toString(player.experienceProgress));
            if (event.getSource().getEntity() instanceof ServerPlayer killer) {
                extra.put("killer", killer.getGameProfile().getName());
                extra.put("killerUuid", killer.getUUID().toString());
            }
            emit("onPlayerDeath", player, extra);
        }
    }

    @SubscribeEvent public void advanced(AdvancementEvent.AdvancementEarnEvent event) {
        var display = event.getAdvancement().getDisplay();
        if (display != null && display.shouldAnnounceChat() && event.getEntity() instanceof ServerPlayer player)
            emit("onPlayerAdvancement", player, Map.of("advancement", display.getTitle().getString(), "desc", display.getDescription().getString()));
    }

    private Map<String, String> values(ServerPlayer player) {
        Map<String, String> values = new HashMap<>();
        values.put("timestamp", Instant.now().toString());
        values.put("time", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        values.put("playersOnline", Integer.toString(server.getPlayerCount()));
        values.put("maxPlayers", Integer.toString(server.getMaxPlayers()));
        values.put("serverIp", server.getLocalIp());
        values.put("serverMotd", server.getMotd());
        values.put("serverName", "Minecraft Server");
        values.put("serverVersion", server.getServerVersion());
        values.put("isOnlineMode", Boolean.toString(server.usesAuthentication()));
        if (player != null) {
            String name = player.getGameProfile().getName();
            values.put("player", config.clean(name, config.preventUsernameMarkdownFormatting));
            values.put("rawUsername", name);
            values.put("uuid", player.getUUID().toString());
            values.put("worldName", player.serverLevel().dimension().location().toString());
        }
        return values;
    }

    private void emit(String event, ServerPlayer player, Map<String, String> extra) {
        if (server == null || sender == null || config == null || !config.enabled) return;
        var message = config.events.get(event);
        if (message == null || !message.announce || (player != null && message.requireOperator && !player.hasPermissions(2))) return;
        var values = values(player);
        values.putAll(extra);
        sender.send(config.webhooks.get(message.target), MessageRenderer.payload(message.message, values), message.headers);
    }

    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        var root = dispatcher.register(Commands.literal("wi").requires(source -> source.hasPermission(2))
            .executes(context -> {
                context.getSource().sendSuccess(() -> Component.literal("WebhookIntegrations Forge: /wi status | reload | enable | disable | seturl <target> <url> | send <target> <message> | template <name> <target>"), false);
                return 1;
            })
            .then(Commands.literal("status").executes(context -> {
                String status = config == null ? "configuration failed to load" : "enabled=" + config.enabled + ", configured targets=" + config.webhooks.values().stream().filter(url -> !url.isBlank()).count();
                context.getSource().sendSuccess(() -> Component.literal("WebhookIntegrations Forge 1.20.1: " + status), false);
                return 1;
            }))
            .then(Commands.literal("reload").executes(context -> {
                try {
                    var replacement = WebhookConfig.load(configPath);
                    config = replacement;
                    context.getSource().sendSuccess(() -> Component.literal("Webhook configuration reloaded."), false);
                    return 1;
                } catch (Exception e) {
                    context.getSource().sendFailure(Component.literal("Invalid configuration; previous settings retained. Check config/webhookintegrations.json."));
                    return 0;
                }
            }))
            .then(Commands.literal("enable").executes(context -> toggle(context.getSource(), true)))
            .then(Commands.literal("disable").executes(context -> toggle(context.getSource(), false)))
            .then(Commands.literal("seturl").then(Commands.argument("target", StringArgumentType.word())
                .then(Commands.argument("url", StringArgumentType.greedyString()).executes(context -> {
                    if (config == null) return 0;
                    String url = StringArgumentType.getString(context, "url");
                    String target = StringArgumentType.getString(context, "target");
                    try {
                        WebhookConfig.validateUrl(url);
                        var replacement = WebhookConfig.GSON.fromJson(WebhookConfig.GSON.toJson(config), WebhookConfig.class);
                        replacement.webhooks.put(target, url);
                        replacement.save(configPath);
                        config = replacement;
                        context.getSource().sendSuccess(() -> Component.literal("Webhook target saved."), false);
                        return 1;
                    } catch (Exception e) {
                        context.getSource().sendFailure(Component.literal("Could not save target. Use a valid HTTP(S) URL and check config file permissions."));
                        return 0;
                    }
                }))))
            .then(Commands.literal("send").then(Commands.argument("target", StringArgumentType.word())
                .then(Commands.argument("message", StringArgumentType.greedyString()).executes(context -> {
                    JsonObject message = new JsonObject();
                    message.addProperty("content", StringArgumentType.getString(context, "message"));
                    return manual(context.getSource(), StringArgumentType.getString(context, "target"), message, false);
                }))))
            .then(Commands.literal("template").then(Commands.argument("name", StringArgumentType.word())
                .then(Commands.argument("target", StringArgumentType.word()).executes(context -> {
                    JsonObject template = config == null ? null : config.templates.get(StringArgumentType.getString(context, "name"));
                    return manual(context.getSource(), StringArgumentType.getString(context, "target"), template, true);
                })))));
        dispatcher.register(Commands.literal("webhookintegrations").requires(source -> source.hasPermission(2)).redirect(root));
    }

    private int toggle(net.minecraft.commands.CommandSourceStack source, boolean enabled) {
        if (config == null) return 0;
        boolean old = config.enabled;
        config.enabled = enabled;
        try { config.save(configPath); }
        catch (Exception e) { config.enabled = old; source.sendFailure(Component.literal("Could not save configuration.")); return 0; }
        source.sendSuccess(() -> Component.literal("Webhooks " + (enabled ? "enabled." : "disabled.")), false);
        return 1;
    }

    private int manual(net.minecraft.commands.CommandSourceStack source, String target, JsonObject message, boolean placeholders) {
        if (config == null || !config.enabled || sender == null || server == null || message == null ||
                !sender.send(config.webhooks.get(target), MessageRenderer.payload(message, placeholders ? values(source.getEntity() instanceof ServerPlayer p ? p : null) : Map.of()))) {
            source.sendFailure(Component.literal("Message not queued. Check enabled state, target URL, template name, and queue capacity."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Webhook message queued."), false);
        return 1;
    }
}
