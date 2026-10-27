package com.benjamintaskis.createsignalextensions.content.speed;

import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.BezierTrackPointLocation;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.RegistryAccess;
import java.util.*;

/** Tests ordinary/X parity and Create's actual travelling-point route scout. */
public final class CosmeticXChecks {
    private static int checks;
    // Exercise the real configuration/category code without a world or registered mod blocks.
    private static final class Board extends SpeedBoardBlockEntity {
        Board(int speed, boolean x) {
            super(null, BlockPos.ZERO, null);
            setConfiguration(speed, speed + 10, speed + 20, true, true, x);
        }
        @Override public boolean hasMuPanel() { return true; }
        @Override public boolean hasHighPanel() { return true; }
        @Override public boolean isValidBlockState(net.minecraft.world.level.block.state.BlockState state) { return true; }
    }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static final class Graph extends TrackGraph {
        final Map<TrackNode, Map<TrackNode, TrackEdge>> links = new IdentityHashMap<>();
        @Override public Map<TrackNode, TrackEdge> getConnectionsFrom(TrackNode node) {
            return links.getOrDefault(node, Map.of());
        }
        TrackEdge connect(TrackNode a, TrackNode b) {
            TrackEdge edge = new TrackEdge(a, b, null, null);
            links.computeIfAbsent(a, $ -> new LinkedHashMap<>()).put(b, edge);
            links.computeIfAbsent(b, $ -> new LinkedHashMap<>()).put(a, new TrackEdge(b, a, null, null));
            return edge;
        }
    }
    private static TrackNode node(double x, double z, int id) {
        var location = new TrackNodeLocation(new Vec3(x, 0, z));
        location.dimension = Level.OVERWORLD;
        return new TrackNode(location, id, new Vec3(0, 1, 0));
    }
    public static void main(String[] args) {
        for (int speed : new int[]{20, 40}) for (int sign : new int[]{-1, 1}) {
            Board ordinaryBoard = new Board(speed, false), xBoard = new Board(speed, true);
            for (var category : TrainSpeedCategory.values()) for (boolean manual : new boolean[]{false, true}) {
                check(ordinaryBoard.getApplicableSpeedKmh(category, manual) == xBoard.getApplicableSpeedKmh(category, manual),
                        "Real board configuration gives identical ordinary/X category and manual speeds");
            }
            CompoundTag saved = new CompoundTag();
            xBoard.write(saved, RegistryAccess.EMPTY, false);
            check(saved.getBoolean("TurnoutMode") && saved.getInt("DefaultSpeedKmh") == speed,
                    "X toggle and numeric settings retain the existing saved-world format");
            Board restored = new Board(60, false);
            restored.read(saved, RegistryAccess.EMPTY, false);
            check(restored.isTurnoutBoard() && restored.getDefaultSpeedKmh() == speed
                    && restored.getMuSpeedKmh() == speed + 10 && restored.getHighSpeedKmh() == speed + 20,
                    "Existing X board restores its prefix and all speed panels without replacement");
            Vec3 motion = new Vec3(sign, 0, 0), face = motion.scale(-1);
            // X is not an input to the shared passage/direction or braking rules.
            check(SpeedBoardRules.validDirection(motion, face), "20/40 and X20/X40 apply facing travel");
            check(!SpeedBoardRules.validDirection(motion.scale(-1), face), "20/40 and X20/X40 reject travel from behind");
            check(!SpeedBoardRules.requiresRearClearance(100, speed), "Lower limit applies to front, with or without X");
            check(SpeedBoardRules.requiresRearClearance(10, speed), "Higher limit waits for rear, with or without X");
            check(!SpeedBoardRules.requiresRearClearance(speed, speed), "Equal limit does not arm a turnout instruction");
            check(SpeedBoardRules.requiresRearClearance(speed, null), "Unrestricted still waits for rear");
            for (double distance : new double[]{0, .2, 10, 100}) {
                double ordinary = SpeedBrakingEnvelope.permittedKmh(ordinaryBoard.getApplicableSpeedKmh(TrainSpeedCategory.DEFAULT, false), distance, sign * 100.0 / 72, .002);
                double prefixed = SpeedBrakingEnvelope.permittedKmh(xBoard.getApplicableSpeedKmh(TrainSpeedCategory.DEFAULT, false), distance, sign * 100.0 / 72, .002);
                check(ordinary == prefixed, "Existing envelope identical for X and ordinary in either direction");
            }
            Graph graph = new Graph();
            TrackNode a = node(-10 * sign, 0, 1), junction = node(0, 0, 2);
            TrackNode straight = node(10 * sign, 0, 3), branch = node(10 * sign, 2 * sign, 4);
            TrackEdge approach = graph.connect(a, junction);
            TrackEdge main = graph.connect(junction, straight), diverging = graph.connect(junction, branch);
            Vec3 branchBoard = diverging.getPosition(graph, .02).add(0, .5, 0);
            check(SpeedBoardRouteMatch.matches(graph, diverging, branchBoard, BlockPos.ZERO, null),
                    "Board immediately beyond turnout belongs to diverging edge");
            check(!SpeedBoardRouteMatch.matches(graph, main, branchBoard, BlockPos.ZERO, null),
                    "Nearby diverging board cannot cause main-route advance braking");
            check(!SpeedBoardRouteMatch.matches(graph, approach, branchBoard, BlockPos.ZERO, null),
                    "Approach proximity does not invent a chosen route");
            for (boolean chooseBranch : new boolean[]{false, true}) {
                for (boolean reversePoint : new boolean[]{false, true}) {
                TrackNode target = chooseBranch ? branch : straight;
                List<Couple<TrackNode>> originalPath = List.of(Couple.create(junction, target));
                var pathCopy = new ArrayList<>(originalPath);
                var scout = reversePoint
                        ? new TravellingPoint(junction, a, graph.getConnectionsFrom(junction).get(a), .5, false)
                        : new TravellingPoint(a, junction, approach, 9.5, false);
                double moved = Math.abs(scout.travel(graph, reversePoint ? -.75 : .75,
                        SpeedRestrictionManager.readOnlyNavigationSelector(pathCopy)));
                check(moved > .7 && (reversePoint ? scout.node1 : scout.node2) == target,
                        "Create scout follows copied actual route in forward and reverse point orientation");
                check(originalPath.size() == 1 && pathCopy.isEmpty(), "Scout consumes only its private path copy");
                boolean seesBoard = SpeedBoardRouteMatch.matches(graph, scout.edge, branchBoard, BlockPos.ZERO, null);
                check(seesBoard == chooseBranch, "Only actual branch route anticipates its 20/40 board");
                if (seesBoard) check(SpeedBrakingEnvelope.permittedKmh(speed, .2, sign * 100.0 / 72, .002) < 100,
                        "Ordinary and X restrictions beyond turnout both use existing advance envelope");
                }
            }
        }
        Graph curved = new Graph();
        TrackNode c0 = node(0, 0, 1), c1 = node(10, 2, 2);
        BlockPos start = BlockPos.ZERO, end = new BlockPos(10, 0, 2);
        var curve = new BezierConnection(Couple.create(start, end),
                Couple.create(Vec3.ZERO, new Vec3(10, 0, 2)),
                Couple.create(new Vec3(1, 0, 0), new Vec3(-1, 0, 0)),
                Couple.create(new Vec3(0, 1, 0), new Vec3(0, 1, 0)), true, false, null);
        var curveEdge = new TrackEdge(c0, c1, curve, null);
        check(SpeedBoardRouteMatch.matches(curved, curveEdge, Vec3.ZERO, start, new BezierTrackPointLocation(end, 0)),
                "Curve-linked restriction matches actual primary connection");
        check(SpeedBoardRouteMatch.matches(curved, new TrackEdge(c1, c0, curve.secondary(), null),
                Vec3.ZERO, start, new BezierTrackPointLocation(end, 0)), "Curve match accepts reverse edge orientation");
        check(!SpeedBoardRouteMatch.matches(curved, curveEdge, Vec3.ZERO, start,
                new BezierTrackPointLocation(new BlockPos(10, 0, -2), 0)), "Neighbouring curve cannot supply a restriction");
        check(!SpeedBoardRules.validDirection(Vec3.ZERO, new Vec3(1, 0, 0)), "Stationary direction guard preserved");
        System.out.println("Cosmetic X operational/actual-route checks passed: " + checks);
    }
}
