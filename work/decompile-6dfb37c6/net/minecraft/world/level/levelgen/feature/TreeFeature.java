package net.minecraft.world.level.levelgen.feature;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.LevelWriter;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.rootplacers.RootPlacer;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;

public class TreeFeature extends Feature<TreeConfiguration> {

    private static final @Block.UpdateFlags int BLOCK_UPDATE_FLAGS = 19;

    public TreeFeature(Codec<TreeConfiguration> codec) {
        super(codec);
    }

    public static boolean isVine(LevelSimulatedReader level, BlockPos pos) {
        return level.isStateAtPosition(pos, (blockstate) -> {
            return blockstate.is(Blocks.VINE);
        });
    }

    public static boolean isAirOrLeaves(LevelSimulatedReader level, BlockPos pos) {
        return level.isStateAtPosition(pos, (blockstate) -> {
            return blockstate.isAir() || blockstate.is(BlockTags.LEAVES);
        });
    }

    private static void setBlockKnownShape(LevelWriter level, BlockPos pos, BlockState blockState) {
        level.setBlock(pos, blockState, 19);
    }

    public static boolean validTreePos(LevelSimulatedReader level, BlockPos pos) {
        return level.isStateAtPosition(pos, (blockstate) -> {
            return blockstate.isAir() || blockstate.is(BlockTags.REPLACEABLE_BY_TREES);
        });
    }

