package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.util.parsing.packrat.DelayedException;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Rule;
import org.jspecify.annotations.Nullable;

public abstract class NumberRunParseRule implements Rule<StringReader, String> {

    private final DelayedException<CommandSyntaxException> noValueError;
    private final DelayedException<CommandSyntaxException> underscoreNotAllowedError;

    public NumberRunParseRule(DelayedException<CommandSyntaxException> noValueError, DelayedException<CommandSyntaxException> underscoreNotAllowedError) {
        this.noValueError = noValueError;
        this.underscoreNotAllowedError = underscoreNotAllowedError;
    }

    @Override
    public @Nullable String parse(ParseState<StringReader> state) {
        StringReader stringreader = state.input();

        stringreader.skipWhitespace();
        String s = stringreader.getString();
        int i = stringreader.getCursor();

        int j;

        for (j = i; j < s.length() && this.isAccepted(s.charAt(j)); ++j) {
            ;
        }

        int k = j - i;

        if (k == 0) {
            state.errorCollector().store(state.mark(), this.noValueError);
            return null;
        } else if (s.charAt(i) != '_' && s.charAt(j - 1) != '_') {
            stringreader.setCursor(j);
            return s.substring(i, j);
        } else {
            state.errorCollector().store(state.mark(), this.underscoreNotAllowedError);
            return null;
        }
    }

    protected abstract boolean isAccepted(char c);
}
