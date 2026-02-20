package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.network.chat.ChatType;
import net.minecraft.server.players.PlayerList;

public class SayCommand {

    public SayCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("say").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.argument("message", MessageArgument.message()).executes((commandcontext) -> {
            MessageArgument.resolveChatMessage(commandcontext, "message", (playerchatmessage) -> {
                CommandSourceStack commandsourcestack = (CommandSourceStack) commandcontext.getSource();
                PlayerList playerlist = commandsourcestack.getServer().getPlayerList();

                playerlist.broadcastChatMessage(playerchatmessage, commandsourcestack, ChatType.bind(ChatType.SAY_COMMAND, commandsourcestack));
            });
            return 1;
        })));
    }
}
