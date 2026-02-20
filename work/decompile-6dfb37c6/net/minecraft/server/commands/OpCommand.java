package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;

public class OpCommand {

    private static final SimpleCommandExceptionType ERROR_ALREADY_OP = new SimpleCommandExceptionType(Component.translatable("commands.op.failed"));

    public OpCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("op").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))).then(Commands.argument("targets", GameProfileArgument.gameProfile()).suggests((commandcontext, suggestionsbuilder) -> {
            PlayerList playerlist = ((CommandSourceStack) commandcontext.getSource()).getServer().getPlayerList();

            return SharedSuggestionProvider.suggest(playerlist.getPlayers().stream().filter((serverplayer) -> {
                return !playerlist.isOp(serverplayer.nameAndId());
            }).map((serverplayer) -> {
                return serverplayer.getGameProfile().name();
            }), suggestionsbuilder);
        }).executes((commandcontext) -> {
            return opPlayers((CommandSourceStack) commandcontext.getSource(), GameProfileArgument.getGameProfiles(commandcontext, "targets"));
        })));
    }

    private static int opPlayers(CommandSourceStack source, Collection<NameAndId> players) throws CommandSyntaxException {
        PlayerList playerlist = source.getServer().getPlayerList();
        int i = 0;

        for (NameAndId nameandid : players) {
            if (!playerlist.isOp(nameandid)) {
                playerlist.op(nameandid);
                ++i;
                source.sendSuccess(() -> {
                    return Component.translatable("commands.op.success", nameandid.name());
                }, true);
            }
        }

        if (i == 0) {
            throw OpCommand.ERROR_ALREADY_OP.create();
        } else {
            return i;
        }
    }
}
