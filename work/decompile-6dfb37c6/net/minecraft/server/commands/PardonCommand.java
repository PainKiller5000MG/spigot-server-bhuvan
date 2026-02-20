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
import net.minecraft.server.players.UserBanList;

public class PardonCommand {

    private static final SimpleCommandExceptionType ERROR_NOT_BANNED = new SimpleCommandExceptionType(Component.translatable("commands.pardon.failed"));

    public PardonCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("pardon").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))).then(Commands.argument("targets", GameProfileArgument.gameProfile()).suggests((commandcontext, suggestionsbuilder) -> {
            return SharedSuggestionProvider.suggest(((CommandSourceStack) commandcontext.getSource()).getServer().getPlayerList().getBans().getUserList(), suggestionsbuilder);
        }).executes((commandcontext) -> {
            return pardonPlayers((CommandSourceStack) commandcontext.getSource(), GameProfileArgument.getGameProfiles(commandcontext, "targets"));
        })));
    }

    private static int pardonPlayers(CommandSourceStack source, Collection<NameAndId> players) throws CommandSyntaxException {
        UserBanList userbanlist = source.getServer().getPlayerList().getBans();
        int i = 0;

        for (NameAndId nameandid : players) {
            if (userbanlist.isBanned(nameandid)) {
                userbanlist.remove(nameandid);
                ++i;
                source.sendSuccess(() -> {
                    return Component.translatable("commands.pardon.success", Component.literal(nameandid.name()));
                }, true);
            }
        }

        if (i == 0) {
            throw PardonCommand.ERROR_NOT_BANNED.create();
        } else {
            return i;
        }
    }
}
