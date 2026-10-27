package com.benjamintaskis.createsignalextensions.content.speed;

import net.minecraft.core.HolderLookup;
import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import com.benjamintaskis.createsignalextensions.config.CommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/** Persist per-train posted/turnout limits and shunt authority across world reloads. */
public final class SpeedRestrictionSavedData extends SavedData {
    private static final String DATA_NAME = "create_signalling_additions_speed_restrictions";
    private static final Factory<SpeedRestrictionSavedData> FACTORY =
            new Factory<>(SpeedRestrictionSavedData::new, SpeedRestrictionSavedData::load, null);
    public record Boundary(String key, Integer limitKmh, BlockPos signPos, String dimension,
                           boolean clearsRoute, long routeSerial, Vec3 target, Vec3 forward, SpeedBoundaryAnchor anchor) {
        public Boundary(String key, Integer limitKmh, BlockPos signPos, String dimension,
                        boolean clearsRoute, long routeSerial, Vec3 target, Vec3 forward) {
            this(key, limitKmh, signPos, dimension, clearsRoute, routeSerial, target, forward, null);
        }
    }
    public record Entry(Integer postedLimit, Integer routeLimit, boolean shuntAuthority,
                        long routeSerial, List<Boundary> boundaries) {}
    private final Map<UUID, Entry> entries = new HashMap<>();

    public static SpeedRestrictionSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Entry get(UUID id) { return entries.get(id); }

    public void retainTrains(java.util.Set<UUID> authoritativeIds) {
        if (entries.keySet().removeIf(id -> !authoritativeIds.contains(id))) setDirty();
    }

    /** Remove state only when a train is confirmed permanently deleted, not merely unloaded. */
    public void remove(UUID id) {
        if (entries.remove(id) != null) {
            if (CommonConfig.speedDiagnostics()) CreateSignallingAdditions.LOGGER.info("[CSA SpeedDiag] REMOVE trainId={}", id);
            setDirty();
        }
    }


    public void update(UUID id, Integer posted, Integer route, boolean shunt, long serial, List<Boundary> boundaries) {
        // Do not create saved records for trains that have never encountered a
        // restriction. This avoids filling world data with unrestricted trains.
        if (posted == null && route == null && !shunt && boundaries.isEmpty()) {
            remove(id);
            return;
        }
        Entry next = new Entry(posted, route, shunt, serial, List.copyOf(boundaries));
        if (!Objects.equals(entries.get(id), next)) {
            if (CommonConfig.speedDiagnostics())
                CreateSignallingAdditions.LOGGER.info("[CSA SpeedDiag] UPDATE trainId={} posted={} route={} shunt={} pending={}",
                        id, posted, route, shunt, boundaries.size());
            entries.put(id, next);
            setDirty();
        }
    }

