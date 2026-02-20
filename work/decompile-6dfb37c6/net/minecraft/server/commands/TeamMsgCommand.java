package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;

public class TeamMsgCommand {

    private static final Style SUGGEST_STYLE = Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.type.team.hover"))).withClickEvent(new ClickEvent.SuggestCommand("/teammsg "));
    private static final SimpleCommandExceptionType ERROR_NOT_ON_TEAM = new SimpleCommandExceptionType(Component.translatable("commands.teammsg.failed.noteam"));

    public TeamMsgCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> literalcommandnode = dispatcher.register((LiteralArgumentBuilder) Commands.literal("teammsg").then(Commands.argument("message", MessageArgument.message()).executes((commandcontext) -> {
            CommandSourceStack commandsourcestack = (CommandSourceStack) commandcontext.getSource();
            Entity entity = commandsourcestack.getEntityOrException();
            PlayerTeam playerteam = entity.getTeam();

            if (playerteam == null) {
                throw TeamMsgCommand.ERROR_NOT_ON_TEAM.create();
            } else {
                List<ServerPlayer> list = commandsourcestack.getServer().getPlayerList().getPlayers().stream().filter((serverplayer) -> {
                    return serverplayer == entity || serverplayer.getTeam() == playerteam;
                }).toList();

                if (!list.isEmpty()) {
                    MessageArgument.resolveChatMessage(commandcontext, "message", (playerchatmessage) -> {
                        sendMessage(commandsourcestack, entity, playerteam, list, playerchatmessage);
                    });
                }

                return list.size();
            }
        })));

        dispatcher.register((LiteralArgumentBuilder) Commands.literal("tm").redirect(literalcommandnode));
    }

    private static void sendMessage(CommandSourceStack source, Entity entity, PlayerTeam team, List<ServerPlayer> receivers, PlayerChatMessage message) {
        Component component = team.getFormattedDisplayName().withStyle(TeamMsgCommand.SUGGEST_STYLE);
        ChatType.Bound chattype_bound = ChatType.bind(ChatType.TEAM_MSG_COMMAND_INCOMING, source).withTargetName(component);
        ChatType.Bound chattype_bound1 = ChatType.bind(ChatType.TEAM_MSG_COMMAND_OUTGOING, source).withTargetName(component);
        OutgoingChatMessage outgoingchatmessage = OutgoingChatMessage.create(message);
        boolean flag = false;

        for (ServerPlayer serverplayer : receivers) {
            ChatType.Bound chattype_bound2 = serverplayer == entity ? chattype_bound1 : chattype_bound;
            boolean flag1 = source.shouldFilterMessageTo(serverplayer);

            serverplayer.sendChatMessage(outgoingchatmessage, flag1, chattype_bound2);
            flag |= flag1 && message.isFullyFiltered();
        }

        if (flag) {
            source.sendSystemMessage(PlayerList.CHAT_FILTERED_FULL);
        }

    }
}
