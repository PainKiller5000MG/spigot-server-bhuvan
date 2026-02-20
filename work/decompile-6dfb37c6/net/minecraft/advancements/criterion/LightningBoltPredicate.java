package net.minecraft.advancements.criterion;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public record LightningBoltPredicate(MinMaxBounds.Ints blocksSetOnFire, Optional<EntityPredicate> entityStruck) implements EntitySubPredicate {

    public static final MapCodec<LightningBoltPredicate> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(MinMaxBounds.Ints.CODEC.optionalFieldOf("blocks_set_on_fire", MinMaxBounds.Ints.ANY).forGetter(LightningBoltPredicate::blocksSetOnFire), EntityPredicate.CODEC.optionalFieldOf("entity_struck").forGetter(LightningBoltPredicate::entityStruck)).apply(instance, LightningBoltPredicate::new);
    });

    public static LightningBoltPredicate blockSetOnFire(MinMaxBounds.Ints count) {
        return new LightningBoltPredicate(count, Optional.empty());
    }

    @Override
    public MapCodec<LightningBoltPredicate> codec() {
        return EntitySubPredicates.LIGHTNING;
    }

    @Override
    public boolean matches(Entity entity, ServerLevel level, @Nullable Vec3 position) {
        if (!(entity instanceof LightningBolt lightningbolt)) {
            return false;
        } else {
            return this.blocksSetOnFire.matches(lightningbolt.getBlocksSetOnFire()) && (this.entityStruck.isEmpty() || lightningbolt.getHitEntities().anyMatch((entity1) -> {
                return ((EntityPredicate) this.entityStruck.get()).matches(level, position, entity1);
            }));
        }
    }
}
