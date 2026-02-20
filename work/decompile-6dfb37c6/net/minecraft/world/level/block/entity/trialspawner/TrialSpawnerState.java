package net.minecraft.world.level.block.entity.trialspawner;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OminousItemSpawner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

public enum TrialSpawnerState implements StringRepresentable {

    INACTIVE("inactive", 0, TrialSpawnerState.ParticleEmission.NONE, -1.0D, false), WAITING_FOR_PLAYERS("waiting_for_players", 4, TrialSpawnerState.ParticleEmission.SMALL_FLAMES, 200.0D, true), ACTIVE("active", 8, TrialSpawnerState.ParticleEmission.FLAMES_AND_SMOKE, 1000.0D, true), WAITING_FOR_REWARD_EJECTION("waiting_for_reward_ejection", 8, TrialSpawnerState.ParticleEmission.SMALL_FLAMES, -1.0D, false), EJECTING_REWARD("ejecting_reward", 8, TrialSpawnerState.ParticleEmission.SMALL_FLAMES, -1.0D, false), COOLDOWN("cooldown", 0, TrialSpawnerState.ParticleEmission.SMOKE_INSIDE_AND_TOP_FACE, -1.0D, false);

    private static final float DELAY_BEFORE_EJECT_AFTER_KILLING_LAST_MOB = 40.0F;
    private static final int TIME_BETWEEN_EACH_EJECTION = Mth.floor(30.0F);
    private final String name;
    private final int lightLevel;
    private final double spinningMobSpeed;
    private final TrialSpawnerState.ParticleEmission particleEmission;
    private final boolean isCapableOfSpawning;

    private TrialSpawnerState(String name, int lightLevel, TrialSpawnerState.ParticleEmission particleEmission, double spinningMobSpeed, boolean isCapableOfSpawning) {
        this.name = name;
        this.lightLevel = lightLevel;
        this.particleEmission = particleEmission;
        this.spinningMobSpeed = spinningMobSpeed;
        this.isCapableOfSpawning = isCapableOfSpawning;
    }

