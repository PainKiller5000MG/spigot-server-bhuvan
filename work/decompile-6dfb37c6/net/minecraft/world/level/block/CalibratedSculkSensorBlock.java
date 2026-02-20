package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.CalibratedSculkSensorBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import org.jspecify.annotations.Nullable;

public class CalibratedSculkSensorBlock extends SculkSensorBlock {

    public static final MapCodec<CalibratedSculkSensorBlock> CODEC = simpleCodec(CalibratedSculkSensorBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    @Override
    public MapCodec<CalibratedSculkSensorBlock> codec() {
        return CalibratedSculkSensorBlock.CODEC;
    }

    public CalibratedSculkSensorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) this.defaultBlockState().setValue(CalibratedSculkSensorBlock.FACING, Direction.NORTH));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new CalibratedSculkSensorBlockEntity(worldPosition, blockState);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> type) {
        return !level.isClientSide() ? createTickerHelper(type, BlockEntityType.CALIBRATED_SCULK_SENSOR, (level1, blockpos, blockstate1, calibratedsculksensorblockentity) -> {
            VibrationSystem.Ticker.tick(level1, calibratedsculksensorblockentity.getVibrationData(), calibratedsculksensorblockentity.getVibrationUser());
        }) : null;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState) super.getStateForPlacement(context).setValue(CalibratedSculkSensorBlock.FACING, context.getHorizontalDirection());
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction != state.getValue(CalibratedSculkSensorBlock.FACING) ? super.getSignal(state, level, pos, direction) : 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CalibratedSculkSensorBlock.FACING);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(CalibratedSculkSensorBlock.FACING, rotation.rotate((Direction) state.getValue(CalibratedSculkSensorBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(CalibratedSculkSensorBlock.FACING)));
    }

    @Override
    public int getActiveTicks() {
        return 10;
    }
}
