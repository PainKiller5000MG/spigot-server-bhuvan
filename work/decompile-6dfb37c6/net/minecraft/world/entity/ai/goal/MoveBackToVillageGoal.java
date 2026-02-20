package net.minecraft.world.entity.ai.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class MoveBackToVillageGoal extends RandomStrollGoal {

    private static final int MAX_XZ_DIST = 10;
    private static final int MAX_Y_DIST = 7;

    public MoveBackToVillageGoal(PathfinderMob mob, double speedModifier, boolean checkNoActionTime) {
        super(mob, speedModifier, 10, checkNoActionTime);
    }

    @Override
    public boolean canUse() {
        ServerLevel serverlevel = (ServerLevel) this.mob.level();
        BlockPos blockpos = this.mob.blockPosition();

        return serverlevel.isVillage(blockpos) ? false : super.canUse();
    }

    @Override
    protected @Nullable Vec3 getPosition() {
        ServerLevel serverlevel = (ServerLevel) this.mob.level();
        BlockPos blockpos = this.mob.blockPosition();
        SectionPos sectionpos = SectionPos.of(blockpos);
        SectionPos sectionpos1 = BehaviorUtils.findSectionClosestToVillage(serverlevel, sectionpos, 2);

        return sectionpos1 != sectionpos ? DefaultRandomPos.getPosTowards(this.mob, 10, 7, Vec3.atBottomCenterOf(sectionpos1.center()), (double) ((float) Math.PI / 2F)) : null;
    }
}
