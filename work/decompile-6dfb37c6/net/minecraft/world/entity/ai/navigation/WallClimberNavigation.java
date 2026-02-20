package net.minecraft.world.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import org.jspecify.annotations.Nullable;

public class WallClimberNavigation extends GroundPathNavigation {

    private @Nullable BlockPos pathToPosition;

    public WallClimberNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    public Path createPath(BlockPos pos, int reachRange) {
        this.pathToPosition = pos;
        return super.createPath(pos, reachRange);
    }

    @Override
    public Path createPath(Entity target, int reachRange) {
        this.pathToPosition = target.blockPosition();
        return super.createPath(target, reachRange);
    }

    @Override
    public boolean moveTo(Entity target, double speedModifier) {
        Path path = this.createPath(target, 0);

        if (path != null) {
            return this.moveTo(path, speedModifier);
        } else {
            this.pathToPosition = target.blockPosition();
            this.speedModifier = speedModifier;
            return true;
        }
    }

    @Override
    public void tick() {
        if (!this.isDone()) {
            super.tick();
        } else {
            if (this.pathToPosition != null) {
                if (!this.pathToPosition.closerToCenterThan(this.mob.position(), (double) this.mob.getBbWidth()) && (this.mob.getY() <= (double) this.pathToPosition.getY() || !BlockPos.containing((double) this.pathToPosition.getX(), this.mob.getY(), (double) this.pathToPosition.getZ()).closerToCenterThan(this.mob.position(), (double) this.mob.getBbWidth()))) {
                    this.mob.getMoveControl().setWantedPosition((double) this.pathToPosition.getX(), (double) this.pathToPosition.getY(), (double) this.pathToPosition.getZ(), this.speedModifier);
                } else {
                    this.pathToPosition = null;
                }
            }

        }
    }
}
