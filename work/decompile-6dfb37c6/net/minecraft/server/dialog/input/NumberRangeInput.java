package net.minecraft.server.dialog.input;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;

public record NumberRangeInput(int width, Component label, String labelFormat, NumberRangeInput.RangeInfo rangeInfo) implements InputControl {

    public static final MapCodec<NumberRangeInput> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Dialog.WIDTH_CODEC.optionalFieldOf("width", 200).forGetter(NumberRangeInput::width), ComponentSerialization.CODEC.fieldOf("label").forGetter(NumberRangeInput::label), Codec.STRING.optionalFieldOf("label_format", "options.generic_value").forGetter(NumberRangeInput::labelFormat), NumberRangeInput.RangeInfo.MAP_CODEC.forGetter(NumberRangeInput::rangeInfo)).apply(instance, NumberRangeInput::new);
    });

    @Override
    public MapCodec<NumberRangeInput> mapCodec() {
        return NumberRangeInput.MAP_CODEC;
    }

    public Component computeLabel(String value) {
        return Component.translatable(this.labelFormat, this.label, value);
    }

    public static record RangeInfo(float start, float end, Optional<Float> initial, Optional<Float> step) {

        public static final MapCodec<NumberRangeInput.RangeInfo> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Codec.FLOAT.fieldOf("start").forGetter(NumberRangeInput.RangeInfo::start), Codec.FLOAT.fieldOf("end").forGetter(NumberRangeInput.RangeInfo::end), Codec.FLOAT.optionalFieldOf("initial").forGetter(NumberRangeInput.RangeInfo::initial), ExtraCodecs.POSITIVE_FLOAT.optionalFieldOf("step").forGetter(NumberRangeInput.RangeInfo::step)).apply(instance, NumberRangeInput.RangeInfo::new);
        }).validate((numberrangeinput_rangeinfo) -> {
            if (numberrangeinput_rangeinfo.initial.isPresent()) {
                double d0 = (double) (Float) numberrangeinput_rangeinfo.initial.get();
                double d1 = (double) Math.min(numberrangeinput_rangeinfo.start, numberrangeinput_rangeinfo.end);
                double d2 = (double) Math.max(numberrangeinput_rangeinfo.start, numberrangeinput_rangeinfo.end);

                if (d0 < d1 || d0 > d2) {
                    return DataResult.error(() -> {
                        return "Initial value " + d0 + " is outside of range [" + d1 + ", " + d2 + "]";
                    });
                }
            }

            return DataResult.success(numberrangeinput_rangeinfo);
        });

        public float computeScaledValue(float sliderValue) {
            float f1 = Mth.lerp(sliderValue, this.start, this.end);

            if (this.step.isEmpty()) {
                return f1;
            } else {
                float f2 = (Float) this.step.get();
                float f3 = this.initialScaledValue();
                float f4 = f1 - f3;
                int i = Math.round(f4 / f2);
                float f5 = f3 + (float) i * f2;

                if (!this.isOutOfRange(f5)) {
                    return f5;
                } else {
                    int j = i - Mth.sign((double) i);

                    return f3 + (float) j * f2;
                }
            }
        }

        private boolean isOutOfRange(float scaledValue) {
            float f1 = this.scaledValueToSlider(scaledValue);

            return (double) f1 < 0.0D || (double) f1 > 1.0D;
        }

        private float initialScaledValue() {
            return this.initial.isPresent() ? (Float) this.initial.get() : (this.start + this.end) / 2.0F;
        }

        public float initialSliderValue() {
            float f = this.initialScaledValue();

            return this.scaledValueToSlider(f);
        }

        private float scaledValueToSlider(float value) {
            return this.start == this.end ? 0.5F : Mth.inverseLerp(value, this.start, this.end);
        }
    }
}
