package com.benjamintaskis.createsignalextensions.content.speed;

import com.simibubi.create.content.trains.graph.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Frozen exact edge identity, independent of graph UUID/objects after a rebuild. No spatial search. */
public record SpeedBoundaryAnchor(CompoundTag identity, double position) {
    public static SpeedBoundaryAnchor capture(SpeedBoardGraphLocation location) {
        return new SpeedBoundaryAnchor(identity(location.from(), location.to(), location.edge()), location.position());
    }

    static CompoundTag identity(TrackNode from, TrackNode to, TrackEdge edge) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", from.getLocation().dimension.location().toString());
        tag.putString("ToDimension", to.getLocation().dimension.location().toString());
        tag.put("From", from.getLocation().write(null)); tag.put("To", to.getLocation().write(null));
        tag.putDouble("Length", edge.getLength()); tag.putBoolean("Turn", edge.isTurn());
        if (edge.isTurn()) {
            var c = edge.getTurn();
            tag.putLong("StartBlock", c.bePositions.getFirst().asLong()); tag.putLong("EndBlock", c.bePositions.getSecond().asLong());
            tag.putString("Start", c.starts.getFirst().toString()); tag.putString("End", c.starts.getSecond().toString());
            tag.putString("Axis1", c.axes.getFirst().toString()); tag.putString("Axis2", c.axes.getSecond().toString());
            tag.putString("Normal1", c.normals.getFirst().toString()); tag.putString("Normal2", c.normals.getSecond().toString());
            if (c.smoothing != null) tag.putString("Smoothing", c.smoothing.getFirst() + ":" + c.smoothing.getSecond());
        }
        return tag;
    }

    public SpeedBoardGraphLocation resolve(TrackGraph graph) {
        if (graph == null) return null;
        var id = ResourceLocation.tryParse(identity.getString("Dimension"));
        var endId = ResourceLocation.tryParse(identity.getString("ToDimension"));
        if (id == null || endId == null) return null;
        var from = graph.locateNode(TrackNodeLocation.read(identity.getCompound("From"), null)
                .in(ResourceKey.create(Registries.DIMENSION, id)));
        var to = graph.locateNode(TrackNodeLocation.read(identity.getCompound("To"), null)
                .in(ResourceKey.create(Registries.DIMENSION, endId)));
        if (from == null || to == null || graph.getConnectionsFrom(from) == null) return null;
        var edge = graph.getConnectionsFrom(from).get(to);
        if (edge == null || !identity.equals(identity(from, to, edge))) return null;
        return new SpeedBoardGraphLocation(graph, from, to, edge, position);
    }

    public CompoundTag write() {
        var tag = identity.copy(); tag.putDouble("Position", position); return tag;
    }

    public static SpeedBoundaryAnchor read(CompoundTag tag) {
        if (!tag.contains("Position", Tag.TAG_DOUBLE) || !tag.contains("Length", Tag.TAG_DOUBLE)
                || !tag.contains("From", Tag.TAG_COMPOUND) || !tag.contains("To", Tag.TAG_COMPOUND)
                || ResourceLocation.tryParse(tag.getString("Dimension")) == null
                || ResourceLocation.tryParse(tag.getString("ToDimension")) == null) return null;
        double position = tag.getDouble("Position"), length = tag.getDouble("Length");
        if (!Double.isFinite(position) || !Double.isFinite(length) || length <= 0 || position < 0 || position > length) return null;
        var identity = tag.copy(); identity.remove("Position");
        return new SpeedBoundaryAnchor(identity, position);
    }
}
