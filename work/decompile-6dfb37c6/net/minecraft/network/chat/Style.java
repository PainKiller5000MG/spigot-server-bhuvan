package net.minecraft.network.chat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import org.jspecify.annotations.Nullable;

public final class Style {

    public static final Style EMPTY = new Style((TextColor) null, (Integer) null, (Boolean) null, (Boolean) null, (Boolean) null, (Boolean) null, (Boolean) null, (ClickEvent) null, (HoverEvent) null, (String) null, (FontDescription) null);
    public static final int NO_SHADOW = 0;
    private final @Nullable TextColor color;
    private final @Nullable Integer shadowColor;
    private final @Nullable Boolean bold;
    private final @Nullable Boolean italic;
    private final @Nullable Boolean underlined;
    private final @Nullable Boolean strikethrough;
    private final @Nullable Boolean obfuscated;
    private final @Nullable ClickEvent clickEvent;
    private final @Nullable HoverEvent hoverEvent;
    private final @Nullable String insertion;
    private final @Nullable FontDescription font;

    private static Style create(Optional<TextColor> color, Optional<Integer> shadowColor, Optional<Boolean> bold, Optional<Boolean> italic, Optional<Boolean> underlined, Optional<Boolean> strikethrough, Optional<Boolean> obfuscated, Optional<ClickEvent> clickEvent, Optional<HoverEvent> hoverEvent, Optional<String> insertion, Optional<FontDescription> font) {
        Style style = new Style((TextColor) color.orElse((Object) null), (Integer) shadowColor.orElse((Object) null), (Boolean) bold.orElse((Object) null), (Boolean) italic.orElse((Object) null), (Boolean) underlined.orElse((Object) null), (Boolean) strikethrough.orElse((Object) null), (Boolean) obfuscated.orElse((Object) null), (ClickEvent) clickEvent.orElse((Object) null), (HoverEvent) hoverEvent.orElse((Object) null), (String) insertion.orElse((Object) null), (FontDescription) font.orElse((Object) null));

        return style.equals(Style.EMPTY) ? Style.EMPTY : style;
    }

    private Style(@Nullable TextColor color, @Nullable Integer shadowColor, @Nullable Boolean bold, @Nullable Boolean italic, @Nullable Boolean underlined, @Nullable Boolean strikethrough, @Nullable Boolean obfuscated, @Nullable ClickEvent clickEvent, @Nullable HoverEvent hoverEvent, @Nullable String insertion, @Nullable FontDescription font) {
        this.color = color;
        this.shadowColor = shadowColor;
        this.bold = bold;
        this.italic = italic;
        this.underlined = underlined;
        this.strikethrough = strikethrough;
        this.obfuscated = obfuscated;
        this.clickEvent = clickEvent;
        this.hoverEvent = hoverEvent;
        this.insertion = insertion;
        this.font = font;
    }

    public @Nullable TextColor getColor() {
        return this.color;
    }

    public @Nullable Integer getShadowColor() {
        return this.shadowColor;
    }

    public boolean isBold() {
        return this.bold == Boolean.TRUE;
    }

    public boolean isItalic() {
        return this.italic == Boolean.TRUE;
    }

    public boolean isStrikethrough() {
        return this.strikethrough == Boolean.TRUE;
    }

    public boolean isUnderlined() {
        return this.underlined == Boolean.TRUE;
    }

    public boolean isObfuscated() {
        return this.obfuscated == Boolean.TRUE;
    }

    public boolean isEmpty() {
        return this == Style.EMPTY;
    }

    public @Nullable ClickEvent getClickEvent() {
        return this.clickEvent;
    }

    public @Nullable HoverEvent getHoverEvent() {
        return this.hoverEvent;
    }

    public @Nullable String getInsertion() {
        return this.insertion;
    }

    public FontDescription getFont() {
        return (FontDescription) (this.font != null ? this.font : FontDescription.DEFAULT);
    }

    private static <T> Style checkEmptyAfterChange(Style newStyle, @Nullable T previous, @Nullable T next) {
        return previous != null && next == null && newStyle.equals(Style.EMPTY) ? Style.EMPTY : newStyle;
    }

