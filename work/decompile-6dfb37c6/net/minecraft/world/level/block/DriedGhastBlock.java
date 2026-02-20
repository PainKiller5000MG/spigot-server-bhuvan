package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class DriedGhastBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {

    public static final MapCodec<DriedGhastBlock> CODEC = simpleCodec(DriedGhastBlock::new);
    public static final int MAX_HYDRATION_LEVEL = 3;
    public static final IntegerProperty HYDRATION_LEVEL = BlockStateProperties.DRIED_GHAST_HYDRATION_LEVELS;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final int HYDRATION_TICK_DELAY = 5000;
    private static final VoxelShape SHAPE = Block.column(10.0D, 10.0D, 0.0D, 10.0D);

    @Override
    public MapCodec<DriedGhastBlock> codec() {
        return DriedGhastBlock.CODEC;
    }

    public DriedGhastBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(DriedGhastBlock.FACING, Direction.NORTH)).setValue(DriedGhastBlock.HYDRATION_LEVEL, 0)).setValue(DriedGhastBlock.WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DriedGhastBlock.FACING, DriedGhastBlock.HYDRATION_LEVEL, DriedGhastBlock.WATERLOGGED);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if ((Boolean) state.getValue(DriedGhastBlock.WATERLOGGED)) {
            ticks.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return DriedGhastBlock.SHAPE;
    }

    public int getHydrationLevel(BlockState state) {
        return (Integer) state.getValue(DriedGhastBlock.HYDRATION_LEVEL);
    }

    private boolean isReadyToSpawn(BlockState state) {
        return this.getHydrationLevel(state) == 3;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos position, RandomSource random) {
        if ((Boolean) state.getValue(DriedGhastBlock.WATERLOGGED)) {
            this.tickWaterlogged(state, level, position, random);
        } else {
            int i = this.getHydrationLevel(state);

            if (i > 0) {
                level.setBlock(position, (BlockState) state.setValue(DriedGhastBlock.HYDRATION_LEVEL, i - 1), 2);
                level.gameEvent(GameEvent.BLOCK_CHANGE, position, GameEvent.Context.of(state));
            }

        }
    }

    private void tickWaterlogged(BlockState state, ServerLevel level, BlockPos position, RandomSource random) {
        if (!this.isReadyToSpawn(state)) {
            level.playSound((Entity) null, position, SoundEvents.DRIED_GHAST_TRANSITION, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.setBlock(position, (BlockState) state.setValue(DriedGhastBlock.HYDRATION_LEVEL, this.getHydrationLevel(state) + 1), 2);
            level.gameEvent(GameEvent.BLOCK_CHANGE, position, GameEvent.Context.of(state));
        } else {
            this.spawnGhastling(level, position, state);
        }

    }

    private void spawnGhastling(ServerLevel level, BlockPos position, BlockState state) {
        level.removeBlock(position, false);
        HappyGhast happyghast = EntityType.HAPPY_GHAST.create(level, EntitySpawnReason.BREEDING);

        if (happyghast != null) {
            Vec3 vec3 = position.getBottomCenter();

            happyghast.setBaby(true);
            float f = Direction.getYRot((Direction) state.getValue(DriedGhastBlock.FACING));

            happyghast.setYHeadRot(f);
            happyghast.snapTo(vec3.x(), vec3.y(), vec3.z(), f, 0.0F);
            level.addFreshEntity(happyghast);
            level.playSound((Entity) null, (Entity) happyghast, SoundEvents.GHASTLING_SPAWN, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double d0 = (double) pos.getX() + 0.5D;
        double d1 = (double) pos.getY() + 0.5D;
        double d2 = (double) pos.getZ() + 0.5D;

        if (!(Boolean) state.getValue(DriedGhastBlock.WATERLOGGED)) {
            if (random.nextInt(40) == 0 && level.getBlockState(pos.below()).is(BlockTags.TRIGGERS_AMBIENT_DRIED_GHAST_BLOCK_SOUNDS)) {
                level.playLocalSound(d0, d1, d2, SoundEvents.DRIED_GHAST_AMBIENT, SoundSource.BLOCKS, 1.0F, 1.0F, false);
            }

            if (random.nextInt(6) == 0) {
                level.addParticle(ParticleTypes.WHITE_SMOKE, d0, d1, d2, 0.0D, 0.02D, 0.0D);
            }
        } else {
            if (random.nextInt(40) == 0) {
                level.playLocalSound(d0, d1, d2, SoundEvents.DRIED_GHAST_AMBIENT_WATER, SoundSource.BLOCKS, 1.0F, 1.0F, false);
            }

            if (random.nextInt(6) == 0) {
                level.addParticle(ParticleTypes.HAPPY_VILLAGER, d0 + (double) ((random.nextFloat() * 2.0F - 1.0F) / 3.0F), d1 + 0.4D, d2 + (double) ((random.nextFloat() * 2.0F - 1.0F) / 3.0F), 0.0D, (double) random.nextFloat(), 0.0D);
            }
        }

    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (((Boolean) state.getValue(DriedGhastBlock.WATERLOGGED) || (Integer) state.getValue(DriedGhastBlock.HYDRATION_LEVEL) > 0) && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, (Block) this, 5000);
        }

    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidstate = context.getLevel().getFluidState(context.getClickedPos());
        boolean flag = fluidstate.getType() == Fluids.WATER;

        return (BlockState) ((BlockState) super.getStateForPlacement(context).setValue(DriedGhastBlock.WATERLOGGED, flag)).setValue(DriedGhastBlock.FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(DriedGhastBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        if (!(Boolean) state.getValue(BlockStateProperties.WATERLOGGED) && fluidState.getType() == Fluids.WATER) {
            if (!level.isClientSide()) {
                level.setBlock(pos, (BlockState) state.setValue(BlockStateProperties.WATERLOGGED, true), 3);
                level.scheduleTick(pos, fluidState.getType(), fluidState.getType().getTickDelay(level));
                level.playSound((Entity) null, pos, SoundEvents.DRIED_GHAST_PLACE_IN_WATER, SoundSource.BLOCKS, 1.0F, 1.0F);
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, by, itemStack);
        level.playSound((Entity) null, pos, (Boolean) state.getValue(DriedGhastBlock.WATERLOGGED) ? SoundEvents.DRIED_GHAST_PLACE_IN_WATER : SoundEvents.DRIED_GHAST_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    @Override
    public boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
