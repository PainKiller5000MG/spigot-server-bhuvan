package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.Collection;
import java.util.Collections;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.WorldCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec2;

public class SetSpawnCommand {

    public SetSpawnCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("spawnpoint").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).executes((commandcontext) -> {
            return setSpawn((CommandSourceStack) commandcontext.getSource(), Collections.singleton(((CommandSourceStack) commandcontext.getSource()).getPlayerOrException()), BlockPos.containing(((CommandSourceStack) commandcontext.getSource()).getPosition()), WorldCoordinates.ZERO_ROTATION);
        })).then(((RequiredArgumentBuilder) Commands.argument("targets", EntityArgument.players()).executes((commandcontext) -> {
            return setSpawn((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), BlockPos.containing(((CommandSourceStack) commandcontext.getSource()).getPosition()), WorldCoordinates.ZERO_ROTATION);
        })).then(((RequiredArgumentBuilder) Commands.argument("pos", BlockPosArgument.blockPos()).executes((commandcontext) -> {
            return setSpawn((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), BlockPosArgument.getSpawnablePos(commandcontext, "pos"), WorldCoordinates.ZERO_ROTATION);
        })).then(Commands.argument("rotation", RotationArgument.rotation()).executes((commandcontext) -> {
            return setSpawn((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), BlockPosArgument.getSpawnablePos(commandcontext, "pos"), RotationArgument.getRotation(commandcontext, "rotation"));
        })))));
    }

    private static int setSpawn(CommandSourceStack source, Collection<ServerPlayer> targets, BlockPos pos, Coordinates rotation) {
        ResourceKey<Level> resourcekey = source.getLevel().dimension();
        Vec2 vec2 = rotation.getRotation(source);
        float f = Mth.wrapDegrees(vec2.y);
        float f1 = Mth.clamp(vec2.x, -90.0F, 90.0F);

        for (ServerPlayer serverplayer : targets) {
            serverplayer.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(resourcekey, pos, f, f1), true), false);
        }

        String s = resourcekey.identifier().toString();

        if (targets.size() == 1) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.spawnpoint.success.single", pos.getX(), pos.getY(), pos.getZ(), f, f1, s, ((ServerPlayer) targets.iterator().next()).getDisplayName());
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.spawnpoint.success.multiple", pos.getX(), pos.getY(), pos.getZ(), f, f1, s, targets.size());
            }, true);
        }

        return targets.size();
    }
}
