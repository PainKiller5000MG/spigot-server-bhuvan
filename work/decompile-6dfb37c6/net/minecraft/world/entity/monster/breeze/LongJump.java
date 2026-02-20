package net.minecraft.world.entity.monster.breeze;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Map;
import java.util.Optional;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.LongJumpUtil;
import net.minecraft.world.entity.ai.behavior.Swim;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class LongJump extends Behavior<Breeze> {

    private static final int REQUIRED_AIR_BLOCKS_ABOVE = 4;
    private static final int JUMP_COOLDOWN_TICKS = 10;
    private static final int JUMP_COOLDOWN_WHEN_HURT_TICKS = 2;
    private static final int INHALING_DURATION_TICKS = Math.round(10.0F);
    private static final float DEFAULT_FOLLOW_RANGE = 24.0F;
    private static final float DEFAULT_MAX_JUMP_VELOCITY = 1.4F;
    private static final float MAX_JUMP_VELOCITY_MULTIPLIER = 0.058333334F;
    private static final ObjectArrayList<Integer> ALLOWED_ANGLES = new ObjectArrayList(Lists.newArrayList(new Integer[]{40, 55, 60, 75, 80}));

    @VisibleForTesting
    public LongJump() {
        super(Map.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT, MemoryModuleType.BREEZE_JUMP_COOLDOWN, MemoryStatus.VALUE_ABSENT, MemoryModuleType.BREEZE_JUMP_INHALING, MemoryStatus.REGISTERED, MemoryModuleType.BREEZE_JUMP_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.BREEZE_SHOOT, MemoryStatus.VALUE_ABSENT, MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.BREEZE_LEAVING_WATER, MemoryStatus.REGISTERED), 200);
    }

    public static boolean canRun(ServerLevel level, Breeze breeze) {
        if (!breeze.onGround() && !breeze.isInWater()) {
            return false;
        } else if (Swim.shouldSwim(breeze)) {
            return false;
        } else if (breeze.getBrain().checkMemory(MemoryModuleType.BREEZE_JUMP_TARGET, MemoryStatus.VALUE_PRESENT)) {
            return true;
        } else {
            LivingEntity livingentity = (LivingEntity) breeze.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse((Object) null);

            if (livingentity == null) {
                return false;
            } else if (outOfAggroRange(breeze, livingentity)) {
                breeze.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                return false;
            } else if (tooCloseForJump(breeze, livingentity)) {
                return false;
            } else if (!canJumpFromCurrentPosition(level, breeze)) {
                return false;
            } else {
                BlockPos blockpos = snapToSurface(breeze, BreezeUtil.randomPointBehindTarget(livingentity, breeze.getRandom()));

                if (blockpos == null) {
                    return false;
                } else {
                    BlockState blockstate = level.getBlockState(blockpos.below());

                    if (breeze.getType().isBlockDangerous(blockstate)) {
                        return false;
                    } else if (!BreezeUtil.hasLineOfSight(breeze, blockpos.getCenter()) && !BreezeUtil.hasLineOfSight(breeze, blockpos.above(4).getCenter())) {
                        return false;
                    } else {
                        breeze.getBrain().setMemory(MemoryModuleType.BREEZE_JUMP_TARGET, blockpos);
                        return true;
                    }
                }
            }
        }
    }

    protected boolean checkExtraStartConditions(ServerLevel level, Breeze breeze) {
        return canRun(level, breeze);
    }

    protected boolean canStillUse(ServerLevel level, Breeze breeze, long timestamp) {
        return breeze.getPose() != Pose.STANDING && !breeze.getBrain().hasMemoryValue(MemoryModuleType.BREEZE_JUMP_COOLDOWN);
    }

    protected void start(ServerLevel level, Breeze breeze, long timestamp) {
        if (breeze.getBrain().checkMemory(MemoryModuleType.BREEZE_JUMP_INHALING, MemoryStatus.VALUE_ABSENT)) {
            breeze.getBrain().setMemoryWithExpiry(MemoryModuleType.BREEZE_JUMP_INHALING, Unit.INSTANCE, (long) LongJump.INHALING_DURATION_TICKS);
        }

        breeze.setPose(Pose.INHALING);
        level.playSound((Entity) null, (Entity) breeze, SoundEvents.BREEZE_CHARGE, SoundSource.HOSTILE, 1.0F, 1.0F);
        breeze.getBrain().getMemory(MemoryModuleType.BREEZE_JUMP_TARGET).ifPresent((blockpos) -> {
            breeze.lookAt(EntityAnchorArgument.Anchor.EYES, blockpos.getCenter());
        });
    }

    protected void tick(ServerLevel level, Breeze breeze, long timestamp) {
        boolean flag = breeze.isInWater();

        if (!flag && breeze.getBrain().checkMemory(MemoryModuleType.BREEZE_LEAVING_WATER, MemoryStatus.VALUE_PRESENT)) {
            breeze.getBrain().eraseMemory(MemoryModuleType.BREEZE_LEAVING_WATER);
        }

        if (isFinishedInhaling(breeze)) {
            Vec3 vec3 = (Vec3) breeze.getBrain().getMemory(MemoryModuleType.BREEZE_JUMP_TARGET).flatMap((blockpos) -> {
                return calculateOptimalJumpVector(breeze, breeze.getRandom(), Vec3.atBottomCenterOf(blockpos));
            }).orElse((Object) null);

            if (vec3 == null) {
                breeze.setPose(Pose.STANDING);
                return;
            }

            if (flag) {
                breeze.getBrain().setMemory(MemoryModuleType.BREEZE_LEAVING_WATER, Unit.INSTANCE);
            }

            breeze.playSound(SoundEvents.BREEZE_JUMP, 1.0F, 1.0F);
            breeze.setPose(Pose.LONG_JUMPING);
            breeze.setYRot(breeze.yBodyRot);
            breeze.setDiscardFriction(true);
            breeze.setDeltaMovement(vec3);
        } else if (isFinishedJumping(breeze)) {
            breeze.playSound(SoundEvents.BREEZE_LAND, 1.0F, 1.0F);
            breeze.setPose(Pose.STANDING);
            breeze.setDiscardFriction(false);
            boolean flag1 = breeze.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY);

            breeze.getBrain().setMemoryWithExpiry(MemoryModuleType.BREEZE_JUMP_COOLDOWN, Unit.INSTANCE, flag1 ? 2L : 10L);
            breeze.getBrain().setMemoryWithExpiry(MemoryModuleType.BREEZE_SHOOT, Unit.INSTANCE, 100L);
        }

    }

    protected void stop(ServerLevel level, Breeze breeze, long timestamp) {
        if (breeze.getPose() == Pose.LONG_JUMPING || breeze.getPose() == Pose.INHALING) {
            breeze.setPose(Pose.STANDING);
        }

        breeze.getBrain().eraseMemory(MemoryModuleType.BREEZE_JUMP_TARGET);
        breeze.getBrain().eraseMemory(MemoryModuleType.BREEZE_JUMP_INHALING);
        breeze.getBrain().eraseMemory(MemoryModuleType.BREEZE_LEAVING_WATER);
    }

    private static boolean isFinishedInhaling(Breeze breeze) {
        return breeze.getBrain().getMemory(MemoryModuleType.BREEZE_JUMP_INHALING).isEmpty() && breeze.getPose() == Pose.INHALING;
    }

    private static boolean isFinishedJumping(Breeze breeze) {
        boolean flag = breeze.getPose() == Pose.LONG_JUMPING;
        boolean flag1 = breeze.onGround();
        boolean flag2 = breeze.isInWater() && breeze.getBrain().checkMemory(MemoryModuleType.BREEZE_LEAVING_WATER, MemoryStatus.VALUE_ABSENT);

        return flag && (flag1 || flag2);
    }

    private static @Nullable BlockPos snapToSurface(LivingEntity entity, Vec3 target) {
        ClipContext clipcontext = new ClipContext(target, target.relative(Direction.DOWN, 10.0D), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity);
        HitResult hitresult = entity.level().clip(clipcontext);

        if (hitresult.getType() == HitResult.Type.BLOCK) {
            return BlockPos.containing(hitresult.getLocation()).above();
        } else {
            ClipContext clipcontext1 = new ClipContext(target, target.relative(Direction.UP, 10.0D), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity);
            HitResult hitresult1 = entity.level().clip(clipcontext1);

            return hitresult1.getType() == HitResult.Type.BLOCK ? BlockPos.containing(hitresult1.getLocation()).above() : null;
        }
    }

    private static boolean outOfAggroRange(Breeze breeze, LivingEntity attackTarget) {
        return !attackTarget.closerThan(breeze, breeze.getAttributeValue(Attributes.FOLLOW_RANGE));
    }

    private static boolean tooCloseForJump(Breeze breeze, LivingEntity attackTarget) {
        return attackTarget.distanceTo(breeze) - 4.0F <= 0.0F;
    }

    private static boolean canJumpFromCurrentPosition(ServerLevel level, Breeze breeze) {
        BlockPos blockpos = breeze.blockPosition();

        if (level.getBlockState(blockpos).is(Blocks.HONEY_BLOCK)) {
            return false;
        } else {
            for (int i = 1; i <= 4; ++i) {
                BlockPos blockpos1 = blockpos.relative(Direction.UP, i);

                if (!level.getBlockState(blockpos1).isAir() && !level.getFluidState(blockpos1).is(FluidTags.WATER)) {
                    return false;
                }
            }

            return true;
        }
    }

    private static Optional<Vec3> calculateOptimalJumpVector(Breeze body, RandomSource random, Vec3 targetPos) {
        for (int i : Util.shuffledCopy(LongJump.ALLOWED_ANGLES, random)) {
            float f = 0.058333334F * (float) body.getAttributeValue(Attributes.FOLLOW_RANGE);
            Optional<Vec3> optional = LongJumpUtil.calculateJumpVectorForAngle(body, targetPos, f, i, false);

            if (optional.isPresent()) {
                if (body.hasEffect(MobEffects.JUMP_BOOST)) {
                    double d0 = ((Vec3) optional.get()).normalize().y * (double) body.getJumpBoostPower();

                    return optional.map((vec31) -> {
                        return vec31.add(0.0D, d0, 0.0D);
                    });
                }

                return optional;
            }
        }

        return Optional.empty();
    }
}