    public Style withColor(@Nullable TextColor color) {
        return Objects.equals(this.color, color) ? this : checkEmptyAfterChange(new Style(color, this.shadowColor, this.bold, this.italic, this.underlined, this.strikethrough, this.obfuscated, this.clickEvent, this.hoverEvent, this.insertion, this.font), this.color, color);
    }

    public Style withColor(@Nullable ChatFormatting color) {
        return this.withColor(color != null ? TextColor.fromLegacyFormat(color) : null);
    }

    public Style withColor(int color) {
        return this.withColor(TextColor.fromRgb(color));
    }

    public Style withShadowColor(int shadowColor) {
        return Objects.equals(this.shadowColor, shadowColor) ? this : checkEmptyAfterChange(new Style(this.color, shadowColor, this.bold, this.italic, this.underlined, this.strikethrough, this.obfuscated, this.clickEvent, this.hoverEvent, this.insertion, this.font), this.shadowColor, shadowColor);
    }

    public Style withoutShadow() {
        return this.withShadowColor(0);
    }

    public Style withBold(@Nullable Boolean bold) {
        return Objects.equals(this.bold, bold) ? this : checkEmptyAfterChange(new Style(this.color, this.shadowColor, bold, this.italic, this.underlined, this.strikethrough, this.obfuscated, this.clickEvent, this.hoverEvent, this.insertion, this.font), this.bold, bold);
    }

    public Style withItalic(@Nullable Boolean italic) {
        return Objects.equals(this.italic, italic) ? this : checkEmptyAfterChange(new Style(this.color, this.shadowColor, this.bold, italic, this.underlined, this.strikethrough, this.obfuscated, this.clickEvent, this.hoverEvent, this.insertion, this.font), this.italic, italic);
    }

    public Style withUnderlined(@Nullable Boolean underlined) {
        return Objects.equals(this.underlined, underlined) ? this : checkEmptyAfterChange(new Style(this.color, this.shadowColor, this.bold, this.italic, underlined, this.strikethrough, this.obfuscated, this.clickEvent, this.hoverEvent, this.insertion, this.font), this.underlined, underlined);
    }

    public Style withStrikethrough(@Nullable Boolean strikethrough) {
        return Objects.equals(this.strikethrough, strikethrough) ? this : checkEmptyAfterChange(new Style(this.color, this.shadowColor, this.bold, this.italic, this.underlined, strikethrough, this.obfuscated, this.clickEvent, this.hoverEvent, this.insertion, this.font), this.strikethrough, strikethrough);
    }

    public Style withObfuscated(@Nullable Boolean obfuscated) {
        return Objects.equals(this.obfuscated, obfuscated) ? this : checkEmptyAfterChange(new Style(this.color, this.shadowColor, this.bold, this.italic, this.underlined, this.strikethrough, obfuscated, this.clickEvent, this.hoverEvent, this.insertion, this.font), this.obfuscated, obfuscated);
    }

    public Style withClickEvent(@Nullable ClickEvent clickEvent) {
        return Objects.equals(this.clickEvent, clickEvent) ? this : checkEmptyAfterChange(new Style(this.color, this.shadowColor, this.bold, this.italic, this.underlined, this.strikethrough, this.obfuscated, clickEvent, this.hoverEvent, this.insertion, this.font), this.clickEvent, clickEvent);
    }

    public Style withHoverEvent(@Nullable HoverEvent hoverEvent) {
        return Objects.equals(this.hoverEvent, hoverEvent) ? this : checkEmptyAfterChange(new Style(this.color, this.shadowColor, this.bold, this.italic, this.underlined, this.strikethrough, this.obfuscated, this.clickEvent, hoverEvent, this.insertion, this.font), this.hoverEvent, hoverEvent);
    }

    public Style withInsertion(@Nullable String insertion) {
        return Objects.equals(this.insertion, insertion) ? this : checkEmptyAfterChange(new Style(this.color, this.shadowColor, this.bold, this.italic, this.underlined, this.strikethrough, this.obfuscated, this.clickEvent, this.hoverEvent, insertion, this.font), this.insertion, insertion);
    }

