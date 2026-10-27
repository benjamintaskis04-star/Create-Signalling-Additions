package com.benjamintaskis.createsignalextensions.content.speed;

import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.*;
import java.util.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** An oriented Create graph location; position uses Create's edge-position units. */
public record SpeedBoardGraphLocation(TrackGraph graph, TrackNode from, TrackNode to,
                                      TrackEdge edge, double position) {
    public static TrackNode findNode(TrackGraph graph, ResourceKey<Level> dimension, Vec3 position) {
        TrackNode found = null;
        for (var location : graph.getNodes()) if (location.dimension.equals(dimension) && location.getLocation().equals(position)) {
            if (found != null) return null;
            found = graph.locateNode(location);
        }
        return found;
    }
    public static boolean acceptsFrozenTarget(Vec3 frozen, Vec3 graphTarget, Vec3 legacyTarget) {
        return frozen == null || frozen.distanceToSqr(graphTarget) <= 1.0e-6
                || legacyTarget != null && frozen.distanceToSqr(legacyTarget) <= 1.0e-6;
    }
    public record Sample(TrackGraph graph, TrackNode from, TrackNode to, TrackEdge edge,
                         double position, boolean reversed) {
        public static Sample of(TrackGraph graph, TravellingPoint point, boolean reversed) {
            return new Sample(graph, point.node1, point.node2, point.edge, point.position, reversed);
        }
        TrackNode exit() { return reversed ? from : to; }
        TrackNode entry() { return reversed ? to : from; }
        double remaining() { return reversed ? position : edge.getLength() - position; }
        double entered() { return reversed ? edge.getLength() - position : position; }
    }
    public boolean live() { return graph.getConnectionsFrom(from) != null && graph.getConnectionsFrom(from).get(to) == edge; }
    public boolean sameEdge(Sample sample) {
        return live() && sample != null && sample.graph() == graph &&
                sample.graph().getConnectionsFrom(sample.from()) != null
                && sample.graph().getConnectionsFrom(sample.from()).get(sample.to()) == sample.edge() &&
                (sample.from() == from && sample.to() == to || sample.from() == to && sample.to() == from);
    }
    public double directedPosition(Sample sample) {
        return sample.from() == from ? sample.position() : edge.getLength() - sample.position();
    }
    public boolean applies(Sample sample) {
        return sameEdge(sample) && (sample.from() == from) != sample.reversed();
    }
    /** Signed distance ahead on this edge; never a world-space pickup radius. */
    public double ahead(Sample sample) {
        return applies(sample) ? position - directedPosition(sample) : Double.NaN;
    }
    /**
     * Crossings on the same edge or across one adjacent edge (including a split).
     * Multi-edge jumps are accepted only when the intermediate path is unique.
     * No branch is guessed from its proximity to a world coordinate.
     */
    public double crossed(Sample previous, Sample current) {
        return crossed(previous, current, 0);
    }
    public double crossed(Sample previous, Sample current, double maxDistance) {
        if (previous == null || current == null || previous.graph() != graph || current.graph() != graph
                || previous.reversed() != current.reversed() || !live()) return Double.NaN;
        if (graph.getConnectionsFrom(previous.from()) == null || graph.getConnectionsFrom(current.from()) == null
                || graph.getConnectionsFrom(previous.from()).get(previous.to()) != previous.edge()
                || graph.getConnectionsFrom(current.from()).get(current.to()) != current.edge()) return Double.NaN;
        if (previous.from() == current.from() && previous.to() == current.to()) {
            double before = ahead(previous), after = ahead(current);
            if (maxDistance > 0 && Math.abs(previous.position() - current.position()) > maxDistance) return Double.NaN;
            return Double.isFinite(before) && before >= -1.0e-7 && after < -1.0e-7 ? Math.max(0, before) : Double.NaN;
        }
        // Infer only a unique compatible path connecting the observed endpoints.
        List<List<Sample>> paths = new ArrayList<>();
        if (previous.exit() == current.entry() && maxDistance <= 0) paths.add(List.of(previous, current));
        else if (maxDistance > 0) connect(previous, current, new ArrayList<>(), new HashSet<>(), paths, 0, new int[]{128}, maxDistance);
        if (paths.size() != 1) return Double.NaN;
        double distance = 0;
        for (Sample segment : paths.getFirst()) {
            double before = ahead(segment);
            double available = segment == current ? 0 : segment.remaining();
            if (applies(segment)) {
                if (segment == current) {
                    double crossedOnEntry = current.entered() + before;
                    if (before < -1.0e-7 && crossedOnEntry >= -1.0e-7) return distance + Math.max(0, crossedOnEntry);
                } else if (before >= -1.0e-7 && before <= available + 1.0e-7) return distance + Math.max(0, before);
            }
            distance += available;
        }
        return Double.NaN;
    }
    private static void connect(Sample at, Sample end, List<Sample> path, Set<TrackEdge> seen,
                                List<List<Sample>> result, int depth, int[] budget, double remaining) {
        if (--budget[0] < 0) { result.add(List.of()); return; }
        if (depth > 8) { result.add(List.of()); return; }
        if (result.size() > 1 || !seen.add(at.edge())) return;
        path.add(at);
        if (at.from() == end.from() && at.to() == end.to()) {
            if (end.entered() <= remaining) { path.set(path.size() - 1, end); result.add(List.copyOf(path)); }
        } else if (at.remaining() <= remaining) {
            for (var exit : at.graph().getConnectionsFrom(at.exit()).entrySet()) {
                if (exit.getKey() == at.entry()) continue;
                TrackNode nextFrom = at.reversed() ? exit.getKey() : at.exit();
                TrackNode nextTo = at.reversed() ? at.exit() : exit.getKey();
                TrackEdge next = at.graph().getConnectionsFrom(nextFrom).get(nextTo);
                if (next == null || (at.reversed() ? !next.canTravelTo(at.edge()) : !at.edge().canTravelTo(next))) continue;
                connect(new Sample(at.graph(), nextFrom, nextTo, next,
                        at.reversed() ? next.getLength() : 0, at.reversed()), end, path, seen, result, depth + 1, budget, remaining - at.remaining());
            }
        }
        path.removeLast(); seen.remove(at.edge());
    }
}
