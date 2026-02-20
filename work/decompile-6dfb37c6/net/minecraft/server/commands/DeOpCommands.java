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

public class DeOpCommands {

    private static final SimpleCommandExceptionType ERROR_NOT_OP = new SimpleCommandExceptionType(Component.translatable("commands.deop.failed"));

    public DeOpCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("deop").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))).then(Commands.argument("targets", GameProfileArgument.gameProfile()).suggests((commandcontext, suggestionsbuilder) -> {
            return SharedSuggestionProvider.suggest(((CommandSourceStack) commandcontext.getSource()).getServer().getPlayerList().getOpNames(), suggestionsbuilder);
        }).executes((commandcontext) -> {
            return deopPlayers((CommandSourceStack) commandcontext.getSource(), GameProfileArgument.getGameProfiles(commandcontext, "targets"));
        })));
    }

    private static int deopPlayers(CommandSourceStack source, Collection<NameAndId> players) throws CommandSyntaxException {
        PlayerList playerlist = source.getServer().getPlayerList();
        int i = 0;

        for (NameAndId nameandid : players) {
            if (playerlist.isOp(nameandid)) {
                playerlist.deop(nameandid);
                ++i;
                source.sendSuccess(() -> {
                    return Component.translatable("commands.deop.success", ((NameAndId) players.iterator().next()).name());
                }, true);
            }
        }

        if (i == 0) {
            throw DeOpCommands.ERROR_NOT_OP.create();
        } else {
            source.getServer().kickUnlistedPlayers();
            return i;
        }
    }
}
