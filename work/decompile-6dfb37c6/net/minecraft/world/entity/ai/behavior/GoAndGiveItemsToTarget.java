package net.minecraft.world.entity.ai.behavior;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.allay.AllayAi;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class GoAndGiveItemsToTarget<E extends LivingEntity & InventoryCarrier> extends Behavior<E> {

    private static final int CLOSE_ENOUGH_DISTANCE_TO_TARGET = 3;
    private static final int ITEM_PICKUP_COOLDOWN_AFTER_THROWING = 60;
    private final Function<LivingEntity, Optional<PositionTracker>> targetPositionGetter;
    private final float speedModifier;

    public GoAndGiveItemsToTarget(Function<LivingEntity, Optional<PositionTracker>> targetPositionGetter, float speedModifier, int timeoutDuration) {
        super(Map.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS, MemoryStatus.REGISTERED), timeoutDuration);
        this.targetPositionGetter = targetPositionGetter;
        this.speedModifier = speedModifier;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, E body) {
        return this.canThrowItemToTarget(body);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, E body, long timestamp) {
        return this.canThrowItemToTarget(body);
    }

    @Override
    protected void start(ServerLevel level, E body, long timestamp) {
        ((Optional) this.targetPositionGetter.apply(body)).ifPresent((positiontracker) -> {
            BehaviorUtils.setWalkAndLookTargetMemories(body, positiontracker, this.speedModifier, 3);
        });
    }

    @Override
    protected void tick(ServerLevel level, E body, long timestamp) {
        Optional<PositionTracker> optional = (Optional) this.targetPositionGetter.apply(body);

        if (!optional.isEmpty()) {
            PositionTracker positiontracker = (PositionTracker) optional.get();
            double d0 = positiontracker.currentPosition().distanceTo(body.getEyePosition());

            if (d0 < 3.0D) {
                ItemStack itemstack = ((InventoryCarrier) body).getInventory().removeItem(0, 1);

                if (!itemstack.isEmpty()) {
                    throwItem(body, itemstack, getThrowPosition(positiontracker));
                    if (body instanceof Allay) {
                        Allay allay = (Allay) body;

                        AllayAi.getLikedPlayer(allay).ifPresent((serverplayer) -> {
                            this.triggerDropItemOnBlock(positiontracker, itemstack, serverplayer);
                        });
                    }

                    body.getBrain().setMemory(MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS, 60);
                }
            }

        }
    }

    private void triggerDropItemOnBlock(PositionTracker depositTarget, ItemStack item, ServerPlayer player) {
        BlockPos blockpos = depositTarget.currentBlockPosition().below();

        CriteriaTriggers.ALLAY_DROP_ITEM_ON_BLOCK.trigger(player, blockpos, item);
    }

    private boolean canThrowItemToTarget(E body) {
        if (((InventoryCarrier) body).getInventory().isEmpty()) {
            return false;
        } else {
            Optional<PositionTracker> optional = (Optional) this.targetPositionGetter.apply(body);

            return optional.isPresent();
        }
    }

    private static Vec3 getThrowPosition(PositionTracker depositTarget) {
        return depositTarget.currentPosition().add(0.0D, 1.0D, 0.0D);
    }

    public static void throwItem(LivingEntity thrower, ItemStack item, Vec3 targetPos) {
        Vec3 vec31 = new Vec3((double) 0.2F, (double) 0.3F, (double) 0.2F);

        BehaviorUtils.throwItem(thrower, item, targetPos, vec31, 0.2F);
        Level level = thrower.level();

        if (level.getGameTime() % 7L == 0L && level.random.nextDouble() < 0.9D) {
            float f = (Float) Util.getRandom(Allay.THROW_SOUND_PITCHES, level.getRandom());

            level.playSound((Entity) null, (Entity) thrower, SoundEvents.ALLAY_THROW, SoundSource.NEUTRAL, 1.0F, f);
        }

    }
}
