package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class CloneCommands {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SimpleCommandExceptionType ERROR_OVERLAP = new SimpleCommandExceptionType(Component.translatable("commands.clone.overlap"));
    private static final Dynamic2CommandExceptionType ERROR_AREA_TOO_LARGE = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("commands.clone.toobig", object, object1);
    });
    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.clone.failed"));
    public static final Predicate<BlockInWorld> FILTER_AIR = (blockinworld) -> {
        return !blockinworld.getState().isAir();
    };

    public CloneCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("clone").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(beginEndDestinationAndModeSuffix(context, (commandcontext) -> {
            return ((CommandSourceStack) commandcontext.getSource()).getLevel();
        }))).then(Commands.literal("from").then(Commands.argument("sourceDimension", DimensionArgument.dimension()).then(beginEndDestinationAndModeSuffix(context, (commandcontext) -> {
            return DimensionArgument.getDimension(commandcontext, "sourceDimension");
        })))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> beginEndDestinationAndModeSuffix(CommandBuildContext context, InCommandFunction<CommandContext<CommandSourceStack>, ServerLevel> fromDimension) {
        return Commands.argument("begin", BlockPosArgument.blockPos()).then(((RequiredArgumentBuilder) Commands.argument("end", BlockPosArgument.blockPos()).then(destinationAndStrictSuffix(context, fromDimension, (commandcontext) -> {
            return ((CommandSourceStack) commandcontext.getSource()).getLevel();
        }))).then(Commands.literal("to").then(Commands.argument("targetDimension", DimensionArgument.dimension()).then(destinationAndStrictSuffix(context, fromDimension, (commandcontext) -> {
            return DimensionArgument.getDimension(commandcontext, "targetDimension");
        })))));
    }

    private static CloneCommands.DimensionAndPosition getLoadedDimensionAndPosition(CommandContext<CommandSourceStack> context, ServerLevel level, String positionArgument) throws CommandSyntaxException {
        BlockPos blockpos = BlockPosArgument.getLoadedBlockPos(context, level, positionArgument);

        return new CloneCommands.DimensionAndPosition(level, blockpos);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> destinationAndStrictSuffix(CommandBuildContext context, InCommandFunction<CommandContext<CommandSourceStack>, ServerLevel> fromDimension, InCommandFunction<CommandContext<CommandSourceStack>, ServerLevel> toDimension) {
        InCommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> incommandfunction2 = (commandcontext) -> {
            return getLoadedDimensionAndPosition(commandcontext, fromDimension.apply(commandcontext), "begin");
        };
        InCommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> incommandfunction3 = (commandcontext) -> {
            return getLoadedDimensionAndPosition(commandcontext, fromDimension.apply(commandcontext), "end");
        };
        InCommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> incommandfunction4 = (commandcontext) -> {
            return getLoadedDimensionAndPosition(commandcontext, toDimension.apply(commandcontext), "destination");
        };

        return modeSuffix(context, incommandfunction2, incommandfunction3, incommandfunction4, false, Commands.argument("destination", BlockPosArgument.blockPos())).then(modeSuffix(context, incommandfunction2, incommandfunction3, incommandfunction4, true, Commands.literal("strict")));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> modeSuffix(CommandBuildContext context, InCommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> beginPos, InCommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> endPos, InCommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> destinationPos, boolean strict, ArgumentBuilder<CommandSourceStack, ?> builder) {
        return builder.executes((commandcontext) -> {
            return clone((CommandSourceStack) commandcontext.getSource(), beginPos.apply(commandcontext), endPos.apply(commandcontext), destinationPos.apply(commandcontext), (blockinworld) -> {
                return true;
            }, CloneCommands.Mode.NORMAL, strict);
        }).then(wrapWithCloneMode(beginPos, endPos, destinationPos, (commandcontext) -> {
            return (blockinworld) -> {
                return true;
            };
        }, strict, Commands.literal("replace"))).then(wrapWithCloneMode(beginPos, endPos, destinationPos, (commandcontext) -> {
            return CloneCommands.FILTER_AIR;
        }, strict, Commands.literal("masked"))).then(Commands.literal("filtered").then(wrapWithCloneMode(beginPos, endPos, destinationPos, (commandcontext) -> {
            return BlockPredicateArgument.getBlockPredicate(commandcontext, "filter");
        }, strict, Commands.argument("filter", BlockPredicateArgument.blockPredicate(context)))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> wrapWithCloneMode(InCommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> beginPos, InCommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> endPos, InCommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> destinationPos, InCommandFunction<CommandContext<CommandSourceStack>, Predicate<BlockInWorld>> filter, boolean strict, ArgumentBuilder<CommandSourceStack, ?> builder) {
        return builder.executes((commandcontext) -> {
            return clone((CommandSourceStack) commandcontext.getSource(), beginPos.apply(commandcontext), endPos.apply(commandcontext), destinationPos.apply(commandcontext), filter.apply(commandcontext), CloneCommands.Mode.NORMAL, strict);
        }).then(Commands.literal("force").executes((commandcontext) -> {
            return clone((CommandSourceStack) commandcontext.getSource(), beginPos.apply(commandcontext), endPos.apply(commandcontext), destinationPos.apply(commandcontext), filter.apply(commandcontext), CloneCommands.Mode.FORCE, strict);
        })).then(Commands.literal("move").executes((commandcontext) -> {
            return clone((CommandSourceStack) commandcontext.getSource(), beginPos.apply(commandcontext), endPos.apply(commandcontext), destinationPos.apply(commandcontext), filter.apply(commandcontext), CloneCommands.Mode.MOVE, strict);
        })).then(Commands.literal("normal").executes((commandcontext) -> {
            return clone((CommandSourceStack) commandcontext.getSource(), beginPos.apply(commandcontext), endPos.apply(commandcontext), destinationPos.apply(commandcontext), filter.apply(commandcontext), CloneCommands.Mode.NORMAL, strict);
        }));
    }

    private static int clone(CommandSourceStack source, CloneCommands.DimensionAndPosition startPosAndDimension, CloneCommands.DimensionAndPosition endPosAndDimension, CloneCommands.DimensionAndPosition destPosAndDimension, Predicate<BlockInWorld> predicate, CloneCommands.Mode mode, boolean strict) throws CommandSyntaxException {
        BlockPos blockpos = startPosAndDimension.position();
        BlockPos blockpos1 = endPosAndDimension.position();
        BoundingBox boundingbox = BoundingBox.fromCorners(blockpos, blockpos1);
        BlockPos blockpos2 = destPosAndDimension.position();
        BlockPos blockpos3 = blockpos2.offset(boundingbox.getLength());
        BoundingBox boundingbox1 = BoundingBox.fromCorners(blockpos2, blockpos3);
        ServerLevel serverlevel = startPosAndDimension.dimension();
        ServerLevel serverlevel1 = destPosAndDimension.dimension();

        if (!mode.canOverlap() && serverlevel == serverlevel1 && boundingbox1.intersects(boundingbox)) {
            throw CloneCommands.ERROR_OVERLAP.create();
        } else {
            int i = boundingbox.getXSpan() * boundingbox.getYSpan() * boundingbox.getZSpan();
            int j = (Integer) source.getLevel().getGameRules().get(GameRules.MAX_BLOCK_MODIFICATIONS);

            if (i > j) {
                throw CloneCommands.ERROR_AREA_TOO_LARGE.create(j, i);
            } else if (serverlevel.hasChunksAt(blockpos, blockpos1) && serverlevel1.hasChunksAt(blockpos2, blockpos3)) {
                if (serverlevel1.isDebug()) {
                    throw CloneCommands.ERROR_FAILED.create();
                } else {
                    List<CloneCommands.CloneBlockInfo> list = Lists.newArrayList();
                    List<CloneCommands.CloneBlockInfo> list1 = Lists.newArrayList();
                    List<CloneCommands.CloneBlockInfo> list2 = Lists.newArrayList();
                    Deque<BlockPos> deque = Lists.newLinkedList();
                    int k = 0;
                    ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(CloneCommands.LOGGER);

                    try {
                        BlockPos blockpos4 = new BlockPos(boundingbox1.minX() - boundingbox.minX(), boundingbox1.minY() - boundingbox.minY(), boundingbox1.minZ() - boundingbox.minZ());

                        for (int l = boundingbox.minZ(); l <= boundingbox.maxZ(); ++l) {
                            for (int i1 = boundingbox.minY(); i1 <= boundingbox.maxY(); ++i1) {
                                for (int j1 = boundingbox.minX(); j1 <= boundingbox.maxX(); ++j1) {
                                    BlockPos blockpos5 = new BlockPos(j1, i1, l);
                                    BlockPos blockpos6 = blockpos5.offset(blockpos4);
                                    BlockInWorld blockinworld = new BlockInWorld(serverlevel, blockpos5, false);
                                    BlockState blockstate = blockinworld.getState();

                                    if (predicate.test(blockinworld)) {
                                        BlockEntity blockentity = serverlevel.getBlockEntity(blockpos5);

                                        if (blockentity != null) {
                                            TagValueOutput tagvalueoutput = TagValueOutput.createWithContext(problemreporter_scopedcollector.forChild(blockentity.problemPath()), source.registryAccess());

                                            blockentity.saveCustomOnly((ValueOutput) tagvalueoutput);
                                            CloneCommands.CloneBlockEntityInfo clonecommands_cloneblockentityinfo = new CloneCommands.CloneBlockEntityInfo(tagvalueoutput.buildResult(), blockentity.components());

                                            list1.add(new CloneCommands.CloneBlockInfo(blockpos6, blockstate, clonecommands_cloneblockentityinfo, serverlevel1.getBlockState(blockpos6)));
                                            deque.addLast(blockpos5);
                                        } else if (!blockstate.isSolidRender() && !blockstate.isCollisionShapeFullBlock(serverlevel, blockpos5)) {
                                            list2.add(new CloneCommands.CloneBlockInfo(blockpos6, blockstate, (CloneCommands.CloneBlockEntityInfo) null, serverlevel1.getBlockState(blockpos6)));
                                            deque.addFirst(blockpos5);
                                        } else {
                                            list.add(new CloneCommands.CloneBlockInfo(blockpos6, blockstate, (CloneCommands.CloneBlockEntityInfo) null, serverlevel1.getBlockState(blockpos6)));
                                            deque.addLast(blockpos5);
                                        }
                                    }
                                }
                            }
                        }

                        int k1 = 2 | (strict ? 816 : 0);

                        if (mode == CloneCommands.Mode.MOVE) {
                            for (BlockPos blockpos7 : deque) {
                                serverlevel.setBlock(blockpos7, Blocks.BARRIER.defaultBlockState(), k1 | 816);
                            }

                            int l1 = strict ? k1 : 3;

                            for (BlockPos blockpos8 : deque) {
                                serverlevel.setBlock(blockpos8, Blocks.AIR.defaultBlockState(), l1);
                            }
                        }

                        List<CloneCommands.CloneBlockInfo> list3 = Lists.newArrayList();

                        list3.addAll(list);
                        list3.addAll(list1);
                        list3.addAll(list2);
                        List<CloneCommands.CloneBlockInfo> list4 = Lists.reverse(list3);

                        for (CloneCommands.CloneBlockInfo clonecommands_cloneblockinfo : list4) {
                            serverlevel1.setBlock(clonecommands_cloneblockinfo.pos, Blocks.BARRIER.defaultBlockState(), k1 | 816);
                        }

                        for (CloneCommands.CloneBlockInfo clonecommands_cloneblockinfo1 : list3) {
                            if (serverlevel1.setBlock(clonecommands_cloneblockinfo1.pos, clonecommands_cloneblockinfo1.state, k1)) {
                                ++k;
                            }
                        }

                        for (CloneCommands.CloneBlockInfo clonecommands_cloneblockinfo2 : list1) {
                            BlockEntity blockentity1 = serverlevel1.getBlockEntity(clonecommands_cloneblockinfo2.pos);

                            if (clonecommands_cloneblockinfo2.blockEntityInfo != null && blockentity1 != null) {
                                blockentity1.loadCustomOnly(TagValueInput.create(problemreporter_scopedcollector.forChild(blockentity1.problemPath()), serverlevel1.registryAccess(), clonecommands_cloneblockinfo2.blockEntityInfo.tag));
                                blockentity1.setComponents(clonecommands_cloneblockinfo2.blockEntityInfo.components);
                                blockentity1.setChanged();
                            }

                            serverlevel1.setBlock(clonecommands_cloneblockinfo2.pos, clonecommands_cloneblockinfo2.state, k1);
                        }

                        if (!strict) {
                            for (CloneCommands.CloneBlockInfo clonecommands_cloneblockinfo3 : list4) {
                                serverlevel1.updateNeighboursOnBlockSet(clonecommands_cloneblockinfo3.pos, clonecommands_cloneblockinfo3.previousStateAtDestination);
                            }
                        }

                        serverlevel1.getBlockTicks().copyAreaFrom(serverlevel.getBlockTicks(), boundingbox, blockpos4);
                    } catch (Throwable throwable) {
                        try {
                            problemreporter_scopedcollector.close();
                        } catch (Throwable throwable1) {
                            throwable.addSuppressed(throwable1);
                        }

                        throw throwable;
                    }

                    problemreporter_scopedcollector.close();
                    if (k == 0) {
                        throw CloneCommands.ERROR_FAILED.create();
                    } else {
                        source.sendSuccess(() -> {
                            return Component.translatable("commands.clone.success", k);
                        }, true);
                        return k;
                    }
                }
            } else {
                throw BlockPosArgument.ERROR_NOT_LOADED.create();
            }
        }
    }

    private static record DimensionAndPosition(ServerLevel dimension, BlockPos position) {

    }

    private static enum Mode {

        FORCE(true), MOVE(true), NORMAL(false);

        private final boolean canOverlap;

        private Mode(boolean canOverlap) {
            this.canOverlap = canOverlap;
        }

        public boolean canOverlap() {
            return this.canOverlap;
        }
    }

    private static record CloneBlockEntityInfo(CompoundTag tag, DataComponentMap components) {

    }

    private static record CloneBlockInfo(BlockPos pos, BlockState state, CloneCommands.@Nullable CloneBlockEntityInfo blockEntityInfo, BlockState previousStateAtDestination) {

    }
}
