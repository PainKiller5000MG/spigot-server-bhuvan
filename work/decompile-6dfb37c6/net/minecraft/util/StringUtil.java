package net.minecraft.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

public class StringUtil {

    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)\\u00A7[0-9A-FK-OR]");
    private static final Pattern LINE_PATTERN = Pattern.compile("\\r\\n|\\v");
    private static final Pattern LINE_END_PATTERN = Pattern.compile("(?:\\r\\n|\\v)$");

    public StringUtil() {}

    public static String formatTickDuration(int ticks, float tickrate) {
        int j = Mth.floor((float) ticks / tickrate);
        int k = j / 60;

        j %= 60;
        int l = k / 60;

        k %= 60;
        return l > 0 ? String.format(Locale.ROOT, "%02d:%02d:%02d", l, k, j) : String.format(Locale.ROOT, "%02d:%02d", k, j);
    }

    public static String stripColor(String input) {
        return StringUtil.STRIP_COLOR_PATTERN.matcher(input).replaceAll("");
    }

    public static boolean isNullOrEmpty(@Nullable String s) {
        return StringUtils.isEmpty(s);
    }

    public static String truncateStringIfNecessary(String s, int maxLength, boolean addDotDotDotIfTruncated) {
        if (s.length() <= maxLength) {
            return s;
        } else if (addDotDotDotIfTruncated && maxLength > 3) {
            String s1 = s.substring(0, maxLength - 3);

            return s1 + "...";
        } else {
            return s.substring(0, maxLength);
        }
    }

    public static int lineCount(String s) {
        if (s.isEmpty()) {
            return 0;
        } else {
            Matcher matcher = StringUtil.LINE_PATTERN.matcher(s);

            int i;

            for (i = 1; matcher.find(); ++i) {
                ;
            }

            return i;
        }
    }

    public static boolean endsWithNewLine(String s) {
        return StringUtil.LINE_END_PATTERN.matcher(s).find();
    }

    public static String trimChatMessage(String message) {
        return truncateStringIfNecessary(message, 256, false);
    }

    public static boolean isAllowedChatCharacter(int ch) {
        return ch != 167 && ch >= 32 && ch != 127;
    }

    public static boolean isValidPlayerName(String name) {
        return name.length() > 16 ? false : name.chars().filter((i) -> {
            return i <= 32 || i >= 127;
        }).findAny().isEmpty();
    }

    public static String filterText(String input) {
        return filterText(input, false);
    }

    public static String filterText(String input, boolean multiline) {
        StringBuilder stringbuilder = new StringBuilder();

        for (char c0 : input.toCharArray()) {
            if (isAllowedChatCharacter(c0)) {
                stringbuilder.append(c0);
            } else if (multiline && c0 == '\n') {
                stringbuilder.append(c0);
            }
        }

        return stringbuilder.toString();
    }

    public static boolean isWhitespace(int codepoint) {
        return Character.isWhitespace(codepoint) || Character.isSpaceChar(codepoint);
    }

    public static boolean isBlank(@Nullable String string) {
        return string != null && !string.isEmpty() ? string.chars().allMatch(StringUtil::isWhitespace) : true;
    }
}
