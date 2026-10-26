package com.benjamintaskis.createsignalextensions.config;

/** Client presentation only; never changes a restriction's travel direction or speed. */
public enum SpeedHighlightRunningSide {
    LEFT, RIGHT;

    /**
     * Mesh side is dot(face - track, (-tangent.z, 0, tangent.x)), positive to
     * the RIGHT of canonical travel. Paint slot 0 travels with that tangent;
     * slot 1 travels against it. Reversing canonical travel flips both side
     * and slots, leaving the physical running side unchanged.
     */
    public int directionIndex(double canonicalSide) {
        boolean rightOfCanonical = canonicalSide >= 0;
        return rightOfCanonical == (this == RIGHT) ? 0 : 1;
    }
}
