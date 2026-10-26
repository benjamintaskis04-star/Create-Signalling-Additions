package com.benjamintaskis.createsignalextensions.client;

/** Pure client animation: distances increase in the restriction's travel direction. */
public final class SpeedZonePulse {
    private SpeedZonePulse() {}
    public static double strength(double distance, double seconds, double blocksPerSecond) {
        double phase = (distance - seconds * blocksPerSecond) / 8.0;
        phase -= Math.floor(phase);
        double separation = Math.min(phase, 1 - phase);
        return Math.max(0, 1 - separation / .16);
    }
    public static int colour(int base, double strength) {
        double mix = .65 * strength;
        int r = base >> 16 & 255, g = base >> 8 & 255, b = base & 255;
        return ((int)(r + (255 - r) * mix) << 16)
                | ((int)(g + (255 - g) * mix) << 8) | (int)(b + (255 - b) * mix);
    }
}
