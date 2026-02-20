package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class ParticleCommand {

    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.particle.failed"));

    public ParticleCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("particle").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(((RequiredArgumentBuilder) Commands.argument("name", ParticleArgument.particle(context)).executes((commandcontext) -> {
            return sendParticles((CommandSourceStack) commandcontext.getSource(), ParticleArgument.getParticle(commandcontext, "name"), ((CommandSourceStack) commandcontext.getSource()).getPosition(), Vec3.ZERO, 0.0F, 0, false, ((CommandSourceStack) commandcontext.getSource()).getServer().getPlayerList().getPlayers());
        })).then(((RequiredArgumentBuilder) Commands.argument("pos", Vec3Argument.vec3()).executes((commandcontext) -> {
            return sendParticles((CommandSourceStack) commandcontext.getSource(), ParticleArgument.getParticle(commandcontext, "name"), Vec3Argument.getVec3(commandcontext, "pos"), Vec3.ZERO, 0.0F, 0, false, ((CommandSourceStack) commandcontext.getSource()).getServer().getPlayerList().getPlayers());
        })).then(Commands.argument("delta", Vec3Argument.vec3(false)).then(Commands.argument("speed", FloatArgumentType.floatArg(0.0F)).then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument("count", IntegerArgumentType.integer(0)).executes((commandcontext) -> {
            return sendParticles((CommandSourceStack) commandcontext.getSource(), ParticleArgument.getParticle(commandcontext, "name"), Vec3Argument.getVec3(commandcontext, "pos"), Vec3Argument.getVec3(commandcontext, "delta"), FloatArgumentType.getFloat(commandcontext, "speed"), IntegerArgumentType.getInteger(commandcontext, "count"), false, ((CommandSourceStack) commandcontext.getSource()).getServer().getPlayerList().getPlayers());
        })).then(((LiteralArgumentBuilder) Commands.literal("force").executes((commandcontext) -> {
            return sendParticles((CommandSourceStack) commandcontext.getSource(), ParticleArgument.getParticle(commandcontext, "name"), Vec3Argument.getVec3(commandcontext, "pos"), Vec3Argument.getVec3(commandcontext, "delta"), FloatArgumentType.getFloat(commandcontext, "speed"), IntegerArgumentType.getInteger(commandcontext, "count"), true, ((CommandSourceStack) commandcontext.getSource()).getServer().getPlayerList().getPlayers());
        })).then(Commands.argument("viewers", EntityArgument.players()).executes((commandcontext) -> {
            return sendParticles((CommandSourceStack) commandcontext.getSource(), ParticleArgument.getParticle(commandcontext, "name"), Vec3Argument.getVec3(commandcontext, "pos"), Vec3Argument.getVec3(commandcontext, "delta"), FloatArgumentType.getFloat(commandcontext, "speed"), IntegerArgumentType.getInteger(commandcontext, "count"), true, EntityArgument.getPlayers(commandcontext, "viewers"));
        })))).then(((LiteralArgumentBuilder) Commands.literal("normal").executes((commandcontext) -> {
            return sendParticles((CommandSourceStack) commandcontext.getSource(), ParticleArgument.getParticle(commandcontext, "name"), Vec3Argument.getVec3(commandcontext, "pos"), Vec3Argument.getVec3(commandcontext, "delta"), FloatArgumentType.getFloat(commandcontext, "speed"), IntegerArgumentType.getInteger(commandcontext, "count"), false, ((CommandSourceStack) commandcontext.getSource()).getServer().getPlayerList().getPlayers());
        })).then(Commands.argument("viewers", EntityArgument.players()).executes((commandcontext) -> {
            return sendParticles((CommandSourceStack) commandcontext.getSource(), ParticleArgument.getParticle(commandcontext, "name"), Vec3Argument.getVec3(commandcontext, "pos"), Vec3Argument.getVec3(commandcontext, "delta"), FloatArgumentType.getFloat(commandcontext, "speed"), IntegerArgumentType.getInteger(commandcontext, "count"), false, EntityArgument.getPlayers(commandcontext, "viewers"));
        })))))))));
    }

    private static int sendParticles(CommandSourceStack source, ParticleOptions particle, Vec3 pos, Vec3 delta, float speed, int count, boolean force, Collection<ServerPlayer> players) throws CommandSyntaxException {
        int j = 0;

        for (ServerPlayer serverplayer : players) {
            if (source.getLevel().sendParticles(serverplayer, particle, force, false, pos.x, pos.y, pos.z, count, delta.x, delta.y, delta.z, (double) speed)) {
                ++j;
            }
        }

        if (j == 0) {
            throw ParticleCommand.ERROR_FAILED.create();
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.particle.success", BuiltInRegistries.PARTICLE_TYPE.getKey(particle.getType()).toString());
            }, true);
            return j;
        }
    }
}
