package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.chars.CharList;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.util.parsing.packrat.Control;
import net.minecraft.util.parsing.packrat.DelayedException;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Scope;
import net.minecraft.util.parsing.packrat.SuggestionSupplier;
import net.minecraft.util.parsing.packrat.Term;

public interface StringReaderTerms {

    static Term<StringReader> word(String value) {
        return new StringReaderTerms.TerminalWord(value);
    }

    static Term<StringReader> character(final char value) {
        return new StringReaderTerms.TerminalCharacters(CharList.of(value)) {
            @Override
            protected boolean isAccepted(char v) {
                return value == v;
            }
        };
    }

    static Term<StringReader> characters(final char v1, final char v2) {
        return new StringReaderTerms.TerminalCharacters(CharList.of(v1, v2)) {
            @Override
            protected boolean isAccepted(char v) {
                return v == v1 || v == v2;
            }
        };
    }

    static StringReader createReader(String contents, int cursor) {
        StringReader stringreader = new StringReader(contents);

        stringreader.setCursor(cursor);
        return stringreader;
    }

    public static final class TerminalWord implements Term<StringReader> {

        private final String value;
        private final DelayedException<CommandSyntaxException> error;
        private final SuggestionSupplier<StringReader> suggestions;

        public TerminalWord(String value) {
            this.value = value;
            this.error = DelayedException.create(CommandSyntaxException.BUILT_IN_EXCEPTIONS.literalIncorrect(), value);
            this.suggestions = (parsestate) -> {
                return Stream.of(value);
            };
        }

        @Override
        public boolean parse(ParseState<StringReader> state, Scope scope, Control control) {
            ((StringReader) state.input()).skipWhitespace();
            int i = state.mark();
            String s = ((StringReader) state.input()).readUnquotedString();

            if (!s.equals(this.value)) {
                state.errorCollector().store(i, this.suggestions, this.error);
                return false;
            } else {
                return true;
            }
        }

        public String toString() {
            return "terminal[" + this.value + "]";
        }
    }

    public abstract static class TerminalCharacters implements Term<StringReader> {

        private final DelayedException<CommandSyntaxException> error;
        private final SuggestionSupplier<StringReader> suggestions;

        public TerminalCharacters(CharList values) {
            String s = (String) values.intStream().mapToObj(Character::toString).collect(Collectors.joining("|"));

            this.error = DelayedException.create(CommandSyntaxException.BUILT_IN_EXCEPTIONS.literalIncorrect(), s);
            this.suggestions = (parsestate) -> {
                return values.intStream().mapToObj(Character::toString);
            };
        }

        @Override
        public boolean parse(ParseState<StringReader> state, Scope scope, Control control) {
            ((StringReader) state.input()).skipWhitespace();
            int i = state.mark();

            if (((StringReader) state.input()).canRead() && this.isAccepted(((StringReader) state.input()).read())) {
                return true;
            } else {
                state.errorCollector().store(i, this.suggestions, this.error);
                return false;
            }
        }

        protected abstract boolean isAccepted(char value);
    }
}
