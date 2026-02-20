package net.minecraft.world.item.enchantment;

import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect;
import net.minecraft.world.item.enchantment.providers.EnchantmentProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jspecify.annotations.Nullable;

public class EnchantmentHelper {

    public EnchantmentHelper() {}

    public static int getItemEnchantmentLevel(Holder<Enchantment> enchantment, ItemStack piece) {
        ItemEnchantments itemenchantments = (ItemEnchantments) piece.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

        return itemenchantments.getLevel(enchantment);
    }

    public static ItemEnchantments updateEnchantments(ItemStack itemStack, Consumer<ItemEnchantments.Mutable> consumer) {
        DataComponentType<ItemEnchantments> datacomponenttype = getComponentType(itemStack);
        ItemEnchantments itemenchantments = (ItemEnchantments) itemStack.get(datacomponenttype);

        if (itemenchantments == null) {
            return ItemEnchantments.EMPTY;
        } else {
            ItemEnchantments.Mutable itemenchantments_mutable = new ItemEnchantments.Mutable(itemenchantments);

            consumer.accept(itemenchantments_mutable);
            ItemEnchantments itemenchantments1 = itemenchantments_mutable.toImmutable();

            itemStack.set(datacomponenttype, itemenchantments1);
            return itemenchantments1;
        }
    }

    public static boolean canStoreEnchantments(ItemStack itemStack) {
        return itemStack.has(getComponentType(itemStack));
    }

    public static void setEnchantments(ItemStack itemStack, ItemEnchantments enchantments) {
        itemStack.set(getComponentType(itemStack), enchantments);
    }

    public static ItemEnchantments getEnchantmentsForCrafting(ItemStack itemStack) {
        return (ItemEnchantments) itemStack.getOrDefault(getComponentType(itemStack), ItemEnchantments.EMPTY);
    }

    private static DataComponentType<ItemEnchantments> getComponentType(ItemStack itemStack) {
        return itemStack.is(Items.ENCHANTED_BOOK) ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS;
    }

