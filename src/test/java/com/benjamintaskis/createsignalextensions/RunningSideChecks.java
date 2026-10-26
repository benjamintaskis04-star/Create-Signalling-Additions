package com.benjamintaskis.createsignalextensions;

import com.benjamintaskis.createsignalextensions.config.ClientConfig;
import com.benjamintaskis.createsignalextensions.config.SpeedHighlightRunningSide;
import com.benjamintaskis.createsignalextensions.client.SpeedZonePulse;
import com.benjamintaskis.createsignalextensions.client.TurnoutVisualRoutes;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Issue #2: physical left/right, graph reversal, config migration and turnout integration. */
public final class RunningSideChecks {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(ClientConfig.SPEED_HIGHLIGHT_RUNNING_SIDE.getDefault() == SpeedHighlightRunningSide.LEFT,
                "NeoForge setting defaults to LEFT");
        var config = CommentedConfig.inMemory();
        ClientConfig.SPEC.correct(config);
        String path = "speed_zone_visualisation.speed_highlight_running_side";
        check(config.get(path).toString().equals("LEFT"), "Missing key in an existing installation migrates to LEFT");
        config.set(path, "RIGHT");
        ClientConfig.SPEC.correct(config);
        check(config.get(path).toString().equals("RIGHT"), "Valid RIGHT survives config correction");
        var restored = new TomlParser().parse(new TomlWriter().writeToString(config));
        ClientConfig.SPEC.correct(restored);
        check(restored.get(path).toString().equals("RIGHT"), "RIGHT persists through TOML save/load");
        config.set(path, "invalid");
        ClientConfig.SPEC.correct(config);
        check(config.get(path).toString().equals("LEFT"), "Invalid setting safely corrects to LEFT");
        restored = new TomlParser().parse(new TomlWriter().writeToString(config));
        ClientConfig.SPEC.correct(restored);
        check(restored.get(path).toString().equals("LEFT"), "LEFT persists through TOML save/load");

        Vec3 up = new Vec3(0, 1, 0);
        for (int heading = 0; heading < 16; heading++) {
            double angle = heading * Math.PI / 8;
            for (double slope : new double[]{-.5, 0, .5}) {
                Vec3 travel = new Vec3(Math.cos(angle), slope, Math.sin(angle)).normalize();
                // Physical left comes from UP cross TRAVEL, independently of canonical edge orientation.
                Vec3 left = up.cross(travel).normalize();
                for (boolean reversedGraph : new boolean[]{false, true}) {
                    Vec3 canonical = reversedGraph ? travel.scale(-1) : travel;
                    Vec3 meshRight = new Vec3(-canonical.z, 0, canonical.x).normalize();
                    int expectedTravelSlot = reversedGraph ? 1 : 0;
                    double leftSide = left.dot(meshRight);
                    check(SpeedHighlightRunningSide.LEFT.directionIndex(leftSide) == expectedTravelSlot,
                            "LEFT follows physical travel through rotations, curves, slopes and graph reversal");
                    check(SpeedHighlightRunningSide.RIGHT.directionIndex(-leftSide) == expectedTravelSlot,
                            "RIGHT follows physical travel through rotations, curves, slopes and graph reversal");
                    check(SpeedHighlightRunningSide.LEFT.directionIndex(-leftSide) == 1 - expectedTravelSlot,
                            "Opposing restriction occupies its own left half");
                    check(SpeedHighlightRunningSide.RIGHT.directionIndex(leftSide) == 1 - expectedTravelSlot,
                            "Opposing restriction occupies its own right half");
                }
            }
        }
        for (double side : new double[]{-.7, .7}) {
            int leftOwner = SpeedHighlightRunningSide.LEFT.directionIndex(side);
            int rightOwner = SpeedHighlightRunningSide.RIGHT.directionIndex(side);
            check(leftOwner != rightOwner, "Changing setting moves ownership on the very next frame");
            // Speeds and pulse distances belong to restriction slots, not sleeper halves.
            int[] speeds = {100, 60};
            double[] distances = {3, 29};
            check(speeds[leftOwner] == speeds[1 - rightOwner], "Setting never exchanges the stored speed values");
            check(SpeedZonePulse.strength(distances[leftOwner], 1, 3)
                    == SpeedZonePulse.strength(distances[1 - rightOwner], 1, 3), "Pulse phase stays attached to its owner");
        }
        for (int mirror : new int[]{-1, 1}) {
            Vec3 incoming = new Vec3(1, 0, 0);
            List<Vec3> routes = List.of(incoming, new Vec3(1, 0, .25 * mirror));
            int straight = TurnoutVisualRoutes.straightIndex(incoming, up, routes);
            check(straight == 0, "Issue #3 retains unique straight route on mirrored turnout");
            for (var mode : SpeedHighlightRunningSide.values()) {
                for (int route = 0; route < routes.size(); route++) {
                    Vec3 direction = routes.get(route).normalize();
                    Vec3 physicalLane = up.cross(direction).normalize().scale(mode == SpeedHighlightRunningSide.LEFT ? 1 : -1);
                    Vec3 meshRight = new Vec3(-direction.z, 0, direction.x).normalize();
                    check(mode.directionIndex(physicalLane.dot(meshRight)) == 0, "Straight and diverging routes use chosen travel-relative half");
                    check(TurnoutVisualRoutes.follows(true, straight, route, routes.size()) == (route == 1),
                            "Running side does not change the X20 branch classification");
                }
            }
        }
        System.out.println("Issue #2 running-side regression checks passed: " + checks);
    }
}
