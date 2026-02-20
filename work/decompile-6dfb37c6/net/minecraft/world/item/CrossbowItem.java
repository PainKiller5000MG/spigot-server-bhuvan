package net.minecraft.world.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public class CrossbowItem extends ProjectileWeaponItem {

    private static final float MAX_CHARGE_DURATION = 1.25F;
    public static final int DEFAULT_RANGE = 8;
    private boolean startSoundPlayed = false;
    private boolean midLoadSoundPlayed = false;
    private static final float START_SOUND_PERCENT = 0.2F;
    private static final float MID_SOUND_PERCENT = 0.5F;
    private static final float ARROW_POWER = 3.15F;
    private static final float FIREWORK_POWER = 1.6F;
    public static final float MOB_ARROW_POWER = 1.6F;
    private static final CrossbowItem.ChargingSounds DEFAULT_SOUNDS = new CrossbowItem.ChargingSounds(Optional.of(SoundEvents.CROSSBOW_LOADING_START), Optional.of(SoundEvents.CROSSBOW_LOADING_MIDDLE), Optional.of(SoundEvents.CROSSBOW_LOADING_END));

    public CrossbowItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public Predicate<ItemStack> getSupportedHeldProjectiles() {
        return CrossbowItem.ARROW_OR_FIREWORK;
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return CrossbowItem.ARROW_ONLY;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        ChargedProjectiles chargedprojectiles = (ChargedProjectiles) itemstack.get(DataComponents.CHARGED_PROJECTILES);

        if (chargedprojectiles != null && !chargedprojectiles.isEmpty()) {
            this.performShooting(level, player, hand, itemstack, getShootingPower(chargedprojectiles), 1.0F, (LivingEntity) null);
            return InteractionResult.CONSUME;
        } else if (!player.getProjectile(itemstack).isEmpty()) {
            this.startSoundPlayed = false;
            this.midLoadSoundPlayed = false;
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        } else {
            return InteractionResult.FAIL;
        }
    }

    private static float getShootingPower(ChargedProjectiles projectiles) {
        return projectiles.contains(Items.FIREWORK_ROCKET) ? 1.6F : 3.15F;
    }

    @Override
    public boolean releaseUsing(ItemStack itemStack, Level level, LivingEntity entity, int remainingTime) {
        int j = this.getUseDuration(itemStack, entity) - remainingTime;

        return getPowerForTime(j, itemStack, entity) >= 1.0F && isCharged(itemStack);
    }

    private static boolean tryLoadProjectiles(LivingEntity shooter, ItemStack heldItem) {
        List<ItemStack> list = draw(heldItem, shooter.getProjectile(heldItem), shooter);

        if (!list.isEmpty()) {
            heldItem.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(list));
            return true;
        } else {
            return false;
        }
    }

    public static boolean isCharged(ItemStack itemStack) {
        ChargedProjectiles chargedprojectiles = (ChargedProjectiles) itemStack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);

        return !chargedprojectiles.isEmpty();
    }

    @Override
    protected void shootProjectile(LivingEntity livingEntity, Projectile projectileEntity, int index, float power, float uncertainty, float angle, @Nullable LivingEntity targetOverride) {
        Vector3f vector3f;

        if (targetOverride != null) {
            double d0 = targetOverride.getX() - livingEntity.getX();
            double d1 = targetOverride.getZ() - livingEntity.getZ();
            double d2 = Math.sqrt(d0 * d0 + d1 * d1);
            double d3 = targetOverride.getY(0.3333333333333333D) - projectileEntity.getY() + d2 * (double) 0.2F;

            vector3f = getProjectileShotVector(livingEntity, new Vec3(d0, d3, d1), angle);
        } else {
            Vec3 vec3 = livingEntity.getUpVector(1.0F);
            Quaternionf quaternionf = (new Quaternionf()).setAngleAxis((double) (angle * ((float) Math.PI / 180F)), vec3.x, vec3.y, vec3.z);
            Vec3 vec31 = livingEntity.getViewVector(1.0F);

            vector3f = vec31.toVector3f().rotate(quaternionf);
        }

        projectileEntity.shoot((double) vector3f.x(), (double) vector3f.y(), (double) vector3f.z(), power, uncertainty);
        float f3 = getShotPitch(livingEntity.getRandom(), index);

        livingEntity.level().playSound((Entity) null, livingEntity.getX(), livingEntity.getY(), livingEntity.getZ(), SoundEvents.CROSSBOW_SHOOT, livingEntity.getSoundSource(), 1.0F, f3);
    }

    private static Vector3f getProjectileShotVector(LivingEntity body, Vec3 originalVector, float angle) {
        Vector3f vector3f = originalVector.toVector3f().normalize();
        Vector3f vector3f1 = (new Vector3f(vector3f)).cross(new Vector3f(0.0F, 1.0F, 0.0F));

        if ((double) vector3f1.lengthSquared() <= 1.0E-7D) {
            Vec3 vec31 = body.getUpVector(1.0F);

            vector3f1 = (new Vector3f(vector3f)).cross(vec31.toVector3f());
        }

        Vector3f vector3f2 = (new Vector3f(vector3f)).rotateAxis(((float) Math.PI / 2F), vector3f1.x, vector3f1.y, vector3f1.z);

        return (new Vector3f(vector3f)).rotateAxis(angle * ((float) Math.PI / 180F), vector3f2.x, vector3f2.y, vector3f2.z);
    }

    @Override
    protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack heldItem, ItemStack projectile, boolean isCrit) {
        if (projectile.is(Items.FIREWORK_ROCKET)) {
            return new FireworkRocketEntity(level, projectile, shooter, shooter.getX(), shooter.getEyeY() - (double) 0.15F, shooter.getZ(), true);
        } else {
            Projectile projectile1 = super.createProjectile(level, shooter, heldItem, projectile, isCrit);

            if (projectile1 instanceof AbstractArrow) {
                AbstractArrow abstractarrow = (AbstractArrow) projectile1;

                abstractarrow.setSoundEvent(SoundEvents.CROSSBOW_HIT);
            }

            return projectile1;
        }
    }

    @Override
    protected int getDurabilityUse(ItemStack projectile) {
        return projectile.is(Items.FIREWORK_ROCKET) ? 3 : 1;
    }

    public void performShooting(Level level, LivingEntity shooter, InteractionHand hand, ItemStack weapon, float power, float uncertainty, @Nullable LivingEntity targetOverride) {
        if (level instanceof ServerLevel serverlevel) {
            ChargedProjectiles chargedprojectiles = (ChargedProjectiles) weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);

            if (chargedprojectiles != null && !chargedprojectiles.isEmpty()) {
                this.shoot(serverlevel, shooter, hand, weapon, chargedprojectiles.getItems(), power, uncertainty, shooter instanceof Player, targetOverride);
                if (shooter instanceof ServerPlayer) {
                    ServerPlayer serverplayer = (ServerPlayer) shooter;

                    CriteriaTriggers.SHOT_CROSSBOW.trigger(serverplayer, weapon);
                    serverplayer.awardStat(Stats.ITEM_USED.get(weapon.getItem()));
                }

            }
        }
    }

    private static float getShotPitch(RandomSource random, int index) {
        return index == 0 ? 1.0F : getRandomShotPitch((index & 1) == 1, random);
    }

    private static float getRandomShotPitch(boolean highPitch, RandomSource random) {
        float f = highPitch ? 0.63F : 0.43F;

        return 1.0F / (random.nextFloat() * 0.5F + 1.8F) + f;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack itemStack, int ticksRemaining) {
        if (!level.isClientSide()) {
            CrossbowItem.ChargingSounds crossbowitem_chargingsounds = this.getChargingSounds(itemStack);
            float f = (float) (itemStack.getUseDuration(entity) - ticksRemaining) / (float) getChargeDuration(itemStack, entity);

            if (f < 0.2F) {
                this.startSoundPlayed = false;
                this.midLoadSoundPlayed = false;
            }

            if (f >= 0.2F && !this.startSoundPlayed) {
                this.startSoundPlayed = true;
                crossbowitem_chargingsounds.start().ifPresent((holder) -> {
                    level.playSound((Entity) null, entity.getX(), entity.getY(), entity.getZ(), (SoundEvent) holder.value(), SoundSource.PLAYERS, 0.5F, 1.0F);
                });
            }

            if (f >= 0.5F && !this.midLoadSoundPlayed) {
                this.midLoadSoundPlayed = true;
                crossbowitem_chargingsounds.mid().ifPresent((holder) -> {
                    level.playSound((Entity) null, entity.getX(), entity.getY(), entity.getZ(), (SoundEvent) holder.value(), SoundSource.PLAYERS, 0.5F, 1.0F);
                });
            }

            if (f >= 1.0F && !isCharged(itemStack) && tryLoadProjectiles(entity, itemStack)) {
                crossbowitem_chargingsounds.end().ifPresent((holder) -> {
                    level.playSound((Entity) null, entity.getX(), entity.getY(), entity.getZ(), (SoundEvent) holder.value(), entity.getSoundSource(), 1.0F, 1.0F / (level.getRandom().nextFloat() * 0.5F + 1.0F) + 0.2F);
                });
            }
        }

    }

    @Override
    public int getUseDuration(ItemStack itemStack, LivingEntity user) {
        return 72000;
    }

    public static int getChargeDuration(ItemStack crossbow, LivingEntity user) {
        float f = EnchantmentHelper.modifyCrossbowChargingTime(crossbow, user, 1.25F);

        return Mth.floor(f * 20.0F);
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack itemStack) {
        return ItemUseAnimation.CROSSBOW;
    }

    CrossbowItem.ChargingSounds getChargingSounds(ItemStack itemStack) {
        return (CrossbowItem.ChargingSounds) EnchantmentHelper.pickHighestLevel(itemStack, EnchantmentEffectComponents.CROSSBOW_CHARGING_SOUNDS).orElse(CrossbowItem.DEFAULT_SOUNDS);
    }

    private static float getPowerForTime(int timeHeld, ItemStack itemStack, LivingEntity holder) {
        float f = (float) timeHeld / (float) getChargeDuration(itemStack, holder);

        if (f > 1.0F) {
            f = 1.0F;
        }

        return f;
    }

    @Override
    public boolean useOnRelease(ItemStack itemStack) {
        return itemStack.is(this);
    }

    @Override
    public int getDefaultProjectileRange() {
        return 8;
    }

    public static record ChargingSounds(Optional<Holder<SoundEvent>> start, Optional<Holder<SoundEvent>> mid, Optional<Holder<SoundEvent>> end) {

        public static final Codec<CrossbowItem.ChargingSounds> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(SoundEvent.CODEC.optionalFieldOf("start").forGetter(CrossbowItem.ChargingSounds::start), SoundEvent.CODEC.optionalFieldOf("mid").forGetter(CrossbowItem.ChargingSounds::mid), SoundEvent.CODEC.optionalFieldOf("end").forGetter(CrossbowItem.ChargingSounds::end)).apply(instance, CrossbowItem.ChargingSounds::new);
        });
    }

    public static enum ChargeType implements StringRepresentable {

        NONE("none"), ARROW("arrow"), ROCKET("rocket");

        public static final Codec<CrossbowItem.ChargeType> CODEC = StringRepresentable.<CrossbowItem.ChargeType>fromEnum(CrossbowItem.ChargeType::values);
        private final String name;

        private ChargeType(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
