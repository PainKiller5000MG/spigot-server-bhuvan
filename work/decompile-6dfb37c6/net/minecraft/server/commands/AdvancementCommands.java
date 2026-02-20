package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class AdvancementCommands {

    private static final DynamicCommandExceptionType ERROR_NO_ACTION_PERFORMED = new DynamicCommandExceptionType((object) -> {
        return (Component) object;
    });
    private static final Dynamic2CommandExceptionType ERROR_CRITERION_NOT_FOUND = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("commands.advancement.criterionNotFound", object, object1);
    });

    public AdvancementCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("advancement").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.literal("grant").then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument("targets", EntityArgument.players()).then(Commands.literal("only").then(((RequiredArgumentBuilder) Commands.argument("advancement", ResourceKeyArgument.key(Registries.ADVANCEMENT)).executes((commandcontext) -> {
            return perform((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), AdvancementCommands.Action.GRANT, getAdvancements(commandcontext, ResourceKeyArgument.getAdvancement(commandcontext, "advancement"), AdvancementCommands.Mode.ONLY));
        })).then(Commands.argument("criterion", StringArgumentType.greedyString()).suggests((commandcontext, suggestionsbuilder) -> {
            return SharedSuggestionProvider.suggest(ResourceKeyArgument.getAdvancement(commandcontext, "advancement").value().criteria().keySet(), suggestionsbuilder);
        }).executes((commandcontext) -> {
            return performCriterion((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), AdvancementCommands.Action.GRANT, ResourceKeyArgument.getAdvancement(commandcontext, "advancement"), StringArgumentType.getString(commandcontext, "criterion"));
        }))))).then(Commands.literal("from").then(Commands.argument("advancement", ResourceKeyArgument.key(Registries.ADVANCEMENT)).executes((commandcontext) -> {
            return perform((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), AdvancementCommands.Action.GRANT, getAdvancements(commandcontext, ResourceKeyArgument.getAdvancement(commandcontext, "advancement"), AdvancementCommands.Mode.FROM));
        })))).then(Commands.literal("until").then(Commands.argument("advancement", ResourceKeyArgument.key(Registries.ADVANCEMENT)).executes((commandcontext) -> {
            return perform((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), AdvancementCommands.Action.GRANT, getAdvancements(commandcontext, ResourceKeyArgument.getAdvancement(commandcontext, "advancement"), AdvancementCommands.Mode.UNTIL));
        })))).then(Commands.literal("through").then(Commands.argument("advancement", ResourceKeyArgument.key(Registries.ADVANCEMENT)).executes((commandcontext) -> {
            return perform((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), AdvancementCommands.Action.GRANT, getAdvancements(commandcontext, ResourceKeyArgument.getAdvancement(commandcontext, "advancement"), AdvancementCommands.Mode.THROUGH));
        })))).then(Commands.literal("everything").executes((commandcontext) -> {
            return perform((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), AdvancementCommands.Action.GRANT, ((CommandSourceStack) commandcontext.getSource()).getServer().getAdvancements().getAllAdvancements(), false);
        }))))).then(Commands.literal("revoke").then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument("targets", EntityArgument.players()).then(Commands.literal("only").then(((RequiredArgumentBuilder) Commands.argument("advancement", ResourceKeyArgument.key(Registries.ADVANCEMENT)).executes((commandcontext) -> {
            return perform((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), AdvancementCommands.Action.REVOKE, getAdvancements(commandcontext, ResourceKeyArgument.getAdvancement(commandcontext, "advancement"), AdvancementCommands.Mode.ONLY));
        })).then(Commands.argument("criterion", StringArgumentType.greedyString()).suggests((commandcontext, suggestionsbuilder) -> {
            return SharedSuggestionProvider.suggest(ResourceKeyArgument.getAdvancement(commandcontext, "advancement").value().criteria().keySet(), suggestionsbuilder);
        }).executes((commandcontext) -> {
            return performCriterion((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), AdvancementCommands.Action.REVOKE, ResourceKeyArgument.getAdvancement(commandcontext, "advancement"), StringArgumentType.getString(commandcontext, "criterion"));
        }))))).then(Commands.literal("from").then(Commands.argument("advancement", ResourceKeyArgument.key(Registries.ADVANCEMENT)).executes((commandcontext) -> {
            return perform((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), AdvancementCommands.Action.REVOKE, getAdvancements(commandcontext, ResourceKeyArgument.getAdvancement(commandcontext, "advancement"), AdvancementCommands.Mode.FROM));
        })))).then(Commands.literal("until").then(Commands.argument("advancement", ResourceKeyArgument.key(Registries.ADVANCEMENT)).executes((commandcontext) -> {
            return perform((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), AdvancementCommands.Action.REVOKE, getAdvancements(commandcontext, ResourceKeyArgument.getAdvancement(commandcontext, "advancement"), AdvancementCommands.Mode.UNTIL));
        })))).then(Commands.literal("through").then(Commands.argument("advancement", ResourceKeyArgument.key(Registries.ADVANCEMENT)).executes((commandcontext) -> {
            return perform((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), AdvancementCommands.Action.REVOKE, getAdvancements(commandcontext, ResourceKeyArgument.getAdvancement(commandcontext, "advancement"), AdvancementCommands.Mode.THROUGH));
        })))).then(Commands.literal("everything").executes((commandcontext) -> {
            return perform((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), AdvancementCommands.Action.REVOKE, ((CommandSourceStack) commandcontext.getSource()).getServer().getAdvancements().getAllAdvancements());
        })))));
    }

    private static int perform(CommandSourceStack source, Collection<ServerPlayer> players, AdvancementCommands.Action action, Collection<AdvancementHolder> advancements) throws CommandSyntaxException {
        return perform(source, players, action, advancements, true);
    }

    private static int perform(CommandSourceStack source, Collection<ServerPlayer> players, AdvancementCommands.Action action, Collection<AdvancementHolder> advancements, boolean showAdvancements) throws CommandSyntaxException {
        int i = 0;

        for (ServerPlayer serverplayer : players) {
            i += action.perform(serverplayer, advancements, showAdvancements);
        }

        if (i == 0) {
            if (advancements.size() == 1) {
                if (players.size() == 1) {
                    throw AdvancementCommands.ERROR_NO_ACTION_PERFORMED.create(Component.translatable(action.getKey() + ".one.to.one.failure", Advancement.name((AdvancementHolder) advancements.iterator().next()), ((ServerPlayer) players.iterator().next()).getDisplayName()));
                } else {
                    throw AdvancementCommands.ERROR_NO_ACTION_PERFORMED.create(Component.translatable(action.getKey() + ".one.to.many.failure", Advancement.name((AdvancementHolder) advancements.iterator().next()), players.size()));
                }
            } else if (players.size() == 1) {
                throw AdvancementCommands.ERROR_NO_ACTION_PERFORMED.create(Component.translatable(action.getKey() + ".many.to.one.failure", advancements.size(), ((ServerPlayer) players.iterator().next()).getDisplayName()));
            } else {
                throw AdvancementCommands.ERROR_NO_ACTION_PERFORMED.create(Component.translatable(action.getKey() + ".many.to.many.failure", advancements.size(), players.size()));
            }
        } else {
            if (advancements.size() == 1) {
                if (players.size() == 1) {
                    source.sendSuccess(() -> {
                        return Component.translatable(action.getKey() + ".one.to.one.success", Advancement.name((AdvancementHolder) advancements.iterator().next()), ((ServerPlayer) players.iterator().next()).getDisplayName());
                    }, true);
                } else {
                    source.sendSuccess(() -> {
                        return Component.translatable(action.getKey() + ".one.to.many.success", Advancement.name((AdvancementHolder) advancements.iterator().next()), players.size());
                    }, true);
                }
            } else if (players.size() == 1) {
                source.sendSuccess(() -> {
                    return Component.translatable(action.getKey() + ".many.to.one.success", advancements.size(), ((ServerPlayer) players.iterator().next()).getDisplayName());
                }, true);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable(action.getKey() + ".many.to.many.success", advancements.size(), players.size());
                }, true);
            }

            return i;
        }
    }

    private static int performCriterion(CommandSourceStack source, Collection<ServerPlayer> players, AdvancementCommands.Action action, AdvancementHolder holder, String criterion) throws CommandSyntaxException {
        int i = 0;
        Advancement advancement = holder.value();

        if (!advancement.criteria().containsKey(criterion)) {
            throw AdvancementCommands.ERROR_CRITERION_NOT_FOUND.create(Advancement.name(holder), criterion);
        } else {
            for (ServerPlayer serverplayer : players) {
                if (action.performCriterion(serverplayer, holder, criterion)) {
                    ++i;
                }
            }

            if (i == 0) {
                if (players.size() == 1) {
                    throw AdvancementCommands.ERROR_NO_ACTION_PERFORMED.create(Component.translatable(action.getKey() + ".criterion.to.one.failure", criterion, Advancement.name(holder), ((ServerPlayer) players.iterator().next()).getDisplayName()));
                } else {
                    throw AdvancementCommands.ERROR_NO_ACTION_PERFORMED.create(Component.translatable(action.getKey() + ".criterion.to.many.failure", criterion, Advancement.name(holder), players.size()));
                }
            } else {
                if (players.size() == 1) {
                    source.sendSuccess(() -> {
                        return Component.translatable(action.getKey() + ".criterion.to.one.success", criterion, Advancement.name(holder), ((ServerPlayer) players.iterator().next()).getDisplayName());
                    }, true);
                } else {
                    source.sendSuccess(() -> {
                        return Component.translatable(action.getKey() + ".criterion.to.many.success", criterion, Advancement.name(holder), players.size());
                    }, true);
                }

                return i;
            }
        }
    }

    private static List<AdvancementHolder> getAdvancements(CommandContext<CommandSourceStack> context, AdvancementHolder target, AdvancementCommands.Mode mode) {
        AdvancementTree advancementtree = ((CommandSourceStack) context.getSource()).getServer().getAdvancements().tree();
        AdvancementNode advancementnode = advancementtree.get(target);

        if (advancementnode == null) {
            return List.of(target);
        } else {
            List<AdvancementHolder> list = new ArrayList();

            if (mode.parents) {
                for (AdvancementNode advancementnode1 = advancementnode.parent(); advancementnode1 != null; advancementnode1 = advancementnode1.parent()) {
                    list.add(advancementnode1.holder());
                }
            }

            list.add(target);
            if (mode.children) {
                addChildren(advancementnode, list);
            }

            return list;
        }
    }

    private static void addChildren(AdvancementNode parent, List<AdvancementHolder> output) {
        for (AdvancementNode advancementnode1 : parent.children()) {
            output.add(advancementnode1.holder());
            addChildren(advancementnode1, output);
        }

    }

    private static enum Action {

        GRANT("grant") {
            @Override
            protected boolean perform(ServerPlayer player, AdvancementHolder advancement) {
                AdvancementProgress advancementprogress = player.getAdvancements().getOrStartProgress(advancement);

                if (advancementprogress.isDone()) {
                    return false;
                } else {
                    for (String s : advancementprogress.getRemainingCriteria()) {
                        player.getAdvancements().award(advancement, s);
                    }

                    return true;
                }
            }

            @Override
            protected boolean performCriterion(ServerPlayer player, AdvancementHolder advancement, String criterion) {
                return player.getAdvancements().award(advancement, criterion);
            }
        },
        REVOKE("revoke") {
            @Override
            protected boolean perform(ServerPlayer player, AdvancementHolder advancement) {
                AdvancementProgress advancementprogress = player.getAdvancements().getOrStartProgress(advancement);

                if (!advancementprogress.hasProgress()) {
                    return false;
                } else {
                    for (String s : advancementprogress.getCompletedCriteria()) {
                        player.getAdvancements().revoke(advancement, s);
                    }

                    return true;
                }
            }

            @Override
            protected boolean performCriterion(ServerPlayer player, AdvancementHolder advancement, String criterion) {
                return player.getAdvancements().revoke(advancement, criterion);
            }
        };

        private final String key;

        private Action(String key) {
            this.key = "commands.advancement." + key;
        }

        public int perform(ServerPlayer player, Iterable<AdvancementHolder> advancements, boolean showAdvancements) {
            int i = 0;

            if (!showAdvancements) {
                player.getAdvancements().flushDirty(player, true);
            }

            for (AdvancementHolder advancementholder : advancements) {
                if (this.perform(player, advancementholder)) {
                    ++i;
                }
            }

            if (!showAdvancements) {
                player.getAdvancements().flushDirty(player, false);
            }

            return i;
        }

        protected abstract boolean perform(ServerPlayer player, AdvancementHolder advancement);

        protected abstract boolean performCriterion(ServerPlayer player, AdvancementHolder advancement, String criterion);

        protected String getKey() {
            return this.key;
        }
    }

    private static enum Mode {

        ONLY(false, false), THROUGH(true, true), FROM(false, true), UNTIL(true, false), EVERYTHING(true, true);

        private final boolean parents;
        private final boolean children;

        private Mode(boolean parents, boolean children) {
            this.parents = parents;
            this.children = children;
        }
    }
}
