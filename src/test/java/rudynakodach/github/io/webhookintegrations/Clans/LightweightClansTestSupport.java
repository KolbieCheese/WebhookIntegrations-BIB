package rudynakodach.github.io.webhookintegrations.Clans;

import io.github.maste.customclans.api.model.BannerPatternSnapshot;
import io.github.maste.customclans.api.model.ClanBannerSnapshot;
import io.github.maste.customclans.api.model.ClanMemberSnapshot;
import io.github.maste.customclans.api.model.ClanSnapshot;
import io.github.maste.customclans.models.ClanRole;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class LightweightClansTestSupport {
    static final UUID PRESIDENT_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID MEMBER_UUID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final Instant CREATED_AT = Instant.parse("2026-03-31T19:15:30Z");
    static final Instant UPDATED_AT = Instant.parse("2026-03-31T20:16:45Z");
    static final Instant JOINED_AT = Instant.parse("2026-03-31T19:20:00Z");

    private LightweightClansTestSupport() {
    }

    static ClanSnapshot clanSnapshot() {
        return clanSnapshot(true, 2);
    }

    static ClanSnapshot clanSnapshot(boolean includeBanner, int memberCount) {
        List<ClanMemberSnapshot> members = List.of(
                new ClanMemberSnapshot(PRESIDENT_UUID, "Kolbie", ClanRole.PRESIDENT, JOINED_AT),
                new ClanMemberSnapshot(MEMBER_UUID, "BuilderBob", ClanRole.MEMBER, JOINED_AT.plusSeconds(60))
        );

        ClanBannerSnapshot banner = includeBanner
                ? new ClanBannerSnapshot(
                "minecraft:black_banner",
                "black",
                List.of(
                        new BannerPatternSnapshot("minecraft:border", "red"),
                        new BannerPatternSnapshot("minecraft:stripe_center", "white")
                )
        )
                : null;

        return new ClanSnapshot(
                42L,
                "Crimson Knights",
                "crimson knights",
                "CK",
                "#ffaa00",
                "PvP and building clan.",
                PRESIDENT_UUID,
                "Kolbie",
                memberCount,
                members,
                banner,
                CREATED_AT,
                UPDATED_AT
        );
    }

    static ClanSnapshot renamedClanSnapshot() {
        return new ClanSnapshot(
                42L,
                "Crimson Empire",
                "crimson empire",
                "CE",
                "#ffaa00",
                "PvP and building clan.",
                PRESIDENT_UUID,
                "Kolbie",
                2,
                clanSnapshot().members(),
                clanSnapshot().banner(),
                CREATED_AT,
                UPDATED_AT.plusSeconds(300)
        );
    }

    static YamlConfiguration pluginConfig() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("isEnabled", true);
        return configuration;
    }

    static JavaPlugin pluginWithConfig(YamlConfiguration configuration) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getConfig()).thenReturn(configuration);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("LightweightClansTests"));
        return plugin;
    }

    static final class RecordingScheduler implements LightweightClansWebhookSender.AsyncTaskScheduler {
        private final Deque<Runnable> queuedTasks = new ArrayDeque<>();
        private final List<Long> delays = new ArrayList<>();

        @Override
        public void runAsync(Runnable task) {
            queuedTasks.add(task);
        }

        @Override
        public void runLaterAsync(Runnable task, long delayTicks) {
            delays.add(delayTicks);
            queuedTasks.add(task);
        }

        int queuedTaskCount() {
            return queuedTasks.size();
        }

        List<Long> delays() {
            return delays;
        }

        void runAll() {
            while (!queuedTasks.isEmpty()) {
                queuedTasks.removeFirst().run();
            }
        }
    }

    static final class RecordingTransport implements LightweightClansWebhookSender.HttpTransport {
        private final Deque<LightweightClansWebhookSender.DeliveryResult> scriptedResults = new ArrayDeque<>();
        private final List<Request> requests = new ArrayList<>();

        void addResult(LightweightClansWebhookSender.DeliveryResult result) {
            scriptedResults.addLast(result);
        }

        List<Request> requests() {
            return requests;
        }

        @Override
        public LightweightClansWebhookSender.DeliveryResult post(
                String endpoint,
                String body,
                Map<String, String> headers,
                ClansWebhookConfig config
        ) {
            requests.add(new Request(endpoint, body, new LinkedHashMap<>(headers)));

            if (!scriptedResults.isEmpty()) {
                return scriptedResults.removeFirst();
            }

            return LightweightClansWebhookSender.DeliveryResult.ok();
        }
    }

    record Request(String endpoint, String body, Map<String, String> headers) {
    }
}
