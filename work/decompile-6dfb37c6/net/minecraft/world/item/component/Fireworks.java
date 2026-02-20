package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;

public record Fireworks(int flightDuration, List<FireworkExplosion> explosions) implements TooltipProvider {

    public static final int MAX_EXPLOSIONS = 256;
    public static final Codec<Fireworks> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(ExtraCodecs.UNSIGNED_BYTE.optionalFieldOf("flight_duration", 0).forGetter(Fireworks::flightDuration), FireworkExplosion.CODEC.sizeLimitedListOf(256).optionalFieldOf("explosions", List.of()).forGetter(Fireworks::explosions)).apply(instance, Fireworks::new);
    });
    public static final StreamCodec<ByteBuf, Fireworks> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Fireworks::flightDuration, FireworkExplosion.STREAM_CODEC.apply(ByteBufCodecs.list(256)), Fireworks::explosions, Fireworks::new);

    public Fireworks {
        if (explosions.size() > 256) {
            throw new IllegalArgumentException("Got " + explosions.size() + " explosions, but maximum is 256");
        }
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
        if (this.flightDuration > 0) {
            consumer.accept(Component.translatable("item.minecraft.firework_rocket.flight").append(CommonComponents.SPACE).append(String.valueOf(this.flightDuration)).withStyle(ChatFormatting.GRAY));
        }

        FireworkExplosion fireworkexplosion = null;
        int i = 0;

        for (FireworkExplosion fireworkexplosion1 : this.explosions) {
            if (fireworkexplosion == null) {
                fireworkexplosion = fireworkexplosion1;
                i = 1;
            } else if (fireworkexplosion.equals(fireworkexplosion1)) {
                ++i;
            } else {
                addExplosionTooltip(consumer, fireworkexplosion, i);
                fireworkexplosion = fireworkexplosion1;
                i = 1;
            }
        }

        if (fireworkexplosion != null) {
            addExplosionTooltip(consumer, fireworkexplosion, i);
        }

    }

    private static void addExplosionTooltip(Consumer<Component> consumer, FireworkExplosion explosion, int count) {
        Component component = explosion.shape().getName();

        if (count == 1) {
            consumer.accept(Component.translatable("item.minecraft.firework_rocket.single_star", component).withStyle(ChatFormatting.GRAY));
        } else {
            consumer.accept(Component.translatable("item.minecraft.firework_rocket.multiple_stars", count, component).withStyle(ChatFormatting.GRAY));
        }

        explosion.addAdditionalTooltip((component1) -> {
            consumer.accept(Component.literal("  ").append(component1));
        });
    }
}
