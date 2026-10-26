package com.benjamintaskis.createsignalextensions.client;

import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.track.ITrackBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/** Reuses only Create's textured sleeper tops. No guessed ribbon or rail geometry. */
public final class SpeedZoneSleeperMesh {
    private SpeedZoneSleeperMesh() {}
    public record Vertex(Vec3 position, float u, float v) {}
    public record Surface(List<Vertex> vertices, Vec3 centre, Vec3 normal, double fraction, double side) {}

    public static List<Surface> build(ClientLevel level, TrackGraph graph, TrackEdge edge,
                                      Vec3 camera, int range, int faceBudget) {
        List<Surface> result = new ArrayList<>();
        if (faceBudget <= 0) return List.of();
        if (edge.isTurn()) {
            var turn = edge.getTurn();
            var baked = turn.getBakedSegments();
            // Bezierator positions and tie transforms are relative to the first BE.
            BlockPos origin = turn.bePositions.getFirst();
            if (!level.hasChunkAt(origin)) return result;
            BlockState state = level.getBlockState(origin);
            BakedModel model = turn.getMaterial().getModelHolder().tie().get();
            for (int i = 1; i < baked.length; i++) {
                if (result.size() >= faceBudget) break;
                if (baked.tieTransform[i] == null) continue;
                double fraction = (turn.getSegmentT(i - 1) + turn.getSegmentT(i)) * .5;
                Vec3 centre = edge.getPosition(graph, fraction);
                if (centre.distanceToSqr(camera) > (range + 16.0) * (range + 16.0)
                        || !level.hasChunkAt(BlockPos.containing(centre))) continue;
                Matrix4f transform = new Matrix4f().translation(origin.getX(), origin.getY(), origin.getZ())
                        .mul(baked.tieTransform[i].pose());
                add(result, model, state, ModelData.EMPTY, transform, graph, edge, fraction, faceBudget);
            }
        } else {
            Set<BlockPos> blocks = new HashSet<>();
            // Sampling catches diagonal and ascending blocks, with no neighbouring chunk loads.
            Vec3 a = edge.getPosition(graph, 0), d = edge.getPosition(graph, 1).subtract(a);
            double nearest = Math.clamp(camera.subtract(a).dot(d) / d.lengthSqr(), 0, 1);
            double margin = (range + 18.0) / edge.getLength();
            double lo = Math.max(0, nearest - margin), hi = Math.min(1, nearest + margin);
            int steps = Math.max(1, (int)Math.ceil((hi - lo) * edge.getLength() * 4));
            for (int i = 0; i <= steps; i++) {
                if (result.size() >= faceBudget) break;
                Vec3 p = edge.getPosition(graph, lo + (hi - lo) * i / steps);
                if (p.distanceToSqr(camera) > (range + 16.0) * (range + 16.0)) continue;
                BlockPos pos = BlockPos.containing(p.add(0, .01, 0));
                if (!blocks.add(pos) || !level.hasChunkAt(pos)) continue;
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof ITrackBlock)) continue;
                BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
                ModelData data = level.getModelDataManager().getAt(pos);
                data = model.getModelData(level, pos, state, data == null ? ModelData.EMPTY : data);
                add(result, model, state, data, new Matrix4f().translation(pos.getX(), pos.getY(), pos.getZ()),
                        graph, edge, Double.NaN, faceBudget);
            }
        }
        return List.copyOf(result);
    }

    private static void add(List<Surface> out, BakedModel model, BlockState state, ModelData modelData, Matrix4f transform,
                            TrackGraph graph, TrackEdge edge, double curveFraction, int faceBudget) {
        var random = RandomSource.create(42);
        List<BakedQuad> quads = new ArrayList<>(model.getQuads(state, null, random, modelData, RenderType.cutoutMipped()));
        for (Direction face : Direction.values()) {
            random.setSeed(42);
            quads.addAll(model.getQuads(state, face, random, modelData, RenderType.cutoutMipped()));
        }
        Vec3 start = edge.getPosition(graph, 0), delta = edge.getPosition(graph, 1).subtract(start);
        for (BakedQuad quad : quads) {
            if (out.size() >= faceBudget) break;
            // Create separates mipmapped sleeper tops from its standard_track rail sprite.
            if (!quad.getSprite().contents().name().getPath().equals("block/standard_track_mip")) continue;
            int[] data = quad.getVertices();
            int stride = data.length / 4;
            List<Vertex> vertices = new ArrayList<>(4);
            Vec3 centre = Vec3.ZERO;
            for (int i = 0; i < 4; i++) {
                int at = i * stride;
                Vector3f p = new Vector3f(Float.intBitsToFloat(data[at]), Float.intBitsToFloat(data[at + 1]),
                        Float.intBitsToFloat(data[at + 2]));
                transform.transformPosition(p);
                Vec3 point = new Vec3(p.x, p.y, p.z);
                centre = centre.add(point);
                vertices.add(new Vertex(point, Float.intBitsToFloat(data[at + 4]), Float.intBitsToFloat(data[at + 5])));
            }
            Vec3 normal = vertices.get(1).position().subtract(vertices.getFirst().position())
                    .cross(vertices.get(2).position().subtract(vertices.getFirst().position())).normalize();
            if (normal.y < .25) continue; // Exclude sleeper sides, including rotated OBJ faces.
            centre = centre.scale(.25);
            double fraction = Double.isNaN(curveFraction) ? centre.subtract(start).dot(delta) / delta.lengthSqr() : curveFraction;
            if (fraction < -1.0e-4 || fraction > 1.0001) continue;
            Vec3 track = edge.getPosition(graph, Math.clamp(fraction, 0, 1));
            Vec3 tangent = edge.getPosition(graph, Math.min(1, fraction + .001))
                    .subtract(edge.getPosition(graph, Math.max(0, fraction - .001)));
            Vec3 lateral = new Vec3(-tangent.z, 0, tangent.x).normalize();
            // Reject other crossing axes and adjacent tracks. Each sleeper top is a half-width face.
            Vec3 across = vertices.get(1).position().subtract(vertices.getFirst().position());
            Vec3 across2 = vertices.get(2).position().subtract(vertices.get(1).position());
            Vec3 longSide = across.lengthSqr() > across2.lengthSqr() ? across : across2;
            if (Math.abs(longSide.normalize().dot(lateral)) < .9 || Math.abs(centre.y - track.y) > .5) continue;
            double side = centre.subtract(track).dot(lateral);
            Vec3 lift = normal.scale(1.0 / 512);
            out.add(new Surface(vertices.stream().map(v -> new Vertex(v.position().add(lift), v.u(), v.v())).toList(),
                    centre, normal, Math.clamp(fraction, 0, 1), side));
        }
    }
}
