package com.benjamintaskis.createsignalextensions.content.speed;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import com.benjamintaskis.createsignalextensions.config.CommonConfig;
import net.minecraft.core.BlockPos;
import java.util.*;

/** Bounded, per-key and global logging limits. Never logs by default. */
public final class SpeedGraphDiagnostics {
    private SpeedGraphDiagnostics() {}
    private static final Map<String, Long> LAST = new LinkedHashMap<>();
    private static long window;
    private static int emitted;
    public static void clear() { LAST.clear(); emitted = 0; window = 0; }
    public static void log(long tick, UUID train, BlockPos board, String phase, String reason,
                           SpeedBoardGraphLocation association, SpeedBoardGraphLocation.Sample point, String extra) {
        if (!CommonConfig.speedGraphDiagnostics()) return;
        String key = train + ":" + board + ":" + phase + ":" + reason;
        if (!reserve(tick, key)) return;
        CreateSignallingAdditions.LOGGER.info("[CSA SpeedGraph] train={} board={} phase={} reason={} binding={} trainLocation={} {}",
                train, board, phase, reason, describe(association), describe(point), extra);
    }
    static boolean reserve(long tick, String key) {
        if (tick - window >= 20 || tick < window) { window = tick; emitted = 0; }
        if (emitted >= 10 || LAST.containsKey(key) && tick - LAST.get(key) < 100) return false;
        if (LAST.size() >= 2048) LAST.remove(LAST.keySet().iterator().next());
        LAST.put(key, tick); emitted++;
        return true;
    }
    private static String describe(SpeedBoardGraphLocation at) {
        return at == null ? "unresolved" : at.graph().id + ":" + at.from().getLocation().getLocation()
                + "->" + at.to().getLocation().getLocation() + "@" + at.position() + "/" + at.edge().getLength();
    }
    private static String describe(SpeedBoardGraphLocation.Sample at) {
        return at == null ? "unknown" : at.graph().id + ":" + at.from().getLocation().getLocation()
                + "->" + at.to().getLocation().getLocation() + "@" + at.position() + " reversed=" + at.reversed();
    }
}
