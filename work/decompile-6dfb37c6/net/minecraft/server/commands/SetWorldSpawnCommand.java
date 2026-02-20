package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.WorldCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec2;

public class SetWorldSpawnCommand {

    public SetWorldSpawnCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("setworldspawn").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).executes((commandcontext) -> {
            return setSpawn((CommandSourceStack) commandcontext.getSource(), BlockPos.containing(((CommandSourceStack) commandcontext.getSource()).getPosition()), WorldCoordinates.ZERO_ROTATION);
        })).then(((RequiredArgumentBuilder) Commands.argument("pos", BlockPosArgument.blockPos()).executes((commandcontext) -> {
            return setSpawn((CommandSourceStack) commandcontext.getSource(), BlockPosArgument.getSpawnablePos(commandcontext, "pos"), WorldCoordinates.ZERO_ROTATION);
        })).then(Commands.argument("rotation", RotationArgument.rotation()).executes((commandcontext) -> {
            return setSpawn((CommandSourceStack) commandcontext.getSource(), BlockPosArgument.getSpawnablePos(commandcontext, "pos"), RotationArgument.getRotation(commandcontext, "rotation"));
        }))));
    }

    private static int setSpawn(CommandSourceStack source, BlockPos pos, Coordinates rotation) {
        ServerLevel serverlevel = source.getLevel();
        Vec2 vec2 = rotation.getRotation(source);
        float f = vec2.y;
        float f1 = vec2.x;
        LevelData.RespawnData leveldata_respawndata = LevelData.RespawnData.of(serverlevel.dimension(), pos, f, f1);

        serverlevel.setRespawnData(leveldata_respawndata);
        source.sendSuccess(() -> {
            return Component.translatable("commands.setworldspawn.success", pos.getX(), pos.getY(), pos.getZ(), leveldata_respawndata.yaw(), leveldata_respawndata.pitch(), serverlevel.dimension().identifier().toString());
        }, true);
        return 1;
    }
}
