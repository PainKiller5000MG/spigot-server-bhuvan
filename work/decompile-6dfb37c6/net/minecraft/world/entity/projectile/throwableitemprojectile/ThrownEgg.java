package net.minecraft.world.entity.projectile.throwableitemprojectile;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class ThrownEgg extends ThrowableItemProjectile {

    private static final EntityDimensions ZERO_SIZED_DIMENSIONS = EntityDimensions.fixed(0.0F, 0.0F);

    public ThrownEgg(EntityType<? extends ThrownEgg> type, Level level) {
        super(type, level);
    }

    public ThrownEgg(Level level, LivingEntity mob, ItemStack itemStack) {
        super(EntityType.EGG, mob, level, itemStack);
    }

    public ThrownEgg(Level level, double x, double y, double z, ItemStack itemStack) {
        super(EntityType.EGG, x, y, z, level, itemStack);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 3) {
            double d0 = 0.08D;

            for (int i = 0; i < 8; ++i) {
                this.level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, this.getItem()), this.getX(), this.getY(), this.getZ(), ((double) this.random.nextFloat() - 0.5D) * 0.08D, ((double) this.random.nextFloat() - 0.5D) * 0.08D, ((double) this.random.nextFloat() - 0.5D) * 0.08D);
            }
        }

    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        hitResult.getEntity().hurt(this.damageSources().thrown(this, this.getOwner()), 0.0F);
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!this.level().isClientSide()) {
            if (this.random.nextInt(8) == 0) {
                int i = 1;

                if (this.random.nextInt(32) == 0) {
                    i = 4;
                }

                for (int j = 0; j < i; ++j) {
                    Chicken chicken = EntityType.CHICKEN.create(this.level(), EntitySpawnReason.TRIGGERED);

                    if (chicken != null) {
                        chicken.setAge(-24000);
                        chicken.snapTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
                        Optional optional = Optional.ofNullable((EitherHolder) this.getItem().get(DataComponents.CHICKEN_VARIANT)).flatMap((eitherholder) -> {
                            return eitherholder.unwrap(this.registryAccess());
                        });

                        Objects.requireNonNull(chicken);
                        optional.ifPresent(chicken::setVariant);
                        if (!chicken.fudgePositionAfterSizeChange(ThrownEgg.ZERO_SIZED_DIMENSIONS)) {
                            break;
                        }

                        this.level().addFreshEntity(chicken);
                    }
                }
            }

            this.level().broadcastEntityEvent(this, (byte) 3);
            this.discard();
        }

    }

    @Override
    protected Item getDefaultItem() {
        return Items.EGG;
    }
}
