/*
 * WebhookIntegrations
 * Copyright (C) 2023 rudynakodach
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package rudynakodach.github.io.webhookintegrations.Commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rudynakodach.github.io.webhookintegrations.AutoUpdater;
import rudynakodach.github.io.webhookintegrations.Clans.LightweightClansBridge;
import rudynakodach.github.io.webhookintegrations.Modules.*;
import rudynakodach.github.io.webhookintegrations.Utils.Config.ConfigBackupManager;
import rudynakodach.github.io.webhookintegrations.WebhookActions;
import rudynakodach.github.io.webhookintegrations.WebhookIntegrations;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class WIActions implements CommandExecutor, TabCompleter {
    JavaPlugin plugin;
    public WIActions(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if(command.getName().equalsIgnoreCase("wi")) {
            if (args.length >= 1) {
                if (args[0].equalsIgnoreCase("reset")) {
                    if(args.length < 2) {
                        commandSender.sendMessage("/wi reset confirm");
                        return true;
                    }

                    if (args[1].equalsIgnoreCase("confirm")) {
                        return resetConfig(commandSender);
                    } else {
                        if(!commandSender.hasPermission("webhookintegrations.config.reset")) {
                            commandSender.sendMessage(
                                    ChatColor.translateAlternateColorCodes('&',
                                            LanguageConfiguration.get().getLocalizedString("no-permission"))
                            );
                            return true;
                        }
                        commandSender.sendMessage(ChatColor.translateAlternateColorCodes('&', LanguageConfiguration.get().getLocalizedString("commands.config.noConfirm")));
                        return true;
                    }
                } else if(args[0].equalsIgnoreCase("help")) {
                    if(commandSender instanceof Player player) {
                        player.sendMessage(Component.text("View the official guide ").append(Component.text("here").decorate(TextDecoration.BOLD).decorate(TextDecoration.UNDERLINED).clickEvent(ClickEvent.openUrl("https://github.com/rudynakodach/WebhookIntegrations/blob/master/docs/guide.md"))).append(Component.text(" [CLICK]").color(NamedTextColor.GRAY)));
                    } else {
                        commandSender.sendMessage("View the official guide here: https://github.com/rudynakodach/WebhookIntegrations/blob/master/docs/guide.md");
                    }
                } else if (args[0].equalsIgnoreCase("reload")) {
                    return reload(commandSender);
                } else if (args[0].equalsIgnoreCase("update")) {
                    return update(commandSender);
                } else if(args[0].equalsIgnoreCase("enable")) {
                    return enable(commandSender);
                } else if(args[0].equalsIgnoreCase("disable")) {
                    return disable(commandSender);
                } else if(args[0].equalsIgnoreCase("setlanguage")) {
                    return setLanguage(commandSender, args);
                } else if(args[0].equalsIgnoreCase("config")) {
                    if(args.length < 2) {
                        commandSender.sendMessage("/wi config setvalue|savebackup|loadbackup");
                        return true;
                    }
                    if(args[1].equalsIgnoreCase("setvalue") && args.length >= 3) {
                        return setConfig(commandSender, args);
                    } else if(args[1].equalsIgnoreCase("savebackup")) {
                        return saveBackup(commandSender, args);
                    } else if(args[1].equalsIgnoreCase("loadbackup") && args.length >= 3) {
                        return loadBackup(commandSender, args);
                    }
                } else if(args[0].equalsIgnoreCase("clans")) {
                    if(args.length < 2) {
                        commandSender.sendMessage("/wi clans status|sync");
                        return true;
                    }
                    if(args[1].equalsIgnoreCase("status")) {
                        return clansStatus(commandSender);
                    } else if(args[1].equalsIgnoreCase("sync")) {
                        return clansSync(commandSender);
                    }
                }
                if(args.length >= 2) {
                    if(args[0].equalsIgnoreCase("template") && args[1].equalsIgnoreCase("send")) {
                        return sendTemplate(commandSender, args);
                    }
                }
            }
            commandSender.sendMessage("This server is running WebhookIntegrations");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("wi")) {
            if (args.length == 1) {
                suggestions.add("help");
                suggestions.add("setlanguage");
                suggestions.add("reset");
                suggestions.add("enable");
                suggestions.add("disable");
                suggestions.add("reload");
                suggestions.add("update");
                suggestions.add("config");
                suggestions.add("clans");
                suggestions.add("template");
            } else if (args.length == 2) {
                if(args[0].equalsIgnoreCase("reset")) {
                    suggestions.add("confirm");
                } else if(args[0].equalsIgnoreCase("setlanguage")) {
                    return LanguageConfiguration.get().getYamlConfig().getKeys(false).stream().toList();
                } else if(args[0].equalsIgnoreCase("config")) {
                    suggestions.add("setvalue");
                    suggestions.add("savebackup");
                    suggestions.add("loadbackup");
                } else if(args[0].equalsIgnoreCase("template")) {
                    suggestions.add("send");
                } else if(args[0].equalsIgnoreCase("clans")) {
                    suggestions.add("status");
                    suggestions.add("sync");
                }
            } else if(args.length == 3) {
                if(args[0].equalsIgnoreCase("config")) {
                    if (args[1].equalsIgnoreCase("setvalue")) {
                        return plugin.getConfig().getKeys(true).stream().toList();
                    } else if (args[1].equalsIgnoreCase("loadbackup")) {
                        File configBackupsDirectory = new File(plugin.getDataFolder(), "config-backups");
                        File[] backups = configBackupsDirectory.listFiles();
                        if (backups == null) {
                            return suggestions;
                        }

                        // Returns a list of all filenames present in the config-backups directory.
                        return Arrays.stream(backups)
                                .map(File::getName)
                                .collect(Collectors.toList());
                    }
                } else if(args[0].equalsIgnoreCase("template") && args[1].equalsIgnoreCase("send")) {
                    return new ArrayList<>(TemplateConfiguration.get().getYamlConfig().getConfigurationSection("templates").getKeys(false));
                }

            } else if(args.length == 4) {
                if(args[0].equalsIgnoreCase("config") && args[1].equalsIgnoreCase("setvalue")) {
                    if(!plugin.getConfig().contains(args[3])) {return null;}
                    Object value = plugin.getConfig().get(args[3]);
                    if(value instanceof Boolean) {
                        suggestions.add("true");
                        suggestions.add("false");
                        return suggestions;
                    }
                }
            }
        }
        return suggestions;
    }

    @Contract(pure = true)
    private @NotNull String colorBoolean(Boolean b) {
        return (b ? ChatColor.GREEN : ChatColor.RED) + String.valueOf(b) + ChatColor.RESET;
    }

    private boolean resetConfig(CommandSender commandSender) {
        if (!commandSender.hasPermission("webhookintegrations.config.reset")) {
            commandSender.sendMessage(
                    ChatColor.translateAlternateColorCodes('&',
                            LanguageConfiguration.get().getLocalizedString("no-permission"))
            );
            return true;
        }

        WebhookIntegrationsModule.resetAll();

        return true;
    }

    private boolean clansStatus(CommandSender commandSender) {
        if (!commandSender.hasPermission("webhookintegrations.clans.status")) {
            commandSender.sendMessage(
                    ChatColor.translateAlternateColorCodes('&',
                            LanguageConfiguration.get().getLocalizedString("no-permission"))
            );
            return true;
        }

        if (!(plugin instanceof WebhookIntegrations webhookIntegrations)) {
            commandSender.sendMessage(ChatColor.RED + "Lightweight Clans bridge status is unavailable.");
            return true;
        }

        LightweightClansBridge.BridgeStatus status = webhookIntegrations.describeLightweightClansBridge();
        String periodicStatus = status.periodicFullSyncEnabled()
                ? ChatColor.GREEN + "every " + status.periodicFullSyncSeconds() + " seconds"
                : ChatColor.RED + "disabled";

        commandSender.sendMessage(ChatColor.GOLD + "Lightweight Clans bridge status:");
        commandSender.sendMessage(ChatColor.GRAY + "- Global webhook switch: " + colorBoolean(status.masterEnabled()));
        commandSender.sendMessage(ChatColor.GRAY + "- Clans bridge enabled: " + colorBoolean(status.clansWebhookEnabled()));
        commandSender.sendMessage(ChatColor.GRAY + "- Endpoint configured: " + colorBoolean(status.endpointConfigured()));
        commandSender.sendMessage(ChatColor.GRAY + "- LightweightClans API available: " + colorBoolean(status.apiAvailable()));
        commandSender.sendMessage(ChatColor.GRAY + "- Bridge active: " + colorBoolean(status.active()));
        commandSender.sendMessage(ChatColor.GRAY + "- Startup full sync: " + colorBoolean(status.fullSyncOnStartup()));
        commandSender.sendMessage(ChatColor.GRAY + "- Periodic full sync: " + periodicStatus + ChatColor.RESET);
        return true;
    }

    private boolean clansSync(CommandSender commandSender) {
        if (!commandSender.hasPermission("webhookintegrations.clans.sync")) {
            commandSender.sendMessage(
                    ChatColor.translateAlternateColorCodes('&',
                            LanguageConfiguration.get().getLocalizedString("no-permission"))
            );
            return true;
        }

        if (!(plugin instanceof WebhookIntegrations webhookIntegrations)) {
            commandSender.sendMessage(ChatColor.RED + "Lightweight Clans bridge sync is unavailable.");
            return true;
        }

        switch (webhookIntegrations.queueLightweightClansManualSync()) {
            case QUEUED -> commandSender.sendMessage(ChatColor.GREEN + "Queued a manual Lightweight Clans full sync. Check the console for sync and delivery logs.");
            case ALREADY_RUNNING -> commandSender.sendMessage(ChatColor.YELLOW + "A Lightweight Clans full sync is already running.");
            case GLOBALLY_DISABLED -> commandSender.sendMessage(ChatColor.RED + "WebhookIntegrations is globally disabled (isEnabled=false).");
            case CLANS_WEBHOOK_DISABLED -> commandSender.sendMessage(ChatColor.RED + "The clans bridge is disabled (clansWebhook.enabled=false).");
            case MISSING_ENDPOINT -> commandSender.sendMessage(ChatColor.RED + "The clans bridge endpoint is blank. Set clansWebhook.endpoint first.");
            case API_UNAVAILABLE -> commandSender.sendMessage(ChatColor.RED + "The LightweightClans API is not available. Check plugin load order and startup logs.");
        }

        return true;
    }

    private boolean reload(CommandSender commandSender) {
        if (!commandSender.hasPermission("webhookintegrations.reload")) {
            commandSender.sendMessage(
                    ChatColor.translateAlternateColorCodes('&',
                            LanguageConfiguration.get().getLocalizedString("no-permission"))
            );
            return true;
        }

        plugin.reloadConfig();
        WebhookIntegrationsModule.reloadAll();
        if (plugin instanceof WebhookIntegrations webhookIntegrations) {
            webhookIntegrations.reloadLightweightClansBridge();
        }

        commandSender.sendMessage(ChatColor.translateAlternateColorCodes('&', LanguageConfiguration.get().getLocalizedString("commands.config.reloadFinish")));
        return true;
    }

    private boolean update(CommandSender commandSender) {
        if(!commandSender.hasPermission("webhookintegrations.update")) {
            commandSender.sendMessage(
                    ChatColor.translateAlternateColorCodes('&',
                            LanguageConfiguration.get().getLocalizedString("no-permission"))
            );
            return true;
        }

        AutoUpdater updater = new AutoUpdater(plugin);

        try {
            int latestVersion = updater.getLatestVersion();
            if (latestVersion > WebhookIntegrations.currentBuildNumber) {
                boolean success = updater.Update();
                if (success) {
                    commandSender.sendMessage(LanguageConfiguration.get().getLocalizedString("commands.update.success"));
                } else {
                    commandSender.sendMessage(LanguageConfiguration.get().getLocalizedString("commands.update.failed"));
                }
            } else {
                if(latestVersion == -1) {
                    commandSender.sendMessage(LanguageConfiguration.get().getLocalizedString("commands.update.versionCheckFailed"));
                } else {
                    commandSender.sendMessage(LanguageConfiguration.get().getLocalizedString("commands.update.latest"));
                }
            }
        } catch (IOException ignored) {
            commandSender.sendMessage(LanguageConfiguration.get().getLocalizedString("commands.update.failed"));
        }
        return true;
    }

    private boolean enable(CommandSender commandSender) {
        if(!commandSender.hasPermission("webhookintegrations.enable")) {
            commandSender.sendMessage(
                    ChatColor.translateAlternateColorCodes('&',
                            LanguageConfiguration.get().getLocalizedString("no-permission"))
            );
            return true;
        }

        plugin.getConfig().set("isEnabled", true);
        plugin.saveConfig();
        plugin.reloadConfig();
        if (plugin instanceof WebhookIntegrations webhookIntegrations) {
            webhookIntegrations.reloadLightweightClansBridge();
        }

        commandSender.sendMessage(LanguageConfiguration.get().getLocalizedString("commands.enable"));

        return true;
    }

    private boolean disable(CommandSender commandSender) {
        if(!commandSender.hasPermission("webhookintegrations.disable")) {
            commandSender.sendMessage(
                    ChatColor.translateAlternateColorCodes('&',
                            LanguageConfiguration.get().getLocalizedString("no-permission"))
            );
            return true;
        }

        plugin.getConfig().set("isEnabled", false);
        plugin.saveConfig();
        plugin.reloadConfig();
        if (plugin instanceof WebhookIntegrations webhookIntegrations) {
            webhookIntegrations.reloadLightweightClansBridge();
        }

        commandSender.sendMessage(LanguageConfiguration.get().getLocalizedString("commands.disable"));

        return true;
    }

    private boolean setLanguage(CommandSender commandSender, String[] args) {
        if(!commandSender.hasPermission("webhookintegrations.setlanguage")) {
            commandSender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                LanguageConfiguration.get().getLocalizedString("no-permission")));
            return true;
        }

        if(args.length < 2) {
            commandSender.sendMessage("/wi setlanguage lang");
            return true;
        }

        String newLang = args[1];
        if(LanguageConfiguration.get().getYamlConfig().contains(newLang)) {
            plugin.getConfig().set("language-override", newLang);
            plugin.reloadConfig();
            LanguageConfiguration.get().setLanguage(newLang);
            commandSender.sendMessage(ChatColor.translateAlternateColorCodes('&', LanguageConfiguration.get().getLocalizedString("commands.setLang.changed")));
        } else {
            commandSender.sendMessage(ChatColor.translateAlternateColorCodes('&', LanguageConfiguration.get().getLocalizedString("commands.setLang.notExists")));
        }
        return true;
    }

    private boolean setConfig(CommandSender commandSender, String[] args) {
        if(!commandSender.hasPermission("webhookintegrations.config.setvalue")) {
            commandSender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    LanguageConfiguration.get().getLocalizedString("no-permission")));
            return true;
        }
        String path = args[2];
        Object value = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : null;
        String message;
        Object oldValue = null;

        if(plugin.getConfig().contains(path)) {
            oldValue = plugin.getConfig().get(path);
            message = LanguageConfiguration.get().getLocalizedString("commands.config.keyEdited");
        } else {
            message = LanguageConfiguration.get().getLocalizedString("commands.config.keyCreated");
        }

        if(value == null) {
            message = LanguageConfiguration.get().getLocalizedString("commands.config.keyRemoved");
        } else {
            if (value.toString().equalsIgnoreCase("true")) {
                value = true;
            } else if (value.toString().equalsIgnoreCase("false")) {
                value = false;
            } else {
                try {
                    value = Integer.parseInt(value.toString());
                } catch (NumberFormatException ignored) {}
            }
        }

        plugin.getConfig().set(path, value);
        try {
            plugin.getConfig().save(new File(plugin.getDataFolder(), "config.yml"));
        } catch (Exception e) {
            commandSender.sendMessage(LanguageConfiguration.get().getLocalizedString("commands.config.saveFailed").replace("%04", e.getMessage()));
            return true;
        }

        message = message.replace("%01", path)
                .replace("%02", value instanceof String ?
                        String.format("\"%s\"", value) :
                        String.valueOf(value));

        if(oldValue != null) {
            message = message.replace("%03", oldValue.toString());
        }

        commandSender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        return true;
    }

    private boolean saveBackup(CommandSender commandSender, String[] args) {
        if(!commandSender.hasPermission("webhookintegrations.config.savebackup")) {
            commandSender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    LanguageConfiguration.get().getLocalizedString("no-permission")));
            return true;
        }
        // Saves the backup as the provided name if possible, current unix time otherwise.
        String backupName = args.length >= 3 ? String.join("_", Arrays.copyOfRange(args, 2, args.length)) :
                new SimpleDateFormat("yyyy-MM-dd_hh-mm-ss-SSS").format(new Date());

        if(!ConfigBackupManager.get().saveBackup(backupName)) {
            commandSender.sendMessage("Failed to create backup");
        } else {
            String message = LanguageConfiguration.get().getLocalizedString("commands.config.backupCreated");
            message = message.replace("%01", backupName);
            message = ChatColor.translateAlternateColorCodes('&', message);

            commandSender.sendMessage(message);
        }

        return true;
    }

    private boolean loadBackup(CommandSender commandSender, String[] args) {
        if(!commandSender.hasPermission("webhookintegrations.config.loadbackup")) {
            commandSender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    LanguageConfiguration.get().getLocalizedString("no-permission")));
            return true;
        }

        String backupName = args[2];

        if(!ConfigBackupManager.get().backupExists(backupName)) {
            commandSender.sendMessage(Component.text("Folder doesn't exist.").color(NamedTextColor.RED));
            return true;
        }

        ConfigBackupManager.get().loadBackup(backupName);

        String message = LanguageConfiguration.get().getLocalizedString("commands.config.backupLoaded");
        message = message.replace("%01", backupName);
        message = ChatColor.translateAlternateColorCodes('&', message);

        commandSender.sendMessage(message);

        return true;
    }

    private boolean sendTemplate(CommandSender sender, String[] args) {

        if (!sender.hasPermission("webhookintegrations.templates.send")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    LanguageConfiguration.get().getLocalizedString("no-permission")));
            return true;
        }

        String templateName = args[2];

        if (!TemplateConfiguration.Template.templateExists(templateName)) {
            sender.sendMessage(
                    ChatColor.translateAlternateColorCodes('&', LanguageConfiguration.get().getLocalizedString("commands.templates.notFound"))
            );
            return true;
        }

        if (!sender.hasPermission("webhookintegrations.templates.send.%s".formatted(templateName)) ||
                !sender.hasPermission("webhookintegrations.templates.send.any")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', LanguageConfiguration.get().getLocalizedString("commands.templates.noAccess")));
            return true;
        }

        TemplateConfiguration.Template template = TemplateConfiguration.get().getTemplate(templateName);

        if (template == null || template.getJson() == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', LanguageConfiguration.get().getLocalizedString("commands.templates.noJson")));
            return true;
        }

        int argsOffset = 0;

        if(args.length >= 4) {
            if (!args[3].startsWith("--")) {
                argsOffset = 1;
                template.setTarget(args[3]);
            }
        }

        HashMap<String, String> params = new HashMap<>();

        if(args.length >= 4 + argsOffset) {
            String[] commandArgs = Arrays.copyOfRange(args, 3 + argsOffset, args.length);
            String argsString = String.join(" ", commandArgs);

            Pattern argsPatters = Pattern.compile("--([a-zA-Z0-9]+) \"((?:[^\"\\\\]|\\\\.)+)\"");
            Matcher matcher = argsPatters.matcher(argsString);

            while (matcher.find()) {
                params.put(matcher.group(1), matcher.group(2));
            }
        }

        new WebhookActions(template.setJson(template.compile(params))).SendAsync();

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', LanguageConfiguration.get().getLocalizedString("commands.templates.success")));

        return true;
    }
}
