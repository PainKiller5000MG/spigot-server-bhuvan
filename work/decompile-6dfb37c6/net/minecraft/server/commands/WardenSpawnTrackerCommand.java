package net.minecraft.server.commands;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import net.minecraft.world.entity.player.Player;

public class WardenSpawnTrackerCommand {

    public WardenSpawnTrackerCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("warden_spawn_tracker").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.literal("clear").executes((commandcontext) -> {
            return resetTracker((CommandSourceStack) commandcontext.getSource(), ImmutableList.of(((CommandSourceStack) commandcontext.getSource()).getPlayerOrException()));
        }))).then(Commands.literal("set").then(Commands.argument("warning_level", IntegerArgumentType.integer(0, 4)).executes((commandcontext) -> {
            return setWarningLevel((CommandSourceStack) commandcontext.getSource(), ImmutableList.of(((CommandSourceStack) commandcontext.getSource()).getPlayerOrException()), IntegerArgumentType.getInteger(commandcontext, "warning_level"));
        }))));
    }

    private static int setWarningLevel(CommandSourceStack source, Collection<? extends Player> players, int warningLevel) {
        for (Player player : players) {
            player.getWardenSpawnTracker().ifPresent((wardenspawntracker) -> {
                wardenspawntracker.setWarningLevel(warningLevel);
            });
        }

        if (players.size() == 1) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.warden_spawn_tracker.set.success.single", ((Player) players.iterator().next()).getDisplayName());
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.warden_spawn_tracker.set.success.multiple", players.size());
            }, true);
        }

        return players.size();
    }

    private static int resetTracker(CommandSourceStack source, Collection<? extends Player> players) {
        for (Player player : players) {
            player.getWardenSpawnTracker().ifPresent(WardenSpawnTracker::reset);
        }

        if (players.size() == 1) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.warden_spawn_tracker.clear.success.single", ((Player) players.iterator().next()).getDisplayName());
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.warden_spawn_tracker.clear.success.multiple", players.size());
            }, true);
        }

        return players.size();
    }
}
