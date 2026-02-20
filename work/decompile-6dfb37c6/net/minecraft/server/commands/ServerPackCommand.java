package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;

public class ServerPackCommand {

    public ServerPackCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("serverpack").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.literal("push").then(((RequiredArgumentBuilder) Commands.argument("url", StringArgumentType.string()).then(((RequiredArgumentBuilder) Commands.argument("uuid", UuidArgument.uuid()).then(Commands.argument("hash", StringArgumentType.word()).executes((commandcontext) -> {
            return pushPack((CommandSourceStack) commandcontext.getSource(), StringArgumentType.getString(commandcontext, "url"), Optional.of(UuidArgument.getUuid(commandcontext, "uuid")), Optional.of(StringArgumentType.getString(commandcontext, "hash")));
        }))).executes((commandcontext) -> {
            return pushPack((CommandSourceStack) commandcontext.getSource(), StringArgumentType.getString(commandcontext, "url"), Optional.of(UuidArgument.getUuid(commandcontext, "uuid")), Optional.empty());
        }))).executes((commandcontext) -> {
            return pushPack((CommandSourceStack) commandcontext.getSource(), StringArgumentType.getString(commandcontext, "url"), Optional.empty(), Optional.empty());
        })))).then(Commands.literal("pop").then(Commands.argument("uuid", UuidArgument.uuid()).executes((commandcontext) -> {
            return popPack((CommandSourceStack) commandcontext.getSource(), UuidArgument.getUuid(commandcontext, "uuid"));
        }))));
    }

    private static void sendToAllConnections(CommandSourceStack source, Packet<?> packet) {
        source.getServer().getConnection().getConnections().forEach((connection) -> {
            connection.send(packet);
        });
    }

    private static int pushPack(CommandSourceStack source, String url, Optional<UUID> maybeId, Optional<String> maybeHash) {
        UUID uuid = (UUID) maybeId.orElseGet(() -> {
            return UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8));
        });
        String s1 = (String) maybeHash.orElse("");
        ClientboundResourcePackPushPacket clientboundresourcepackpushpacket = new ClientboundResourcePackPushPacket(uuid, url, s1, false, (Optional) null);

        sendToAllConnections(source, clientboundresourcepackpushpacket);
        return 0;
    }

    private static int popPack(CommandSourceStack source, UUID uuid) {
        ClientboundResourcePackPopPacket clientboundresourcepackpoppacket = new ClientboundResourcePackPopPacket(Optional.of(uuid));

        sendToAllConnections(source, clientboundresourcepackpoppacket);
        return 0;
    }
}
