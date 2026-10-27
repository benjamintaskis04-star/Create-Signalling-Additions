package com.benjamintaskis.createsignalextensions.content.speed;

import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.track.*;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Real Create graphs/edges, without a Minecraft world. No proximity mock substitutes. */
public final class GraphAssociationChecks {
    private static int checks;
    private static final Vec3 UP = new Vec3(0, 1, 0), EAST = new Vec3(1, 0, 0), FACE = EAST.scale(-1);
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static final class Graph extends TrackGraph {
        final Map<TrackNodeLocation, TrackNode> nodes = new LinkedHashMap<>();
        final Map<TrackNode, Map<TrackNode, TrackEdge>> links = new IdentityHashMap<>();
        @Override public Set<TrackNodeLocation> getNodes() { return nodes.keySet(); }
        @Override public TrackNode locateNode(TrackNodeLocation location) { return nodes.get(location); }
        @Override public Map<TrackNode, TrackEdge> getConnectionsFrom(TrackNode node) { return links.getOrDefault(node, Map.of()); }
        TrackNode node(double x, double z) {
            var at = new TrackNodeLocation(new Vec3(x, 0, z)); at.dimension = Level.OVERWORLD;
            var node = new TrackNode(at, nodes.size() + 1, UP); nodes.put(at, node); return node;
        }
        TrackEdge connect(TrackNode a, TrackNode b, BezierConnection curve) {
            var edge = new TrackEdge(a, b, curve, null);
            links.computeIfAbsent(a, $ -> new LinkedHashMap<>()).put(b, edge);
            links.computeIfAbsent(b, $ -> new LinkedHashMap<>()).put(a, new TrackEdge(b, a, curve == null ? null : curve.secondary(), null));
            return edge;
        }
    }
    private static final class SavedBoard extends SpeedBoardBlockEntity {
        SavedBoard() { super(null, BlockPos.ZERO, null); }
        @Override public boolean isValidBlockState(BlockState state) { return true; }
        @Override public boolean hasMuPanel() { return false; }
        @Override public boolean hasHighPanel() { return false; }
    }
    private static SpeedBoardGraphLocation.Sample sample(Graph graph, TrackNode a, TrackNode b, double position, boolean reversed) {
        return new SpeedBoardGraphLocation.Sample(graph, a, b, graph.getConnectionsFrom(a).get(b), position, reversed);
    }
    private static BezierConnection curve(BlockPos start, BlockPos end, Vec3 a, Vec3 b) {
        return new BezierConnection(Couple.create(start, end), Couple.create(a, b), Couple.create(EAST, FACE), Couple.create(UP, UP), true, false, null);
    }
    public static void main(String[] args) {
        Graph graph = new Graph();
        TrackNode a = graph.node(0, 0), b = graph.node(10, 0);
        var edge = graph.connect(a, b, null);
        var binding = SpeedBoardGraphResolver.straight(List.of(graph), Level.OVERWORLD, new Vec3(5, 0, 0), EAST, FACE).location();
        check(binding != null && binding.graph() == graph && binding.edge() == edge && binding.position() == 5, "Single straight association");
        check(binding.crossed(sample(graph, a, b, 4, false), sample(graph, a, b, 6, false)) == 1, "Front/rear crosses exact graph position");
        check(!Double.isFinite(binding.crossed(sample(graph, a, b, 6, false), sample(graph, a, b, 7, false))), "Already-passed point never retriggers by proximity");
        check(binding.crossed(sample(graph, b, a, 6, true), sample(graph, b, a, 4, true)) == 1, "Reversed graph point, same physical direction");
        check(!Double.isFinite(binding.crossed(sample(graph, b, a, 4, false), sample(graph, b, a, 6, false))), "Opposite travel rejected");
        check(!Double.isFinite(binding.crossed(null, sample(graph, a, b, 6, false))), "No fabricated crossing on load");
        check(binding.crossed(sample(graph, a, b, 1, false), sample(graph, a, b, 9, false)) == 4, "Fast within-edge crossing cannot skip board");
        // Within old pickup radius, but distinct selected line and graph edge.
        TrackNode p0 = graph.node(0, .5), p1 = graph.node(10, .5);
        graph.connect(p0, p1, null);
        var parallel = SpeedBoardGraphResolver.straight(List.of(graph), Level.OVERWORLD, new Vec3(5, 0, .5), EAST, FACE).location();
        check(parallel != null && parallel.from() == p0, "Saved anchor chooses parallel track, not first nearby edge");
        check(!Double.isFinite(parallel.ahead(sample(graph, a, b, 4, false))), "Parallel board not anticipated");
        check(!Double.isFinite(parallel.crossed(sample(graph, a, b, 4, false), sample(graph, a, b, 6, false))), "Parallel board not enforced");
        var other = SpeedBoardGraphResolver.straight(List.of(graph), Level.OVERWORLD, new Vec3(7, 0, 0), EAST, FACE).location();
        check(other.position() > binding.position() && other.crossed(sample(graph, a, b, 4, false), sample(graph, a, b, 8, false)) == 3,
                "Multiple close boards have ordered graph distances");
        Graph duplicate = new Graph(); var d0 = duplicate.node(0, 0); var d1 = duplicate.node(10, 0); duplicate.connect(d0, d1, null);
        check(SpeedBoardGraphResolver.straight(List.of(graph, duplicate), Level.OVERWORLD, new Vec3(5, 0, 0), EAST, FACE).location() == null,
                "Duplicate candidate graphs rejected, never first-match attached");
        Graph fork = new Graph();
        var before = fork.node(-10, 0); var junction = fork.node(0, 0); var mainEnd = fork.node(10, 0); var branchEnd = fork.node(10, 2);
        var approach = fork.connect(before, junction, null); fork.connect(junction, mainEnd, null);
        BlockPos anchor = BlockPos.ZERO, curveEnd = new BlockPos(10, 0, 2);
        var bc = curve(anchor, curveEnd, Vec3.ZERO, new Vec3(10, 0, 2));
        var branchEdge = fork.connect(junction, branchEnd, bc);
        var curveTarget = new BezierTrackPointLocation(curveEnd, 0);
        var branch = SpeedBoardGraphResolver.curve(List.of(fork), Level.OVERWORLD, anchor, curveTarget, bc, FACE).location();
        check(branch != null && branch.position() == .5 && branch.edge() == branchEdge, "Create segment-0 position immediately after split");
        var prev = sample(fork, before, junction, 9.8, false);
        var main = sample(fork, junction, mainEnd, .8, false);
        var diverging = sample(fork, junction, branchEnd, .8, false);
        check(!Double.isFinite(branch.crossed(prev, main)), "Straight turnout route cannot pick branch board");
        check(Math.abs(branch.crossed(prev, diverging) - .7) < 1.0e-6, "Diverging train crosses branch board even after edge transition");
        check(!Double.isFinite(branch.ahead(main)), "Straight-route advance scout rejects diverging board");
        check(branch.ahead(sample(fork, junction, branchEnd, .1, false)) > 0, "Advance braking detects board before reaching it");
        check(SpeedBrakingEnvelope.permittedKmh(20, 100, 100.0 / 72, .002) < 100, "Existing braking envelope still anticipates restriction");
        for (boolean chooseBranch : new boolean[]{false, true}) for (boolean reversePoint : new boolean[]{false, true}) {
            var target = chooseBranch ? branchEnd : mainEnd;
            var path = List.of(Couple.create(junction, target)); var copy = new ArrayList<>(path);
            var scout = reversePoint ? new TravellingPoint(junction, before, fork.getConnectionsFrom(junction).get(before), .2, false)
                    : new TravellingPoint(before, junction, approach, 9.8, false);
            var start = SpeedBoardGraphLocation.Sample.of(fork, scout, reversePoint);
            scout.travel(fork, reversePoint ? -1 : 1, SpeedRestrictionManager.readOnlyNavigationSelector(copy));
            var end = SpeedBoardGraphLocation.Sample.of(fork, scout, reversePoint);
            check(Double.isFinite(branch.crossed(start, end)) == chooseBranch, "Actual copied route and reverse scout select correct board");
            check(path.size() == 1 && copy.isEmpty(), "Live navigation path remains untouched");
        }
        // Crossover: another physical route intersects in world space, without sharing its graph edge.
        var cross0 = fork.node(0, 2); var cross1 = fork.node(10, 0); fork.connect(cross0, cross1, null);
        check(!Double.isFinite(branch.crossed(sample(fork, cross0, cross1, 4, false), sample(fork, cross0, cross1, 6, false))), "Crossover spatial intersection is not a board passage");
        SavedBoard old = new SavedBoard(); old.setConfiguration(20, 20, 20, false, false, true);
        old.setTrackTarget(anchor, false, curveTarget);
        CompoundTag nbt = new CompoundTag(); old.write(nbt, RegistryAccess.EMPTY, false);
        SavedBoard restored = new SavedBoard(); restored.read(nbt, RegistryAccess.EMPTY, false);
        check(restored.isTurnoutBoard() && restored.getDefaultSpeedKmh() == 20 && restored.getBezierTarget().equals(curveTarget), "Existing saved X link/settings retained");
        check(SpeedBoardGraphResolver.curve(List.of(fork), Level.OVERWORLD, restored.getTargetTrack(), restored.getBezierTarget(), bc, FACE).location() != null,
                "Saved board re-associated without replacement");
        graph.links.get(a).remove(b); graph.links.get(b).remove(a);
        check(!binding.live() && !Double.isFinite(binding.ahead(sample(graph, p0, p1, 4, false))), "Track removal invalidates old association");
        check(SpeedBoardGraphResolver.straight(List.of(graph), Level.OVERWORLD, new Vec3(5, 0, 0), EAST, FACE).location() == null, "Removed track cannot bind parallel line");
        var replacement = graph.connect(a, b, null);
        var rebuilt = SpeedBoardGraphResolver.straight(List.of(graph), Level.OVERWORLD, new Vec3(5, 0, 0), EAST, FACE).location();
        check(rebuilt != null && rebuilt.edge() == replacement && rebuilt.edge() != binding.edge(), "Reconstruction rebinds current edge");
        check(!Double.isFinite(rebuilt.crossed(new SpeedBoardGraphLocation.Sample(graph, a, b, edge, 4, false), sample(graph, a, b, 6, false))), "No fabricated passage across stale edge rebuild");
        var newGraph = SpeedBoardGraphResolver.straight(List.of(duplicate), Level.OVERWORLD, new Vec3(5, 0, 0), EAST, FACE).location();
        check(newGraph != null && newGraph.graph().id.equals(duplicate.id), "Graph identity is rebound from anchor after replacement");
        check(!Double.isFinite(newGraph.crossed(sample(graph, a, b, 4, false), sample(duplicate, d0, d1, 6, false))), "Different graph samples never form a passage");
        // Smoothed transitions use Create's exact nominal ends and yOffsetPixels.
        Graph slope = new Graph();
        var lo = slope.node(0, .5);
        var raisedAt = new TrackNodeLocation(new Vec3(1, 0, .5)); raisedAt.dimension = Level.OVERWORLD; raisedAt.yOffsetPixels = 8;
        var hi = new TrackNode(raisedAt, 2, UP); slope.nodes.put(raisedAt, hi);
        var slopeEdge = slope.connect(lo, hi, null);
        var tilted = SpeedBoardGraphResolver.betweenNodes(List.of(slope), lo.getLocation(), hi.getLocation(), FACE).location();
        check(tilted != null && tilted.edge() == slopeEdge && tilted.position() == slopeEdge.getLength() / 2,
                "Tilted straight selected by exact Create smoothing endpoints");
        check(SpeedBoardGraphLocation.findNode(slope, Level.OVERWORLD, hi.getLocation().getLocation()) == hi,
                "Client coordinate lookup preserves nonzero smoothing offset");
        check(!new TrackNodeLocation(hi.getLocation().getLocation()).equals(hi.getLocation()),
                "Regression demonstrates lossy old client node reconstruction");
        check(SpeedBoardGraphLocation.acceptsFrozenTarget(new Vec3(5, .5, 0), new Vec3(5, 0, 0), new Vec3(5, .5, 0)),
                "Legacy saved rear boundary migrates block-centre height");
        check(!SpeedBoardGraphLocation.acceptsFrozenTarget(new Vec3(7, .5, 0), new Vec3(5, 0, 0), new Vec3(5, .5, 0)),
                "Relinked board cannot release an unrelated frozen rear boundary");
        // Direct adjacent crossing remains decisive even if a long return loop also reaches that edge.
        var loop1 = fork.node(30, 5); var loop2 = fork.node(-20, 5);
        fork.connect(mainEnd, loop1, null); fork.connect(loop1, loop2, null); fork.connect(loop2, junction, null);
        check(Double.isFinite(branch.crossed(prev, diverging)), "Impossible long loop cannot veto observed adjacent passage");
        for (var steer : new TravellingPoint.SteerDirection[]{TravellingPoint.SteerDirection.NONE, TravellingPoint.SteerDirection.RIGHT}) {
            var manual = new TravellingPoint(before, junction, approach, 9.8, false);
            manual.travel(fork, 1, manual.steer(steer, UP));
            check((manual.node2 == branchEnd) == (steer == TravellingPoint.SteerDirection.RIGHT),
                    "Manual advance scout uses Create Navigation's world-UP steering convention");
        }
        check(!com.benjamintaskis.createsignalextensions.config.CommonConfig.SPEED_GRAPH_DIAGNOSTICS.getDefault(), "Graph diagnostics disabled by default");
        SpeedGraphDiagnostics.clear();
        check(SpeedGraphDiagnostics.reserve(0, "one") && !SpeedGraphDiagnostics.reserve(1, "one"), "Per-key diagnostics rate limit");
        for (int i = 0; i < 9; i++) check(SpeedGraphDiagnostics.reserve(1, "key" + i), "Global allowance");
        check(!SpeedGraphDiagnostics.reserve(1, "overflow"), "Global diagnostic cap");
        check(SpeedGraphDiagnostics.reserve(100, "one"), "Rate limit allows later focused sample");
        SpeedGraphDiagnostics.clear();
        System.out.println("Graph association regression checks passed: " + checks);
    }
}
