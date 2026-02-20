package net.minecraft.world.level;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public interface EntityGetter {

    List<Entity> getEntities(@Nullable Entity except, AABB bb, Predicate<? super Entity> selector);

    <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> type, AABB bb, Predicate<? super T> selector);

    default <T extends Entity> List<T> getEntitiesOfClass(Class<T> baseClass, AABB bb, Predicate<? super T> selector) {
        return this.getEntities(EntityTypeTest.forClass(baseClass), bb, selector);
    }

    List<? extends Player> players();

    default List<Entity> getEntities(@Nullable Entity except, AABB bb) {
        return this.getEntities(except, bb, EntitySelector.NO_SPECTATORS);
    }

    default boolean isUnobstructed(@Nullable Entity source, VoxelShape shape) {
        if (shape.isEmpty()) {
            return true;
        } else {
            for (Entity entity1 : this.getEntities(source, shape.bounds())) {
                if (!entity1.isRemoved() && entity1.blocksBuilding && (source == null || !entity1.isPassengerOfSameVehicle(source)) && Shapes.joinIsNotEmpty(shape, Shapes.create(entity1.getBoundingBox()), BooleanOp.AND)) {
                    return false;
                }
            }

            return true;
        }
    }

    default <T extends Entity> List<T> getEntitiesOfClass(Class<T> baseClass, AABB bb) {
        return this.<T>getEntitiesOfClass(baseClass, bb, EntitySelector.NO_SPECTATORS);
    }

    default List<VoxelShape> getEntityCollisions(@Nullable Entity source, AABB testArea) {
        if (testArea.getSize() < 1.0E-7D) {
            return List.of();
        } else {
            Predicate predicate;

            if (source == null) {
                predicate = EntitySelector.CAN_BE_COLLIDED_WITH;
            } else {
                predicate = EntitySelector.NO_SPECTATORS;
                Objects.requireNonNull(source);
                predicate = predicate.and(source::canCollideWith);
            }

            Predicate<Entity> predicate1 = predicate;
            List<Entity> list = this.getEntities(source, testArea.inflate(1.0E-7D), predicate1);

            if (list.isEmpty()) {
                return List.of();
            } else {
                ImmutableList.Builder<VoxelShape> immutablelist_builder = ImmutableList.builderWithExpectedSize(list.size());

                for (Entity entity1 : list) {
                    immutablelist_builder.add(Shapes.create(entity1.getBoundingBox()));
                }

                return immutablelist_builder.build();
            }
        }
    }

    default @Nullable Player getNearestPlayer(double x, double y, double z, double range, @Nullable Predicate<Entity> predicate) {
        double d4 = -1.0D;
        Player player = null;

        for (Player player1 : this.players()) {
            if (predicate == null || predicate.test(player1)) {
                double d5 = player1.distanceToSqr(x, y, z);

                if ((range < 0.0D || d5 < range * range) && (d4 == -1.0D || d5 < d4)) {
                    d4 = d5;
                    player = player1;
                }
            }
        }

        return player;
    }

    default @Nullable Player getNearestPlayer(Entity source, double maxDist) {
        return this.getNearestPlayer(source.getX(), source.getY(), source.getZ(), maxDist, false);
    }

    default @Nullable Player getNearestPlayer(double x, double y, double z, double maxDist, boolean filterOutCreative) {
        Predicate<Entity> predicate = filterOutCreative ? EntitySelector.NO_CREATIVE_OR_SPECTATOR : EntitySelector.NO_SPECTATORS;

        return this.getNearestPlayer(x, y, z, maxDist, predicate);
    }

    default boolean hasNearbyAlivePlayer(double x, double y, double z, double range) {
        for (Player player : this.players()) {
            if (EntitySelector.NO_SPECTATORS.test(player) && EntitySelector.LIVING_ENTITY_STILL_ALIVE.test(player)) {
                double d4 = player.distanceToSqr(x, y, z);

                if (range < 0.0D || d4 < range * range) {
                    return true;
                }
            }
        }

        return false;
    }

    default @Nullable Player getPlayerByUUID(UUID uuid) {
        for (int i = 0; i < this.players().size(); ++i) {
            Player player = (Player) this.players().get(i);

            if (uuid.equals(player.getUUID())) {
                return player;
            }
        }

        return null;
    }
}
