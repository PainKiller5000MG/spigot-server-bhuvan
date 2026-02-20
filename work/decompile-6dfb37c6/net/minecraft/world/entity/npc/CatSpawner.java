package net.minecraft.world.entity.npc;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.phys.AABB;

public class CatSpawner implements CustomSpawner {

    private static final int TICK_DELAY = 1200;
    private int nextTick;

    public CatSpawner() {}

    @Override
    public void tick(ServerLevel level, boolean spawnEnemies) {
        --this.nextTick;
        if (this.nextTick <= 0) {
            this.nextTick = 1200;
            Player player = level.getRandomPlayer();

            if (player != null) {
                RandomSource randomsource = level.random;
                int i = (8 + randomsource.nextInt(24)) * (randomsource.nextBoolean() ? -1 : 1);
                int j = (8 + randomsource.nextInt(24)) * (randomsource.nextBoolean() ? -1 : 1);
                BlockPos blockpos = player.blockPosition().offset(i, 0, j);
                int k = 10;

                if (level.hasChunksAt(blockpos.getX() - 10, blockpos.getZ() - 10, blockpos.getX() + 10, blockpos.getZ() + 10)) {
                    if (SpawnPlacements.isSpawnPositionOk(EntityType.CAT, level, blockpos)) {
                        if (level.isCloseToVillage(blockpos, 2)) {
                            this.spawnInVillage(level, blockpos);
                        } else if (level.structureManager().getStructureWithPieceAt(blockpos, StructureTags.CATS_SPAWN_IN).isValid()) {
                            this.spawnInHut(level, blockpos);
                        }
                    }

                }
            }
        }
    }

    private void spawnInVillage(ServerLevel serverLevel, BlockPos spawnPos) {
        int i = 48;

        if (serverLevel.getPoiManager().getCountInRange((holder) -> {
            return holder.is(PoiTypes.HOME);
        }, spawnPos, 48, PoiManager.Occupancy.IS_OCCUPIED) > 4L) {
            List<Cat> list = serverLevel.<Cat>getEntitiesOfClass(Cat.class, (new AABB(spawnPos)).inflate(48.0D, 8.0D, 48.0D));

            if (list.size() < 5) {
                this.spawnCat(spawnPos, serverLevel, false);
            }
        }

    }

    private void spawnInHut(ServerLevel level, BlockPos spawnPos) {
        int i = 16;
        List<Cat> list = level.<Cat>getEntitiesOfClass(Cat.class, (new AABB(spawnPos)).inflate(16.0D, 8.0D, 16.0D));

        if (list.isEmpty()) {
            this.spawnCat(spawnPos, level, true);
        }

    }

    private void spawnCat(BlockPos spawnPos, ServerLevel level, boolean makePersistent) {
        Cat cat = EntityType.CAT.create(level, EntitySpawnReason.NATURAL);

        if (cat != null) {
            cat.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), EntitySpawnReason.NATURAL, (SpawnGroupData) null);
            if (makePersistent) {
                cat.setPersistenceRequired();
            }

            cat.snapTo(spawnPos, 0.0F, 0.0F);
            level.addFreshEntityWithPassengers(cat);
        }
    }
}
