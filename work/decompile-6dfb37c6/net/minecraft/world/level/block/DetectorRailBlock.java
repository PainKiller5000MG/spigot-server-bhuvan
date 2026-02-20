package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartCommandBlock;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.AABB;

public class DetectorRailBlock extends BaseRailBlock {

    public static final MapCodec<DetectorRailBlock> CODEC = simpleCodec(DetectorRailBlock::new);
    public static final EnumProperty<RailShape> SHAPE = BlockStateProperties.RAIL_SHAPE_STRAIGHT;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final int PRESSED_CHECK_PERIOD = 20;

    @Override
    public MapCodec<DetectorRailBlock> codec() {
        return DetectorRailBlock.CODEC;
    }

    public DetectorRailBlock(BlockBehaviour.Properties properties) {
        super(true, properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(DetectorRailBlock.POWERED, false)).setValue(DetectorRailBlock.SHAPE, RailShape.NORTH_SOUTH)).setValue(DetectorRailBlock.WATERLOGGED, false));
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (!level.isClientSide()) {
            if (!(Boolean) state.getValue(DetectorRailBlock.POWERED)) {
                this.checkPressed(level, pos, state);
            }
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if ((Boolean) state.getValue(DetectorRailBlock.POWERED)) {
            this.checkPressed(level, pos, state);
        }
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return (Boolean) state.getValue(DetectorRailBlock.POWERED) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return !(Boolean) state.getValue(DetectorRailBlock.POWERED) ? 0 : (direction == Direction.UP ? 15 : 0);
    }

    private void checkPressed(Level level, BlockPos pos, BlockState state) {
        if (this.canSurvive(state, level, pos)) {
            boolean flag = (Boolean) state.getValue(DetectorRailBlock.POWERED);
            boolean flag1 = false;
            List<AbstractMinecart> list = this.<AbstractMinecart>getInteractingMinecartOfType(level, pos, AbstractMinecart.class, (entity) -> {
                return true;
            });

            if (!list.isEmpty()) {
                flag1 = true;
            }

            if (flag1 && !flag) {
                BlockState blockstate1 = (BlockState) state.setValue(DetectorRailBlock.POWERED, true);

                level.setBlock(pos, blockstate1, 3);
                this.updatePowerToConnected(level, pos, blockstate1, true);
                level.updateNeighborsAt(pos, this);
                level.updateNeighborsAt(pos.below(), this);
                level.setBlocksDirty(pos, state, blockstate1);
            }

            if (!flag1 && flag) {
                BlockState blockstate2 = (BlockState) state.setValue(DetectorRailBlock.POWERED, false);

                level.setBlock(pos, blockstate2, 3);
                this.updatePowerToConnected(level, pos, blockstate2, false);
                level.updateNeighborsAt(pos, this);
                level.updateNeighborsAt(pos.below(), this);
                level.setBlocksDirty(pos, state, blockstate2);
            }

            if (flag1) {
                level.scheduleTick(pos, (Block) this, 20);
            }

            level.updateNeighbourForOutputSignal(pos, this);
        }
    }

    protected void updatePowerToConnected(Level level, BlockPos pos, BlockState state, boolean powered) {
        RailState railstate = new RailState(level, pos, state);

        for (BlockPos blockpos1 : railstate.getConnections()) {
            BlockState blockstate1 = level.getBlockState(blockpos1);

            level.neighborChanged(blockstate1, blockpos1, blockstate1.getBlock(), (Orientation) null, false);
        }

    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock())) {
            BlockState blockstate2 = this.updateState(state, level, pos, movedByPiston);

            this.checkPressed(level, pos, blockstate2);
        }
    }

    @Override
    public Property<RailShape> getShapeProperty() {
        return DetectorRailBlock.SHAPE;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        if ((Boolean) state.getValue(DetectorRailBlock.POWERED)) {
            List<MinecartCommandBlock> list = this.<MinecartCommandBlock>getInteractingMinecartOfType(level, pos, MinecartCommandBlock.class, (entity) -> {
                return true;
            });

            if (!list.isEmpty()) {
                return ((MinecartCommandBlock) list.get(0)).getCommandBlock().getSuccessCount();
            }

            List<AbstractMinecart> list1 = this.<AbstractMinecart>getInteractingMinecartOfType(level, pos, AbstractMinecart.class, EntitySelector.CONTAINER_ENTITY_SELECTOR);

            if (!list1.isEmpty()) {
                return AbstractContainerMenu.getRedstoneSignalFromContainer((Container) list1.get(0));
            }
        }

        return 0;
    }

    private <T extends AbstractMinecart> List<T> getInteractingMinecartOfType(Level level, BlockPos pos, Class<T> type, Predicate<Entity> containerEntitySelector) {
        return level.<T>getEntitiesOfClass(type, this.getSearchBB(pos), containerEntitySelector);
    }

    private AABB getSearchBB(BlockPos pos) {
        double d0 = 0.2D;

        return new AABB((double) pos.getX() + 0.2D, (double) pos.getY(), (double) pos.getZ() + 0.2D, (double) (pos.getX() + 1) - 0.2D, (double) (pos.getY() + 1) - 0.2D, (double) (pos.getZ() + 1) - 0.2D);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        RailShape railshape = (RailShape) state.getValue(DetectorRailBlock.SHAPE);
        RailShape railshape1 = this.rotate(railshape, rotation);

        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, railshape1);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        RailShape railshape = (RailShape) state.getValue(DetectorRailBlock.SHAPE);
        RailShape railshape1 = this.mirror(railshape, mirror);

        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, railshape1);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DetectorRailBlock.SHAPE, DetectorRailBlock.POWERED, DetectorRailBlock.WATERLOGGED);
    }
}
