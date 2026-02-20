package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.Collection;
import java.util.Collections;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;

public class GameModeCommand {

    public static final PermissionCheck PERMISSION_CHECK = new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER);

    public GameModeCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("gamemode").requires(Commands.hasPermission(GameModeCommand.PERMISSION_CHECK))).then(((RequiredArgumentBuilder) Commands.argument("gamemode", GameModeArgument.gameMode()).executes((commandcontext) -> {
            return setMode(commandcontext, Collections.singleton(((CommandSourceStack) commandcontext.getSource()).getPlayerOrException()), GameModeArgument.getGameMode(commandcontext, "gamemode"));
        })).then(Commands.argument("target", EntityArgument.players()).executes((commandcontext) -> {
            return setMode(commandcontext, EntityArgument.getPlayers(commandcontext, "target"), GameModeArgument.getGameMode(commandcontext, "gamemode"));
        }))));
    }

    private static void logGamemodeChange(CommandSourceStack source, ServerPlayer target, GameType newType) {
        Component component = Component.translatable("gameMode." + newType.getName());

        if (source.getEntity() == target) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.gamemode.success.self", component);
            }, true);
        } else {
            if ((Boolean) source.getLevel().getGameRules().get(GameRules.SEND_COMMAND_FEEDBACK)) {
                target.sendSystemMessage(Component.translatable("gameMode.changed", component));
            }

            source.sendSuccess(() -> {
                return Component.translatable("commands.gamemode.success.other", target.getDisplayName(), component);
            }, true);
        }

    }

    private static int setMode(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players, GameType type) {
        int i = 0;

        for (ServerPlayer serverplayer : players) {
            if (setGameMode((CommandSourceStack) context.getSource(), serverplayer, type)) {
                ++i;
            }
        }

        return i;
    }

    public static void setGameMode(ServerPlayer player, GameType type) {
        setGameMode(player.createCommandSourceStack(), player, type);
    }

    private static boolean setGameMode(CommandSourceStack source, ServerPlayer player, GameType type) {
        if (player.setGameMode(type)) {
            logGamemodeChange(source, player, type);
            return true;
        } else {
            return false;
        }
    }
}
