package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.function.Predicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import org.jspecify.annotations.Nullable;

public class SetBlockCommand {

    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.setblock.failed"));

    public SetBlockCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        Predicate<BlockInWorld> predicate = (blockinworld) -> {
            return blockinworld.getLevel().isEmptyBlock(blockinworld.getPos());
        };

        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("setblock").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.argument("pos", BlockPosArgument.blockPos()).then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument("block", BlockStateArgument.block(context)).executes((commandcontext) -> {
            return setBlock((CommandSourceStack) commandcontext.getSource(), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), BlockStateArgument.getBlock(commandcontext, "block"), SetBlockCommand.Mode.REPLACE, (Predicate) null, false);
        })).then(Commands.literal("destroy").executes((commandcontext) -> {
            return setBlock((CommandSourceStack) commandcontext.getSource(), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), BlockStateArgument.getBlock(commandcontext, "block"), SetBlockCommand.Mode.DESTROY, (Predicate) null, false);
        }))).then(Commands.literal("keep").executes((commandcontext) -> {
            return setBlock((CommandSourceStack) commandcontext.getSource(), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), BlockStateArgument.getBlock(commandcontext, "block"), SetBlockCommand.Mode.REPLACE, predicate, false);
        }))).then(Commands.literal("replace").executes((commandcontext) -> {
            return setBlock((CommandSourceStack) commandcontext.getSource(), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), BlockStateArgument.getBlock(commandcontext, "block"), SetBlockCommand.Mode.REPLACE, (Predicate) null, false);
        }))).then(Commands.literal("strict").executes((commandcontext) -> {
            return setBlock((CommandSourceStack) commandcontext.getSource(), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), BlockStateArgument.getBlock(commandcontext, "block"), SetBlockCommand.Mode.REPLACE, (Predicate) null, true);
        })))));
    }

    private static int setBlock(CommandSourceStack source, BlockPos pos, BlockInput block, SetBlockCommand.Mode mode, @Nullable Predicate<BlockInWorld> predicate, boolean strict) throws CommandSyntaxException {
        ServerLevel serverlevel = source.getLevel();

        if (serverlevel.isDebug()) {
            throw SetBlockCommand.ERROR_FAILED.create();
        } else if (predicate != null && !predicate.test(new BlockInWorld(serverlevel, pos, true))) {
            throw SetBlockCommand.ERROR_FAILED.create();
        } else {
            boolean flag1;

            if (mode == SetBlockCommand.Mode.DESTROY) {
                serverlevel.destroyBlock(pos, true);
                flag1 = !block.getState().isAir() || !serverlevel.getBlockState(pos).isAir();
            } else {
                flag1 = true;
            }

            BlockState blockstate = serverlevel.getBlockState(pos);

            if (flag1 && !block.place(serverlevel, pos, 2 | (strict ? 816 : 256))) {
                throw SetBlockCommand.ERROR_FAILED.create();
            } else {
                if (!strict) {
                    serverlevel.updateNeighboursOnBlockSet(pos, blockstate);
                }

                source.sendSuccess(() -> {
                    return Component.translatable("commands.setblock.success", pos.getX(), pos.getY(), pos.getZ());
                }, true);
                return 1;
            }
        }
    }

    public static enum Mode {

        REPLACE, DESTROY;

        private Mode() {}
    }
}
