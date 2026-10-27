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

    public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        STATES.clear();
        TRACK_LINKED_SIGNS.clear();
        SpeedZoneSync.clear();
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
        TrainSpeedState state = new TrainSpeedState();
        SpeedRestrictionSavedData.Entry entry = SpeedRestrictionSavedData.get(server.overworld()).get(id);
        if (CommonConfig.speedDiagnostics())
            CreateSignallingAdditions.LOGGER.info("[CSA SpeedDiag] RESTORE trainId={} saved={} posted={} route={} shunt={} pending={}",
                    id, entry != null, entry == null ? null : entry.postedLimit(),
                    entry == null ? null : entry.routeLimit(), entry != null && entry.shuntAuthority(),
                    entry == null ? 0 : entry.boundaries().size());
        if (entry != null) {
            state.postedLimitKmh = entry.postedLimit();
            state.routeLimitKmh = entry.routeLimit();
            state.shuntAuthority = entry.shuntAuthority();
            state.routeRestrictionSerial = entry.routeSerial();
            for (SpeedRestrictionSavedData.Boundary b : entry.boundaries()) {
                ResourceLocation location = ResourceLocation.tryParse(b.dimension());
                if (location != null && state.pendingHigherBoundaries.stream().noneMatch(
                        existing -> existing.key.equals(b.key()) && existing.dimension.equals(
                                ResourceKey.create(Registries.DIMENSION, location))))
                    state.pendingHigherBoundaries.add(new PendingSpeedBoundary(
                        b.key(), b.limitKmh(), b.signPos(),
                        ResourceKey.create(Registries.DIMENSION, location), b.clearsRoute(), b.routeSerial(), b.target(), b.forward()));
            }
            state.instructionSerial++;
        }
        return state;
    }

    private static void persistState(MinecraftServer server, UUID id, TrainSpeedState state) {
        SpeedRestrictionSavedData.get(server.overworld()).update(id, state.postedLimitKmh,
                state.routeLimitKmh, state.shuntAuthority, state.routeRestrictionSerial,
                state.pendingHigherBoundaries.stream().map(b -> new SpeedRestrictionSavedData.Boundary(
                        b.key, b.limitKmh, b.signPos, b.dimension.location().toString(),
                        b.clearsRoute, b.routeSerial, b.target, b.forward)).toList());
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

        // A higher (or unrestricted) posted speed does not become effective until
        // the rear of the complete train has entered the new restriction.
        Carriage rearCarriage = TrainTravelDirection.reversed(train)
                ? train.carriages.get(0)
                : train.carriages.get(train.carriages.size() - 1);
        TravellingPoint rearPoint = TrainTravelDirection.reversed(train)
                ? rearCarriage.getLeadingPoint()
                : rearCarriage.getTrailingPoint();
        scanRearForPendingBoard(server, train, state, rearPoint);

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
            if (!linked.hasTrackTarget())
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

            if (linked instanceof SpeedBoardBlockEntity board) {
                boolean manual = isPlayerControlled(train);
                TrainSpeedCategory category = TrainCategoryManager.getEffectiveCategory(level, train);
                Integer selected = board.isUnrestricted() ? null : board.getApplicableSpeedKmh(category, manual);

                // Every speed board, including X-prefixed boards, supersedes any
                // legacy carried turnout restriction, but
                // releasing to a higher/unrestricted speed still obeys the normal
                // whole-train-clear boundary. Compare against the CURRENT effective
                // restriction before deciding whether this is an increase.
                Integer current = state.effectiveLimitKmh();

                // Lower restrictions take effect at the front of the train. Higher
                // restrictions (including unrestricted) wait for the rear to clear.
                boolean isHigher = SpeedBoardRules.requiresRearClearance(current, selected);
                if (isHigher) {
                    // Keep every higher-speed boundary the train is currently spanning.
                    // A long train may have its front beyond several boards before its rear
                    // reaches the first one; one pending slot would lose that history.
                    // A restored boundary may be encountered again after reloading,
                    // or a trigger can be observed twice while the front remains near
                    // the same board. Do not queue a second rear-clearance instruction.
                    boolean alreadyPending = state.pendingHigherBoundaries.stream()
                            .anyMatch(boundary -> boundary.key.equals(key)
                                    && boundary.dimension.equals(dimension));
                    if (alreadyPending) {
                        log(train, "ignored duplicate pending rear-clearance boundary at "
                                + signPos.toShortString());
                        continue;
                    }
                    state.pendingHigherBoundaries.add(new PendingSpeedBoundary(
                            key, selected, signPos.immutable(), dimension, true, state.routeRestrictionSerial,
                            target, motion.normalize()));
                    log(train, "front passed " + (selected == null ? "unrestricted" : selected + " km/h")
                            + " speed board at " + signPos.toShortString()
                            + "; waiting for rear of train to clear before applying"
                            + " (pending boundaries=" + state.pendingHigherBoundaries.size() + ")");
                } else {
                    // A newly encountered lower/equal restriction is authoritative from
                    // the front of the train. Any earlier pending increases are now stale:
                    // they must never raise the speed after the front has already entered
                    // this newer, more restrictive section.
                    if (!state.pendingHigherBoundaries.isEmpty()) {
                        log(train, "discarded " + state.pendingHigherBoundaries.size()
                                + " older pending higher-speed boundary/boundaries because front entered "
                                + selected + " km/h restriction");
                        state.pendingHigherBoundaries.clear();
                    }
                    state.routeLimitKmh = null;
                    state.postedLimitKmh = selected;
                    state.instructionSerial++;
                    log(train, "passed " + selected + " km/h speed board at " + signPos.toShortString()
                            + " (category " + category.displayName() + (manual ? ", manual/default" : "")
                            + "; linked track point " + formatPos(target) + ")");
                }
            } else if (linked instanceof EndOfShuntSignBlockEntity) {
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

    private static void scanRearForPendingBoard(MinecraftServer server, Train train, TrainSpeedState state,
                                                TravellingPoint rearPoint) {
        if (state.pendingHigherBoundaries.isEmpty() || rearPoint == null || rearPoint.edge == null
                || rearPoint.node1 == null || train.graph == null)
            return;

        ResourceKey<Level> dimension = rearPoint.node1.getLocation().dimension;
        ServerLevel level = server.getLevel(dimension);
        if (level == null)
            return;
        Map<BlockPos, TrackLinkedBlockEntity> byPos = TRACK_LINKED_SIGNS.get(dimension);
        Vec3 rear = rearPoint.getPosition(train.graph);
        boolean reversed = TrainTravelDirection.reversed(train);
        Vec3 previousRear = dimension.equals(state.lastRearDimension) && state.lastRearReversed == reversed
                ? state.lastRearPosition : null;
        state.lastRearPosition = rear;
        state.lastRearDimension = dimension;
        state.lastRearReversed = reversed;
        Iterator<PendingSpeedBoundary> pending = state.pendingHigherBoundaries.iterator();
        while (pending.hasNext()) {
            PendingSpeedBoundary boundary = pending.next();
            if (!dimension.equals(boundary.dimension))
                continue;
            Vec3 target = boundary.target, forward = boundary.forward;
            // Old Alpha 1 saves lack a frozen target; upgrade when the sign is loaded.
            if (target == null || forward == null) {
                TrackLinkedBlockEntity linked = byPos == null ? null : byPos.get(boundary.signPos);
                if (!(linked instanceof SpeedBoardBlockEntity) || !linked.hasTrackTarget()) continue;
                target = linked.getTrackTargetPosition(level);
                Direction facing = linked.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
                forward = new Vec3(-facing.getStepX(), 0, -facing.getStepZ());
                boundary.target = target; boundary.forward = forward;
            }
            if (!RearClearance.crossed(previousRear, rear, target, forward, TRIGGER_RADIUS))
                continue;

            state.postedLimitKmh = boundary.limitKmh;
            if (boundary.clearsRoute && state.routeRestrictionSerial == boundary.routeSerial)
                state.routeLimitKmh = null;
            state.instructionSerial++;
            log(train, "rear cleared speed board at " + boundary.signPos.toShortString()
                    + "; posted restriction now "
                    + (boundary.limitKmh == null ? "unrestricted" : boundary.limitKmh + " km/h")
                    + "; remaining pending boundaries=" + (state.pendingHigherBoundaries.size() - 1));
            pending.remove();
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
    private record ScoutedSpeedBoard(SpeedBoardBlockEntity board, Vec3 target) {}

    private static List<ScoutedSpeedBoard> snapshotSpeedBoards(ServerLevel level) {
        Map<BlockPos, TrackLinkedBlockEntity> signs = TRACK_LINKED_SIGNS.get(level.dimension());
        if (signs == null || signs.isEmpty())
            return List.of();
        List<ScoutedSpeedBoard> result = new ArrayList<>();
        for (TrackLinkedBlockEntity linked : signs.values()) {
            if (!(linked instanceof SpeedBoardBlockEntity board) || !linked.hasTrackTarget())
                continue;
            Vec3 target = linked.getTrackTargetPosition(level);
            if (target != null)
                result.add(new ScoutedSpeedBoard(board, target));
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
            if (!boards.isEmpty()) {
                for (ScoutedSpeedBoard candidate : boards) {
                    SpeedBoardBlockEntity board = candidate.board();
                    TrackLinkedBlockEntity linked = board;
                    Vec3 target = candidate.target();
                    if (target.distanceToSqr(pos) > 1.25 * 1.25)
                        continue;
                    // Nearby turnout branches can be within the proximity radius.
                    // Only anticipate boards linked to the edge on the copied actual route.
                    if (!SpeedBoardRouteMatch.matches(train.graph, scout.edge, target,
                            board.getTargetTrack(), board.getBezierTarget())) continue;
                    if (!isValidSignDirection(linked, scoutMotion))
                        continue;
                    String key = dim.location() + ":speed:" + linked.getBlockPos().asLong();
                    if (key.equals(ignoredCurrent))
                        continue;
                    ignoredCurrent = key;
                    if (board.isUnrestricted())
                        return;
                    TrainSpeedCategory category = TrainCategoryManager.getEffectiveCategory(level, train);
                    int selected = board.getApplicableSpeedKmh(category, playerControlled);
                    Integer currentEffective = state.effectiveLimitKmh();
                    double comparison = currentEffective == null ? blocksPerTickToKmh(train.maxSpeed()) : currentEffective;
                    if (selected < comparison) {
                        state.upcomingLimitKmh = selected;
                        state.upcomingDistanceBlocks = travelled;
                        if (playerControlled && !key.equals(state.lastManualApproachBoardKey)) {
                            setPlayerThrottleForLimit(train, selected,
                                    "upcoming lower restriction " + selected + " km/h");
                            state.lastManualApproachBoardKey = key;
                        }
                    }
                    return; // first board ahead governs the next posted transition
                }
            }

            double moved;
            try {
                Vec3 here = scout.getPosition(train.graph);
                Vec3 ahead = scout.getPositionWithOffset(train.graph, travelSign * 0.25, false);
                Vec3 steerVector = ahead.subtract(here);
                TravellingPoint.ITrackSelector selector = navigating
                        ? navigationSelector : scout.steer(train.manualSteer, steerVector);
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

    private static final class PendingSpeedBoundary {
        final String key;
        final Integer limitKmh;
        final BlockPos signPos;
        final ResourceKey<Level> dimension;
        final boolean clearsRoute;
        final long routeSerial;
        Vec3 target;
        Vec3 forward;

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

    private static final class TrainSpeedState {
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
        Vec3 lastFrontPosition;
        ResourceKey<Level> lastFrontDimension;
        boolean lastFrontReversed;
        Vec3 lastRearPosition;
        ResourceKey<Level> lastRearDimension;
        boolean lastRearReversed;

        Integer effectiveLimitKmh() {
            Integer result = postedLimitKmh;
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
