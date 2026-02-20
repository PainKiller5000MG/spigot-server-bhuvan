package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.BlockUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class GrowingPlantBodyBlock extends GrowingPlantBlock implements BonemealableBlock {

    protected GrowingPlantBodyBlock(BlockBehaviour.Properties properties, Direction growthDirection, VoxelShape shape, boolean scheduleFluidTicks) {
        super(properties, growthDirection, shape, scheduleFluidTicks);
    }

    @Override
    protected abstract MapCodec<? extends GrowingPlantBodyBlock> codec();

    protected BlockState updateHeadAfterConvertedFromBody(BlockState bodyState, BlockState headState) {
        return headState;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (directionToNeighbour == this.growthDirection.getOpposite() && !state.canSurvive(level, pos)) {
            ticks.scheduleTick(pos, (Block) this, 1);
        }

        GrowingPlantHeadBlock growingplantheadblock = this.getHeadBlock();

        if (directionToNeighbour == this.growthDirection && !neighbourState.is(this) && !neighbourState.is(growingplantheadblock)) {
            return this.updateHeadAfterConvertedFromBody(state, growingplantheadblock.getStateForPlacement(random));
        } else {
            if (this.scheduleFluidTicks) {
                ticks.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
            }

            return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
        }
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(this.getHeadBlock());
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        Optional<BlockPos> optional = this.getHeadPos(level, pos, state.getBlock());

        return optional.isPresent() && this.getHeadBlock().canGrowInto(level.getBlockState(((BlockPos) optional.get()).relative(this.growthDirection)));
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        Optional<BlockPos> optional = this.getHeadPos(level, pos, state.getBlock());

        if (optional.isPresent()) {
            BlockState blockstate1 = level.getBlockState((BlockPos) optional.get());

            ((GrowingPlantHeadBlock) blockstate1.getBlock()).performBonemeal(level, random, (BlockPos) optional.get(), blockstate1);
        }

    }

    private Optional<BlockPos> getHeadPos(BlockGetter level, BlockPos pos, Block bodyBlock) {
        return BlockUtil.getTopConnectedBlock(level, pos, bodyBlock, this.growthDirection, this.getHeadBlock());
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        boolean flag = super.canBeReplaced(state, context);

        return flag && context.getItemInHand().is(this.getHeadBlock().asItem()) ? false : flag;
    }

    @Override
    protected Block getBodyBlock() {
        return this;
    }
}
