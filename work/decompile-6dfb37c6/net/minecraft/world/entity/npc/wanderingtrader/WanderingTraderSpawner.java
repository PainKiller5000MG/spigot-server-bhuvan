package net.minecraft.world.entity.npc.wanderingtrader;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.equine.TraderLlama;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ServerLevelData;
import org.jspecify.annotations.Nullable;

public class WanderingTraderSpawner implements CustomSpawner {

    private static final int DEFAULT_TICK_DELAY = 1200;
    public static final int DEFAULT_SPAWN_DELAY = 24000;
    private static final int MIN_SPAWN_CHANCE = 25;
    private static final int MAX_SPAWN_CHANCE = 75;
    private static final int SPAWN_CHANCE_INCREASE = 25;
    private static final int SPAWN_ONE_IN_X_CHANCE = 10;
    private static final int NUMBER_OF_SPAWN_ATTEMPTS = 10;
    private final RandomSource random = RandomSource.create();
    private final ServerLevelData serverLevelData;
    private int tickDelay;
    private int spawnDelay;
    private int spawnChance;

    public WanderingTraderSpawner(ServerLevelData serverLevelData) {
        this.serverLevelData = serverLevelData;
        this.tickDelay = 1200;
        this.spawnDelay = serverLevelData.getWanderingTraderSpawnDelay();
        this.spawnChance = serverLevelData.getWanderingTraderSpawnChance();
        if (this.spawnDelay == 0 && this.spawnChance == 0) {
            this.spawnDelay = 24000;
            serverLevelData.setWanderingTraderSpawnDelay(this.spawnDelay);
            this.spawnChance = 25;
            serverLevelData.setWanderingTraderSpawnChance(this.spawnChance);
        }

    }

    @Override
    public void tick(ServerLevel level, boolean spawnEnemies) {
        if ((Boolean) level.getGameRules().get(GameRules.SPAWN_WANDERING_TRADERS)) {
            if (--this.tickDelay <= 0) {
                this.tickDelay = 1200;
                this.spawnDelay -= 1200;
                this.serverLevelData.setWanderingTraderSpawnDelay(this.spawnDelay);
                if (this.spawnDelay <= 0) {
                    this.spawnDelay = 24000;
                    int i = this.spawnChance;

                    this.spawnChance = Mth.clamp(this.spawnChance + 25, 25, 75);
                    this.serverLevelData.setWanderingTraderSpawnChance(this.spawnChance);
                    if (this.random.nextInt(100) <= i) {
                        if (this.spawn(level)) {
                            this.spawnChance = 25;
                        }

                    }
                }
            }
        }
    }

    private boolean spawn(ServerLevel level) {
        Player player = level.getRandomPlayer();

        if (player == null) {
            return true;
        } else if (this.random.nextInt(10) != 0) {
            return false;
        } else {
            BlockPos blockpos = player.blockPosition();
            int i = 48;
            PoiManager poimanager = level.getPoiManager();
            Optional<BlockPos> optional = poimanager.find((holder) -> {
                return holder.is(PoiTypes.MEETING);
            }, (blockpos1) -> {
                return true;
            }, blockpos, 48, PoiManager.Occupancy.ANY);
            BlockPos blockpos1 = (BlockPos) optional.orElse(blockpos);
            BlockPos blockpos2 = this.findSpawnPositionNear(level, blockpos1, 48);

            if (blockpos2 != null && this.hasEnoughSpace(level, blockpos2)) {
                if (level.getBiome(blockpos2).is(BiomeTags.WITHOUT_WANDERING_TRADER_SPAWNS)) {
                    return false;
                }

                WanderingTrader wanderingtrader = EntityType.WANDERING_TRADER.spawn(level, blockpos2, EntitySpawnReason.EVENT);

                if (wanderingtrader != null) {
                    for (int j = 0; j < 2; ++j) {
                        this.tryToSpawnLlamaFor(level, wanderingtrader, 4);
                    }

                    this.serverLevelData.setWanderingTraderId(wanderingtrader.getUUID());
                    wanderingtrader.setDespawnDelay(48000);
                    wanderingtrader.setWanderTarget(blockpos1);
                    wanderingtrader.setHomeTo(blockpos1, 16);
                    return true;
                }
            }

            return false;
        }
    }

    private void tryToSpawnLlamaFor(ServerLevel level, WanderingTrader trader, int radius) {
        BlockPos blockpos = this.findSpawnPositionNear(level, trader.blockPosition(), radius);

        if (blockpos != null) {
            TraderLlama traderllama = EntityType.TRADER_LLAMA.spawn(level, blockpos, EntitySpawnReason.EVENT);

            if (traderllama != null) {
                traderllama.setLeashedTo(trader, true);
            }
        }
    }

    private @Nullable BlockPos findSpawnPositionNear(LevelReader level, BlockPos referencePosition, int radius) {
        BlockPos blockpos1 = null;
        SpawnPlacementType spawnplacementtype = SpawnPlacements.getPlacementType(EntityType.WANDERING_TRADER);

        for (int j = 0; j < 10; ++j) {
            int k = referencePosition.getX() + this.random.nextInt(radius * 2) - radius;
            int l = referencePosition.getZ() + this.random.nextInt(radius * 2) - radius;
            int i1 = level.getHeight(Heightmap.Types.WORLD_SURFACE, k, l);
            BlockPos blockpos2 = new BlockPos(k, i1, l);

            if (spawnplacementtype.isSpawnPositionOk(level, blockpos2, EntityType.WANDERING_TRADER)) {
                blockpos1 = blockpos2;
                break;
            }
        }

        return blockpos1;
    }

    private boolean hasEnoughSpace(BlockGetter level, BlockPos spawnPos) {
        for (BlockPos blockpos1 : BlockPos.betweenClosed(spawnPos, spawnPos.offset(1, 2, 1))) {
            if (!level.getBlockState(blockpos1).getCollisionShape(level, blockpos1).isEmpty()) {
                return false;
            }
        }

        return true;
    }
}
