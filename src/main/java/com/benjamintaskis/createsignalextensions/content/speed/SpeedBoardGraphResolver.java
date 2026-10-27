package com.benjamintaskis.createsignalextensions.content.speed;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.track.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Rebind saved track/curve anchors against current Create graphs, never a nearest train/edge. */
public final class SpeedBoardGraphResolver {
    private SpeedBoardGraphResolver() {}
    public record Result(SpeedBoardGraphLocation location, String reason) {}
    private record Candidate(TrackGraph graph, TrackNode from, TrackNode to, TrackEdge edge) {}
    private record CurveKey(BlockPos a, BlockPos b) {
        static CurveKey of(BlockPos a, BlockPos b) { return a.asLong() < b.asLong() ? new CurveKey(a, b) : new CurveKey(b, a); }
    }
    private record LineKey(long x, long y, long z, long across, long height) {
        static LineKey of(Vec3 point, Vec3 direction) {
            Vec3 d = direction.normalize();
            if (d.x < -1.0e-8 || Math.abs(d.x) < 1.0e-8 && d.z < 0) d = d.scale(-1);
            double h = Math.hypot(d.x, d.z);
            if (h < 1.0e-8) return null;
            return new LineKey(q(d.x), q(d.y), q(d.z), q((-d.z * point.x + d.x * point.z) / h),
                    q(point.y - d.y * (point.x * d.x + point.z * d.z) / (h * h)));
        }
        private static long q(double value) { return Math.round(value * 1_000_000); }
    }
    private record Index(Collection<TrackGraph> graphs, Map<LineKey, List<Candidate>> lines,
                         Map<CurveKey, List<Candidate>> curves) {}
    private record Tick(long time, Index index, Map<SpeedBoardBlockEntity, Result> results) {}
    private static final Map<ServerLevel, Tick> CACHE = new WeakHashMap<>();
    public static void clear() { CACHE.clear(); }
    public static Result resolve(ServerLevel level, SpeedBoardBlockEntity board) {
        // At most once per board/tick. Never retain authority across a graph rebuild/tick.
        Tick tick = CACHE.get(level);
        if (tick == null || tick.time() != level.getGameTime()) {
            tick = new Tick(level.getGameTime(), index(Create.RAILWAYS.trackNetworks.values(), level.dimension()), new WeakHashMap<>());
            CACHE.put(level, tick);
        }
        Index index = tick.index();
        return tick.results().computeIfAbsent(board, $ -> resolveWorld(level, board, index));
    }
    private static Result resolveWorld(ServerLevel level, SpeedBoardBlockEntity board, Index index) {
        if (!board.hasTrackTarget()) return new Result(null, "UNLINKED");
        BlockPos anchor = board.getTargetTrack();
        if (!level.hasChunkAt(anchor)) return new Result(null, "TRACK_UNLOADED");
        var state = level.getBlockState(anchor);
        if (!(state.getBlock() instanceof ITrackBlock track)) return new Result(null, "TRACK_REMOVED");
        Vec3 face = Vec3.atLowerCornerOf(board.getBlockState().getValue(SpeedBoardBlock.FACING).getNormal());
        if (board.hasBezierTarget()) {
            if (!(level.getBlockEntity(anchor) instanceof TrackBlockEntity be)) return new Result(null, "CURVE_MISSING");
            var target = board.getBezierTarget();
            var curve = be.getConnections().get(target.curveTarget());
            if (curve == null) return new Result(null, "CURVE_MISSING"); // Never fall back to a block centre.
            return curve(index, anchor, target, curve, face);
        }
        var axes = track.getTrackAxes(level, anchor, state);
        if (axes.size() != 1) return new Result(null, "AMBIGUOUS_TRACK_AXIS");
        Vec3 centre = Vec3.atBottomCenterOf(anchor).add(0, track.getElevationAtCenter(level, anchor, state), 0);
        if (level.getBlockEntity(anchor) instanceof TrackBlockEntity be && be.isTilted()) {
            Vec3 first = centre.subtract(axes.getFirst().scale(.5)), second = centre.add(axes.getFirst().scale(.5));
            var a = new TrackNodeLocation(first).in(level); var b = new TrackNodeLocation(second).in(level);
            a.yOffsetPixels = track.getYOffsetAt(level, anchor, state, first);
            b.yOffsetPixels = track.getYOffsetAt(level, anchor, state, second);
            return betweenNodes(index.graphs(), a, b, face);
        }
        return straight(index, centre, axes.getFirst(), face);
    }
    private static Index index(Collection<TrackGraph> graphs, ResourceKey<Level> dimension) {
        Map<LineKey, List<Candidate>> lines = new HashMap<>();
        Map<CurveKey, List<Candidate>> curves = new HashMap<>();
        var snapshot = List.copyOf(graphs);
        for (var graph : snapshot) for (var location : graph.getNodes()) {
            if (!location.dimension.equals(dimension)) continue;
            var from = graph.locateNode(location);
            for (var entry : graph.getConnectionsFrom(from).entrySet()) {
                var to = entry.getKey(); var edge = entry.getValue();
                if (from.getNetId() > to.getNetId() || !to.getLocation().dimension.equals(dimension) || edge.getLength() < 1.0e-6) continue;
                var candidate = new Candidate(graph, from, to, edge);
                if (edge.isTurn()) {
                    var ends = edge.getTurn().bePositions;
                    curves.computeIfAbsent(CurveKey.of(ends.getFirst(), ends.getSecond()), $ -> new ArrayList<>()).add(candidate);
                } else {
                    Vec3 a = edge.getPosition(graph, 0), b = edge.getPosition(graph, 1);
                    var key = LineKey.of(a, b.subtract(a));
                    if (key != null) lines.computeIfAbsent(key, $ -> new ArrayList<>()).add(candidate);
                }
            }
        }
        return new Index(snapshot, lines, curves);
    }
    public static Result betweenNodes(Collection<TrackGraph> graphs, TrackNodeLocation a, TrackNodeLocation b, Vec3 face) {
        List<SpeedBoardGraphLocation> matches = new ArrayList<>();
        for (var graph : graphs) {
            var from = graph.locateNode(a); var to = graph.locateNode(b);
            if (from == null || to == null) continue;
            var edge = graph.getConnectionsFrom(from).get(to);
            if (edge != null && !edge.isTurn()) add(matches, graph, from, to, edge, edge.getLength() / 2, face);
        }
        return unique(matches);
    }
    public static Result straight(Collection<TrackGraph> graphs, ResourceKey<Level> dimension,
                                  Vec3 centre, Vec3 axis, Vec3 face) {
        return straight(index(graphs, dimension), centre, axis, face);
    }
    private static Result straight(Index index, Vec3 centre, Vec3 axis, Vec3 face) {
        List<SpeedBoardGraphLocation> matches = new ArrayList<>();
        for (var candidate : index.lines().getOrDefault(LineKey.of(centre, axis), List.of())) {
                var graph = candidate.graph(); var from = candidate.from(); var to = candidate.to(); var edge = candidate.edge();
                Vec3 a = edge.getPosition(graph, 0), d = edge.getPosition(graph, 1).subtract(a);
                if (Math.abs(d.normalize().dot(axis.normalize())) < .999) continue;
                double fraction = centre.subtract(a).dot(d) / d.lengthSqr();
                if (fraction < -1.0e-7 || fraction > 1.0000001) continue;
                fraction = Math.clamp(fraction, 0, 1);
                // Numerical coincidence with the EXPLICIT saved block/axis, not a pickup radius.
                if (a.add(d.scale(fraction)).distanceToSqr(centre) > 1.0e-6) continue;
                add(matches, graph, from, to, edge, fraction * edge.getLength(), face);
        }
        return unique(matches);
    }
    public static Result curve(Collection<TrackGraph> graphs, ResourceKey<Level> dimension,
                               BlockPos anchor, BezierTrackPointLocation target, BezierConnection worldCurve, Vec3 face) {
        return curve(index(graphs, dimension), anchor, target, worldCurve, face);
    }
    private static Result curve(Index index, BlockPos anchor, BezierTrackPointLocation target, BezierConnection worldCurve, Vec3 face) {
        List<SpeedBoardGraphLocation> matches = new ArrayList<>();
        for (var candidate : index.curves().getOrDefault(CurveKey.of(anchor, target.curveTarget()), List.of())) {
                var graph = candidate.graph(); var from = candidate.from(); var to = candidate.to(); var edge = candidate.edge();
                var turn = edge.getTurn();
                boolean direct = turn.bePositions.getFirst().equals(anchor) && turn.bePositions.getSecond().equals(target.curveTarget());
                boolean reverse = turn.bePositions.getSecond().equals(anchor) && turn.bePositions.getFirst().equals(target.curveTarget());
                if (!direct && !reverse || Math.abs(turn.getLength() - worldCurve.getLength()) > 1.0e-6) continue;
                if (!(direct ? turn.starts.getFirst() : turn.starts.getSecond()).equals(worldCurve.starts.getFirst())) continue;
                if (!(direct ? turn.starts.getSecond() : turn.starts.getFirst()).equals(worldCurve.starts.getSecond())
                        || !(direct ? turn.axes.getFirst() : turn.axes.getSecond()).equals(worldCurve.axes.getFirst())
                        || !(direct ? turn.axes.getSecond() : turn.axes.getFirst()).equals(worldCurve.axes.getSecond())) continue;
                // Same position convention as Create 6.0.10 TrackGraphHelper.getBezierGraphLocationAt.
                double position = (target.segment() + 1) / 2.0;
                if (target.segment() < 0 || position > edge.getLength()) continue;
                add(matches, graph, from, to, edge, direct ? position : edge.getLength() - position, face);
        }
        return unique(matches);
    }
    private static void add(List<SpeedBoardGraphLocation> matches, TrackGraph graph, TrackNode from, TrackNode to,
                            TrackEdge edge, double position, Vec3 face) {
        double fraction = position / edge.getLength();
        Vec3 tangent = edge.getPosition(graph, Math.min(1, fraction + .001))
                .subtract(edge.getPosition(graph, Math.max(0, fraction - .001)));
        double dot = tangent.dot(face);
        if (Math.abs(dot) < 1.0e-8) return;
        if (dot > 0) {
            var swap = from; from = to; to = swap;
            edge = graph.getConnectionsFrom(from).get(to);
            if (edge == null) return;
            position = edge.getLength() - position;
        }
        matches.add(new SpeedBoardGraphLocation(graph, from, to, edge, position));
    }
    private static Result unique(List<SpeedBoardGraphLocation> candidates) {
        if (candidates.stream().map(SpeedBoardGraphLocation::graph).distinct().count() > 1)
            return new Result(null, "AMBIGUOUS_GRAPH");
        // At a graph node prefer the oriented outgoing edge over an incoming edge ending there.
        var outgoing = candidates.stream().filter(c -> c.position() < 1.0e-7).toList();
        var usable = outgoing.isEmpty() ? candidates : outgoing;
        return usable.size() == 1 ? new Result(usable.getFirst(), "BOUND")
                : new Result(null, usable.isEmpty() ? "NO_UNAMBIGUOUS_EDGE" : "AMBIGUOUS_EDGE");
    }
}
