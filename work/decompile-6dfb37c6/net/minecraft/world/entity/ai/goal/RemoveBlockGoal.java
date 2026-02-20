package net.minecraft.world.entity.ai.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class RemoveBlockGoal extends MoveToBlockGoal {

    private final Block blockToRemove;
    private final Mob removerMob;
    private int ticksSinceReachedGoal;
    private static final int WAIT_AFTER_BLOCK_FOUND = 20;

    public RemoveBlockGoal(Block blockToRemove, PathfinderMob mob, double speedModifier, int verticalSearchRange) {
        super(mob, speedModifier, 24, verticalSearchRange);
        this.blockToRemove = blockToRemove;
        this.removerMob = mob;
    }

    @Override
    public boolean canUse() {
        if (!(Boolean) getServerLevel((Entity) this.removerMob).getGameRules().get(GameRules.MOB_GRIEFING)) {
            return false;
        } else if (this.nextStartTick > 0) {
            --this.nextStartTick;
            return false;
        } else if (this.findNearestBlock()) {
            this.nextStartTick = reducedTickDelay(20);
            return true;
        } else {
            this.nextStartTick = this.nextStartTick(this.mob);
            return false;
        }
    }

    @Override
    public void stop() {
        super.stop();
        this.removerMob.fallDistance = 1.0D;
    }

    @Override
    public void start() {
        super.start();
        this.ticksSinceReachedGoal = 0;
    }

    public void playDestroyProgressSound(LevelAccessor level, BlockPos pos) {}

    public void playBreakSound(Level level, BlockPos pos) {}

    @Override
    public void tick() {
        super.tick();
        Level level = this.removerMob.level();
        BlockPos blockpos = this.removerMob.blockPosition();
        BlockPos blockpos1 = this.getPosWithBlock(blockpos, level);
        RandomSource randomsource = this.removerMob.getRandom();

        if (this.isReachedTarget() && blockpos1 != null) {
            if (this.ticksSinceReachedGoal > 0) {
                Vec3 vec3 = this.removerMob.getDeltaMovement();

                this.removerMob.setDeltaMovement(vec3.x, 0.3D, vec3.z);
                if (!level.isClientSide()) {
                    double d0 = 0.08D;

                    ((ServerLevel) level).sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.EGG)), (double) blockpos1.getX() + 0.5D, (double) blockpos1.getY() + 0.7D, (double) blockpos1.getZ() + 0.5D, 3, ((double) randomsource.nextFloat() - 0.5D) * 0.08D, ((double) randomsource.nextFloat() - 0.5D) * 0.08D, ((double) randomsource.nextFloat() - 0.5D) * 0.08D, (double) 0.15F);
                }
            }

            if (this.ticksSinceReachedGoal % 2 == 0) {
                Vec3 vec31 = this.removerMob.getDeltaMovement();

                this.removerMob.setDeltaMovement(vec31.x, -0.3D, vec31.z);
                if (this.ticksSinceReachedGoal % 6 == 0) {
                    this.playDestroyProgressSound(level, this.blockPos);
                }
            }

            if (this.ticksSinceReachedGoal > 60) {
                level.removeBlock(blockpos1, false);
                if (!level.isClientSide()) {
                    for (int i = 0; i < 20; ++i) {
                        double d1 = randomsource.nextGaussian() * 0.02D;
                        double d2 = randomsource.nextGaussian() * 0.02D;
                        double d3 = randomsource.nextGaussian() * 0.02D;

                        ((ServerLevel) level).sendParticles(ParticleTypes.POOF, (double) blockpos1.getX() + 0.5D, (double) blockpos1.getY(), (double) blockpos1.getZ() + 0.5D, 1, d1, d2, d3, (double) 0.15F);
                    }

                    this.playBreakSound(level, blockpos1);
                }
            }

            ++this.ticksSinceReachedGoal;
        }

    }

    private @Nullable BlockPos getPosWithBlock(BlockPos pos, BlockGetter level) {
        if (level.getBlockState(pos).is(this.blockToRemove)) {
            return pos;
        } else {
            BlockPos[] ablockpos = new BlockPos[]{pos.below(), pos.west(), pos.east(), pos.north(), pos.south(), pos.below().below()};

            for (BlockPos blockpos1 : ablockpos) {
                if (level.getBlockState(blockpos1).is(this.blockToRemove)) {
                    return blockpos1;
                }
            }

            return null;
        }
    }

    @Override
    protected boolean isValidTarget(LevelReader level, BlockPos pos) {
        ChunkAccess chunkaccess = level.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), ChunkStatus.FULL, false);

        return chunkaccess == null ? false : chunkaccess.getBlockState(pos).is(this.blockToRemove) && chunkaccess.getBlockState(pos.above()).isAir() && chunkaccess.getBlockState(pos.above(2)).isAir();
    }
}
