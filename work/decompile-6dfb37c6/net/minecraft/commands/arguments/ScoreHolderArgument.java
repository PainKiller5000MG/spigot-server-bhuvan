package net.minecraft.commands.arguments;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.ScoreHolder;

public class ScoreHolderArgument implements ArgumentType<ScoreHolderArgument.Result> {

    public static final SuggestionProvider<CommandSourceStack> SUGGEST_SCORE_HOLDERS = (commandcontext, suggestionsbuilder) -> {
        StringReader stringreader = new StringReader(suggestionsbuilder.getInput());

        stringreader.setCursor(suggestionsbuilder.getStart());
        EntitySelectorParser entityselectorparser = new EntitySelectorParser(stringreader, ((CommandSourceStack) commandcontext.getSource()).permissions().hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS));

        try {
            entityselectorparser.parse();
        } catch (CommandSyntaxException commandsyntaxexception) {
            ;
        }

        return entityselectorparser.fillSuggestions(suggestionsbuilder, (suggestionsbuilder1) -> {
            SharedSuggestionProvider.suggest(((CommandSourceStack) commandcontext.getSource()).getOnlinePlayerNames(), suggestionsbuilder1);
        });
    };
    private static final Collection<String> EXAMPLES = Arrays.asList("Player", "0123", "*", "@e");
    private static final SimpleCommandExceptionType ERROR_NO_RESULTS = new SimpleCommandExceptionType(Component.translatable("argument.scoreHolder.empty"));
    private final boolean multiple;

    public ScoreHolderArgument(boolean multiple) {
        this.multiple = multiple;
    }

    public static ScoreHolder getName(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return (ScoreHolder) getNames(context, name).iterator().next();
    }

    public static Collection<ScoreHolder> getNames(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return getNames(context, name, Collections::emptyList);
    }

    public static Collection<ScoreHolder> getNamesWithDefaultWildcard(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ServerScoreboard serverscoreboard = ((CommandSourceStack) context.getSource()).getServer().getScoreboard();

        Objects.requireNonNull(serverscoreboard);
        return getNames(context, name, serverscoreboard::getTrackedPlayers);
    }

    public static Collection<ScoreHolder> getNames(CommandContext<CommandSourceStack> context, String name, Supplier<Collection<ScoreHolder>> wildcard) throws CommandSyntaxException {
        Collection<ScoreHolder> collection = ((ScoreHolderArgument.Result) context.getArgument(name, ScoreHolderArgument.Result.class)).getNames((CommandSourceStack) context.getSource(), wildcard);

        if (collection.isEmpty()) {
            throw EntityArgument.NO_ENTITIES_FOUND.create();
        } else {
            return collection;
        }
    }

    public static ScoreHolderArgument scoreHolder() {
        return new ScoreHolderArgument(false);
    }

    public static ScoreHolderArgument scoreHolders() {
        return new ScoreHolderArgument(true);
    }

    public ScoreHolderArgument.Result parse(StringReader reader) throws CommandSyntaxException {
        return this.parse(reader, true);
    }

    public <S> ScoreHolderArgument.Result parse(StringReader reader, S source) throws CommandSyntaxException {
        return this.parse(reader, EntitySelectorParser.allowSelectors(source));
    }

    private ScoreHolderArgument.Result parse(StringReader reader, boolean allowSelectors) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '@') {
            EntitySelectorParser entityselectorparser = new EntitySelectorParser(reader, allowSelectors);
            EntitySelector entityselector = entityselectorparser.parse();

            if (!this.multiple && entityselector.getMaxResults() > 1) {
                throw EntityArgument.ERROR_NOT_SINGLE_ENTITY.createWithContext(reader);
            } else {
                return new ScoreHolderArgument.SelectorResult(entityselector);
            }
        } else {
            int i = reader.getCursor();

            while (reader.canRead() && reader.peek() != ' ') {
                reader.skip();
            }

            String s = reader.getString().substring(i, reader.getCursor());

            if (s.equals("*")) {
                return (commandsourcestack, supplier) -> {
                    Collection<ScoreHolder> collection = (Collection) supplier.get();

                    if (collection.isEmpty()) {
                        throw ScoreHolderArgument.ERROR_NO_RESULTS.create();
                    } else {
                        return collection;
                    }
                };
            } else {
                List<ScoreHolder> list = List.of(ScoreHolder.forNameOnly(s));

                if (s.startsWith("#")) {
                    return (commandsourcestack, supplier) -> {
                        return list;
                    };
                } else {
                    try {
                        UUID uuid = UUID.fromString(s);

                        return (commandsourcestack, supplier) -> {
                            MinecraftServer minecraftserver = commandsourcestack.getServer();
                            ScoreHolder scoreholder = null;
                            List<ScoreHolder> list1 = null;

                            for (ServerLevel serverlevel : minecraftserver.getAllLevels()) {
                                Entity entity = serverlevel.getEntity(uuid);

                                if (entity != null) {
                                    if (scoreholder == null) {
                                        scoreholder = entity;
                                    } else {
                                        if (list1 == null) {
                                            list1 = new ArrayList();
                                            list1.add(scoreholder);
                                        }

                                        list1.add(entity);
                                    }
                                }
                            }

                            if (list1 != null) {
                                return list1;
                            } else if (scoreholder != null) {
                                return List.of(scoreholder);
                            } else {
                                return list;
                            }
                        };
                    } catch (IllegalArgumentException illegalargumentexception) {
                        return (commandsourcestack, supplier) -> {
                            MinecraftServer minecraftserver = commandsourcestack.getServer();
                            ServerPlayer serverplayer = minecraftserver.getPlayerList().getPlayerByName(s);

                            return serverplayer != null ? List.of(serverplayer) : list;
                        };
                    }
                }
            }
        }
    }

    public Collection<String> getExamples() {
        return ScoreHolderArgument.EXAMPLES;
    }

    public static class SelectorResult implements ScoreHolderArgument.Result {

        private final EntitySelector selector;

        public SelectorResult(EntitySelector selector) {
            this.selector = selector;
        }

        @Override
        public Collection<ScoreHolder> getNames(CommandSourceStack sender, Supplier<Collection<ScoreHolder>> wildcard) throws CommandSyntaxException {
            List<? extends Entity> list = this.selector.findEntities(sender);

            if (list.isEmpty()) {
                throw EntityArgument.NO_ENTITIES_FOUND.create();
            } else {
                return List.copyOf(list);
            }
        }
    }

    public static class Info implements ArgumentTypeInfo<ScoreHolderArgument, ScoreHolderArgument.Info.Template> {

        private static final byte FLAG_MULTIPLE = 1;

        public Info() {}

        public void serializeToNetwork(ScoreHolderArgument.Info.Template template, FriendlyByteBuf out) {
            int i = 0;

            if (template.multiple) {
                i |= 1;
            }

            out.writeByte(i);
        }

        @Override
        public ScoreHolderArgument.Info.Template deserializeFromNetwork(FriendlyByteBuf in) {
            byte b0 = in.readByte();
            boolean flag = (b0 & 1) != 0;

            return new ScoreHolderArgument.Info.Template(flag);
        }

        public void serializeToJson(ScoreHolderArgument.Info.Template template, JsonObject out) {
            out.addProperty("amount", template.multiple ? "multiple" : "single");
        }

        public ScoreHolderArgument.Info.Template unpack(ScoreHolderArgument argument) {
            return new ScoreHolderArgument.Info.Template(argument.multiple);
        }

        public final class Template implements ArgumentTypeInfo.Template<ScoreHolderArgument> {

            private final boolean multiple;

            private Template(boolean multiple) {
                this.multiple = multiple;
            }

            @Override
            public ScoreHolderArgument instantiate(CommandBuildContext context) {
                return new ScoreHolderArgument(this.multiple);
            }

            @Override
            public ArgumentTypeInfo<ScoreHolderArgument, ?> type() {
                return Info.this;
            }
        }
    }

    @FunctionalInterface
    public interface Result {

        Collection<ScoreHolder> getNames(CommandSourceStack sender, Supplier<Collection<ScoreHolder>> wildcard) throws CommandSyntaxException;
    }
}