    private static SpeedRestrictionSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        SpeedRestrictionSavedData data = new SpeedRestrictionSavedData();
        ListTag list = tag.getList("Trains", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            if (!item.hasUUID("Id")) continue;
            Integer posted = item.contains("Posted", Tag.TAG_INT) && item.getInt("Posted") > 0 ? item.getInt("Posted") : null;
            Integer route = item.contains("Route", Tag.TAG_INT) && item.getInt("Route") > 0 ? item.getInt("Route") : null;
            List<Boundary> boundaries = new ArrayList<>();
            ListTag pending = item.getList("PendingBoundaries", Tag.TAG_COMPOUND);
            for (int j = 0; j < pending.size(); j++) {
                CompoundTag b = pending.getCompound(j);
                if (!b.contains("Key", Tag.TAG_STRING) || !b.contains("Dimension", Tag.TAG_STRING)) continue;
                String dimension = b.getString("Dimension");
                if (ResourceLocation.tryParse(dimension) == null) continue;
                // Ignore malformed or excessive persisted boundaries rather than allowing a
                // damaged save to create unbounded per-train work on every server tick.
                if (boundaries.size() >= 4096 || !b.contains("Position", Tag.TAG_LONG)) continue;
                if (b.getString("Key").length() > 512) continue;
                Integer boundaryLimit = b.contains("Limit", Tag.TAG_INT) ? b.getInt("Limit") : null;
                if (boundaryLimit != null && boundaryLimit <= 0) continue;
                boundaries.add(new Boundary(b.getString("Key"),
                        boundaryLimit,
                        BlockPos.of(b.getLong("Position")), dimension,
                        b.getBoolean("ClearsRoute"), b.getLong("RouteSerial"), readVector(b, "Target"), readVector(b, "Forward"),
                        b.contains("GraphAnchor", Tag.TAG_COMPOUND) ? SpeedBoundaryAnchor.read(b.getCompound("GraphAnchor")) : null));
            }
            data.entries.put(item.getUUID("Id"), new Entry(posted, route,
                    item.getBoolean("ShuntAuthority"), item.getLong("RouteSerial"), List.copyOf(boundaries)));
        }
        if (CommonConfig.speedDiagnostics()) {
            CreateSignallingAdditions.LOGGER.info("[CSA SpeedDiag] LOAD SavedData entries={}", data.entries.size());
            data.entries.forEach((id, e) -> CreateSignallingAdditions.LOGGER.info(
                    "[CSA SpeedDiag] LOADED trainId={} posted={} route={} shunt={} pending={}",
                    id, e.postedLimit(), e.routeLimit(), e.shuntAuthority(), e.boundaries().size()));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        entries.forEach((id, state) -> {
            CompoundTag item = new CompoundTag();
            item.putUUID("Id", id);
            if (state.postedLimit() != null) item.putInt("Posted", state.postedLimit());
            if (state.routeLimit() != null) item.putInt("Route", state.routeLimit());
            item.putBoolean("ShuntAuthority", state.shuntAuthority());
            item.putLong("RouteSerial", state.routeSerial());
            ListTag pending = new ListTag();
            for (Boundary boundary : state.boundaries()) {
                CompoundTag b = new CompoundTag();
                b.putString("Key", boundary.key());
                if (boundary.limitKmh() != null) b.putInt("Limit", boundary.limitKmh());
                b.putLong("Position", boundary.signPos().asLong());
                b.putString("Dimension", boundary.dimension());
                b.putBoolean("ClearsRoute", boundary.clearsRoute());
                b.putLong("RouteSerial", boundary.routeSerial());
                writeVector(b, "Target", boundary.target());
                writeVector(b, "Forward", boundary.forward());
                if (boundary.anchor() != null) b.put("GraphAnchor", boundary.anchor().write());
                pending.add(b);
            }
            item.put("PendingBoundaries", pending);
            list.add(item);
        });
        tag.put("Trains", list);
        if (CommonConfig.speedDiagnostics()) {
            CreateSignallingAdditions.LOGGER.info("[CSA SpeedDiag] SAVE SavedData entries={}", entries.size());
            entries.forEach((id, e) -> CreateSignallingAdditions.LOGGER.info(
                    "[CSA SpeedDiag] SAVED trainId={} posted={} route={} shunt={} pending={}",
                    id, e.postedLimit(), e.routeLimit(), e.shuntAuthority(), e.boundaries().size()));
        }
        return tag;
    }

    private static Vec3 readVector(CompoundTag tag, String name) {
        if (!tag.contains(name, Tag.TAG_COMPOUND)) return null;
        CompoundTag v = tag.getCompound(name);
        double x = v.getDouble("X"), y = v.getDouble("Y"), z = v.getDouble("Z");
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) ? new Vec3(x, y, z) : null;
    }
    private static void writeVector(CompoundTag tag, String name, Vec3 vector) {
        if (vector == null) return;
        CompoundTag v = new CompoundTag();
        v.putDouble("X", vector.x); v.putDouble("Y", vector.y); v.putDouble("Z", vector.z); tag.put(name, v);
    }
}
