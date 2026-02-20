package net.minecraft.world.timeline;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.KeyframeTrack;
import net.minecraft.util.Util;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.modifier.AttributeModifier;
import net.minecraft.world.level.Level;

public class Timeline {

    public static final Codec<Holder<Timeline>> CODEC = RegistryFixedCodec.<Holder<Timeline>>create(Registries.TIMELINE);
    private static final Codec<Map<EnvironmentAttribute<?>, AttributeTrack<?, ?>>> TRACKS_CODEC = Codec.dispatchedMap(EnvironmentAttributes.CODEC, Util.memoize(AttributeTrack::createCodec));
    public static final Codec<Timeline> DIRECT_CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(ExtraCodecs.POSITIVE_INT.optionalFieldOf("period_ticks").forGetter((timeline) -> {
            return timeline.periodTicks;
        }), Timeline.TRACKS_CODEC.optionalFieldOf("tracks", Map.of()).forGetter((timeline) -> {
            return timeline.tracks;
        })).apply(instance, Timeline::new);
    }).validate(Timeline::validateInternal);
    public static final Codec<Timeline> NETWORK_CODEC = Timeline.DIRECT_CODEC.xmap(Timeline::filterSyncableTracks, Timeline::filterSyncableTracks);
    private final Optional<Integer> periodTicks;
    private final Map<EnvironmentAttribute<?>, AttributeTrack<?, ?>> tracks;

    private static Timeline filterSyncableTracks(Timeline timeline) {
        Map<EnvironmentAttribute<?>, AttributeTrack<?, ?>> map = Map.copyOf(Maps.filterKeys(timeline.tracks, EnvironmentAttribute::isSyncable));

        return new Timeline(timeline.periodTicks, map);
    }

    private Timeline(Optional<Integer> periodTicks, Map<EnvironmentAttribute<?>, AttributeTrack<?, ?>> tracks) {
        this.periodTicks = periodTicks;
        this.tracks = tracks;
    }

    private static DataResult<Timeline> validateInternal(Timeline timeline) {
        if (timeline.periodTicks.isEmpty()) {
            return DataResult.success(timeline);
        } else {
            int i = (Integer) timeline.periodTicks.get();
            DataResult<Timeline> dataresult = DataResult.success(timeline);

            for (AttributeTrack<?, ?> attributetrack : timeline.tracks.values()) {
                dataresult = dataresult.apply2stable((timeline1, attributetrack1) -> {
                    return timeline1;
                }, AttributeTrack.validatePeriod(attributetrack, i));
            }

            return dataresult;
        }
    }

    public static Timeline.Builder builder() {
        return new Timeline.Builder();
    }

    public long getCurrentTicks(Level level) {
        long i = this.getTotalTicks(level);

        return this.periodTicks.isEmpty() ? i : i % (long) (Integer) this.periodTicks.get();
    }

    public long getTotalTicks(Level level) {
        return level.getDayTime();
    }

    public Optional<Integer> periodTicks() {
        return this.periodTicks;
    }

    public Set<EnvironmentAttribute<?>> attributes() {
        return this.tracks.keySet();
    }

    public <Value> AttributeTrackSampler<Value, ?> createTrackSampler(EnvironmentAttribute<Value> attribute, LongSupplier dayTimeGetter) {
        AttributeTrack<Value, ?> attributetrack = (AttributeTrack) this.tracks.get(attribute);

        if (attributetrack == null) {
            throw new IllegalStateException("Timeline has no track for " + String.valueOf(attribute));
        } else {
            return attributetrack.bakeSampler(attribute, this.periodTicks, dayTimeGetter);
        }
    }

    public static class Builder {

        private Optional<Integer> periodTicks = Optional.empty();
        private final ImmutableMap.Builder<EnvironmentAttribute<?>, AttributeTrack<?, ?>> tracks = ImmutableMap.builder();

        private Builder() {}

        public Timeline.Builder setPeriodTicks(int periodTicks) {
            this.periodTicks = Optional.of(periodTicks);
            return this;
        }

        public <Value, Argument> Timeline.Builder addModifierTrack(EnvironmentAttribute<Value> attribute, AttributeModifier<Value, Argument> modifier, Consumer<KeyframeTrack.Builder<Argument>> builder) {
            attribute.type().checkAllowedModifier(modifier);
            KeyframeTrack.Builder<Argument> keyframetrack_builder = new KeyframeTrack.Builder<Argument>();

            builder.accept(keyframetrack_builder);
            this.tracks.put(attribute, new AttributeTrack(modifier, keyframetrack_builder.build()));
            return this;
        }

        public <Value> Timeline.Builder addTrack(EnvironmentAttribute<Value> attribute, Consumer<KeyframeTrack.Builder<Value>> builder) {
            return this.addModifierTrack(attribute, AttributeModifier.override(), builder);
        }

        public Timeline build() {
            return new Timeline(this.periodTicks, this.tracks.build());
        }
    }
}
