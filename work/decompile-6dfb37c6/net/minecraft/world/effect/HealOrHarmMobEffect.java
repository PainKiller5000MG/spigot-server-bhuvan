package net.minecraft.world.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

class HealOrHarmMobEffect extends InstantenousMobEffect {

    private final boolean isHarm;

    public HealOrHarmMobEffect(MobEffectCategory category, int color, boolean isHarm) {
        super(category, color);
        this.isHarm = isHarm;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
        if (this.isHarm == mob.isInvertedHealAndHarm()) {
            mob.heal((float) Math.max(4 << amplification, 0));
        } else {
            mob.hurtServer(level, mob.damageSources().magic(), (float) (6 << amplification));
        }

        return true;
    }

    @Override
    public void applyInstantenousEffect(ServerLevel serverLevel, @Nullable Entity source, @Nullable Entity owner, LivingEntity mob, int amplification, double scale) {
        if (this.isHarm == mob.isInvertedHealAndHarm()) {
            int j = (int) (scale * (double) (4 << amplification) + 0.5D);

            mob.heal((float) j);
        } else {
            int k = (int) (scale * (double) (6 << amplification) + 0.5D);

            if (source == null) {
                mob.hurtServer(serverLevel, mob.damageSources().magic(), (float) k);
            } else {
                mob.hurtServer(serverLevel, mob.damageSources().indirectMagic(source, owner), (float) k);
            }
        }

    }
}
