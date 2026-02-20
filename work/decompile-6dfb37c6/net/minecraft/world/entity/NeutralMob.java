package net.minecraft.world.entity;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public interface NeutralMob {

    String TAG_ANGER_END_TIME = "anger_end_time";
    String TAG_ANGRY_AT = "angry_at";
    long NO_ANGER_END_TIME = -1L;

    long getPersistentAngerEndTime();

    default void setTimeToRemainAngry(long remainingTime) {
        this.setPersistentAngerEndTime(this.level().getGameTime() + remainingTime);
    }

    void setPersistentAngerEndTime(long endTime);

    @Nullable
    EntityReference<LivingEntity> getPersistentAngerTarget();

    void setPersistentAngerTarget(@Nullable EntityReference<LivingEntity> persistentAngerTarget);

    void startPersistentAngerTimer();

    Level level();

    default void addPersistentAngerSaveData(ValueOutput output) {
        output.putLong("anger_end_time", this.getPersistentAngerEndTime());
        output.storeNullable("angry_at", EntityReference.codec(), this.getPersistentAngerTarget());
    }

    default void readPersistentAngerSaveData(Level level, ValueInput input) {
        Optional<Long> optional = input.getLong("anger_end_time");

        if (optional.isPresent()) {
            this.setPersistentAngerEndTime((Long) optional.get());
        } else {
            Optional<Integer> optional1 = input.getInt("AngerTime");

            if (optional1.isPresent()) {
                this.setTimeToRemainAngry((long) (Integer) optional1.get());
            } else {
                this.setPersistentAngerEndTime(-1L);
            }
        }

        if (level instanceof ServerLevel) {
            this.setPersistentAngerTarget(EntityReference.read(input, "angry_at"));
            this.setTarget(EntityReference.getLivingEntity(this.getPersistentAngerTarget(), level));
        }
    }

    default void updatePersistentAnger(ServerLevel level, boolean stayAngryIfTargetPresent) {
        LivingEntity livingentity = this.getTarget();
        EntityReference<LivingEntity> entityreference = this.getPersistentAngerTarget();

        if (livingentity != null && livingentity.isDeadOrDying() && entityreference != null && entityreference.matches(livingentity) && livingentity instanceof Mob) {
            this.stopBeingAngry();
        } else {
            if (livingentity != null) {
                if (entityreference == null || !entityreference.matches(livingentity)) {
                    this.setPersistentAngerTarget(EntityReference.of(livingentity));
                }

                this.startPersistentAngerTimer();
            }

            if (entityreference != null && !this.isAngry() && (livingentity == null || !isValidPlayerTarget(livingentity) || !stayAngryIfTargetPresent)) {
                this.stopBeingAngry();
            }

        }
    }

    private static boolean isValidPlayerTarget(LivingEntity target) {
        boolean flag;

        if (target instanceof Player player) {
            if (!player.isCreative() && !player.isSpectator()) {
                flag = true;
                return flag;
            }
        }

        flag = false;
        return flag;
    }

    default boolean isAngryAt(LivingEntity entity, ServerLevel level) {
        if (!this.canAttack(entity)) {
            return false;
        } else if (isValidPlayerTarget(entity) && this.isAngryAtAllPlayers(level)) {
            return true;
        } else {
            EntityReference<LivingEntity> entityreference = this.getPersistentAngerTarget();

            return entityreference != null && entityreference.matches(entity);
        }
    }

    default boolean isAngryAtAllPlayers(ServerLevel level) {
        return (Boolean) level.getGameRules().get(GameRules.UNIVERSAL_ANGER) && this.isAngry() && this.getPersistentAngerTarget() == null;
    }

    default boolean isAngry() {
        long i = this.getPersistentAngerEndTime();

        if (i > 0L) {
            long j = i - this.level().getGameTime();

            return j > 0L;
        } else {
            return false;
        }
    }

    default void playerDied(ServerLevel level, Player player) {
        if ((Boolean) level.getGameRules().get(GameRules.FORGIVE_DEAD_PLAYERS)) {
            EntityReference<LivingEntity> entityreference = this.getPersistentAngerTarget();

            if (entityreference != null && entityreference.matches(player)) {
                this.stopBeingAngry();
            }
        }
    }

    default void forgetCurrentTargetAndRefreshUniversalAnger() {
        this.stopBeingAngry();
        this.startPersistentAngerTimer();
    }

    default void stopBeingAngry() {
        this.setLastHurtByMob((LivingEntity) null);
        this.setPersistentAngerTarget((EntityReference) null);
        this.setTarget((LivingEntity) null);
        this.setPersistentAngerEndTime(-1L);
    }

    @Nullable
    LivingEntity getLastHurtByMob();

    void setLastHurtByMob(@Nullable LivingEntity hurtBy);

    void setTarget(@Nullable LivingEntity target);

    boolean canAttack(LivingEntity target);

    @Nullable
    LivingEntity getTarget();
}
