package net.minecraft.world.entity.projectile.throwableitemprojectile;

import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public abstract class AbstractThrownPotion extends ThrowableItemProjectile {

    public static final double SPLASH_RANGE = 4.0D;
    protected static final double SPLASH_RANGE_SQ = 16.0D;
    public static final Predicate<LivingEntity> WATER_SENSITIVE_OR_ON_FIRE = (livingentity) -> {
        return livingentity.isSensitiveToWater() || livingentity.isOnFire();
    };

    public AbstractThrownPotion(EntityType<? extends AbstractThrownPotion> type, Level level) {
        super(type, level);
    }

    public AbstractThrownPotion(EntityType<? extends AbstractThrownPotion> type, Level level, LivingEntity owner, ItemStack itemStack) {
        super(type, owner, level, itemStack);
    }

    public AbstractThrownPotion(EntityType<? extends AbstractThrownPotion> type, Level level, double x, double y, double z, ItemStack itemStack) {
        super(type, x, y, z, level, itemStack);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05D;
    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        super.onHitBlock(hitResult);
        if (!this.level().isClientSide()) {
            ItemStack itemstack = this.getItem();
            Direction direction = hitResult.getDirection();
            BlockPos blockpos = hitResult.getBlockPos();
            BlockPos blockpos1 = blockpos.relative(direction);
            PotionContents potioncontents = (PotionContents) itemstack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);

            if (potioncontents.is(Potions.WATER)) {
                this.dowseFire(blockpos1);
                this.dowseFire(blockpos1.relative(direction.getOpposite()));

                for (Direction direction1 : Direction.Plane.HORIZONTAL) {
                    this.dowseFire(blockpos1.relative(direction1));
                }
            }

        }
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            ItemStack itemstack = this.getItem();
            PotionContents potioncontents = (PotionContents) itemstack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);

            if (potioncontents.is(Potions.WATER)) {
                this.onHitAsWater(serverlevel);
            } else if (potioncontents.hasEffects()) {
                this.onHitAsPotion(serverlevel, itemstack, hitResult);
            }

            int i = potioncontents.potion().isPresent() && ((Potion) ((Holder) potioncontents.potion().get()).value()).hasInstantEffects() ? 2007 : 2002;

            serverlevel.levelEvent(i, this.blockPosition(), potioncontents.getColor());
            this.discard();
        }
    }

    private void onHitAsWater(ServerLevel level) {
        AABB aabb = this.getBoundingBox().inflate(4.0D, 2.0D, 4.0D);

        for (LivingEntity livingentity : this.level().getEntitiesOfClass(LivingEntity.class, aabb, AbstractThrownPotion.WATER_SENSITIVE_OR_ON_FIRE)) {
            double d0 = this.distanceToSqr((Entity) livingentity);

            if (d0 < 16.0D) {
                if (livingentity.isSensitiveToWater()) {
                    livingentity.hurtServer(level, this.damageSources().indirectMagic(this, this.getOwner()), 1.0F);
                }

                if (livingentity.isOnFire() && livingentity.isAlive()) {
                    livingentity.extinguishFire();
                }
            }
        }

        for (Axolotl axolotl : this.level().getEntitiesOfClass(Axolotl.class, aabb)) {
            axolotl.rehydrate();
        }

    }

    protected abstract void onHitAsPotion(ServerLevel level, ItemStack potionItem, HitResult hitResult);

    private void dowseFire(BlockPos pos) {
        BlockState blockstate = this.level().getBlockState(pos);

        if (blockstate.is(BlockTags.FIRE)) {
            this.level().destroyBlock(pos, false, this);
        } else if (AbstractCandleBlock.isLit(blockstate)) {
            AbstractCandleBlock.extinguish((Player) null, blockstate, this.level(), pos);
        } else if (CampfireBlock.isLitCampfire(blockstate)) {
            this.level().levelEvent((Entity) null, 1009, pos, 0);
            CampfireBlock.dowse(this.getOwner(), this.level(), pos, blockstate);
            this.level().setBlockAndUpdate(pos, (BlockState) blockstate.setValue(CampfireBlock.LIT, false));
        }

    }

    @Override
    public DoubleDoubleImmutablePair calculateHorizontalHurtKnockbackDirection(LivingEntity hurtEntity, DamageSource damageSource) {
        double d0 = hurtEntity.position().x - this.position().x;
        double d1 = hurtEntity.position().z - this.position().z;

        return DoubleDoubleImmutablePair.of(d0, d1);
    }
}
