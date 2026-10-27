package com.benjamintaskis.createsignalextensions.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server/world signalling presentation options shared by all players. */
public final class CommonConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue OUT_OF_USE_X;
    public static final ModConfigSpec.BooleanValue SIGNAL_DEBUG;
    public static final ModConfigSpec.IntValue SIGNAL_REFRESH_TICKS;
    public static final ModConfigSpec.BooleanValue ADAPTIVE_CHUNK_LOADING;
    public static final ModConfigSpec.IntValue MAX_SIGNALLING_FORCED_CHUNKS;
    public static final ModConfigSpec.IntValue SHUNT_PROCEED_SPEED_KMH;
    public static final ModConfigSpec.BooleanValue SPEED_DIAGNOSTICS;
    public static final ModConfigSpec.BooleanValue SPEED_GRAPH_DIAGNOSTICS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("signals");
        OUT_OF_USE_X = b.comment(
                        "Show the prototypical white Out of Use X on unlinked NSW signals.",
                        "When false, unlinked signals use their normal diagnostic indication (usually flashing STOP).",
                        "Distant signals keep their yellow unlinked marker when false. Markers such as the End of Line Marker are unaffected.")
                .define("out_of_use_x", false);
        SIGNAL_DEBUG = b.comment("Enable verbose CSA signal/route diagnostic logging. Does not disable Signal Debug Display Link sources.")
                .define("signal_debug", false);
        SIGNAL_REFRESH_TICKS = b.comment("Server ticks between CSA signal and Route Interface refreshes. 10 ticks = 2 refreshes/second at 20 TPS.")
                .defineInRange("signal_refresh_ticks", 10, 5, 40);
        b.pop();
        b.push("chunk_loading");
        ADAPTIVE_CHUNK_LOADING = b.comment("Dynamically keep signalling chunks loaded ahead of nearby players and active train paths.")
                .define("adaptive_chunk_loading", true);
        MAX_SIGNALLING_FORCED_CHUNKS = b.comment("Maximum number of chunks Create: Signalling Additions may force-load at once. Player-triggered signalling has priority over train-triggered signalling.")
                .defineInRange("max_forced_chunks", 128, 16, 2048);
        b.pop();
        b.push("speed_restrictions");
        SPEED_GRAPH_DIAGNOSTICS = b.comment("Rate-limited graph association, route choice and board accept/reject diagnostics. Disabled during normal gameplay.")
                .define("graph_diagnostics", false);
        SPEED_DIAGNOSTICS = b.comment("Log speed-board decisions and speed-state persistence (load, restore, changes and disk save). Diagnostic build default: enabled.", "Independent of signals.signal_debug. Disable after testing to reduce log volume.")
                .define("speed_diagnostics", true);
        SHUNT_PROCEED_SPEED_KMH = b.comment("Maximum speed in km/h while a train is under SHUNT PROCEED authority. Cleared by an End of Shunt sign.")
                .defineInRange("shunt_proceed_speed_kmh", 25, 1, 999);
        b.pop();
        SPEC = b.build();
    }

    private CommonConfig() {}
    public static boolean outOfUseX() { return OUT_OF_USE_X.get(); }
    public static boolean signalDebug() { return SIGNAL_DEBUG.get(); }
    public static int signalRefreshTicks() { return SIGNAL_REFRESH_TICKS.get(); }
    public static boolean adaptiveChunkLoading() { return ADAPTIVE_CHUNK_LOADING.get(); }
    public static int maxSignallingForcedChunks() { return MAX_SIGNALLING_FORCED_CHUNKS.get(); }
    public static boolean speedDiagnostics() { return SPEED_DIAGNOSTICS.get(); }
    public static boolean speedGraphDiagnostics() { return SPEED_GRAPH_DIAGNOSTICS.get(); }
    public static int shuntProceedSpeedKmh() { return SHUNT_PROCEED_SPEED_KMH.get(); }
}
