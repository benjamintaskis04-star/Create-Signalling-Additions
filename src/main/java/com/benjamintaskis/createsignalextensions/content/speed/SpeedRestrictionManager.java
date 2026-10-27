package com.benjamintaskis.createsignalextensions.content.speed;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import com.benjamintaskis.createsignalextensions.config.CommonConfig;
import com.benjamintaskis.createsignalextensions.content.signal.GroundShuntSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.MainlineAddonLampBlock;
import com.benjamintaskis.createsignalextensions.content.signal.ShuntDisplayedAspect;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.entity.Navigation;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackNode;
import net.createmod.catnip.data.Couple;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;
import com.benjamintaskis.createsignalextensions.network.TrainSpeedHudPayload;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Objects;
import com.simibubi.create.content.trains.graph.TrackGraph;

/**
 * Alpha 2 speed-restriction foundation.
 *
 * Posted and shunt limits are independent sources; the lowest active limit wins.
 * A speed board replaces/clears the posted limit, while End of Shunt clears only
 * the shunt-authority limit. With no active limit Create retains full control.
 *
 * Important Create unit note:
 * Train.speed and Train.targetSpeed are blocks PER TICK. Create's configured
 * train speeds are blocks/second and Train.maxSpeed() divides them by 20. A
 * railway limit expressed in km/h must therefore be divided by 3.6 and then 20,
 * i.e. divided by 72, before comparing it with Train.speed.
 */
public final class SpeedRestrictionManager {
    private static final double TRIGGER_RADIUS = 2.75;
    private static final double TRIGGER_RADIUS_SQR = TRIGGER_RADIUS * TRIGGER_RADIUS;
    private static final double KMH_TO_BLOCKS_PER_TICK = 1.0 / 72.0;
    private static final double ADVANCE_STEP_BLOCKS = 1.0;
    // Comfortable railway-style service braking used to form the approach envelope.
    // v^2 = u^2 + 2as, converted from m/s to km/h (3.6^2 = 12.96).

    private static final Map<UUID, TrainSpeedState> STATES = new HashMap<>();
    private static Field navigationCurrentPathField;
    private static boolean navigationCurrentPathLookupAttempted;

    /**
     * Loaded track-linked signs, indexed by dimension and their block position.
     *
     * Alpha 2c searched for a sign block around the train and only afterwards
     * compared the train with the selected track point. That fails whenever the
     * sign itself is several blocks away from the track. The registry lets us
     * compare directly with the selected track point, regardless of where the
     * sign model was mounted.
     */
    private static final Map<ResourceKey<Level>, Map<BlockPos, TrackLinkedBlockEntity>> TRACK_LINKED_SIGNS = new HashMap<>();

    private SpeedRestrictionManager() {}

    public static List<TrackLinkedBlockEntity> trackLinkedSnapshot(ServerLevel level) {
        var signs = TRACK_LINKED_SIGNS.get(level.dimension());
        return signs == null ? List.of() : List.copyOf(signs.values());
    }

    /** Diagnostic-only bounded view; does not load chunks, restore state or change the registry. */
    public static List<TrackLinkedBlockEntity> trackLinkedSnapshot(ServerLevel level, int maximum) {
        if (maximum < 0) throw new IllegalArgumentException("negative snapshot bound");
        var signs = TRACK_LINKED_SIGNS.get(level.dimension());
        return signs == null ? List.of() : signs.values().stream().limit(maximum).toList();
    }

