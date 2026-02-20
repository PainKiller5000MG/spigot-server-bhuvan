package net.minecraft.world.entity.ai.sensing;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

public abstract class Sensor<E extends LivingEntity> {

    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private static final int DEFAULT_SCAN_RATE = 20;
    private static final int DEFAULT_TARGETING_RANGE = 16;
    private static final TargetingConditions TARGET_CONDITIONS = TargetingConditions.forNonCombat().range(16.0D);
    private static final TargetingConditions TARGET_CONDITIONS_IGNORE_INVISIBILITY_TESTING = TargetingConditions.forNonCombat().range(16.0D).ignoreInvisibilityTesting();
    private static final TargetingConditions ATTACK_TARGET_CONDITIONS = TargetingConditions.forCombat().range(16.0D);
    private static final TargetingConditions ATTACK_TARGET_CONDITIONS_IGNORE_INVISIBILITY_TESTING = TargetingConditions.forCombat().range(16.0D).ignoreInvisibilityTesting();
    private static final TargetingConditions ATTACK_TARGET_CONDITIONS_IGNORE_LINE_OF_SIGHT = TargetingConditions.forCombat().range(16.0D).ignoreLineOfSight();
    private static final TargetingConditions ATTACK_TARGET_CONDITIONS_IGNORE_INVISIBILITY_AND_LINE_OF_SIGHT = TargetingConditions.forCombat().range(16.0D).ignoreLineOfSight().ignoreInvisibilityTesting();
    private final int scanRate;
    private long timeToTick;

    public Sensor(int scanRate) {
        this.scanRate = scanRate;
        this.timeToTick = (long) Sensor.RANDOM.nextInt(scanRate);
    }

    public Sensor() {
        this(20);
    }

    public final void tick(ServerLevel level, E body) {
        if (--this.timeToTick <= 0L) {
            this.timeToTick = (long) this.scanRate;
            this.updateTargetingConditionRanges(body);
            this.doTick(level, body);
        }

    }

    private void updateTargetingConditionRanges(E body) {
        double d0 = ((LivingEntity) body).getAttributeValue(Attributes.FOLLOW_RANGE);

        Sensor.TARGET_CONDITIONS.range(d0);
        Sensor.TARGET_CONDITIONS_IGNORE_INVISIBILITY_TESTING.range(d0);
        Sensor.ATTACK_TARGET_CONDITIONS.range(d0);
        Sensor.ATTACK_TARGET_CONDITIONS_IGNORE_INVISIBILITY_TESTING.range(d0);
        Sensor.ATTACK_TARGET_CONDITIONS_IGNORE_LINE_OF_SIGHT.range(d0);
        Sensor.ATTACK_TARGET_CONDITIONS_IGNORE_INVISIBILITY_AND_LINE_OF_SIGHT.range(d0);
    }

    protected abstract void doTick(ServerLevel level, E body);

    public abstract Set<MemoryModuleType<?>> requires();

    public static boolean isEntityTargetable(ServerLevel level, LivingEntity body, LivingEntity entity) {
        return body.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, entity) ? Sensor.TARGET_CONDITIONS_IGNORE_INVISIBILITY_TESTING.test(level, body, entity) : Sensor.TARGET_CONDITIONS.test(level, body, entity);
    }

    public static boolean isEntityAttackable(ServerLevel level, LivingEntity body, LivingEntity target) {
        return body.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, target) ? Sensor.ATTACK_TARGET_CONDITIONS_IGNORE_INVISIBILITY_TESTING.test(level, body, target) : Sensor.ATTACK_TARGET_CONDITIONS.test(level, body, target);
    }

    public static BiPredicate<ServerLevel, LivingEntity> wasEntityAttackableLastNTicks(LivingEntity body, int ticks) {
        return rememberPositives(ticks, (serverlevel, livingentity1) -> {
            return isEntityAttackable(serverlevel, body, livingentity1);
        });
    }

    public static boolean isEntityAttackableIgnoringLineOfSight(ServerLevel level, LivingEntity body, LivingEntity target) {
        return body.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, target) ? Sensor.ATTACK_TARGET_CONDITIONS_IGNORE_INVISIBILITY_AND_LINE_OF_SIGHT.test(level, body, target) : Sensor.ATTACK_TARGET_CONDITIONS_IGNORE_LINE_OF_SIGHT.test(level, body, target);
    }

    static <T, U> BiPredicate<T, U> rememberPositives(int invocations, BiPredicate<T, U> predicate) {
        AtomicInteger atomicinteger = new AtomicInteger(0);

        return (object, object1) -> {
            if (predicate.test(object, object1)) {
                atomicinteger.set(invocations);
                return true;
            } else {
                return atomicinteger.decrementAndGet() >= 0;
            }
        };
    }
}
