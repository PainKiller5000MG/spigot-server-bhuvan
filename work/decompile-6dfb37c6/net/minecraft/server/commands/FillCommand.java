package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

public class FillCommand {

    private static final Dynamic2CommandExceptionType ERROR_AREA_TOO_LARGE = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("commands.fill.toobig", object, object1);
    });
    private static final BlockInput HOLLOW_CORE = new BlockInput(Blocks.AIR.defaultBlockState(), Collections.emptySet(), (CompoundTag) null);
    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.fill.failed"));

    public FillCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("fill").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.argument("from", BlockPosArgument.blockPos()).then(Commands.argument("to", BlockPosArgument.blockPos()).then(wrapWithMode(context, Commands.argument("block", BlockStateArgument.block(context)), (commandcontext) -> {
            return BlockPosArgument.getLoadedBlockPos(commandcontext, "from");
        }, (commandcontext) -> {
            return BlockPosArgument.getLoadedBlockPos(commandcontext, "to");
        }, (commandcontext) -> {
            return BlockStateArgument.getBlock(commandcontext, "block");
        }, (commandcontext) -> {
            return null;
        }).then(((LiteralArgumentBuilder) Commands.literal("replace").executes((commandcontext) -> {
            return fillBlocks((CommandSourceStack) commandcontext.getSource(), BoundingBox.fromCorners(BlockPosArgument.getLoadedBlockPos(commandcontext, "from"), BlockPosArgument.getLoadedBlockPos(commandcontext, "to")), BlockStateArgument.getBlock(commandcontext, "block"), FillCommand.Mode.REPLACE, (Predicate) null, false);
        })).then(wrapWithMode(context, Commands.argument("filter", BlockPredicateArgument.blockPredicate(context)), (commandcontext) -> {
            return BlockPosArgument.getLoadedBlockPos(commandcontext, "from");
        }, (commandcontext) -> {
            return BlockPosArgument.getLoadedBlockPos(commandcontext, "to");
        }, (commandcontext) -> {
            return BlockStateArgument.getBlock(commandcontext, "block");
        }, (commandcontext) -> {
            return BlockPredicateArgument.getBlockPredicate(commandcontext, "filter");
        }))).then(Commands.literal("keep").executes((commandcontext) -> {
            return fillBlocks((CommandSourceStack) commandcontext.getSource(), BoundingBox.fromCorners(BlockPosArgument.getLoadedBlockPos(commandcontext, "from"), BlockPosArgument.getLoadedBlockPos(commandcontext, "to")), BlockStateArgument.getBlock(commandcontext, "block"), FillCommand.Mode.REPLACE, (blockinworld) -> {
                return blockinworld.getLevel().isEmptyBlock(blockinworld.getPos());
            }, false);
        }))))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> wrapWithMode(CommandBuildContext context, ArgumentBuilder<CommandSourceStack, ?> builder, InCommandFunction<CommandContext<CommandSourceStack>, BlockPos> from, InCommandFunction<CommandContext<CommandSourceStack>, BlockPos> to, InCommandFunction<CommandContext<CommandSourceStack>, BlockInput> block, FillCommand.NullableCommandFunction<CommandContext<CommandSourceStack>, Predicate<BlockInWorld>> filter) {
        return builder.executes((commandcontext) -> {
            return fillBlocks((CommandSourceStack) commandcontext.getSource(), BoundingBox.fromCorners(from.apply(commandcontext), to.apply(commandcontext)), block.apply(commandcontext), FillCommand.Mode.REPLACE, filter.apply(commandcontext), false);
        }).then(Commands.literal("outline").executes((commandcontext) -> {
            return fillBlocks((CommandSourceStack) commandcontext.getSource(), BoundingBox.fromCorners(from.apply(commandcontext), to.apply(commandcontext)), block.apply(commandcontext), FillCommand.Mode.OUTLINE, filter.apply(commandcontext), false);
        })).then(Commands.literal("hollow").executes((commandcontext) -> {
            return fillBlocks((CommandSourceStack) commandcontext.getSource(), BoundingBox.fromCorners(from.apply(commandcontext), to.apply(commandcontext)), block.apply(commandcontext), FillCommand.Mode.HOLLOW, filter.apply(commandcontext), false);
        })).then(Commands.literal("destroy").executes((commandcontext) -> {
            return fillBlocks((CommandSourceStack) commandcontext.getSource(), BoundingBox.fromCorners(from.apply(commandcontext), to.apply(commandcontext)), block.apply(commandcontext), FillCommand.Mode.DESTROY, filter.apply(commandcontext), false);
        })).then(Commands.literal("strict").executes((commandcontext) -> {
            return fillBlocks((CommandSourceStack) commandcontext.getSource(), BoundingBox.fromCorners(from.apply(commandcontext), to.apply(commandcontext)), block.apply(commandcontext), FillCommand.Mode.REPLACE, filter.apply(commandcontext), true);
        }));
    }

    private static int fillBlocks(CommandSourceStack source, BoundingBox region, BlockInput target, FillCommand.Mode mode, @Nullable Predicate<BlockInWorld> predicate, boolean strict) throws CommandSyntaxException {
        int i = region.getXSpan() * region.getYSpan() * region.getZSpan();
        int j = (Integer)source.getLevel().getGameRules().get(GameRules.MAX_BLOCK_MODIFICATIONS);

        if (i > j) {
            throw FillCommand.ERROR_AREA_TOO_LARGE.create(j, i);
        } else {
            record 1UpdatedPosition(BlockPos pos, BlockState oldState) {

            }

            List<1UpdatedPosition> list = Lists.newArrayList();
            ServerLevel serverlevel = source.getLevel();

            if (serverlevel.isDebug()) {
                throw FillCommand.ERROR_FAILED.create();
            } else {
                int k = 0;

                for(BlockPos blockpos : BlockPos.betweenClosed(region.minX(), region.minY(), region.minZ(), region.maxX(), region.maxY(), region.maxZ())) {
                    if (predicate == null || predicate.test(new BlockInWorld(serverlevel, blockpos, true))) {
                        BlockState blockstate = serverlevel.getBlockState(blockpos);
                        boolean flag1 = false;

                        if (mode.affector.affect(serverlevel, blockpos)) {
                            flag1 = true;
                        }

                        BlockInput blockinput1 = mode.filter.filter(region, blockpos, target, serverlevel);

                        if (blockinput1 == null) {
                            if (flag1) {
                                ++k;
                            }
                        } else if (!blockinput1.place(serverlevel, blockpos, 2 | (strict ? 816 : 256))) {
                            if (flag1) {
                                ++k;
                            }
                        } else {
                            if (!strict) {
                                list.add(new 1UpdatedPosition(blockpos.immutable(), blockstate));
                            }

                            ++k;
                        }
                    }
                }

                for(1UpdatedPosition 1updatedposition : list) {
                    serverlevel.updateNeighboursOnBlockSet(1updatedposition.pos, 1updatedposition.oldState);
                }

                if (k == 0) {
                    throw FillCommand.ERROR_FAILED.create();
                } else {
                    source.sendSuccess(() -> {
                        return Component.translatable("commands.fill.success", k);
                    }, true);
                    return k;
                }
            }
        }
    }

    private static enum Mode {

        REPLACE(FillCommand.Affector.NOOP, FillCommand.Filter.NOOP), OUTLINE(FillCommand.Affector.NOOP, (boundingbox, blockpos, blockinput, serverlevel) -> {
            return blockpos.getX() != boundingbox.minX() && blockpos.getX() != boundingbox.maxX() && blockpos.getY() != boundingbox.minY() && blockpos.getY() != boundingbox.maxY() && blockpos.getZ() != boundingbox.minZ() && blockpos.getZ() != boundingbox.maxZ() ? null : blockinput;
        }), HOLLOW(FillCommand.Affector.NOOP, (boundingbox, blockpos, blockinput, serverlevel) -> {
            return blockpos.getX() != boundingbox.minX() && blockpos.getX() != boundingbox.maxX() && blockpos.getY() != boundingbox.minY() && blockpos.getY() != boundingbox.maxY() && blockpos.getZ() != boundingbox.minZ() && blockpos.getZ() != boundingbox.maxZ() ? FillCommand.HOLLOW_CORE : blockinput;
        }), DESTROY((serverlevel, blockpos) -> {
            return serverlevel.destroyBlock(blockpos, true);
        }, FillCommand.Filter.NOOP);

        public final FillCommand.Filter filter;
        public final FillCommand.Affector affector;

        private Mode(FillCommand.Affector affector, FillCommand.Filter filter) {
            this.affector = affector;
            this.filter = filter;
        }
    }

    @FunctionalInterface
    public interface Filter {

        FillCommand.Filter NOOP = (boundingbox, blockpos, blockinput, serverlevel) -> {
            return blockinput;
        };

        @Nullable
        BlockInput filter(BoundingBox region, BlockPos pos, BlockInput block, ServerLevel level);
    }

    @FunctionalInterface
    public interface Affector {

        FillCommand.Affector NOOP = (serverlevel, blockpos) -> {
            return false;
        };

        boolean affect(ServerLevel level, BlockPos pos);
    }

    @FunctionalInterface
    private interface NullableCommandFunction<T, R> {

        @Nullable
        R apply(T t) throws CommandSyntaxException;
    }
}
