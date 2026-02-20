package net.minecraft.world.waypoints;

import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.function.TriFunction;
import org.slf4j.Logger;

public abstract class TrackedWaypoint implements Waypoint {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final StreamCodec<ByteBuf, TrackedWaypoint> STREAM_CODEC = StreamCodec.<ByteBuf, TrackedWaypoint>ofMember(TrackedWaypoint::write, TrackedWaypoint::read);
    protected final Either<UUID, String> identifier;
    private final Waypoint.Icon icon;
    private final TrackedWaypoint.Type type;

    private TrackedWaypoint(Either<UUID, String> identifier, Waypoint.Icon icon, TrackedWaypoint.Type type) {
        this.identifier = identifier;
        this.icon = icon;
        this.type = type;
    }

    public Either<UUID, String> id() {
        return this.identifier;
    }

    public abstract void update(TrackedWaypoint other);

    public void write(ByteBuf buf) {
        FriendlyByteBuf friendlybytebuf = new FriendlyByteBuf(buf);

        friendlybytebuf.writeEither(this.identifier, UUIDUtil.STREAM_CODEC, FriendlyByteBuf::writeUtf);
        Waypoint.Icon.STREAM_CODEC.encode(friendlybytebuf, this.icon);
        friendlybytebuf.writeEnum(this.type);
        this.writeContents(buf);
    }

    public abstract void writeContents(ByteBuf buf);

    private static TrackedWaypoint read(ByteBuf buf) {
        FriendlyByteBuf friendlybytebuf = new FriendlyByteBuf(buf);
        Either<UUID, String> either = friendlybytebuf.<UUID, String>readEither(UUIDUtil.STREAM_CODEC, FriendlyByteBuf::readUtf);
        Waypoint.Icon waypoint_icon = (Waypoint.Icon) Waypoint.Icon.STREAM_CODEC.decode(friendlybytebuf);
        TrackedWaypoint.Type trackedwaypoint_type = (TrackedWaypoint.Type) friendlybytebuf.readEnum(TrackedWaypoint.Type.class);

        return (TrackedWaypoint) trackedwaypoint_type.constructor.apply(either, waypoint_icon, friendlybytebuf);
    }

    public static TrackedWaypoint setPosition(UUID identifier, Waypoint.Icon icon, Vec3i position) {
        return new TrackedWaypoint.Vec3iWaypoint(identifier, icon, position);
    }

    public static TrackedWaypoint setChunk(UUID identifier, Waypoint.Icon icon, ChunkPos chunk) {
        return new TrackedWaypoint.ChunkWaypoint(identifier, icon, chunk);
    }

    public static TrackedWaypoint setAzimuth(UUID identifier, Waypoint.Icon icon, float angle) {
        return new TrackedWaypoint.AzimuthWaypoint(identifier, icon, angle);
    }

    public static TrackedWaypoint empty(UUID identifier) {
        return new TrackedWaypoint.EmptyWaypoint(identifier);
    }

    public abstract double yawAngleToCamera(Level level, TrackedWaypoint.Camera camera, PartialTickSupplier partialTickSupplier);

    public abstract TrackedWaypoint.PitchDirection pitchDirectionToCamera(Level level, TrackedWaypoint.Projector projector, PartialTickSupplier partialTickSupplier);

    public abstract double distanceSquared(Entity fromEntity);

    public Waypoint.Icon icon() {
        return this.icon;
    }

    public static enum PitchDirection {

        NONE, UP, DOWN;

        private PitchDirection() {}
    }

    private static enum Type {

        EMPTY(TrackedWaypoint.EmptyWaypoint::new), VEC3I(TrackedWaypoint.Vec3iWaypoint::new), CHUNK(TrackedWaypoint.ChunkWaypoint::new), AZIMUTH(TrackedWaypoint.AzimuthWaypoint::new);

        private final TriFunction<Either<UUID, String>, Waypoint.Icon, FriendlyByteBuf, TrackedWaypoint> constructor;

        private Type(TriFunction<Either<UUID, String>, Waypoint.Icon, FriendlyByteBuf, TrackedWaypoint> constructor) {
            this.constructor = constructor;
        }
    }

    private static class EmptyWaypoint extends TrackedWaypoint {

        private EmptyWaypoint(Either<UUID, String> identifier, Waypoint.Icon icon, FriendlyByteBuf byteBuf) {
            super(identifier, icon, TrackedWaypoint.Type.EMPTY);
        }

