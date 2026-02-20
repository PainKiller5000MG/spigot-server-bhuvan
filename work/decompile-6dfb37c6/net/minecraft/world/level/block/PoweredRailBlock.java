package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;

public class PoweredRailBlock extends BaseRailBlock {

    public static final MapCodec<PoweredRailBlock> CODEC = simpleCodec(PoweredRailBlock::new);
    public static final EnumProperty<RailShape> SHAPE = BlockStateProperties.RAIL_SHAPE_STRAIGHT;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    @Override
    public MapCodec<PoweredRailBlock> codec() {
        return PoweredRailBlock.CODEC;
    }

    protected PoweredRailBlock(BlockBehaviour.Properties properties) {
        super(true, properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_SOUTH)).setValue(PoweredRailBlock.POWERED, false)).setValue(PoweredRailBlock.WATERLOGGED, false));
    }

    protected boolean findPoweredRailSignal(Level level, BlockPos pos, BlockState state, boolean forward, int searchDepth) {
        if (searchDepth >= 8) {
            return false;
        } else {
            int j = pos.getX();
            int k = pos.getY();
            int l = pos.getZ();
            boolean flag1 = true;
            RailShape railshape = (RailShape) state.getValue(PoweredRailBlock.SHAPE);

            switch (railshape) {
                case NORTH_SOUTH:
                    if (forward) {
                        ++l;
                    } else {
                        --l;
                    }
                    break;
                case EAST_WEST:
                    if (forward) {
                        --j;
                    } else {
                        ++j;
                    }
                    break;
                case ASCENDING_EAST:
                    if (forward) {
                        --j;
                    } else {
                        ++j;
                        ++k;
                        flag1 = false;
                    }

                    railshape = RailShape.EAST_WEST;
                    break;
                case ASCENDING_WEST:
                    if (forward) {
                        --j;
                        ++k;
                        flag1 = false;
                    } else {
                        ++j;
                    }

                    railshape = RailShape.EAST_WEST;
                    break;
                case ASCENDING_NORTH:
                    if (forward) {
                        ++l;
                    } else {
                        --l;
                        ++k;
                        flag1 = false;
                    }

                    railshape = RailShape.NORTH_SOUTH;
                    break;
                case ASCENDING_SOUTH:
                    if (forward) {
                        ++l;
                        ++k;
                        flag1 = false;
                    } else {
                        --l;
                    }

                    railshape = RailShape.NORTH_SOUTH;
            }

            return this.isSameRailWithPower(level, new BlockPos(j, k, l), forward, searchDepth, railshape) ? true : flag1 && this.isSameRailWithPower(level, new BlockPos(j, k - 1, l), forward, searchDepth, railshape);
        }
    }

    protected boolean isSameRailWithPower(Level level, BlockPos pos, boolean forward, int searchDepth, RailShape dir) {
        BlockState blockstate = level.getBlockState(pos);

        if (!blockstate.is(this)) {
            return false;
        } else {
            RailShape railshape1 = (RailShape) blockstate.getValue(PoweredRailBlock.SHAPE);

            return dir != RailShape.EAST_WEST || railshape1 != RailShape.NORTH_SOUTH && railshape1 != RailShape.ASCENDING_NORTH && railshape1 != RailShape.ASCENDING_SOUTH ? (dir != RailShape.NORTH_SOUTH || railshape1 != RailShape.EAST_WEST && railshape1 != RailShape.ASCENDING_EAST && railshape1 != RailShape.ASCENDING_WEST ? ((Boolean) blockstate.getValue(PoweredRailBlock.POWERED) ? (level.hasNeighborSignal(pos) ? true : this.findPoweredRailSignal(level, pos, blockstate, forward, searchDepth + 1)) : false) : false) : false;
        }
    }

    @Override
    protected void updateState(BlockState state, Level level, BlockPos pos, Block block) {
        boolean flag = (Boolean) state.getValue(PoweredRailBlock.POWERED);
        boolean flag1 = level.hasNeighborSignal(pos) || this.findPoweredRailSignal(level, pos, state, true, 0) || this.findPoweredRailSignal(level, pos, state, false, 0);

        if (flag1 != flag) {
            level.setBlock(pos, (BlockState) state.setValue(PoweredRailBlock.POWERED, flag1), 3);
            level.updateNeighborsAt(pos.below(), this);
            if (((RailShape) state.getValue(PoweredRailBlock.SHAPE)).isSlope()) {
                level.updateNeighborsAt(pos.above(), this);
            }
        }

    }

    @Override
    public Property<RailShape> getShapeProperty() {
        return PoweredRailBlock.SHAPE;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        RailShape railshape = (RailShape) state.getValue(PoweredRailBlock.SHAPE);
        RailShape railshape1 = this.rotate(railshape, rotation);

        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, railshape1);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        RailShape railshape = (RailShape) state.getValue(PoweredRailBlock.SHAPE);
        RailShape railshape1 = this.mirror(railshape, mirror);

        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, railshape1);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PoweredRailBlock.SHAPE, PoweredRailBlock.POWERED, PoweredRailBlock.WATERLOGGED);
    }
}
