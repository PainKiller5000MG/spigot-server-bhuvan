package net.minecraft.world.item.component;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public record AttackRange(float minRange, float maxRange, float minCreativeRange, float maxCreativeRange, float hitboxMargin, float mobFactor) {

    public static final Codec<AttackRange> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(ExtraCodecs.floatRange(0.0F, 64.0F).optionalFieldOf("min_reach", 0.0F).forGetter(AttackRange::minRange), ExtraCodecs.floatRange(0.0F, 64.0F).optionalFieldOf("max_reach", 3.0F).forGetter(AttackRange::maxRange), ExtraCodecs.floatRange(0.0F, 64.0F).optionalFieldOf("min_creative_reach", 0.0F).forGetter(AttackRange::minCreativeRange), ExtraCodecs.floatRange(0.0F, 64.0F).optionalFieldOf("max_creative_reach", 5.0F).forGetter(AttackRange::maxCreativeRange), ExtraCodecs.floatRange(0.0F, 1.0F).optionalFieldOf("hitbox_margin", 0.3F).forGetter(AttackRange::hitboxMargin), Codec.floatRange(0.0F, 2.0F).optionalFieldOf("mob_factor", 1.0F).forGetter(AttackRange::mobFactor)).apply(instance, AttackRange::new);
    });
    public static final StreamCodec<ByteBuf, AttackRange> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.FLOAT, AttackRange::minRange, ByteBufCodecs.FLOAT, AttackRange::maxRange, ByteBufCodecs.FLOAT, AttackRange::minCreativeRange, ByteBufCodecs.FLOAT, AttackRange::maxCreativeRange, ByteBufCodecs.FLOAT, AttackRange::hitboxMargin, ByteBufCodecs.FLOAT, AttackRange::mobFactor, AttackRange::new);

    public static AttackRange defaultFor(LivingEntity livingEntity) {
        return new AttackRange(0.0F, (float) livingEntity.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE), 0.0F, (float) livingEntity.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE), 0.0F, 1.0F);
    }

    public HitResult getClosesetHit(Entity attacker, float partial, Predicate<Entity> matching) {
        Either<BlockHitResult, Collection<EntityHitResult>> either = ProjectileUtil.getHitEntitiesAlong(attacker, this, matching, ClipContext.Block.OUTLINE);

        if (either.left().isPresent()) {
            return (HitResult) either.left().get();
        } else {
            Collection<EntityHitResult> collection = (Collection) either.right().get();
            EntityHitResult entityhitresult = null;
            Vec3 vec3 = attacker.getEyePosition(partial);
            double d0 = Double.MAX_VALUE;

            for (EntityHitResult entityhitresult1 : collection) {
                double d1 = vec3.distanceToSqr(entityhitresult1.getLocation());

                if (d1 < d0) {
                    d0 = d1;
                    entityhitresult = entityhitresult1;
                }
            }

            if (entityhitresult != null) {
                return entityhitresult;
            } else {
                Vec3 vec31 = attacker.getHeadLookAngle();
                Vec3 vec32 = attacker.getEyePosition(partial).add(vec31);

                return BlockHitResult.miss(vec32, Direction.getApproximateNearest(vec31), BlockPos.containing(vec32));
            }
        }
    }

    public float effectiveMinRange(Entity entity) {
        if (entity instanceof Player player) {
            return player.isSpectator() ? 0.0F : (player.isCreative() ? this.minCreativeRange : this.minRange);
        } else {
            return this.minRange * this.mobFactor;
        }
    }

    public float effectiveMaxRange(Entity entity) {
        if (entity instanceof Player player) {
            return player.isCreative() ? this.maxCreativeRange : this.maxRange;
        } else {
            return this.maxRange * this.mobFactor;
        }
    }

    public boolean isInRange(LivingEntity attacker, Vec3 location) {
        Objects.requireNonNull(location);
        return this.isInRange(attacker, location::distanceToSqr, 0.0D);
    }

    public boolean isInRange(LivingEntity attacker, AABB boundingBox, double extraBuffer) {
        Objects.requireNonNull(boundingBox);
        return this.isInRange(attacker, boundingBox::distanceToSqr, extraBuffer);
    }

    private boolean isInRange(LivingEntity attacker, ToDoubleFunction<Vec3> distanceFunction, double extraBuffer) {
        double d1 = Math.sqrt(distanceFunction.applyAsDouble(attacker.getEyePosition()));
        double d2 = (double) (this.effectiveMinRange(attacker) - this.hitboxMargin) - extraBuffer;
        double d3 = (double) (this.effectiveMaxRange(attacker) + this.hitboxMargin) + extraBuffer;

        return d1 >= d2 && d1 <= d3;
    }
}
