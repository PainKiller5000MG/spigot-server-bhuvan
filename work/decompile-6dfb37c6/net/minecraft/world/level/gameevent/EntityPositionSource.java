package net.minecraft.world.level.gameevent;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class EntityPositionSource implements PositionSource {

    public static final MapCodec<EntityPositionSource> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(UUIDUtil.CODEC.fieldOf("source_entity").forGetter(EntityPositionSource::getUuid), Codec.FLOAT.fieldOf("y_offset").orElse(0.0F).forGetter((entitypositionsource) -> {
            return entitypositionsource.yOffset;
        })).apply(instance, (uuid, ofloat) -> {
            return new EntityPositionSource(Either.right(Either.left(uuid)), ofloat);
        });
    });
    public static final StreamCodec<ByteBuf, EntityPositionSource> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, EntityPositionSource::getId, ByteBufCodecs.FLOAT, (entitypositionsource) -> {
        return entitypositionsource.yOffset;
    }, (integer, ofloat) -> {
        return new EntityPositionSource(Either.right(Either.right(integer)), ofloat);
    });
    private Either<Entity, Either<UUID, Integer>> entityOrUuidOrId;
    private final float yOffset;

    public EntityPositionSource(Entity entity, float yOffset) {
        this(Either.left(entity), yOffset);
    }

    private EntityPositionSource(Either<Entity, Either<UUID, Integer>> entityOrUuidOrId, float yOffset) {
        this.entityOrUuidOrId = entityOrUuidOrId;
        this.yOffset = yOffset;
    }

    @Override
    public Optional<Vec3> getPosition(Level level) {
        if (this.entityOrUuidOrId.left().isEmpty()) {
            this.resolveEntity(level);
        }

        return this.entityOrUuidOrId.left().map((entity) -> {
            return entity.position().add(0.0D, (double) this.yOffset, 0.0D);
        });
    }

    private void resolveEntity(Level level) {
        ((Optional) this.entityOrUuidOrId.map(Optional::of, (either) -> {
            Function function = (uuid) -> {
                Entity entity;

                if (level instanceof ServerLevel serverlevel) {
                    entity = serverlevel.getEntity(uuid);
                } else {
                    entity = null;
                }

                return entity;
            };

            Objects.requireNonNull(level);
            return Optional.ofNullable((Entity) either.map(function, level::getEntity));
        })).ifPresent((entity) -> {
            this.entityOrUuidOrId = Either.left(entity);
        });
    }

    public UUID getUuid() {
        return (UUID) this.entityOrUuidOrId.map(Entity::getUUID, (either) -> {
            return (UUID) either.map(Function.identity(), (integer) -> {
                throw new RuntimeException("Unable to get entityId from uuid");
            });
        });
    }

    private int getId() {
        return (Integer) this.entityOrUuidOrId.map(Entity::getId, (either) -> {
            return (Integer) either.map((uuid) -> {
                throw new IllegalStateException("Unable to get entityId from uuid");
            }, Function.identity());
        });
    }

    @Override
    public PositionSourceType<EntityPositionSource> getType() {
        return PositionSourceType.ENTITY;
    }

    public static class Type implements PositionSourceType<EntityPositionSource> {

        public Type() {}

        @Override
        public MapCodec<EntityPositionSource> codec() {
            return EntityPositionSource.CODEC;
        }

        @Override
        public StreamCodec<ByteBuf, EntityPositionSource> streamCodec() {
            return EntityPositionSource.STREAM_CODEC;
        }
    }
}
