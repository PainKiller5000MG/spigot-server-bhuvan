package net.minecraft.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

public class HexColorArgument implements ArgumentType<Integer> {

    private static final Collection<String> EXAMPLES = Arrays.asList("F00", "FF0000");
    public static final DynamicCommandExceptionType ERROR_INVALID_HEX = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("argument.hexcolor.invalid", object);
    });

    private HexColorArgument() {}

    public static HexColorArgument hexColor() {
        return new HexColorArgument();
    }

    public static Integer getHexColor(CommandContext<CommandSourceStack> context, String name) {
        return (Integer) context.getArgument(name, Integer.class);
    }

    public Integer parse(StringReader reader) throws CommandSyntaxException {
        String s = reader.readUnquotedString();
        Integer integer;

        switch (s.length()) {
            case 3:
                integer = ARGB.color(duplicateDigit(Integer.parseInt(s, 0, 1, 16)), duplicateDigit(Integer.parseInt(s, 1, 2, 16)), duplicateDigit(Integer.parseInt(s, 2, 3, 16)));
                break;
            case 6:
                integer = ARGB.color(Integer.parseInt(s, 0, 2, 16), Integer.parseInt(s, 2, 4, 16), Integer.parseInt(s, 4, 6, 16));
                break;
            default:
                throw HexColorArgument.ERROR_INVALID_HEX.createWithContext(reader, s);
        }

        return integer;
    }

    private static int duplicateDigit(int digit) {
        return digit * 17;
    }

    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> contextBuilder, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(HexColorArgument.EXAMPLES, builder);
    }

    public Collection<String> getExamples() {
        return HexColorArgument.EXAMPLES;
    }
}