    public static boolean hasAnyEnchantments(ItemStack itemStack) {
        return !((ItemEnchantments) itemStack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)).isEmpty() || !((ItemEnchantments) itemStack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)).isEmpty();
    }

    public static int processDurabilityChange(ServerLevel serverLevel, ItemStack itemStack, int amount) {
        MutableFloat mutablefloat = new MutableFloat((float) amount);

        runIterationOnItem(itemStack, (holder, j) -> {
            ((Enchantment) holder.value()).modifyDurabilityChange(serverLevel, j, itemStack, mutablefloat);
        });
        return mutablefloat.intValue();
    }

    public static int processAmmoUse(ServerLevel serverLevel, ItemStack weapon, ItemStack ammo, int amount) {
        MutableFloat mutablefloat = new MutableFloat((float) amount);

        runIterationOnItem(weapon, (holder, j) -> {
            ((Enchantment) holder.value()).modifyAmmoCount(serverLevel, j, ammo, mutablefloat);
        });
        return mutablefloat.intValue();
    }

    public static int processBlockExperience(ServerLevel serverLevel, ItemStack itemStack, int amount) {
        MutableFloat mutablefloat = new MutableFloat((float) amount);

        runIterationOnItem(itemStack, (holder, j) -> {
            ((Enchantment) holder.value()).modifyBlockExperience(serverLevel, j, itemStack, mutablefloat);
        });
        return mutablefloat.intValue();
    }

    public static int processMobExperience(ServerLevel serverLevel, @Nullable Entity killer, Entity killed, int amount) {
        if (killer instanceof LivingEntity livingentity) {
            MutableFloat mutablefloat = new MutableFloat((float) amount);

            runIterationOnEquipment(livingentity, (holder, j, enchantediteminuse) -> {
                ((Enchantment) holder.value()).modifyMobExperience(serverLevel, j, enchantediteminuse.itemStack(), killed, mutablefloat);
            });
            return mutablefloat.intValue();
        } else {
            return amount;
        }
    }

    public static ItemStack createBook(EnchantmentInstance enchant) {
        ItemStack itemstack = new ItemStack(Items.ENCHANTED_BOOK);

        itemstack.enchant(enchant.enchantment(), enchant.level());
        return itemstack;
    }

    private static void runIterationOnItem(ItemStack piece, EnchantmentHelper.EnchantmentVisitor method) {
        ItemEnchantments itemenchantments = (ItemEnchantments) piece.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

        for (Object2IntMap.Entry<Holder<Enchantment>> object2intmap_entry : itemenchantments.entrySet()) {
            method.accept((Holder) object2intmap_entry.getKey(), object2intmap_entry.getIntValue());
        }

    }

    private static void runIterationOnItem(ItemStack piece, EquipmentSlot slot, LivingEntity owner, EnchantmentHelper.EnchantmentInSlotVisitor method) {
        if (!piece.isEmpty()) {
            ItemEnchantments itemenchantments = (ItemEnchantments) piece.get(DataComponents.ENCHANTMENTS);

            if (itemenchantments != null && !itemenchantments.isEmpty()) {
                EnchantedItemInUse enchantediteminuse = new EnchantedItemInUse(piece, slot, owner);

                for (Object2IntMap.Entry<Holder<Enchantment>> object2intmap_entry : itemenchantments.entrySet()) {
                    Holder<Enchantment> holder = (Holder) object2intmap_entry.getKey();

                    if (((Enchantment) holder.value()).matchingSlot(slot)) {
                        method.accept(holder, object2intmap_entry.getIntValue(), enchantediteminuse);
                    }
                }

            }
        }
    }

    private static void runIterationOnEquipment(LivingEntity owner, EnchantmentHelper.EnchantmentInSlotVisitor method) {
        for (EquipmentSlot equipmentslot : EquipmentSlot.VALUES) {
            runIterationOnItem(owner.getItemBySlot(equipmentslot), equipmentslot, owner, method);
        }

    }

    public static boolean isImmuneToDamage(ServerLevel serverLevel, LivingEntity victim, DamageSource source) {
        MutableBoolean mutableboolean = new MutableBoolean();

        runIterationOnEquipment(victim, (holder, i, enchantediteminuse) -> {
            mutableboolean.setValue(mutableboolean.isTrue() || ((Enchantment) holder.value()).isImmuneToDamage(serverLevel, i, victim, source));
        });
        return mutableboolean.isTrue();
    }

    public static float getDamageProtection(ServerLevel serverLevel, LivingEntity victim, DamageSource source) {
        MutableFloat mutablefloat = new MutableFloat(0.0F);

        runIterationOnEquipment(victim, (holder, i, enchantediteminuse) -> {
            ((Enchantment) holder.value()).modifyDamageProtection(serverLevel, i, enchantediteminuse.itemStack(), victim, source, mutablefloat);
        });
        return mutablefloat.floatValue();
    }

    public static float modifyDamage(ServerLevel serverLevel, ItemStack itemStack, Entity victim, DamageSource damageSource, float damage) {
        MutableFloat mutablefloat = new MutableFloat(damage);

        runIterationOnItem(itemStack, (holder, i) -> {
            ((Enchantment) holder.value()).modifyDamage(serverLevel, i, itemStack, victim, damageSource, mutablefloat);
        });
        return mutablefloat.floatValue();
    }

    public static float modifyFallBasedDamage(ServerLevel serverLevel, ItemStack itemStack, Entity victim, DamageSource damageSource, float damage) {
        MutableFloat mutablefloat = new MutableFloat(damage);

        runIterationOnItem(itemStack, (holder, i) -> {
            ((Enchantment) holder.value()).modifyFallBasedDamage(serverLevel, i, itemStack, victim, damageSource, mutablefloat);
        });
        return mutablefloat.floatValue();
    }

    public static float modifyArmorEffectiveness(ServerLevel serverLevel, ItemStack itemStack, Entity victim, DamageSource damageSource, float armorFraction) {
        MutableFloat mutablefloat = new MutableFloat(armorFraction);

        runIterationOnItem(itemStack, (holder, i) -> {
            ((Enchantment) holder.value()).modifyArmorEffectivness(serverLevel, i, itemStack, victim, damageSource, mutablefloat);
        });
        return mutablefloat.floatValue();
    }

    public static float modifyKnockback(ServerLevel serverLevel, ItemStack itemStack, Entity victim, DamageSource damageSource, float knockback) {
        MutableFloat mutablefloat = new MutableFloat(knockback);

        runIterationOnItem(itemStack, (holder, i) -> {
            ((Enchantment) holder.value()).modifyKnockback(serverLevel, i, itemStack, victim, damageSource, mutablefloat);
        });
        return mutablefloat.floatValue();
    }

    public static void doPostAttackEffects(ServerLevel serverLevel, Entity victim, DamageSource damageSource) {
        Entity entity1 = damageSource.getEntity();

        if (entity1 instanceof LivingEntity livingentity) {
            doPostAttackEffectsWithItemSource(serverLevel, victim, damageSource, livingentity.getWeaponItem());
        } else {
            doPostAttackEffectsWithItemSource(serverLevel, victim, damageSource, (ItemStack) null);
        }

    }

    public static void doLungeEffects(ServerLevel serverLevel, Entity entity) {
        if (entity instanceof LivingEntity livingentity) {
            runIterationOnItem(entity.getWeaponItem(), EquipmentSlot.MAINHAND, livingentity, (holder, i, enchantediteminuse) -> {
                ((Enchantment) holder.value()).doLunge(serverLevel, i, enchantediteminuse, entity);
            });
        }

    }

    public static void doPostAttackEffectsWithItemSource(ServerLevel serverLevel, Entity victim, DamageSource damageSource, @Nullable ItemStack source) {
        doPostAttackEffectsWithItemSourceOnBreak(serverLevel, victim, damageSource, source, (Consumer) null);
    }

    public static void doPostAttackEffectsWithItemSourceOnBreak(ServerLevel serverLevel, Entity victim, DamageSource damageSource, @Nullable ItemStack source, @Nullable Consumer<Item> attackerlessOnBreak) {
        if (victim instanceof LivingEntity livingentity) {
            runIterationOnEquipment(livingentity, (holder, i, enchantediteminuse) -> {
                ((Enchantment) holder.value()).doPostAttack(serverLevel, i, enchantediteminuse, EnchantmentTarget.VICTIM, victim, damageSource);
            });
        }

        if (source != null) {
            Entity entity1 = damageSource.getEntity();

            if (entity1 instanceof LivingEntity) {
                LivingEntity livingentity1 = (LivingEntity) entity1;

                runIterationOnItem(source, EquipmentSlot.MAINHAND, livingentity1, (holder, i, enchantediteminuse) -> {
                    ((Enchantment) holder.value()).doPostAttack(serverLevel, i, enchantediteminuse, EnchantmentTarget.ATTACKER, victim, damageSource);
                });
            } else if (attackerlessOnBreak != null) {
                EnchantedItemInUse enchantediteminuse = new EnchantedItemInUse(source, (EquipmentSlot) null, (LivingEntity) null, attackerlessOnBreak);

                runIterationOnItem(source, (holder, i) -> {
                    ((Enchantment) holder.value()).doPostAttack(serverLevel, i, enchantediteminuse, EnchantmentTarget.ATTACKER, victim, damageSource);
                });
            }
        }

    }

    public static void runLocationChangedEffects(ServerLevel serverLevel, LivingEntity entity) {
        runIterationOnEquipment(entity, (holder, i, enchantediteminuse) -> {
            ((Enchantment) holder.value()).runLocationChangedEffects(serverLevel, i, enchantediteminuse, entity);
        });
    }

    public static void runLocationChangedEffects(ServerLevel serverLevel, ItemStack stack, LivingEntity entity, EquipmentSlot slot) {
        runIterationOnItem(stack, slot, entity, (holder, i, enchantediteminuse) -> {
            ((Enchantment) holder.value()).runLocationChangedEffects(serverLevel, i, enchantediteminuse, entity);
        });
    }

    public static void stopLocationBasedEffects(LivingEntity entity) {
        runIterationOnEquipment(entity, (holder, i, enchantediteminuse) -> {
            ((Enchantment) holder.value()).stopLocationBasedEffects(i, enchantediteminuse, entity);
        });
    }

    public static void stopLocationBasedEffects(ItemStack stack, LivingEntity entity, EquipmentSlot slot) {
        runIterationOnItem(stack, slot, entity, (holder, i, enchantediteminuse) -> {
            ((Enchantment) holder.value()).stopLocationBasedEffects(i, enchantediteminuse, entity);
        });
    }

    public static void tickEffects(ServerLevel serverLevel, LivingEntity entity) {
        runIterationOnEquipment(entity, (holder, i, enchantediteminuse) -> {
            ((Enchantment) holder.value()).tick(serverLevel, i, enchantediteminuse, entity);
        });
    }

    public static int getEnchantmentLevel(Holder<Enchantment> enchantment, LivingEntity entity) {
        Iterable<ItemStack> iterable = ((Enchantment) enchantment.value()).getSlotItems(entity).values();
        int i = 0;

        for (ItemStack itemstack : iterable) {
            int j = getItemEnchantmentLevel(enchantment, itemstack);

            if (j > i) {
                i = j;
            }
        }

        return i;
    }

    public static int processProjectileCount(ServerLevel serverLevel, ItemStack weapon, Entity shooter, int count) {
        MutableFloat mutablefloat = new MutableFloat((float) count);

        runIterationOnItem(weapon, (holder, j) -> {
            ((Enchantment) holder.value()).modifyProjectileCount(serverLevel, j, weapon, shooter, mutablefloat);
        });
        return Math.max(0, mutablefloat.intValue());
    }

    public static float processProjectileSpread(ServerLevel serverLevel, ItemStack weapon, Entity shooter, float angle) {
        MutableFloat mutablefloat = new MutableFloat(angle);

        runIterationOnItem(weapon, (holder, i) -> {
            ((Enchantment) holder.value()).modifyProjectileSpread(serverLevel, i, weapon, shooter, mutablefloat);
        });
        return Math.max(0.0F, mutablefloat.floatValue());
    }

    public static int getPiercingCount(ServerLevel serverLevel, ItemStack weapon, ItemStack ammo) {
        MutableFloat mutablefloat = new MutableFloat(0.0F);

        runIterationOnItem(weapon, (holder, i) -> {
            ((Enchantment) holder.value()).modifyPiercingCount(serverLevel, i, ammo, mutablefloat);
        });
        return Math.max(0, mutablefloat.intValue());
    }

    public static void onProjectileSpawned(ServerLevel serverLevel, ItemStack weapon, Projectile projectileEntity, Consumer<Item> onBreak) {
        Entity entity = projectileEntity.getOwner();
        LivingEntity livingentity;

        if (entity instanceof LivingEntity livingentity1) {
            livingentity = livingentity1;
        } else {
            livingentity = null;
        }

        LivingEntity livingentity2 = livingentity;
        EnchantedItemInUse enchantediteminuse = new EnchantedItemInUse(weapon, (EquipmentSlot) null, livingentity2, onBreak);

        runIterationOnItem(weapon, (holder, i) -> {
            ((Enchantment) holder.value()).onProjectileSpawned(serverLevel, i, enchantediteminuse, projectileEntity);
        });
    }

    public static void onHitBlock(ServerLevel serverLevel, ItemStack weapon, @Nullable LivingEntity owner, Entity entity, @Nullable EquipmentSlot slot, Vec3 hitLocation, BlockState hitBlock, Consumer<Item> onBreak) {
        EnchantedItemInUse enchantediteminuse = new EnchantedItemInUse(weapon, slot, owner, onBreak);

        runIterationOnItem(weapon, (holder, i) -> {
            ((Enchantment) holder.value()).onHitBlock(serverLevel, i, enchantediteminuse, entity, hitLocation, hitBlock);
        });
    }

    public static int modifyDurabilityToRepairFromXp(ServerLevel serverLevel, ItemStack item, int durability) {
        MutableFloat mutablefloat = new MutableFloat((float) durability);

        runIterationOnItem(item, (holder, j) -> {
            ((Enchantment) holder.value()).modifyDurabilityToRepairFromXp(serverLevel, j, item, mutablefloat);
        });
        return Math.max(0, mutablefloat.intValue());
    }

    public static float processEquipmentDropChance(ServerLevel serverLevel, LivingEntity entity, DamageSource killingBlow, float chance) {
        MutableFloat mutablefloat = new MutableFloat(chance);
        RandomSource randomsource = entity.getRandom();

        runIterationOnEquipment(entity, (holder, i, enchantediteminuse) -> {
            LootContext lootcontext = Enchantment.damageContext(serverLevel, i, entity, killingBlow);

            ((Enchantment) holder.value()).getEffects(EnchantmentEffectComponents.EQUIPMENT_DROPS).forEach((targetedconditionaleffect) -> {
                if (targetedconditionaleffect.enchanted() == EnchantmentTarget.VICTIM && targetedconditionaleffect.affected() == EnchantmentTarget.VICTIM && targetedconditionaleffect.matches(lootcontext)) {
                    mutablefloat.setValue(((EnchantmentValueEffect) targetedconditionaleffect.effect()).process(i, randomsource, mutablefloat.floatValue()));
                }

            });
        });
        Entity entity1 = killingBlow.getEntity();

        if (entity1 instanceof LivingEntity livingentity1) {
            runIterationOnEquipment(livingentity1, (holder, i, enchantediteminuse) -> {
                LootContext lootcontext = Enchantment.damageContext(serverLevel, i, entity, killingBlow);

                ((Enchantment) holder.value()).getEffects(EnchantmentEffectComponents.EQUIPMENT_DROPS).forEach((targetedconditionaleffect) -> {
                    if (targetedconditionaleffect.enchanted() == EnchantmentTarget.ATTACKER && targetedconditionaleffect.affected() == EnchantmentTarget.VICTIM && targetedconditionaleffect.matches(lootcontext)) {
                        mutablefloat.setValue(((EnchantmentValueEffect) targetedconditionaleffect.effect()).process(i, randomsource, mutablefloat.floatValue()));
                    }

                });
            });
        }

        return mutablefloat.floatValue();
    }

    public static void forEachModifier(ItemStack itemStack, EquipmentSlotGroup slot, BiConsumer<Holder<Attribute>, AttributeModifier> consumer) {
        runIterationOnItem(itemStack, (holder, i) -> {
            ((Enchantment) holder.value()).getEffects(EnchantmentEffectComponents.ATTRIBUTES).forEach((enchantmentattributeeffect) -> {
                if (((Enchantment) holder.value()).definition().slots().contains(slot)) {
                    consumer.accept(enchantmentattributeeffect.attribute(), enchantmentattributeeffect.getModifier(i, slot));
                }

            });
        });
    }

    public static void forEachModifier(ItemStack itemStack, EquipmentSlot slot, BiConsumer<Holder<Attribute>, AttributeModifier> consumer) {
        runIterationOnItem(itemStack, (holder, i) -> {
            ((Enchantment) holder.value()).getEffects(EnchantmentEffectComponents.ATTRIBUTES).forEach((enchantmentattributeeffect) -> {
                if (((Enchantment) holder.value()).matchingSlot(slot)) {
                    consumer.accept(enchantmentattributeeffect.attribute(), enchantmentattributeeffect.getModifier(i, slot));
                }

            });
        });
    }

    public static int getFishingLuckBonus(ServerLevel serverLevel, ItemStack rod, Entity fisher) {
        MutableFloat mutablefloat = new MutableFloat(0.0F);

        runIterationOnItem(rod, (holder, i) -> {
            ((Enchantment) holder.value()).modifyFishingLuckBonus(serverLevel, i, rod, fisher, mutablefloat);
        });
        return Math.max(0, mutablefloat.intValue());
    }

    public static float getFishingTimeReduction(ServerLevel serverLevel, ItemStack rod, Entity fisher) {
        MutableFloat mutablefloat = new MutableFloat(0.0F);

        runIterationOnItem(rod, (holder, i) -> {
            ((Enchantment) holder.value()).modifyFishingTimeReduction(serverLevel, i, rod, fisher, mutablefloat);
        });
        return Math.max(0.0F, mutablefloat.floatValue());
    }

    public static int getTridentReturnToOwnerAcceleration(ServerLevel serverLevel, ItemStack weapon, Entity trident) {
        MutableFloat mutablefloat = new MutableFloat(0.0F);

        runIterationOnItem(weapon, (holder, i) -> {
            ((Enchantment) holder.value()).modifyTridentReturnToOwnerAcceleration(serverLevel, i, weapon, trident, mutablefloat);
        });
        return Math.max(0, mutablefloat.intValue());
    }

    public static float modifyCrossbowChargingTime(ItemStack crossbow, LivingEntity holder, float time) {
        MutableFloat mutablefloat = new MutableFloat(time);

        runIterationOnItem(crossbow, (holder1, i) -> {
            ((Enchantment) holder1.value()).modifyCrossbowChargeTime(holder.getRandom(), i, mutablefloat);
        });
        return Math.max(0.0F, mutablefloat.floatValue());
    }

    public static float getTridentSpinAttackStrength(ItemStack trident, LivingEntity holder) {
        MutableFloat mutablefloat = new MutableFloat(0.0F);

        runIterationOnItem(trident, (holder1, i) -> {
            ((Enchantment) holder1.value()).modifyTridentSpinAttackStrength(holder.getRandom(), i, mutablefloat);
        });
        return mutablefloat.floatValue();
    }

    public static boolean hasTag(ItemStack item, TagKey<Enchantment> tag) {
        ItemEnchantments itemenchantments = (ItemEnchantments) item.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

        for (Object2IntMap.Entry<Holder<Enchantment>> object2intmap_entry : itemenchantments.entrySet()) {
            Holder<Enchantment> holder = (Holder) object2intmap_entry.getKey();

            if (holder.is(tag)) {
                return true;
            }
        }

        return false;
    }

    public static boolean has(ItemStack item, DataComponentType<?> effectType) {
        MutableBoolean mutableboolean = new MutableBoolean(false);

        runIterationOnItem(item, (holder, i) -> {
            if (((Enchantment) holder.value()).effects().has(effectType)) {
                mutableboolean.setTrue();
            }

        });
        return mutableboolean.booleanValue();
    }

    public static <T> Optional<T> pickHighestLevel(ItemStack itemStack, DataComponentType<List<T>> componentType) {
        Pair<List<T>, Integer> pair = getHighestLevel(itemStack, componentType);

        if (pair != null) {
            List<T> list = (List) pair.getFirst();
            int i = (Integer) pair.getSecond();

            return Optional.of(list.get(Math.min(i, list.size()) - 1));
        } else {
            return Optional.empty();
        }
    }

    public static <T> Pair<T, Integer> getHighestLevel(ItemStack item, DataComponentType<T> effectType) {
        MutableObject<Pair<T, Integer>> mutableobject = new MutableObject();

        runIterationOnItem(item, (holder, i) -> {
            if (mutableobject.get() == null || (Integer) ((Pair) mutableobject.get()).getSecond() < i) {
                T t0 = (T) ((Enchantment) holder.value()).effects().get(effectType);

                if (t0 != null) {
                    mutableobject.setValue(Pair.of(t0, i));
                }
            }

        });
        return (Pair) mutableobject.get();
    }

    public static Optional<EnchantedItemInUse> getRandomItemWith(DataComponentType<?> componentType, LivingEntity source, Predicate<ItemStack> predicate) {
        List<EnchantedItemInUse> list = new ArrayList();

        for (EquipmentSlot equipmentslot : EquipmentSlot.VALUES) {
            ItemStack itemstack = source.getItemBySlot(equipmentslot);

            if (predicate.test(itemstack)) {
                ItemEnchantments itemenchantments = (ItemEnchantments) itemstack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

                for (Object2IntMap.Entry<Holder<Enchantment>> object2intmap_entry : itemenchantments.entrySet()) {
                    Holder<Enchantment> holder = (Holder) object2intmap_entry.getKey();

                    if (((Enchantment) holder.value()).effects().has(componentType) && ((Enchantment) holder.value()).matchingSlot(equipmentslot)) {
                        list.add(new EnchantedItemInUse(itemstack, equipmentslot, source));
                    }
                }
            }
        }

        return Util.<EnchantedItemInUse>getRandomSafe(list, source.getRandom());
    }

    public static int getEnchantmentCost(RandomSource random, int slot, int bookcases, ItemStack itemStack) {
        Enchantable enchantable = (Enchantable) itemStack.get(DataComponents.ENCHANTABLE);

        if (enchantable == null) {
            return 0;
        } else {
            if (bookcases > 15) {
                bookcases = 15;
            }

            int k = random.nextInt(8) + 1 + (bookcases >> 1) + random.nextInt(bookcases + 1);

            return slot == 0 ? Math.max(k / 3, 1) : (slot == 1 ? k * 2 / 3 + 1 : Math.max(k, bookcases * 2));
        }
    }

    public static ItemStack enchantItem(RandomSource random, ItemStack itemStack, int enchantmentCost, RegistryAccess registryAccess, Optional<? extends HolderSet<Enchantment>> set) {
        return enchantItem(random, itemStack, enchantmentCost, (Stream) set.map(HolderSet::stream).orElseGet(() -> {
            return registryAccess.lookupOrThrow(Registries.ENCHANTMENT).listElements().map((holder_reference) -> {
                return holder_reference;
            });
        }));
    }

    public static ItemStack enchantItem(RandomSource random, ItemStack itemStack, int enchantmentCost, Stream<Holder<Enchantment>> source) {
        List<EnchantmentInstance> list = selectEnchantment(random, itemStack, enchantmentCost, source);

        if (itemStack.is(Items.BOOK)) {
            itemStack = new ItemStack(Items.ENCHANTED_BOOK);
        }

        for (EnchantmentInstance enchantmentinstance : list) {
            itemStack.enchant(enchantmentinstance.enchantment(), enchantmentinstance.level());
        }

        return itemStack;
    }

    public static List<EnchantmentInstance> selectEnchantment(RandomSource random, ItemStack itemStack, int enchantmentCost, Stream<Holder<Enchantment>> source) {
        List<EnchantmentInstance> list = Lists.newArrayList();
        Enchantable enchantable = (Enchantable) itemStack.get(DataComponents.ENCHANTABLE);

        if (enchantable == null) {
            return list;
        } else {
            enchantmentCost += 1 + random.nextInt(enchantable.value() / 4 + 1) + random.nextInt(enchantable.value() / 4 + 1);
            float f = (random.nextFloat() + random.nextFloat() - 1.0F) * 0.15F;

            enchantmentCost = Mth.clamp(Math.round((float) enchantmentCost + (float) enchantmentCost * f), 1, Integer.MAX_VALUE);
            List<EnchantmentInstance> list1 = getAvailableEnchantmentResults(enchantmentCost, itemStack, source);

            if (!list1.isEmpty()) {
                Optional optional = WeightedRandom.getRandomItem(random, list1, EnchantmentInstance::weight);

                Objects.requireNonNull(list);
                optional.ifPresent(list::add);

                while (random.nextInt(50) <= enchantmentCost) {
                    if (!list.isEmpty()) {
                        filterCompatibleEnchantments(list1, (EnchantmentInstance) list.getLast());
                    }

                    if (list1.isEmpty()) {
                        break;
                    }

                    optional = WeightedRandom.getRandomItem(random, list1, EnchantmentInstance::weight);
                    Objects.requireNonNull(list);
                    optional.ifPresent(list::add);
                    enchantmentCost /= 2;
                }
            }

            return list;
        }
    }

    public static void filterCompatibleEnchantments(List<EnchantmentInstance> enchants, EnchantmentInstance target) {
        enchants.removeIf((enchantmentinstance1) -> {
            return !Enchantment.areCompatible(target.enchantment(), enchantmentinstance1.enchantment());
        });
    }

    public static boolean isEnchantmentCompatible(Collection<Holder<Enchantment>> enchants, Holder<Enchantment> target) {
        for (Holder<Enchantment> holder1 : enchants) {
            if (!Enchantment.areCompatible(holder1, target)) {
                return false;
            }
        }

        return true;
    }

    public static List<EnchantmentInstance> getAvailableEnchantmentResults(int value, ItemStack itemStack, Stream<Holder<Enchantment>> source) {
        List<EnchantmentInstance> list = Lists.newArrayList();
        boolean flag = itemStack.is(Items.BOOK);

        source.filter((holder) -> {
            return ((Enchantment) holder.value()).isPrimaryItem(itemStack) || flag;
        }).forEach((holder) -> {
            Enchantment enchantment = (Enchantment) holder.value();

            for (int j = enchantment.getMaxLevel(); j >= enchantment.getMinLevel(); --j) {
                if (value >= enchantment.getMinCost(j) && value <= enchantment.getMaxCost(j)) {
                    list.add(new EnchantmentInstance(holder, j));
                    break;
                }
            }

        });
        return list;
    }

    public static void enchantItemFromProvider(ItemStack itemStack, RegistryAccess registryAccess, ResourceKey<EnchantmentProvider> providerKey, DifficultyInstance difficulty, RandomSource random) {
        EnchantmentProvider enchantmentprovider = (EnchantmentProvider) registryAccess.lookupOrThrow(Registries.ENCHANTMENT_PROVIDER).getValue(providerKey);

        if (enchantmentprovider != null) {
            updateEnchantments(itemStack, (itemenchantments_mutable) -> {
                enchantmentprovider.enchant(itemStack, itemenchantments_mutable, random, difficulty);
            });
        }

    }

    @FunctionalInterface
    private interface EnchantmentInSlotVisitor {

        void accept(Holder<Enchantment> enchantment, int level, EnchantedItemInUse item);
    }

    @FunctionalInterface
    private interface EnchantmentVisitor {

        void accept(Holder<Enchantment> enchantment, int level);
    }
}
