package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public class BonusChestFeature extends Feature<NoneFeatureConfiguration> {

    public BonusChestFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        RandomSource randomsource = context.random();
        WorldGenLevel worldgenlevel = context.level();
        ChunkPos chunkpos = new ChunkPos(context.origin());
        IntArrayList intarraylist = Util.toShuffledList(IntStream.rangeClosed(chunkpos.getMinBlockX(), chunkpos.getMaxBlockX()), randomsource);
        IntArrayList intarraylist1 = Util.toShuffledList(IntStream.rangeClosed(chunkpos.getMinBlockZ(), chunkpos.getMaxBlockZ()), randomsource);
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
        IntListIterator intlistiterator = intarraylist.iterator();

        while (intlistiterator.hasNext()) {
            Integer integer = (Integer) intlistiterator.next();
            IntListIterator intlistiterator1 = intarraylist1.iterator();

            while (intlistiterator1.hasNext()) {
                Integer integer1 = (Integer) intlistiterator1.next();

                blockpos_mutableblockpos.set(integer, 0, integer1);
                BlockPos blockpos = worldgenlevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockpos_mutableblockpos);

                if (worldgenlevel.isEmptyBlock(blockpos) || worldgenlevel.getBlockState(blockpos).getCollisionShape(worldgenlevel, blockpos).isEmpty()) {
                    worldgenlevel.setBlock(blockpos, Blocks.CHEST.defaultBlockState(), 2);
                    RandomizableContainer.setBlockEntityLootTable(worldgenlevel, randomsource, blockpos, BuiltInLootTables.SPAWN_BONUS_CHEST);
                    BlockState blockstate = Blocks.TORCH.defaultBlockState();

                    for (Direction direction : Direction.Plane.HORIZONTAL) {
                        BlockPos blockpos1 = blockpos.relative(direction);

                        if (blockstate.canSurvive(worldgenlevel, blockpos1)) {
                            worldgenlevel.setBlock(blockpos1, blockstate, 2);
                        }
                    }

                    return true;
                }
            }
        }

        return false;
    }
}
