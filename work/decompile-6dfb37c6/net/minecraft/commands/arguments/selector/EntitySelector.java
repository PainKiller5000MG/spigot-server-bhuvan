package net.minecraft.commands.arguments.selector;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class EntitySelector {

    public static final int INFINITE = Integer.MAX_VALUE;
    public static final BiConsumer<Vec3, List<? extends Entity>> ORDER_ARBITRARY = (vec3, list) -> {
    };
    private static final EntityTypeTest<Entity, ?> ANY_TYPE = new EntityTypeTest<Entity, Entity>() {
        public Entity tryCast(Entity entity) {
            return entity;
        }

        @Override
        public Class<? extends Entity> getBaseClass() {
            return Entity.class;
        }
    };
    private final int maxResults;
    private final boolean includesEntities;
    private final boolean worldLimited;
    private final List<Predicate<Entity>> contextFreePredicates;
    private final MinMaxBounds.@Nullable Doubles range;
    private final Function<Vec3, Vec3> position;
    private final @Nullable AABB aabb;
    private final BiConsumer<Vec3, List<? extends Entity>> order;
    private final boolean currentEntity;
    private final @Nullable String playerName;
    private final @Nullable UUID entityUUID;
    private final EntityTypeTest<Entity, ?> type;
    private final boolean usesSelector;

    public EntitySelector(int maxResults, boolean includesEntities, boolean worldLimited, List<Predicate<Entity>> contextFreePredicates, MinMaxBounds.@Nullable Doubles range, Function<Vec3, Vec3> position, @Nullable AABB aabb, BiConsumer<Vec3, List<? extends Entity>> order, boolean currentEntity, @Nullable String playerName, @Nullable UUID entityUUID, @Nullable EntityType<?> type, boolean usesSelector) {
        this.maxResults = maxResults;
        this.includesEntities = includesEntities;
        this.worldLimited = worldLimited;
        this.contextFreePredicates = contextFreePredicates;
        this.range = range;
        this.position = position;
        this.aabb = aabb;
        this.order = order;
        this.currentEntity = currentEntity;
        this.playerName = playerName;
        this.entityUUID = entityUUID;
        this.type = (EntityTypeTest<Entity, ?>) (type == null ? EntitySelector.ANY_TYPE : type);
        this.usesSelector = usesSelector;
    }

    public int getMaxResults() {
        return this.maxResults;
    }

    public boolean includesEntities() {
        return this.includesEntities;
    }

    public boolean isSelfSelector() {
        return this.currentEntity;
    }

    public boolean isWorldLimited() {
        return this.worldLimited;
    }

    public boolean usesSelector() {
        return this.usesSelector;
    }

    private void checkPermissions(CommandSourceStack sender) throws CommandSyntaxException {
        if (this.usesSelector && !sender.permissions().hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS)) {
            throw EntityArgument.ERROR_SELECTORS_NOT_ALLOWED.create();
        }
    }

    public Entity findSingleEntity(CommandSourceStack sender) throws CommandSyntaxException {
        this.checkPermissions(sender);
        List<? extends Entity> list = this.findEntities(sender);

        if (list.isEmpty()) {
            throw EntityArgument.NO_ENTITIES_FOUND.create();
        } else if (list.size() > 1) {
            throw EntityArgument.ERROR_NOT_SINGLE_ENTITY.create();
        } else {
            return (Entity) list.get(0);
        }
    }

    public List<? extends Entity> findEntities(CommandSourceStack sender) throws CommandSyntaxException {
        this.checkPermissions(sender);
        if (!this.includesEntities) {
            return this.findPlayers(sender);
        } else if (this.playerName != null) {
            ServerPlayer serverplayer = sender.getServer().getPlayerList().getPlayerByName(this.playerName);

            return serverplayer == null ? List.of() : List.of(serverplayer);
        } else if (this.entityUUID != null) {
            for (ServerLevel serverlevel : sender.getServer().getAllLevels()) {
                Entity entity = serverlevel.getEntity(this.entityUUID);

                if (entity != null) {
                    if (entity.getType().isEnabled(sender.enabledFeatures())) {
                        return List.of(entity);
                    }
                    break;
                }
            }

            return List.of();
        } else {
            Vec3 vec3 = (Vec3) this.position.apply(sender.getPosition());
            AABB aabb = this.getAbsoluteAabb(vec3);

            if (this.currentEntity) {
                Predicate<Entity> predicate = this.getPredicate(vec3, aabb, (FeatureFlagSet) null);

                return sender.getEntity() != null && predicate.test(sender.getEntity()) ? List.of(sender.getEntity()) : List.of();
            } else {
                Predicate<Entity> predicate1 = this.getPredicate(vec3, aabb, sender.enabledFeatures());
                List<Entity> list = new ObjectArrayList();

                if (this.isWorldLimited()) {
                    this.addEntities(list, sender.getLevel(), aabb, predicate1);
                } else {
                    for (ServerLevel serverlevel1 : sender.getServer().getAllLevels()) {
                        this.addEntities(list, serverlevel1, aabb, predicate1);
                    }
                }

                return this.<Entity>sortAndLimit(vec3, list);
            }
        }
    }

    private void addEntities(List<Entity> result, ServerLevel level, @Nullable AABB absoluteAABB, Predicate<Entity> predicate) {
        int i = this.getResultLimit();

        if (result.size() < i) {
            if (absoluteAABB != null) {
                level.getEntities(this.type, absoluteAABB, predicate, result, i);
            } else {
                level.getEntities(this.type, predicate, result, i);
            }

        }
    }

    private int getResultLimit() {
        return this.order == EntitySelector.ORDER_ARBITRARY ? this.maxResults : Integer.MAX_VALUE;
    }

    public ServerPlayer findSinglePlayer(CommandSourceStack sender) throws CommandSyntaxException {
        this.checkPermissions(sender);
        List<ServerPlayer> list = this.findPlayers(sender);

        if (list.size() != 1) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        } else {
            return (ServerPlayer) list.get(0);
        }
    }

    public List<ServerPlayer> findPlayers(CommandSourceStack sender) throws CommandSyntaxException {
        this.checkPermissions(sender);
        if (this.playerName != null) {
            ServerPlayer serverplayer = sender.getServer().getPlayerList().getPlayerByName(this.playerName);

            return serverplayer == null ? List.of() : List.of(serverplayer);
        } else if (this.entityUUID != null) {
            ServerPlayer serverplayer1 = sender.getServer().getPlayerList().getPlayer(this.entityUUID);

            return serverplayer1 == null ? List.of() : List.of(serverplayer1);
        } else {
            Vec3 vec3 = (Vec3) this.position.apply(sender.getPosition());
            AABB aabb = this.getAbsoluteAabb(vec3);
            Predicate<Entity> predicate = this.getPredicate(vec3, aabb, (FeatureFlagSet) null);

            if (this.currentEntity) {
                Entity entity = sender.getEntity();

                if (entity instanceof ServerPlayer) {
                    ServerPlayer serverplayer2 = (ServerPlayer) entity;

                    if (predicate.test(serverplayer2)) {
                        return List.of(serverplayer2);
                    }
                }

                return List.of();
            } else {
                int i = this.getResultLimit();
                List<ServerPlayer> list;

                if (this.isWorldLimited()) {
                    list = sender.getLevel().getPlayers(predicate, i);
                } else {
                    list = new ObjectArrayList();

                    for (ServerPlayer serverplayer3 : sender.getServer().getPlayerList().getPlayers()) {
                        if (predicate.test(serverplayer3)) {
                            list.add(serverplayer3);
                            if (list.size() >= i) {
                                return list;
                            }
                        }
                    }
                }

                return this.<ServerPlayer>sortAndLimit(vec3, list);
            }
        }
    }

    private @Nullable AABB getAbsoluteAabb(Vec3 pos) {
        return this.aabb != null ? this.aabb.move(pos) : null;
    }

    private Predicate<Entity> getPredicate(Vec3 pos, @Nullable AABB absoluteAabb, @Nullable FeatureFlagSet enabledFeatures) {
        boolean flag = enabledFeatures != null;
        boolean flag1 = absoluteAabb != null;
        boolean flag2 = this.range != null;
        int i = (flag ? 1 : 0) + (flag1 ? 1 : 0) + (flag2 ? 1 : 0);
        List<Predicate<Entity>> list;

        if (i == 0) {
            list = this.contextFreePredicates;
        } else {
            List<Predicate<Entity>> list1 = new ObjectArrayList(this.contextFreePredicates.size() + i);

            list1.addAll(this.contextFreePredicates);
            if (flag) {
                list1.add((Predicate) (entity) -> {
                    return entity.getType().isEnabled(enabledFeatures);
                });
            }

            if (flag1) {
                list1.add((Predicate) (entity) -> {
                    return absoluteAabb.intersects(entity.getBoundingBox());
                });
            }

            if (flag2) {
                list1.add((Predicate) (entity) -> {
                    return this.range.matchesSqr(entity.distanceToSqr(pos));
                });
            }

            list = list1;
        }

        return Util.allOf(list);
    }

    private <T extends Entity> List<T> sortAndLimit(Vec3 pos, List<T> result) {
        if (result.size() > 1) {
            this.order.accept(pos, result);
        }

        return result.subList(0, Math.min(this.maxResults, result.size()));
    }

    public static Component joinNames(List<? extends Entity> entities) {
        return ComponentUtils.formatList(entities, Entity::getDisplayName);
    }
}
