package com.benjamintaskis.createsignalextensions.content.speed;

import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.track.BezierTrackPointLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Read-only eligibility for a board on the edge actually being scouted. */
public final class SpeedBoardRouteMatch {
    private SpeedBoardRouteMatch() {}
    public static boolean matches(TrackGraph graph, TrackEdge edge, Vec3 target,
                                  BlockPos track, BezierTrackPointLocation curve) {
        if (curve != null) {
            if (!edge.isTurn()) return false;
            var ends = edge.getTurn().bePositions;
            return ends.getFirst().equals(track) && ends.getSecond().equals(curve.curveTarget())
                    || ends.getSecond().equals(track) && ends.getFirst().equals(curve.curveTarget());
        }
        if (edge.isTurn()) return false;
        Vec3 a = edge.getPosition(graph, 0), d = edge.getPosition(graph, 1).subtract(a);
        double horizontal = d.x * d.x + d.z * d.z;
        if (horizontal < 1.0e-8) return false;
        double t = ((target.x - a.x) * d.x + (target.z - a.z) * d.z) / horizontal;
        if (t < -1.0e-6 || t > 1.000001) return false;
        Vec3 projected = a.add(d.scale(t));
        double dx = target.x - projected.x, dz = target.z - projected.z;
        // Straight target positions use block centres, up to half a block above the graph.
        return dx * dx + dz * dz < 1.0e-4 && Math.abs(target.y - projected.y) <= .75;
    }
}
