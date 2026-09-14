# Forge 1.20.1

Install `WebhookIntegrations-Forge-1.20.1-<version>.jar` in the server's `mods/` directory. Use Minecraft **1.20.1**, Forge **47.4.0 or later in the 47.x series**, and **Java 17**. Clients do not need this mod. Do not install the Paper JAR on Forge.

Start the server once to generate `config/webhookintegrations.json`, set `webhooks.main` to your Discord webhook URL, then run `/wi reload`. Treat webhook URLs as secrets; editing the file avoids entering them in command logs. Invalid configuration on startup disables delivery until a successful reload; invalid reloads retain the previous working settings.

## Features

The Forge edition supports server start/stop, player join/leave, chat, death, advancement, and optional player count announcements. Cancelled chat/death events are ignored. Kicks appear as leaves because Forge has no matching Bukkit kick event. Player count messages default to disabled.

Each entry in `events` has `announce`, `target`, `requireOperator`, and a `message` JSON object. Use any Discord webhook JSON structure, including embeds. Multiple named destinations are supported under `webhooks`. HTTP delivery uses a bounded asynchronous queue, request timeouts, and up to three attempts for transient failures; shutdown allows 15 seconds to drain the queue. Delivery is best effort, not a durable outbox.

String values inside message JSON support `$player$`, `$rawUsername$`, `$uuid$`, `$worldName$`, `$time$`, `$timestamp$`, `$playersOnline$`, `$maxPlayers$`, `$serverIp$`, `$serverMotd$`, `$serverName$`, `$serverVersion$`, and `$isOnlineMode$`. Player placeholders apply only to player events. Chat adds `$message$`; death adds `$deathMessage$`, `$oldLevel$`, `$oldExp$`, and `$killer$`/`$killerUuid$` for player kills; advancements add `$advancement$` and `$desc$`. Time uses the server's local clock; timestamps use UTC. `serverName` is `Minecraft Server`.

Placeholder replacement preserves JSON escaping and does not expand placeholders inside player messages. Discord mentions are disabled in generated payloads. `censoring`, `removeColorCoding`, `preventUsernameMarkdownFormatting`, and `preventMessageMarkdownFormatting` control player text handling.

## Commands

All commands require operator permission level 2 (or server console). `/webhookintegrations` aliases `/wi`.

- `/wi status`: show enabled state and the number of configured destinations without exposing URLs.
- `/wi reload`: load configuration from disk.
- `/wi enable` and `/wi disable`: persist the global delivery switch.
- `/wi seturl <target> <url>`: save a destination URL.
- `/wi send <target> <message>`: queue a plain text message.
- `/wi template <name> <target>`: send a JSON object from `templates`, expanding built-in placeholders for the command source.

For example, add `"welcome": {"content": "Welcome to $serverMotd$!"}` inside `templates`, then run `/wi template welcome main`.

## Differences from Paper

Forge uses its own JSON configuration; Paper YAML files are not automatically imported. Copy webhook targets and adapt event JSON manually. Bukkit permissions, PlaceholderAPI, LightweightClans, vanished-player metadata, join/leave debounce, YAML globals/parameterized templates, custom HTTP headers, language files, and the automatic JAR updater are Paper features. `requireOperator` provides a per-event operator restriction in Forge. Download Forge updates from GitHub and replace the old JAR while the server is stopped.

The existing LightweightClans dependency exposes Bukkit services/events and cannot load on Forge. The clans webhook bridge remains available in the Paper edition.

## Build

With JDK 17 selected, run `cd forge` then `./gradlew build` (Windows: `gradlew.bat build`). Release builds pass `-PmodVersion=<version>`; local builds read the base version from the root `pom.xml`. The reobfuscated installable JAR is under `forge/build/libs/`.

Paper is built separately from the repository root with JDK 25 and Maven. CI tests/builds both and publishes both assets in the same GitHub release. Root `filename`, `downloadurl`, and `buildnumber` remain Paper-compatible for installed auto-updaters; `releases/paper/` and `releases/forge/` provide explicit platform metadata.

CI also installs a disposable dedicated Forge server and runs `forge/scripts/smoke_test.py` against the packaged JAR. This verifies loading, start/stop delivery, admin commands, templates, enable/disable, and invalid reload recovery against a local HTTP receiver. Unit tests cover JSON safety, configuration, and HTTP retry behavior. Player-triggered events still need multiplayer gameplay testing.
