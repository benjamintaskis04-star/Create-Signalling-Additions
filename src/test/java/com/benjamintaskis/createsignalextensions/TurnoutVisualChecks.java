package com.benjamintaskis.createsignalextensions;

import com.benjamintaskis.createsignalextensions.client.TurnoutVisualRoutes;
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

/** Issue #3 regression checks using the production classifier and visual packet codec. */
public final class TurnoutVisualChecks {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static Vec3 rotate(Vec3 v, double angle) { return v.yRot((float)angle); }
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
        Vec3 forward = new Vec3(1, 0, 0), up = new Vec3(0, 1, 0);
        // getDirection(true) samples the first quarter of each edge, not the common start tangent.
        Vec3 diverging = new Vec3(1, 0, .25).normalize();
        for (int quarter = 0; quarter < 8; quarter++) {
            double angle = quarter * Math.PI / 4;
            for (int mirror : new int[]{-1, 1}) {
                Vec3 approach = rotate(forward, angle);
                Vec3 branch = rotate(new Vec3(1, 0, .25 * mirror), angle);
                check(TurnoutVisualRoutes.straightIndex(approach, up, List.of(approach, branch)) == 0,
                        "Straight branch at orientation " + quarter + " mirror " + mirror);
                check(TurnoutVisualRoutes.straightIndex(approach, up, List.of(branch, approach)) == 1,
                        "Classification independent of graph adjacency order");
            }
        }
        check(TurnoutVisualRoutes.straightIndex(forward, up, List.of(forward, diverging)) == 0, "Facing turnout");
        check(TurnoutVisualRoutes.straightIndex(forward.scale(-1), up,
                List.of(forward.scale(-1), diverging.scale(-1))) == 0, "Reverse-oriented approach");
        Vec3 rising = new Vec3(1, .5, 0).normalize();
        check(TurnoutVisualRoutes.straightIndex(rising, up, List.of(new Vec3(1, .5, .25), rising)) == 1,
                "Sloped turnout retains lateral classification");
        check(TurnoutVisualRoutes.straightIndex(rising, new Vec3(-.5, 1, 0), List.of(rising, new Vec3(1, .5, .25))) == 0,
                "Tilted graph normal");
        Vec3 curvedIncoming = new Vec3(1, 0, .1);
        check(TurnoutVisualRoutes.straightIndex(curvedIncoming, up,
                List.of(new Vec3(1, 0, .4), curvedIncoming)) == 1, "Curved approach and curved default continuation");
        check(TurnoutVisualRoutes.straightIndex(forward, up,
                List.of(new Vec3(1, 0, -.25), new Vec3(1, 0, .25))) == -1, "Symmetric Y does not invent a main route");
        check(TurnoutVisualRoutes.straightIndex(forward, up, List.of(forward, forward)) == -1, "Coincident directions are ambiguous");
        check(TurnoutVisualRoutes.straightIndex(forward, up, List.of(forward)) == -1, "Trailing merge is not a facing junction");
        check(TurnoutVisualRoutes.straightIndex(Vec3.ZERO, up, List.of(forward, diverging)) == -1, "Degenerate approach");
        check(TurnoutVisualRoutes.straightIndex(forward, up, List.of(forward, Vec3.ZERO)) == -1, "Degenerate outgoing geometry");
        check(!TurnoutVisualRoutes.follows(true, 0, 0, 2), "Waiting X20 never paints the straight route");
        check(TurnoutVisualRoutes.follows(true, 0, 1, 2), "Waiting X20 enters the diverging route");
        check(TurnoutVisualRoutes.follows(true, -1, 0, 1), "X20 remains armed across an unbranched approach");
        check(!TurnoutVisualRoutes.follows(true, -1, 1, 2), "Unknown split does not claim a diverging restriction");
        check(TurnoutVisualRoutes.follows(false, -1, 0, 2), "Active diverging restriction continues through later forks");
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
        System.out.println("Issue #3 turnout visual regression checks passed: " + checks);
    }
}
