package com.benjamintaskis.createsignalextensions;

import com.benjamintaskis.createsignalextensions.client.SpeedZoneBoundaryResolver;
import com.benjamintaskis.createsignalextensions.client.SpeedZoneColour;
import com.benjamintaskis.createsignalextensions.client.SpeedZonePulse;
import com.benjamintaskis.createsignalextensions.content.speed.SpeedZoneBoardData.Board;
import com.benjamintaskis.createsignalextensions.network.SpeedZoneBoardsPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Ordinary/X boundary parity and the retained compatible visual packet codec. */
public final class TurnoutVisualChecks {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void rejectsCount(int count) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            buffer.writeResourceLocation(ResourceLocation.parse("minecraft:overworld"));
            buffer.writeVarInt(count);
            try { SpeedZoneBoardsPayload.STREAM_CODEC.decode(buffer); throw new AssertionError("Accepted invalid count"); }
            catch (IllegalArgumentException expected) { check(true, "Invalid count rejected"); }
        } finally { buffer.release(); }
    }
    public static void main(String[] args) {
        Vec3 forward = new Vec3(1, 0, 0);
        for (int speed : new int[]{20, 40}) {
            var ordinaryZones = SpeedZoneBoundaryResolver.resolve(List.of(
                    new SpeedZoneBoundaryResolver.Board(0, 100, false),
                    new SpeedZoneBoundaryResolver.Board(1, speed, false),
                    new SpeedZoneBoundaryResolver.Board(20, 0, false)), 30);
            var xZones = SpeedZoneBoundaryResolver.resolve(List.of(
                    new SpeedZoneBoundaryResolver.Board(0, 100, false),
                    new SpeedZoneBoundaryResolver.Board(1, speed, true),
                    new SpeedZoneBoundaryResolver.Board(20, 0, false)), 30);
            check(xZones.equals(ordinaryZones), "X prefix cannot change branch boundary resolution");
            check(xZones.size() == 2 && xZones.getLast().speedKmh() == speed
                    && xZones.getLast().start() == 1, "Restriction starts at its own branch board");
        }
        check(SpeedZoneColour.rgb(100) == 0x00cd41, "100 km/h stays green");
        check(SpeedZoneColour.rgb(20) == 0x0066ff, "20 km/h stays blue");
        check(SpeedZonePulse.strength(13, 1, 3) == SpeedZonePulse.strength(5, 1, 3), "Pulse phase carries over approach and branches");

        Board ordinary = new Board(BlockPos.ZERO, Vec3.ZERO, forward, .2, 100);
        Board turnout = new Board(new BlockPos(1, 2, 3), Vec3.ZERO, forward, .6, 20, true);
        check(ordinary.valid() && turnout.valid() && !ordinary.turnout() && turnout.turnout(), "Both visual board types retained");
        var payload = new SpeedZoneBoardsPayload(ResourceLocation.parse("minecraft:overworld"), List.of(ordinary, turnout));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            SpeedZoneBoardsPayload.STREAM_CODEC.encode(buffer, payload);
            check(payload.equals(SpeedZoneBoardsPayload.STREAM_CODEC.decode(buffer)), "Packet preserves normal speed, X20 and turnout flag");
            check(buffer.readableBytes() == 0, "Packet fully consumed");
        } finally { buffer.release(); }
        rejectsCount(-1); rejectsCount(4097);
        System.out.println("Cosmetic-X boundary/packet regression checks passed: " + checks);
    }
}