    TrialSpawnerState tickAndGetNext(BlockPos spawnerPos, TrialSpawner trialSpawner, ServerLevel serverLevel) {
        TrialSpawnerStateData trialspawnerstatedata = trialSpawner.getStateData();
        TrialSpawnerConfig trialspawnerconfig = trialSpawner.activeConfig();
        TrialSpawnerState trialspawnerstate;

        switch (this.ordinal()) {
            case 0:
                trialspawnerstate = trialspawnerstatedata.getOrCreateDisplayEntity(trialSpawner, serverLevel, TrialSpawnerState.WAITING_FOR_PLAYERS) == null ? this : TrialSpawnerState.WAITING_FOR_PLAYERS;
                break;
            case 1:
                if (!trialSpawner.canSpawnInLevel(serverLevel)) {
                    trialspawnerstatedata.resetStatistics();
                    trialspawnerstate = this;
                } else if (!trialspawnerstatedata.hasMobToSpawn(trialSpawner, serverLevel.random)) {
                    trialspawnerstate = TrialSpawnerState.INACTIVE;
                } else {
                    trialspawnerstatedata.tryDetectPlayers(serverLevel, spawnerPos, trialSpawner);
                    trialspawnerstate = trialspawnerstatedata.detectedPlayers.isEmpty() ? this : TrialSpawnerState.ACTIVE;
                }
                break;
            case 2:
                if (!trialSpawner.canSpawnInLevel(serverLevel)) {
                    trialspawnerstatedata.resetStatistics();
                    trialspawnerstate = TrialSpawnerState.WAITING_FOR_PLAYERS;
                } else if (!trialspawnerstatedata.hasMobToSpawn(trialSpawner, serverLevel.random)) {
                    trialspawnerstate = TrialSpawnerState.INACTIVE;
                } else {
                    int i = trialspawnerstatedata.countAdditionalPlayers(spawnerPos);

                    trialspawnerstatedata.tryDetectPlayers(serverLevel, spawnerPos, trialSpawner);
                    if (trialSpawner.isOminous()) {
                        this.spawnOminousOminousItemSpawner(serverLevel, spawnerPos, trialSpawner);
                    }

                    if (trialspawnerstatedata.hasFinishedSpawningAllMobs(trialspawnerconfig, i)) {
                        if (trialspawnerstatedata.haveAllCurrentMobsDied()) {
                            trialspawnerstatedata.cooldownEndsAt = serverLevel.getGameTime() + (long) trialSpawner.getTargetCooldownLength();
                            trialspawnerstatedata.totalMobsSpawned = 0;
                            trialspawnerstatedata.nextMobSpawnsAt = 0L;
                            trialspawnerstate = TrialSpawnerState.WAITING_FOR_REWARD_EJECTION;
                            break;
                        }
                    } else if (trialspawnerstatedata.isReadyToSpawnNextMob(serverLevel, trialspawnerconfig, i)) {
                        trialSpawner.spawnMob(serverLevel, spawnerPos).ifPresent((uuid) -> {
                            trialspawnerstatedata.currentMobs.add(uuid);
                            ++trialspawnerstatedata.totalMobsSpawned;
                            trialspawnerstatedata.nextMobSpawnsAt = serverLevel.getGameTime() + (long) trialspawnerconfig.ticksBetweenSpawn();
                            trialspawnerconfig.spawnPotentialsDefinition().getRandom(serverLevel.getRandom()).ifPresent((spawndata) -> {
                                trialspawnerstatedata.nextSpawnData = Optional.of(spawndata);
                                trialSpawner.markUpdated();
                            });
                        });
                    }

                    trialspawnerstate = this;
                }
                break;
            case 3:
                if (trialspawnerstatedata.isReadyToOpenShutter(serverLevel, 40.0F, trialSpawner.getTargetCooldownLength())) {
                    serverLevel.playSound((Entity) null, spawnerPos, SoundEvents.TRIAL_SPAWNER_OPEN_SHUTTER, SoundSource.BLOCKS);
                    trialspawnerstate = TrialSpawnerState.EJECTING_REWARD;
                } else {
                    trialspawnerstate = this;
                }
                break;
            case 4:
                if (!trialspawnerstatedata.isReadyToEjectItems(serverLevel, (float) TrialSpawnerState.TIME_BETWEEN_EACH_EJECTION, trialSpawner.getTargetCooldownLength())) {
                    trialspawnerstate = this;
                } else if (trialspawnerstatedata.detectedPlayers.isEmpty()) {
                    serverLevel.playSound((Entity) null, spawnerPos, SoundEvents.TRIAL_SPAWNER_CLOSE_SHUTTER, SoundSource.BLOCKS);
                    trialspawnerstatedata.ejectingLootTable = Optional.empty();
                    trialspawnerstate = TrialSpawnerState.COOLDOWN;
                } else {
                    if (trialspawnerstatedata.ejectingLootTable.isEmpty()) {
                        trialspawnerstatedata.ejectingLootTable = trialspawnerconfig.lootTablesToEject().getRandom(serverLevel.getRandom());
                    }

                    trialspawnerstatedata.ejectingLootTable.ifPresent((resourcekey) -> {
                        trialSpawner.ejectReward(serverLevel, spawnerPos, resourcekey);
                    });
                    trialspawnerstatedata.detectedPlayers.remove(trialspawnerstatedata.detectedPlayers.iterator().next());
                    trialspawnerstate = this;
                }
                break;
            case 5:
                trialspawnerstatedata.tryDetectPlayers(serverLevel, spawnerPos, trialSpawner);
                if (!trialspawnerstatedata.detectedPlayers.isEmpty()) {
                    trialspawnerstatedata.totalMobsSpawned = 0;
                    trialspawnerstatedata.nextMobSpawnsAt = 0L;
                    trialspawnerstate = TrialSpawnerState.ACTIVE;
                } else if (trialspawnerstatedata.isCooldownFinished(serverLevel)) {
                    trialSpawner.removeOminous(serverLevel, spawnerPos);
                    trialspawnerstatedata.reset();
                    trialspawnerstate = TrialSpawnerState.WAITING_FOR_PLAYERS;
                } else {
                    trialspawnerstate = this;
                }
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return trialspawnerstate;
    }

    private void spawnOminousOminousItemSpawner(ServerLevel level, BlockPos trialSpawnerPos, TrialSpawner trialSpawner) {
        TrialSpawnerStateData trialspawnerstatedata = trialSpawner.getStateData();
        TrialSpawnerConfig trialspawnerconfig = trialSpawner.activeConfig();
        ItemStack itemstack = (ItemStack) trialspawnerstatedata.getDispensingItems(level, trialspawnerconfig, trialSpawnerPos).getRandom(level.random).orElse(ItemStack.EMPTY);

        if (!itemstack.isEmpty()) {
            if (this.timeToSpawnItemSpawner(level, trialspawnerstatedata)) {
                calculatePositionToSpawnSpawner(level, trialSpawnerPos, trialSpawner, trialspawnerstatedata).ifPresent((vec3) -> {
                    OminousItemSpawner ominousitemspawner = OminousItemSpawner.create(level, itemstack);

                    ominousitemspawner.snapTo(vec3);
                    level.addFreshEntity(ominousitemspawner);
                    float f = (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.2F + 1.0F;

                    level.playSound((Entity) null, BlockPos.containing(vec3), SoundEvents.TRIAL_SPAWNER_SPAWN_ITEM_BEGIN, SoundSource.BLOCKS, 1.0F, f);
                    trialspawnerstatedata.cooldownEndsAt = level.getGameTime() + trialSpawner.ominousConfig().ticksBetweenItemSpawners();
                });
            }

        }
    }

    private static Optional<Vec3> calculatePositionToSpawnSpawner(ServerLevel level, BlockPos trialSpawnerPos, TrialSpawner trialSpawner, TrialSpawnerStateData data) {
        Stream stream = data.detectedPlayers.stream();

        Objects.requireNonNull(level);
        List<Player> list = stream.map(level::getPlayerByUUID).filter(Objects::nonNull).filter((player) -> {
            return !player.isCreative() && !player.isSpectator() && player.isAlive() && player.distanceToSqr(trialSpawnerPos.getCenter()) <= (double) Mth.square(trialSpawner.getRequiredPlayerRange());
        }).toList();

        if (list.isEmpty()) {
            return Optional.empty();
        } else {
            Entity entity = selectEntityToSpawnItemAbove(list, data.currentMobs, trialSpawner, trialSpawnerPos, level);

            return entity == null ? Optional.empty() : calculatePositionAbove(entity, level);
        }
    }

    private static Optional<Vec3> calculatePositionAbove(Entity entityToSpawnItemAbove, ServerLevel level) {
        Vec3 vec3 = entityToSpawnItemAbove.position();
        Vec3 vec31 = vec3.relative(Direction.UP, (double) (entityToSpawnItemAbove.getBbHeight() + 2.0F + (float) level.random.nextInt(4)));
        BlockHitResult blockhitresult = level.clip(new ClipContext(vec3, vec31, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
        Vec3 vec32 = blockhitresult.getBlockPos().getCenter().relative(Direction.DOWN, 1.0D);
        BlockPos blockpos = BlockPos.containing(vec32);

        return !level.getBlockState(blockpos).getCollisionShape(level, blockpos).isEmpty() ? Optional.empty() : Optional.of(vec32);
    }

    private static @Nullable Entity selectEntityToSpawnItemAbove(List<Player> nearbyPlayers, Set<UUID> mobIds, TrialSpawner trialSpawner, BlockPos spawnerPos, ServerLevel level) {
        Stream stream = mobIds.stream();

        Objects.requireNonNull(level);
        Stream<Entity> stream1 = stream.map(level::getEntity).filter(Objects::nonNull).filter((entity) -> {
            return entity.isAlive() && entity.distanceToSqr(spawnerPos.getCenter()) <= (double) Mth.square(trialSpawner.getRequiredPlayerRange());
        });
        List<? extends Entity> list1 = level.random.nextBoolean() ? stream1.toList() : nearbyPlayers;

        return list1.isEmpty() ? null : (list1.size() == 1 ? (Entity) list1.getFirst() : (Entity) Util.getRandom(list1, level.random));
    }

    private boolean timeToSpawnItemSpawner(ServerLevel serverLevel, TrialSpawnerStateData data) {
        return serverLevel.getGameTime() >= data.cooldownEndsAt;
    }

    public int lightLevel() {
        return this.lightLevel;
    }

    public double spinningMobSpeed() {
        return this.spinningMobSpeed;
    }

    public boolean hasSpinningMob() {
        return this.spinningMobSpeed >= 0.0D;
    }

    public boolean isCapableOfSpawning() {
        return this.isCapableOfSpawning;
    }

    public void emitParticles(Level level, BlockPos blockPos, boolean isOminous) {
        this.particleEmission.emit(level, level.getRandom(), blockPos, isOminous);
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    private static class LightLevel {

        private static final int UNLIT = 0;
        private static final int HALF_LIT = 4;
        private static final int LIT = 8;

        private LightLevel() {}
    }

    private static class SpinningMob {

        private static final double NONE = -1.0D;
        private static final double SLOW = 200.0D;
        private static final double FAST = 1000.0D;

        private SpinningMob() {}
    }

    private interface ParticleEmission {

        TrialSpawnerState.ParticleEmission NONE = (level, randomsource, blockpos, flag) -> {
        };
        TrialSpawnerState.ParticleEmission SMALL_FLAMES = (level, randomsource, blockpos, flag) -> {
            if (randomsource.nextInt(2) == 0) {
                Vec3 vec3 = blockpos.getCenter().offsetRandom(randomsource, 0.9F);

                addParticle(flag ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SMALL_FLAME, vec3, level);
            }

        };
        TrialSpawnerState.ParticleEmission FLAMES_AND_SMOKE = (level, randomsource, blockpos, flag) -> {
            Vec3 vec3 = blockpos.getCenter().offsetRandom(randomsource, 1.0F);

            addParticle(ParticleTypes.SMOKE, vec3, level);
            addParticle(flag ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, vec3, level);
        };
        TrialSpawnerState.ParticleEmission SMOKE_INSIDE_AND_TOP_FACE = (level, randomsource, blockpos, flag) -> {
            Vec3 vec3 = blockpos.getCenter().offsetRandom(randomsource, 0.9F);

            if (randomsource.nextInt(3) == 0) {
                addParticle(ParticleTypes.SMOKE, vec3, level);
            }

            if (level.getGameTime() % 20L == 0L) {
                Vec3 vec31 = blockpos.getCenter().add(0.0D, 0.5D, 0.0D);
                int i = level.getRandom().nextInt(4) + 20;

                for (int j = 0; j < i; ++j) {
                    addParticle(ParticleTypes.SMOKE, vec31, level);
                }
            }

        };

        private static void addParticle(SimpleParticleType smoke, Vec3 vec, Level level) {
            level.addParticle(smoke, vec.x(), vec.y(), vec.z(), 0.0D, 0.0D, 0.0D);
        }

        void emit(Level level, RandomSource random, BlockPos blockPos, boolean isOminous);
    }
}