    private boolean doPlace(WorldGenLevel level, RandomSource random, BlockPos origin, BiConsumer<BlockPos, BlockState> rootSetter, BiConsumer<BlockPos, BlockState> trunkSetter, FoliagePlacer.FoliageSetter foliageSetter, TreeConfiguration config) {
        int i = config.trunkPlacer.getTreeHeight(random);
        int j = config.foliagePlacer.foliageHeight(random, i, config);
        int k = i - j;
        int l = config.foliagePlacer.foliageRadius(random, k);
        BlockPos blockpos1 = (BlockPos) config.rootPlacer.map((rootplacer) -> {
            return rootplacer.getTrunkOrigin(origin, random);
        }).orElse(origin);
        int i1 = Math.min(origin.getY(), blockpos1.getY());
        int j1 = Math.max(origin.getY(), blockpos1.getY()) + i + 1;

        if (i1 >= level.getMinY() + 1 && j1 <= level.getMaxY() + 1) {
            OptionalInt optionalint = config.minimumSize.minClippedHeight();
            int k1 = this.getMaxFreeTreeHeight(level, i, blockpos1, config);

            if (k1 >= i || !optionalint.isEmpty() && k1 >= optionalint.getAsInt()) {
                if (config.rootPlacer.isPresent() && !((RootPlacer) config.rootPlacer.get()).placeRoots(level, rootSetter, random, origin, blockpos1, config)) {
                    return false;
                } else {
                    List<FoliagePlacer.FoliageAttachment> list = config.trunkPlacer.placeTrunk(level, trunkSetter, random, k1, blockpos1, config);

                    list.forEach((foliageplacer_foliageattachment) -> {
                        config.foliagePlacer.createFoliage(level, foliageSetter, random, config, k1, foliageplacer_foliageattachment, j, l);
                    });
                    return true;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private int getMaxFreeTreeHeight(LevelSimulatedReader level, int maxTreeHeight, BlockPos treePos, TreeConfiguration config) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

        for (int j = 0; j <= maxTreeHeight + 1; ++j) {
            int k = config.minimumSize.getSizeAtHeight(maxTreeHeight, j);

            for (int l = -k; l <= k; ++l) {
                for (int i1 = -k; i1 <= k; ++i1) {
                    blockpos_mutableblockpos.setWithOffset(treePos, l, j, i1);
                    if (!config.trunkPlacer.isFree(level, blockpos_mutableblockpos) || !config.ignoreVines && isVine(level, blockpos_mutableblockpos)) {
                        return j - 2;
                    }
                }
            }
        }

        return maxTreeHeight;
    }

    @Override
    protected void setBlock(LevelWriter level, BlockPos pos, BlockState blockState) {
        setBlockKnownShape(level, pos, blockState);
    }

    @Override
    public final boolean place(FeaturePlaceContext<TreeConfiguration> context) {
        final WorldGenLevel worldgenlevel = context.level();
        RandomSource randomsource = context.random();
        BlockPos blockpos = context.origin();
        TreeConfiguration treeconfiguration = context.config();
        Set<BlockPos> set = Sets.newHashSet();
        Set<BlockPos> set1 = Sets.newHashSet();
        final Set<BlockPos> set2 = Sets.newHashSet();
        Set<BlockPos> set3 = Sets.newHashSet();
        BiConsumer<BlockPos, BlockState> biconsumer = (blockpos1, blockstate) -> {
            set.add(blockpos1.immutable());
            worldgenlevel.setBlock(blockpos1, blockstate, 19);
        };
        BiConsumer<BlockPos, BlockState> biconsumer1 = (blockpos1, blockstate) -> {
            set1.add(blockpos1.immutable());
            worldgenlevel.setBlock(blockpos1, blockstate, 19);
        };
        FoliagePlacer.FoliageSetter foliageplacer_foliagesetter = new FoliagePlacer.FoliageSetter() {
            @Override
            public void set(BlockPos pos, BlockState state) {
                set2.add(pos.immutable());
                worldgenlevel.setBlock(pos, state, 19);
            }

            @Override
            public boolean isSet(BlockPos pos) {
                return set2.contains(pos);
            }
        };
        BiConsumer<BlockPos, BlockState> biconsumer2 = (blockpos1, blockstate) -> {
            set3.add(blockpos1.immutable());
            worldgenlevel.setBlock(blockpos1, blockstate, 19);
        };
        boolean flag = this.doPlace(worldgenlevel, randomsource, blockpos, biconsumer, biconsumer1, foliageplacer_foliagesetter, treeconfiguration);

        if (flag && (!set1.isEmpty() || !set2.isEmpty())) {
            if (!treeconfiguration.decorators.isEmpty()) {
                TreeDecorator.Context treedecorator_context = new TreeDecorator.Context(worldgenlevel, biconsumer2, randomsource, set1, set2, set);

                treeconfiguration.decorators.forEach((treedecorator) -> {
                    treedecorator.place(treedecorator_context);
                });
            }

            return (Boolean) BoundingBox.encapsulatingPositions(Iterables.concat(set, set1, set2, set3)).map((boundingbox) -> {
                DiscreteVoxelShape discretevoxelshape = updateLeaves(worldgenlevel, boundingbox, set1, set3, set);

                StructureTemplate.updateShapeAtEdge(worldgenlevel, 3, discretevoxelshape, boundingbox.minX(), boundingbox.minY(), boundingbox.minZ());
                return true;
            }).orElse(false);
        } else {
            return false;
        }
    }

    private static DiscreteVoxelShape updateLeaves(LevelAccessor level, BoundingBox bounds, Set<BlockPos> logs, Set<BlockPos> decorationSet, Set<BlockPos> rootPositions) {
        DiscreteVoxelShape discretevoxelshape = new BitSetDiscreteVoxelShape(bounds.getXSpan(), bounds.getYSpan(), bounds.getZSpan());
        int i = 7;
        List<Set<BlockPos>> list = Lists.newArrayList();

        for (int j = 0; j < 7; ++j) {
            list.add(Sets.newHashSet());
        }

        for (BlockPos blockpos : Lists.newArrayList(Sets.union(decorationSet, rootPositions))) {
            if (bounds.isInside(blockpos)) {
                discretevoxelshape.fill(blockpos.getX() - bounds.minX(), blockpos.getY() - bounds.minY(), blockpos.getZ() - bounds.minZ());
            }
        }

        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
        int k = 0;

        ((Set) list.get(0)).addAll(logs);

        while (true) {
            while (k >= 7 || !((Set) ((List) list).get(k)).isEmpty()) {
                if (k >= 7) {
                    return discretevoxelshape;
                }

                Iterator<BlockPos> iterator = ((Set) list.get(k)).iterator();
                BlockPos blockpos1 = (BlockPos) iterator.next();

                iterator.remove();
                if (bounds.isInside(blockpos1)) {
                    if (k != 0) {
                        BlockState blockstate = level.getBlockState(blockpos1);

                        setBlockKnownShape(level, blockpos1, (BlockState) blockstate.setValue(BlockStateProperties.DISTANCE, k));
                    }

                    discretevoxelshape.fill(blockpos1.getX() - bounds.minX(), blockpos1.getY() - bounds.minY(), blockpos1.getZ() - bounds.minZ());

                    for (Direction direction : Direction.values()) {
                        blockpos_mutableblockpos.setWithOffset(blockpos1, direction);
                        if (bounds.isInside(blockpos_mutableblockpos)) {
                            int l = blockpos_mutableblockpos.getX() - bounds.minX();
                            int i1 = blockpos_mutableblockpos.getY() - bounds.minY();
                            int j1 = blockpos_mutableblockpos.getZ() - bounds.minZ();

                            if (!discretevoxelshape.isFull(l, i1, j1)) {
                                BlockState blockstate1 = level.getBlockState(blockpos_mutableblockpos);
                                OptionalInt optionalint = LeavesBlock.getOptionalDistanceAt(blockstate1);

                                if (!optionalint.isEmpty()) {
                                    int k1 = Math.min(optionalint.getAsInt(), k + 1);

                                    if (k1 < 7) {
                                        ((Set) list.get(k1)).add(blockpos_mutableblockpos.immutable());
                                        k = Math.min(k, k1);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ++k;
        }
    }

    public static List<BlockPos> getLowestTrunkOrRootOfTree(TreeDecorator.Context context) {
        List<BlockPos> list = Lists.newArrayList();
        List<BlockPos> list1 = context.roots();
        List<BlockPos> list2 = context.logs();

        if (list1.isEmpty()) {
            list.addAll(list2);
        } else if (!list2.isEmpty() && ((BlockPos) list1.get(0)).getY() == ((BlockPos) list2.get(0)).getY()) {
            list.addAll(list2);
            list.addAll(list1);
        } else {
            list.addAll(list1);
        }

        return list;
    }
}
