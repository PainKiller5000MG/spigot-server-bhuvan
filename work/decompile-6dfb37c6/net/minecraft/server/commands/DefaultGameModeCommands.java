package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;

public class DefaultGameModeCommands {

    public DefaultGameModeCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("defaultgamemode").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.argument("gamemode", GameModeArgument.gameMode()).executes((commandcontext) -> {
            return setMode((CommandSourceStack) commandcontext.getSource(), GameModeArgument.getGameMode(commandcontext, "gamemode"));
        })));
    }

    private static int setMode(CommandSourceStack source, GameType type) {
        MinecraftServer minecraftserver = source.getServer();

        minecraftserver.setDefaultGameType(type);
        int i = minecraftserver.enforceGameTypeForPlayers(minecraftserver.getForcedGameType());

        source.sendSuccess(() -> {
            return Component.translatable("commands.defaultgamemode.success", type.getLongDisplayName());
        }, true);
        return i;
    }
}
