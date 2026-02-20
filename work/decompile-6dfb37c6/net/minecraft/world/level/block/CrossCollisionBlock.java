package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class CrossCollisionBlock extends Block implements SimpleWaterloggedBlock {

    public static final BooleanProperty NORTH = PipeBlock.NORTH;
    public static final BooleanProperty EAST = PipeBlock.EAST;
    public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
    public static final BooleanProperty WEST = PipeBlock.WEST;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = (Map) PipeBlock.PROPERTY_BY_DIRECTION.entrySet().stream().filter((entry) -> {
        return ((Direction) entry.getKey()).getAxis().isHorizontal();
    }).collect(Util.toMap());
    private final Function<BlockState, VoxelShape> collisionShapes;
    private final Function<BlockState, VoxelShape> shapes;

    protected CrossCollisionBlock(float postWidth, float postHeight, float wallWidth, float wallHeight, float collisionHeight, BlockBehaviour.Properties properties) {
        super(properties);
        this.collisionShapes = this.makeShapes(postWidth, collisionHeight, wallWidth, 0.0F, collisionHeight);
        this.shapes = this.makeShapes(postWidth, postHeight, wallWidth, 0.0F, wallHeight);
    }

    @Override
    protected abstract MapCodec<? extends CrossCollisionBlock> codec();

    protected Function<BlockState, VoxelShape> makeShapes(float postWidth, float postHeight, float wallWidth, float wallBottom, float wallTop) {
        VoxelShape voxelshape = Block.column((double) postWidth, 0.0D, (double) postHeight);
        Map<Direction, VoxelShape> map = Shapes.rotateHorizontal(Block.boxZ((double) wallWidth, (double) wallBottom, (double) wallTop, 0.0D, 8.0D));

        return this.getShapeForEachState((blockstate) -> {
            VoxelShape voxelshape1 = voxelshape;

            for (Map.Entry<Direction, BooleanProperty> map_entry : CrossCollisionBlock.PROPERTY_BY_DIRECTION.entrySet()) {
                if ((Boolean) blockstate.getValue((Property) map_entry.getValue())) {
                    voxelshape1 = Shapes.or(voxelshape1, (VoxelShape) map.get(map_entry.getKey()));
                }
            }

            return voxelshape1;
        }, new Property[]{CrossCollisionBlock.WATERLOGGED});
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return !(Boolean) state.getValue(CrossCollisionBlock.WATERLOGGED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (VoxelShape) this.shapes.apply(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (VoxelShape) this.collisionShapes.apply(state);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(CrossCollisionBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        switch (rotation) {
            case CLOCKWISE_180:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue(CrossCollisionBlock.NORTH, (Boolean) state.getValue(CrossCollisionBlock.SOUTH))).setValue(CrossCollisionBlock.EAST, (Boolean) state.getValue(CrossCollisionBlock.WEST))).setValue(CrossCollisionBlock.SOUTH, (Boolean) state.getValue(CrossCollisionBlock.NORTH))).setValue(CrossCollisionBlock.WEST, (Boolean) state.getValue(CrossCollisionBlock.EAST));
            case COUNTERCLOCKWISE_90:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue(CrossCollisionBlock.NORTH, (Boolean) state.getValue(CrossCollisionBlock.EAST))).setValue(CrossCollisionBlock.EAST, (Boolean) state.getValue(CrossCollisionBlock.SOUTH))).setValue(CrossCollisionBlock.SOUTH, (Boolean) state.getValue(CrossCollisionBlock.WEST))).setValue(CrossCollisionBlock.WEST, (Boolean) state.getValue(CrossCollisionBlock.NORTH));
            case CLOCKWISE_90:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue(CrossCollisionBlock.NORTH, (Boolean) state.getValue(CrossCollisionBlock.WEST))).setValue(CrossCollisionBlock.EAST, (Boolean) state.getValue(CrossCollisionBlock.NORTH))).setValue(CrossCollisionBlock.SOUTH, (Boolean) state.getValue(CrossCollisionBlock.EAST))).setValue(CrossCollisionBlock.WEST, (Boolean) state.getValue(CrossCollisionBlock.SOUTH));
            default:
                return state;
        }
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        switch (mirror) {
            case LEFT_RIGHT:
                return (BlockState) ((BlockState) state.setValue(CrossCollisionBlock.NORTH, (Boolean) state.getValue(CrossCollisionBlock.SOUTH))).setValue(CrossCollisionBlock.SOUTH, (Boolean) state.getValue(CrossCollisionBlock.NORTH));
            case FRONT_BACK:
                return (BlockState) ((BlockState) state.setValue(CrossCollisionBlock.EAST, (Boolean) state.getValue(CrossCollisionBlock.WEST))).setValue(CrossCollisionBlock.WEST, (Boolean) state.getValue(CrossCollisionBlock.EAST));
            default:
                return super.mirror(state, mirror);
        }
    }
}
