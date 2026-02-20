package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class LongJumpToRandomPos<E extends Mob> extends Behavior<E> {

    protected static final int FIND_JUMP_TRIES = 20;
    private static final int PREPARE_JUMP_DURATION = 40;
    protected static final int MIN_PATHFIND_DISTANCE_TO_VALID_JUMP = 8;
    private static final int TIME_OUT_DURATION = 200;
    private static final List<Integer> ALLOWED_ANGLES = Lists.newArrayList(new Integer[]{65, 70, 75, 80});
    private final UniformInt timeBetweenLongJumps;
    protected final int maxLongJumpHeight;
    protected final int maxLongJumpWidth;
    protected final float maxJumpVelocityMultiplier;
    protected List<LongJumpToRandomPos.PossibleJump> jumpCandidates;
    protected Optional<Vec3> initialPosition;
    protected @Nullable Vec3 chosenJump;
    protected int findJumpTries;
    protected long prepareJumpStart;
    private final Function<E, SoundEvent> getJumpSound;
    private final BiPredicate<E, BlockPos> acceptableLandingSpot;

    public LongJumpToRandomPos(UniformInt timeBetweenLongJumps, int maxLongJumpHeight, int maxLongJumpWidth, float maxJumpVelocityMultiplier, Function<E, SoundEvent> getJumpSound) {
        this(timeBetweenLongJumps, maxLongJumpHeight, maxLongJumpWidth, maxJumpVelocityMultiplier, getJumpSound, LongJumpToRandomPos::defaultAcceptableLandingSpot);
    }

    public static <E extends Mob> boolean defaultAcceptableLandingSpot(E body, BlockPos targetPos) {
        Level level = body.level();
        BlockPos blockpos1 = targetPos.below();

        return level.getBlockState(blockpos1).isSolidRender() && body.getPathfindingMalus(WalkNodeEvaluator.getPathTypeStatic(body, targetPos)) == 0.0F;
    }

    public LongJumpToRandomPos(UniformInt timeBetweenLongJumps, int maxLongJumpHeight, int maxLongJumpWidth, float maxJumpVelocityMultiplier, Function<E, SoundEvent> getJumpSound, BiPredicate<E, BlockPos> acceptableLandingSpot) {
        super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT, MemoryModuleType.LONG_JUMP_MID_JUMP, MemoryStatus.VALUE_ABSENT), 200);
        this.jumpCandidates = Lists.newArrayList();
        this.initialPosition = Optional.empty();
        this.timeBetweenLongJumps = timeBetweenLongJumps;
        this.maxLongJumpHeight = maxLongJumpHeight;
        this.maxLongJumpWidth = maxLongJumpWidth;
        this.maxJumpVelocityMultiplier = maxJumpVelocityMultiplier;
        this.getJumpSound = getJumpSound;
        this.acceptableLandingSpot = acceptableLandingSpot;
    }

    protected boolean checkExtraStartConditions(ServerLevel level, Mob body) {
        boolean flag = body.onGround() && !body.isInWater() && !body.isInLava() && !level.getBlockState(body.blockPosition()).is(Blocks.HONEY_BLOCK);

        if (!flag) {
            body.getBrain().setMemory(MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS, this.timeBetweenLongJumps.sample(level.random) / 2);
        }

        return flag;
    }

    protected boolean canStillUse(ServerLevel level, Mob body, long timestamp) {
        boolean flag = this.initialPosition.isPresent() && ((Vec3) this.initialPosition.get()).equals(body.position()) && this.findJumpTries > 0 && !body.isInWater() && (this.chosenJump != null || !this.jumpCandidates.isEmpty());

        if (!flag && body.getBrain().getMemory(MemoryModuleType.LONG_JUMP_MID_JUMP).isEmpty()) {
            body.getBrain().setMemory(MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS, this.timeBetweenLongJumps.sample(level.random) / 2);
            body.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        }

        return flag;
    }

    protected void start(ServerLevel level, E body, long timestamp) {
        this.chosenJump = null;
        this.findJumpTries = 20;
        this.initialPosition = Optional.of(body.position());
        BlockPos blockpos = body.blockPosition();
        int j = blockpos.getX();
        int k = blockpos.getY();
        int l = blockpos.getZ();

        this.jumpCandidates = (List) BlockPos.betweenClosedStream(j - this.maxLongJumpWidth, k - this.maxLongJumpHeight, l - this.maxLongJumpWidth, j + this.maxLongJumpWidth, k + this.maxLongJumpHeight, l + this.maxLongJumpWidth).filter((blockpos1) -> {
            return !blockpos1.equals(blockpos);
        }).map((blockpos1) -> {
            return new LongJumpToRandomPos.PossibleJump(blockpos1.immutable(), Mth.ceil(blockpos.distSqr(blockpos1)));
        }).collect(Collectors.toCollection(Lists::newArrayList));
    }

    protected void tick(ServerLevel level, E body, long timestamp) {
        if (this.chosenJump != null) {
            if (timestamp - this.prepareJumpStart >= 40L) {
                body.setYRot(body.yBodyRot);
                body.setDiscardFriction(true);
                double d0 = this.chosenJump.length();
                double d1 = d0 + (double) body.getJumpBoostPower();

                body.setDeltaMovement(this.chosenJump.scale(d1 / d0));
                body.getBrain().setMemory(MemoryModuleType.LONG_JUMP_MID_JUMP, true);
                level.playSound((Entity) null, (Entity) body, (SoundEvent) this.getJumpSound.apply(body), SoundSource.NEUTRAL, 1.0F, 1.0F);
            }
        } else {
            --this.findJumpTries;
            this.pickCandidate(level, body, timestamp);
        }

    }

    protected void pickCandidate(ServerLevel level, E body, long timestamp) {
        while (true) {
            if (!this.jumpCandidates.isEmpty()) {
                Optional<LongJumpToRandomPos.PossibleJump> optional = this.getJumpCandidate(level);

                if (optional.isEmpty()) {
                    continue;
                }

                LongJumpToRandomPos.PossibleJump longjumptorandompos_possiblejump = (LongJumpToRandomPos.PossibleJump) optional.get();
                BlockPos blockpos = longjumptorandompos_possiblejump.targetPos();

                if (!this.isAcceptableLandingPosition(level, body, blockpos)) {
                    continue;
                }

                Vec3 vec3 = Vec3.atCenterOf(blockpos);
                Vec3 vec31 = this.calculateOptimalJumpVector(body, vec3);

                if (vec31 == null) {
                    continue;
                }

                body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(blockpos));
                PathNavigation pathnavigation = body.getNavigation();
                Path path = pathnavigation.createPath(blockpos, 0, 8);

                if (path != null && path.canReach()) {
                    continue;
                }

                this.chosenJump = vec31;
                this.prepareJumpStart = timestamp;
                return;
            }

            return;
        }
    }

    protected Optional<LongJumpToRandomPos.PossibleJump> getJumpCandidate(ServerLevel level) {
        Optional<LongJumpToRandomPos.PossibleJump> optional = WeightedRandom.<LongJumpToRandomPos.PossibleJump>getRandomItem(level.random, this.jumpCandidates, LongJumpToRandomPos.PossibleJump::weight);
        List list = this.jumpCandidates;

        Objects.requireNonNull(this.jumpCandidates);
        optional.ifPresent(list::remove);
        return optional;
    }

    private boolean isAcceptableLandingPosition(ServerLevel level, E body, BlockPos targetPos) {
        BlockPos blockpos1 = body.blockPosition();
        int i = blockpos1.getX();
        int j = blockpos1.getZ();

        return i == targetPos.getX() && j == targetPos.getZ() ? false : this.acceptableLandingSpot.test(body, targetPos);
    }

    protected @Nullable Vec3 calculateOptimalJumpVector(Mob body, Vec3 targetPos) {
        List<Integer> list = Lists.newArrayList(LongJumpToRandomPos.ALLOWED_ANGLES);

        Collections.shuffle(list);
        float f = (float) (body.getAttributeValue(Attributes.JUMP_STRENGTH) * (double) this.maxJumpVelocityMultiplier);

        for (int i : list) {
            Optional<Vec3> optional = LongJumpUtil.calculateJumpVectorForAngle(body, targetPos, f, i, true);

            if (optional.isPresent()) {
                return (Vec3) optional.get();
            }
        }

        return null;
    }

    public static record PossibleJump(BlockPos targetPos, int weight) {

    }
}
