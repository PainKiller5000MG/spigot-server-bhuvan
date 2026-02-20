package net.minecraft.world.timeline;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.function.LongSupplier;
import net.minecraft.util.KeyframeTrack;
import net.minecraft.util.Util;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.modifier.AttributeModifier;

public record AttributeTrack<Value, Argument>(AttributeModifier<Value, Argument> modifier, KeyframeTrack<Argument> argumentTrack) {

    public static <Value> Codec<AttributeTrack<Value, ?>> createCodec(EnvironmentAttribute<Value> attribute) {
        MapCodec<AttributeModifier<Value, ?>> mapcodec = attribute.type().modifierCodec().optionalFieldOf("modifier", AttributeModifier.override());

        return mapcodec.dispatch(AttributeTrack::modifier, Util.memoize((attributemodifier) -> {
            return createCodecWithModifier(attribute, attributemodifier);
        }));
    }

    private static <Value, Argument> MapCodec<AttributeTrack<Value, Argument>> createCodecWithModifier(EnvironmentAttribute<Value> attribute, AttributeModifier<Value, Argument> modifier) {
        return KeyframeTrack.mapCodec(modifier.argumentCodec(attribute)).xmap((keyframetrack) -> {
            return new AttributeTrack(modifier, keyframetrack);
        }, AttributeTrack::argumentTrack);
    }

    public AttributeTrackSampler<Value, Argument> bakeSampler(EnvironmentAttribute<Value> attribute, Optional<Integer> periodTicks, LongSupplier dayTimeGetter) {
        return new AttributeTrackSampler<Value, Argument>(periodTicks, this.modifier, this.argumentTrack, this.modifier.argumentKeyframeLerp(attribute), dayTimeGetter);
    }

    public static DataResult<AttributeTrack<?, ?>> validatePeriod(AttributeTrack<?, ?> track, int periodTicks) {
        return KeyframeTrack.validatePeriod(track.argumentTrack(), periodTicks).map((keyframetrack) -> {
            return track;
        });
    }
}
