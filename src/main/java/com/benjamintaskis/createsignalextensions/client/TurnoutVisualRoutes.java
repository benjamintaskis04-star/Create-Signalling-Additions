package com.benjamintaskis.createsignalextensions.client;

import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Read-only visual classification, independent of train steering or selected routes. */
public final class TurnoutVisualRoutes {
    private TurnoutVisualRoutes() {}

    /**
     * Create NONE steering ranks abs((incoming cross up) dot outgoing).
     * Use the oriented edges' getDirection(false/true) trajectories, NOT a
     * curve flag or its initial tangent (all turnout tangents can coincide).
     * Unlike Create's last-entry tie break, ambiguous geometry returns unknown.
     */
    public static int straightIndex(Vec3 incoming, Vec3 up, List<Vec3> outgoing) {
        Vec3 lateral = incoming.normalize().cross(up.normalize());
        if (outgoing.size() < 2 || lateral.lengthSqr() < 1.0e-8) return -1;
        lateral = lateral.normalize();
        int best = -1;
        double closest = Double.POSITIVE_INFINITY;
        boolean tied = false;
        for (int i = 0; i < outgoing.size(); i++) {
            Vec3 trajectory = outgoing.get(i);
            if (trajectory == null || trajectory.lengthSqr() < 1.0e-8) return -1;
            double score = Math.abs(lateral.dot(trajectory.normalize()));
            if (!Double.isFinite(score)) return -1;
            if (score < closest - 1.0e-6) {
                best = i; closest = score; tied = false;
            } else if (Math.abs(score - closest) <= 1.0e-6) tied = true;
        }
        return tied ? -1 : best;
    }

    /** Waiting X boards paint nothing before their first facing junction. */
    public static boolean follows(boolean waiting, int straight, int exit, int exitCount) {
        return !waiting || exitCount == 1 || straight >= 0 && exit != straight;
    }
}
