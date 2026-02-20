package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class SculkShriekerBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {

    public static final MapCodec<SculkShriekerBlock> CODEC = simpleCodec(SculkShriekerBlock::new);
    public static final BooleanProperty SHRIEKING = BlockStateProperties.SHRIEKING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty CAN_SUMMON = BlockStateProperties.CAN_SUMMON;
    private static final VoxelShape SHAPE_COLLISION = Block.column(16.0D, 0.0D, 8.0D);
    public static final double TOP_Y = SculkShriekerBlock.SHAPE_COLLISION.max(Direction.Axis.Y);

    @Override
    public MapCodec<SculkShriekerBlock> codec() {
        return SculkShriekerBlock.CODEC;
    }

    public SculkShriekerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(SculkShriekerBlock.SHRIEKING, false)).setValue(SculkShriekerBlock.WATERLOGGED, false)).setValue(SculkShriekerBlock.CAN_SUMMON, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SculkShriekerBlock.SHRIEKING);
        builder.add(SculkShriekerBlock.WATERLOGGED);
        builder.add(SculkShriekerBlock.CAN_SUMMON);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState onState, Entity entity) {
        if (level instanceof ServerLevel serverlevel) {
            ServerPlayer serverplayer = SculkShriekerBlockEntity.tryGetPlayer(entity);

            if (serverplayer != null) {
                serverlevel.getBlockEntity(pos, BlockEntityType.SCULK_SHRIEKER).ifPresent((sculkshriekerblockentity) -> {
                    sculkshriekerblockentity.tryShriek(serverlevel, serverplayer);
                });
            }
        }

        super.stepOn(level, pos, onState, entity);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if ((Boolean) state.getValue(SculkShriekerBlock.SHRIEKING)) {
            level.setBlock(pos, (BlockState) state.setValue(SculkShriekerBlock.SHRIEKING, false), 3);
            level.getBlockEntity(pos, BlockEntityType.SCULK_SHRIEKER).ifPresent((sculkshriekerblockentity) -> {
                sculkshriekerblockentity.tryRespond(level);
            });
        }

    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SculkShriekerBlock.SHAPE_COLLISION;
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return SculkShriekerBlock.SHAPE_COLLISION;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new SculkShriekerBlockEntity(worldPosition, blockState);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if ((Boolean) state.getValue(SculkShriekerBlock.WATERLOGGED)) {
            ticks.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState) this.defaultBlockState().setValue(SculkShriekerBlock.WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(SculkShriekerBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, tool, dropExperience);
        if (dropExperience) {
            this.tryDropExperience(level, pos, tool, ConstantInt.of(5));
        }

    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> type) {
        return !level.isClientSide() ? BaseEntityBlock.createTickerHelper(type, BlockEntityType.SCULK_SHRIEKER, (level1, blockpos, blockstate1, sculkshriekerblockentity) -> {
            VibrationSystem.Ticker.tick(level1, sculkshriekerblockentity.getVibrationData(), sculkshriekerblockentity.getVibrationUser());
        }) : null;
    }
}
