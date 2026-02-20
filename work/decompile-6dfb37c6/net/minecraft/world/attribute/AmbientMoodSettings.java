package net.minecraft.world.attribute;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public record AmbientMoodSettings(Holder<SoundEvent> soundEvent, int tickDelay, int blockSearchExtent, double soundPositionOffset) {

    public static final Codec<AmbientMoodSettings> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(SoundEvent.CODEC.fieldOf("sound").forGetter((ambientmoodsettings) -> {
            return ambientmoodsettings.soundEvent;
        }), Codec.INT.fieldOf("tick_delay").forGetter((ambientmoodsettings) -> {
            return ambientmoodsettings.tickDelay;
        }), Codec.INT.fieldOf("block_search_extent").forGetter((ambientmoodsettings) -> {
            return ambientmoodsettings.blockSearchExtent;
        }), Codec.DOUBLE.fieldOf("offset").forGetter((ambientmoodsettings) -> {
            return ambientmoodsettings.soundPositionOffset;
        })).apply(instance, AmbientMoodSettings::new);
    });
    public static final AmbientMoodSettings LEGACY_CAVE_SETTINGS = new AmbientMoodSettings(SoundEvents.AMBIENT_CAVE, 6000, 8, 2.0D);
}
