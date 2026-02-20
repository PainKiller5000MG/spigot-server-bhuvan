package net.minecraft.world.entity.projectile;

import com.mojang.datafixers.util.Either;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class ProjectileUtil {

    public static final float DEFAULT_ENTITY_HIT_RESULT_MARGIN = 0.3F;

    public ProjectileUtil() {}

    public static HitResult getHitResultOnMoveVector(Entity source, Predicate<Entity> matching) {
        Vec3 vec3 = source.getDeltaMovement();
        Level level = source.level();
        Vec3 vec31 = source.position();

        return getHitResult(vec31, source, matching, vec3, level, computeMargin(source), ClipContext.Block.COLLIDER);
    }

    public static Either<BlockHitResult, Collection<EntityHitResult>> getHitEntitiesAlong(Entity attacker, AttackRange attackRange, Predicate<Entity> matching, ClipContext.Block blockClipType) {
        Vec3 vec3 = attacker.getHeadLookAngle();
        Vec3 vec31 = attacker.getEyePosition();
        Vec3 vec32 = vec31.add(vec3.scale((double) attackRange.effectiveMinRange(attacker)));
        double d0 = attacker.getKnownMovement().dot(vec3);
        Vec3 vec33 = vec31.add(vec3.scale((double) attackRange.effectiveMaxRange(attacker) + Math.max(0.0D, d0)));

        return getHitEntitiesAlong(attacker, vec31, vec32, matching, vec33, attackRange.hitboxMargin(), blockClipType);
    }

    public static HitResult getHitResultOnMoveVector(Entity source, Predicate<Entity> matching, ClipContext.Block clipType) {
        Vec3 vec3 = source.getDeltaMovement();
        Level level = source.level();
        Vec3 vec31 = source.position();

        return getHitResult(vec31, source, matching, vec3, level, computeMargin(source), clipType);
    }

    public static HitResult getHitResultOnViewVector(Entity source, Predicate<Entity> matching, double distance) {
        Vec3 vec3 = source.getViewVector(0.0F).scale(distance);
        Level level = source.level();
        Vec3 vec31 = source.getEyePosition();

        return getHitResult(vec31, source, matching, vec3, level, 0.0F, ClipContext.Block.COLLIDER);
    }

    private static HitResult getHitResult(Vec3 from, Entity source, Predicate<Entity> matching, Vec3 delta, Level level, float entityMargin, ClipContext.Block clipType) {
        Vec3 vec32 = from.add(delta);
        HitResult hitresult = level.clipIncludingBorder(new ClipContext(from, vec32, clipType, ClipContext.Fluid.NONE, source));

        if (hitresult.getType() != HitResult.Type.MISS) {
            vec32 = hitresult.getLocation();
        }

        HitResult hitresult1 = getEntityHitResult(level, source, from, vec32, source.getBoundingBox().expandTowards(delta).inflate(1.0D), matching, entityMargin);

        if (hitresult1 != null) {
            hitresult = hitresult1;
        }

        return hitresult;
    }

    private static Either<BlockHitResult, Collection<EntityHitResult>> getHitEntitiesAlong(Entity source, Vec3 origin, Vec3 from, Predicate<Entity> matching, Vec3 to, float entityMargin, ClipContext.Block clipType) {
        Level level = source.level();
        BlockHitResult blockhitresult = level.clipIncludingBorder(new ClipContext(origin, to, clipType, ClipContext.Fluid.NONE, source));

        if (blockhitresult.getType() != HitResult.Type.MISS) {
            to = blockhitresult.getLocation();
            if (origin.distanceToSqr(to) < origin.distanceToSqr(from)) {
                return Either.left(blockhitresult);
            }
        }

        AABB aabb = AABB.ofSize(from, (double) entityMargin, (double) entityMargin, (double) entityMargin).expandTowards(to.subtract(from)).inflate(1.0D);
        Collection<EntityHitResult> collection = getManyEntityHitResult(level, source, from, to, aabb, matching, entityMargin, clipType, true);

        return !collection.isEmpty() ? Either.right(collection) : Either.left(blockhitresult);
    }

    public static @Nullable EntityHitResult getEntityHitResult(Entity except, Vec3 from, Vec3 to, AABB box, Predicate<Entity> matching, double maxValue) {
        Level level = except.level();
        double d1 = maxValue;
        Entity entity1 = null;
        Vec3 vec32 = null;

        for (Entity entity2 : level.getEntities(except, box, matching)) {
            AABB aabb1 = entity2.getBoundingBox().inflate((double) entity2.getPickRadius());
            Optional<Vec3> optional = aabb1.clip(from, to);

            if (aabb1.contains(from)) {
                if (d1 >= 0.0D) {
                    entity1 = entity2;
                    vec32 = (Vec3) optional.orElse(from);
                    d1 = 0.0D;
                }
            } else if (optional.isPresent()) {
                Vec3 vec33 = (Vec3) optional.get();
                double d2 = from.distanceToSqr(vec33);

                if (d2 < d1 || d1 == 0.0D) {
                    if (entity2.getRootVehicle() == except.getRootVehicle()) {
                        if (d1 == 0.0D) {
                            entity1 = entity2;
                            vec32 = vec33;
                        }
                    } else {
                        entity1 = entity2;
                        vec32 = vec33;
                        d1 = d2;
                    }
                }
            }
        }

        if (entity1 == null) {
            return null;
        } else {
            return new EntityHitResult(entity1, vec32);
        }
    }

    public static @Nullable EntityHitResult getEntityHitResult(Level level, Projectile source, Vec3 from, Vec3 to, AABB targetSearchArea, Predicate<Entity> matching) {
        return getEntityHitResult(level, source, from, to, targetSearchArea, matching, computeMargin(source));
    }

    public static float computeMargin(Entity source) {
        return Math.max(0.0F, Math.min(0.3F, (float) (source.tickCount - 2) / 20.0F));
    }

    public static @Nullable EntityHitResult getEntityHitResult(Level level, Entity source, Vec3 from, Vec3 to, AABB targetSearchArea, Predicate<Entity> matching, float entityMargin) {
        double d0 = Double.MAX_VALUE;
        Optional<Vec3> optional = Optional.empty();
        Entity entity1 = null;

        for (Entity entity2 : level.getEntities(source, targetSearchArea, matching)) {
            AABB aabb1 = entity2.getBoundingBox().inflate((double) entityMargin);
            Optional<Vec3> optional1 = aabb1.clip(from, to);

            if (optional1.isPresent()) {
                double d1 = from.distanceToSqr((Vec3) optional1.get());

                if (d1 < d0) {
                    entity1 = entity2;
                    d0 = d1;
                    optional = optional1;
                }
            }
        }

        if (entity1 == null) {
            return null;
        } else {
            return new EntityHitResult(entity1, (Vec3) optional.get());
        }
    }

    public static Collection<EntityHitResult> getManyEntityHitResult(Level level, Entity source, Vec3 from, Vec3 to, AABB targetSearchArea, Predicate<Entity> matching, boolean includeFromEntity) {
        return getManyEntityHitResult(level, source, from, to, targetSearchArea, matching, computeMargin(source), ClipContext.Block.COLLIDER, includeFromEntity);
    }

    public static Collection<EntityHitResult> getManyEntityHitResult(Level level, Entity source, Vec3 from, Vec3 to, AABB targetSearchArea, Predicate<Entity> matching, float entityMargin, ClipContext.Block clipType, boolean includeFromEntity) {
        List<EntityHitResult> list = new ArrayList();

        for (Entity entity1 : level.getEntities(source, targetSearchArea, matching)) {
            AABB aabb1 = entity1.getBoundingBox();

            if (includeFromEntity && aabb1.contains(from)) {
                list.add(new EntityHitResult(entity1, from));
            } else {
                Optional<Vec3> optional = aabb1.clip(from, to);

                if (optional.isPresent()) {
                    list.add(new EntityHitResult(entity1, (Vec3) optional.get()));
                } else if ((double) entityMargin > 0.0D) {
                    Optional<Vec3> optional1 = aabb1.inflate((double) entityMargin).clip(from, to);

                    if (!optional1.isEmpty()) {
                        Vec3 vec32 = (Vec3) optional1.get();
                        Vec3 vec33 = aabb1.getCenter();
                        BlockHitResult blockhitresult = level.clipIncludingBorder(new ClipContext(vec32, vec33, clipType, ClipContext.Fluid.NONE, source));

                        if (blockhitresult.getType() != HitResult.Type.MISS) {
                            vec33 = blockhitresult.getLocation();
                        }

                        Optional<Vec3> optional2 = entity1.getBoundingBox().clip(vec32, vec33);

                        if (optional2.isPresent()) {
                            list.add(new EntityHitResult(entity1, (Vec3) optional2.get()));
                        }
                    }
                }
            }
        }

        return list;
    }

    public static void rotateTowardsMovement(Entity projectile, float rotationSpeed) {
        Vec3 vec3 = projectile.getDeltaMovement();

        if (vec3.lengthSqr() != 0.0D) {
            double d0 = vec3.horizontalDistance();

            projectile.setYRot((float) (Mth.atan2(vec3.z, vec3.x) * (double) (180F / (float) Math.PI)) + 90.0F);
            projectile.setXRot((float) (Mth.atan2(d0, vec3.y) * (double) (180F / (float) Math.PI)) - 90.0F);

            while (projectile.getXRot() - projectile.xRotO < -180.0F) {
                projectile.xRotO -= 360.0F;
            }

            while (projectile.getXRot() - projectile.xRotO >= 180.0F) {
                projectile.xRotO += 360.0F;
            }

            while (projectile.getYRot() - projectile.yRotO < -180.0F) {
                projectile.yRotO -= 360.0F;
            }

            while (projectile.getYRot() - projectile.yRotO >= 180.0F) {
                projectile.yRotO += 360.0F;
            }

            projectile.setXRot(Mth.lerp(rotationSpeed, projectile.xRotO, projectile.getXRot()));
            projectile.setYRot(Mth.lerp(rotationSpeed, projectile.yRotO, projectile.getYRot()));
        }
    }

    public static InteractionHand getWeaponHoldingHand(LivingEntity mob, Item weaponItem) {
        return mob.getMainHandItem().is(weaponItem) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    public static AbstractArrow getMobArrow(LivingEntity mob, ItemStack projectile, float power, @Nullable ItemStack firedFromWeapon) {
        ArrowItem arrowitem = (ArrowItem) (projectile.getItem() instanceof ArrowItem ? projectile.getItem() : Items.ARROW);
        AbstractArrow abstractarrow = arrowitem.createArrow(mob.level(), projectile, mob, firedFromWeapon);

        abstractarrow.setBaseDamageFromMob(power);
        return abstractarrow;
    }
}
