package net.minecraft.world.level.dimension.end;

import com.google.common.collect.ImmutableList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.SpikeConfiguration;

public enum DragonRespawnAnimation {

    START {
        @Override
        public void tick(ServerLevel level, EndDragonFight fight, List<EndCrystal> crystals, int time, BlockPos portal) {
            BlockPos blockpos1 = new BlockPos(0, 128, 0);

            for (EndCrystal endcrystal : crystals) {
                endcrystal.setBeamTarget(blockpos1);
            }

            fight.setRespawnStage(null.PREPARING_TO_SUMMON_PILLARS);
        }
    },
    PREPARING_TO_SUMMON_PILLARS {
        @Override
        public void tick(ServerLevel level, EndDragonFight fight, List<EndCrystal> crystals, int time, BlockPos portal) {
            if (time < 100) {
                if (time == 0 || time == 50 || time == 51 || time == 52 || time >= 95) {
                    level.levelEvent(3001, new BlockPos(0, 128, 0), 0);
                }
            } else {
                fight.setRespawnStage(null.SUMMONING_PILLARS);
            }

        }
    },
    SUMMONING_PILLARS {
        @Override
        public void tick(ServerLevel level, EndDragonFight fight, List<EndCrystal> crystals, int time, BlockPos portal) {
            int j = 40;
            boolean flag = time % 40 == 0;
            boolean flag1 = time % 40 == 39;

            if (flag || flag1) {
                List<SpikeFeature.EndSpike> list1 = SpikeFeature.getSpikesForLevel(level);
                int k = time / 40;

                if (k < list1.size()) {
                    SpikeFeature.EndSpike spikefeature_endspike = (SpikeFeature.EndSpike) list1.get(k);

                    if (flag) {
                        for (EndCrystal endcrystal : crystals) {
                            endcrystal.setBeamTarget(new BlockPos(spikefeature_endspike.getCenterX(), spikefeature_endspike.getHeight() + 1, spikefeature_endspike.getCenterZ()));
                        }
                    } else {
                        int l = 10;

                        for (BlockPos blockpos1 : BlockPos.betweenClosed(new BlockPos(spikefeature_endspike.getCenterX() - 10, spikefeature_endspike.getHeight() - 10, spikefeature_endspike.getCenterZ() - 10), new BlockPos(spikefeature_endspike.getCenterX() + 10, spikefeature_endspike.getHeight() + 10, spikefeature_endspike.getCenterZ() + 10))) {
                            level.removeBlock(blockpos1, false);
                        }

                        level.explode((Entity) null, (double) ((float) spikefeature_endspike.getCenterX() + 0.5F), (double) spikefeature_endspike.getHeight(), (double) ((float) spikefeature_endspike.getCenterZ() + 0.5F), 5.0F, Level.ExplosionInteraction.BLOCK);
                        SpikeConfiguration spikeconfiguration = new SpikeConfiguration(true, ImmutableList.of(spikefeature_endspike), new BlockPos(0, 128, 0));

                        Feature.END_SPIKE.place(spikeconfiguration, level, level.getChunkSource().getGenerator(), RandomSource.create(), new BlockPos(spikefeature_endspike.getCenterX(), 45, spikefeature_endspike.getCenterZ()));
                    }
                } else if (flag) {
                    fight.setRespawnStage(null.SUMMONING_DRAGON);
                }
            }

        }
    },
    SUMMONING_DRAGON {
        @Override
        public void tick(ServerLevel level, EndDragonFight fight, List<EndCrystal> crystals, int time, BlockPos portal) {
            if (time >= 100) {
                fight.setRespawnStage(null.END);
                fight.resetSpikeCrystals();

                for (EndCrystal endcrystal : crystals) {
                    endcrystal.setBeamTarget((BlockPos) null);
                    level.explode(endcrystal, endcrystal.getX(), endcrystal.getY(), endcrystal.getZ(), 6.0F, Level.ExplosionInteraction.NONE);
                    endcrystal.discard();
                }
            } else if (time >= 80) {
                level.levelEvent(3001, new BlockPos(0, 128, 0), 0);
            } else if (time == 0) {
                for (EndCrystal endcrystal1 : crystals) {
                    endcrystal1.setBeamTarget(new BlockPos(0, 128, 0));
                }
            } else if (time < 5) {
                level.levelEvent(3001, new BlockPos(0, 128, 0), 0);
            }

        }
    },
    END {
        @Override
        public void tick(ServerLevel level, EndDragonFight fight, List<EndCrystal> crystals, int time, BlockPos portal) {}
    };

    private DragonRespawnAnimation() {}

    public abstract void tick(ServerLevel level, EndDragonFight fight, List<EndCrystal> crystals, int time, BlockPos portal);
}
