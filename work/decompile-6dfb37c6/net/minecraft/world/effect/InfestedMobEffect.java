package net.minecraft.world.effect;

import java.util.function.ToIntFunction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

class InfestedMobEffect extends MobEffect {

    private final float chanceToSpawn;
    private final ToIntFunction<RandomSource> spawnedCount;

    protected InfestedMobEffect(MobEffectCategory category, int color, float chanceToSpawn, ToIntFunction<RandomSource> spawnedCount) {
        super(category, color, ParticleTypes.INFESTED);
        this.chanceToSpawn = chanceToSpawn;
        this.spawnedCount = spawnedCount;
    }

    @Override
    public void onMobHurt(ServerLevel level, LivingEntity mob, int amplifier, DamageSource source, float damage) {
        if (mob.getRandom().nextFloat() <= this.chanceToSpawn) {
            int j = this.spawnedCount.applyAsInt(mob.getRandom());

            for (int k = 0; k < j; ++k) {
                this.spawnSilverfish(level, mob, mob.getX(), mob.getY() + (double) mob.getBbHeight() / 2.0D, mob.getZ());
            }
        }

    }

    private void spawnSilverfish(ServerLevel level, LivingEntity mob, double x, double y, double z) {
        Silverfish silverfish = EntityType.SILVERFISH.create(level, EntitySpawnReason.TRIGGERED);

        if (silverfish != null) {
            RandomSource randomsource = mob.getRandom();
            float f = ((float) Math.PI / 2F);
            float f1 = Mth.randomBetween(randomsource, (-(float) Math.PI / 2F), ((float) Math.PI / 2F));
            Vector3f vector3f = mob.getLookAngle().toVector3f().mul(0.3F).mul(1.0F, 1.5F, 1.0F).rotateY(f1);

            silverfish.snapTo(x, y, z, level.getRandom().nextFloat() * 360.0F, 0.0F);
            silverfish.setDeltaMovement(new Vec3(vector3f));
            level.addFreshEntity(silverfish);
            silverfish.playSound(SoundEvents.SILVERFISH_HURT);
        }
    }
}
