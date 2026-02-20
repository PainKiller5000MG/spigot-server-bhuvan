package net.minecraft.world.level;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class SimpleExplosionDamageCalculator extends ExplosionDamageCalculator {

    private final boolean explodesBlocks;
    private final boolean damagesEntities;
    private final Optional<Float> knockbackMultiplier;
    private final Optional<HolderSet<Block>> immuneBlocks;

    public SimpleExplosionDamageCalculator(boolean explodesBlocks, boolean damagesEntities, Optional<Float> knockbackMultiplier, Optional<HolderSet<Block>> immuneBlocks) {
        this.explodesBlocks = explodesBlocks;
        this.damagesEntities = damagesEntities;
        this.knockbackMultiplier = knockbackMultiplier;
        this.immuneBlocks = immuneBlocks;
    }

    @Override
    public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter level, BlockPos pos, BlockState block, FluidState fluid) {
        return this.immuneBlocks.isPresent() ? (block.is((HolderSet) this.immuneBlocks.get()) ? Optional.of(3600000.0F) : Optional.empty()) : super.getBlockExplosionResistance(explosion, level, pos, block, fluid);
    }

    @Override
    public boolean shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, float power) {
        return this.explodesBlocks;
    }

    @Override
    public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
        return this.damagesEntities;
    }

    @Override
    public float getKnockbackMultiplier(Entity entity) {
        boolean flag;
        label17:
        {
            if (entity instanceof Player player) {
                if (player.getAbilities().flying) {
                    flag = true;
                    break label17;
                }
            }

            flag = false;
        }

        boolean flag1 = flag;

        return flag1 ? 0.0F : (Float) this.knockbackMultiplier.orElseGet(() -> {
            return super.getKnockbackMultiplier(entity);
        });
    }
}
