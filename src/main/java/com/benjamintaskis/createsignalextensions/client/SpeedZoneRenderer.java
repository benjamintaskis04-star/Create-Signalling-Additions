package com.benjamintaskis.createsignalextensions.client;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import com.benjamintaskis.createsignalextensions.config.ClientConfig;
import com.benjamintaskis.createsignalextensions.content.speed.SpeedZoneBoardData.Board;
import com.benjamintaskis.createsignalextensions.network.SpeedZoneBoardsPayload;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.trains.graph.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import java.util.*;

/** Depth-tested, client-only visualisation; never supplies operational authority. */
@EventBusSubscriber(modid = CreateSignallingAdditions.ID, value = Dist.CLIENT)
public final class SpeedZoneRenderer {
    private SpeedZoneRenderer() {}
    private static final RenderType TYPE = RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS);
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new ByteBufferBuilder(65536));
    private static List<Board> boards = List.of();
    private static ResourceLocation dimension;
    private static ClientLevel cachedLevel;
    private static long lastBuild = Long.MIN_VALUE;
    private static int cachedRange;
    private static Vec3 cachedCamera = Vec3.ZERO;
    private static final List<Zone> zones = new ArrayList<>();
    private static final Map<SpeedZoneSleeperMesh.Surface, Paint[]> faces = new LinkedHashMap<>();
    private record EdgeKey(TrackGraph graph, TrackNode from, TrackNode to) {
        EdgeKey canonical() {
            TrackEdge edge = graph.getConnectionsFrom(from).get(to);
            boolean keep = edge != null && edge.isTurn() ? edge.getTurn().isPrimary() : from.getNetId() < to.getNetId();
            return keep ? this : new EdgeKey(graph, to, from);
        }
    }
    private record Mapped(Board board, EdgeKey edge) {}
    private record Piece(SpeedZoneSleeperMesh.Surface surface, double distance, boolean forward) {}
    private record Zone(int speed, List<Piece> pieces, Set<SpeedZoneSleeperMesh.Surface> surfaces) {
        Zone(int speed, List<Piece> pieces) {
            this(speed, pieces, Collections.newSetFromMap(new IdentityHashMap<>()));
            for (Piece piece : pieces) surfaces.add(piece.surface());
        }
    }
    private record Walk(EdgeKey edge, double fraction, double distance, Set<EdgeKey> visited, boolean waitingForTurnout) {}
    private record Paint(Zone zone, Piece piece) {}

    public static void accept(SpeedZoneBoardsPayload payload) {
        dimension = payload.dimension(); boards = payload.boards(); lastBuild = Long.MIN_VALUE;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        boards = List.of(); zones.clear(); faces.clear(); dimension = null; cachedLevel = null; lastBuild = Long.MIN_VALUE;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;
        if (!mc.level.dimension().location().equals(dimension)) { zones.clear(); faces.clear(); return; }
        Vec3 camera = event.getCamera().getPosition();
        int range = ClientConfig.speedZoneRenderDistance();
        if (cachedLevel != mc.level || lastBuild == Long.MIN_VALUE || mc.level.getGameTime() - lastBuild >= 20
                || cachedCamera.distanceToSqr(camera) > 256 || cachedRange != range) {
            rebuild(mc.level, camera, range);
            cachedLevel = mc.level; lastBuild = mc.level.getGameTime(); cachedCamera = camera; cachedRange = range;
        }
        Vec3 look = Vec3.directionFromRotation(event.getCamera().getXRot(), event.getCamera().getYRot());
        double reach = Math.min(range, mc.hitResult == null ? range : mc.hitResult.getLocation().distanceTo(camera) + .5);
        // Selecting physical track includes both directions, even when their speeds differ.
        Set<Zone> selected = Collections.newSetFromMap(new IdentityHashMap<>());
        double best = reach;
        Paint[] hit = null;
        for (var entry : faces.entrySet()) {
            Vec3 mid = entry.getKey().centre();
            double along = mid.subtract(camera).dot(look);
            if (along < 0 || along > best) continue;
            if (mid.distanceToSqr(camera.add(look.scale(along))) < .8) { hit = entry.getValue(); best = along; }
        }
        if (hit != null) {
            for (Paint paint : hit) if (paint != null) selected.add(paint.zone());
        }
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        try {
            pose.translate(-camera.x, -camera.y, -camera.z);
            VertexConsumer consumer = BUFFERS.getBuffer(TYPE);
            double seconds = (mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false)) / 20.0;
            boolean showAll = ClientConfig.showAllSpeedZones(), animate = ClientConfig.speedZonePulse();
            float opacity = ClientConfig.speedZoneOpacity();
            double pulseSpeed = ClientConfig.speedZonePulseSpeed();
            var runningSide = ClientConfig.speedHighlightRunningSide();
            for (var entry : faces.entrySet()) {
                var surface = entry.getKey();
                if (surface.centre().distanceToSqr(camera) > range * range
                        || !mc.level.hasChunkAt(BlockPos.containing(surface.centre()))) continue;
                Paint[] pair = entry.getValue();
                Paint paint = pair[0] == null ? pair[1] : pair[1] == null ? pair[0]
                        : pair[runningSide.directionIndex(surface.side())];
                // An inspected ordinary zone also shows its branch-specific X overlay.
                if (!showAll && !inspected(selected, surface)) continue;
                int base = SpeedZoneColour.rgb(paint.zone().speed());
                double pulse = animate ? SpeedZonePulse.strength(paint.piece().distance(), seconds, pulseSpeed) : 0;
                int rgb = SpeedZonePulse.colour(base, pulse);
                int alpha = Math.round((float)(opacity + (1 - opacity) * .45 * pulse) * 255);
                var vertices = surface.vertices();
                Vec3 normal = surface.normal();
                for (var v : vertices) consumer.addVertex(pose.last(), (float)v.position().x, (float)v.position().y, (float)v.position().z)
                        .setColor(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, alpha).setUv(v.u(), v.v())
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                        .setNormal(pose.last(), (float)normal.x, (float)normal.y, (float)normal.z);
            }
            BUFFERS.endBatch(TYPE);
        } finally { pose.popPose(); }
    }

    private static boolean inspected(Set<Zone> selected, SpeedZoneSleeperMesh.Surface surface) {
        for (Zone zone : selected) if (zone.surfaces().contains(surface)) return true;
        return false;
    }

    private static void rebuild(ClientLevel level, Vec3 camera, int range) {
        zones.clear();
        faces.clear();
        Map<EdgeKey, List<Board>> byEdge = new HashMap<>();
        List<Mapped> starts = new ArrayList<>();
        for (Board board : boards) {
            TrackNodeLocation a = new TrackNodeLocation(board.from()).in(level);
            TrackNodeLocation b = new TrackNodeLocation(board.to()).in(level);
            for (TrackGraph graph : CreateClient.RAILWAYS.trackNetworks.values()) {
                TrackNode from = graph.locateNode(a), to = graph.locateNode(b);
                if (from == null || to == null || graph.getConnectionsFrom(from).get(to) == null) continue;
                EdgeKey key = new EdgeKey(graph, from, to);
                // X boards arm a branch-only overlay, never an ordinary zone boundary.
                if (board.turnout()) starts.add(new Mapped(board, key));
                else byEdge.computeIfAbsent(key, $ -> new ArrayList<>()).add(board);
                break;
            }
        }
        for (var entry : byEdge.entrySet()) {
            var ordered = entry.getValue();
            ordered.sort(Comparator.comparingDouble(Board::fraction));
            List<Board> distinct = new ArrayList<>();
            for (Board board : ordered) {
                if (!distinct.isEmpty() && Math.abs(distinct.getLast().fraction() - board.fraction()) < 1.0e-6) {
                    Board previous = distinct.getLast();
                    if (previous.speed() == 0 || board.speed() > 0 && board.speed() < previous.speed())
                        distinct.set(distinct.size() - 1, board);
                } else distinct.add(board);
            }
            entry.setValue(distinct);
            for (Board board : distinct) starts.add(new Mapped(board, entry.getKey()));
        }
        Map<EdgeKey, List<SpeedZoneSleeperMesh.Surface>> meshes = new HashMap<>();
        int totalFaces = 0;
        int walkedEdges = 0;
        int cachedFaces = 0;
        for (Mapped start : starts) {
            if (start.board().speed() == 0 || totalFaces >= 65536) continue;
            List<Piece> pieces = new ArrayList<>();
            ArrayDeque<Walk> pending = new ArrayDeque<>();
            pending.add(new Walk(start.edge(), start.board().fraction(), 0, Set.of(), start.board().turnout()));
            Set<EdgeKey> expanded = new HashSet<>();
            for (int count = 0; count < 256 && !pending.isEmpty() && totalFaces < 65536; count++) {
                if (++walkedEdges > 4096) break;
                Walk walk = pending.removeFirst();
                EdgeKey key = walk.edge();
                if (walk.distance() >= 2048 || walk.visited().contains(key) || !expanded.add(key)) continue;
                TrackEdge edge = key.graph().getConnectionsFrom(key.from()).get(key.to());
                if (edge == null || !key.from().getLocation().dimension.equals(level.dimension())
                        || !key.to().getLocation().dimension.equals(level.dimension()) || edge.getLength() <= 1.0e-6) continue;
                double end = 1;
                boolean boundary = false;
                for (Board next : byEdge.getOrDefault(key, List.of())) {
                    if (next.fraction() > walk.fraction() + 1.0e-6 && next.fraction() <= end) {
                        end = next.fraction(); boundary = true;
                    }
                }
                double length = Math.min((end - walk.fraction()) * edge.getLength(), 2048 - walk.distance());
                double endFraction = walk.fraction() + length / edge.getLength();
                EdgeKey canonical = key.canonical();
                TrackEdge physical = canonical.graph().getConnectionsFrom(canonical.from()).get(canonical.to());
                boolean forward = canonical.equals(key);
                if (physical != null && !walk.waitingForTurnout()) {
                    var mesh = meshes.get(canonical);
                    if (mesh == null) {
                        mesh = SpeedZoneSleeperMesh.build(level, canonical.graph(), physical, camera, range, 32768 - cachedFaces);
                        cachedFaces += mesh.size();
                        meshes.put(canonical, mesh);
                    }
                    for (var surface : mesh) {
                        double f = forward ? surface.fraction() : 1 - surface.fraction();
                        if (f < walk.fraction() || f >= endFraction || totalFaces >= 65536) continue;
                        pieces.add(new Piece(surface, walk.distance() + (f - walk.fraction()) * edge.getLength(), forward));
                        totalFaces++;
                    }
                }
                if (boundary || endFraction < 1 - 1.0e-6) continue;
                Set<EdgeKey> visited = new HashSet<>(walk.visited());
                visited.add(key);
                var exits = key.graph().getConnectionsFrom(key.to()).entrySet().stream()
                        .filter(exit -> exit.getKey() != key.from() && edge.canTravelTo(exit.getValue()))
                        .toList();
                int straight = walk.waitingForTurnout() && exits.size() > 1
                        ? TurnoutVisualRoutes.straightIndex(edge.getDirection(false), key.to().getNormal(),
                                exits.stream().map(exit -> exit.getValue().getDirection(true)).toList()) : -1;
                for (int i = 0; i < exits.size(); i++) {
                    if (!TurnoutVisualRoutes.follows(walk.waitingForTurnout(), straight, i, exits.size())) continue;
                    var exit = exits.get(i);
                    EdgeKey next = new EdgeKey(key.graph(), key.to(), exit.getKey());
                    if (byEdge.getOrDefault(next, List.of()).stream().anyMatch(bd -> bd.fraction() < 1.0e-6)) continue;
                    pending.addLast(new Walk(next, 0, walk.distance() + length, visited,
                            walk.waitingForTurnout() && exits.size() == 1));
                }
            }
            if (!pieces.isEmpty()) zones.add(new Zone(start.board().speed(), List.copyOf(pieces)));
        }
        // Resolve overlap once per rebuild, never once per animation frame.
        for (Zone zone : zones) for (Piece piece : zone.pieces()) {
            Paint[] pair = faces.computeIfAbsent(piece.surface(), $ -> new Paint[2]);
            int direction = piece.forward() ? 0 : 1;
            if (pair[direction] == null || zone.speed() < pair[direction].zone().speed())
                pair[direction] = new Paint(zone, piece);
        }
    }
}
