package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import java.util.Date;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import org.jspecify.annotations.Nullable;

public class BanPlayerCommands {

    private static final SimpleCommandExceptionType ERROR_ALREADY_BANNED = new SimpleCommandExceptionType(Component.translatable("commands.ban.failed"));

    public BanPlayerCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("ban").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))).then(((RequiredArgumentBuilder) Commands.argument("targets", GameProfileArgument.gameProfile()).executes((commandcontext) -> {
            return banPlayers((CommandSourceStack) commandcontext.getSource(), GameProfileArgument.getGameProfiles(commandcontext, "targets"), (Component) null);
        })).then(Commands.argument("reason", MessageArgument.message()).executes((commandcontext) -> {
            return banPlayers((CommandSourceStack) commandcontext.getSource(), GameProfileArgument.getGameProfiles(commandcontext, "targets"), MessageArgument.getMessage(commandcontext, "reason"));
        }))));
    }

    private static int banPlayers(CommandSourceStack source, Collection<NameAndId> players, @Nullable Component reason) throws CommandSyntaxException {
        UserBanList userbanlist = source.getServer().getPlayerList().getBans();
        int i = 0;

        for (NameAndId nameandid : players) {
            if (!userbanlist.isBanned(nameandid)) {
                UserBanListEntry userbanlistentry = new UserBanListEntry(nameandid, (Date) null, source.getTextName(), (Date) null, reason == null ? null : reason.getString());

                userbanlist.add(userbanlistentry);
                ++i;
                source.sendSuccess(() -> {
                    return Component.translatable("commands.ban.success", Component.literal(nameandid.name()), userbanlistentry.getReasonMessage());
                }, true);
                ServerPlayer serverplayer = source.getServer().getPlayerList().getPlayer(nameandid.id());

                if (serverplayer != null) {
                    serverplayer.connection.disconnect((Component) Component.translatable("multiplayer.disconnect.banned"));
                }
            }
        }

        if (i == 0) {
            throw BanPlayerCommands.ERROR_ALREADY_BANNED.create();
        } else {
            return i;
        }
    }
}
