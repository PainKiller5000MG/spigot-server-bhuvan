package net.minecraft.world.entity.ai.village;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class VillageSiege implements CustomSpawner {

    private static final Logger LOGGER = LogUtils.getLogger();
    private boolean hasSetupSiege;
    private VillageSiege.State siegeState;
    private int zombiesToSpawn;
    private int nextSpawnTime;
    private int spawnX;
    private int spawnY;
    private int spawnZ;

    public VillageSiege() {
        this.siegeState = VillageSiege.State.SIEGE_DONE;
    }

    @Override
    public void tick(ServerLevel level, boolean spawnEnemies) {
        if (!level.isBrightOutside() && spawnEnemies) {
            long i = level.getDayTime() % 24000L;

            if (i == 18000L) {
                this.siegeState = level.random.nextInt(10) == 0 ? VillageSiege.State.SIEGE_TONIGHT : VillageSiege.State.SIEGE_DONE;
            }

            if (this.siegeState != VillageSiege.State.SIEGE_DONE) {
                if (!this.hasSetupSiege) {
                    if (!this.tryToSetupSiege(level)) {
                        return;
                    }

                    this.hasSetupSiege = true;
                }

                if (this.nextSpawnTime > 0) {
                    --this.nextSpawnTime;
                } else {
                    this.nextSpawnTime = 2;
                    if (this.zombiesToSpawn > 0) {
                        this.trySpawn(level);
                        --this.zombiesToSpawn;
                    } else {
                        this.siegeState = VillageSiege.State.SIEGE_DONE;
                    }

                }
            }
        } else {
            this.siegeState = VillageSiege.State.SIEGE_DONE;
            this.hasSetupSiege = false;
        }
    }

    private boolean tryToSetupSiege(ServerLevel level) {
        for (Player player : level.players()) {
            if (!player.isSpectator()) {
                BlockPos blockpos = player.blockPosition();

                if (level.isVillage(blockpos) && !level.getBiome(blockpos).is(BiomeTags.WITHOUT_ZOMBIE_SIEGES)) {
                    for (int i = 0; i < 10; ++i) {
                        float f = level.random.nextFloat() * ((float) Math.PI * 2F);

                        this.spawnX = blockpos.getX() + Mth.floor(Mth.cos((double) f) * 32.0F);
                        this.spawnY = blockpos.getY();
                        this.spawnZ = blockpos.getZ() + Mth.floor(Mth.sin((double) f) * 32.0F);
                        if (this.findRandomSpawnPos(level, new BlockPos(this.spawnX, this.spawnY, this.spawnZ)) != null) {
                            this.nextSpawnTime = 0;
                            this.zombiesToSpawn = 20;
                            break;
                        }
                    }

                    return true;
                }
            }
        }

        return false;
    }

    private void trySpawn(ServerLevel level) {
        Vec3 vec3 = this.findRandomSpawnPos(level, new BlockPos(this.spawnX, this.spawnY, this.spawnZ));

        if (vec3 != null) {
            Zombie zombie;

            try {
                zombie = new Zombie(level);
                zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(zombie.blockPosition()), EntitySpawnReason.EVENT, (SpawnGroupData) null);
            } catch (Exception exception) {
                VillageSiege.LOGGER.warn("Failed to create zombie for village siege at {}", vec3, exception);
                return;
            }

            zombie.snapTo(vec3.x, vec3.y, vec3.z, level.random.nextFloat() * 360.0F, 0.0F);
            level.addFreshEntityWithPassengers(zombie);
        }
    }

    private @Nullable Vec3 findRandomSpawnPos(ServerLevel level, BlockPos pos) {
        for (int i = 0; i < 10; ++i) {
            int j = pos.getX() + level.random.nextInt(16) - 8;
            int k = pos.getZ() + level.random.nextInt(16) - 8;
            int l = level.getHeight(Heightmap.Types.WORLD_SURFACE, j, k);
            BlockPos blockpos1 = new BlockPos(j, l, k);

            if (level.isVillage(blockpos1) && Monster.checkMonsterSpawnRules(EntityType.ZOMBIE, level, EntitySpawnReason.EVENT, blockpos1, level.random)) {
                return Vec3.atBottomCenterOf(blockpos1);
            }
        }

        return null;
    }

    private static enum State {

        SIEGE_CAN_ACTIVATE, SIEGE_TONIGHT, SIEGE_DONE;

        private State() {}
    }
}
