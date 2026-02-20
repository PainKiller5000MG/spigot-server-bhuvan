package net.minecraft.commands.arguments;

import com.google.common.collect.Iterables;
import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;

public class EntityArgument implements ArgumentType<EntitySelector> {

    private static final Collection<String> EXAMPLES = Arrays.asList("Player", "0123", "@e", "@e[type=foo]", "dd12be42-52a9-4a91-a8a1-11c01849e498");
    public static final SimpleCommandExceptionType ERROR_NOT_SINGLE_ENTITY = new SimpleCommandExceptionType(Component.translatable("argument.entity.toomany"));
    public static final SimpleCommandExceptionType ERROR_NOT_SINGLE_PLAYER = new SimpleCommandExceptionType(Component.translatable("argument.player.toomany"));
    public static final SimpleCommandExceptionType ERROR_ONLY_PLAYERS_ALLOWED = new SimpleCommandExceptionType(Component.translatable("argument.player.entities"));
    public static final SimpleCommandExceptionType NO_ENTITIES_FOUND = new SimpleCommandExceptionType(Component.translatable("argument.entity.notfound.entity"));
    public static final SimpleCommandExceptionType NO_PLAYERS_FOUND = new SimpleCommandExceptionType(Component.translatable("argument.entity.notfound.player"));
    public static final SimpleCommandExceptionType ERROR_SELECTORS_NOT_ALLOWED = new SimpleCommandExceptionType(Component.translatable("argument.entity.selector.not_allowed"));
    private final boolean single;
    private final boolean playersOnly;

    protected EntityArgument(boolean single, boolean playersOnly) {
        this.single = single;
        this.playersOnly = playersOnly;
    }

    public static EntityArgument entity() {
        return new EntityArgument(true, false);
    }

    public static Entity getEntity(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return ((EntitySelector) context.getArgument(name, EntitySelector.class)).findSingleEntity((CommandSourceStack) context.getSource());
    }

    public static EntityArgument entities() {
        return new EntityArgument(false, false);
    }

    public static Collection<? extends Entity> getEntities(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        Collection<? extends Entity> collection = getOptionalEntities(context, name);

        if (collection.isEmpty()) {
            throw EntityArgument.NO_ENTITIES_FOUND.create();
        } else {
            return collection;
        }
    }

    public static Collection<? extends Entity> getOptionalEntities(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return ((EntitySelector) context.getArgument(name, EntitySelector.class)).findEntities((CommandSourceStack) context.getSource());
    }

    public static Collection<ServerPlayer> getOptionalPlayers(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return ((EntitySelector) context.getArgument(name, EntitySelector.class)).findPlayers((CommandSourceStack) context.getSource());
    }

    public static EntityArgument player() {
        return new EntityArgument(true, true);
    }

    public static ServerPlayer getPlayer(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return ((EntitySelector) context.getArgument(name, EntitySelector.class)).findSinglePlayer((CommandSourceStack) context.getSource());
    }

    public static EntityArgument players() {
        return new EntityArgument(false, true);
    }

    public static Collection<ServerPlayer> getPlayers(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        List<ServerPlayer> list = ((EntitySelector) context.getArgument(name, EntitySelector.class)).findPlayers((CommandSourceStack) context.getSource());

        if (list.isEmpty()) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        } else {
            return list;
        }
    }

    public EntitySelector parse(StringReader reader) throws CommandSyntaxException {
        return this.parse(reader, true);
    }

    public <S> EntitySelector parse(StringReader reader, S source) throws CommandSyntaxException {
        return this.parse(reader, EntitySelectorParser.allowSelectors(source));
    }

    private EntitySelector parse(StringReader reader, boolean allowSelectors) throws CommandSyntaxException {
        int i = 0;
        EntitySelectorParser entityselectorparser = new EntitySelectorParser(reader, allowSelectors);
        EntitySelector entityselector = entityselectorparser.parse();

        if (entityselector.getMaxResults() > 1 && this.single) {
            if (this.playersOnly) {
                reader.setCursor(0);
                throw EntityArgument.ERROR_NOT_SINGLE_PLAYER.createWithContext(reader);
            } else {
                reader.setCursor(0);
                throw EntityArgument.ERROR_NOT_SINGLE_ENTITY.createWithContext(reader);
            }
        } else if (entityselector.includesEntities() && this.playersOnly && !entityselector.isSelfSelector()) {
            reader.setCursor(0);
            throw EntityArgument.ERROR_ONLY_PLAYERS_ALLOWED.createWithContext(reader);
        } else {
            return entityselector;
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
                Collection<String> collection = sharedsuggestionprovider.getOnlinePlayerNames();
                Iterable<String> iterable = (Iterable<String>) (this.playersOnly ? collection : Iterables.concat(collection, sharedsuggestionprovider.getSelectedEntities()));

                SharedSuggestionProvider.suggest(iterable, suggestionsbuilder1);
            });
        } else {
            return Suggestions.empty();
        }
    }

    public Collection<String> getExamples() {
        return EntityArgument.EXAMPLES;
    }

    public static class Info implements ArgumentTypeInfo<EntityArgument, EntityArgument.Info.Template> {

        private static final byte FLAG_SINGLE = 1;
        private static final byte FLAG_PLAYERS_ONLY = 2;

        public Info() {}

        public void serializeToNetwork(EntityArgument.Info.Template template, FriendlyByteBuf out) {
            int i = 0;

            if (template.single) {
                i |= 1;
            }

            if (template.playersOnly) {
                i |= 2;
            }

            out.writeByte(i);
        }

        @Override
        public EntityArgument.Info.Template deserializeFromNetwork(FriendlyByteBuf in) {
            byte b0 = in.readByte();

            return new EntityArgument.Info.Template((b0 & 1) != 0, (b0 & 2) != 0);
        }

        public void serializeToJson(EntityArgument.Info.Template template, JsonObject out) {
            out.addProperty("amount", template.single ? "single" : "multiple");
            out.addProperty("type", template.playersOnly ? "players" : "entities");
        }

        public EntityArgument.Info.Template unpack(EntityArgument argument) {
            return new EntityArgument.Info.Template(argument.single, argument.playersOnly);
        }

        public final class Template implements ArgumentTypeInfo.Template<EntityArgument> {

            private final boolean single;
            private final boolean playersOnly;

            private Template(boolean single, boolean playersOnly) {
                this.single = single;
                this.playersOnly = playersOnly;
            }

            @Override
            public EntityArgument instantiate(CommandBuildContext context) {
                return new EntityArgument(this.single, this.playersOnly);
            }

            @Override
            public ArgumentTypeInfo<EntityArgument, ?> type() {
                return Info.this;
            }
        }
    }
}
