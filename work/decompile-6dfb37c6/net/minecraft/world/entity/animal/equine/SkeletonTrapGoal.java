package net.minecraft.world.entity.animal.equine;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.providers.VanillaEnchantmentProviders;
import org.jspecify.annotations.Nullable;

public class SkeletonTrapGoal extends Goal {

    private final SkeletonHorse horse;

    public SkeletonTrapGoal(SkeletonHorse horse) {
        this.horse = horse;
    }

    @Override
    public boolean canUse() {
        return this.horse.level().hasNearbyAlivePlayer(this.horse.getX(), this.horse.getY(), this.horse.getZ(), 10.0D);
    }

    @Override
    public void tick() {
        ServerLevel serverlevel = (ServerLevel) this.horse.level();
        DifficultyInstance difficultyinstance = serverlevel.getCurrentDifficultyAt(this.horse.blockPosition());

        this.horse.setTrap(false);
        this.horse.setTamed(true);
        this.horse.setAge(0);
        LightningBolt lightningbolt = EntityType.LIGHTNING_BOLT.create(serverlevel, EntitySpawnReason.TRIGGERED);

        if (lightningbolt != null) {
            lightningbolt.snapTo(this.horse.getX(), this.horse.getY(), this.horse.getZ());
            lightningbolt.setVisualOnly(true);
            serverlevel.addFreshEntity(lightningbolt);
            Skeleton skeleton = this.createSkeleton(difficultyinstance, this.horse);

            if (skeleton != null) {
                skeleton.startRiding(this.horse);
                serverlevel.addFreshEntityWithPassengers(skeleton);

                for (int i = 0; i < 3; ++i) {
                    AbstractHorse abstracthorse = this.createHorse(difficultyinstance);

                    if (abstracthorse != null) {
                        Skeleton skeleton1 = this.createSkeleton(difficultyinstance, abstracthorse);

                        if (skeleton1 != null) {
                            skeleton1.startRiding(abstracthorse);
                            abstracthorse.push(this.horse.getRandom().triangle(0.0D, 1.1485D), 0.0D, this.horse.getRandom().triangle(0.0D, 1.1485D));
                            serverlevel.addFreshEntityWithPassengers(abstracthorse);
                        }
                    }
                }

            }
        }
    }

    private @Nullable AbstractHorse createHorse(DifficultyInstance difficulty) {
        SkeletonHorse skeletonhorse = EntityType.SKELETON_HORSE.create(this.horse.level(), EntitySpawnReason.TRIGGERED);

        if (skeletonhorse != null) {
            skeletonhorse.finalizeSpawn((ServerLevel) this.horse.level(), difficulty, EntitySpawnReason.TRIGGERED, (SpawnGroupData) null);
            skeletonhorse.setPos(this.horse.getX(), this.horse.getY(), this.horse.getZ());
            skeletonhorse.invulnerableTime = 60;
            skeletonhorse.setPersistenceRequired();
            skeletonhorse.setTamed(true);
            skeletonhorse.setAge(0);
        }

        return skeletonhorse;
    }

    private @Nullable Skeleton createSkeleton(DifficultyInstance difficulty, AbstractHorse horse) {
        Skeleton skeleton = EntityType.SKELETON.create(horse.level(), EntitySpawnReason.TRIGGERED);

        if (skeleton != null) {
            skeleton.finalizeSpawn((ServerLevel) horse.level(), difficulty, EntitySpawnReason.TRIGGERED, (SpawnGroupData) null);
            skeleton.setPos(horse.getX(), horse.getY(), horse.getZ());
            skeleton.invulnerableTime = 60;
            skeleton.setPersistenceRequired();
            if (skeleton.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            }

            this.enchant(skeleton, EquipmentSlot.MAINHAND, difficulty);
            this.enchant(skeleton, EquipmentSlot.HEAD, difficulty);
        }

        return skeleton;
    }

    private void enchant(Skeleton skeleton, EquipmentSlot slot, DifficultyInstance difficulty) {
        ItemStack itemstack = skeleton.getItemBySlot(slot);

        itemstack.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        EnchantmentHelper.enchantItemFromProvider(itemstack, skeleton.level().registryAccess(), VanillaEnchantmentProviders.MOB_SPAWN_EQUIPMENT, difficulty, skeleton.getRandom());
        skeleton.setItemSlot(slot, itemstack);
    }
}
