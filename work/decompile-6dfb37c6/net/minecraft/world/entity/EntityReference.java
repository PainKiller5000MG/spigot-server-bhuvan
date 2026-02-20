package net.minecraft.world.entity;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.players.OldUsersConverter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.UUIDLookup;
import net.minecraft.world.level.entity.UniquelyIdentifyable;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public final class EntityReference<StoredEntityType extends UniquelyIdentifyable> {

    private static final Codec<? extends EntityReference<?>> CODEC = UUIDUtil.CODEC.xmap(EntityReference::new, EntityReference::getUUID);
    private static final StreamCodec<ByteBuf, ? extends EntityReference<?>> STREAM_CODEC = UUIDUtil.STREAM_CODEC.map(EntityReference::new, EntityReference::getUUID);
    private Either<UUID, StoredEntityType> entity;

    public static <Type extends UniquelyIdentifyable> Codec<EntityReference<Type>> codec() {
        return EntityReference.CODEC;
    }

    public static <Type extends UniquelyIdentifyable> StreamCodec<ByteBuf, EntityReference<Type>> streamCodec() {
        return EntityReference.STREAM_CODEC;
    }

    private EntityReference(StoredEntityType entity) {
        this.entity = Either.right(entity);
    }

    private EntityReference(UUID uuid) {
        this.entity = Either.left(uuid);
    }

    public static <T extends UniquelyIdentifyable> @Nullable EntityReference<T> of(@Nullable T entity) {
        return entity != null ? new EntityReference(entity) : null;
    }

    public static <T extends UniquelyIdentifyable> EntityReference<T> of(UUID uuid) {
        return new EntityReference<T>(uuid);
    }

    public UUID getUUID() {
        return (UUID) this.entity.map((uuid) -> {
            return uuid;
        }, UniquelyIdentifyable::getUUID);
    }

    public @Nullable StoredEntityType getEntity(UUIDLookup<? extends UniquelyIdentifyable> lookup, Class<StoredEntityType> clazz) {
        Optional<StoredEntityType> optional = this.entity.right();

        if (optional.isPresent()) {
            StoredEntityType storedentitytype = (StoredEntityType) (optional.get());

            if (!storedentitytype.isRemoved()) {
                return storedentitytype;
            }

            this.entity = Either.left(storedentitytype.getUUID());
        }

        Optional<UUID> optional1 = this.entity.left();

        if (optional1.isPresent()) {
            StoredEntityType storedentitytype1 = this.resolve(lookup.lookup((UUID) optional1.get()), clazz);

            if (storedentitytype1 != null && !storedentitytype1.isRemoved()) {
                this.entity = Either.right(storedentitytype1);
                return storedentitytype1;
            }
        }

        return null;
    }

    public @Nullable StoredEntityType getEntity(Level level, Class<StoredEntityType> clazz) {
        if (Player.class.isAssignableFrom(clazz)) {
            Objects.requireNonNull(level);
            return (StoredEntityType) this.getEntity(level::getPlayerInAnyDimension, clazz);
        } else {
            Objects.requireNonNull(level);
            return (StoredEntityType) this.getEntity(level::getEntityInAnyDimension, clazz);
        }
    }

    private @Nullable StoredEntityType resolve(@Nullable UniquelyIdentifyable entity, Class<StoredEntityType> clazz) {
        return (StoredEntityType) (entity != null && clazz.isAssignableFrom(entity.getClass()) ? (UniquelyIdentifyable) clazz.cast(entity) : null);
    }

    public boolean matches(StoredEntityType entity) {
        return this.getUUID().equals(entity.getUUID());
    }

    public void store(ValueOutput output, String key) {
        output.store(key, UUIDUtil.CODEC, this.getUUID());
    }

    public static void store(@Nullable EntityReference<?> reference, ValueOutput output, String key) {
        if (reference != null) {
            reference.store(output, key);
        }

    }

    public static <StoredEntityType extends UniquelyIdentifyable> @Nullable StoredEntityType get(@Nullable EntityReference<StoredEntityType> reference, Level level, Class<StoredEntityType> clazz) {
        return (StoredEntityType) (reference != null ? reference.getEntity(level, clazz) : null);
    }

    public static @Nullable Entity getEntity(@Nullable EntityReference<Entity> reference, Level level) {
        return (Entity) get(reference, level, Entity.class);
    }

    public static @Nullable LivingEntity getLivingEntity(@Nullable EntityReference<LivingEntity> reference, Level level) {
        return (LivingEntity) get(reference, level, LivingEntity.class);
    }

    public static @Nullable Player getPlayer(@Nullable EntityReference<Player> reference, Level level) {
        return (Player) get(reference, level, Player.class);
    }

    public static <StoredEntityType extends UniquelyIdentifyable> @Nullable EntityReference<StoredEntityType> read(ValueInput input, String key) {
        return (EntityReference) input.read(key, codec()).orElse((Object) null);
    }

    public static <StoredEntityType extends UniquelyIdentifyable> @Nullable EntityReference<StoredEntityType> readWithOldOwnerConversion(ValueInput input, String key, Level level) {
        Optional<UUID> optional = input.<UUID>read(key, UUIDUtil.CODEC);

        return optional.isPresent() ? of((UUID) optional.get()) : (EntityReference) input.getString(key).map((s1) -> {
            return OldUsersConverter.convertMobOwnerIfNecessary(level.getServer(), s1);
        }).map(EntityReference::new).orElse((Object) null);
    }

    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else {
            boolean flag;

            if (obj instanceof EntityReference) {
                EntityReference<?> entityreference = (EntityReference) obj;

                if (this.getUUID().equals(entityreference.getUUID())) {
                    flag = true;
                    return flag;
                }
            }

            flag = false;
            return flag;
        }
    }

    public int hashCode() {
        return this.getUUID().hashCode();
    }
}
