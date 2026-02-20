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

public record TextInput(int width, Component label, boolean labelVisible, String initial, int maxLength, Optional<TextInput.MultilineOptions> multiline) implements InputControl {

    public static final MapCodec<TextInput> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Dialog.WIDTH_CODEC.optionalFieldOf("width", 200).forGetter(TextInput::width), ComponentSerialization.CODEC.fieldOf("label").forGetter(TextInput::label), Codec.BOOL.optionalFieldOf("label_visible", true).forGetter(TextInput::labelVisible), Codec.STRING.optionalFieldOf("initial", "").forGetter(TextInput::initial), ExtraCodecs.POSITIVE_INT.optionalFieldOf("max_length", 32).forGetter(TextInput::maxLength), TextInput.MultilineOptions.CODEC.optionalFieldOf("multiline").forGetter(TextInput::multiline)).apply(instance, TextInput::new);
    }).validate((textinput) -> {
        return textinput.initial.length() > textinput.maxLength() ? DataResult.error(() -> {
            return "Default text length exceeds allowed size";
        }) : DataResult.success(textinput);
    });

    @Override
    public MapCodec<TextInput> mapCodec() {
        return TextInput.MAP_CODEC;
    }

    public static record MultilineOptions(Optional<Integer> maxLines, Optional<Integer> height) {

        public static final int MAX_HEIGHT = 512;
        public static final Codec<TextInput.MultilineOptions> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(ExtraCodecs.POSITIVE_INT.optionalFieldOf("max_lines").forGetter(TextInput.MultilineOptions::maxLines), ExtraCodecs.intRange(1, 512).optionalFieldOf("height").forGetter(TextInput.MultilineOptions::height)).apply(instance, TextInput.MultilineOptions::new);
        });
    }
}
