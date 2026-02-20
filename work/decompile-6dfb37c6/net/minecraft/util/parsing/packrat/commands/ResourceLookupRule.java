package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.resources.Identifier;
import net.minecraft.util.parsing.packrat.DelayedException;
import net.minecraft.util.parsing.packrat.NamedRule;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Rule;
import org.jspecify.annotations.Nullable;

public abstract class ResourceLookupRule<C, V> implements Rule<StringReader, V>, ResourceSuggestion {

    private final NamedRule<StringReader, Identifier> idParser;
    protected final C context;
    private final DelayedException<CommandSyntaxException> error;

    protected ResourceLookupRule(NamedRule<StringReader, Identifier> idParser, C context) {
        this.idParser = idParser;
        this.context = context;
        this.error = DelayedException.create(Identifier.ERROR_INVALID);
    }

    @Override
    public @Nullable V parse(ParseState<StringReader> state) {
        ((StringReader) state.input()).skipWhitespace();
        int i = state.mark();
        Identifier identifier = (Identifier) state.parse(this.idParser);

        if (identifier != null) {
            try {
                return (V) this.validateElement((ImmutableStringReader) state.input(), identifier);
            } catch (Exception exception) {
                state.errorCollector().store(i, this, exception);
                return null;
            }
        } else {
            state.errorCollector().store(i, this, this.error);
            return null;
        }
    }

    protected abstract V validateElement(ImmutableStringReader reader, Identifier id) throws Exception;
}
