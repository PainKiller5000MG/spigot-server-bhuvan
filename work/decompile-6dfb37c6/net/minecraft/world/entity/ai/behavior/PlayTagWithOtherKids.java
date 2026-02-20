package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.Maps;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

public class PlayTagWithOtherKids {

    private static final int MAX_FLEE_XZ_DIST = 20;
    private static final int MAX_FLEE_Y_DIST = 8;
    private static final float FLEE_SPEED_MODIFIER = 0.6F;
    private static final float CHASE_SPEED_MODIFIER = 0.6F;
    private static final int MAX_CHASERS_PER_TARGET = 5;
    private static final int AVERAGE_WAIT_TIME_BETWEEN_RUNS = 10;

    public PlayTagWithOtherKids() {}

    public static BehaviorControl<PathfinderMob> create() {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.present(MemoryModuleType.VISIBLE_VILLAGER_BABIES), behaviorbuilder_instance.absent(MemoryModuleType.WALK_TARGET), behaviorbuilder_instance.registered(MemoryModuleType.LOOK_TARGET), behaviorbuilder_instance.registered(MemoryModuleType.INTERACTION_TARGET)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1, memoryaccessor2, memoryaccessor3) -> {
                return (serverlevel, pathfindermob, i) -> {
                    if (serverlevel.getRandom().nextInt(10) != 0) {
                        return false;
                    } else {
                        List<LivingEntity> list = (List) behaviorbuilder_instance.get(memoryaccessor);
                        Optional<LivingEntity> optional = list.stream().filter((livingentity) -> {
                            return isFriendChasingMe(pathfindermob, livingentity);
                        }).findAny();

                        if (!optional.isPresent()) {
                            Optional<LivingEntity> optional1 = findSomeoneBeingChased(list);

                            if (optional1.isPresent()) {
                                chaseKid(memoryaccessor3, memoryaccessor2, memoryaccessor1, (LivingEntity) optional1.get());
                                return true;
                            } else {
                                list.stream().findAny().ifPresent((livingentity) -> {
                                    chaseKid(memoryaccessor3, memoryaccessor2, memoryaccessor1, livingentity);
                                });
                                return true;
                            }
                        } else {
                            for (int j = 0; j < 10; ++j) {
                                Vec3 vec3 = LandRandomPos.getPos(pathfindermob, 20, 8);

                                if (vec3 != null && serverlevel.isVillage(BlockPos.containing(vec3))) {
                                    memoryaccessor1.set(new WalkTarget(vec3, 0.6F, 0));
                                    break;
                                }
                            }

                            return true;
                        }
                    }
                };
            });
        });
    }

    private static void chaseKid(MemoryAccessor<?, LivingEntity> interactionTarget, MemoryAccessor<?, PositionTracker> lookTarget, MemoryAccessor<?, WalkTarget> walkTarget, LivingEntity kidToChase) {
        interactionTarget.set(kidToChase);
        lookTarget.set(new EntityTracker(kidToChase, true));
        walkTarget.set(new WalkTarget(new EntityTracker(kidToChase, false), 0.6F, 1));
    }

    private static Optional<LivingEntity> findSomeoneBeingChased(List<LivingEntity> friendsNearby) {
        Map<LivingEntity, Integer> map = checkHowManyChasersEachFriendHas(friendsNearby);

        return map.entrySet().stream().sorted(Comparator.comparingInt(Entry::getValue)).filter((entry) -> {
            return (Integer) entry.getValue() > 0 && (Integer) entry.getValue() <= 5;
        }).map(Entry::getKey).findFirst();
    }

    private static Map<LivingEntity, Integer> checkHowManyChasersEachFriendHas(List<LivingEntity> friendsNearby) {
        Map<LivingEntity, Integer> map = Maps.newHashMap();

        friendsNearby.stream().filter(PlayTagWithOtherKids::isChasingSomeone).forEach((livingentity) -> {
            map.compute(whoAreYouChasing(livingentity), (livingentity1, integer) -> {
                return integer == null ? 1 : integer + 1;
            });
        });
        return map;
    }

    private static LivingEntity whoAreYouChasing(LivingEntity friend) {
        return (LivingEntity) friend.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).get();
    }

    private static boolean isChasingSomeone(LivingEntity friend) {
        return friend.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).isPresent();
    }

    private static boolean isFriendChasingMe(LivingEntity me, LivingEntity friend) {
        return friend.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).filter((livingentity2) -> {
            return livingentity2 == me;
        }).isPresent();
    }
}
