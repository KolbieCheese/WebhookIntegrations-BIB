package rudynakodach.github.io.webhookintegrations.Clans;

import io.github.maste.customclans.api.event.ClanBannerUpdatedEvent;
import io.github.maste.customclans.api.event.ClanCreatedEvent;
import io.github.maste.customclans.api.event.ClanDeletedEvent;
import io.github.maste.customclans.api.event.ClanMemberJoinedEvent;
import io.github.maste.customclans.api.event.ClanMemberKickedEvent;
import io.github.maste.customclans.api.event.ClanMemberLeftEvent;
import io.github.maste.customclans.api.event.ClanPresidentTransferredEvent;
import io.github.maste.customclans.api.event.ClanUpdatedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class LightweightClansListener implements Listener {
    private final LightweightClansBridge bridge;

    public LightweightClansListener(LightweightClansBridge bridge) {
        this.bridge = bridge;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClanCreated(ClanCreatedEvent event) {
        bridge.sendClanSnapshot("clan.created", event.getClan(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClanUpdated(ClanUpdatedEvent event) {
        bridge.sendClanSnapshot("clan.updated", event.getAfter(), event.getChangedFields().orElse(null));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClanDeleted(ClanDeletedEvent event) {
        bridge.sendDeletedClan("clan.deleted", event.getDeletedClan());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClanMemberJoined(ClanMemberJoinedEvent event) {
        bridge.sendClanSnapshot("clan.member_joined", event.getAfter(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClanMemberLeft(ClanMemberLeftEvent event) {
        bridge.sendClanSnapshot("clan.member_left", event.getAfter(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClanMemberKicked(ClanMemberKickedEvent event) {
        bridge.sendClanSnapshot("clan.member_kicked", event.getAfter(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClanPresidentTransferred(ClanPresidentTransferredEvent event) {
        bridge.sendClanSnapshot("clan.president_transferred", event.getAfter(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClanBannerUpdated(ClanBannerUpdatedEvent event) {
        bridge.sendClanSnapshot("clan.banner_updated", event.getAfter(), null);
    }
}
