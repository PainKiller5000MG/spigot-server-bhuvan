package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.InsideBlockEffectType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class BaseFireBlock extends Block {

    private static final int SECONDS_ON_FIRE = 8;
    private static final int MIN_FIRE_TICKS_TO_ADD = 1;
    private static final int MAX_FIRE_TICKS_TO_ADD = 3;
    private final float fireDamage;
    protected static final VoxelShape SHAPE = Block.column(16.0D, 0.0D, 1.0D);

    public BaseFireBlock(BlockBehaviour.Properties properties, float fireDamage) {
        super(properties);
        this.fireDamage = fireDamage;
    }

    @Override
    protected abstract MapCodec<? extends BaseFireBlock> codec();

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return getState(context.getLevel(), context.getClickedPos());
    }

    public static BlockState getState(BlockGetter level, BlockPos pos) {
        BlockPos blockpos1 = pos.below();
        BlockState blockstate = level.getBlockState(blockpos1);

        return SoulFireBlock.canSurviveOnBlock(blockstate) ? Blocks.SOUL_FIRE.defaultBlockState() : ((FireBlock) Blocks.FIRE).getStateForPlacement(level, pos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BaseFireBlock.SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(24) == 0) {
            level.playLocalSound((double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1.0F + random.nextFloat(), random.nextFloat() * 0.7F + 0.3F, false);
        }

        BlockPos blockpos1 = pos.below();
        BlockState blockstate1 = level.getBlockState(blockpos1);

        if (!this.canBurn(blockstate1) && !blockstate1.isFaceSturdy(level, blockpos1, Direction.UP)) {
            if (this.canBurn(level.getBlockState(pos.west()))) {
                for (int i = 0; i < 2; ++i) {
                    double d0 = (double) pos.getX() + random.nextDouble() * (double) 0.1F;
                    double d1 = (double) pos.getY() + random.nextDouble();
                    double d2 = (double) pos.getZ() + random.nextDouble();

                    level.addParticle(ParticleTypes.LARGE_SMOKE, d0, d1, d2, 0.0D, 0.0D, 0.0D);
                }
            }

            if (this.canBurn(level.getBlockState(pos.east()))) {
                for (int j = 0; j < 2; ++j) {
                    double d3 = (double) (pos.getX() + 1) - random.nextDouble() * (double) 0.1F;
                    double d4 = (double) pos.getY() + random.nextDouble();
                    double d5 = (double) pos.getZ() + random.nextDouble();

                    level.addParticle(ParticleTypes.LARGE_SMOKE, d3, d4, d5, 0.0D, 0.0D, 0.0D);
                }
            }

            if (this.canBurn(level.getBlockState(pos.north()))) {
                for (int k = 0; k < 2; ++k) {
                    double d6 = (double) pos.getX() + random.nextDouble();
                    double d7 = (double) pos.getY() + random.nextDouble();
                    double d8 = (double) pos.getZ() + random.nextDouble() * (double) 0.1F;

                    level.addParticle(ParticleTypes.LARGE_SMOKE, d6, d7, d8, 0.0D, 0.0D, 0.0D);
                }
            }

            if (this.canBurn(level.getBlockState(pos.south()))) {
                for (int l = 0; l < 2; ++l) {
                    double d9 = (double) pos.getX() + random.nextDouble();
                    double d10 = (double) pos.getY() + random.nextDouble();
                    double d11 = (double) (pos.getZ() + 1) - random.nextDouble() * (double) 0.1F;

                    level.addParticle(ParticleTypes.LARGE_SMOKE, d9, d10, d11, 0.0D, 0.0D, 0.0D);
                }
            }

            if (this.canBurn(level.getBlockState(pos.above()))) {
                for (int i1 = 0; i1 < 2; ++i1) {
                    double d12 = (double) pos.getX() + random.nextDouble();
                    double d13 = (double) (pos.getY() + 1) - random.nextDouble() * (double) 0.1F;
                    double d14 = (double) pos.getZ() + random.nextDouble();

                    level.addParticle(ParticleTypes.LARGE_SMOKE, d12, d13, d14, 0.0D, 0.0D, 0.0D);
                }
            }
        } else {
            for (int j1 = 0; j1 < 3; ++j1) {
                double d15 = (double) pos.getX() + random.nextDouble();
                double d16 = (double) pos.getY() + random.nextDouble() * 0.5D + 0.5D;
                double d17 = (double) pos.getZ() + random.nextDouble();

                level.addParticle(ParticleTypes.LARGE_SMOKE, d15, d16, d17, 0.0D, 0.0D, 0.0D);
            }
        }

    }

    protected abstract boolean canBurn(BlockState state);

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        effectApplier.apply(InsideBlockEffectType.CLEAR_FREEZE);
        effectApplier.apply(InsideBlockEffectType.FIRE_IGNITE);
        effectApplier.runAfter(InsideBlockEffectType.FIRE_IGNITE, (entity1) -> {
            entity1.hurt(entity1.level().damageSources().inFire(), this.fireDamage);
        });
    }

    public static void fireIgnite(Entity entity) {
        if (!entity.fireImmune()) {
            if (entity.getRemainingFireTicks() < 0) {
                entity.setRemainingFireTicks(entity.getRemainingFireTicks() + 1);
            } else if (entity instanceof ServerPlayer) {
                int i = entity.level().getRandom().nextInt(1, 3);

                entity.setRemainingFireTicks(entity.getRemainingFireTicks() + i);
            }

            if (entity.getRemainingFireTicks() >= 0) {
                entity.igniteForSeconds(8.0F);
            }
        }

    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock())) {
            if (inPortalDimension(level)) {
                Optional<PortalShape> optional = PortalShape.findEmptyPortalShape(level, pos, Direction.Axis.X);

                if (optional.isPresent()) {
                    ((PortalShape) optional.get()).createPortalBlocks(level);
                    return;
                }
            }

            if (!state.canSurvive(level, pos)) {
                level.removeBlock(pos, false);
            }

        }
    }

    private static boolean inPortalDimension(Level level) {
        return level.dimension() == Level.OVERWORLD || level.dimension() == Level.NETHER;
    }

    @Override
    protected void spawnDestroyParticles(Level level, Player player, BlockPos pos, BlockState state) {}

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            level.levelEvent((Entity) null, 1009, pos, 0);
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    public static boolean canBePlacedAt(Level level, BlockPos pos, Direction forwardDirection) {
        BlockState blockstate = level.getBlockState(pos);

        return !blockstate.isAir() ? false : getState(level, pos).canSurvive(level, pos) || isPortal(level, pos, forwardDirection);
    }

    private static boolean isPortal(Level level, BlockPos pos, Direction forwardDirection) {
        if (!inPortalDimension(level)) {
            return false;
        } else {
            BlockPos.MutableBlockPos blockpos_mutableblockpos = pos.mutable();
            boolean flag = false;

            for (Direction direction1 : Direction.values()) {
                if (level.getBlockState(blockpos_mutableblockpos.set(pos).move(direction1)).is(Blocks.OBSIDIAN)) {
                    flag = true;
                    break;
                }
            }

            if (!flag) {
                return false;
            } else {
                Direction.Axis direction_axis = forwardDirection.getAxis().isHorizontal() ? forwardDirection.getCounterClockWise().getAxis() : Direction.Plane.HORIZONTAL.getRandomAxis(level.random);

                return PortalShape.findEmptyPortalShape(level, pos, direction_axis).isPresent();
            }
        }
    }
}
