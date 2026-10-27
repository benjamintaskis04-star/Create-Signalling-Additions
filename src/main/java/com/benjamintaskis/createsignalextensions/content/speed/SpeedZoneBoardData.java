package com.benjamintaskis.createsignalextensions.content.speed;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Visual-only projections from the shared graph resolver. X remains cosmetic. */
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
    private final Set<BlockPos> validated = new HashSet<>();
    private static final Factory<SpeedZoneBoardData> FACTORY = new Factory<>(SpeedZoneBoardData::new, SpeedZoneBoardData::load, null);
    public static SpeedZoneBoardData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "csa_speed_zone_boards");
    }
    public List<Board> snapshot() { return List.copyOf(boards.values()); }
    public void remove(BlockPos pos) { if (boards.remove(pos) != null) setDirty(); }

    public void update(SpeedBoardBlockEntity board) {
        if (!(board.getLevel() instanceof ServerLevel level)) return;
        var location = SpeedBoardGraphResolver.resolve(level, board).location();
        if (location == null) { remove(board.getBlockPos()); return; }
        Board next = new Board(board.getBlockPos().immutable(),
                location.from().getLocation().getLocation(), location.to().getLocation().getLocation(),
                location.position() / location.edge().getLength(),
                board.isUnrestricted() ? 0 : board.getDefaultSpeedKmh(), board.isTurnoutBoard());
        if (next.valid() && !next.equals(boards.put(next.sign(), next))) setDirty();
        validated.add(next.sign());
    }
    public void validateLoaded(ServerLevel level) {
        // Persisted visual projections are not authority after a graph change/reload.
        if (boards.values().removeIf(b -> !validated.contains(b.sign()))) setDirty();
        validated.clear();
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
