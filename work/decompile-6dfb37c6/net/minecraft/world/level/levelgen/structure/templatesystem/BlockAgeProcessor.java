package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import org.jspecify.annotations.Nullable;

public class BlockAgeProcessor extends StructureProcessor {

    public static final MapCodec<BlockAgeProcessor> CODEC = Codec.FLOAT.fieldOf("mossiness").xmap(BlockAgeProcessor::new, (blockageprocessor) -> {
        return blockageprocessor.mossiness;
    });
    private static final float PROBABILITY_OF_REPLACING_FULL_BLOCK = 0.5F;
    private static final float PROBABILITY_OF_REPLACING_STAIRS = 0.5F;
    private static final float PROBABILITY_OF_REPLACING_OBSIDIAN = 0.15F;
    private static final BlockState[] NON_MOSSY_REPLACEMENTS = new BlockState[]{Blocks.STONE_SLAB.defaultBlockState(), Blocks.STONE_BRICK_SLAB.defaultBlockState()};
    private final float mossiness;

    public BlockAgeProcessor(float mossiness) {
        this.mossiness = mossiness;
    }

    @Override
    public StructureTemplate.@Nullable StructureBlockInfo processBlock(LevelReader level, BlockPos targetPosition, BlockPos referencePos, StructureTemplate.StructureBlockInfo originalBlockInfo, StructureTemplate.StructureBlockInfo processedBlockInfo, StructurePlaceSettings settings) {
        RandomSource randomsource = settings.getRandom(processedBlockInfo.pos());
        BlockState blockstate = processedBlockInfo.state();
        BlockPos blockpos2 = processedBlockInfo.pos();
        BlockState blockstate1 = null;

        if (!blockstate.is(Blocks.STONE_BRICKS) && !blockstate.is(Blocks.STONE) && !blockstate.is(Blocks.CHISELED_STONE_BRICKS)) {
            if (blockstate.is(BlockTags.STAIRS)) {
                blockstate1 = this.maybeReplaceStairs(blockstate, randomsource);
            } else if (blockstate.is(BlockTags.SLABS)) {
                blockstate1 = this.maybeReplaceSlab(blockstate, randomsource);
            } else if (blockstate.is(BlockTags.WALLS)) {
                blockstate1 = this.maybeReplaceWall(blockstate, randomsource);
            } else if (blockstate.is(Blocks.OBSIDIAN)) {
                blockstate1 = this.maybeReplaceObsidian(randomsource);
            }
        } else {
            blockstate1 = this.maybeReplaceFullStoneBlock(randomsource);
        }

        return blockstate1 != null ? new StructureTemplate.StructureBlockInfo(blockpos2, blockstate1, processedBlockInfo.nbt()) : processedBlockInfo;
    }

    private @Nullable BlockState maybeReplaceFullStoneBlock(RandomSource random) {
        if (random.nextFloat() >= 0.5F) {
            return null;
        } else {
            BlockState[] ablockstate = new BlockState[]{Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), getRandomFacingStairs(random, Blocks.STONE_BRICK_STAIRS)};
            BlockState[] ablockstate1 = new BlockState[]{Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), getRandomFacingStairs(random, Blocks.MOSSY_STONE_BRICK_STAIRS)};

            return this.getRandomBlock(random, ablockstate, ablockstate1);
        }
    }

    private @Nullable BlockState maybeReplaceStairs(BlockState blockState, RandomSource random) {
        if (random.nextFloat() >= 0.5F) {
            return null;
        } else {
            BlockState[] ablockstate = new BlockState[]{Blocks.MOSSY_STONE_BRICK_STAIRS.withPropertiesOf(blockState), Blocks.MOSSY_STONE_BRICK_SLAB.defaultBlockState()};

            return this.getRandomBlock(random, BlockAgeProcessor.NON_MOSSY_REPLACEMENTS, ablockstate);
        }
    }

    private @Nullable BlockState maybeReplaceSlab(BlockState blockState, RandomSource random) {
        return random.nextFloat() < this.mossiness ? Blocks.MOSSY_STONE_BRICK_SLAB.withPropertiesOf(blockState) : null;
    }

    private @Nullable BlockState maybeReplaceWall(BlockState blockState, RandomSource random) {
        return random.nextFloat() < this.mossiness ? Blocks.MOSSY_STONE_BRICK_WALL.withPropertiesOf(blockState) : null;
    }

    private @Nullable BlockState maybeReplaceObsidian(RandomSource random) {
        return random.nextFloat() < 0.15F ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : null;
    }

    private static BlockState getRandomFacingStairs(RandomSource random, Block stairBlock) {
        return (BlockState) ((BlockState) stairBlock.defaultBlockState().setValue(StairBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random))).setValue(StairBlock.HALF, (Half) Util.getRandom(Half.values(), random));
    }

    private BlockState getRandomBlock(RandomSource random, BlockState[] nonMossyBlocks, BlockState[] mossyBlocks) {
        return random.nextFloat() < this.mossiness ? getRandomBlock(random, mossyBlocks) : getRandomBlock(random, nonMossyBlocks);
    }

    private static BlockState getRandomBlock(RandomSource random, BlockState[] blocks) {
        return blocks[random.nextInt(blocks.length)];
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return StructureProcessorType.BLOCK_AGE;
    }
}