    public Style withFont(@Nullable FontDescription font) {
        return Objects.equals(this.font, font) ? this : checkEmptyAfterChange(new Style(this.color, this.shadowColor, this.bold, this.italic, this.underlined, this.strikethrough, this.obfuscated, this.clickEvent, this.hoverEvent, this.insertion, font), this.font, font);
    }

    public Style applyFormat(ChatFormatting format) {
        TextColor textcolor = this.color;
        Boolean obool = this.bold;
        Boolean obool1 = this.italic;
        Boolean obool2 = this.strikethrough;
        Boolean obool3 = this.underlined;
        Boolean obool4 = this.obfuscated;

        switch (format) {
            case OBFUSCATED:
                obool4 = true;
                break;
            case BOLD:
                obool = true;
                break;
            case STRIKETHROUGH:
                obool2 = true;
                break;
            case UNDERLINE:
                obool3 = true;
                break;
            case ITALIC:
                obool1 = true;
                break;
            case RESET:
                return Style.EMPTY;
            default:
                textcolor = TextColor.fromLegacyFormat(format);
        }

        return new Style(textcolor, this.shadowColor, obool, obool1, obool3, obool2, obool4, this.clickEvent, this.hoverEvent, this.insertion, this.font);
    }

    public Style applyLegacyFormat(ChatFormatting format) {
        TextColor textcolor = this.color;
        Boolean obool = this.bold;
        Boolean obool1 = this.italic;
        Boolean obool2 = this.strikethrough;
        Boolean obool3 = this.underlined;
        Boolean obool4 = this.obfuscated;

        switch (format) {
            case OBFUSCATED:
                obool4 = true;
                break;
            case BOLD:
                obool = true;
                break;
            case STRIKETHROUGH:
                obool2 = true;
                break;
            case UNDERLINE:
                obool3 = true;
                break;
            case ITALIC:
                obool1 = true;
                break;
            case RESET:
                return Style.EMPTY;
            default:
                obool4 = false;
                obool = false;
                obool2 = false;
                obool3 = false;
                obool1 = false;
                textcolor = TextColor.fromLegacyFormat(format);
        }

        return new Style(textcolor, this.shadowColor, obool, obool1, obool3, obool2, obool4, this.clickEvent, this.hoverEvent, this.insertion, this.font);
    }

    public Style applyFormats(ChatFormatting... formats) {
        TextColor textcolor = this.color;
        Boolean obool = this.bold;
        Boolean obool1 = this.italic;
        Boolean obool2 = this.strikethrough;
        Boolean obool3 = this.underlined;
        Boolean obool4 = this.obfuscated;

        for (ChatFormatting chatformatting : formats) {
            switch (chatformatting) {
                case OBFUSCATED:
                    obool4 = true;
                    break;
                case BOLD:
                    obool = true;
                    break;
                case STRIKETHROUGH:
                    obool2 = true;
                    break;
                case UNDERLINE:
                    obool3 = true;
                    break;
                case ITALIC:
                    obool1 = true;
                    break;
                case RESET:
                    return Style.EMPTY;
                default:
                    textcolor = TextColor.fromLegacyFormat(chatformatting);
            }
        }

        return new Style(textcolor, this.shadowColor, obool, obool1, obool3, obool2, obool4, this.clickEvent, this.hoverEvent, this.insertion, this.font);
    }

    public Style applyTo(Style other) {
        return this == Style.EMPTY ? other : (other == Style.EMPTY ? this : new Style(this.color != null ? this.color : other.color, this.shadowColor != null ? this.shadowColor : other.shadowColor, this.bold != null ? this.bold : other.bold, this.italic != null ? this.italic : other.italic, this.underlined != null ? this.underlined : other.underlined, this.strikethrough != null ? this.strikethrough : other.strikethrough, this.obfuscated != null ? this.obfuscated : other.obfuscated, this.clickEvent != null ? this.clickEvent : other.clickEvent, this.hoverEvent != null ? this.hoverEvent : other.hoverEvent, this.insertion != null ? this.insertion : other.insertion, this.font != null ? this.font : other.font));
    }

