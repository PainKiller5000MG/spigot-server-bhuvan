package net.minecraft.server.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceOrIdArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.Holder;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.jspecify.annotations.Nullable;

public class DebugConfigCommand {

    public DebugConfigCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("debugconfig").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))).then(Commands.literal("config").then(Commands.argument("target", EntityArgument.player()).executes((commandcontext) -> {
            return config((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayer(commandcontext, "target"));
        })))).then(Commands.literal("unconfig").then(Commands.argument("target", UuidArgument.uuid()).suggests((commandcontext, suggestionsbuilder) -> {
            return SharedSuggestionProvider.suggest(getUuidsInConfig(((CommandSourceStack) commandcontext.getSource()).getServer()), suggestionsbuilder);
        }).executes((commandcontext) -> {
            return unconfig((CommandSourceStack) commandcontext.getSource(), UuidArgument.getUuid(commandcontext, "target"));
        })))).then(Commands.literal("dialog").then(Commands.argument("target", UuidArgument.uuid()).suggests((commandcontext, suggestionsbuilder) -> {
            return SharedSuggestionProvider.suggest(getUuidsInConfig(((CommandSourceStack) commandcontext.getSource()).getServer()), suggestionsbuilder);
        }).then(Commands.argument("dialog", ResourceOrIdArgument.dialog(context)).executes((commandcontext) -> {
            return showDialog((CommandSourceStack) commandcontext.getSource(), UuidArgument.getUuid(commandcontext, "target"), ResourceOrIdArgument.getDialog(commandcontext, "dialog"));
        })))));
    }

    private static Iterable<String> getUuidsInConfig(MinecraftServer server) {
        Set<String> set = new HashSet();

        for (Connection connection : server.getConnection().getConnections()) {
            PacketListener packetlistener = connection.getPacketListener();

            if (packetlistener instanceof ServerConfigurationPacketListenerImpl serverconfigurationpacketlistenerimpl) {
                set.add(serverconfigurationpacketlistenerimpl.getOwner().id().toString());
            }
        }

        return set;
    }

    private static int config(CommandSourceStack source, ServerPlayer target) {
        GameProfile gameprofile = target.getGameProfile();

        target.connection.switchToConfig();
        source.sendSuccess(() -> {
            String s = gameprofile.name();

            return Component.literal("Switched player " + s + "(" + String.valueOf(gameprofile.id()) + ") to config mode");
        }, false);
        return 1;
    }

    private static @Nullable ServerConfigurationPacketListenerImpl findConfigPlayer(MinecraftServer server, UUID target) {
        for (Connection connection : server.getConnection().getConnections()) {
            PacketListener packetlistener = connection.getPacketListener();

            if (packetlistener instanceof ServerConfigurationPacketListenerImpl serverconfigurationpacketlistenerimpl) {
                if (serverconfigurationpacketlistenerimpl.getOwner().id().equals(target)) {
                    return serverconfigurationpacketlistenerimpl;
                }
            }
        }

        return null;
    }

    private static int unconfig(CommandSourceStack source, UUID target) {
        ServerConfigurationPacketListenerImpl serverconfigurationpacketlistenerimpl = findConfigPlayer(source.getServer(), target);

        if (serverconfigurationpacketlistenerimpl != null) {
            serverconfigurationpacketlistenerimpl.returnToWorld();
            return 1;
        } else {
            source.sendFailure(Component.literal("Can't find player to unconfig"));
            return 0;
        }
    }

    private static int showDialog(CommandSourceStack source, UUID target, Holder<Dialog> dialog) {
        ServerConfigurationPacketListenerImpl serverconfigurationpacketlistenerimpl = findConfigPlayer(source.getServer(), target);

        if (serverconfigurationpacketlistenerimpl != null) {
            serverconfigurationpacketlistenerimpl.send(new ClientboundShowDialogPacket(dialog));
            return 1;
        } else {
            source.sendFailure(Component.literal("Can't find player to talk to"));
            return 0;
        }
    }
}
