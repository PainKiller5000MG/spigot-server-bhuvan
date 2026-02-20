package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public abstract class BasePressurePlateBlock extends Block {

    private static final VoxelShape SHAPE_PRESSED = Block.column(14.0D, 0.0D, 0.5D);
    private static final VoxelShape SHAPE = Block.column(14.0D, 0.0D, 1.0D);
    protected static final AABB TOUCH_AABB = (AABB) Block.column(14.0D, 0.0D, 4.0D).toAabbs().getFirst();
    protected final BlockSetType type;

    protected BasePressurePlateBlock(BlockBehaviour.Properties properties, BlockSetType type) {
        super(properties.sound(type.soundType()));
        this.type = type;
    }

    @Override
    protected abstract MapCodec<? extends BasePressurePlateBlock> codec();

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getSignalForState(state) > 0 ? BasePressurePlateBlock.SHAPE_PRESSED : BasePressurePlateBlock.SHAPE;
    }

    protected int getPressedTime() {
        return 20;
    }

    @Override
    public boolean isPossibleToRespawnInThis(BlockState state) {
        return true;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return directionToNeighbour == Direction.DOWN && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos blockpos1 = pos.below();

        return canSupportRigidBlock(level, blockpos1) || canSupportCenter(level, blockpos1, Direction.UP);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int i = this.getSignalForState(state);

        if (i > 0) {
            this.checkPressed((Entity) null, level, pos, state, i);
        }

    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (!level.isClientSide()) {
            int i = this.getSignalForState(state);

            if (i == 0) {
                this.checkPressed(entity, level, pos, state, i);
            }

        }
    }

    private void checkPressed(@Nullable Entity sourceEntity, Level level, BlockPos pos, BlockState state, int oldSignal) {
        int j = this.getSignalStrength(level, pos);
        boolean flag = oldSignal > 0;
        boolean flag1 = j > 0;

        if (oldSignal != j) {
            BlockState blockstate1 = this.setSignalForState(state, j);

            level.setBlock(pos, blockstate1, 2);
            this.updateNeighbours(level, pos);
            level.setBlocksDirty(pos, state, blockstate1);
        }

        if (!flag1 && flag) {
            level.playSound((Entity) null, pos, this.type.pressurePlateClickOff(), SoundSource.BLOCKS);
            level.gameEvent(sourceEntity, (Holder) GameEvent.BLOCK_DEACTIVATE, pos);
        } else if (flag1 && !flag) {
            level.playSound((Entity) null, pos, this.type.pressurePlateClickOn(), SoundSource.BLOCKS);
            level.gameEvent(sourceEntity, (Holder) GameEvent.BLOCK_ACTIVATE, pos);
        }

        if (flag1) {
            level.scheduleTick(new BlockPos(pos), (Block) this, this.getPressedTime());
        }

    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!movedByPiston && this.getSignalForState(state) > 0) {
            this.updateNeighbours(level, pos);
        }

    }

    protected void updateNeighbours(Level level, BlockPos pos) {
        level.updateNeighborsAt(pos, this);
        level.updateNeighborsAt(pos.below(), this);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return this.getSignalForState(state);
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction == Direction.UP ? this.getSignalForState(state) : 0;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    protected static int getEntityCount(Level level, AABB entityDetectionBox, Class<? extends Entity> entityClass) {
        return level.getEntitiesOfClass(entityClass, entityDetectionBox, EntitySelector.NO_SPECTATORS.and((entity) -> {
            return !entity.isIgnoringBlockTriggers();
        })).size();
    }

    protected abstract int getSignalStrength(Level level, BlockPos pos);

    protected abstract int getSignalForState(BlockState state);

    protected abstract BlockState setSignalForState(BlockState state, int signal);
}
