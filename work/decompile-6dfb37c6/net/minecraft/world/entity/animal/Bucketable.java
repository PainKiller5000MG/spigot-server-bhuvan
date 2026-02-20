package net.minecraft.world.entity.animal;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public interface Bucketable {

    boolean fromBucket();

    void setFromBucket(boolean fromBucket);

    void saveToBucketTag(ItemStack bucket);

    void loadFromBucketTag(CompoundTag tag);

    ItemStack getBucketItemStack();

    SoundEvent getPickupSound();

    /** @deprecated */
    @Deprecated
    static void saveDefaultDataToBucketTag(Mob entity, ItemStack bucket) {
        bucket.copyFrom(DataComponents.CUSTOM_NAME, entity);
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, bucket, (compoundtag) -> {
            if (entity.isNoAi()) {
                compoundtag.putBoolean("NoAI", entity.isNoAi());
            }

            if (entity.isSilent()) {
                compoundtag.putBoolean("Silent", entity.isSilent());
            }

            if (entity.isNoGravity()) {
                compoundtag.putBoolean("NoGravity", entity.isNoGravity());
            }

            if (entity.hasGlowingTag()) {
                compoundtag.putBoolean("Glowing", entity.hasGlowingTag());
            }

            if (entity.isInvulnerable()) {
                compoundtag.putBoolean("Invulnerable", entity.isInvulnerable());
            }

            compoundtag.putFloat("Health", entity.getHealth());
        });
    }

    /** @deprecated */
    @Deprecated
    static void loadDefaultDataFromBucketTag(Mob entity, CompoundTag tag) {
        Optional optional = tag.getBoolean("NoAI");

        Objects.requireNonNull(entity);
        optional.ifPresent(entity::setNoAi);
        optional = tag.getBoolean("Silent");
        Objects.requireNonNull(entity);
        optional.ifPresent(entity::setSilent);
        optional = tag.getBoolean("NoGravity");
        Objects.requireNonNull(entity);
        optional.ifPresent(entity::setNoGravity);
        optional = tag.getBoolean("Glowing");
        Objects.requireNonNull(entity);
        optional.ifPresent(entity::setGlowingTag);
        optional = tag.getBoolean("Invulnerable");
        Objects.requireNonNull(entity);
        optional.ifPresent(entity::setInvulnerable);
        optional = tag.getFloat("Health");
        Objects.requireNonNull(entity);
        optional.ifPresent(entity::setHealth);
    }

    static <T extends LivingEntity & Bucketable> Optional<InteractionResult> bucketMobPickup(Player player, InteractionHand hand, T pickupEntity) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (itemstack.getItem() == Items.WATER_BUCKET && pickupEntity.isAlive()) {
            pickupEntity.playSound(((Bucketable) pickupEntity).getPickupSound(), 1.0F, 1.0F);
            ItemStack itemstack1 = ((Bucketable) pickupEntity).getBucketItemStack();

            ((Bucketable) pickupEntity).saveToBucketTag(itemstack1);
            ItemStack itemstack2 = ItemUtils.createFilledResult(itemstack, player, itemstack1, false);

            player.setItemInHand(hand, itemstack2);
            Level level = pickupEntity.level();

            if (!level.isClientSide()) {
                CriteriaTriggers.FILLED_BUCKET.trigger((ServerPlayer) player, itemstack1);
            }

            pickupEntity.discard();
            return Optional.of(InteractionResult.SUCCESS);
        } else {
            return Optional.empty();
        }
    }
}
