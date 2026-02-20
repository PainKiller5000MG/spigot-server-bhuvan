package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class ConcretePowderBlock extends FallingBlock {

    public static final MapCodec<ConcretePowderBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(BuiltInRegistries.BLOCK.byNameCodec().fieldOf("concrete").forGetter((concretepowderblock) -> {
            return concretepowderblock.concrete;
        }), propertiesCodec()).apply(instance, ConcretePowderBlock::new);
    });
    private final Block concrete;

    @Override
    public MapCodec<ConcretePowderBlock> codec() {
        return ConcretePowderBlock.CODEC;
    }

    public ConcretePowderBlock(Block concrete, BlockBehaviour.Properties properties) {
        super(properties);
        this.concrete = concrete;
    }

    @Override
    public void onLand(Level level, BlockPos pos, BlockState state, BlockState replacedBlock, FallingBlockEntity entity) {
        if (shouldSolidify(level, pos, replacedBlock)) {
            level.setBlock(pos, this.concrete.defaultBlockState(), 3);
        }

    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockGetter blockgetter = context.getLevel();
        BlockPos blockpos = context.getClickedPos();
        BlockState blockstate = blockgetter.getBlockState(blockpos);

        return shouldSolidify(blockgetter, blockpos, blockstate) ? this.concrete.defaultBlockState() : super.getStateForPlacement(context);
    }

    private static boolean shouldSolidify(BlockGetter level, BlockPos pos, BlockState replacedBlock) {
        return canSolidify(replacedBlock) || touchesLiquid(level, pos);
    }

    private static boolean touchesLiquid(BlockGetter level, BlockPos pos) {
        boolean flag = false;
        BlockPos.MutableBlockPos blockpos_mutableblockpos = pos.mutable();

        for (Direction direction : Direction.values()) {
            BlockState blockstate = level.getBlockState(blockpos_mutableblockpos);

            if (direction != Direction.DOWN || canSolidify(blockstate)) {
                blockpos_mutableblockpos.setWithOffset(pos, direction);
                blockstate = level.getBlockState(blockpos_mutableblockpos);
                if (canSolidify(blockstate) && !blockstate.isFaceSturdy(level, pos, direction.getOpposite())) {
                    flag = true;
                    break;
                }
            }
        }

        return flag;
    }

    private static boolean canSolidify(BlockState state) {
        return state.getFluidState().is(FluidTags.WATER);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return touchesLiquid(level, pos) ? this.concrete.defaultBlockState() : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    public int getDustColor(BlockState blockState, BlockGetter level, BlockPos pos) {
        return blockState.getMapColor(level, pos).col;
    }
}
