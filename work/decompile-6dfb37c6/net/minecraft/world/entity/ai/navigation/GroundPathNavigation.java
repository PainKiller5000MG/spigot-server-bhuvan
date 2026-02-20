package net.minecraft.world.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

public class GroundPathNavigation extends PathNavigation {

    private boolean avoidSun;
    private boolean canPathToTargetsBelowSurface;

    public GroundPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new WalkNodeEvaluator();
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    @Override
    protected boolean canUpdatePath() {
        return this.mob.onGround() || this.mob.isInLiquid() || this.mob.isPassenger();
    }

    @Override
    protected Vec3 getTempMobPos() {
        return new Vec3(this.mob.getX(), (double) this.getSurfaceY(), this.mob.getZ());
    }

    @Override
    public Path createPath(BlockPos pos, int reachRange) {
        LevelChunk levelchunk = this.level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));

        if (levelchunk == null) {
            return null;
        } else {
            if (!this.canPathToTargetsBelowSurface) {
                pos = this.findSurfacePosition(levelchunk, pos, reachRange);
            }

            return super.createPath(pos, reachRange);
        }
    }

    final BlockPos findSurfacePosition(LevelChunk chunk, BlockPos pos, int reachRange) {
        if (chunk.getBlockState(pos).isAir()) {
            BlockPos.MutableBlockPos blockpos_mutableblockpos = pos.mutable().move(Direction.DOWN);

            while (blockpos_mutableblockpos.getY() >= this.level.getMinY() && chunk.getBlockState(blockpos_mutableblockpos).isAir()) {
                blockpos_mutableblockpos.move(Direction.DOWN);
            }

            if (blockpos_mutableblockpos.getY() >= this.level.getMinY()) {
                return blockpos_mutableblockpos.above();
            }

            blockpos_mutableblockpos.setY(pos.getY() + 1);

            while (blockpos_mutableblockpos.getY() <= this.level.getMaxY() && chunk.getBlockState(blockpos_mutableblockpos).isAir()) {
                blockpos_mutableblockpos.move(Direction.UP);
            }

            pos = blockpos_mutableblockpos;
        }

        if (!chunk.getBlockState(pos).isSolid()) {
            return pos;
        } else {
            BlockPos.MutableBlockPos blockpos_mutableblockpos1 = pos.mutable().move(Direction.UP);

            while (blockpos_mutableblockpos1.getY() <= this.level.getMaxY() && chunk.getBlockState(blockpos_mutableblockpos1).isSolid()) {
                blockpos_mutableblockpos1.move(Direction.UP);
            }

            return blockpos_mutableblockpos1.immutable();
        }
    }

    @Override
    public Path createPath(Entity target, int reachRange) {
        return this.createPath(target.blockPosition(), reachRange);
    }

    private int getSurfaceY() {
        if (this.mob.isInWater() && this.canFloat()) {
            int i = this.mob.getBlockY();
            BlockState blockstate = this.level.getBlockState(BlockPos.containing(this.mob.getX(), (double) i, this.mob.getZ()));
            int j = 0;

            while (blockstate.is(Blocks.WATER)) {
                ++i;
                blockstate = this.level.getBlockState(BlockPos.containing(this.mob.getX(), (double) i, this.mob.getZ()));
                ++j;
                if (j > 16) {
                    return this.mob.getBlockY();
                }
            }

            return i;
        } else {
            return Mth.floor(this.mob.getY() + 0.5D);
        }
    }

    @Override
    protected void trimPath() {
        super.trimPath();
        if (this.avoidSun) {
            if (this.level.canSeeSky(BlockPos.containing(this.mob.getX(), this.mob.getY() + 0.5D, this.mob.getZ()))) {
                return;
            }

            for (int i = 0; i < this.path.getNodeCount(); ++i) {
                Node node = this.path.getNode(i);

                if (this.level.canSeeSky(new BlockPos(node.x, node.y, node.z))) {
                    this.path.truncateNodes(i);
                    return;
                }
            }
        }

    }

    @Override
    public boolean canNavigateGround() {
        return true;
    }

    protected boolean hasValidPathType(PathType pathType) {
        return pathType == PathType.WATER ? false : (pathType == PathType.LAVA ? false : pathType != PathType.OPEN);
    }

    public void setAvoidSun(boolean avoidSun) {
        this.avoidSun = avoidSun;
    }

    public void setCanWalkOverFences(boolean canWalkOverFences) {
        this.nodeEvaluator.setCanWalkOverFences(canWalkOverFences);
    }

    public void setCanPathToTargetsBelowSurface(boolean canPathToTargetsBelowSurface) {
        this.canPathToTargetsBelowSurface = canPathToTargetsBelowSurface;
    }
}
