package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public class ExperienceCommand {

    private static final SimpleCommandExceptionType ERROR_SET_POINTS_INVALID = new SimpleCommandExceptionType(Component.translatable("commands.experience.set.points.invalid"));

    public ExperienceCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> literalcommandnode = dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("experience").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.literal("add").then(Commands.argument("target", EntityArgument.players()).then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument("amount", IntegerArgumentType.integer()).executes((commandcontext) -> {
            return addExperience((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "target"), IntegerArgumentType.getInteger(commandcontext, "amount"), ExperienceCommand.Type.POINTS);
        })).then(Commands.literal("points").executes((commandcontext) -> {
            return addExperience((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "target"), IntegerArgumentType.getInteger(commandcontext, "amount"), ExperienceCommand.Type.POINTS);
        }))).then(Commands.literal("levels").executes((commandcontext) -> {
            return addExperience((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "target"), IntegerArgumentType.getInteger(commandcontext, "amount"), ExperienceCommand.Type.LEVELS);
        })))))).then(Commands.literal("set").then(Commands.argument("target", EntityArgument.players()).then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument("amount", IntegerArgumentType.integer(0)).executes((commandcontext) -> {
            return setExperience((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "target"), IntegerArgumentType.getInteger(commandcontext, "amount"), ExperienceCommand.Type.POINTS);
        })).then(Commands.literal("points").executes((commandcontext) -> {
            return setExperience((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "target"), IntegerArgumentType.getInteger(commandcontext, "amount"), ExperienceCommand.Type.POINTS);
        }))).then(Commands.literal("levels").executes((commandcontext) -> {
            return setExperience((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "target"), IntegerArgumentType.getInteger(commandcontext, "amount"), ExperienceCommand.Type.LEVELS);
        })))))).then(Commands.literal("query").then(((RequiredArgumentBuilder) Commands.argument("target", EntityArgument.player()).then(Commands.literal("points").executes((commandcontext) -> {
            return queryExperience((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayer(commandcontext, "target"), ExperienceCommand.Type.POINTS);
        }))).then(Commands.literal("levels").executes((commandcontext) -> {
            return queryExperience((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayer(commandcontext, "target"), ExperienceCommand.Type.LEVELS);
        })))));

        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("xp").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).redirect(literalcommandnode));
    }

    private static int queryExperience(CommandSourceStack source, ServerPlayer target, ExperienceCommand.Type type) {
        int i = type.query.applyAsInt(target);

        source.sendSuccess(() -> {
            return Component.translatable("commands.experience.query." + type.name, target.getDisplayName(), i);
        }, false);
        return i;
    }

    private static int addExperience(CommandSourceStack source, Collection<? extends ServerPlayer> players, int amount, ExperienceCommand.Type type) {
        for (ServerPlayer serverplayer : players) {
            type.add.accept(serverplayer, amount);
        }

        if (players.size() == 1) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.experience.add." + type.name + ".success.single", amount, ((ServerPlayer) players.iterator().next()).getDisplayName());
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.experience.add." + type.name + ".success.multiple", amount, players.size());
            }, true);
        }

        return players.size();
    }

    private static int setExperience(CommandSourceStack source, Collection<? extends ServerPlayer> players, int amount, ExperienceCommand.Type type) throws CommandSyntaxException {
        int j = 0;

        for (ServerPlayer serverplayer : players) {
            if (type.set.test(serverplayer, amount)) {
                ++j;
            }
        }

        if (j == 0) {
            throw ExperienceCommand.ERROR_SET_POINTS_INVALID.create();
        } else {
            if (players.size() == 1) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.experience.set." + type.name + ".success.single", amount, ((ServerPlayer) players.iterator().next()).getDisplayName());
                }, true);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.experience.set." + type.name + ".success.multiple", amount, players.size());
                }, true);
            }

            return players.size();
        }
    }

    private static enum Type {

        POINTS("points", Player::giveExperiencePoints, (serverplayer, integer) -> {
            if (integer >= serverplayer.getXpNeededForNextLevel()) {
                return false;
            } else {
                serverplayer.setExperiencePoints(integer);
                return true;
            }
        }, (serverplayer) -> {
            return Mth.floor(serverplayer.experienceProgress * (float) serverplayer.getXpNeededForNextLevel());
        }), LEVELS("levels", ServerPlayer::giveExperienceLevels, (serverplayer, integer) -> {
            serverplayer.setExperienceLevels(integer);
            return true;
        }, (serverplayer) -> {
            return serverplayer.experienceLevel;
        });

        public final BiConsumer<ServerPlayer, Integer> add;
        public final BiPredicate<ServerPlayer, Integer> set;
        public final String name;
        private final ToIntFunction<ServerPlayer> query;

        private Type(String name, BiConsumer<ServerPlayer, Integer> add, BiPredicate<ServerPlayer, Integer> set, ToIntFunction<ServerPlayer> query) {
            this.add = add;
            this.name = name;
            this.set = set;
            this.query = query;
        }
    }
}
