package rudynakodach.github.io.webhookintegrations.Clans;

import io.github.maste.customclans.api.event.ClanBannerUpdatedEvent;
import io.github.maste.customclans.api.event.ClanCreatedEvent;
import io.github.maste.customclans.api.event.ClanDeletedEvent;
import io.github.maste.customclans.api.event.ClanMemberJoinedEvent;
import io.github.maste.customclans.api.event.ClanMemberKickedEvent;
import io.github.maste.customclans.api.event.ClanMemberLeftEvent;
import io.github.maste.customclans.api.event.ClanPresidentTransferredEvent;
import io.github.maste.customclans.api.event.ClanUpdatedEvent;
import io.github.maste.customclans.api.model.ClanMemberSnapshot;
import io.github.maste.customclans.models.ClanRole;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class LightweightClansListenerTest {

    @Test
    void clanCreatedEventSendsClanCreatedPayload() {
        Fixture fixture = fixture(true, true);

        fixture.listener.onClanCreated(new ClanCreatedEvent(LightweightClansTestSupport.clanSnapshot()));

        assertEquals(0, fixture.transport.requests().size());
        fixture.scheduler.runAll();

        assertEquals(1, fixture.transport.requests().size());
        assertTrue(fixture.transport.requests().getFirst().body().contains("\"event\":\"clan.created\""));
        assertTrue(fixture.transport.requests().getFirst().body().contains("\"name\":\"Crimson Knights\""));
    }

    @Test
    void clanUpdatedEventSendsClanUpdatedPayload() {
        Fixture fixture = fixture(true, true);

        fixture.listener.onClanUpdated(new ClanUpdatedEvent(
                LightweightClansTestSupport.clanSnapshot(),
                LightweightClansTestSupport.renamedClanSnapshot(),
                Set.of("memberCount", "banner")
        ));
        fixture.scheduler.runAll();

        String body = fixture.transport.requests().getFirst().body();
        assertTrue(body.contains("\"event\":\"clan.updated\""));
        assertTrue(body.contains("\"changedFields\":[\"banner\",\"memberCount\"]"));
        assertTrue(body.contains("\"name\":\"Crimson Empire\""));
    }

    @Test
    void clanDeletedEventSendsDeletePayload() {
        Fixture fixture = fixture(true, true);

        fixture.listener.onClanDeleted(new ClanDeletedEvent(LightweightClansTestSupport.clanSnapshot()));
        fixture.scheduler.runAll();

        String body = fixture.transport.requests().getFirst().body();
        assertTrue(body.contains("\"event\":\"clan.deleted\""));
        assertTrue(body.contains("\"normalizedName\":\"crimson knights\""));
        assertFalse(body.contains("\"tag\":\"CK\""));
        assertFalse(body.contains("\"memberCount\":"));
    }

    @Test
    void memberJoinEventSendsExpectedPayload() {
        Fixture fixture = fixture(true, true);

        ClanMemberSnapshot joinedMember = new ClanMemberSnapshot(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "NewRecruit",
                ClanRole.MEMBER,
                LightweightClansTestSupport.JOINED_AT.plusSeconds(90)
        );

        fixture.listener.onClanMemberJoined(new ClanMemberJoinedEvent(
                LightweightClansTestSupport.clanSnapshot(),
                LightweightClansTestSupport.clanSnapshot(),
                joinedMember
        ));
        fixture.scheduler.runAll();

        assertTrue(fixture.transport.requests().getFirst().body().contains("\"event\":\"clan.member_joined\""));
    }

    @Test
    void memberLeftEventSendsExpectedPayload() {
        Fixture fixture = fixture(true, true);

        fixture.listener.onClanMemberLeft(new ClanMemberLeftEvent(
                LightweightClansTestSupport.clanSnapshot(),
                LightweightClansTestSupport.clanSnapshot(),
                LightweightClansTestSupport.clanSnapshot().members().get(1)
        ));
        fixture.scheduler.runAll();

        assertTrue(fixture.transport.requests().getFirst().body().contains("\"event\":\"clan.member_left\""));
    }

    @Test
    void memberKickedEventSendsExpectedPayload() {
        Fixture fixture = fixture(true, true);

        fixture.listener.onClanMemberKicked(new ClanMemberKickedEvent(
                LightweightClansTestSupport.clanSnapshot(),
                LightweightClansTestSupport.clanSnapshot(),
                LightweightClansTestSupport.clanSnapshot().members().get(1),
                LightweightClansTestSupport.clanSnapshot().members().getFirst()
        ));
        fixture.scheduler.runAll();

        assertTrue(fixture.transport.requests().getFirst().body().contains("\"event\":\"clan.member_kicked\""));
    }

    @Test
    void presidentTransferredEventSendsExpectedPayload() {
        Fixture fixture = fixture(true, true);

        fixture.listener.onClanPresidentTransferred(new ClanPresidentTransferredEvent(
                LightweightClansTestSupport.clanSnapshot(),
                LightweightClansTestSupport.clanSnapshot(),
                LightweightClansTestSupport.clanSnapshot().members().getFirst(),
                LightweightClansTestSupport.clanSnapshot().members().get(1)
        ));
        fixture.scheduler.runAll();

        assertTrue(fixture.transport.requests().getFirst().body().contains("\"event\":\"clan.president_transferred\""));
    }

    @Test
    void bannerUpdatedEventIncludesBannerPayload() {
        Fixture fixture = fixture(true, true);

        fixture.listener.onClanBannerUpdated(new ClanBannerUpdatedEvent(
                LightweightClansTestSupport.clanSnapshot(false, 2),
                LightweightClansTestSupport.clanSnapshot(true, 2)
        ));
        fixture.scheduler.runAll();

        String body = fixture.transport.requests().getFirst().body();
        assertTrue(body.contains("\"event\":\"clan.banner_updated\""));
        assertTrue(body.contains("\"banner\":{\"baseMaterial\":\"minecraft:black_banner\""));
    }

    @Test
    void listenerPathQueuesAsyncDeliveryInsteadOfSendingInline() {
        Fixture fixture = fixture(true, true);

        fixture.listener.onClanCreated(new ClanCreatedEvent(LightweightClansTestSupport.clanSnapshot()));

        assertEquals(1, fixture.scheduler.queuedTaskCount());
        assertTrue(fixture.transport.requests().isEmpty());
    }

    private Fixture fixture(boolean includeMembers, boolean includeBanner) {
        ClansWebhookConfig config = new ClansWebhookConfig(true, "https://example.com/webhook", "secret", false, 0, includeMembers, includeBanner, 5000, 5000, 0, 1);
        LightweightClansTestSupport.RecordingScheduler scheduler = new LightweightClansTestSupport.RecordingScheduler();
        LightweightClansTestSupport.RecordingTransport transport = new LightweightClansTestSupport.RecordingTransport();
        LightweightClansBridge bridge = new LightweightClansBridge(
                LightweightClansTestSupport.pluginWithConfig(LightweightClansTestSupport.pluginConfig()),
                config,
                mock(LightweightClansServiceResolver.class),
                mock(PluginManager.class),
                new LightweightClansPayloadMapper(),
                new LightweightClansWebhookSender(
                        LightweightClansTestSupport.pluginWithConfig(LightweightClansTestSupport.pluginConfig()),
                        config,
                        scheduler,
                        transport,
                        new LightweightClansWebhookSigner()
                )
        );

        return new Fixture(new LightweightClansListener(bridge), scheduler, transport);
    }

    private record Fixture(
            LightweightClansListener listener,
            LightweightClansTestSupport.RecordingScheduler scheduler,
            LightweightClansTestSupport.RecordingTransport transport
    ) {
    }
}
