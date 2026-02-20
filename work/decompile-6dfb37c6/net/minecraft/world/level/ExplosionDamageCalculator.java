package net.minecraft.world.level;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

public class ExplosionDamageCalculator {

    public ExplosionDamageCalculator() {}

    public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter level, BlockPos pos, BlockState block, FluidState fluid) {
        return block.isAir() && fluid.isEmpty() ? Optional.empty() : Optional.of(Math.max(block.getBlock().getExplosionResistance(), fluid.getExplosionResistance()));
    }

    public boolean shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, float power) {
        return true;
    }

    public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
        return true;
    }

    public float getKnockbackMultiplier(Entity entity) {
        return 1.0F;
    }

    public float getEntityDamageAmount(Explosion explosion, Entity entity, float exposure) {
        float f1 = explosion.radius() * 2.0F;
        Vec3 vec3 = explosion.center();
        double d0 = Math.sqrt(entity.distanceToSqr(vec3)) / (double) f1;
        double d1 = (1.0D - d0) * (double) exposure;

        return (float) ((d1 * d1 + d1) / 2.0D * 7.0D * (double) f1 + 1.0D);
    }
}
