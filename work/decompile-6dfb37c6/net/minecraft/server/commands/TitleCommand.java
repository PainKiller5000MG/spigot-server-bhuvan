package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.function.Function;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

public class TitleCommand {

    public TitleCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("title").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument("targets", EntityArgument.players()).then(Commands.literal("clear").executes((commandcontext) -> {
            return clearTitle((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"));
        }))).then(Commands.literal("reset").executes((commandcontext) -> {
            return resetTitle((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"));
        }))).then(Commands.literal("title").then(Commands.argument("title", ComponentArgument.textComponent(context)).executes((commandcontext) -> {
            return showTitle((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), ComponentArgument.getRawComponent(commandcontext, "title"), "title", ClientboundSetTitleTextPacket::new);
        })))).then(Commands.literal("subtitle").then(Commands.argument("title", ComponentArgument.textComponent(context)).executes((commandcontext) -> {
            return showTitle((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), ComponentArgument.getRawComponent(commandcontext, "title"), "subtitle", ClientboundSetSubtitleTextPacket::new);
        })))).then(Commands.literal("actionbar").then(Commands.argument("title", ComponentArgument.textComponent(context)).executes((commandcontext) -> {
            return showTitle((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), ComponentArgument.getRawComponent(commandcontext, "title"), "actionbar", ClientboundSetActionBarTextPacket::new);
        })))).then(Commands.literal("times").then(Commands.argument("fadeIn", TimeArgument.time()).then(Commands.argument("stay", TimeArgument.time()).then(Commands.argument("fadeOut", TimeArgument.time()).executes((commandcontext) -> {
            return setTimes((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), IntegerArgumentType.getInteger(commandcontext, "fadeIn"), IntegerArgumentType.getInteger(commandcontext, "stay"), IntegerArgumentType.getInteger(commandcontext, "fadeOut"));
        })))))));
    }

    private static int clearTitle(CommandSourceStack source, Collection<ServerPlayer> targets) {
        ClientboundClearTitlesPacket clientboundcleartitlespacket = new ClientboundClearTitlesPacket(false);

        for (ServerPlayer serverplayer : targets) {
            serverplayer.connection.send(clientboundcleartitlespacket);
        }

        if (targets.size() == 1) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.title.cleared.single", ((ServerPlayer) targets.iterator().next()).getDisplayName());
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.title.cleared.multiple", targets.size());
            }, true);
        }

        return targets.size();
    }

    private static int resetTitle(CommandSourceStack source, Collection<ServerPlayer> targets) {
        ClientboundClearTitlesPacket clientboundcleartitlespacket = new ClientboundClearTitlesPacket(true);

        for (ServerPlayer serverplayer : targets) {
            serverplayer.connection.send(clientboundcleartitlespacket);
        }

        if (targets.size() == 1) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.title.reset.single", ((ServerPlayer) targets.iterator().next()).getDisplayName());
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.title.reset.multiple", targets.size());
            }, true);
        }

        return targets.size();
    }

    private static int showTitle(CommandSourceStack source, Collection<ServerPlayer> targets, Component title, String type, Function<Component, Packet<?>> factory) throws CommandSyntaxException {
        for (ServerPlayer serverplayer : targets) {
            serverplayer.connection.send((Packet) factory.apply(ComponentUtils.updateForEntity(source, title, serverplayer, 0)));
        }

        if (targets.size() == 1) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.title.show." + type + ".single", ((ServerPlayer) targets.iterator().next()).getDisplayName());
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.title.show." + type + ".multiple", targets.size());
            }, true);
        }

        return targets.size();
    }

    private static int setTimes(CommandSourceStack source, Collection<ServerPlayer> targets, int fadeIn, int stay, int fadeOut) {
        ClientboundSetTitlesAnimationPacket clientboundsettitlesanimationpacket = new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut);

        for (ServerPlayer serverplayer : targets) {
            serverplayer.connection.send(clientboundsettitlesanimationpacket);
        }

        if (targets.size() == 1) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.title.times.single", ((ServerPlayer) targets.iterator().next()).getDisplayName());
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.title.times.multiple", targets.size());
            }, true);
        }

        return targets.size();
    }
}
