package com.benjamintaskis.createsignalextensions;

import com.benjamintaskis.createsignalextensions.client.*;
import com.benjamintaskis.createsignalextensions.content.speed.*;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Behaviour checks for the safety-sensitive changes, independent of a game world. */
public final class Alpha1aChecks {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        Vec3 target = Vec3.ZERO, forward = new Vec3(1, 0, 0);
        check(!RearClearance.crossed(new Vec3(-3, 0, 0), new Vec3(-1, 0, 0), target, forward, 2.75), "Approaching rear must not release");
        check(RearClearance.crossed(new Vec3(-1, 0, 0), new Vec3(.1, 0, 0), target, forward, 2.75), "Rear passing boundary releases");
        check(RearClearance.crossed(new Vec3(-4, 0, 0), new Vec3(4, 0, 0), target, forward, 2.75), "Fast rear crossing must not skip boundary");
        check(!RearClearance.crossed(new Vec3(1, 0, 0), new Vec3(-1, 0, 0), target, forward, 2.75), "Reversal must not release original instruction");
        check(!RearClearance.crossed(new Vec3(-1, 5, 0), new Vec3(1, 5, 0), target, forward, 2.75), "Parallel track must not release");
        check(!RearClearance.crossed(null, target, target, forward, 2.75), "Stationary restored train must retain boundary");
        check(!RearClearance.crossed(target, target, target, forward, 2.75), "Stopped rear must retain boundary");
        check(RearClearance.crossed(new Vec3(1, 0, 0), new Vec3(-1, 0, 0), target, forward.scale(-1), 2.75), "Reverse direction boundary works");

        var zones = SpeedZoneBoundaryResolver.resolve(List.of(
                new SpeedZoneBoundaryResolver.Board(0, 60, false),
                new SpeedZoneBoundaryResolver.Board(10, 20, true),
                new SpeedZoneBoundaryResolver.Board(30, 0, false)), 100);
        check(zones.size() == 2 && zones.getFirst().end() == 10 && zones.getLast().speedKmh() == 20
                && zones.getLast().end() == 30, "X board is an ordinary boundary; unrestricted ends its zone");
        zones = SpeedZoneBoundaryResolver.resolve(List.of(
                new SpeedZoneBoundaryResolver.Board(0, 60, false),
                new SpeedZoneBoundaryResolver.Board(20, 100, false),
                new SpeedZoneBoundaryResolver.Board(20, 40, false)), 50);
        check(zones.size() == 2 && zones.getLast().speedKmh() == 40, "Coincident boundaries do not create empty zones");
        check(SpeedZoneBoundaryResolver.resolve(List.of(new SpeedZoneBoundaryResolver.Board(Double.NaN, 60, false)), 100).isEmpty(), "Invalid boundary is ignored");
        check(SpeedZoneColour.rgb(10) == SpeedZoneColour.rgb(30), "Low speed palette clamps");
        check(SpeedZoneColour.rgb(300) == SpeedZoneColour.rgb(200), "High speed palette clamps");
        check(SpeedZoneColour.rgb(99) != SpeedZoneColour.rgb(100), "Palette interpolates continuously");
        var quads = SpeedZoneTrackMesh.build(List.of(Vec3.ZERO, new Vec3(1, 1, 0), new Vec3(1, 1, 1)), 2, .03);
        check(quads.size() == 2 && Math.abs(quads.getFirst().leftEnd().y - 1.03) < 1.0e-9, "Mesh follows curve and slope samples");
        check(SpeedZoneTrackMesh.build(List.of(Vec3.ZERO, Vec3.ZERO), 2, .03).isEmpty(), "Degenerate mesh is ignored");

        check(SpeedBrakingEnvelope.permittedKmh(20, 0, 2, .002) == 20, "X20 envelope reaches posted speed at board");
        check(SpeedBrakingEnvelope.permittedKmh(20, 100, 2, .001) < SpeedBrakingEnvelope.permittedKmh(20, 100, 2, .004), "Weak brakes must start braking earlier");
        check(SpeedBrakingEnvelope.permittedKmh(20, 100, -2, .002) == SpeedBrakingEnvelope.permittedKmh(20, 100, 2, .002), "Braking symmetric in reverse");
        check(SpeedBrakingEnvelope.lookAheadBlocks(0, .001) == 640, "Look-ahead preserves baseline at low speed");
        check(SpeedBrakingEnvelope.lookAheadBlocks(100.0 / 72, .001) > 1000, "Weak brakes require more than old 640 block horizon");
        check(SpeedBrakingEnvelope.lookAheadBlocks(10, .000001) == 8192, "Look-ahead remains bounded");
        for (double acceleration : new double[]{.001, .002, .005}) {
            double speed = 100.0 / 72, distance = SpeedBrakingEnvelope.lookAheadBlocks(speed, acceleration);
            for (int tick = 0; tick < 5000 && distance > 0; tick++) {
                double cap = SpeedBrakingEnvelope.permittedKmh(20, distance, speed, acceleration) / 72;
                speed = speed > cap ? Math.max(cap, speed - acceleration) : Math.min(cap, speed + acceleration);
                distance -= speed;
            }
            check(distance <= 0 && speed * 72 <= 20.001, "Automatic train reaches X20 at 20 km/h with acceleration " + acceleration);
        }
        System.out.println("Alpha 1a regression checks passed: " + checks);
    }
}
