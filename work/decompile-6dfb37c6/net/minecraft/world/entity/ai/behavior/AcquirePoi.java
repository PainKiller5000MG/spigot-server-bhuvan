package net.minecraft.world.entity.ai.behavior;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.pathfinder.Path;
import org.apache.commons.lang3.mutable.MutableLong;
import org.jspecify.annotations.Nullable;

public class AcquirePoi {

    public static final int SCAN_RANGE = 48;

    public AcquirePoi() {}

    public static BehaviorControl<PathfinderMob> create(Predicate<Holder<PoiType>> poiType, MemoryModuleType<GlobalPos> memoryToAcquire, boolean onlyIfAdult, Optional<Byte> onPoiAcquisitionEvent, BiPredicate<ServerLevel, BlockPos> validPoi) {
        return create(poiType, memoryToAcquire, memoryToAcquire, onlyIfAdult, onPoiAcquisitionEvent, validPoi);
    }

    public static BehaviorControl<PathfinderMob> create(Predicate<Holder<PoiType>> poiType, MemoryModuleType<GlobalPos> memoryToAcquire, boolean onlyIfAdult, Optional<Byte> onPoiAcquisitionEvent) {
        return create(poiType, memoryToAcquire, memoryToAcquire, onlyIfAdult, onPoiAcquisitionEvent, (serverlevel, blockpos) -> {
            return true;
        });
    }

    public static BehaviorControl<PathfinderMob> create(Predicate<Holder<PoiType>> poiType, MemoryModuleType<GlobalPos> memoryToValidate, MemoryModuleType<GlobalPos> memoryToAcquire, boolean onlyIfAdult, Optional<Byte> onPoiAcquisitionEvent, BiPredicate<ServerLevel, BlockPos> validPoi) {
        int i = 5;
        int j = 20;
        MutableLong mutablelong = new MutableLong(0L);
        Long2ObjectMap<AcquirePoi.JitteredLinearRetry> long2objectmap = new Long2ObjectOpenHashMap();
        OneShot<PathfinderMob> oneshot = BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.absent(memoryToAcquire)).apply(behaviorbuilder_instance, (memoryaccessor) -> {
                return (serverlevel, pathfindermob, k) -> {
                    if (onlyIfAdult && pathfindermob.isBaby()) {
                        return false;
                    } else if (mutablelong.longValue() == 0L) {
                        mutablelong.setValue(serverlevel.getGameTime() + (long) serverlevel.random.nextInt(20));
                        return false;
                    } else if (serverlevel.getGameTime() < mutablelong.longValue()) {
                        return false;
                    } else {
                        mutablelong.setValue(k + 20L + (long) serverlevel.getRandom().nextInt(20));
                        PoiManager poimanager = serverlevel.getPoiManager();

                        long2objectmap.long2ObjectEntrySet().removeIf((entry) -> {
                            return !((AcquirePoi.JitteredLinearRetry) entry.getValue()).isStillValid(k);
                        });
                        Predicate<BlockPos> predicate1 = (blockpos) -> {
                            AcquirePoi.JitteredLinearRetry acquirepoi_jitteredlinearretry = (AcquirePoi.JitteredLinearRetry) long2objectmap.get(blockpos.asLong());

                            if (acquirepoi_jitteredlinearretry == null) {
                                return true;
                            } else if (!acquirepoi_jitteredlinearretry.shouldRetry(k)) {
                                return false;
                            } else {
                                acquirepoi_jitteredlinearretry.markAttempt(k);
                                return true;
                            }
                        };
                        Set<Pair<Holder<PoiType>, BlockPos>> set = (Set) poimanager.findAllClosestFirstWithType(poiType, predicate1, pathfindermob.blockPosition(), 48, PoiManager.Occupancy.HAS_SPACE).limit(5L).filter((pair) -> {
                            return validPoi.test(serverlevel, (BlockPos) pair.getSecond());
                        }).collect(Collectors.toSet());
                        Path path = findPathToPois(pathfindermob, set);

                        if (path != null && path.canReach()) {
                            BlockPos blockpos = path.getTarget();

                            poimanager.getType(blockpos).ifPresent((holder) -> {
                                poimanager.take(poiType, (holder1, blockpos1) -> {
                                    return blockpos1.equals(blockpos);
                                }, blockpos, 1);
                                memoryaccessor.set(GlobalPos.of(serverlevel.dimension(), blockpos));
                                onPoiAcquisitionEvent.ifPresent((obyte) -> {
                                    serverlevel.broadcastEntityEvent(pathfindermob, obyte);
                                });
                                long2objectmap.clear();
                                serverlevel.debugSynchronizers().updatePoi(blockpos);
                            });
                        } else {
                            for (Pair<Holder<PoiType>, BlockPos> pair : set) {
                                long2objectmap.computeIfAbsent(((BlockPos) pair.getSecond()).asLong(), (l) -> {
                                    return new AcquirePoi.JitteredLinearRetry(serverlevel.random, k);
                                });
                            }
                        }

                        return true;
                    }
                };
            });
        });

        return memoryToAcquire == memoryToValidate ? oneshot : BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.absent(memoryToValidate)).apply(behaviorbuilder_instance, (memoryaccessor) -> {
                return oneshot;
            });
        });
    }

    public static @Nullable Path findPathToPois(Mob body, Set<Pair<Holder<PoiType>, BlockPos>> pois) {
        if (pois.isEmpty()) {
            return null;
        } else {
            Set<BlockPos> set1 = new HashSet();
            int i = 1;

            for (Pair<Holder<PoiType>, BlockPos> pair : pois) {
                i = Math.max(i, ((PoiType) ((Holder) pair.getFirst()).value()).validRange());
                set1.add((BlockPos) pair.getSecond());
            }

            return body.getNavigation().createPath(set1, i);
        }
    }

    private static class JitteredLinearRetry {

        private static final int MIN_INTERVAL_INCREASE = 40;
        private static final int MAX_INTERVAL_INCREASE = 80;
        private static final int MAX_RETRY_PATHFINDING_INTERVAL = 400;
        private final RandomSource random;
        private long previousAttemptTimestamp;
        private long nextScheduledAttemptTimestamp;
        private int currentDelay;

        JitteredLinearRetry(RandomSource random, long firstAttemptTimestamp) {
            this.random = random;
            this.markAttempt(firstAttemptTimestamp);
        }

        public void markAttempt(long timestamp) {
            this.previousAttemptTimestamp = timestamp;
            int j = this.currentDelay + this.random.nextInt(40) + 40;

            this.currentDelay = Math.min(j, 400);
            this.nextScheduledAttemptTimestamp = timestamp + (long) this.currentDelay;
        }

        public boolean isStillValid(long timestamp) {
            return timestamp - this.previousAttemptTimestamp < 400L;
        }

        public boolean shouldRetry(long timestamp) {
            return timestamp >= this.nextScheduledAttemptTimestamp;
        }

        public String toString() {
            return "RetryMarker{, previousAttemptAt=" + this.previousAttemptTimestamp + ", nextScheduledAttemptAt=" + this.nextScheduledAttemptTimestamp + ", currentDelay=" + this.currentDelay + "}";
        }
    }
}
