package com.benjamintaskis.createsignalextensions;

import com.benjamintaskis.createsignalextensions.client.SpeedZonePulse;
import com.benjamintaskis.createsignalextensions.client.SpeedZoneColour;

/** Direction and continuity checks; does not stand in for an in-game visual test. */
public final class Alpha1bChecks {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(SpeedZonePulse.strength(0, 0, 3) == 1, "Pulse starts at origin");
        check(SpeedZonePulse.strength(3, 1, 3) == 1, "Pulse moves forward three blocks in one second");
        check(SpeedZonePulse.strength(6, 1, 6) == 1, "Speed option changes travel speed");
        check(SpeedZonePulse.strength(4, 0, 3) == 0, "Space between pulses has no brightness boost");
        check(SpeedZonePulse.strength(8, 0, 3) == 1, "Pulse repeats every eight blocks");
        check(SpeedZonePulse.strength(-8, 0, 3) == 1, "Negative phase wraps correctly");
        // A reverse restriction uses distance from the opposite end of the same physical edge.
        double length = 32, time = 1, speed = 3;
        check(SpeedZonePulse.strength(length - 29, time, speed) == 1,
                "Reverse pulse moves toward decreasing world distance");
        check(SpeedZonePulse.strength(3, time, speed) > SpeedZonePulse.strength(length - 3, time, speed),
                "Opposing lanes retain distinct pulse directions");
        for (int kmh : new int[]{30, 65, 100, 150, 175, 200}) {
            int base = SpeedZoneColour.rgb(kmh);
            check(SpeedZonePulse.colour(base, 0) == base, "No pulse preserves base palette at " + kmh);
            int bright = SpeedZonePulse.colour(base, 1);
            check((bright >> 16 & 255) >= (base >> 16 & 255)
                    && (bright >> 8 & 255) >= (base >> 8 & 255)
                    && (bright & 255) >= (base & 255), "Pulse brightens without extinguishing colour at " + kmh);
        }
        for (int tick = 0; tick < 200; tick++) {
            double value = SpeedZonePulse.strength(2.5, tick / 20.0, .1);
            check(value >= 0 && value <= 1, "Pulse stays bounded");
        }
        check(Math.abs(SpeedZonePulse.strength(2.5, 100, 3)
                - SpeedZonePulse.strength(10.5, 100, 3)) < 1.0e-10, "Phase remains continuous across edge boundaries");
        System.out.println("Alpha 1b visual checks passed: " + checks);
    }
}