        private EmptyWaypoint(UUID identifier) {
            super(Either.left(identifier), Waypoint.Icon.NULL, TrackedWaypoint.Type.EMPTY);
        }

        @Override
        public void update(TrackedWaypoint other) {}

        @Override
        public void writeContents(ByteBuf buf) {}

        @Override
        public double yawAngleToCamera(Level level, TrackedWaypoint.Camera camera, PartialTickSupplier partialTickSupplier) {
            return Double.NaN;
        }

        @Override
        public TrackedWaypoint.PitchDirection pitchDirectionToCamera(Level level, TrackedWaypoint.Projector projector, PartialTickSupplier partialTickSupplier) {
            return TrackedWaypoint.PitchDirection.NONE;
        }

        @Override
        public double distanceSquared(Entity fromEntity) {
            return Double.POSITIVE_INFINITY;
        }
    }

    private static class Vec3iWaypoint extends TrackedWaypoint {

        private Vec3i vector;

        public Vec3iWaypoint(UUID identifier, Waypoint.Icon icon, Vec3i vector) {
            super(Either.left(identifier), icon, TrackedWaypoint.Type.VEC3I);
            this.vector = vector;
        }

        public Vec3iWaypoint(Either<UUID, String> identifier, Waypoint.Icon icon, FriendlyByteBuf byteBuf) {
            super(identifier, icon, TrackedWaypoint.Type.VEC3I);
            this.vector = new Vec3i(byteBuf.readVarInt(), byteBuf.readVarInt(), byteBuf.readVarInt());
        }

        @Override
        public void update(TrackedWaypoint other) {
            if (other instanceof TrackedWaypoint.Vec3iWaypoint trackedwaypoint_vec3iwaypoint) {
                this.vector = trackedwaypoint_vec3iwaypoint.vector;
            } else {
                TrackedWaypoint.LOGGER.warn("Unsupported Waypoint update operation: {}", other.getClass());
            }

        }

        @Override
        public void writeContents(ByteBuf buf) {
            VarInt.write(buf, this.vector.getX());
            VarInt.write(buf, this.vector.getY());
            VarInt.write(buf, this.vector.getZ());
        }

        private Vec3 position(Level level, PartialTickSupplier partialTick) {
            Optional optional = this.identifier.left();

            Objects.requireNonNull(level);
            return (Vec3) optional.map(level::getEntity).map((entity) -> {
                return entity.blockPosition().distManhattan(this.vector) > 3 ? null : entity.getEyePosition(partialTick.apply(entity));
            }).orElseGet(() -> {
                return Vec3.atCenterOf(this.vector);
            });
        }

        @Override
        public double yawAngleToCamera(Level level, TrackedWaypoint.Camera camera, PartialTickSupplier partialTickSupplier) {
            Vec3 vec3 = camera.position().subtract(this.position(level, partialTickSupplier)).rotateClockwise90();
            float f = (float) Mth.atan2(vec3.z(), vec3.x()) * (180F / (float) Math.PI);

            return (double) Mth.degreesDifference(camera.yaw(), f);
        }

        @Override
        public TrackedWaypoint.PitchDirection pitchDirectionToCamera(Level level, TrackedWaypoint.Projector projector, PartialTickSupplier partialTickSupplier) {
            Vec3 vec3 = projector.projectPointToScreen(this.position(level, partialTickSupplier));
            boolean flag = vec3.z > 1.0D;
            double d0 = flag ? -vec3.y : vec3.y;

            if (d0 < -1.0D) {
                return TrackedWaypoint.PitchDirection.DOWN;
            } else if (d0 > 1.0D) {
                return TrackedWaypoint.PitchDirection.UP;
            } else {
                if (flag) {
                    if (vec3.y > 0.0D) {
                        return TrackedWaypoint.PitchDirection.UP;
                    }

                    if (vec3.y < 0.0D) {
                        return TrackedWaypoint.PitchDirection.DOWN;
                    }
                }

                return TrackedWaypoint.PitchDirection.NONE;
            }
        }

        @Override
        public double distanceSquared(Entity fromEntity) {
            return fromEntity.distanceToSqr(Vec3.atCenterOf(this.vector));
        }
    }

    private static class ChunkWaypoint extends TrackedWaypoint {

        private ChunkPos chunkPos;

        public ChunkWaypoint(UUID identifier, Waypoint.Icon icon, ChunkPos chunkPos) {
            super(Either.left(identifier), icon, TrackedWaypoint.Type.CHUNK);
            this.chunkPos = chunkPos;
        }

