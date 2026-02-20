package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class KickCommand {

    private static final SimpleCommandExceptionType ERROR_KICKING_OWNER = new SimpleCommandExceptionType(Component.translatable("commands.kick.owner.failed"));
    private static final SimpleCommandExceptionType ERROR_SINGLEPLAYER = new SimpleCommandExceptionType(Component.translatable("commands.kick.singleplayer.failed"));

    public KickCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("kick").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))).then(((RequiredArgumentBuilder) Commands.argument("targets", EntityArgument.players()).executes((commandcontext) -> {
            return kickPlayers((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), Component.translatable("multiplayer.disconnect.kicked"));
        })).then(Commands.argument("reason", MessageArgument.message()).executes((commandcontext) -> {
            return kickPlayers((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), MessageArgument.getMessage(commandcontext, "reason"));
        }))));
    }

    private static int kickPlayers(CommandSourceStack source, Collection<ServerPlayer> players, Component reason) throws CommandSyntaxException {
        if (!source.getServer().isPublished()) {
            throw KickCommand.ERROR_SINGLEPLAYER.create();
        } else {
            int i = 0;

            for (ServerPlayer serverplayer : players) {
                if (!source.getServer().isSingleplayerOwner(serverplayer.nameAndId())) {
                    serverplayer.connection.disconnect(reason);
                    source.sendSuccess(() -> {
                        return Component.translatable("commands.kick.success", serverplayer.getDisplayName(), reason);
                    }, true);
                    ++i;
                }
            }

            if (i == 0) {
                throw KickCommand.ERROR_KICKING_OWNER.create();
            } else {
                return i;
            }
        }
    }
}
