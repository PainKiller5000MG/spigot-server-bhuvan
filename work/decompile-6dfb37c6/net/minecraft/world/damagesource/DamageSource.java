package net.minecraft.world.damagesource;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class DamageSource {

    private final Holder<DamageType> type;
    private final @Nullable Entity causingEntity;
    private final @Nullable Entity directEntity;
    private final @Nullable Vec3 damageSourcePosition;

    public String toString() {
        return "DamageSource (" + this.type().msgId() + ")";
    }

    public float getFoodExhaustion() {
        return this.type().exhaustion();
    }

    public boolean isDirect() {
        return this.causingEntity == this.directEntity;
    }

    public DamageSource(Holder<DamageType> type, @Nullable Entity directEntity, @Nullable Entity causingEntity, @Nullable Vec3 damageSourcePosition) {
        this.type = type;
        this.causingEntity = causingEntity;
        this.directEntity = directEntity;
        this.damageSourcePosition = damageSourcePosition;
    }

    public DamageSource(Holder<DamageType> type, @Nullable Entity directEntity, @Nullable Entity causingEntity) {
        this(type, directEntity, causingEntity, (Vec3) null);
    }

    public DamageSource(Holder<DamageType> type, Vec3 damageSourcePosition) {
        this(type, (Entity) null, (Entity) null, damageSourcePosition);
    }

    public DamageSource(Holder<DamageType> type, @Nullable Entity causingEntity) {
        this(type, causingEntity, causingEntity);
    }

    public DamageSource(Holder<DamageType> type) {
        this(type, (Entity) null, (Entity) null, (Vec3) null);
    }

    public @Nullable Entity getDirectEntity() {
        return this.directEntity;
    }

    public @Nullable Entity getEntity() {
        return this.causingEntity;
    }

    public @Nullable ItemStack getWeaponItem() {
        return this.directEntity != null ? this.directEntity.getWeaponItem() : null;
    }

    public Component getLocalizedDeathMessage(LivingEntity victim) {
        String s = "death.attack." + this.type().msgId();

        if (this.causingEntity == null && this.directEntity == null) {
            LivingEntity livingentity1 = victim.getKillCredit();
            String s1 = s + ".player";

            return livingentity1 != null ? Component.translatable(s1, victim.getDisplayName(), livingentity1.getDisplayName()) : Component.translatable(s, victim.getDisplayName());
        } else {
            Component component = this.causingEntity == null ? this.directEntity.getDisplayName() : this.causingEntity.getDisplayName();
            Entity entity = this.causingEntity;
            ItemStack itemstack;

            if (entity instanceof LivingEntity) {
                LivingEntity livingentity2 = (LivingEntity) entity;

                itemstack = livingentity2.getMainHandItem();
            } else {
                itemstack = ItemStack.EMPTY;
            }

            ItemStack itemstack1 = itemstack;

            return !itemstack1.isEmpty() && itemstack1.has(DataComponents.CUSTOM_NAME) ? Component.translatable(s + ".item", victim.getDisplayName(), component, itemstack1.getDisplayName()) : Component.translatable(s, victim.getDisplayName(), component);
        }
    }

    public String getMsgId() {
        return this.type().msgId();
    }

    public boolean scalesWithDifficulty() {
        boolean flag;

        switch (this.type().scaling()) {
            case NEVER:
                flag = false;
                break;
            case WHEN_CAUSED_BY_LIVING_NON_PLAYER:
                flag = this.causingEntity instanceof LivingEntity && !(this.causingEntity instanceof Player);
                break;
            case ALWAYS:
                flag = true;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return flag;
    }

    public boolean isCreativePlayer() {
        Entity entity = this.getEntity();
        boolean flag;

        if (entity instanceof Player player) {
            if (player.getAbilities().instabuild) {
                flag = true;
                return flag;
            }
        }

        flag = false;
        return flag;
    }

    public @Nullable Vec3 getSourcePosition() {
        return this.damageSourcePosition != null ? this.damageSourcePosition : (this.directEntity != null ? this.directEntity.position() : null);
    }

    public @Nullable Vec3 sourcePositionRaw() {
        return this.damageSourcePosition;
    }

    public boolean is(TagKey<DamageType> tag) {
        return this.type.is(tag);
    }

    public boolean is(ResourceKey<DamageType> typeKey) {
        return this.type.is(typeKey);
    }

    public DamageType type() {
        return this.type.value();
    }

    public Holder<DamageType> typeHolder() {
        return this.type;
    }
}
