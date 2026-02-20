package net.minecraft.world.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.raid.Raid;

class BadOmenMobEffect extends MobEffect {

    protected BadOmenMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int remainingDuration, int amplification) {
        return true;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
        if (mob instanceof ServerPlayer serverplayer) {
            if (!serverplayer.isSpectator() && level.getDifficulty() != Difficulty.PEACEFUL && level.isVillage(serverplayer.blockPosition())) {
                Raid raid = level.getRaidAt(serverplayer.blockPosition());

                if (raid == null || raid.getRaidOmenLevel() < raid.getMaxRaidOmenLevel()) {
                    serverplayer.addEffect(new MobEffectInstance(MobEffects.RAID_OMEN, 600, amplification));
                    serverplayer.setRaidOmenPosition(serverplayer.blockPosition());
                    return false;
                }
            }
        }

        return true;
    }
}
