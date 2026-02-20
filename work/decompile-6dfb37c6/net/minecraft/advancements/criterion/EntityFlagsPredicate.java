package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public record EntityFlagsPredicate(Optional<Boolean> isOnGround, Optional<Boolean> isOnFire, Optional<Boolean> isCrouching, Optional<Boolean> isSprinting, Optional<Boolean> isSwimming, Optional<Boolean> isFlying, Optional<Boolean> isBaby, Optional<Boolean> isInWater, Optional<Boolean> isFallFlying) {

    public static final Codec<EntityFlagsPredicate> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(Codec.BOOL.optionalFieldOf("is_on_ground").forGetter(EntityFlagsPredicate::isOnGround), Codec.BOOL.optionalFieldOf("is_on_fire").forGetter(EntityFlagsPredicate::isOnFire), Codec.BOOL.optionalFieldOf("is_sneaking").forGetter(EntityFlagsPredicate::isCrouching), Codec.BOOL.optionalFieldOf("is_sprinting").forGetter(EntityFlagsPredicate::isSprinting), Codec.BOOL.optionalFieldOf("is_swimming").forGetter(EntityFlagsPredicate::isSwimming), Codec.BOOL.optionalFieldOf("is_flying").forGetter(EntityFlagsPredicate::isFlying), Codec.BOOL.optionalFieldOf("is_baby").forGetter(EntityFlagsPredicate::isBaby), Codec.BOOL.optionalFieldOf("is_in_water").forGetter(EntityFlagsPredicate::isInWater), Codec.BOOL.optionalFieldOf("is_fall_flying").forGetter(EntityFlagsPredicate::isFallFlying)).apply(instance, EntityFlagsPredicate::new);
    });

    public boolean matches(Entity entity) {
        if (this.isOnGround.isPresent() && entity.onGround() != (Boolean) this.isOnGround.get()) {
            return false;
        } else if (this.isOnFire.isPresent() && entity.isOnFire() != (Boolean) this.isOnFire.get()) {
            return false;
        } else if (this.isCrouching.isPresent() && entity.isCrouching() != (Boolean) this.isCrouching.get()) {
            return false;
        } else if (this.isSprinting.isPresent() && entity.isSprinting() != (Boolean) this.isSprinting.get()) {
            return false;
        } else if (this.isSwimming.isPresent() && entity.isSwimming() != (Boolean) this.isSwimming.get()) {
            return false;
        } else {
            if (this.isFlying.isPresent()) {
                boolean flag;
                label68:
                {
                    label67:
                    {
                        if (entity instanceof LivingEntity) {
                            LivingEntity livingentity = (LivingEntity) entity;

                            if (livingentity.isFallFlying()) {
                                break label67;
                            }

                            if (livingentity instanceof Player) {
                                Player player = (Player) livingentity;

                                if (player.getAbilities().flying) {
                                    break label67;
                                }
                            }
                        }

                        flag = false;
                        break label68;
                    }

                    flag = true;
                }

                boolean flag1 = flag;

                if (flag1 != (Boolean) this.isFlying.get()) {
                    return false;
                }
            }

            if (this.isInWater.isPresent() && entity.isInWater() != (Boolean) this.isInWater.get()) {
                return false;
            } else {
                if (this.isFallFlying.isPresent() && entity instanceof LivingEntity) {
                    LivingEntity livingentity1 = (LivingEntity) entity;

                    if (livingentity1.isFallFlying() != (Boolean) this.isFallFlying.get()) {
                        return false;
                    }
                }

                if (this.isBaby.isPresent() && entity instanceof LivingEntity) {
                    LivingEntity livingentity2 = (LivingEntity) entity;

                    if (livingentity2.isBaby() != (Boolean) this.isBaby.get()) {
                        return false;
                    }
                }

                return true;
            }
        }
    }

    public static class Builder {

        private Optional<Boolean> isOnGround = Optional.empty();
        private Optional<Boolean> isOnFire = Optional.empty();
        private Optional<Boolean> isCrouching = Optional.empty();
        private Optional<Boolean> isSprinting = Optional.empty();
        private Optional<Boolean> isSwimming = Optional.empty();
        private Optional<Boolean> isFlying = Optional.empty();
        private Optional<Boolean> isBaby = Optional.empty();
        private Optional<Boolean> isInWater = Optional.empty();
        private Optional<Boolean> isFallFlying = Optional.empty();

        public Builder() {}

        public static EntityFlagsPredicate.Builder flags() {
            return new EntityFlagsPredicate.Builder();
        }

        public EntityFlagsPredicate.Builder setOnGround(Boolean onGround) {
            this.isOnGround = Optional.of(onGround);
            return this;
        }

        public EntityFlagsPredicate.Builder setOnFire(Boolean onFire) {
            this.isOnFire = Optional.of(onFire);
            return this;
        }

        public EntityFlagsPredicate.Builder setCrouching(Boolean crouching) {
            this.isCrouching = Optional.of(crouching);
            return this;
        }

        public EntityFlagsPredicate.Builder setSprinting(Boolean sprinting) {
            this.isSprinting = Optional.of(sprinting);
            return this;
        }

        public EntityFlagsPredicate.Builder setSwimming(Boolean swimming) {
            this.isSwimming = Optional.of(swimming);
            return this;
        }

        public EntityFlagsPredicate.Builder setIsFlying(Boolean flying) {
            this.isFlying = Optional.of(flying);
            return this;
        }

        public EntityFlagsPredicate.Builder setIsBaby(Boolean baby) {
            this.isBaby = Optional.of(baby);
            return this;
        }

        public EntityFlagsPredicate.Builder setIsInWater(Boolean inWater) {
            this.isInWater = Optional.of(inWater);
            return this;
        }

        public EntityFlagsPredicate.Builder setIsFallFlying(Boolean fallFlying) {
            this.isFallFlying = Optional.of(fallFlying);
            return this;
        }

        public EntityFlagsPredicate build() {
            return new EntityFlagsPredicate(this.isOnGround, this.isOnFire, this.isCrouching, this.isSprinting, this.isSwimming, this.isFlying, this.isBaby, this.isInWater, this.isFallFlying);
        }
    }
}
