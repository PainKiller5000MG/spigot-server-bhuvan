package net.minecraft.commands.functions;

import com.google.common.collect.ImmutableList;
import java.util.List;

public record StringTemplate(List<String> segments, List<String> variables) {

    public static StringTemplate fromString(String input) {
        ImmutableList.Builder<String> immutablelist_builder = ImmutableList.builder();
        ImmutableList.Builder<String> immutablelist_builder1 = ImmutableList.builder();
        int i = input.length();
        int j = 0;
        int k = input.indexOf(36);

        while (k != -1) {
            if (k != i - 1 && input.charAt(k + 1) == '(') {
                immutablelist_builder.add(input.substring(j, k));
                int l = input.indexOf(41, k + 1);

                if (l == -1) {
                    throw new IllegalArgumentException("Unterminated macro variable");
                }

                String s1 = input.substring(k + 2, l);

                if (!isValidVariableName(s1)) {
                    throw new IllegalArgumentException("Invalid macro variable name '" + s1 + "'");
                }

                immutablelist_builder1.add(s1);
                j = l + 1;
                k = input.indexOf(36, j);
            } else {
                k = input.indexOf(36, k + 1);
            }
        }

        if (j == 0) {
            throw new IllegalArgumentException("No variables in macro");
        } else {
            if (j != i) {
                immutablelist_builder.add(input.substring(j));
            }

            return new StringTemplate(immutablelist_builder.build(), immutablelist_builder1.build());
        }
    }

    public static boolean isValidVariableName(String s) {
        for (int i = 0; i < s.length(); ++i) {
            char c0 = s.charAt(i);

            if (!Character.isLetterOrDigit(c0) && c0 != '_') {
                return false;
            }
        }

        return true;
    }

    public String substitute(List<String> arguments) {
        StringBuilder stringbuilder = new StringBuilder();

        for (int i = 0; i < this.variables.size(); ++i) {
            stringbuilder.append((String) this.segments.get(i)).append((String) arguments.get(i));
            CommandFunction.checkCommandLineLength(stringbuilder);
        }

        if (this.segments.size() > this.variables.size()) {
            stringbuilder.append((String) this.segments.getLast());
        }

        CommandFunction.checkCommandLineLength(stringbuilder);
        return stringbuilder.toString();
    }
}
