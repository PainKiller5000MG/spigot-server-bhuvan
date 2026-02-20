package net.minecraft.world.entity.ai.goal;

import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class GolemRandomStrollInVillageGoal extends RandomStrollGoal {

    private static final int POI_SECTION_SCAN_RADIUS = 2;
    private static final int VILLAGER_SCAN_RADIUS = 32;
    private static final int RANDOM_POS_XY_DISTANCE = 10;
    private static final int RANDOM_POS_Y_DISTANCE = 7;

    public GolemRandomStrollInVillageGoal(PathfinderMob mob, double speedModifier) {
        super(mob, speedModifier, 240, false);
    }

    @Override
    protected @Nullable Vec3 getPosition() {
        float f = this.mob.level().random.nextFloat();

        if (this.mob.level().random.nextFloat() < 0.3F) {
            return this.getPositionTowardsAnywhere();
        } else {
            Vec3 vec3;

            if (f < 0.7F) {
                vec3 = this.getPositionTowardsVillagerWhoWantsGolem();
                if (vec3 == null) {
                    vec3 = this.getPositionTowardsPoi();
                }
            } else {
                vec3 = this.getPositionTowardsPoi();
                if (vec3 == null) {
                    vec3 = this.getPositionTowardsVillagerWhoWantsGolem();
                }
            }

            return vec3 == null ? this.getPositionTowardsAnywhere() : vec3;
        }
    }

    private @Nullable Vec3 getPositionTowardsAnywhere() {
        return LandRandomPos.getPos(this.mob, 10, 7);
    }

    private @Nullable Vec3 getPositionTowardsVillagerWhoWantsGolem() {
        ServerLevel serverlevel = (ServerLevel) this.mob.level();
        List<Villager> list = serverlevel.getEntities(EntityType.VILLAGER, this.mob.getBoundingBox().inflate(32.0D), this::doesVillagerWantGolem);

        if (list.isEmpty()) {
            return null;
        } else {
            Villager villager = (Villager) list.get(this.mob.level().random.nextInt(list.size()));
            Vec3 vec3 = villager.position();

            return LandRandomPos.getPosTowards(this.mob, 10, 7, vec3);
        }
    }

    private @Nullable Vec3 getPositionTowardsPoi() {
        SectionPos sectionpos = this.getRandomVillageSection();

        if (sectionpos == null) {
            return null;
        } else {
            BlockPos blockpos = this.getRandomPoiWithinSection(sectionpos);

            return blockpos == null ? null : LandRandomPos.getPosTowards(this.mob, 10, 7, Vec3.atBottomCenterOf(blockpos));
        }
    }

    private @Nullable SectionPos getRandomVillageSection() {
        ServerLevel serverlevel = (ServerLevel) this.mob.level();
        List<SectionPos> list = (List) SectionPos.cube(SectionPos.of((EntityAccess) this.mob), 2).filter((sectionpos) -> {
            return serverlevel.sectionsToVillage(sectionpos) == 0;
        }).collect(Collectors.toList());

        return list.isEmpty() ? null : (SectionPos) list.get(serverlevel.random.nextInt(list.size()));
    }

    private @Nullable BlockPos getRandomPoiWithinSection(SectionPos sectionPos) {
        ServerLevel serverlevel = (ServerLevel) this.mob.level();
        PoiManager poimanager = serverlevel.getPoiManager();
        List<BlockPos> list = (List) poimanager.getInRange((holder) -> {
            return true;
        }, sectionPos.center(), 8, PoiManager.Occupancy.IS_OCCUPIED).map(PoiRecord::getPos).collect(Collectors.toList());

        return list.isEmpty() ? null : (BlockPos) list.get(serverlevel.random.nextInt(list.size()));
    }

    private boolean doesVillagerWantGolem(Villager villager) {
        return villager.wantsToSpawnGolem(this.mob.level().getGameTime());
    }
}
