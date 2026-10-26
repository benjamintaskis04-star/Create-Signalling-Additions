package com.benjamintaskis.createsignalextensions.network;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import com.benjamintaskis.createsignalextensions.content.speed.SpeedZoneBoardData.Board;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

public record SpeedZoneBoardsPayload(ResourceLocation dimension, List<Board> boards) implements CustomPacketPayload {
    public static final Type<SpeedZoneBoardsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateSignallingAdditions.ID, "speed_zone_boards"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpeedZoneBoardsPayload> STREAM_CODEC =
            StreamCodec.ofMember(SpeedZoneBoardsPayload::encode, SpeedZoneBoardsPayload::decode);
    public SpeedZoneBoardsPayload { boards = List.copyOf(boards); }
    private static Vec3 vector(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
    private static SpeedZoneBoardsPayload decode(RegistryFriendlyByteBuf b) {
        ResourceLocation dimension = b.readResourceLocation();
        int size = b.readVarInt();
        if (size < 0 || size > 4096) throw new IllegalArgumentException("Invalid speed board count");
        List<Board> boards = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Board board = new Board(b.readBlockPos(), vector(b), vector(b), b.readDouble(), b.readVarInt(), b.readBoolean());
            if (board.valid()) boards.add(board);
        }
        return new SpeedZoneBoardsPayload(dimension, boards);
    }
    private void encode(RegistryFriendlyByteBuf b) {
        b.writeResourceLocation(dimension); b.writeVarInt(boards.size());
        for (Board board : boards) {
            b.writeBlockPos(board.sign());
            b.writeDouble(board.from().x); b.writeDouble(board.from().y); b.writeDouble(board.from().z);
            b.writeDouble(board.to().x); b.writeDouble(board.to().y); b.writeDouble(board.to().z);
            b.writeDouble(board.fraction()); b.writeVarInt(board.speed()); b.writeBoolean(board.turnout());
        }
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
