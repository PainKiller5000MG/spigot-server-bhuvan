package net.minecraft.util;

import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

public class StringDecomposer {

    private static final char REPLACEMENT_CHAR = '\ufffd';
    private static final Optional<Object> STOP_ITERATION = Optional.of(Unit.INSTANCE);

    public StringDecomposer() {}

    private static boolean feedChar(Style style, FormattedCharSink output, int pos, char ch) {
        return Character.isSurrogate(ch) ? output.accept(pos, style, 65533) : output.accept(pos, style, ch);
    }

    public static boolean iterate(String string, Style style, FormattedCharSink output) {
        int i = string.length();

        for (int j = 0; j < i; ++j) {
            char c0 = string.charAt(j);

            if (Character.isHighSurrogate(c0)) {
                if (j + 1 >= i) {
                    if (!output.accept(j, style, 65533)) {
                        return false;
                    }
                    break;
                }

                char c1 = string.charAt(j + 1);

                if (Character.isLowSurrogate(c1)) {
                    if (!output.accept(j, style, Character.toCodePoint(c0, c1))) {
                        return false;
                    }

                    ++j;
                } else if (!output.accept(j, style, 65533)) {
                    return false;
                }
            } else if (!feedChar(style, output, j, c0)) {
                return false;
            }
        }

        return true;
    }

    public static boolean iterateBackwards(String string, Style style, FormattedCharSink output) {
        int i = string.length();

        for (int j = i - 1; j >= 0; --j) {
            char c0 = string.charAt(j);

            if (Character.isLowSurrogate(c0)) {
                if (j - 1 < 0) {
                    if (!output.accept(0, style, 65533)) {
                        return false;
                    }
                    break;
                }

                char c1 = string.charAt(j - 1);

                if (Character.isHighSurrogate(c1)) {
                    --j;
                    if (!output.accept(j, style, Character.toCodePoint(c1, c0))) {
                        return false;
                    }
                } else if (!output.accept(j, style, 65533)) {
                    return false;
                }
            } else if (!feedChar(style, output, j, c0)) {
                return false;
            }
        }

        return true;
    }

    public static boolean iterateFormatted(String string, Style style, FormattedCharSink output) {
        return iterateFormatted(string, 0, style, output);
    }

    public static boolean iterateFormatted(String string, int offset, Style style, FormattedCharSink output) {
        return iterateFormatted(string, offset, style, style, output);
    }

    public static boolean iterateFormatted(String string, int offset, Style currentStyle, Style resetStyle, FormattedCharSink output) {
        int j = string.length();
        Style style2 = currentStyle;

        for (int k = offset; k < j; ++k) {
            char c0 = string.charAt(k);

            if (c0 == 167) {
                if (k + 1 >= j) {
                    break;
                }

                char c1 = string.charAt(k + 1);
                ChatFormatting chatformatting = ChatFormatting.getByCode(c1);

                if (chatformatting != null) {
                    style2 = chatformatting == ChatFormatting.RESET ? resetStyle : style2.applyLegacyFormat(chatformatting);
                }

                ++k;
            } else if (Character.isHighSurrogate(c0)) {
                if (k + 1 >= j) {
                    if (!output.accept(k, style2, 65533)) {
                        return false;
                    }
                    break;
                }

                char c2 = string.charAt(k + 1);

                if (Character.isLowSurrogate(c2)) {
                    if (!output.accept(k, style2, Character.toCodePoint(c0, c2))) {
                        return false;
                    }

                    ++k;
                } else if (!output.accept(k, style2, 65533)) {
                    return false;
                }
            } else if (!feedChar(style2, output, k, c0)) {
                return false;
            }
        }

        return true;
    }

    public static boolean iterateFormatted(FormattedText component, Style rootStyle, FormattedCharSink output) {
        return component.visit((style1, s) -> {
            return iterateFormatted(s, 0, style1, output) ? Optional.empty() : StringDecomposer.STOP_ITERATION;
        }, rootStyle).isEmpty();
    }

    public static String filterBrokenSurrogates(String input) {
        StringBuilder stringbuilder = new StringBuilder();

        iterate(input, Style.EMPTY, (i, style, j) -> {
            stringbuilder.appendCodePoint(j);
            return true;
        });
        return stringbuilder.toString();
    }

    public static String getPlainText(FormattedText input) {
        StringBuilder stringbuilder = new StringBuilder();

        iterateFormatted(input, Style.EMPTY, (i, style, j) -> {
            stringbuilder.appendCodePoint(j);
            return true;
        });
        return stringbuilder.toString();
    }
}
