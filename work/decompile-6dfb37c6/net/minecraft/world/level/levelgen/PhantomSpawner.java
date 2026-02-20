package net.minecraft.world.level.levelgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.FluidState;

public class PhantomSpawner implements CustomSpawner {

    private int nextTick;

    public PhantomSpawner() {}

    @Override
    public void tick(ServerLevel level, boolean spawnEnemies) {
        if (spawnEnemies) {
            if ((Boolean) level.getGameRules().get(GameRules.SPAWN_PHANTOMS)) {
                RandomSource randomsource = level.random;

                --this.nextTick;
                if (this.nextTick <= 0) {
                    this.nextTick += (60 + randomsource.nextInt(60)) * 20;
                    if (level.getSkyDarken() >= 5 || !level.dimensionType().hasSkyLight()) {
                        for (ServerPlayer serverplayer : level.players()) {
                            if (!serverplayer.isSpectator()) {
                                BlockPos blockpos = serverplayer.blockPosition();

                                if (!level.dimensionType().hasSkyLight() || blockpos.getY() >= level.getSeaLevel() && level.canSeeSky(blockpos)) {
                                    DifficultyInstance difficultyinstance = level.getCurrentDifficultyAt(blockpos);

                                    if (difficultyinstance.isHarderThan(randomsource.nextFloat() * 3.0F)) {
                                        ServerStatsCounter serverstatscounter = serverplayer.getStats();
                                        int i = Mth.clamp(serverstatscounter.getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)), 1, Integer.MAX_VALUE);
                                        int j = 24000;

                                        if (randomsource.nextInt(i) >= 72000) {
                                            BlockPos blockpos1 = blockpos.above(20 + randomsource.nextInt(15)).east(-10 + randomsource.nextInt(21)).south(-10 + randomsource.nextInt(21));
                                            BlockState blockstate = level.getBlockState(blockpos1);
                                            FluidState fluidstate = level.getFluidState(blockpos1);

                                            if (NaturalSpawner.isValidEmptySpawnBlock(level, blockpos1, blockstate, fluidstate, EntityType.PHANTOM)) {
                                                SpawnGroupData spawngroupdata = null;
                                                int k = 1 + randomsource.nextInt(difficultyinstance.getDifficulty().getId() + 1);

                                                for (int l = 0; l < k; ++l) {
                                                    Phantom phantom = EntityType.PHANTOM.create(level, EntitySpawnReason.NATURAL);

                                                    if (phantom != null) {
                                                        phantom.snapTo(blockpos1, 0.0F, 0.0F);
                                                        spawngroupdata = phantom.finalizeSpawn(level, difficultyinstance, EntitySpawnReason.NATURAL, spawngroupdata);
                                                        level.addFreshEntityWithPassengers(phantom);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                    }
                }
            }
        }
    }
}
