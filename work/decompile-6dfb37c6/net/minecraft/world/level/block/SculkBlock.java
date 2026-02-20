package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

public class SculkBlock extends DropExperienceBlock implements SculkBehaviour {

    public static final MapCodec<SculkBlock> CODEC = simpleCodec(SculkBlock::new);

    @Override
    public MapCodec<SculkBlock> codec() {
        return SculkBlock.CODEC;
    }

    public SculkBlock(BlockBehaviour.Properties properties) {
        super(ConstantInt.of(1), properties);
    }

    @Override
    public int attemptUseCharge(SculkSpreader.ChargeCursor cursor, LevelAccessor level, BlockPos originPos, RandomSource random, SculkSpreader spreader, boolean spreadVein) {
        int i = cursor.getCharge();

        if (i != 0 && random.nextInt(spreader.chargeDecayRate()) == 0) {
            BlockPos blockpos1 = cursor.getPos();
            boolean flag1 = blockpos1.closerThan(originPos, (double) spreader.noGrowthRadius());

            if (!flag1 && canPlaceGrowth(level, blockpos1)) {
                int j = spreader.growthSpawnCost();

                if (random.nextInt(j) < i) {
                    BlockPos blockpos2 = blockpos1.above();
                    BlockState blockstate = this.getRandomGrowthState(level, blockpos2, random, spreader.isWorldGeneration());

                    level.setBlock(blockpos2, blockstate, 3);
                    level.playSound((Entity) null, blockpos1, blockstate.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
                }

                return Math.max(0, i - j);
            } else {
                return random.nextInt(spreader.additionalDecayRate()) != 0 ? i : i - (flag1 ? 1 : getDecayPenalty(spreader, blockpos1, originPos, i));
            }
        } else {
            return i;
        }
    }

    private static int getDecayPenalty(SculkSpreader spreader, BlockPos pos, BlockPos originPos, int charge) {
        int j = spreader.noGrowthRadius();
        float f = Mth.square((float) Math.sqrt(pos.distSqr(originPos)) - (float) j);
        int k = Mth.square(24 - j);
        float f1 = Math.min(1.0F, f / (float) k);

        return Math.max(1, (int) ((float) charge * f1 * 0.5F));
    }

    private BlockState getRandomGrowthState(LevelAccessor level, BlockPos pos, RandomSource random, boolean isWorldGen) {
        BlockState blockstate;

        if (random.nextInt(11) == 0) {
            blockstate = (BlockState) Blocks.SCULK_SHRIEKER.defaultBlockState().setValue(SculkShriekerBlock.CAN_SUMMON, isWorldGen);
        } else {
            blockstate = Blocks.SCULK_SENSOR.defaultBlockState();
        }

        return blockstate.hasProperty(BlockStateProperties.WATERLOGGED) && !level.getFluidState(pos).isEmpty() ? (BlockState) blockstate.setValue(BlockStateProperties.WATERLOGGED, true) : blockstate;
    }

    private static boolean canPlaceGrowth(LevelAccessor level, BlockPos pos) {
        BlockState blockstate = level.getBlockState(pos.above());

        if (blockstate.isAir() || blockstate.is(Blocks.WATER) && blockstate.getFluidState().is(Fluids.WATER)) {
            int i = 0;

            for (BlockPos blockpos1 : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 2, 4))) {
                BlockState blockstate1 = level.getBlockState(blockpos1);

                if (blockstate1.is(Blocks.SCULK_SENSOR) || blockstate1.is(Blocks.SCULK_SHRIEKER)) {
                    ++i;
                }

                if (i > 2) {
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean canChangeBlockStateOnSpread() {
        return false;
    }
}
