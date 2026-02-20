package net.minecraft.world.effect;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class MobEffectUtil {

    public MobEffectUtil() {}

    public static Component formatDuration(MobEffectInstance instance, float scale, float tickrate) {
        if (instance.isInfiniteDuration()) {
            return Component.translatable("effect.duration.infinite");
        } else {
            int i = Mth.floor((float) instance.getDuration() * scale);

            return Component.literal(StringUtil.formatTickDuration(i, tickrate));
        }
    }

    public static boolean hasDigSpeed(LivingEntity mob) {
        return mob.hasEffect(MobEffects.HASTE) || mob.hasEffect(MobEffects.CONDUIT_POWER);
    }

    public static int getDigSpeedAmplification(LivingEntity mob) {
        int i = 0;
        int j = 0;

        if (mob.hasEffect(MobEffects.HASTE)) {
            i = mob.getEffect(MobEffects.HASTE).getAmplifier();
        }

        if (mob.hasEffect(MobEffects.CONDUIT_POWER)) {
            j = mob.getEffect(MobEffects.CONDUIT_POWER).getAmplifier();
        }

        return Math.max(i, j);
    }

    public static boolean hasWaterBreathing(LivingEntity mob) {
        return mob.hasEffect(MobEffects.WATER_BREATHING) || mob.hasEffect(MobEffects.CONDUIT_POWER) || mob.hasEffect(MobEffects.BREATH_OF_THE_NAUTILUS);
    }

    public static boolean shouldEffectsRefillAirsupply(LivingEntity mob) {
        return !mob.hasEffect(MobEffects.BREATH_OF_THE_NAUTILUS) || mob.hasEffect(MobEffects.WATER_BREATHING) || mob.hasEffect(MobEffects.CONDUIT_POWER);
    }

    public static List<ServerPlayer> addEffectToPlayersAround(ServerLevel level, @Nullable Entity source, Vec3 position, double radius, MobEffectInstance effectInstance, int displayEffectLimit) {
        Holder<MobEffect> holder = effectInstance.getEffect();
        List<ServerPlayer> list = level.getPlayers((serverplayer) -> {
            return serverplayer.gameMode.isSurvival() && (source == null || !source.isAlliedTo((Entity) serverplayer)) && position.closerThan(serverplayer.position(), radius) && (!serverplayer.hasEffect(holder) || serverplayer.getEffect(holder).getAmplifier() < effectInstance.getAmplifier() || serverplayer.getEffect(holder).endsWithin(displayEffectLimit - 1));
        });

        list.forEach((serverplayer) -> {
            serverplayer.addEffect(new MobEffectInstance(effectInstance), source);
        });
        return list;
    }
}
