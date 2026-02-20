package net.minecraft.network.chat;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Lifecycle;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import org.jspecify.annotations.Nullable;

public final class TextColor {

    private static final String CUSTOM_COLOR_PREFIX = "#";
    public static final Codec<TextColor> CODEC = Codec.STRING.comapFlatMap(TextColor::parseColor, TextColor::serialize);
    private static final Map<ChatFormatting, TextColor> LEGACY_FORMAT_TO_COLOR = (Map) Stream.of(ChatFormatting.values()).filter(ChatFormatting::isColor).collect(ImmutableMap.toImmutableMap(Function.identity(), (chatformatting) -> {
        return new TextColor(chatformatting.getColor(), chatformatting.getName());
    }));
    private static final Map<String, TextColor> NAMED_COLORS = (Map) TextColor.LEGACY_FORMAT_TO_COLOR.values().stream().collect(ImmutableMap.toImmutableMap((textcolor) -> {
        return textcolor.name;
    }, Function.identity()));
    private final int value;
    public final @Nullable String name;

    private TextColor(int value, String name) {
        this.value = value & 16777215;
        this.name = name;
    }

    private TextColor(int value) {
        this.value = value & 16777215;
        this.name = null;
    }

    public int getValue() {
        return this.value;
    }

    public String serialize() {
        return this.name != null ? this.name : this.formatValue();
    }

    private String formatValue() {
        return String.format(Locale.ROOT, "#%06X", this.value);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            TextColor textcolor = (TextColor) o;

            return this.value == textcolor.value;
        } else {
            return false;
        }
    }

    public int hashCode() {
        return Objects.hash(new Object[]{this.value, this.name});
    }

    public String toString() {
        return this.serialize();
    }

    public static @Nullable TextColor fromLegacyFormat(ChatFormatting format) {
        return (TextColor) TextColor.LEGACY_FORMAT_TO_COLOR.get(format);
    }

    public static TextColor fromRgb(int rgb) {
        return new TextColor(rgb);
    }

    public static DataResult<TextColor> parseColor(String color) {
        if (color.startsWith("#")) {
            try {
                int i = Integer.parseInt(color.substring(1), 16);

                return i >= 0 && i <= 16777215 ? DataResult.success(fromRgb(i), Lifecycle.stable()) : DataResult.error(() -> {
                    return "Color value out of range: " + color;
                });
            } catch (NumberFormatException numberformatexception) {
                return DataResult.error(() -> {
                    return "Invalid color value: " + color;
                });
            }
        } else {
            TextColor textcolor = (TextColor) TextColor.NAMED_COLORS.get(color);

            return textcolor == null ? DataResult.error(() -> {
                return "Invalid color name: " + color;
            }) : DataResult.success(textcolor, Lifecycle.stable());
        }
    }
}
