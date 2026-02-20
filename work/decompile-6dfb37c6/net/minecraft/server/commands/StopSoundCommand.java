package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

public class StopSoundCommand {

    public StopSoundCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        RequiredArgumentBuilder<CommandSourceStack, EntitySelector> requiredargumentbuilder = (RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument("targets", EntityArgument.players()).executes((commandcontext) -> {
            return stopSound((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), (SoundSource) null, (Identifier) null);
        })).then(Commands.literal("*").then(Commands.argument("sound", IdentifierArgument.id()).suggests(SuggestionProviders.cast(SuggestionProviders.AVAILABLE_SOUNDS)).executes((commandcontext) -> {
            return stopSound((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), (SoundSource) null, IdentifierArgument.getId(commandcontext, "sound"));
        })));

        for (SoundSource soundsource : SoundSource.values()) {
            requiredargumentbuilder.then(((LiteralArgumentBuilder) Commands.literal(soundsource.getName()).executes((commandcontext) -> {
                return stopSound((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), soundsource, (Identifier) null);
            })).then(Commands.argument("sound", IdentifierArgument.id()).suggests(SuggestionProviders.cast(SuggestionProviders.AVAILABLE_SOUNDS)).executes((commandcontext) -> {
                return stopSound((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), soundsource, IdentifierArgument.getId(commandcontext, "sound"));
            })));
        }

        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("stopsound").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(requiredargumentbuilder));
    }

    private static int stopSound(CommandSourceStack source, Collection<ServerPlayer> targets, @Nullable SoundSource soundSource, @Nullable Identifier sound) {
        ClientboundStopSoundPacket clientboundstopsoundpacket = new ClientboundStopSoundPacket(sound, soundSource);

        for (ServerPlayer serverplayer : targets) {
            serverplayer.connection.send(clientboundstopsoundpacket);
        }

        if (soundSource != null) {
            if (sound != null) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.stopsound.success.source.sound", Component.translationArg(sound), soundSource.getName());
                }, true);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.stopsound.success.source.any", soundSource.getName());
                }, true);
            }
        } else if (sound != null) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.stopsound.success.sourceless.sound", Component.translationArg(sound));
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.stopsound.success.sourceless.any");
            }, true);
        }

        return targets.size();
    }
}
