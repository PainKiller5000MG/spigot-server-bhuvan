package net.minecraft;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

public enum ChatFormatting implements StringRepresentable {

    BLACK("BLACK", '0', 0, 0), DARK_BLUE("DARK_BLUE", '1', 1, 170), DARK_GREEN("DARK_GREEN", '2', 2, 43520), DARK_AQUA("DARK_AQUA", '3', 3, 43690), DARK_RED("DARK_RED", '4', 4, 11141120), DARK_PURPLE("DARK_PURPLE", '5', 5, 11141290), GOLD("GOLD", '6', 6, 16755200), GRAY("GRAY", '7', 7, 11184810), DARK_GRAY("DARK_GRAY", '8', 8, 5592405), BLUE("BLUE", '9', 9, 5592575), GREEN("GREEN", 'a', 10, 5635925), AQUA("AQUA", 'b', 11, 5636095), RED("RED", 'c', 12, 16733525), LIGHT_PURPLE("LIGHT_PURPLE", 'd', 13, 16733695), YELLOW("YELLOW", 'e', 14, 16777045), WHITE("WHITE", 'f', 15, 16777215), OBFUSCATED("OBFUSCATED", 'k', true), BOLD("BOLD", 'l', true), STRIKETHROUGH("STRIKETHROUGH", 'm', true), UNDERLINE("UNDERLINE", 'n', true), ITALIC("ITALIC", 'o', true), RESET("RESET", 'r', -1, (Integer) null);

    public static final Codec<ChatFormatting> CODEC = StringRepresentable.<ChatFormatting>fromEnum(ChatFormatting::values);
    public static final Codec<ChatFormatting> COLOR_CODEC = ChatFormatting.CODEC.validate((chatformatting) -> {
        return chatformatting.isFormat() ? DataResult.error(() -> {
            return "Formatting was not a valid color: " + String.valueOf(chatformatting);
        }) : DataResult.success(chatformatting);
    });
    public static final char PREFIX_CODE = '\u00a7';
    private static final Map<String, ChatFormatting> FORMATTING_BY_NAME = (Map) Arrays.stream(values()).collect(Collectors.toMap((chatformatting) -> {
        return cleanName(chatformatting.name);
    }, (chatformatting) -> {
        return chatformatting;
    }));
    private static final Pattern STRIP_FORMATTING_PATTERN = Pattern.compile("(?i)\u00a7[0-9A-FK-OR]");
    private final String name;
    public final char code;
    private final boolean isFormat;
    private final String toString;
    private final int id;
    private final @Nullable Integer color;

    private static String cleanName(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private ChatFormatting(String name, @Nullable char code, int id, Integer color) {
        this(name, code, false, id, color);
    }

    private ChatFormatting(String name, char code, boolean isFormat) {
        this(name, code, isFormat, -1, (Integer) null);
    }

    private ChatFormatting(String name, char code, @Nullable boolean isFormat, int id, Integer color) {
        this.name = name;
        this.code = code;
        this.isFormat = isFormat;
        this.id = id;
        this.color = color;
        this.toString = "\u00a7" + String.valueOf(code);
    }

    public char getChar() {
        return this.code;
    }

    public int getId() {
        return this.id;
    }

    public boolean isFormat() {
        return this.isFormat;
    }

    public boolean isColor() {
        return !this.isFormat && this != ChatFormatting.RESET;
    }

    public @Nullable Integer getColor() {
        return this.color;
    }

    public String getName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public String toString() {
        return this.toString;
    }

    @Contract("!null->!null;_->_")
    public static @Nullable String stripFormatting(@Nullable String input) {
        return input == null ? null : ChatFormatting.STRIP_FORMATTING_PATTERN.matcher(input).replaceAll("");
    }

    public static @Nullable ChatFormatting getByName(@Nullable String name) {
        return name == null ? null : (ChatFormatting) ChatFormatting.FORMATTING_BY_NAME.get(cleanName(name));
    }

    public static @Nullable ChatFormatting getById(int id) {
        if (id < 0) {
            return ChatFormatting.RESET;
        } else {
            for (ChatFormatting chatformatting : values()) {
                if (chatformatting.getId() == id) {
                    return chatformatting;
                }
            }

            return null;
        }
    }

    public static @Nullable ChatFormatting getByCode(char code) {
        char c1 = Character.toLowerCase(code);

        for (ChatFormatting chatformatting : values()) {
            if (chatformatting.code == c1) {
                return chatformatting;
            }
        }

        return null;
    }

    public static Collection<String> getNames(boolean getColors, boolean getFormats) {
        List<String> list = Lists.newArrayList();

        for (ChatFormatting chatformatting : values()) {
            if ((!chatformatting.isColor() || getColors) && (!chatformatting.isFormat() || getFormats)) {
                list.add(chatformatting.getName());
            }
        }

        return list;
    }

    @Override
    public String getSerializedName() {
        return this.getName();
    }
}
