package net.minecraft.world.level.block.entity.trialspawner;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public interface PlayerDetector {

    PlayerDetector NO_CREATIVE_PLAYERS = (serverlevel, playerdetector_entityselector, blockpos, d0, flag) -> {
        return playerdetector_entityselector.getPlayers(serverlevel, (player) -> {
            return player.blockPosition().closerThan(blockpos, d0) && !player.isCreative() && !player.isSpectator();
        }).stream().filter((player) -> {
            return !flag || inLineOfSight(serverlevel, blockpos.getCenter(), player.getEyePosition());
        }).map(Entity::getUUID).toList();
    };
    PlayerDetector INCLUDING_CREATIVE_PLAYERS = (serverlevel, playerdetector_entityselector, blockpos, d0, flag) -> {
        return playerdetector_entityselector.getPlayers(serverlevel, (player) -> {
            return player.blockPosition().closerThan(blockpos, d0) && !player.isSpectator();
        }).stream().filter((player) -> {
            return !flag || inLineOfSight(serverlevel, blockpos.getCenter(), player.getEyePosition());
        }).map(Entity::getUUID).toList();
    };
    PlayerDetector SHEEP = (serverlevel, playerdetector_entityselector, blockpos, d0, flag) -> {
        AABB aabb = (new AABB(blockpos)).inflate(d0);

        return playerdetector_entityselector.getEntities(serverlevel, EntityType.SHEEP, aabb, LivingEntity::isAlive).stream().filter((sheep) -> {
            return !flag || inLineOfSight(serverlevel, blockpos.getCenter(), sheep.getEyePosition());
        }).map(Entity::getUUID).toList();
    };

    List<UUID> detect(ServerLevel level, PlayerDetector.EntitySelector selector, BlockPos spawnerPos, double requiredPlayerRange, boolean requireLineOfSight);

    private static boolean inLineOfSight(Level level, Vec3 origin, Vec3 dest) {
        BlockHitResult blockhitresult = level.clip(new ClipContext(dest, origin, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));

        return blockhitresult.getBlockPos().equals(BlockPos.containing(origin)) || blockhitresult.getType() == HitResult.Type.MISS;
    }

    public interface EntitySelector {

        PlayerDetector.EntitySelector SELECT_FROM_LEVEL = new PlayerDetector.EntitySelector() {
            @Override
            public List<ServerPlayer> getPlayers(ServerLevel level, Predicate<? super Player> selector) {
                return level.getPlayers(selector);
            }

            @Override
            public <T extends Entity> List<T> getEntities(ServerLevel level, EntityTypeTest<Entity, T> type, AABB aabb, Predicate<? super T> selector) {
                return level.getEntities(type, aabb, selector);
            }
        };

        List<? extends Player> getPlayers(ServerLevel level, Predicate<? super Player> selector);

        <T extends Entity> List<T> getEntities(ServerLevel level, EntityTypeTest<Entity, T> type, AABB bb, Predicate<? super T> selector);

        static PlayerDetector.EntitySelector onlySelectPlayer(Player player) {
            return onlySelectPlayers(List.of(player));
        }

        static PlayerDetector.EntitySelector onlySelectPlayers(final List<Player> players) {
            return new PlayerDetector.EntitySelector() {
                @Override
                public List<Player> getPlayers(ServerLevel level, Predicate<? super Player> selector) {
                    return players.stream().filter(selector).toList();
                }

                @Override
                public <T extends Entity> List<T> getEntities(ServerLevel level, EntityTypeTest<Entity, T> type, AABB bb, Predicate<? super T> selector) {
                    Stream stream = players.stream();

                    Objects.requireNonNull(type);
                    return stream.map(type::tryCast).filter(Objects::nonNull).filter(selector).toList();
                }
            };
        }
    }
}
