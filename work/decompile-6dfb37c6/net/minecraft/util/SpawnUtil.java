package net.minecraft.util;

import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;

public class SpawnUtil {

    public SpawnUtil() {}

    public static <T extends Mob> Optional<T> trySpawnMob(EntityType<T> entityType, EntitySpawnReason spawnReason, ServerLevel level, BlockPos start, int spawnAttempts, int spawnRangeXZ, int spawnRangeY, SpawnUtil.Strategy strategy, boolean checkCollisions) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = start.mutable();

        for (int l = 0; l < spawnAttempts; ++l) {
            int i1 = Mth.randomBetweenInclusive(level.random, -spawnRangeXZ, spawnRangeXZ);
            int j1 = Mth.randomBetweenInclusive(level.random, -spawnRangeXZ, spawnRangeXZ);

            blockpos_mutableblockpos.setWithOffset(start, i1, spawnRangeY, j1);
            if (level.getWorldBorder().isWithinBounds((BlockPos) blockpos_mutableblockpos) && moveToPossibleSpawnPosition(level, spawnRangeY, blockpos_mutableblockpos, strategy) && (!checkCollisions || level.noCollision(entityType.getSpawnAABB((double) blockpos_mutableblockpos.getX() + 0.5D, (double) blockpos_mutableblockpos.getY(), (double) blockpos_mutableblockpos.getZ() + 0.5D)))) {
                T t0 = entityType.create(level, (Consumer) null, blockpos_mutableblockpos, spawnReason, false, false);

                if (t0 != null) {
                    if (t0.checkSpawnRules(level, spawnReason) && t0.checkSpawnObstruction(level)) {
                        level.addFreshEntityWithPassengers(t0);
                        t0.playAmbientSound();
                        return Optional.of(t0);
                    }

                    t0.discard();
                }
            }
        }

        return Optional.empty();
    }

    private static boolean moveToPossibleSpawnPosition(ServerLevel level, int spawnRangeY, BlockPos.MutableBlockPos searchPos, SpawnUtil.Strategy strategy) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos1 = (new BlockPos.MutableBlockPos()).set(searchPos);
        BlockState blockstate = level.getBlockState(blockpos_mutableblockpos1);

        for (int j = spawnRangeY; j >= -spawnRangeY; --j) {
            searchPos.move(Direction.DOWN);
            blockpos_mutableblockpos1.setWithOffset(searchPos, Direction.UP);
            BlockState blockstate1 = level.getBlockState(searchPos);

            if (strategy.canSpawnOn(level, searchPos, blockstate1, blockpos_mutableblockpos1, blockstate)) {
                searchPos.move(Direction.UP);
                return true;
            }

            blockstate = blockstate1;
        }

        return false;
    }

    public interface Strategy {

        /** @deprecated */
        @Deprecated
        SpawnUtil.Strategy LEGACY_IRON_GOLEM = (serverlevel, blockpos, blockstate, blockpos1, blockstate1) -> {
            return !blockstate.is(Blocks.COBWEB) && !blockstate.is(Blocks.CACTUS) && !blockstate.is(Blocks.GLASS_PANE) && !(blockstate.getBlock() instanceof StainedGlassPaneBlock) && !(blockstate.getBlock() instanceof StainedGlassBlock) && !(blockstate.getBlock() instanceof LeavesBlock) && !blockstate.is(Blocks.CONDUIT) && !blockstate.is(Blocks.ICE) && !blockstate.is(Blocks.TNT) && !blockstate.is(Blocks.GLOWSTONE) && !blockstate.is(Blocks.BEACON) && !blockstate.is(Blocks.SEA_LANTERN) && !blockstate.is(Blocks.FROSTED_ICE) && !blockstate.is(Blocks.TINTED_GLASS) && !blockstate.is(Blocks.GLASS) ? (blockstate1.isAir() || blockstate1.liquid()) && (blockstate.isSolid() || blockstate.is(Blocks.POWDER_SNOW)) : false;
        };
        SpawnUtil.Strategy ON_TOP_OF_COLLIDER = (serverlevel, blockpos, blockstate, blockpos1, blockstate1) -> {
            return blockstate1.getCollisionShape(serverlevel, blockpos1).isEmpty() && Block.isFaceFull(blockstate.getCollisionShape(serverlevel, blockpos), Direction.UP);
        };
        SpawnUtil.Strategy ON_TOP_OF_COLLIDER_NO_LEAVES = (serverlevel, blockpos, blockstate, blockpos1, blockstate1) -> {
            return blockstate1.getCollisionShape(serverlevel, blockpos1).isEmpty() && !blockstate.is(BlockTags.LEAVES) && Block.isFaceFull(blockstate.getCollisionShape(serverlevel, blockpos), Direction.UP);
        };

        boolean canSpawnOn(ServerLevel level, BlockPos pos, BlockState blockState, BlockPos abovePos, BlockState aboveState);
    }
}
