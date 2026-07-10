# WebhookIntegrations
The simplest solution for Discord Webhook integration with your Minecraft server.

<p align="center">
    <img src="https://github.com/rudynakodach/WebhookIntegrations/blob/master/images/helloworld.png?raw=true" alt="Chat message example"/>
</p>

[![License](https://img.shields.io/github/license/rudynakodach/WebhookIntegrations?style=for-the-badge)](https://img.shields.io/github/license/rudynakodach/WebhookIntegrations)

[![Servers](https://img.shields.io/bstats/servers/18509?style=for-the-badge)](https://bstats.org/plugin/bukkit/WebhookIntegrations/18509) 
[![Players](https://img.shields.io/bstats/players/18509?style=for-the-badge)](https://bstats.org/plugin/bukkit/WebhookIntegrations/18509)
![Downloads](https://img.shields.io/github/downloads/rudynakodach/WebhookIntegrations/total?style=for-the-badge)

## Attribution

This repository is a custom Beauty in Blocks build of [rudynakodach/WebhookIntegrations](https://github.com/rudynakodach/WebhookIntegrations). The original WebhookIntegrations plugin was created and maintained by [rudynakodach](https://github.com/rudynakodach); this project builds on that work.

## Need help?
**Read the official guide [here](docs/guide.md)**

# Download
Get the plugin from [SpigotMC](https://www.spigotmc.org/resources/webhookintegrations-1-17-1-19-pl-en.107688/) or [CurseForge](https://curseforge.com/minecraft/bukkit-plugins/webhookintegrations)

### ⚠️ Note: This plugin no longer supports legacy Minecraft versions. Use ViaBackwards, ViaVersion or ViaRewind to allow older clients to connect to your server. ⚠️
## Compatibility

This custom build targets Paper `26.2` and is compiled against Paper API `26.2.build.48-alpha`. Paper `26.2` requires Java 25 or newer, so GitHub release builds use Java 25.

# Features

- Multi-webhook support
- Highly configurable JSON messages with placeholders
- PlaceholderAPI implementation
- Lightweight Clans JSON webhook bridge with authoritative snapshots, lifecycle event forwarding, and manual sync commands
- configurable permission system
- chat logging
- configurable censoring system
- system start and stop message logging 
- \+ many more

#### Like the plugin? Consider leaving a review on [Spigot](https://www.spigotmc.org/resources/1-17-webhookintegrations-simplifying-discord-integrations.107688/)!

## Lightweight Clans bridge

This fork adds an optional JSON webhook bridge for `LightweightClans`. It is separate from the normal Discord webhook targets under `webhooks.*`: set `clansWebhook.endpoint` to a receiver that understands signed JSON events, such as a website API. The bridge does not bundle `LightweightClans`; install that plugin on the same Paper server.

The bridge activates only when all of these are true:

- `isEnabled: true`
- `clansWebhook.enabled: true`
- `clansWebhook.endpoint` is not blank
- `LightweightClansApi` is registered in Bukkit `ServicesManager`

If those requirements are not met, only the clans bridge is skipped. The rest of WebhookIntegrations can keep running.

### Setup

1. Install this WebhookIntegrations build and `LightweightClans` on the same Paper server.
2. Start the server once so `plugins/WebhookIntegrations/config.yml` exists.
3. Set `clansWebhook.enabled` to `true`.
4. Set `clansWebhook.endpoint` to your JSON receiver URL. This is not a Discord webhook URL.
5. Set `clansWebhook.secret` to the same shared secret your receiver uses to verify `X-Webhook-Signature`.
6. Restart the server or run `/wi reload`.
7. Run `/wi clans status` to confirm the bridge is active.
8. Run `/wi clans sync` when you want to push a fresh authoritative snapshot immediately.

Example `config.yml` section:

```yml
clansWebhook:
  enabled: true
  endpoint: "https://example.com/api/clans-webhook"
  secret: "replace-me"
  fullSyncOnStartup: true
  periodicFullSyncEnabled: false
  periodicFullSyncSeconds: 7200
  includeMembers: true
  includeBanner: true
  connectTimeoutMillis: 5000
  readTimeoutMillis: 5000
  retryAttempts: 5
  retryDelaySeconds: 30
```

### Sync behavior

- Startup sync: when `fullSyncOnStartup` is true, sends one authoritative `clan.snapshot`, then one `clan.sync` per current clan for backward compatibility.
- Manual sync: `/wi clans sync` queues the same full-sync flow immediately.
- Periodic sync: when `periodicFullSyncEnabled` is true, schedules the same full-sync flow every `periodicFullSyncSeconds`.
- Periodic interval guard: `0` disables the periodic schedule; positive values below `7200` seconds are raised to `7200`.
- Empty server reset: if the LightweightClans API reports no clans, the bridge still sends `clan.snapshot` with `"clans": []` so the receiver can clear stale state.

### Live events

The bridge listens for LightweightClans lifecycle events and sends these event names:

- `clan.snapshot`: authoritative replace-all snapshot from startup, manual, or periodic full sync
- `clan.sync`: per-clan compatibility upsert sent after each full snapshot
- `clan.created`
- `clan.updated`
- `clan.deleted`
- `clan.member_joined`
- `clan.member_left`
- `clan.member_kicked`
- `clan.president_transferred`
- `clan.banner_updated`

Snapshots and non-delete events include the current clan data. The `includeMembers` and `includeBanner` flags control whether `members` and `banner` appear in those clan objects. `clan.updated` may also include sorted `changedFields`. Delete events include only the deleted clan identity: `id`, `name`, and `normalizedName`.

### Commands

- `/wi clans status`: shows the global switch, bridge switch, endpoint status, API availability, active state, startup sync, and periodic sync settings. Requires `webhookintegrations.clans.status`, defaulting to ops.
- `/wi clans sync`: queues a manual full sync. Requires `webhookintegrations.clans.sync`, defaulting to ops.
- `/wi reload`: reloads regular WebhookIntegrations config and reapplies the clans bridge settings.

### Receiver contract

Every request is an HTTP `POST` with `application/json` and these headers:

- `Content-Type: application/json`
- `X-Webhook-Source: lightweight-clans`
- `X-Webhook-Event: <event name>`
- `X-Webhook-Timestamp: <ISO-8601 request timestamp used for signature freshness>`
- `X-Webhook-Signature: sha256=<hmac>`

Verify the signature with HMAC-SHA256 using `clansWebhook.secret` and this exact payload:

```text
timestamp + "." + rawRequestBody
```

Your receiver should return a `2xx` status after it accepts the event. The sender retries connection failures plus HTTP `408`, `429`, and `5xx` responses using `retryAttempts` and `retryDelaySeconds`; most other `4xx` responses are treated as final failures.

Recommended receiver behavior:

- Treat `clan.snapshot` as the authoritative replace-all state.
- Treat `clan.sync` and non-delete lifecycle events as upserts for one clan.
- Treat `clan.deleted` as a deletion by `id` or normalized identity.
- Preserve the raw request body until after signature verification.

Authoritative full-sync payload example:

```json
{
  "event": "clan.snapshot",
  "occurredAt": "2026-03-31T21:09:00Z",
  "clans": [
    {
      "id": 42,
      "name": "Crimson Knights",
      "normalizedName": "crimson knights",
      "tag": "CK",
      "tagColor": "#ffaa00",
      "description": "PvP and building clan.",
      "presidentUuid": "11111111-1111-1111-1111-111111111111",
      "presidentName": "Kolbie",
      "memberCount": 12,
      "members": [],
      "banner": null,
      "createdAt": "2026-03-31T19:15:30Z",
      "updatedAt": "2026-03-31T21:09:00Z"
    }
  ]
}
```

Empty authoritative snapshot example:

```json
{
  "event": "clan.snapshot",
  "occurredAt": "2026-03-31T21:09:00Z",
  "clans": []
}
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
