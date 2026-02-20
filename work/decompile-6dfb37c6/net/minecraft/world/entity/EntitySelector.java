package net.minecraft.world.entity;

import com.google.common.base.Predicates;
import java.util.function.Predicate;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Team;

public final class EntitySelector {

    public static final Predicate<Entity> ENTITY_STILL_ALIVE = Entity::isAlive;
    public static final Predicate<Entity> LIVING_ENTITY_STILL_ALIVE = (entity) -> {
        return entity.isAlive() && entity instanceof LivingEntity;
    };
    public static final Predicate<Entity> ENTITY_NOT_BEING_RIDDEN = (entity) -> {
        return entity.isAlive() && !entity.isVehicle() && !entity.isPassenger();
    };
    public static final Predicate<Entity> CONTAINER_ENTITY_SELECTOR = (entity) -> {
        return entity instanceof Container && entity.isAlive();
    };
    public static final Predicate<Entity> NO_CREATIVE_OR_SPECTATOR = (entity) -> {
        boolean flag;

        if (entity instanceof Player player) {
            if (entity.isSpectator() || player.isCreative()) {
                flag = false;
                return flag;
            }
        }

        flag = true;
        return flag;
    };
    public static final Predicate<Entity> NO_SPECTATORS = (entity) -> {
        return !entity.isSpectator();
    };
    public static final Predicate<Entity> CAN_BE_COLLIDED_WITH = EntitySelector.NO_SPECTATORS.and((entity) -> {
        return entity.canBeCollidedWith((Entity) null);
    });
    public static final Predicate<Entity> CAN_BE_PICKED = EntitySelector.NO_SPECTATORS.and(Entity::isPickable);

    private EntitySelector() {}

    public static Predicate<Entity> withinDistance(double centerX, double centerY, double centerZ, double distance) {
        double d4 = distance * distance;

        return (entity) -> {
            return entity.distanceToSqr(centerX, centerY, centerZ) <= d4;
        };
    }

    public static Predicate<Entity> pushableBy(Entity entity) {
        Team team = entity.getTeam();
        Team.CollisionRule team_collisionrule = team == null ? Team.CollisionRule.ALWAYS : team.getCollisionRule();

        return (Predicate<Entity>) (team_collisionrule == Team.CollisionRule.NEVER ? Predicates.alwaysFalse() : EntitySelector.NO_SPECTATORS.and((entity1) -> {
            if (!entity1.isPushable()) {
                return false;
            } else {
                if (entity.level().isClientSide()) {
                    if (!(entity1 instanceof Player)) {
                        return false;
                    }

                    Player player = (Player) entity1;

                    if (!player.isLocalPlayer()) {
                        return false;
                    }
                }

                Team team1 = entity1.getTeam();
                Team.CollisionRule team_collisionrule1 = team1 == null ? Team.CollisionRule.ALWAYS : team1.getCollisionRule();

                if (team_collisionrule1 == Team.CollisionRule.NEVER) {
                    return false;
                } else {
                    boolean flag = team != null && team.isAlliedTo(team1);

                    if ((team_collisionrule == Team.CollisionRule.PUSH_OWN_TEAM || team_collisionrule1 == Team.CollisionRule.PUSH_OWN_TEAM) && flag) {
                        return false;
                    } else if ((team_collisionrule == Team.CollisionRule.PUSH_OTHER_TEAMS || team_collisionrule1 == Team.CollisionRule.PUSH_OTHER_TEAMS) && !flag) {
                        return false;
                    } else {
                        return true;
                    }
                }
            }
        }));
    }

    public static Predicate<Entity> notRiding(Entity entity) {
        return (entity1) -> {
            while (true) {
                if (entity1.isPassenger()) {
                    entity1 = entity1.getVehicle();
                    if (entity1 != entity) {
                        continue;
                    }

                    return false;
                }

                return true;
            }
        };
    }
}
