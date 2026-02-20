package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class PlaySoundCommand {

    private static final SimpleCommandExceptionType ERROR_TOO_FAR = new SimpleCommandExceptionType(Component.translatable("commands.playsound.failed"));

    public PlaySoundCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        RequiredArgumentBuilder<CommandSourceStack, Identifier> requiredargumentbuilder = (RequiredArgumentBuilder) Commands.argument("sound", IdentifierArgument.id()).suggests(SuggestionProviders.cast(SuggestionProviders.AVAILABLE_SOUNDS)).executes((commandcontext) -> {
            return playSound((CommandSourceStack) commandcontext.getSource(), getCallingPlayerAsCollection(((CommandSourceStack) commandcontext.getSource()).getPlayer()), IdentifierArgument.getId(commandcontext, "sound"), SoundSource.MASTER, ((CommandSourceStack) commandcontext.getSource()).getPosition(), 1.0F, 1.0F, 0.0F);
        });

        for (SoundSource soundsource : SoundSource.values()) {
            requiredargumentbuilder.then(source(soundsource));
        }

        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("playsound").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(requiredargumentbuilder));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> source(SoundSource source) {
        return (LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal(source.getName()).executes((commandcontext) -> {
            return playSound((CommandSourceStack) commandcontext.getSource(), getCallingPlayerAsCollection(((CommandSourceStack) commandcontext.getSource()).getPlayer()), IdentifierArgument.getId(commandcontext, "sound"), source, ((CommandSourceStack) commandcontext.getSource()).getPosition(), 1.0F, 1.0F, 0.0F);
        })).then(((RequiredArgumentBuilder) Commands.argument("targets", EntityArgument.players()).executes((commandcontext) -> {
            return playSound((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), IdentifierArgument.getId(commandcontext, "sound"), source, ((CommandSourceStack) commandcontext.getSource()).getPosition(), 1.0F, 1.0F, 0.0F);
        })).then(((RequiredArgumentBuilder) Commands.argument("pos", Vec3Argument.vec3()).executes((commandcontext) -> {
            return playSound((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), IdentifierArgument.getId(commandcontext, "sound"), source, Vec3Argument.getVec3(commandcontext, "pos"), 1.0F, 1.0F, 0.0F);
        })).then(((RequiredArgumentBuilder) Commands.argument("volume", FloatArgumentType.floatArg(0.0F)).executes((commandcontext) -> {
            return playSound((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), IdentifierArgument.getId(commandcontext, "sound"), source, Vec3Argument.getVec3(commandcontext, "pos"), (Float) commandcontext.getArgument("volume", Float.class), 1.0F, 0.0F);
        })).then(((RequiredArgumentBuilder) Commands.argument("pitch", FloatArgumentType.floatArg(0.0F, 2.0F)).executes((commandcontext) -> {
            return playSound((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), IdentifierArgument.getId(commandcontext, "sound"), source, Vec3Argument.getVec3(commandcontext, "pos"), (Float) commandcontext.getArgument("volume", Float.class), (Float) commandcontext.getArgument("pitch", Float.class), 0.0F);
        })).then(Commands.argument("minVolume", FloatArgumentType.floatArg(0.0F, 1.0F)).executes((commandcontext) -> {
            return playSound((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), IdentifierArgument.getId(commandcontext, "sound"), source, Vec3Argument.getVec3(commandcontext, "pos"), (Float) commandcontext.getArgument("volume", Float.class), (Float) commandcontext.getArgument("pitch", Float.class), (Float) commandcontext.getArgument("minVolume", Float.class));
        }))))));
    }

    private static Collection<ServerPlayer> getCallingPlayerAsCollection(@Nullable ServerPlayer player) {
        return player != null ? List.of(player) : List.of();
    }

    private static int playSound(CommandSourceStack source, Collection<ServerPlayer> players, Identifier sound, SoundSource soundSource, Vec3 position, float volume, float pitch, float minVolume) throws CommandSyntaxException {
        Holder<SoundEvent> holder = Holder.<SoundEvent>direct(SoundEvent.createVariableRangeEvent(sound));
        double d0 = (double) Mth.square(((SoundEvent) holder.value()).getRange(volume));
        ServerLevel serverlevel = source.getLevel();
        long i = serverlevel.getRandom().nextLong();
        List<ServerPlayer> list = new ArrayList();

        for (ServerPlayer serverplayer : players) {
            if (serverplayer.level() == serverlevel) {
                double d1 = position.x - serverplayer.getX();
                double d2 = position.y - serverplayer.getY();
                double d3 = position.z - serverplayer.getZ();
                double d4 = d1 * d1 + d2 * d2 + d3 * d3;
                Vec3 vec31 = position;
                float f3 = volume;

                if (d4 > d0) {
                    if (minVolume <= 0.0F) {
                        continue;
                    }

                    double d5 = Math.sqrt(d4);

                    vec31 = new Vec3(serverplayer.getX() + d1 / d5 * 2.0D, serverplayer.getY() + d2 / d5 * 2.0D, serverplayer.getZ() + d3 / d5 * 2.0D);
                    f3 = minVolume;
                }

                serverplayer.connection.send(new ClientboundSoundPacket(holder, soundSource, vec31.x(), vec31.y(), vec31.z(), f3, pitch, i));
                list.add(serverplayer);
            }
        }

        int j = list.size();

        if (j == 0) {
            throw PlaySoundCommand.ERROR_TOO_FAR.create();
        } else {
            if (j == 1) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.playsound.success.single", Component.translationArg(sound), ((ServerPlayer) list.getFirst()).getDisplayName());
                }, true);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.playsound.success.multiple", Component.translationArg(sound), j);
                }, true);
            }

            return j;
        }
    }
}
