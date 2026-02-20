package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DragonEggBlock extends FallingBlock {

    public static final MapCodec<DragonEggBlock> CODEC = simpleCodec(DragonEggBlock::new);
    private static final VoxelShape SHAPE = Block.column(14.0D, 0.0D, 16.0D);

    @Override
    public MapCodec<DragonEggBlock> codec() {
        return DragonEggBlock.CODEC;
    }

    public DragonEggBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return DragonEggBlock.SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        this.teleport(state, level, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        this.teleport(state, level, pos);
    }

    private void teleport(BlockState state, Level level, BlockPos pos) {
        WorldBorder worldborder = level.getWorldBorder();

        for (int i = 0; i < 1000; ++i) {
            BlockPos blockpos1 = pos.offset(level.random.nextInt(16) - level.random.nextInt(16), level.random.nextInt(8) - level.random.nextInt(8), level.random.nextInt(16) - level.random.nextInt(16));

            if (level.getBlockState(blockpos1).isAir() && worldborder.isWithinBounds(blockpos1) && !level.isOutsideBuildHeight(blockpos1)) {
                if (level.isClientSide()) {
                    for (int j = 0; j < 128; ++j) {
                        double d0 = level.random.nextDouble();
                        float f = (level.random.nextFloat() - 0.5F) * 0.2F;
                        float f1 = (level.random.nextFloat() - 0.5F) * 0.2F;
                        float f2 = (level.random.nextFloat() - 0.5F) * 0.2F;
                        double d1 = Mth.lerp(d0, (double) blockpos1.getX(), (double) pos.getX()) + (level.random.nextDouble() - 0.5D) + 0.5D;
                        double d2 = Mth.lerp(d0, (double) blockpos1.getY(), (double) pos.getY()) + level.random.nextDouble() - 0.5D;
                        double d3 = Mth.lerp(d0, (double) blockpos1.getZ(), (double) pos.getZ()) + (level.random.nextDouble() - 0.5D) + 0.5D;

                        level.addParticle(ParticleTypes.PORTAL, d1, d2, d3, (double) f, (double) f1, (double) f2);
                    }
                } else {
                    level.setBlock(blockpos1, state, 2);
                    level.removeBlock(pos, false);
                }

                return;
            }
        }

    }

    @Override
    protected int getDelayAfterPlace() {
        return 5;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public int getDustColor(BlockState blockState, BlockGetter level, BlockPos pos) {
        return -16777216;
    }
}