    public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        STATES.clear();
        TRACK_LINKED_SIGNS.clear();
        SpeedZoneSync.clear();
        SpeedBoardGraphResolver.clear();
        SpeedGraphDiagnostics.clear();
    }

    public static void registerTrackLinked(TrackLinkedBlockEntity be) {
        if (be == null || be.getLevel() == null || be.getLevel().isClientSide)
            return;
        TRACK_LINKED_SIGNS
                .computeIfAbsent(be.getLevel().dimension(), $ -> new LinkedHashMap<>())
                .put(be.getBlockPos().immutable(), be);
    }

    public static void unregisterTrackLinked(TrackLinkedBlockEntity be) {
        if (be == null || be.getLevel() == null || be.getLevel().isClientSide)
            return;
        Map<BlockPos, TrackLinkedBlockEntity> byPos = TRACK_LINKED_SIGNS.get(be.getLevel().dimension());
        if (byPos == null)
            return;
        byPos.remove(be.getBlockPos());
        if (byPos.isEmpty())
            TRACK_LINKED_SIGNS.remove(be.getLevel().dimension());
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        TrainMovementModeManager.validateManualShunts(event.getServer().overworld());
        MinecraftServer server = event.getServer();
        // Create's registry includes virtual/unloaded and temporarily graphless
        // trains. Prune against that registry, never against this tick's scan list.
        if (server.getTickCount() % 200 == 0)
            SpeedRestrictionSavedData.get(server.overworld()).retainTrains(Create.RAILWAYS.trains.keySet());
        Set<UUID> live = new HashSet<>();

        for (Train train : Create.RAILWAYS.trains.values()) {
            if (train == null || train.id == null || train.graph == null || train.carriages == null || train.carriages.isEmpty())
                continue;

            live.add(train.id);
            TrainSpeedState state = STATES.computeIfAbsent(train.id, id -> restoreState(server, id));
            state.manualShuntActive = TrainMovementModeManager.isManualShunting(server.overworld(), train);
            boolean playerControlled = isPlayerControlled(train);
            scanTriggers(server, train, state);
            scanUpcomingRestriction(server, train, state, playerControlled);
            reportEffectiveLimitChange(train, state);
            updatePlayerSpeedLimit(train, state, playerControlled);
            enforce(train, state, playerControlled);
            syncPlayerSpeedHud(server, train, state, playerControlled);
            state.playerControlledLastTick = playerControlled;
            persistState(server, train.id, state);
        }

        Iterator<UUID> it = STATES.keySet().iterator();
        while (it.hasNext())
            if (!live.contains(it.next()))
                it.remove();
    }

    private static TrainSpeedState restoreState(MinecraftServer server, UUID id) {
        SpeedRestrictionSavedData.Entry entry = SpeedRestrictionSavedData.get(server.overworld()).get(id);
        if (CommonConfig.speedDiagnostics())
            CreateSignallingAdditions.LOGGER.info("[CSA SpeedDiag] RESTORE trainId={} saved={} posted={} route={} shunt={} pending={}",
                    id, entry != null, entry == null ? null : entry.postedLimit(),
                    entry == null ? null : entry.routeLimit(), entry != null && entry.shuntAuthority(),
                    entry == null ? 0 : entry.boundaries().size());
        return restoreState(entry);
    }

    static TrainSpeedState restoreState(SpeedRestrictionSavedData.Entry entry) {
        TrainSpeedState state = new TrainSpeedState();
        if (entry != null) {
            state.postedLimitKmh = entry.postedLimit();
            state.routeLimitKmh = entry.routeLimit();
            state.shuntAuthority = entry.shuntAuthority();
            state.routeRestrictionSerial = entry.routeSerial();
            for (SpeedRestrictionSavedData.Boundary b : entry.boundaries()) {
                ResourceLocation location = ResourceLocation.tryParse(b.dimension());
                if (location != null && state.pendingHigherBoundaries.stream().noneMatch(
                        existing -> existing.key.equals(b.key()) && existing.dimension.equals(
                                ResourceKey.create(Registries.DIMENSION, location)))) {
                    var boundary = new PendingSpeedBoundary(
                        b.key(), b.limitKmh(), b.signPos(),
                        ResourceKey.create(Registries.DIMENSION, location), b.clearsRoute(), b.routeSerial(), b.target(), b.forward());
                    boundary.anchor = b.anchor();
                    boundary.order = state.pendingHigherBoundaries.size() - entry.boundaries().size();
                    state.pendingHigherBoundaries.add(boundary);
                }
            }
            state.instructionSerial++;
        }
        return state;
    }

    private static void persistState(MinecraftServer server, UUID id, TrainSpeedState state) {
        saveState(SpeedRestrictionSavedData.get(server.overworld()), id, state);
    }

    static void saveState(SpeedRestrictionSavedData data, UUID id, TrainSpeedState state) {
        data.update(id, state.postedLimitKmh,
                state.routeLimitKmh, state.shuntAuthority, state.routeRestrictionSerial,
                state.pendingHigherBoundaries.stream().map(b -> new SpeedRestrictionSavedData.Boundary(
                        b.key, b.limitKmh, b.signPos, b.dimension.location().toString(),
                        b.clearsRoute, b.routeSerial, b.target, b.forward, b.anchor)).toList());
    }

    private static void scanTriggers(MinecraftServer server, Train train, TrainSpeedState state) {
        Set<String> now = new HashSet<>();

        // Scan the leading end every tick so even a fast Create train cannot jump
        // over a selected track point between signalling refreshes.
        Carriage frontCarriage = TrainTravelDirection.reversed(train)
                ? train.carriages.get(train.carriages.size() - 1)
                : train.carriages.get(0);
        TravellingPoint frontPoint = TrainTravelDirection.reversed(train)
                ? frontCarriage.getTrailingPoint()
                : frontCarriage.getLeadingPoint();

        scanPoint(server, train, state, frontPoint, now);

        // Speed-board front/rear passages are observed around Train.tick, including
        // the first movement after load. This post-tick scanner retains shunt logic.

        state.nearbyTriggers.clear();
        state.nearbyTriggers.addAll(now);
    }

    private static void scanPoint(MinecraftServer server, Train train, TrainSpeedState state,
                                  TravellingPoint point, Set<String> now) {
        if (point == null || point.edge == null || point.node1 == null || train.graph == null)
            return;

        ResourceKey<Level> dimension = point.node1.getLocation().dimension;
        ServerLevel level = server.getLevel(dimension);
        if (level == null)
            return;

        Vec3 position = point.getPosition(train.graph);
        Vec3 motion = null;
        boolean reversed = TrainTravelDirection.reversed(train);
        if (state.lastFrontPosition != null && dimension.equals(state.lastFrontDimension)
                && state.lastFrontReversed == reversed)
            motion = position.subtract(state.lastFrontPosition);
        state.lastFrontPosition = position;
        state.lastFrontDimension = dimension;
        state.lastFrontReversed = reversed;

        // Track-linked equipment is checked against its SELECTED TRACK POINT, not
        // against the physical sign block. This fixes speed boards mounted a few
        // blocks away from the line and works for both straight and Bezier targets.
        scanTrackLinkedSigns(server, level, train, state, position, motion, dimension, now);

        // Ground shunt signals are not track-linked BEs, so retain a small local
        // block scan for SHUNT PROCEED authority.
        BlockPos center = BlockPos.containing(position);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -4, -4), center.offset(4, 4, 4))) {
            if (Vec3.atCenterOf(pos).distanceToSqr(position) > TRIGGER_RADIUS_SQR)
                continue;
            // Beta 2a: this tick handler must never synchronously request an
            // unloaded chunk. A missed point will be reconsidered on a later tick
            // after normal Minecraft/Create chunk loading makes it available.
            if (!level.hasChunkAt(pos))
                continue;

            var blockState = level.getBlockState(pos);
            boolean shuntProceed = blockState.getBlock() instanceof GroundShuntSignalBlock
                    && blockState.getValue(GroundShuntSignalBlock.ASPECT) == ShuntDisplayedAspect.SHUNT_PROCEED;
            boolean mainlineShunt = blockState.getBlock() instanceof MainlineAddonLampBlock lamp
                    && lamp.contains(blockState, MainlineAddonLampBlock.Kind.SHUNT)
                    && (blockState.getValue(MainlineAddonLampBlock.LIT)
                        || blockState.getValue(MainlineAddonLampBlock.SHUNT_LIT));
            if (!shuntProceed && !mainlineShunt) continue;

            String key = dimension.location() + ":shunt:" + pos.asLong();
            now.add(key);
            if (state.nearbyTriggers.contains(key)) continue;
            state.shuntAuthority = true;
            state.instructionSerial++;
            log(train, "passed SHUNT PROCEED at " + pos.toShortString()
                    + "; shunt restriction " + CommonConfig.shuntProceedSpeedKmh() + " km/h active");
        }
    }

    private static void scanTrackLinkedSigns(MinecraftServer server, ServerLevel level, Train train, TrainSpeedState state,
                                             Vec3 trainPosition, Vec3 motion, ResourceKey<Level> dimension, Set<String> now) {
        Map<BlockPos, TrackLinkedBlockEntity> byPos = TRACK_LINKED_SIGNS.get(dimension);
        if (byPos == null || byPos.isEmpty())
            return;

        Iterator<Map.Entry<BlockPos, TrackLinkedBlockEntity>> iterator = byPos.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, TrackLinkedBlockEntity> entry = iterator.next();
            TrackLinkedBlockEntity linked = entry.getValue();

            // Discard stale entries left by a chunk unload/removal edge case.
            if (linked == null || linked.isRemoved() || linked.getLevel() != level) {
                iterator.remove();
                continue;
            }
            if (linked instanceof SpeedBoardBlockEntity || !linked.hasTrackTarget())
                continue;

            Vec3 target = linked.getTrackTargetPosition(level);
            if (target == null || target.distanceToSqr(trainPosition) > TRIGGER_RADIUS_SQR)
                continue;

            // Beta 2: track-linked signs are directional. The front of the train
            // must be moving INTO the visible face of the placed sign. Passing the
            // same linked point from behind is ignored completely. This uses actual
            // front-point motion, so it remains correct on curves and when reversing.
            if (!isValidSignDirection(linked, motion))
                continue;

            BlockPos signPos = linked.getBlockPos();
            String type = linked instanceof SpeedBoardBlockEntity ? "speed" : "end_shunt";
            String key = dimension.location() + ":" + type + ":" + signPos.asLong();
            now.add(key);
            if (state.nearbyTriggers.contains(key))
                continue;

            if (linked instanceof EndOfShuntSignBlockEntity) {
                if (state.shuntAuthority)
                    log(train, "passed End of Shunt at " + signPos.toShortString()
                            + "; shunt restriction cleared");
                state.shuntAuthority = false;
                state.instructionSerial++;
            }
        }

        if (byPos.isEmpty())
            TRACK_LINKED_SIGNS.remove(dimension);
    }

    // At most 128 actual movement observations per train, never an unbounded route history.
    static final int BOUNDARY_HISTORY = 128;

    record BoundaryBoard(String key, Integer limit, BlockPos sign, ResourceKey<Level> dimension,
                         SpeedBoardGraphLocation location, Vec3 legacyTarget, Object configuration) {}
    private record BoardConfiguration(BlockPos track, Object curve, Direction facing, Integer limit) {}

    static Set<ResourceKey<Level>> boundaryDimensions(TravellingPoint physicalFront, TravellingPoint physicalRear) {
        Set<ResourceKey<Level>> dimensions = new HashSet<>();
        if (physicalFront != null && physicalFront.node1 != null) dimensions.add(physicalFront.node1.getLocation().dimension);
        if (physicalRear != null && physicalRear.node1 != null) dimensions.add(physicalRear.node1.getLocation().dimension);
        return dimensions;
    }
    private static List<BoundaryBoard> boundaryBoards(MinecraftServer server, Train train) {
        List<BoundaryBoard> result = new ArrayList<>();
        // Create passes the overworld to Train.tick even for trains elsewhere.
        for (var dimension : boundaryDimensions(train.carriages.getFirst().getLeadingPoint(), train.carriages.getLast().getTrailingPoint())) {
            var level = server.getLevel(dimension);
            if (level != null) result.addAll(boundaryBoards(level, train));
        }
        return result;
    }

    private static List<BoundaryBoard> boundaryBoards(ServerLevel level, Train train) {
        var signs = TRACK_LINKED_SIGNS.get(level.dimension());
        if (signs == null) return List.of();
        boolean manual = isPlayerControlled(train);
        var category = TrainCategoryManager.getEffectiveCategory(level, train);
        List<BoundaryBoard> result = new ArrayList<>();
        for (var linked : signs.values()) {
            if (!(linked instanceof SpeedBoardBlockEntity board) || board.isRemoved()
                    || board.getLevel() != level || !board.hasTrackTarget()) continue;
            var location = SpeedBoardGraphResolver.resolve(level, board).location();
            Integer limit = board.isUnrestricted() ? null : board.getApplicableSpeedKmh(category, manual);
            var configuration = new BoardConfiguration(board.getTargetTrack(), board.getBezierTarget(),
                    board.getBlockState().getValue(SpeedBoardBlock.FACING), limit);
            String key = level.dimension().location() + ":speed:" + board.getBlockPos().asLong();
            result.add(new BoundaryBoard(key, limit, board.getBlockPos().immutable(), level.dimension(), location,
                    location == null ? null : board.getTrackTargetPosition(level), configuration));
        }
        return result;
    }

    /** Called by Train.tick HEAD, after Create's earlyTick reattachment and before navigation/movement. */
    public static void beforeTrainMovement(Train train, Level level) {
        if (!(level instanceof ServerLevel serverLevel) || train.id == null
                || Create.RAILWAYS.trains.get(train.id) != train || train.graph == null || train.carriages.isEmpty()) return;
        var state = STATES.computeIfAbsent(train.id, id -> restoreState(serverLevel.getServer(), id));
        beginBoundaryMovement(state, train.graph, train.carriages.getFirst().getLeadingPoint(),
                train.carriages.getLast().getTrailingPoint(), boundaryBoards(serverLevel.getServer(), train));
    }

    /** Called at Train.tick RETURN. Navigation may have changed direction since HEAD. */
    public static void afterTrainMovement(Train train, Level level) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        var state = STATES.get(train.id);
        if (state == null || train.graph == null || train.carriages.isEmpty()) return;
        var boards = boundaryBoards(serverLevel.getServer(), train);
        finishBoundaryMovement(state, train.graph, train.carriages.getFirst().getLeadingPoint(),
                train.carriages.getLast().getTrailingPoint(), movementReversed(state, TrainTravelDirection.reversed(train)),
                movementBudget(state, train.speed), boards);
        if (CommonConfig.speedGraphDiagnostics()) {
            for (var event : state.boundaryEvents)
                SpeedGraphDiagnostics.log(serverLevel.getServer().getTickCount(), train.id, event.sign, event.phase,
                        "VERIFIED_GRAPH_PASSAGE", event.location, event.phase.equals("REAR") ? state.lastRearGraph : state.lastFrontGraph,
                        "order=" + event.order + " pending=" + state.pendingHigherBoundaries.size() + " effective=" + state.effectiveLimitKmh());
            for (var board : boards) SpeedGraphDiagnostics.log(serverLevel.getServer().getTickCount(), train.id, board.sign(), "OBSERVATION",
                    board.location() == null ? "ASSOCIATION_UNAVAILABLE" : "GRAPH_BOUND_OBSERVATION",
                    board.location(), state.lastFrontGraph, "pending=" + state.pendingHigherBoundaries.size());
        }
    }

    /** First carriage only: signals/stations can zero speed during travel before Train.tick returns. */
    public static void beforeCarriageTravel(Train train, Level level) {
        if (!(level instanceof ServerLevel)) return;
        var state = STATES.get(train.id);
        if (state != null) captureMovementCommand(state, train.speed);
    }
    static void captureMovementCommand(TrainSpeedState state, double speed) {
        if (state.boundaryPrepared && !state.boundaryTravelStarted) {
            state.boundaryTravelStarted = true;
            state.boundaryMovementSpeed = speed;
        }
    }
    static boolean movementReversed(TrainSpeedState state, boolean fallback) {
        return state.boundaryTravelStarted && Math.abs(state.boundaryMovementSpeed) > 1.0e-8
                ? state.boundaryMovementSpeed < 0 : fallback;
    }
    static double movementBudget(TrainSpeedState state, double afterSpeed) {
        return Math.max(Math.abs(state.boundaryMovementSpeed), Math.abs(afterSpeed)) * 2 + 1;
    }

    private record Observation(SpeedBoardGraphLocation.Sample point, SpeedBoundaryAnchor anchor) {
        static Observation of(TrackGraph graph, TravellingPoint point, Observation previous) {
            if (point == null || point.edge == null || point.node1 == null || point.node2 == null) return null;
            var sample = SpeedBoardGraphLocation.Sample.of(graph, point, false);
            // Geometry is immutable for a live Create edge; share its frozen identity until edge replacement.
            var anchor = previous != null && previous.point.edge() == point.edge && previous.point.graph() == graph
                    ? new SpeedBoundaryAnchor(previous.anchor.identity(), point.position)
                    : SpeedBoundaryAnchor.capture(new SpeedBoardGraphLocation(graph, point.node1, point.node2, point.edge, point.position));
            return new Observation(sample, anchor);
        }
        SpeedBoardGraphLocation.Sample on(TrackGraph graph, boolean reversed) {
            var p = point;
            if (p.graph() == graph && graph.getConnectionsFrom(p.from()) != null
                    && graph.getConnectionsFrom(p.from()).get(p.to()) == p.edge())
                return new SpeedBoardGraphLocation.Sample(graph, p.from(), p.to(), p.edge(), p.position(), reversed);
            var rebound = anchor.resolve(graph);
            return rebound == null ? null : new SpeedBoardGraphLocation.Sample(graph, rebound.from(), rebound.to(),
                    rebound.edge(), rebound.position(), reversed);
        }
    }
    private record Movement(long sequence, Observation frontBefore, Observation frontAfter,
                            Observation rearBefore, Observation rearAfter, boolean reversed, double budget) {
        double crossing(SpeedBoardGraphLocation location, boolean rear, boolean historical) {
            var before = rear ? rearBefore : frontBefore; var after = rear ? rearAfter : frontAfter;
            if (before == null || after == null) return Double.NaN;
            var a = before.on(location.graph(), reversed); var b = after.on(location.graph(), reversed);
            if (a == null || b == null) return Double.NaN;
            if (!historical && a.edge() == before.point.edge() && b.edge() == after.point.edge())
                return location.crossed(a, b, budget);
            // Never infer an old route through today's intermediate topology. Replay proves
            // only an observed same edge or an observed direct adjacency, with exact geometry.
            if (before.anchor.identity().equals(after.anchor.identity())) return location.crossed(a, b, budget);
            String exit = reversed ? "From" : "To", entry = reversed ? "To" : "From";
            if (!before.anchor.identity().getCompound(exit).equals(after.anchor.identity().getCompound(entry))
                    || !before.anchor.identity().getString(reversed ? "Dimension" : "ToDimension")
                    .equals(after.anchor.identity().getString(reversed ? "ToDimension" : "Dimension"))
                    || a.exit() != b.entry() || a.remaining() + b.entered() > budget) return Double.NaN;
            return location.crossed(a, b, 0);
        }
        double order(double distance) { return sequence + Math.clamp(distance / (budget + 1), 0, .999999); }
    }
    private static final class BoardCursor {
        Object configuration;
        long through, lastSeen;
        BoardCursor(Object configuration, long sequence) { this.configuration = configuration; through = sequence - 1; lastSeen = sequence; }
    }
    private record BoundaryEvent(String phase, BlockPos sign, SpeedBoardGraphLocation location, double order) {}
    private static void boundaryEvent(TrainSpeedState state, String phase, PendingSpeedBoundary boundary) {
        if (state.boundaryEvents.size() < 8) state.boundaryEvents.add(new BoundaryEvent(phase,boundary.signPos,boundary.resolved,boundary.order));
    }

    static void beginBoundaryMovement(TrainSpeedState state, TrackGraph graph, TravellingPoint physicalFront,
                                      TravellingPoint physicalRear, List<BoundaryBoard> boards) {
        state.boundarySequence++;
        state.boundaryEvents.clear();
        state.beforePhysicalFront = Observation.of(graph, physicalFront, state.beforePhysicalFront);
        state.beforePhysicalRear = Observation.of(graph, physicalRear, state.beforePhysicalRear);
        state.boundaryPrepared = true;
        state.boundaryTravelStarted = false;
        state.boundaryMovementSpeed = 0;
        state.preparedBoards = boards;
        rememberBoards(state, boards);
    }

    private static void rememberBoards(TrainSpeedState state, List<BoundaryBoard> boards) {
        for (var board : boards) {
            var cursor = state.boardCursors.get(board.key());
            if (cursor == null || !Objects.equals(cursor.configuration, board.configuration())) {
                cursor = new BoardCursor(board.configuration(), state.boundarySequence);
                state.boardCursors.put(board.key(), cursor);
            }
            cursor.lastSeen = state.boundarySequence;
        }
        long oldest = state.movements.isEmpty() ? state.boundarySequence : state.movements.getFirst().sequence;
        // Removed/unloaded board metadata must not accumulate forever while a train is stopped.
        state.boardCursors.values().removeIf(c -> c.lastSeen < oldest || state.boundarySequence - c.lastSeen > BOUNDARY_HISTORY);
    }

    static void finishBoundaryMovement(TrainSpeedState state, TrackGraph graph, TravellingPoint physicalFront,
                                       TravellingPoint physicalRear, boolean reversed, double budget, List<BoundaryBoard> boards) {
        if (!state.boundaryPrepared) return; // A post-movement initial point is never proof of passage/non-passage.
        state.boundaryPrepared = false;
        // Retain observations from the dimension left during this movement, including a portal transition.
        if (!state.preparedBoards.isEmpty()) {
            Map<String, BoundaryBoard> combined = new LinkedHashMap<>();
            for (var board : state.preparedBoards) combined.put(board.key(), board);
            for (var board : boards) combined.put(board.key(), board);
            boards = List.copyOf(combined.values());
            state.preparedBoards = List.of();
        }
        var afterFront = Observation.of(graph, physicalFront, state.beforePhysicalFront);
        var afterRear = Observation.of(graph, physicalRear, state.beforePhysicalRear);
        var movement = new Movement(state.boundarySequence,
                reversed ? state.beforePhysicalRear : state.beforePhysicalFront, reversed ? afterRear : afterFront,
                reversed ? state.beforePhysicalFront : state.beforePhysicalRear, reversed ? afterFront : afterRear,
                reversed, budget);
        if (moved(state.beforePhysicalFront, afterFront) || moved(state.beforePhysicalRear, afterRear)) state.movements.addLast(movement);
        while (state.movements.size() > BOUNDARY_HISTORY) state.movements.removeFirst();
        state.lastFrontGraph = movement.frontAfter == null ? null : movement.frontAfter.on(graph, reversed);
        state.lastRearGraph = movement.rearAfter == null ? null : movement.rearAfter.on(graph, reversed);
        rememberBoards(state, boards);

        record Passage(BoundaryBoard board, double order) {}
        List<Passage> passages = new ArrayList<>();
        for (var board : boards) {
            if (board.location() == null || !board.location().live()) continue;
            var cursor = state.boardCursors.get(board.key());
            for (var iterator = state.movements.descendingIterator(); iterator.hasNext();) {
                var frame = iterator.next();
                if (frame.sequence <= cursor.through) break;
                double crossed = frame.crossing(board.location(), false, frame.sequence != state.boundarySequence);
                if (Double.isFinite(crossed)) passages.add(new Passage(board, frame.order(crossed)));
            }
            cursor.through = state.boundarySequence;
        }
        passages.sort(Comparator.comparingDouble(Passage::order));
        for (var passage : passages) enterBoundary(state, passage.board, passage.order);
        reconcileRear(state, graph, boards);
    }

    private static boolean moved(Observation before, Observation after) {
        return before != null && after != null && (before.point.graph() != after.point.graph()
                || before.point.from() != after.point.from() || before.point.to() != after.point.to()
                || before.point.position() != after.point.position());
    }

    static void enterBoundary(TrainSpeedState state, BoundaryBoard board, double order) {
        if (order < state.lastClearedBoundaryOrder || state.pendingHigherBoundaries.stream()
                .anyMatch(b -> b.key.equals(board.key()) && b.dimension.equals(board.dimension()))) return;
        var location = board.location();
        var target = location.edge().getPosition(location.graph(), location.position() / location.edge().getLength());
        var boundary = new PendingSpeedBoundary(board.key(), board.limit(), board.sign(), board.dimension(),
                true, state.routeRestrictionSerial, target, location.edge().getDirectionAt(location.position()));
        boundary.anchor = SpeedBoundaryAnchor.capture(location); boundary.order = order;
        boundary.resolved = location;
        // Every entered section stays represented until a verified rear passage supersedes it.
        if (!SpeedBoardRules.requiresRearClearance(state.effectiveLimitKmh(), board.limit())) state.routeLimitKmh = null;
        state.pendingHigherBoundaries.add(boundary);
        state.pendingHigherBoundaries.sort(Comparator.comparingDouble(b -> b.order));
        state.instructionSerial++;
        boundaryEvent(state, order < state.boundarySequence ? "REPLAY" : "FRONT", boundary);
    }

    private static void reconcileRear(TrainSpeedState state, TrackGraph graph, List<BoundaryBoard> boards) {
        record Clearance(PendingSpeedBoundary boundary, double order) {}
        List<Clearance> clearances = new ArrayList<>();
        for (var boundary : state.pendingHigherBoundaries) {
            var location = boundary.resolved;
            if (location == null || location.graph() != graph || !location.live())
                location = boundary.anchor == null ? null : boundary.anchor.resolve(graph);
            if (location == null && boundary.anchor == null) {
                // Old worlds: upgrade only against the same verified frozen target and direction.
                for (var board : boards) if (boundary.key.equals(board.key()) && boundary.dimension.equals(board.dimension())
                        && board.location() != null && SpeedBoardGraphLocation.acceptsFrozenTarget(boundary.target,
                        board.location().edge().getPosition(graph, board.location().position() / board.location().edge().getLength()), board.legacyTarget())
                        && (boundary.forward == null || boundary.forward.dot(board.location().edge().getDirectionAt(board.location().position())) > 0)) {
                    boundary.anchor = SpeedBoundaryAnchor.capture(board.location()); location = board.location(); break;
                }
            }
            if (location == null) continue;
            boundary.resolved = location;
            for (var iterator = state.movements.descendingIterator(); iterator.hasNext();) {
                var frame = iterator.next();
                if (frame.sequence <= boundary.rearThrough || frame.sequence + 1 <= boundary.order) break;
                double crossed = frame.crossing(location, true, frame.sequence != state.boundarySequence);
                if (Double.isFinite(crossed) && frame.order(crossed) >= boundary.order) {
                    clearances.add(new Clearance(boundary, frame.order(crossed))); break;
                }
            }
            boundary.rearThrough = state.boundarySequence;
        }
        clearances.sort(Comparator.comparingDouble(Clearance::order));
        for (int i = 0; i < clearances.size();) {
            double crossingOrder = clearances.get(i).order;
            PendingSpeedBoundary latest = null;
            Integer clearedLimit = null;
            boolean clearsRoute = false;
            // Coincident instructions have no physical ordering: the most restrictive wins,
            // including after an old save assigned them separate queue indices.
            while (i < clearances.size() && clearances.get(i).order == crossingOrder) {
                var boundary = clearances.get(i++).boundary;
                if (!state.pendingHigherBoundaries.contains(boundary)) continue;
                if (boundary.limitKmh != null) clearedLimit = clearedLimit == null ? boundary.limitKmh : Math.min(clearedLimit, boundary.limitKmh);
                if (latest == null || boundary.order > latest.order) latest = boundary;
                clearsRoute |= boundary.clearsRoute && state.routeRestrictionSerial == boundary.routeSerial;
            }
            if (latest == null) continue;
            if (latest.order == state.lastClearedBoundaryOrder && state.postedLimitKmh != null)
                clearedLimit = clearedLimit == null ? state.postedLimitKmh : Math.min(clearedLimit,state.postedLimitKmh);
            state.postedLimitKmh = clearedLimit;
            if (clearsRoute) state.routeLimitKmh = null;
            state.lastClearedBoundaryOrder = Math.max(state.lastClearedBoundaryOrder, latest.order);
            // The complete train has entered this instruction: older instructions are superseded,
            // including those encountered before a reversal. Later occupied sections still constrain it.
            double supersededThrough = latest.order;
            state.pendingHigherBoundaries.removeIf(b -> b.order <= supersededThrough);
            state.instructionSerial++;
            boundaryEvent(state, "REAR", latest);
        }
    }

    /**
     * Alpha 4b1 advance braking. A copy of the leading travelling point scouts
     * along a COPY of Create's active navigation path. Never call
     * Navigation.control() from a speculative scout: Create's selector consumes
     * entries from Navigation.currentPath as it crosses junctions, which would
     * corrupt the real scheduled train route. CSA must remain read-only.
     */
    // Snapshot speed-board track targets once per dimension per train scan. The old
    // implementation resolved every board target at every 1-block scout step.
    // A local snapshot avoids persistent cache invalidation and keeps registry order.
    private record ScoutedSpeedBoard(SpeedBoardBlockEntity board, SpeedBoardGraphLocation location) {}

    private static List<ScoutedSpeedBoard> snapshotSpeedBoards(ServerLevel level) {
        Map<BlockPos, TrackLinkedBlockEntity> signs = TRACK_LINKED_SIGNS.get(level.dimension());
        if (signs == null || signs.isEmpty())
            return List.of();
        List<ScoutedSpeedBoard> result = new ArrayList<>();
        for (TrackLinkedBlockEntity linked : signs.values()) {
            if (!(linked instanceof SpeedBoardBlockEntity board) || board.isRemoved() || board.getLevel() != level || !linked.hasTrackTarget())
                continue;
            var location = SpeedBoardGraphResolver.resolve(level, board).location();
            if (location != null) result.add(new ScoutedSpeedBoard(board, location));
        }
        return result;
    }

    private static void scanUpcomingRestriction(MinecraftServer server, Train train, TrainSpeedState state,
                                                boolean playerControlled) {
        state.upcomingLimitKmh = null;
        state.upcomingDistanceBlocks = Double.POSITIVE_INFINITY;
        if (train.graph == null || train.carriages.isEmpty())
            return;

        boolean reversed = TrainTravelDirection.reversed(train);
        double travelSign = reversed ? -1.0 : 1.0;
        Carriage frontCarriage = reversed ? train.carriages.get(train.carriages.size() - 1) : train.carriages.get(0);
        TravellingPoint source = reversed ? frontCarriage.getTrailingPoint() : frontCarriage.getLeadingPoint();
        if (source == null || source.edge == null || source.node1 == null)
            return;

        TravellingPoint scout = new TravellingPoint(source.node1, source.node2, source.edge, source.position, source.upsideDown);
        double travelled = 0.0;
        String ignoredCurrent = null;
        SpeedBoardGraphLocation.Sample previousScout = null;
        double previousTravelled = 0;
        // A selector consumes entries from this private list as the scout crosses
        // junctions. Copy it ONCE: copying on every step loses that progress.
        boolean navigating = train.navigation != null && train.navigation.isActive();
        List<Couple<TrackNode>> pathCopy = navigating ? copyNavigationPath(train.navigation) : null;
        if (navigating && pathCopy == null)
            return;
        TravellingPoint.ITrackSelector navigationSelector = navigating
                ? readOnlyNavigationSelector(pathCopy) : null;
        Map<ResourceKey<Level>, List<ScoutedSpeedBoard>> boardsByDimension = new HashMap<>();
        double lookAhead = SpeedBrakingEnvelope.lookAheadBlocks(train.speed, train.acceleration());
        while (travelled < lookAhead) {
            ResourceKey<Level> dim = scout.node1.getLocation().dimension;
            ServerLevel level = server.getLevel(dim);
            if (level == null)
                break;
            Vec3 pos = scout.getPosition(train.graph);
            Vec3 scoutMotion;
            try {
                scoutMotion = scout.getPositionWithOffset(train.graph, travelSign * 0.25, false).subtract(pos);
            } catch (Exception ignored) {
                scoutMotion = null;
            }

            // Alpha 5c1: shunting trains must approach SHUNT PROCEED at the shunt
            // speed, rather than crossing the signal at line speed and being
            // clamped afterwards.  The movement-mode manager is authoritative:
            // ordinary running trains ignore shunt aspects for speed purposes.
            if (TrainMovementModeManager.isShunting(level, train)) {
                BlockPos shuntCenter = BlockPos.containing(pos);
                for (BlockPos signalPos : BlockPos.betweenClosed(
                        shuntCenter.offset(-2, -2, -2), shuntCenter.offset(2, 2, 2))) {
                    // Beta 2a: advance scouting may run far beyond currently loaded
                    // terrain. Never let a speed look-ahead force a chunk load.
                    if (!level.hasChunkAt(signalPos))
                        continue;
                    var shuntState = level.getBlockState(signalPos);
                    if (!(shuntState.getBlock() instanceof GroundShuntSignalBlock)
                            || shuntState.getValue(GroundShuntSignalBlock.ASPECT) != ShuntDisplayedAspect.SHUNT_PROCEED)
                        continue;
                    if (Vec3.atCenterOf(signalPos).distanceToSqr(pos) > TRIGGER_RADIUS_SQR)
                        continue;

                    int shuntLimit = CommonConfig.shuntProceedSpeedKmh();
                    Integer currentEffective = state.effectiveLimitKmh();
                    double comparison = currentEffective == null
                            ? blocksPerTickToKmh(train.maxSpeed()) : currentEffective;
                    if (shuntLimit < comparison) {
                        state.upcomingLimitKmh = shuntLimit;
                        state.upcomingDistanceBlocks = travelled;
                    }
                    return;
                }
            }

            List<ScoutedSpeedBoard> boards = boardsByDimension.computeIfAbsent(
                    dim, $ -> snapshotSpeedBoards(level));
            var currentScout = SpeedBoardGraphLocation.Sample.of(train.graph, scout, reversed);
            ScoutedSpeedBoard nearest = null;
            double nearestDistance = Double.POSITIVE_INFINITY;
            for (var candidate : boards) {
                var location = candidate.location();
                double ahead = location.ahead(currentScout);
                double distance = Double.isFinite(ahead) && ahead >= -1.0e-7 && ahead <= ADVANCE_STEP_BLOCKS
                        ? travelled + Math.max(0, ahead) : Double.POSITIVE_INFINITY;
                double crossed = location.crossed(previousScout, currentScout, ADVANCE_STEP_BLOCKS * 4 + 1);
                if (Double.isFinite(crossed)) distance = Math.min(distance, previousTravelled + crossed);
                String key = dim.location() + ":speed:" + candidate.board().getBlockPos().asLong();
                if (!key.equals(ignoredCurrent) && distance < nearestDistance) {
                    nearest = candidate; nearestDistance = distance;
                }
                if (CommonConfig.speedGraphDiagnostics()) SpeedGraphDiagnostics.log(server.getTickCount(), train.id,
                        candidate.board().getBlockPos(), "ADVANCE", Double.isFinite(distance) ? "BOARD_ON_SCOUT_ROUTE" : "OTHER_EDGE_DIRECTION_OR_POSITION",
                        location, currentScout, "ahead=" + ahead + " distance=" + distance);
            }
            if (nearest != null) {
                var board = nearest.board();
                ignoredCurrent = dim.location() + ":speed:" + board.getBlockPos().asLong();
                if (board.isUnrestricted()) return;
                int selected = board.getApplicableSpeedKmh(TrainCategoryManager.getEffectiveCategory(level, train), playerControlled);
                Integer currentEffective = state.effectiveLimitKmh();
                double comparison = currentEffective == null ? blocksPerTickToKmh(train.maxSpeed()) : currentEffective;
                if (selected < comparison) {
                    state.upcomingLimitKmh = selected;
                    state.upcomingDistanceBlocks = nearestDistance;
                    if (playerControlled && !ignoredCurrent.equals(state.lastManualApproachBoardKey)) {
                        setPlayerThrottleForLimit(train, selected, "upcoming lower restriction " + selected + " km/h");
                        state.lastManualApproachBoardKey = ignoredCurrent;
                    }
                }
                return;
            }
            previousScout = currentScout;
            previousTravelled = travelled;
            double moved;
            try {
                Vec3 here = scout.getPosition(train.graph);
                Vec3 ahead = scout.getPositionWithOffset(train.graph, travelSign * 0.25, false);
                Vec3 steerVector = ahead.subtract(here);
                TravellingPoint.ITrackSelector selector = navigating
                        ? navigationSelector : scout.steer(train.manualSteer, new Vec3(0, 1, 0));
                if (CommonConfig.speedGraphDiagnostics()) {
                    var base = selector;
                    selector = (graph, pair) -> {
                        var selected = base.apply(graph, pair);
                        SpeedGraphDiagnostics.log(server.getTickCount(), train.id, null, "JUNCTION",
                                selected == null ? "ROUTE_UNKNOWN" : "SELECTED_ROUTE", null, currentScout,
                                "navigation=" + navigating + " steer=" + train.manualSteer
                                + " selected=" + (selected == null ? null : selected.getKey().getLocation().getLocation())
                                + " options=" + pair.getSecond().stream().map(e -> e.getKey().getLocation().getLocation()).toList());
                        return selected;
                    };
                }
                moved = Math.abs(scout.travel(train.graph, travelSign * ADVANCE_STEP_BLOCKS, selector));
            } catch (Exception ex) {
                return;
            }
            if (moved < 1.0e-5)
                return;
            travelled += moved;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Couple<TrackNode>> copyNavigationPath(Navigation navigation) {
        try {
            if (!navigationCurrentPathLookupAttempted) {
                navigationCurrentPathLookupAttempted = true;
                navigationCurrentPathField = Navigation.class.getDeclaredField("currentPath");
                navigationCurrentPathField.setAccessible(true);
            }
            if (navigationCurrentPathField == null)
                return null;
            Object value = navigationCurrentPathField.get(navigation);
            if (!(value instanceof List<?> path))
                return null;
            return new ArrayList<>((List<Couple<TrackNode>>) path);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            navigationCurrentPathField = null;
            CreateSignallingAdditions.LOGGER.warn("Could not read Create Navigation.currentPath for read-only speed look-ahead", ex);
            return null;
        }
    }

    static TravellingPoint.ITrackSelector readOnlyNavigationSelector(List<Couple<TrackNode>> pathCopy) {
        return (graph, optionsPair) -> {
            List<Map.Entry<TrackNode, TrackEdge>> options = optionsPair.getSecond();
            if (options == null || options.isEmpty())
                return null;
            if (pathCopy.isEmpty())
                return options.size() == 1 ? options.getFirst() : null;

            Couple<TrackNode> next = pathCopy.get(0);
            TrackEdge wanted = graph.getConnection(next);
            if (wanted != null) {
                for (Map.Entry<TrackNode, TrackEdge> option : options) {
                    if (option.getValue() == wanted) {
                        pathCopy.remove(0); // consume only CSA's private copy
                        return option;
                    }
                }
            }
            return options.size() == 1 ? options.getFirst() : null;
        };
    }


    private static void syncPlayerSpeedHud(MinecraftServer server, Train train, TrainSpeedState state, boolean playerControlled) {
        if (!playerControlled)
            return;
        UUID controller = null;
        for (Carriage carriage : train.carriages) {
            var entity = carriage.anyAvailableEntity();
            if (entity != null && entity.getControllingPlayer().isPresent()) {
                controller = entity.getControllingPlayer().get();
                break;
            }
        }
        if (controller == null)
            return;
        ServerPlayer player = server.getPlayerList().getPlayer(controller);
        if (player == null)
            return;
        double speedKmh = Math.abs(train.speed) * 72.0;
        double manualTopSpeed = train.maxSpeed() * AllConfigs.server().trains.manualTrainSpeedModifier.getF();
        double limiterKmh = Math.max(0.0, manualTopSpeed * train.throttle * 72.0);
        PacketDistributor.sendToPlayer(player, new TrainSpeedHudPayload(speedKmh, limiterKmh));
    }

    private static void reportEffectiveLimitChange(Train train, TrainSpeedState state) {
        Integer effective = state.effectiveLimitKmh();
        if (java.util.Objects.equals(effective, state.lastLoggedEffectiveLimitKmh))
            return;

        String from = state.lastLoggedEffectiveLimitKmh == null
                ? "unrestricted"
                : state.lastLoggedEffectiveLimitKmh + " km/h";
        String to = effective == null ? "unrestricted" : effective + " km/h";
        log(train, "effective restriction changed " + from + " -> " + to);
        state.lastLoggedEffectiveLimitKmh = effective;
        state.clampLoggedForCurrentLimit = false;
    }

    /**
     * Player-controlled trains use Create's own throttle field as the driver's
     * speed-limit control. Infrastructure updates that control once when an
     * instruction is passed (or when the player first takes control), then CSA
     * deliberately leaves it alone so the driver can change it manually.
     *
     * Automated trains continue to use the hard operational clamp below.
     */
    private static void updatePlayerSpeedLimit(Train train, TrainSpeedState state, boolean playerControlled) {
        if (!playerControlled)
            return;

        boolean justTookControl = !state.playerControlledLastTick;
        boolean newInstruction = state.lastPlayerInstructionSerial != state.instructionSerial;
        if (!justTookControl && !newInstruction)
            return;

        Integer limit = state.effectiveLimitKmh();
        double beforeThrottle = train.throttle;
        double desiredThrottle;

        if (limit == null) {
            desiredThrottle = 1.0;
        } else {
            // Create's manual controls use:
            //   cappedTopSpeed = train.maxSpeed() * manualTrainSpeedModifier * train.throttle
            // train.maxSpeed() is blocks/tick, so convert our absolute km/h target
            // to blocks/tick before turning it into the corresponding percentage.
            double manualTopSpeed = train.maxSpeed()
                    * AllConfigs.server().trains.manualTrainSpeedModifier.getF();
            double desiredBlocksPerTick = limit * KMH_TO_BLOCKS_PER_TICK;
            desiredThrottle = manualTopSpeed <= 1.0e-9
                    ? 1.0
                    : Math.max(0.0, Math.min(1.0, desiredBlocksPerTick / manualTopSpeed));
        }

        train.throttle = desiredThrottle;
        state.lastPlayerInstructionSerial = state.instructionSerial;

        String requested = limit == null ? "unrestricted" : limit + " km/h";
        log(train, String.format(java.util.Locale.ROOT,
                "player speed limit set to %s (Create throttle %.0f%% -> %.0f%%); driver may override manually",
                requested, beforeThrottle * 100.0, desiredThrottle * 100.0));
    }

    private static void setPlayerThrottleForLimit(Train train, int limit, String reason) {
        double manualTopSpeed = train.maxSpeed() * AllConfigs.server().trains.manualTrainSpeedModifier.getF();
        double desiredBlocksPerTick = limit * KMH_TO_BLOCKS_PER_TICK;
        double desiredThrottle = manualTopSpeed <= 1.0e-9 ? 1.0
                : Math.max(0.0, Math.min(1.0, desiredBlocksPerTick / manualTopSpeed));
        double before = train.throttle;
        // Advance braking is allowed to reduce the driver's limiter, never raise it.
        // A later higher-speed boundary is handled only when the rear clears it.
        if (desiredThrottle >= before - 1.0e-9) {
            log(train, String.format(java.util.Locale.ROOT,
                    "%s: Create throttle remains %.0f%% (advance braking will not raise limiter); driver may override manually",
                    reason, before * 100.0));
            return;
        }
        train.throttle = desiredThrottle;
        log(train, String.format(java.util.Locale.ROOT,
                "%s: Create throttle %.0f%% -> %.0f%%; driver may override manually",
                reason, before * 100.0, desiredThrottle * 100.0));
    }


    /**
     * Alpha 5c2: called immediately before Create approaches Train.targetSpeed.
     * This is deliberately inside Create's movement path rather than a server
     * post-tick correction, so Navigation cannot overwrite the CSA ceiling
     * before the train consumes the target.
     */
    public static void applyNavigationTargetCap(Train train) {
        if (train == null || train.id == null || isPlayerControlled(train))
            return;
        TrainSpeedState state = STATES.get(train.id);
        if (state == null) {
            var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server == null || Create.RAILWAYS.trains.get(train.id) != train) return;
            state = restoreState(server, train.id);
            STATES.put(train.id, state);
        }

        if (state.upcomingLimitKmh != null && Double.isFinite(state.upcomingDistanceBlocks)) {
            double target = state.upcomingLimitKmh;
            double brakingDistance = state.upcomingDistanceBlocks;
            double envelopeKmh = brakingEnvelopeKmh(train, target, brakingDistance);
            capCreateTargetSpeed(train, envelopeKmh * KMH_TO_BLOCKS_PER_TICK);
        }

        Integer limit = state.effectiveLimitKmh();
        if (limit != null)
            capCreateTargetSpeed(train, limit * KMH_TO_BLOCKS_PER_TICK);

        // Record the requested command and the actual motion only when the
        // diagnostic switch is enabled. Sampling avoids flooding server logs.
        if (CommonConfig.speedDiagnostics() && train.id != null
                && (train.id.hashCode() & 31) == (System.currentTimeMillis() / 50L & 31)) {
            CreateSignallingAdditions.LOGGER.info(
                    "[CSA SpeedDiag] NAV trainId={} actualKmh={} targetKmh={} effectiveCapKmh={} routeCapKmh={} upcomingCapKmh={}",
                    train.id, blocksPerTickToKmh(train.speed),
                    blocksPerTickToKmh(train.targetSpeed), limit,
                    state.routeLimitKmh, state.upcomingLimitKmh);
        }
    }


    private static boolean isValidSignDirection(TrackLinkedBlockEntity linked, Vec3 motion) {
        if (motion == null || motion.lengthSqr() < 1.0e-8)
            return false;
        BlockState state = linked.getBlockState();
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING))
            return true;
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        Vec3 faceNormal = new Vec3(facing.getStepX(), 0.0, facing.getStepZ());
        // The sign face points toward an approaching driver; therefore valid train
        // movement is opposite the face normal. A small dead-band avoids stationary
        // jitter being interpreted as a passage.
        return SpeedBoardRules.validDirection(motion, faceNormal);
    }

    private static boolean isPlayerControlled(Train train) {
        for (Carriage carriage : train.carriages) {
            var entity = carriage.anyAvailableEntity();
            if (entity != null && entity.getControllingPlayer().isPresent())
                return true;
        }
        return false;
    }

    private static void enforce(Train train, TrainSpeedState state, boolean playerControlled) {
        // While a player is driving, the posted/shunt instruction is represented
        // by Create's own adjustable throttle instead of a permanent CSA clamp.
        if (playerControlled) {
            state.clampLoggedForCurrentLimit = false;
            return;
        }

        Integer limit = state.effectiveLimitKmh();

        // Alpha 5c1: shape Create's OWN automatic target-speed command.  Earlier
        // builds only reduced Train.speed; Navigation continued requesting line
        // speed every tick, so the two controllers fought each other.  Capping
        // targetSpeed after Create has calculated it makes Create perform the
        // braking/acceleration while CSA supplies only the permitted envelope.
        if (state.upcomingLimitKmh != null && Double.isFinite(state.upcomingDistanceBlocks)) {
            double target = state.upcomingLimitKmh;
            double brakingDistance = state.upcomingDistanceBlocks;
            double envelopeKmh = brakingEnvelopeKmh(train, target, brakingDistance);
            double envelopeBpt = envelopeKmh * KMH_TO_BLOCKS_PER_TICK;
            capCreateTargetSpeed(train, envelopeBpt);
        }

        if (limit == null) {
            state.clampLoggedForCurrentLimit = false;
            return;
        }

        // Create stores train speed in blocks/tick, not blocks/second.
        // 1 block ~= 1 metre: km/h / 3.6 = blocks/s, then / 20 = blocks/tick.
        double maxBlocksPerTick = limit * KMH_TO_BLOCKS_PER_TICK;
        double beforeTarget = train.targetSpeed;
        double beforeSpeed = train.speed;
        boolean clamped = false;

        // Cap Create's current automatic command as well as retaining the actual
        // speed cap as a final safety net.  We never store/restore targetSpeed:
        // Navigation recalculates its desired value on subsequent ticks, so once
        // the restriction clears Create immediately regains full authority.
        capCreateTargetSpeed(train, maxBlocksPerTick);
        if (Math.abs(train.speed) > maxBlocksPerTick) {
            train.speed = Math.copySign(maxBlocksPerTick, train.speed);
            clamped = true;
        }

        if (clamped && !state.clampLoggedForCurrentLimit) {
            log(train, String.format(java.util.Locale.ROOT,
                    "capped by %d km/h limit: Create requested %.1f km/h, actual %.1f -> %.1f km/h",
                    limit,
                    blocksPerTickToKmh(beforeTarget),
                    blocksPerTickToKmh(beforeSpeed), blocksPerTickToKmh(train.speed)));
            state.clampLoggedForCurrentLimit = true;
        }
    }

    private static double brakingEnvelopeKmh(Train train, double targetKmh, double distance) {
        // Match Navigation's real blocks/tick² braking ability. A fixed 1.5 m/s²
        // envelope can demand more braking than Create's configured train supplies.
        return SpeedBrakingEnvelope.permittedKmh(targetKmh, distance, train.speed, train.acceleration());
    }

    private static void capCreateTargetSpeed(Train train, double permittedBlocksPerTick) {
        if (permittedBlocksPerTick < 0.0 || !Double.isFinite(permittedBlocksPerTick))
            return;
        if (Math.abs(train.targetSpeed) > permittedBlocksPerTick)
            train.targetSpeed = Math.copySign(permittedBlocksPerTick, train.targetSpeed);
    }

    private static double blocksPerTickToKmh(double value) {
        return Math.abs(value) * 72.0;
    }

    private static String formatPos(Vec3 pos) {
        return String.format(java.util.Locale.ROOT, "%.2f, %.2f, %.2f", pos.x, pos.y, pos.z);
    }

    private static void log(Train train, String action) {
        String name = train.name == null ? train.id.toString() : train.name.getString();
        if (CommonConfig.speedDiagnostics())
            CreateSignallingAdditions.LOGGER.info("Speed restriction train '{}': {}", name, action);
    }

    static final class PendingSpeedBoundary {
        final String key;
        final Integer limitKmh;
        final BlockPos signPos;
        final ResourceKey<Level> dimension;
        final boolean clearsRoute;
        final long routeSerial;
        Vec3 target;
        Vec3 forward;
        SpeedBoundaryAnchor anchor;
        SpeedBoardGraphLocation resolved;
        double order;
        long rearThrough;

        PendingSpeedBoundary(String key, Integer limitKmh, BlockPos signPos,
                             ResourceKey<Level> dimension, boolean clearsRoute, long routeSerial, Vec3 target, Vec3 forward) {
            this.key = key;
            this.limitKmh = limitKmh;
            this.signPos = signPos;
            this.dimension = dimension;
            this.clearsRoute = clearsRoute;
            this.routeSerial = routeSerial;
            this.target = target;
            this.forward = forward;
        }
    }

    static final class TrainSpeedState {
        Integer postedLimitKmh;
        Integer routeLimitKmh;
        boolean shuntAuthority;
        boolean manualShuntActive;
        Integer lastLoggedEffectiveLimitKmh;
        boolean clampLoggedForCurrentLimit;
        boolean playerControlledLastTick;
        long instructionSerial;
        long routeRestrictionSerial;
        long lastPlayerInstructionSerial = -1;
        final List<PendingSpeedBoundary> pendingHigherBoundaries = new ArrayList<>();
        Integer upcomingLimitKmh;
        double upcomingDistanceBlocks = Double.POSITIVE_INFINITY;
        String lastManualApproachBoardKey;
        final Set<String> nearbyTriggers = new HashSet<>();
        SpeedBoardGraphLocation.Sample lastFrontGraph;
        SpeedBoardGraphLocation.Sample lastRearGraph;
        Vec3 lastFrontPosition;
        ResourceKey<Level> lastFrontDimension;
        boolean lastFrontReversed;
        Vec3 lastRearPosition;
        ResourceKey<Level> lastRearDimension;
        boolean lastRearReversed;
        long boundarySequence;
        boolean boundaryPrepared;
        boolean boundaryTravelStarted;
        double boundaryMovementSpeed;
        Observation beforePhysicalFront, beforePhysicalRear;
        List<BoundaryBoard> preparedBoards = List.of();
        final ArrayDeque<Movement> movements = new ArrayDeque<>();
        final Map<String, BoardCursor> boardCursors = new HashMap<>();
        final List<BoundaryEvent> boundaryEvents = new ArrayList<>();
        double lastClearedBoundaryOrder = Double.NEGATIVE_INFINITY;

        Integer effectiveLimitKmh() {
            Integer result = postedLimitKmh;
            for (var boundary : pendingHigherBoundaries)
                if (boundary.limitKmh != null) result = result == null ? boundary.limitKmh : Math.min(result, boundary.limitKmh);
            if (routeLimitKmh != null)
                result = result == null ? routeLimitKmh : Math.min(result, routeLimitKmh);
            if (shuntAuthority || manualShuntActive) {
                int shunt = CommonConfig.shuntProceedSpeedKmh();
                result = result == null ? shunt : Math.min(result, shunt);
            }
            return result;
        }
    }
}
