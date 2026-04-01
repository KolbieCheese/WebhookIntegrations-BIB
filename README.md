# WebhookIntegrations
The simplest solution for Discord Webhook integration with your Minecraft server.

<p align="center">
    <img src="https://github.com/rudynakodach/WebhookIntegrations/blob/master/images/helloworld.png?raw=true" alt="Chat message example"/>
</p>

[![License](https://img.shields.io/github/license/rudynakodach/WebhookIntegrations?style=for-the-badge)](https://img.shields.io/github/license/rudynakodach/WebhookIntegrations)

[![Servers](https://img.shields.io/bstats/servers/18509?style=for-the-badge)](https://bstats.org/plugin/bukkit/WebhookIntegrations/18509) 
[![Players](https://img.shields.io/bstats/players/18509?style=for-the-badge)](https://bstats.org/plugin/bukkit/WebhookIntegrations/18509)
![Downloads](https://img.shields.io/github/downloads/rudynakodach/WebhookIntegrations/total?style=for-the-badge)

## Need help?
**Read the official guide [here](docs/guide.md)**

# Download
Get the plugin from [SpigotMC](https://www.spigotmc.org/resources/webhookintegrations-1-17-1-19-pl-en.107688/) or [CurseForge](https://curseforge.com/minecraft/bukkit-plugins/webhookintegrations)

### ⚠️ Note: This plugin no longer supports legacy Minecraft versions. Use ViaBackwards, ViaVersion or ViaRewind to allow older clients to connect to your server. ⚠️
# Features

- Multi-webhook support
- Highly configurable JSON messages with placeholders
- PlaceholderAPI implementation
- Lightweight Clans webhook bridge with startup sync and lifecycle event forwarding
- configurable permission system
- chat logging
- configurable censoring system
- system start and stop message logging 
- \+ many more

#### Like the plugin? Consider leaving a review on [Spigot](https://www.spigotmc.org/resources/1-17-webhookintegrations-simplifying-discord-integrations.107688/)!

## Lightweight Clans bridge

WebhookIntegrations can now forward Lightweight Clans snapshots to a regular JSON webhook endpoint.

- Dependency: install `LightweightClans` on the same Paper server; it is not bundled into the WebhookIntegrations jar
- Discovery: the bridge resolves `LightweightClansApi` through Bukkit `ServicesManager`
- Startup sync: `clan.sync`
- Periodic full sync: `clan.sync` every `clansWebhook.periodicFullSyncSeconds` seconds when enabled
- Lifecycle events:
  - `clan.created`
  - `clan.updated`
  - `clan.deleted`
  - `clan.member_joined`
  - `clan.member_left`
  - `clan.member_kicked`
  - `clan.president_transferred`
  - `clan.banner_updated`

Example `config.yml` section:

```yml
clansWebhook:
  enabled: true
  endpoint: "https://example.com/api/clans-webhook"
  secret: "replace-me"
  fullSyncOnStartup: true
  periodicFullSyncSeconds: 60
  includeMembers: true
  includeBanner: true
  connectTimeoutMillis: 5000
  readTimeoutMillis: 5000
  retryAttempts: 5
  retryDelaySeconds: 30
```

Every request is sent as `application/json` with these headers:

- `X-Webhook-Source: lightweight-clans`
- `X-Webhook-Event: <event name>`
- `X-Webhook-Timestamp: <same ISO-8601 timestamp used in the payload>`
- `X-Webhook-Signature: sha256=<hmac>`

The signature payload is:

```text
timestamp + "." + rawRequestBody
```

Non-delete payload example:

```json
{
  "event": "clan.updated",
  "occurredAt": "2026-03-31T21:10:15Z",
  "changedFields": ["banner", "memberCount"],
  "clan": {
    "id": 42,
    "name": "Crimson Knights",
    "normalizedName": "crimson knights",
    "tag": "CK",
    "tagColor": "#ffaa00",
    "description": "PvP and building clan.",
    "presidentUuid": "11111111-1111-1111-1111-111111111111",
    "presidentName": "Kolbie",
    "memberCount": 12,
    "members": [
      {
        "playerUuid": "11111111-1111-1111-1111-111111111111",
        "lastKnownName": "Kolbie",
        "role": "PRESIDENT",
        "joinedAt": "2026-03-31T19:20:00Z"
      }
    ],
    "banner": {
      "baseMaterial": "minecraft:black_banner",
      "baseColor": "black",
      "patterns": [
        { "patternId": "minecraft:border", "colorId": "red" },
        { "patternId": "minecraft:stripe_center", "colorId": "white" }
      ]
    },
    "createdAt": "2026-03-31T19:15:30Z",
    "updatedAt": "2026-03-31T21:10:15Z"
  }
}
```

Delete payload example:

```json
{
  "event": "clan.deleted",
  "occurredAt": "2026-03-31T21:12:00Z",
  "clan": {
    "id": 42,
    "name": "Crimson Knights",
    "normalizedName": "crimson knights"
  }
}
```
