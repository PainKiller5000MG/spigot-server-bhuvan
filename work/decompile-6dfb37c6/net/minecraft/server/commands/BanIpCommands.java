package net.minecraft.server.commands;

import com.google.common.net.InetAddresses;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Date;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.IpBanList;
import net.minecraft.server.players.IpBanListEntry;
import org.jspecify.annotations.Nullable;

public class BanIpCommands {

    private static final SimpleCommandExceptionType ERROR_INVALID_IP = new SimpleCommandExceptionType(Component.translatable("commands.banip.invalid"));
    private static final SimpleCommandExceptionType ERROR_ALREADY_BANNED = new SimpleCommandExceptionType(Component.translatable("commands.banip.failed"));

    public BanIpCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("ban-ip").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))).then(((RequiredArgumentBuilder) Commands.argument("target", StringArgumentType.word()).executes((commandcontext) -> {
            return banIpOrName((CommandSourceStack) commandcontext.getSource(), StringArgumentType.getString(commandcontext, "target"), (Component) null);
        })).then(Commands.argument("reason", MessageArgument.message()).executes((commandcontext) -> {
            return banIpOrName((CommandSourceStack) commandcontext.getSource(), StringArgumentType.getString(commandcontext, "target"), MessageArgument.getMessage(commandcontext, "reason"));
        }))));
    }

    private static int banIpOrName(CommandSourceStack source, String target, @Nullable Component reason) throws CommandSyntaxException {
        if (InetAddresses.isInetAddress(target)) {
            return banIp(source, target, reason);
        } else {
            ServerPlayer serverplayer = source.getServer().getPlayerList().getPlayerByName(target);

            if (serverplayer != null) {
                return banIp(source, serverplayer.getIpAddress(), reason);
            } else {
                throw BanIpCommands.ERROR_INVALID_IP.create();
            }
        }
    }

    private static int banIp(CommandSourceStack source, String ip, @Nullable Component reason) throws CommandSyntaxException {
        IpBanList ipbanlist = source.getServer().getPlayerList().getIpBans();

        if (ipbanlist.isBanned(ip)) {
            throw BanIpCommands.ERROR_ALREADY_BANNED.create();
        } else {
            List<ServerPlayer> list = source.getServer().getPlayerList().getPlayersWithAddress(ip);
            IpBanListEntry ipbanlistentry = new IpBanListEntry(ip, (Date) null, source.getTextName(), (Date) null, reason == null ? null : reason.getString());

            ipbanlist.add(ipbanlistentry);
            source.sendSuccess(() -> {
                return Component.translatable("commands.banip.success", ip, ipbanlistentry.getReasonMessage());
            }, true);
            if (!list.isEmpty()) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.banip.info", list.size(), EntitySelector.joinNames(list));
                }, true);
            }

            for (ServerPlayer serverplayer : list) {
                serverplayer.connection.disconnect((Component) Component.translatable("multiplayer.disconnect.ip_banned"));
            }

            return list.size();
        }
    }
}
