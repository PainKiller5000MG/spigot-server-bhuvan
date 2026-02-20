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
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

public class ObjectiveArgument implements ArgumentType<String> {

    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "*", "012");
    private static final DynamicCommandExceptionType ERROR_OBJECTIVE_NOT_FOUND = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("arguments.objective.notFound", object);
    });
    private static final DynamicCommandExceptionType ERROR_OBJECTIVE_READ_ONLY = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("arguments.objective.readonly", object);
    });

    public ObjectiveArgument() {}

    public static ObjectiveArgument objective() {
        return new ObjectiveArgument();
    }

    public static Objective getObjective(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        String s1 = (String) context.getArgument(name, String.class);
        Scoreboard scoreboard = ((CommandSourceStack) context.getSource()).getServer().getScoreboard();
        Objective objective = scoreboard.getObjective(s1);

        if (objective == null) {
            throw ObjectiveArgument.ERROR_OBJECTIVE_NOT_FOUND.create(s1);
        } else {
            return objective;
        }
    }

    public static Objective getWritableObjective(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        Objective objective = getObjective(context, name);

        if (objective.getCriteria().isReadOnly()) {
            throw ObjectiveArgument.ERROR_OBJECTIVE_READ_ONLY.create(objective.getName());
        } else {
            return objective;
        }
    }

    public String parse(StringReader reader) throws CommandSyntaxException {
        return reader.readUnquotedString();
    }

    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        S s0 = (S) context.getSource();

        if (s0 instanceof CommandSourceStack commandsourcestack) {
            return SharedSuggestionProvider.suggest(commandsourcestack.getServer().getScoreboard().getObjectiveNames(), builder);
        } else if (s0 instanceof SharedSuggestionProvider sharedsuggestionprovider) {
            return sharedsuggestionprovider.customSuggestion(context);
        } else {
            return Suggestions.empty();
        }
    }

    public Collection<String> getExamples() {
        return ObjectiveArgument.EXAMPLES;
    }
}
