package net.minecraft.util.parsing.packrat;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.util.parsing.packrat.commands.StringReaderTerms;

public interface DelayedException<T extends Exception> {

    T create(String contents, int position);

    static DelayedException<CommandSyntaxException> create(SimpleCommandExceptionType type) {
        return (s, i) -> {
            return type.createWithContext(StringReaderTerms.createReader(s, i));
        };
    }

    static DelayedException<CommandSyntaxException> create(DynamicCommandExceptionType type, String argument) {
        return (s1, i) -> {
            return type.createWithContext(StringReaderTerms.createReader(s1, i), argument);
        };
    }
}
