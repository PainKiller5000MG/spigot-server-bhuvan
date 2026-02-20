package net.minecraft.world.level.levelgen.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class EndPodiumFeature extends Feature<NoneFeatureConfiguration> {

    public static final int PODIUM_RADIUS = 4;
    public static final int PODIUM_PILLAR_HEIGHT = 4;
    public static final int RIM_RADIUS = 1;
    public static final float CORNER_ROUNDING = 0.5F;
    private static final BlockPos END_PODIUM_LOCATION = BlockPos.ZERO;
    private final boolean active;

    public static BlockPos getLocation(BlockPos offset) {
        return EndPodiumFeature.END_PODIUM_LOCATION.offset(offset);
    }

    public EndPodiumFeature(boolean active) {
        super(NoneFeatureConfiguration.CODEC);
        this.active = active;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        BlockPos blockpos = context.origin();
        WorldGenLevel worldgenlevel = context.level();

        for (BlockPos blockpos1 : BlockPos.betweenClosed(new BlockPos(blockpos.getX() - 4, blockpos.getY() - 1, blockpos.getZ() - 4), new BlockPos(blockpos.getX() + 4, blockpos.getY() + 32, blockpos.getZ() + 4))) {
            boolean flag = blockpos1.closerThan(blockpos, 2.5D);

            if (flag || blockpos1.closerThan(blockpos, 3.5D)) {
                if (blockpos1.getY() < blockpos.getY()) {
                    if (flag) {
                        this.setBlock(worldgenlevel, blockpos1, Blocks.BEDROCK.defaultBlockState());
                    } else if (blockpos1.getY() < blockpos.getY()) {
                        if (this.active) {
                            this.dropPreviousAndSetBlock(worldgenlevel, blockpos1, Blocks.END_STONE);
                        } else {
                            this.setBlock(worldgenlevel, blockpos1, Blocks.END_STONE.defaultBlockState());
                        }
                    }
                } else if (blockpos1.getY() > blockpos.getY()) {
                    if (this.active) {
                        this.dropPreviousAndSetBlock(worldgenlevel, blockpos1, Blocks.AIR);
                    } else {
                        this.setBlock(worldgenlevel, blockpos1, Blocks.AIR.defaultBlockState());
                    }
                } else if (!flag) {
                    this.setBlock(worldgenlevel, blockpos1, Blocks.BEDROCK.defaultBlockState());
                } else if (this.active) {
                    this.dropPreviousAndSetBlock(worldgenlevel, new BlockPos(blockpos1), Blocks.END_PORTAL);
                } else {
                    this.setBlock(worldgenlevel, new BlockPos(blockpos1), Blocks.AIR.defaultBlockState());
                }
            }
        }

        for (int i = 0; i < 4; ++i) {
            this.setBlock(worldgenlevel, blockpos.above(i), Blocks.BEDROCK.defaultBlockState());
        }

        BlockPos blockpos2 = blockpos.above(2);

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            this.setBlock(worldgenlevel, blockpos2.relative(direction), (BlockState) Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, direction));
        }

        return true;
    }

    private void dropPreviousAndSetBlock(WorldGenLevel level, BlockPos pos, Block block) {
        if (!level.getBlockState(pos).is(block)) {
            level.destroyBlock(pos, true, (Entity) null);
            this.setBlock(level, pos, block.defaultBlockState());
        }

    }
}
