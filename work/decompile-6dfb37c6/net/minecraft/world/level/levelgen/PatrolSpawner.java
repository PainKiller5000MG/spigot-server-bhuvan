package net.minecraft.world.level.levelgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

public class PatrolSpawner implements CustomSpawner {

    private int nextTick;

    public PatrolSpawner() {}

    @Override
    public void tick(ServerLevel level, boolean spawnEnemies) {
        if (spawnEnemies) {
            if ((Boolean) level.getGameRules().get(GameRules.SPAWN_PATROLS)) {
                RandomSource randomsource = level.random;

                --this.nextTick;
                if (this.nextTick <= 0) {
                    this.nextTick += 12000 + randomsource.nextInt(1200);
                    if (level.isBrightOutside()) {
                        if (randomsource.nextInt(5) == 0) {
                            int i = level.players().size();

                            if (i >= 1) {
                                Player player = (Player) level.players().get(randomsource.nextInt(i));

                                if (!player.isSpectator()) {
                                    if (!level.isCloseToVillage(player.blockPosition(), 2)) {
                                        int j = (24 + randomsource.nextInt(24)) * (randomsource.nextBoolean() ? -1 : 1);
                                        int k = (24 + randomsource.nextInt(24)) * (randomsource.nextBoolean() ? -1 : 1);
                                        BlockPos.MutableBlockPos blockpos_mutableblockpos = player.blockPosition().mutable().move(j, 0, k);
                                        int l = 10;

                                        if (level.hasChunksAt(blockpos_mutableblockpos.getX() - 10, blockpos_mutableblockpos.getZ() - 10, blockpos_mutableblockpos.getX() + 10, blockpos_mutableblockpos.getZ() + 10)) {
                                            if ((Boolean) level.environmentAttributes().getValue(EnvironmentAttributes.CAN_PILLAGER_PATROL_SPAWN, (BlockPos) blockpos_mutableblockpos)) {
                                                int i1 = (int) Math.ceil((double) level.getCurrentDifficultyAt(blockpos_mutableblockpos).getEffectiveDifficulty()) + 1;

                                                for (int j1 = 0; j1 < i1; ++j1) {
                                                    blockpos_mutableblockpos.setY(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockpos_mutableblockpos).getY());
                                                    if (j1 == 0) {
                                                        if (!this.spawnPatrolMember(level, blockpos_mutableblockpos, randomsource, true)) {
                                                            break;
                                                        }
                                                    } else {
                                                        this.spawnPatrolMember(level, blockpos_mutableblockpos, randomsource, false);
                                                    }

                                                    blockpos_mutableblockpos.setX(blockpos_mutableblockpos.getX() + randomsource.nextInt(5) - randomsource.nextInt(5));
                                                    blockpos_mutableblockpos.setZ(blockpos_mutableblockpos.getZ() + randomsource.nextInt(5) - randomsource.nextInt(5));
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

    private boolean spawnPatrolMember(ServerLevel level, BlockPos pos, RandomSource random, boolean isLeader) {
        BlockState blockstate = level.getBlockState(pos);

        if (!NaturalSpawner.isValidEmptySpawnBlock(level, pos, blockstate, blockstate.getFluidState(), EntityType.PILLAGER)) {
            return false;
        } else if (!PatrollingMonster.checkPatrollingMonsterSpawnRules(EntityType.PILLAGER, level, EntitySpawnReason.PATROL, pos, random)) {
            return false;
        } else {
            PatrollingMonster patrollingmonster = EntityType.PILLAGER.create(level, EntitySpawnReason.PATROL);

            if (patrollingmonster != null) {
                if (isLeader) {
                    patrollingmonster.setPatrolLeader(true);
                    patrollingmonster.findPatrolTarget();
                }

                patrollingmonster.setPos((double) pos.getX(), (double) pos.getY(), (double) pos.getZ());
                patrollingmonster.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.PATROL, (SpawnGroupData) null);
                level.addFreshEntityWithPassengers(patrollingmonster);
                return true;
            } else {
                return false;
            }
        }
    }
}
