package net.minecraft.world.item.consume_effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public record TeleportRandomlyConsumeEffect(float diameter) implements ConsumeEffect {

    private static final float DEFAULT_DIAMETER = 16.0F;
    public static final MapCodec<TeleportRandomlyConsumeEffect> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(ExtraCodecs.POSITIVE_FLOAT.optionalFieldOf("diameter", 16.0F).forGetter(TeleportRandomlyConsumeEffect::diameter)).apply(instance, TeleportRandomlyConsumeEffect::new);
    });
    public static final StreamCodec<RegistryFriendlyByteBuf, TeleportRandomlyConsumeEffect> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.FLOAT, TeleportRandomlyConsumeEffect::diameter, TeleportRandomlyConsumeEffect::new);

    public TeleportRandomlyConsumeEffect() {
        this(16.0F);
    }

    @Override
    public ConsumeEffect.Type<TeleportRandomlyConsumeEffect> getType() {
        return ConsumeEffect.Type.TELEPORT_RANDOMLY;
    }

    @Override
    public boolean apply(Level level, ItemStack stack, LivingEntity user) {
        boolean flag = false;

        for (int i = 0; i < 16; ++i) {
            double d0 = user.getX() + (user.getRandom().nextDouble() - 0.5D) * (double) this.diameter;
            double d1 = Mth.clamp(user.getY() + (user.getRandom().nextDouble() - 0.5D) * (double) this.diameter, (double) level.getMinY(), (double) (level.getMinY() + ((ServerLevel) level).getLogicalHeight() - 1));
            double d2 = user.getZ() + (user.getRandom().nextDouble() - 0.5D) * (double) this.diameter;

            if (user.isPassenger()) {
                user.stopRiding();
            }

            Vec3 vec3 = user.position();

            if (user.randomTeleport(d0, d1, d2, true)) {
                level.gameEvent(GameEvent.TELEPORT, vec3, GameEvent.Context.of((Entity) user));
                SoundSource soundsource;
                SoundEvent soundevent;

                if (user instanceof Fox) {
                    soundevent = SoundEvents.FOX_TELEPORT;
                    soundsource = SoundSource.NEUTRAL;
                } else {
                    soundevent = SoundEvents.CHORUS_FRUIT_TELEPORT;
                    soundsource = SoundSource.PLAYERS;
                }

                level.playSound((Entity) null, user.getX(), user.getY(), user.getZ(), soundevent, soundsource);
                user.resetFallDistance();
                flag = true;
                break;
            }
        }

        if (flag && user instanceof Player player) {
            player.resetCurrentImpulseContext();
        }

        return flag;
    }
}
