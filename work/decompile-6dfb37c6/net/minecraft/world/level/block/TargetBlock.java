package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class TargetBlock extends Block {

    public static final MapCodec<TargetBlock> CODEC = simpleCodec(TargetBlock::new);
    private static final IntegerProperty OUTPUT_POWER = BlockStateProperties.POWER;
    private static final int ACTIVATION_TICKS_ARROWS = 20;
    private static final int ACTIVATION_TICKS_OTHER = 8;

    @Override
    public MapCodec<TargetBlock> codec() {
        return TargetBlock.CODEC;
    }

    public TargetBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) (this.stateDefinition.any()).setValue(TargetBlock.OUTPUT_POWER, 0));
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hitResult, Projectile projectile) {
        int i = updateRedstoneOutput(level, state, hitResult, projectile);
        Entity entity = projectile.getOwner();

        if (entity instanceof ServerPlayer serverplayer) {
            serverplayer.awardStat(Stats.TARGET_HIT);
            CriteriaTriggers.TARGET_BLOCK_HIT.trigger(serverplayer, projectile, hitResult.getLocation(), i);
        }

    }

    private static int updateRedstoneOutput(LevelAccessor level, BlockState state, BlockHitResult hitResult, Entity entity) {
        int i = getRedstoneStrength(hitResult, hitResult.getLocation());
        int j = entity instanceof AbstractArrow ? 20 : 8;

        if (!level.getBlockTicks().hasScheduledTick(hitResult.getBlockPos(), state.getBlock())) {
            setOutputPower(level, state, i, hitResult.getBlockPos(), j);
        }

        return i;
    }

    private static int getRedstoneStrength(BlockHitResult hitResult, Vec3 hitLocation) {
        Direction direction = hitResult.getDirection();
        double d0 = Math.abs(Mth.frac(hitLocation.x) - 0.5D);
        double d1 = Math.abs(Mth.frac(hitLocation.y) - 0.5D);
        double d2 = Math.abs(Mth.frac(hitLocation.z) - 0.5D);
        Direction.Axis direction_axis = direction.getAxis();
        double d3;

        if (direction_axis == Direction.Axis.Y) {
            d3 = Math.max(d0, d2);
        } else if (direction_axis == Direction.Axis.Z) {
            d3 = Math.max(d0, d1);
        } else {
            d3 = Math.max(d1, d2);
        }

        return Math.max(1, Mth.ceil(15.0D * Mth.clamp((0.5D - d3) / 0.5D, 0.0D, 1.0D)));
    }

    private static void setOutputPower(LevelAccessor level, BlockState state, int outputStrength, BlockPos pos, int duration) {
        level.setBlock(pos, (BlockState) state.setValue(TargetBlock.OUTPUT_POWER, outputStrength), 3);
        level.scheduleTick(pos, state.getBlock(), duration);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if ((Integer) state.getValue(TargetBlock.OUTPUT_POWER) != 0) {
            level.setBlock(pos, (BlockState) state.setValue(TargetBlock.OUTPUT_POWER, 0), 3);
        }

    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return (Integer) state.getValue(TargetBlock.OUTPUT_POWER);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TargetBlock.OUTPUT_POWER);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            if ((Integer) state.getValue(TargetBlock.OUTPUT_POWER) > 0 && !level.getBlockTicks().hasScheduledTick(pos, this)) {
                level.setBlock(pos, (BlockState) state.setValue(TargetBlock.OUTPUT_POWER, 0), 18);
            }

        }
    }
}
