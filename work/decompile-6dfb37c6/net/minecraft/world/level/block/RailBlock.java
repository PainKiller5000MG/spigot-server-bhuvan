package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailBlock extends BaseRailBlock {

    public static final MapCodec<RailBlock> CODEC = simpleCodec(RailBlock::new);
    public static final EnumProperty<RailShape> SHAPE = BlockStateProperties.RAIL_SHAPE;

    @Override
    public MapCodec<RailBlock> codec() {
        return RailBlock.CODEC;
    }

    protected RailBlock(BlockBehaviour.Properties properties) {
        super(false, properties);
        this.registerDefaultState((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(RailBlock.SHAPE, RailShape.NORTH_SOUTH)).setValue(RailBlock.WATERLOGGED, false));
    }

    @Override
    protected void updateState(BlockState state, Level level, BlockPos pos, Block block) {
        if (block.defaultBlockState().isSignalSource() && (new RailState(level, pos, state)).countPotentialConnections() == 3) {
            this.updateDir(level, pos, state, false);
        }

    }

    @Override
    public Property<RailShape> getShapeProperty() {
        return RailBlock.SHAPE;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        RailShape railshape = (RailShape) state.getValue(RailBlock.SHAPE);
        RailShape railshape1 = this.rotate(railshape, rotation);

        return (BlockState) state.setValue(RailBlock.SHAPE, railshape1);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        RailShape railshape = (RailShape) state.getValue(RailBlock.SHAPE);
        RailShape railshape1 = this.mirror(railshape, mirror);

        return (BlockState) state.setValue(RailBlock.SHAPE, railshape1);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RailBlock.SHAPE, RailBlock.WATERLOGGED);
    }
}
