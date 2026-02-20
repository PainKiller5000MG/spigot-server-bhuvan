package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public abstract class ClientboundMoveEntityPacket implements Packet<ClientGamePacketListener> {

    protected final int entityId;
    protected final short xa;
    protected final short ya;
    protected final short za;
    protected final byte yRot;
    protected final byte xRot;
    protected final boolean onGround;
    protected final boolean hasRot;
    protected final boolean hasPos;

    protected ClientboundMoveEntityPacket(int entityId, short xa, short ya, short za, byte yRot, byte xRot, boolean onGround, boolean hasRot, boolean hasPos) {
        this.entityId = entityId;
        this.xa = xa;
        this.ya = ya;
        this.za = za;
        this.yRot = yRot;
        this.xRot = xRot;
        this.onGround = onGround;
        this.hasRot = hasRot;
        this.hasPos = hasPos;
    }

    @Override
    public abstract PacketType<? extends ClientboundMoveEntityPacket> type();

    public void handle(ClientGamePacketListener listener) {
        listener.handleMoveEntity(this);
    }

    public String toString() {
        return "Entity_" + super.toString();
    }

    public @Nullable Entity getEntity(Level level) {
        return level.getEntity(this.entityId);
    }

    public short getXa() {
        return this.xa;
    }

    public short getYa() {
        return this.ya;
    }

    public short getZa() {
        return this.za;
    }

    public float getYRot() {
        return Mth.unpackDegrees(this.yRot);
    }

    public float getXRot() {
        return Mth.unpackDegrees(this.xRot);
    }

    public boolean hasRotation() {
        return this.hasRot;
    }

    public boolean hasPosition() {
        return this.hasPos;
    }

    public boolean isOnGround() {
        return this.onGround;
    }

    public static class PosRot extends ClientboundMoveEntityPacket {

        public static final StreamCodec<FriendlyByteBuf, ClientboundMoveEntityPacket.PosRot> STREAM_CODEC = Packet.<FriendlyByteBuf, ClientboundMoveEntityPacket.PosRot>codec(ClientboundMoveEntityPacket.PosRot::write, ClientboundMoveEntityPacket.PosRot::read);

        public PosRot(int id, short xa, short ya, short za, byte yRot, byte xRot, boolean onGround) {
            super(id, xa, ya, za, yRot, xRot, onGround, true, true);
        }

        private static ClientboundMoveEntityPacket.PosRot read(FriendlyByteBuf input) {
            int i = input.readVarInt();
            short short0 = input.readShort();
            short short1 = input.readShort();
            short short2 = input.readShort();
            byte b0 = input.readByte();
            byte b1 = input.readByte();
            boolean flag = input.readBoolean();

            return new ClientboundMoveEntityPacket.PosRot(i, short0, short1, short2, b0, b1, flag);
        }

        private void write(FriendlyByteBuf output) {
            output.writeVarInt(this.entityId);
            output.writeShort(this.xa);
            output.writeShort(this.ya);
            output.writeShort(this.za);
            output.writeByte(this.yRot);
            output.writeByte(this.xRot);
            output.writeBoolean(this.onGround);
        }

        @Override
        public PacketType<ClientboundMoveEntityPacket.PosRot> type() {
            return GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_POS_ROT;
        }
    }

    public static class Pos extends ClientboundMoveEntityPacket {

        public static final StreamCodec<FriendlyByteBuf, ClientboundMoveEntityPacket.Pos> STREAM_CODEC = Packet.<FriendlyByteBuf, ClientboundMoveEntityPacket.Pos>codec(ClientboundMoveEntityPacket.Pos::write, ClientboundMoveEntityPacket.Pos::read);

        public Pos(int id, short xa, short ya, short za, boolean onGround) {
            super(id, xa, ya, za, (byte) 0, (byte) 0, onGround, false, true);
        }

        private static ClientboundMoveEntityPacket.Pos read(FriendlyByteBuf input) {
            int i = input.readVarInt();
            short short0 = input.readShort();
            short short1 = input.readShort();
            short short2 = input.readShort();
            boolean flag = input.readBoolean();

            return new ClientboundMoveEntityPacket.Pos(i, short0, short1, short2, flag);
        }

        private void write(FriendlyByteBuf output) {
            output.writeVarInt(this.entityId);
            output.writeShort(this.xa);
            output.writeShort(this.ya);
            output.writeShort(this.za);
            output.writeBoolean(this.onGround);
        }

        @Override
        public PacketType<ClientboundMoveEntityPacket.Pos> type() {
            return GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_POS;
        }
    }

    public static class Rot extends ClientboundMoveEntityPacket {

        public static final StreamCodec<FriendlyByteBuf, ClientboundMoveEntityPacket.Rot> STREAM_CODEC = Packet.<FriendlyByteBuf, ClientboundMoveEntityPacket.Rot>codec(ClientboundMoveEntityPacket.Rot::write, ClientboundMoveEntityPacket.Rot::read);

        public Rot(int id, byte yRot, byte xRot, boolean onGround) {
            super(id, (short) 0, (short) 0, (short) 0, yRot, xRot, onGround, true, false);
        }

        private static ClientboundMoveEntityPacket.Rot read(FriendlyByteBuf input) {
            int i = input.readVarInt();
            byte b0 = input.readByte();
            byte b1 = input.readByte();
            boolean flag = input.readBoolean();

            return new ClientboundMoveEntityPacket.Rot(i, b0, b1, flag);
        }

        private void write(FriendlyByteBuf output) {
            output.writeVarInt(this.entityId);
            output.writeByte(this.yRot);
            output.writeByte(this.xRot);
            output.writeBoolean(this.onGround);
        }

        @Override
        public PacketType<ClientboundMoveEntityPacket.Rot> type() {
            return GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_ROT;
        }
    }
}
