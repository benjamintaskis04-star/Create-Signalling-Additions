package com.benjamintaskis.createsignalextensions.content.speed;

import com.simibubi.create.content.trains.graph.TrackGraphHelper;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.Create;
import net.createmod.catnip.data.Couple;
import com.simibubi.create.content.trains.track.BezierTrackPointLocation;
import com.simibubi.create.content.trains.track.ITrackBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Visual-only board index, including X-board branch instructions. Never used for enforcement. */
public final class SpeedZoneBoardData extends SavedData {
    public record Board(BlockPos sign, Vec3 from, Vec3 to, double fraction, int speed, boolean turnout) {
        public Board(BlockPos sign, Vec3 from, Vec3 to, double fraction, int speed) {
            this(sign, from, to, fraction, speed, false);
        }
        public boolean valid() {
            return finite(from) && finite(to) && from.distanceToSqr(to) > 1.0e-8
                    && Double.isFinite(fraction) && fraction >= 0 && fraction <= 1
                    && speed >= 0 && speed <= 999;
        }
        private static boolean finite(Vec3 v) {
            return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
        }
    }
    private final Map<BlockPos, Board> boards = new LinkedHashMap<>();
    private record Link(BlockPos track, boolean direction, BezierTrackPointLocation curve,
                        net.minecraft.world.level.block.state.BlockState state, int speed, boolean turnout, long revision) {}
    private final Map<BlockPos, Link> resolved = new HashMap<>();
    private long graphRevision;
    public void graphRevision(long revision) { graphRevision = revision; }
    private static final Factory<SpeedZoneBoardData> FACTORY = new Factory<>(SpeedZoneBoardData::new, SpeedZoneBoardData::load, null);
    public static SpeedZoneBoardData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "csa_speed_zone_boards");
    }
    public List<Board> snapshot() { return List.copyOf(boards.values()); }
    public void remove(BlockPos pos) { resolved.remove(pos); if (boards.remove(pos) != null) setDirty(); }

    public void update(SpeedBoardBlockEntity board) {
        if (!(board.getLevel() instanceof ServerLevel level)) return;
        if (!board.hasTrackTarget()) { remove(board.getBlockPos()); return; }
        Link link = new Link(board.getTargetTrack(), board.getTargetDirection(), board.getBezierTarget(),
                board.getBlockState(), board.getDefaultSpeedKmh(), board.isTurnoutBoard(), graphRevision);
        if (link.equals(resolved.get(board.getBlockPos()))) return;
        BlockPos target = board.getTargetTrack();
        if (!level.hasChunkAt(target)) return;
        var state = level.getBlockState(target);
        if (!(state.getBlock() instanceof ITrackBlock track)) { remove(board.getBlockPos()); return; }
        Direction.AxisDirection direction = board.getTargetDirection() ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE;
        TrackGraphLocation location;
        if (board.hasBezierTarget()) {
            location = TrackGraphHelper.getBezierGraphLocationAt(level, target, direction, board.getBezierTarget());
        } else {
            var axes = track.getTrackAxes(level, target, state);
            // Crossing axes cannot be distinguished by the old auxiliary link data.
            if (axes.size() != 1) { remove(board.getBlockPos()); return; }
            // Create's straight-track helper walks neighbouring world blocks.
            // Match the selected axis against its existing graph geometry instead,
            // so an overlay lookup cannot synchronously load a neighbouring chunk.
            Vec3 centre = Vec3.atBottomCenterOf(target).add(0, track.getElevationAtCenter(level, target, state), 0);
            location = straightLocation(level, centre, axes.getFirst());
        }
        if (location == null) { remove(board.getBlockPos()); return; }
        var graph = location.graph;
        var from = graph.locateNode(location.edge.getFirst());
        var to = graph.locateNode(location.edge.getSecond());
        if (from == null || to == null) return;
        var edge = graph.getConnectionsFrom(from).get(to);
        if (edge == null || edge.getLength() <= 1.0e-6) return;
        double fraction = Math.max(0, Math.min(1, location.position / edge.getLength()));
        Vec3 tangent = edge.getPosition(graph, Math.min(1, fraction + .001))
                .subtract(edge.getPosition(graph, Math.max(0, fraction - .001)));
        Direction facing = board.getBlockState().getValue(SpeedBoardBlock.FACING);
        double dot = tangent.dot(new Vec3(facing.getStepX(), 0, facing.getStepZ()));
        if (Math.abs(dot) < 1.0e-6) { remove(board.getBlockPos()); return; }
        Vec3 a = from.getLocation().getLocation(), b = to.getLocation().getLocation();
        if (dot > 0) { Vec3 swap = a; a = b; b = swap; fraction = 1 - fraction; }
        Board next = new Board(board.getBlockPos().immutable(), a, b, fraction,
                board.isUnrestricted() ? 0 : board.getDefaultSpeedKmh(), board.isTurnoutBoard() && !board.isUnrestricted());
        if (next.valid()) {
            if (!next.equals(boards.put(next.sign(), next))) setDirty();
            resolved.put(next.sign(), link);
        }
    }

    private static TrackGraphLocation straightLocation(ServerLevel level, Vec3 centre, Vec3 axis) {
        for (var graph : Create.RAILWAYS.trackNetworks.values()) for (var nodeLocation : graph.getNodes()) {
            if (!nodeLocation.dimension.equals(level.dimension())) continue;
            var from = graph.locateNode(nodeLocation);
            for (var entry : graph.getConnectionsFrom(from).entrySet()) {
                var to = entry.getKey(); var edge = entry.getValue();
                if (from.getNetId() > to.getNetId() || edge.isTurn() || !to.getLocation().dimension.equals(level.dimension())) continue;
                Vec3 a = edge.getPosition(graph, 0), delta = edge.getPosition(graph, 1).subtract(a);
                double lengthSquared = delta.lengthSqr();
                if (lengthSquared < 1.0e-8 || Math.abs(delta.normalize().dot(axis.normalize())) < .999) continue;
                double fraction = centre.subtract(a).dot(delta) / lengthSquared;
                if (fraction < -1.0e-6 || fraction > 1.000001) continue;
                fraction = Math.max(0, Math.min(1, fraction));
                if (a.add(delta.scale(fraction)).distanceToSqr(centre) > .01) continue;
                var location = new TrackGraphLocation();
                location.graph = graph;
                location.edge = Couple.create(from.getLocation(), to.getLocation());
                location.position = fraction * edge.getLength();
                return location;
            }
        }
        return null;
    }

    public void validateLoaded(ServerLevel level) {
        if (boards.values().removeIf(b -> level.hasChunkAt(b.sign())
                && !(level.getBlockState(b.sign()).getBlock() instanceof SpeedBoardBlock))) setDirty();
    }
    private static SpeedZoneBoardData load(CompoundTag tag, HolderLookup.Provider provider) {
        var data = new SpeedZoneBoardData();
        ListTag list = tag.getList("Boards", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(list.size(), 4096); i++) {
            CompoundTag b = list.getCompound(i);
            Board board = new Board(BlockPos.of(b.getLong("Sign")),
                    new Vec3(b.getDouble("Ax"), b.getDouble("Ay"), b.getDouble("Az")),
                    new Vec3(b.getDouble("Bx"), b.getDouble("By"), b.getDouble("Bz")),
                    b.getDouble("Fraction"), b.getInt("Speed"), b.getBoolean("Turnout"));
            if (board.valid()) data.boards.put(board.sign(), board);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Board board : boards.values()) {
            CompoundTag b = new CompoundTag();
            b.putLong("Sign", board.sign().asLong());
            b.putDouble("Ax", board.from().x); b.putDouble("Ay", board.from().y); b.putDouble("Az", board.from().z);
            b.putDouble("Bx", board.to().x); b.putDouble("By", board.to().y); b.putDouble("Bz", board.to().z);
            b.putDouble("Fraction", board.fraction()); b.putInt("Speed", board.speed()); list.add(b);
            b.putBoolean("Turnout", board.turnout());
        }
        tag.put("Boards", list); return tag;
    }
}
