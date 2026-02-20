package net.minecraft.server.level;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public interface ServerEntityGetter extends EntityGetter {

    ServerLevel getLevel();

    default @Nullable Player getNearestPlayer(TargetingConditions targetConditions, LivingEntity source) {
        return (Player) this.getNearestEntity(this.players(), targetConditions, source, source.getX(), source.getY(), source.getZ());
    }

    default @Nullable Player getNearestPlayer(TargetingConditions targetConditions, LivingEntity source, double x, double y, double z) {
        return (Player) this.getNearestEntity(this.players(), targetConditions, source, x, y, z);
    }

    default @Nullable Player getNearestPlayer(TargetingConditions targetConditions, double x, double y, double z) {
        return (Player) this.getNearestEntity(this.players(), targetConditions, (LivingEntity) null, x, y, z);
    }

    default <T extends LivingEntity> @Nullable T getNearestEntity(Class<? extends T> type, TargetingConditions targetConditions, @Nullable LivingEntity source, double x, double y, double z, AABB bb) {
        return (T) this.getNearestEntity(this.getEntitiesOfClass(type, bb, (livingentity1) -> {
            return true;
        }), targetConditions, source, x, y, z);
    }

    default @Nullable LivingEntity getNearestEntity(TagKey<EntityType<?>> tag, TargetingConditions targetConditions, @Nullable LivingEntity source, double x, double y, double z, AABB bb) {
        double d3 = Double.MAX_VALUE;
        LivingEntity livingentity1 = null;

        for (LivingEntity livingentity2 : this.getEntitiesOfClass(LivingEntity.class, bb, (livingentity3) -> {
            return livingentity3.getType().is(tag);
        })) {
            if (targetConditions.test(this.getLevel(), source, livingentity2)) {
                double d4 = livingentity2.distanceToSqr(x, y, z);

                if (d4 < d3) {
                    d3 = d4;
                    livingentity1 = livingentity2;
                }
            }
        }

        return livingentity1;
    }

    default <T extends LivingEntity> @Nullable T getNearestEntity(List<? extends T> entities, TargetingConditions targetConditions, @Nullable LivingEntity source, double x, double y, double z) {
        double d3 = -1.0D;
        T t0 = null;

        for (T t1 : entities) {
            if (targetConditions.test(this.getLevel(), source, t1)) {
                double d4 = t1.distanceToSqr(x, y, z);

                if (d3 == -1.0D || d4 < d3) {
                    d3 = d4;
                    t0 = t1;
                }
            }
        }

        return t0;
    }

    default List<Player> getNearbyPlayers(TargetingConditions targetConditions, LivingEntity source, AABB bb) {
        List<Player> list = new ArrayList();

        for (Player player : this.players()) {
            if (bb.contains(player.getX(), player.getY(), player.getZ()) && targetConditions.test(this.getLevel(), source, player)) {
                list.add(player);
            }
        }

        return list;
    }

    default <T extends LivingEntity> List<T> getNearbyEntities(Class<T> type, TargetingConditions targetConditions, LivingEntity source, AABB bb) {
        List<T> list = this.<T>getEntitiesOfClass(type, bb, (livingentity1) -> {
            return true;
        });
        List<T> list1 = new ArrayList();

        for (T t0 : list) {
            if (targetConditions.test(this.getLevel(), source, t0)) {
                list1.add(t0);
            }
        }

        return list1;
    }
}
