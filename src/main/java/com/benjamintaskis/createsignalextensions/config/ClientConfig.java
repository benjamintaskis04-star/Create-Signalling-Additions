package com.benjamintaskis.createsignalextensions.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MINIMUM_GUI_SCALE_PERCENT;
    public static final ModConfigSpec.BooleanValue SHOW_ALL_SPEED_ZONES;
    public static final ModConfigSpec.IntValue SPEED_ZONE_RENDER_DISTANCE;
    public static final ModConfigSpec.DoubleValue SPEED_ZONE_OPACITY;
    public static final ModConfigSpec.BooleanValue SPEED_ZONE_PULSE;
    public static final ModConfigSpec.DoubleValue SPEED_ZONE_PULSE_SPEED;
    public static final ModConfigSpec.EnumValue<SpeedHighlightRunningSide> SPEED_HIGHLIGHT_RUNNING_SIDE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("display");
        b.push("route_interface_gui");
        MINIMUM_GUI_SCALE_PERCENT = b.comment("Smallest percentage of the designed Route Interface/Route Map size before vertical scrolling is used.")
                .translation("create_signal_extensions.configuration.display.route_interface_gui.minimum_gui_scale_percent")
                .defineInRange("minimum_gui_scale_percent", 75, 50, 100);
        b.pop();
        b.pop();
        b.push("speed_zone_visualisation");
        SPEED_HIGHLIGHT_RUNNING_SIDE = b.comment("Sleeper half used for each travel direction when opposite restrictions overlap. LEFT/RIGHT is relative to travel, not the camera. Applies immediately after client config reload.")
                .defineEnum("speed_highlight_running_side", SpeedHighlightRunningSide.LEFT);
        SHOW_ALL_SPEED_ZONES = b.comment("Show configured speed zones in range. Crosshair inspection remains enabled independently.")
                .define("show_all_speed_zones", false);
        SPEED_ZONE_RENDER_DISTANCE = b.comment("Maximum speed-zone overlay distance in blocks (also capped by loaded chunks).")
                .defineInRange("render_distance", 128, 16, 256);
        SPEED_ZONE_OPACITY = b.comment("Translucent speed-zone overlay opacity.")
                .defineInRange("opacity", 0.5, 0.05, 0.85);
        SPEED_ZONE_PULSE = b.comment("Animate a travelling highlight in each restriction's direction. Base tint stays visible.")
                .define("pulse_enabled", true);
        SPEED_ZONE_PULSE_SPEED = b.comment("Directional pulse travel speed in blocks per second (visual only).")
                .defineInRange("pulse_speed", 3.0, 0.1, 20.0);
        b.pop();
        SPEC = b.build();
    }

    private ClientConfig() {}
    public static boolean showAllSpeedZones() { return SHOW_ALL_SPEED_ZONES.get(); }
    public static int speedZoneRenderDistance() { return SPEED_ZONE_RENDER_DISTANCE.get(); }
    public static float speedZoneOpacity() { return SPEED_ZONE_OPACITY.get().floatValue(); }
    public static boolean speedZonePulse() { return SPEED_ZONE_PULSE.get(); }
    public static double speedZonePulseSpeed() { return SPEED_ZONE_PULSE_SPEED.get(); }
    public static SpeedHighlightRunningSide speedHighlightRunningSide() { return SPEED_HIGHLIGHT_RUNNING_SIDE.get(); }
    public static double minimumGuiScale() { return MINIMUM_GUI_SCALE_PERCENT.get() / 100.0; }
}
