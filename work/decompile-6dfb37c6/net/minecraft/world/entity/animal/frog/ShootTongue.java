package net.minecraft.world.entity.animal.frog;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.pathfinder.Path;

public class ShootTongue extends Behavior<Frog> {

    public static final int TIME_OUT_DURATION = 100;
    public static final int CATCH_ANIMATION_DURATION = 6;
    public static final int TONGUE_ANIMATION_DURATION = 10;
    private static final float EATING_DISTANCE = 1.75F;
    private static final float EATING_MOVEMENT_FACTOR = 0.75F;
    public static final int UNREACHABLE_TONGUE_TARGETS_COOLDOWN_DURATION = 100;
    public static final int MAX_UNREACHBLE_TONGUE_TARGETS_IN_MEMORY = 5;
    private int eatAnimationTimer;
    private int calculatePathCounter;
    private final SoundEvent tongueSound;
    private final SoundEvent eatSound;
    private ShootTongue.State state;

    public ShootTongue(SoundEvent tongueSound, SoundEvent eatSound) {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT, MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT), 100);
        this.state = ShootTongue.State.DONE;
        this.tongueSound = tongueSound;
        this.eatSound = eatSound;
    }

    protected boolean checkExtraStartConditions(ServerLevel level, Frog body) {
        LivingEntity livingentity = (LivingEntity) body.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();
        boolean flag = this.canPathfindToTarget(body, livingentity);

        if (!flag) {
            body.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            this.addUnreachableTargetToMemory(body, livingentity);
        }

        return flag && body.getPose() != Pose.CROAKING && Frog.canEat(livingentity);
    }

    protected boolean canStillUse(ServerLevel level, Frog body, long timestamp) {
        return body.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET) && this.state != ShootTongue.State.DONE && !body.getBrain().hasMemoryValue(MemoryModuleType.IS_PANICKING);
    }

    protected void start(ServerLevel level, Frog body, long timestamp) {
        LivingEntity livingentity = (LivingEntity) body.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();

        BehaviorUtils.lookAtEntity(body, livingentity);
        body.setTongueTarget(livingentity);
        body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(livingentity.position(), 2.0F, 0));
        this.calculatePathCounter = 10;
        this.state = ShootTongue.State.MOVE_TO_TARGET;
    }

    protected void stop(ServerLevel level, Frog body, long timestamp) {
        body.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        body.eraseTongueTarget();
        body.setPose(Pose.STANDING);
    }

    private void eatEntity(ServerLevel level, Frog body) {
        level.playSound((Entity) null, (Entity) body, this.eatSound, SoundSource.NEUTRAL, 2.0F, 1.0F);
        Optional<Entity> optional = body.getTongueTarget();

        if (optional.isPresent()) {
            Entity entity = (Entity) optional.get();

            if (entity.isAlive()) {
                body.doHurtTarget(level, entity);
                if (!entity.isAlive()) {
                    entity.remove(Entity.RemovalReason.KILLED);
                }
            }
        }

    }

    protected void tick(ServerLevel level, Frog body, long timestamp) {
        LivingEntity livingentity = (LivingEntity) body.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();

        body.setTongueTarget(livingentity);
        switch (this.state.ordinal()) {
            case 0:
                if (livingentity.distanceTo(body) < 1.75F) {
                    level.playSound((Entity) null, (Entity) body, this.tongueSound, SoundSource.NEUTRAL, 2.0F, 1.0F);
                    body.setPose(Pose.USING_TONGUE);
                    livingentity.setDeltaMovement(livingentity.position().vectorTo(body.position()).normalize().scale(0.75D));
                    this.eatAnimationTimer = 0;
                    this.state = ShootTongue.State.CATCH_ANIMATION;
                } else if (this.calculatePathCounter <= 0) {
                    body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(livingentity.position(), 2.0F, 0));
                    this.calculatePathCounter = 10;
                } else {
                    --this.calculatePathCounter;
                }
                break;
            case 1:
                if (this.eatAnimationTimer++ >= 6) {
                    this.state = ShootTongue.State.EAT_ANIMATION;
                    this.eatEntity(level, body);
                }
                break;
            case 2:
                if (this.eatAnimationTimer >= 10) {
                    this.state = ShootTongue.State.DONE;
                } else {
                    ++this.eatAnimationTimer;
                }
            case 3:
        }

    }

    private boolean canPathfindToTarget(Frog body, LivingEntity target) {
        Path path = body.getNavigation().createPath(target, 0);

        return path != null && path.getDistToTarget() < 1.75F;
    }

    private void addUnreachableTargetToMemory(Frog body, LivingEntity entity) {
        List<UUID> list = (List) body.getBrain().getMemory(MemoryModuleType.UNREACHABLE_TONGUE_TARGETS).orElseGet(ArrayList::new);
        boolean flag = !list.contains(entity.getUUID());

        if (list.size() == 5 && flag) {
            list.remove(0);
        }

        if (flag) {
            list.add(entity.getUUID());
        }

        body.getBrain().setMemoryWithExpiry(MemoryModuleType.UNREACHABLE_TONGUE_TARGETS, list, 100L);
    }

    private static enum State {

        MOVE_TO_TARGET, CATCH_ANIMATION, EAT_ANIMATION, DONE;

        private State() {}
    }
}
