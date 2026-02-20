package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public record SummonEntityEffect(HolderSet<EntityType<?>> entityTypes, boolean joinTeam) implements EnchantmentEntityEffect {

    public static final MapCodec<SummonEntityEffect> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(RegistryCodecs.homogeneousList(Registries.ENTITY_TYPE).fieldOf("entity").forGetter(SummonEntityEffect::entityTypes), Codec.BOOL.optionalFieldOf("join_team", false).forGetter(SummonEntityEffect::joinTeam)).apply(instance, SummonEntityEffect::new);
    });

    @Override
    public void apply(ServerLevel serverLevel, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 position) {
        BlockPos blockpos = BlockPos.containing(position);

        if (Level.isInSpawnableBounds(blockpos)) {
            Optional<Holder<EntityType<?>>> optional = this.entityTypes().getRandomElement(serverLevel.getRandom());

            if (!optional.isEmpty()) {
                Entity entity1 = ((EntityType) ((Holder) optional.get()).value()).spawn(serverLevel, blockpos, EntitySpawnReason.TRIGGERED);

                if (entity1 != null) {
                    if (entity1 instanceof LightningBolt) {
                        LightningBolt lightningbolt = (LightningBolt) entity1;
                        LivingEntity livingentity = item.owner();

                        if (livingentity instanceof ServerPlayer) {
                            ServerPlayer serverplayer = (ServerPlayer) livingentity;

                            lightningbolt.setCause(serverplayer);
                        }
                    }

                    if (this.joinTeam && entity.getTeam() != null) {
                        serverLevel.getScoreboard().addPlayerToTeam(entity1.getScoreboardName(), entity.getTeam());
                    }

                    entity1.snapTo(position.x, position.y, position.z, entity1.getYRot(), entity1.getXRot());
                }
            }
        }
    }

    @Override
    public MapCodec<SummonEntityEffect> codec() {
        return SummonEntityEffect.CODEC;
    }
}
