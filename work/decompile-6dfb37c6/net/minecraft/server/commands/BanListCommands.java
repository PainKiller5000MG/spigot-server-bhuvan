package net.minecraft.server.commands;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.BanListEntry;
import net.minecraft.server.players.PlayerList;

public class BanListCommands {

    public BanListCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("banlist").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))).executes((commandcontext) -> {
            PlayerList playerlist = ((CommandSourceStack) commandcontext.getSource()).getServer().getPlayerList();

            return showList((CommandSourceStack) commandcontext.getSource(), Lists.newArrayList(Iterables.concat(playerlist.getBans().getEntries(), playerlist.getIpBans().getEntries())));
        })).then(Commands.literal("ips").executes((commandcontext) -> {
            return showList((CommandSourceStack) commandcontext.getSource(), ((CommandSourceStack) commandcontext.getSource()).getServer().getPlayerList().getIpBans().getEntries());
        }))).then(Commands.literal("players").executes((commandcontext) -> {
            return showList((CommandSourceStack) commandcontext.getSource(), ((CommandSourceStack) commandcontext.getSource()).getServer().getPlayerList().getBans().getEntries());
        })));
    }

    private static int showList(CommandSourceStack source, Collection<? extends BanListEntry<?>> list) {
        if (list.isEmpty()) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.banlist.none");
            }, false);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.banlist.list", list.size());
            }, false);

            for (BanListEntry<?> banlistentry : list) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.banlist.entry", banlistentry.getDisplayName(), banlistentry.getSource(), banlistentry.getReasonMessage());
                }, false);
            }
        }

        return list.size();
    }
}
