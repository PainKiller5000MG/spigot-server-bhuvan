package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;

public class ListPlayersCommand {

    public ListPlayersCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("list").executes((commandcontext) -> {
            return listPlayers((CommandSourceStack) commandcontext.getSource());
        })).then(Commands.literal("uuids").executes((commandcontext) -> {
            return listPlayersWithUuids((CommandSourceStack) commandcontext.getSource());
        })));
    }

    private static int listPlayers(CommandSourceStack source) {
        return format(source, Player::getDisplayName);
    }

    private static int listPlayersWithUuids(CommandSourceStack source) {
        return format(source, (serverplayer) -> {
            return Component.translatable("commands.list.nameAndId", serverplayer.getName(), Component.translationArg(serverplayer.getGameProfile().id()));
        });
    }

    private static int format(CommandSourceStack source, Function<ServerPlayer, Component> formatter) {
        PlayerList playerlist = source.getServer().getPlayerList();
        List<ServerPlayer> list = playerlist.getPlayers();
        Component component = ComponentUtils.formatList(list, formatter);

        source.sendSuccess(() -> {
            return Component.translatable("commands.list.players", list.size(), playerlist.getMaxPlayers(), component);
        }, false);
        return list.size();
    }
}