        public ChunkWaypoint(Either<UUID, String> identifier, Waypoint.Icon icon, FriendlyByteBuf byteBuf) {
            super(identifier, icon, TrackedWaypoint.Type.CHUNK);
            this.chunkPos = new ChunkPos(byteBuf.readVarInt(), byteBuf.readVarInt());
        }

        @Override
        public void update(TrackedWaypoint other) {
            if (other instanceof TrackedWaypoint.ChunkWaypoint trackedwaypoint_chunkwaypoint) {
                this.chunkPos = trackedwaypoint_chunkwaypoint.chunkPos;
            } else {
                TrackedWaypoint.LOGGER.warn("Unsupported Waypoint update operation: {}", other.getClass());
            }

        }

        @Override
        public void writeContents(ByteBuf buf) {
            VarInt.write(buf, this.chunkPos.x);
            VarInt.write(buf, this.chunkPos.z);
        }

        private Vec3 position(double positionY) {
            return Vec3.atCenterOf(this.chunkPos.getMiddleBlockPosition((int) positionY));
        }

        @Override
        public double yawAngleToCamera(Level level, TrackedWaypoint.Camera camera, PartialTickSupplier partialTickSupplier) {
            Vec3 vec3 = camera.position();
            Vec3 vec31 = vec3.subtract(this.position(vec3.y())).rotateClockwise90();
            float f = (float) Mth.atan2(vec31.z(), vec31.x()) * (180F / (float) Math.PI);

            return (double) Mth.degreesDifference(camera.yaw(), f);
        }

        @Override
        public TrackedWaypoint.PitchDirection pitchDirectionToCamera(Level level, TrackedWaypoint.Projector projector, PartialTickSupplier partialTickSupplier) {
            double d0 = projector.projectHorizonToScreen();

            return d0 < -1.0D ? TrackedWaypoint.PitchDirection.DOWN : (d0 > 1.0D ? TrackedWaypoint.PitchDirection.UP : TrackedWaypoint.PitchDirection.NONE);
        }

        @Override
        public double distanceSquared(Entity fromEntity) {
            return fromEntity.distanceToSqr(Vec3.atCenterOf(this.chunkPos.getMiddleBlockPosition(fromEntity.getBlockY())));
        }
    }

    private static class AzimuthWaypoint extends TrackedWaypoint {

        private float angle;

        public AzimuthWaypoint(UUID identifier, Waypoint.Icon icon, float angle) {
            super(Either.left(identifier), icon, TrackedWaypoint.Type.AZIMUTH);
            this.angle = angle;
        }

        public AzimuthWaypoint(Either<UUID, String> identifier, Waypoint.Icon icon, FriendlyByteBuf byteBuf) {
            super(identifier, icon, TrackedWaypoint.Type.AZIMUTH);
            this.angle = byteBuf.readFloat();
        }

        @Override
        public void update(TrackedWaypoint other) {
            if (other instanceof TrackedWaypoint.AzimuthWaypoint trackedwaypoint_azimuthwaypoint) {
                this.angle = trackedwaypoint_azimuthwaypoint.angle;
            } else {
                TrackedWaypoint.LOGGER.warn("Unsupported Waypoint update operation: {}", other.getClass());
            }

        }

        @Override
        public void writeContents(ByteBuf buf) {
            buf.writeFloat(this.angle);
        }

        @Override
        public double yawAngleToCamera(Level level, TrackedWaypoint.Camera camera, PartialTickSupplier partialTickSupplier) {
            return (double) Mth.degreesDifference(camera.yaw(), this.angle * (180F / (float) Math.PI));
        }

        @Override
        public TrackedWaypoint.PitchDirection pitchDirectionToCamera(Level level, TrackedWaypoint.Projector projector, PartialTickSupplier partialTickSupplier) {
            double d0 = projector.projectHorizonToScreen();

            return d0 < -1.0D ? TrackedWaypoint.PitchDirection.DOWN : (d0 > 1.0D ? TrackedWaypoint.PitchDirection.UP : TrackedWaypoint.PitchDirection.NONE);
        }

        @Override
        public double distanceSquared(Entity fromEntity) {
            return Double.POSITIVE_INFINITY;
        }
    }

    public interface Camera {

        float yaw();

        Vec3 position();
    }

    public interface Projector {

        Vec3 projectPointToScreen(Vec3 point);

        double projectHorizonToScreen();
    }
}
