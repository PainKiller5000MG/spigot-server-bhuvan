package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.phys.Vec3;

public abstract class ServerboundMovePlayerPacket implements Packet<ServerGamePacketListener> {

    private static final int FLAG_ON_GROUND = 1;
    private static final int FLAG_HORIZONTAL_COLLISION = 2;
    public final double x;
    public final double y;
    public final double z;
    public final float yRot;
    public final float xRot;
    protected final boolean onGround;
    protected final boolean horizontalCollision;
    public final boolean hasPos;
    public final boolean hasRot;

    private static int packFlags(boolean onGround, boolean horizontalCollision) {
        int i = 0;

        if (onGround) {
            i |= 1;
        }

        if (horizontalCollision) {
            i |= 2;
        }

        return i;
    }

    private static boolean unpackOnGround(int flags) {
        return (flags & 1) != 0;
    }

    private static boolean unpackHorizontalCollision(int flags) {
        return (flags & 2) != 0;
    }

    protected ServerboundMovePlayerPacket(double x, double y, double z, float yRot, float xRot, boolean onGround, boolean horizontalCollision, boolean hasPos, boolean hasRot) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yRot = yRot;
        this.xRot = xRot;
        this.onGround = onGround;
        this.horizontalCollision = horizontalCollision;
        this.hasPos = hasPos;
        this.hasRot = hasRot;
    }

    @Override
    public abstract PacketType<? extends ServerboundMovePlayerPacket> type();

    public void handle(ServerGamePacketListener listener) {
        listener.handleMovePlayer(this);
    }

    public double getX(double fallback) {
        return this.hasPos ? this.x : fallback;
    }

    public double getY(double fallback) {
        return this.hasPos ? this.y : fallback;
    }

    public double getZ(double fallback) {
        return this.hasPos ? this.z : fallback;
    }

    public float getYRot(float fallback) {
        return this.hasRot ? this.yRot : fallback;
    }

    public float getXRot(float fallback) {
        return this.hasRot ? this.xRot : fallback;
    }

    public boolean isOnGround() {
        return this.onGround;
    }

    public boolean horizontalCollision() {
        return this.horizontalCollision;
    }

    public boolean hasPosition() {
        return this.hasPos;
    }

    public boolean hasRotation() {
        return this.hasRot;
    }

    public static class PosRot extends ServerboundMovePlayerPacket {

        public static final StreamCodec<FriendlyByteBuf, ServerboundMovePlayerPacket.PosRot> STREAM_CODEC = Packet.<FriendlyByteBuf, ServerboundMovePlayerPacket.PosRot>codec(ServerboundMovePlayerPacket.PosRot::write, ServerboundMovePlayerPacket.PosRot::read);

        public PosRot(Vec3 pos, float yRot, float xRot, boolean onGround, boolean horizontalCollision) {
            super(pos.x, pos.y, pos.z, yRot, xRot, onGround, horizontalCollision, true, true);
        }

        public PosRot(double x, double y, double z, float yRot, float xRot, boolean onGround, boolean horizontalCollision) {
            super(x, y, z, yRot, xRot, onGround, horizontalCollision, true, true);
        }

        private static ServerboundMovePlayerPacket.PosRot read(FriendlyByteBuf input) {
            double d0 = input.readDouble();
            double d1 = input.readDouble();
            double d2 = input.readDouble();
            float f = input.readFloat();
            float f1 = input.readFloat();
            short short0 = input.readUnsignedByte();
            boolean flag = ServerboundMovePlayerPacket.unpackOnGround(short0);
            boolean flag1 = ServerboundMovePlayerPacket.unpackHorizontalCollision(short0);

            return new ServerboundMovePlayerPacket.PosRot(d0, d1, d2, f, f1, flag, flag1);
        }

        private void write(FriendlyByteBuf output) {
            output.writeDouble(this.x);
            output.writeDouble(this.y);
            output.writeDouble(this.z);
            output.writeFloat(this.yRot);
            output.writeFloat(this.xRot);
            output.writeByte(ServerboundMovePlayerPacket.packFlags(this.onGround, this.horizontalCollision));
        }

        @Override
        public PacketType<ServerboundMovePlayerPacket.PosRot> type() {
            return GamePacketTypes.SERVERBOUND_MOVE_PLAYER_POS_ROT;
        }
    }

    public static class Pos extends ServerboundMovePlayerPacket {

        public static final StreamCodec<FriendlyByteBuf, ServerboundMovePlayerPacket.Pos> STREAM_CODEC = Packet.<FriendlyByteBuf, ServerboundMovePlayerPacket.Pos>codec(ServerboundMovePlayerPacket.Pos::write, ServerboundMovePlayerPacket.Pos::read);

        public Pos(Vec3 pos, boolean onGround, boolean horizontalCollision) {
            super(pos.x, pos.y, pos.z, 0.0F, 0.0F, onGround, horizontalCollision, true, false);
        }

        public Pos(double x, double y, double z, boolean onGround, boolean horizontalCollision) {
            super(x, y, z, 0.0F, 0.0F, onGround, horizontalCollision, true, false);
        }

        private static ServerboundMovePlayerPacket.Pos read(FriendlyByteBuf input) {
            double d0 = input.readDouble();
            double d1 = input.readDouble();
            double d2 = input.readDouble();
            short short0 = input.readUnsignedByte();
            boolean flag = ServerboundMovePlayerPacket.unpackOnGround(short0);
            boolean flag1 = ServerboundMovePlayerPacket.unpackHorizontalCollision(short0);

            return new ServerboundMovePlayerPacket.Pos(d0, d1, d2, flag, flag1);
        }

        private void write(FriendlyByteBuf output) {
            output.writeDouble(this.x);
            output.writeDouble(this.y);
            output.writeDouble(this.z);
            output.writeByte(ServerboundMovePlayerPacket.packFlags(this.onGround, this.horizontalCollision));
        }

        @Override
        public PacketType<ServerboundMovePlayerPacket.Pos> type() {
            return GamePacketTypes.SERVERBOUND_MOVE_PLAYER_POS;
        }
    }

    public static class Rot extends ServerboundMovePlayerPacket {

        public static final StreamCodec<FriendlyByteBuf, ServerboundMovePlayerPacket.Rot> STREAM_CODEC = Packet.<FriendlyByteBuf, ServerboundMovePlayerPacket.Rot>codec(ServerboundMovePlayerPacket.Rot::write, ServerboundMovePlayerPacket.Rot::read);

        public Rot(float yRot, float xRot, boolean onGround, boolean horizontalCollision) {
            super(0.0D, 0.0D, 0.0D, yRot, xRot, onGround, horizontalCollision, false, true);
        }

        private static ServerboundMovePlayerPacket.Rot read(FriendlyByteBuf input) {
            float f = input.readFloat();
            float f1 = input.readFloat();
            short short0 = input.readUnsignedByte();
            boolean flag = ServerboundMovePlayerPacket.unpackOnGround(short0);
            boolean flag1 = ServerboundMovePlayerPacket.unpackHorizontalCollision(short0);

            return new ServerboundMovePlayerPacket.Rot(f, f1, flag, flag1);
        }

        private void write(FriendlyByteBuf output) {
            output.writeFloat(this.yRot);
            output.writeFloat(this.xRot);
            output.writeByte(ServerboundMovePlayerPacket.packFlags(this.onGround, this.horizontalCollision));
        }

        @Override
        public PacketType<ServerboundMovePlayerPacket.Rot> type() {
            return GamePacketTypes.SERVERBOUND_MOVE_PLAYER_ROT;
        }
    }

    public static class StatusOnly extends ServerboundMovePlayerPacket {

        public static final StreamCodec<FriendlyByteBuf, ServerboundMovePlayerPacket.StatusOnly> STREAM_CODEC = Packet.<FriendlyByteBuf, ServerboundMovePlayerPacket.StatusOnly>codec(ServerboundMovePlayerPacket.StatusOnly::write, ServerboundMovePlayerPacket.StatusOnly::read);

        public StatusOnly(boolean onGround, boolean horizontalCollision) {
            super(0.0D, 0.0D, 0.0D, 0.0F, 0.0F, onGround, horizontalCollision, false, false);
        }

        private static ServerboundMovePlayerPacket.StatusOnly read(FriendlyByteBuf input) {
            short short0 = input.readUnsignedByte();
            boolean flag = ServerboundMovePlayerPacket.unpackOnGround(short0);
            boolean flag1 = ServerboundMovePlayerPacket.unpackHorizontalCollision(short0);

            return new ServerboundMovePlayerPacket.StatusOnly(flag, flag1);
        }

        private void write(FriendlyByteBuf output) {
            output.writeByte(ServerboundMovePlayerPacket.packFlags(this.onGround, this.horizontalCollision));
        }

        @Override
        public PacketType<ServerboundMovePlayerPacket.StatusOnly> type() {
            return GamePacketTypes.SERVERBOUND_MOVE_PLAYER_STATUS_ONLY;
        }
    }
}
