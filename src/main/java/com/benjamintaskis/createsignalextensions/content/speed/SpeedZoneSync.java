package com.benjamintaskis.createsignalextensions.content.speed;

import com.benjamintaskis.createsignalextensions.network.SpeedZoneBoardsPayload;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

public final class SpeedZoneSync {
    private SpeedZoneSync() {}
    private static final Map<UUID, SpeedZoneBoardsPayload> SENT = new HashMap<>();
    public static void clear() { SENT.clear(); }
    public static void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }
    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        for (var level : server.getAllLevels()) {
            var data = SpeedZoneBoardData.get(level);
            for (var linked : SpeedRestrictionManager.trackLinkedSnapshot(level))
                if (linked instanceof SpeedBoardBlockEntity board) data.update(board);
            data.validateLoaded(level);
            var payload = new SpeedZoneBoardsPayload(level.dimension().location(), data.snapshot().stream().limit(4096).toList());
            for (var player : level.players()) {
                if (!payload.equals(SENT.put(player.getUUID(), payload))) PacketDistributor.sendToPlayer(player, payload);
            }
        }
        SENT.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }
}
