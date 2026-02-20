package net.minecraft.world.entity;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.Scoreboard;

public enum ConversionType {

    SINGLE(true) {
        @Override
        void convert(Mob from, Mob to, ConversionParams params) {
            Entity entity = from.getFirstPassenger();

            to.copyPosition(from);
            to.setDeltaMovement(from.getDeltaMovement());
            if (entity != null) {
                entity.stopRiding();
                entity.boardingCooldown = 0;

                for (Entity entity1 : to.getPassengers()) {
                    entity1.stopRiding();
                    entity1.remove(Entity.RemovalReason.DISCARDED);
                }

                entity.startRiding(to);
            }

            Entity entity2 = from.getVehicle();

            if (entity2 != null) {
                from.stopRiding();
                to.startRiding(entity2, false, false);
            }

            if (params.keepEquipment()) {
                for (EquipmentSlot equipmentslot : EquipmentSlot.VALUES) {
                    ItemStack itemstack = from.getItemBySlot(equipmentslot);

                    if (!itemstack.isEmpty()) {
                        to.setItemSlot(equipmentslot, itemstack.copyAndClear());
                        to.setDropChance(equipmentslot, from.getDropChances().byEquipment(equipmentslot));
                    }
                }
            }

            to.fallDistance = from.fallDistance;
            to.setSharedFlag(7, from.isFallFlying());
            to.lastHurtByPlayerMemoryTime = from.lastHurtByPlayerMemoryTime;
            to.hurtTime = from.hurtTime;
            to.yBodyRot = from.yBodyRot;
            to.setOnGround(from.onGround());
            Optional optional = from.getSleepingPos();

            Objects.requireNonNull(to);
            optional.ifPresent(to::setSleepingPos);
            Entity entity3 = from.getLeashHolder();

            if (entity3 != null) {
                to.setLeashedTo(entity3, true);
            }

            this.convertCommon(from, to, params);
        }
    },
    SPLIT_ON_DEATH(false) {
        @Override
        void convert(Mob from, Mob to, ConversionParams params) {
            Entity entity = from.getFirstPassenger();

            if (entity != null) {
                entity.stopRiding();
            }

            Entity entity1 = from.getLeashHolder();

            if (entity1 != null) {
                from.dropLeash();
            }

            this.convertCommon(from, to, params);
        }
    };

    private static final Set<DataComponentType<?>> COMPONENTS_TO_COPY = Set.of(DataComponents.CUSTOM_NAME, DataComponents.CUSTOM_DATA);
    private final boolean discardAfterConversion;

    private ConversionType(boolean discardAfterConversion) {
        this.discardAfterConversion = discardAfterConversion;
    }

    public boolean shouldDiscardAfterConversion() {
        return this.discardAfterConversion;
    }

    abstract void convert(Mob from, Mob to, ConversionParams params);

    void convertCommon(Mob from, Mob to, ConversionParams params) {
        to.setAbsorptionAmount(from.getAbsorptionAmount());

        for (MobEffectInstance mobeffectinstance : from.getActiveEffects()) {
            to.addEffect(new MobEffectInstance(mobeffectinstance));
        }

        if (from.isBaby()) {
            to.setBaby(true);
        }

        if (from instanceof AgeableMob ageablemob) {
            if (to instanceof AgeableMob ageablemob1) {
                ageablemob1.setAge(ageablemob.getAge());
                ageablemob1.forcedAge = ageablemob.forcedAge;
                ageablemob1.forcedAgeTimer = ageablemob.forcedAgeTimer;
            }
        }

        Brain<?> brain = from.getBrain();
        Brain<?> brain1 = to.getBrain();

        if (brain.checkMemory(MemoryModuleType.ANGRY_AT, MemoryStatus.REGISTERED) && brain.hasMemoryValue(MemoryModuleType.ANGRY_AT)) {
            brain1.setMemory(MemoryModuleType.ANGRY_AT, brain.getMemory(MemoryModuleType.ANGRY_AT));
        }

        if (params.preserveCanPickUpLoot()) {
            to.setCanPickUpLoot(from.canPickUpLoot());
        }

        to.setLeftHanded(from.isLeftHanded());
        to.setNoAi(from.isNoAi());
        if (from.isPersistenceRequired()) {
            to.setPersistenceRequired();
        }

        to.setCustomNameVisible(from.isCustomNameVisible());
        to.setSharedFlagOnFire(from.isOnFire());
        to.setInvulnerable(from.isInvulnerable());
        to.setNoGravity(from.isNoGravity());
        to.setPortalCooldown(from.getPortalCooldown());
        to.setSilent(from.isSilent());
        Set set = from.getTags();

        Objects.requireNonNull(to);
        set.forEach(to::addTag);

        for (DataComponentType<?> datacomponenttype : ConversionType.COMPONENTS_TO_COPY) {
            copyComponent(from, to, datacomponenttype);
        }

        if (params.team() != null) {
            Scoreboard scoreboard = to.level().getScoreboard();

            scoreboard.addPlayerToTeam(to.getStringUUID(), params.team());
            if (from.getTeam() != null && from.getTeam() == params.team()) {
                scoreboard.removePlayerFromTeam(from.getStringUUID(), from.getTeam());
            }
        }

        if (from instanceof Zombie zombie) {
            if (zombie.canBreakDoors() && to instanceof Zombie zombie1) {
                zombie1.setCanBreakDoors(true);
            }
        }

    }

    private static <T> void copyComponent(Mob from, Mob to, DataComponentType<T> componentType) {
        T t0 = (T) from.get(componentType);

        if (t0 != null) {
            to.setComponent(componentType, t0);
        }

    }
}
