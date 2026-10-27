package com.benjamintaskis.createsignalextensions.content.speed;

import net.minecraft.world.phys.Vec3;

/** Shared ordinary-board rules. Cosmetic prefixes are deliberately not an input. */
public final class SpeedBoardRules {
    private SpeedBoardRules() {}
    public static boolean requiresRearClearance(Integer current, Integer next) {
        return next == null || current != null && next > current;
    }
    public static boolean validDirection(Vec3 motion, Vec3 faceNormal) {
        return motion != null && motion.lengthSqr() >= 1.0e-8 && motion.dot(faceNormal) < -1.0e-5;
    }
}
