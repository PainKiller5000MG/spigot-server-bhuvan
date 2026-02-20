package net.minecraft.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;

public class GameProfileArgument implements ArgumentType<GameProfileArgument.Result> {

    private static final Collection<String> EXAMPLES = Arrays.asList("Player", "0123", "dd12be42-52a9-4a91-a8a1-11c01849e498", "@e");
    public static final SimpleCommandExceptionType ERROR_UNKNOWN_PLAYER = new SimpleCommandExceptionType(Component.translatable("argument.player.unknown"));

    public GameProfileArgument() {}

    public static Collection<NameAndId> getGameProfiles(CommandContext<CommandSourceStack> source, String name) throws CommandSyntaxException {
        return ((GameProfileArgument.Result) source.getArgument(name, GameProfileArgument.Result.class)).getNames((CommandSourceStack) source.getSource());
    }

    public static GameProfileArgument gameProfile() {
        return new GameProfileArgument();
    }

    public <S> GameProfileArgument.Result parse(StringReader reader, S source) throws CommandSyntaxException {
        return parse(reader, EntitySelectorParser.allowSelectors(source));
    }

    public GameProfileArgument.Result parse(StringReader reader) throws CommandSyntaxException {
        return parse(reader, true);
    }

    private static GameProfileArgument.Result parse(StringReader reader, boolean allowSelectors) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '@') {
            EntitySelectorParser entityselectorparser = new EntitySelectorParser(reader, allowSelectors);
            EntitySelector entityselector = entityselectorparser.parse();

            if (entityselector.includesEntities()) {
                throw EntityArgument.ERROR_ONLY_PLAYERS_ALLOWED.createWithContext(reader);
            } else {
                return new GameProfileArgument.SelectorResult(entityselector);
            }
        } else {
            int i = reader.getCursor();

            while (reader.canRead() && reader.peek() != ' ') {
                reader.skip();
            }

            String s = reader.getString().substring(i, reader.getCursor());

            return (commandsourcestack) -> {
                Optional<NameAndId> optional = commandsourcestack.getServer().services().nameToIdCache().get(s);
                SimpleCommandExceptionType simplecommandexceptiontype = GameProfileArgument.ERROR_UNKNOWN_PLAYER;

                Objects.requireNonNull(simplecommandexceptiontype);
                return Collections.singleton((NameAndId) optional.orElseThrow(simplecommandexceptiontype::create));
            };
        }
    }

    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> contextBuilder, SuggestionsBuilder builder) {
        Object object = contextBuilder.getSource();

        if (object instanceof SharedSuggestionProvider sharedsuggestionprovider) {
            StringReader stringreader = new StringReader(builder.getInput());

            stringreader.setCursor(builder.getStart());
            EntitySelectorParser entityselectorparser = new EntitySelectorParser(stringreader, sharedsuggestionprovider.permissions().hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS));

            try {
                entityselectorparser.parse();
            } catch (CommandSyntaxException commandsyntaxexception) {
                ;
            }

            return entityselectorparser.fillSuggestions(builder, (suggestionsbuilder1) -> {
                SharedSuggestionProvider.suggest(sharedsuggestionprovider.getOnlinePlayerNames(), suggestionsbuilder1);
            });
        } else {
            return Suggestions.empty();
        }
    }

    public Collection<String> getExamples() {
        return GameProfileArgument.EXAMPLES;
    }

    public static class SelectorResult implements GameProfileArgument.Result {

        private final EntitySelector selector;

        public SelectorResult(EntitySelector selector) {
            this.selector = selector;
        }

        @Override
        public Collection<NameAndId> getNames(CommandSourceStack sender) throws CommandSyntaxException {
            List<ServerPlayer> list = this.selector.findPlayers(sender);

            if (list.isEmpty()) {
                throw EntityArgument.NO_PLAYERS_FOUND.create();
            } else {
                List<NameAndId> list1 = new ArrayList();

                for (ServerPlayer serverplayer : list) {
                    list1.add(serverplayer.nameAndId());
                }

                return list1;
            }
        }
    }

    @FunctionalInterface
    public interface Result {

        Collection<NameAndId> getNames(CommandSourceStack sender) throws CommandSyntaxException;
    }
}