    public String toString() {
        final StringBuilder stringbuilder = new StringBuilder("{");

        class 1Collector {

            private boolean isNotFirst;

            _Collector/* $FF was: 1Collector*/() {
}

            private void prependSeparator() {
                if (this.isNotFirst) {
                    stringbuilder.append(',');
                }

                this.isNotFirst = true;
            }

            private void addFlagString(String name, @Nullable Boolean value) {
                if (value != null) {
                    this.prependSeparator();
                    if (!value) {
                        stringbuilder.append('!');
                    }

                    stringbuilder.append(name);
                }

            }

            private void addValueString(String name, @Nullable Object value) {
                if (value != null) {
                    this.prependSeparator();
                    stringbuilder.append(name);
                    stringbuilder.append('=');
                    stringbuilder.append(value);
                }

            }
        }

        1Collector 1collector = new 1Collector();

        1collector.addValueString("color", this.color);
        1collector.addValueString("shadowColor", this.shadowColor);
        1collector.addFlagString("bold", this.bold);
        1collector.addFlagString("italic", this.italic);
        1collector.addFlagString("underlined", this.underlined);
        1collector.addFlagString("strikethrough", this.strikethrough);
        1collector.addFlagString("obfuscated", this.obfuscated);
        1collector.addValueString("clickEvent", this.clickEvent);
        1collector.addValueString("hoverEvent", this.hoverEvent);
        1collector.addValueString("insertion", this.insertion);
        1collector.addValueString("font", this.font);
        stringbuilder.append("}");
        return stringbuilder.toString();
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof Style)) {
            return false;
        } else {
            Style style = (Style) o;

            return this.bold == style.bold && Objects.equals(this.getColor(), style.getColor()) && Objects.equals(this.getShadowColor(), style.getShadowColor()) && this.italic == style.italic && this.obfuscated == style.obfuscated && this.strikethrough == style.strikethrough && this.underlined == style.underlined && Objects.equals(this.clickEvent, style.clickEvent) && Objects.equals(this.hoverEvent, style.hoverEvent) && Objects.equals(this.insertion, style.insertion) && Objects.equals(this.font, style.font);
        }
    }

    public int hashCode() {
        return Objects.hash(new Object[]{this.color, this.shadowColor, this.bold, this.italic, this.underlined, this.strikethrough, this.obfuscated, this.clickEvent, this.hoverEvent, this.insertion});
    }

    public static class Serializer {

        public static final MapCodec<Style> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(TextColor.CODEC.optionalFieldOf("color").forGetter((style) -> {
                return Optional.ofNullable(style.color);
            }), ExtraCodecs.ARGB_COLOR_CODEC.optionalFieldOf("shadow_color").forGetter((style) -> {
                return Optional.ofNullable(style.shadowColor);
            }), Codec.BOOL.optionalFieldOf("bold").forGetter((style) -> {
                return Optional.ofNullable(style.bold);
            }), Codec.BOOL.optionalFieldOf("italic").forGetter((style) -> {
                return Optional.ofNullable(style.italic);
            }), Codec.BOOL.optionalFieldOf("underlined").forGetter((style) -> {
                return Optional.ofNullable(style.underlined);
            }), Codec.BOOL.optionalFieldOf("strikethrough").forGetter((style) -> {
                return Optional.ofNullable(style.strikethrough);
            }), Codec.BOOL.optionalFieldOf("obfuscated").forGetter((style) -> {
                return Optional.ofNullable(style.obfuscated);
            }), ClickEvent.CODEC.optionalFieldOf("click_event").forGetter((style) -> {
                return Optional.ofNullable(style.clickEvent);
            }), HoverEvent.CODEC.optionalFieldOf("hover_event").forGetter((style) -> {
                return Optional.ofNullable(style.hoverEvent);
            }), Codec.STRING.optionalFieldOf("insertion").forGetter((style) -> {
                return Optional.ofNullable(style.insertion);
            }), FontDescription.CODEC.optionalFieldOf("font").forGetter((style) -> {
                return Optional.ofNullable(style.font);
            })).apply(instance, Style::create);
        });
        public static final Codec<Style> CODEC = Style.Serializer.MAP_CODEC.codec();
        public static final StreamCodec<RegistryFriendlyByteBuf, Style> TRUSTED_STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistriesTrusted(Style.Serializer.CODEC);

        public Serializer() {}
    }
}
