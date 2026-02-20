package net.minecraft.world.level;

import com.mojang.logging.LogUtils;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class BaseSpawner {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String SPAWN_DATA_TAG = "SpawnData";
    private static final int EVENT_SPAWN = 1;
    private static final int DEFAULT_SPAWN_DELAY = 20;
    private static final int DEFAULT_MIN_SPAWN_DELAY = 200;
    private static final int DEFAULT_MAX_SPAWN_DELAY = 800;
    private static final int DEFAULT_SPAWN_COUNT = 4;
    private static final int DEFAULT_MAX_NEARBY_ENTITIES = 6;
    private static final int DEFAULT_REQUIRED_PLAYER_RANGE = 16;
    private static final int DEFAULT_SPAWN_RANGE = 4;
    public int spawnDelay = 20;
    public WeightedList<SpawnData> spawnPotentials = WeightedList.<SpawnData>of();
    public @Nullable SpawnData nextSpawnData;
    private double spin;
    private double oSpin;
    public int minSpawnDelay = 200;
    public int maxSpawnDelay = 800;
    public int spawnCount = 4;
    private @Nullable Entity displayEntity;
    public int maxNearbyEntities = 6;
    public int requiredPlayerRange = 16;
    public int spawnRange = 4;

    public BaseSpawner() {}

    public void setEntityId(EntityType<?> type, @Nullable Level level, RandomSource random, BlockPos pos) {
        this.getOrCreateNextSpawnData(level, random, pos).getEntityToSpawn().putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
    }

    private boolean isNearPlayer(Level level, BlockPos pos) {
        return level.hasNearbyAlivePlayer((double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, (double) this.requiredPlayerRange);
    }

    public void clientTick(Level level, BlockPos pos) {
        if (!this.isNearPlayer(level, pos)) {
            this.oSpin = this.spin;
        } else if (this.displayEntity != null) {
            RandomSource randomsource = level.getRandom();
            double d0 = (double) pos.getX() + randomsource.nextDouble();
            double d1 = (double) pos.getY() + randomsource.nextDouble();
            double d2 = (double) pos.getZ() + randomsource.nextDouble();

            level.addParticle(ParticleTypes.SMOKE, d0, d1, d2, 0.0D, 0.0D, 0.0D);
            level.addParticle(ParticleTypes.FLAME, d0, d1, d2, 0.0D, 0.0D, 0.0D);
            if (this.spawnDelay > 0) {
                --this.spawnDelay;
            }

            this.oSpin = this.spin;
            this.spin = (this.spin + (double) (1000.0F / ((float) this.spawnDelay + 200.0F))) % 360.0D;
        }

    }

    public void serverTick(ServerLevel level, BlockPos pos) {
        if (this.isNearPlayer(level, pos) && level.isSpawnerBlockEnabled()) {
            if (this.spawnDelay == -1) {
                this.delay(level, pos);
            }

            if (this.spawnDelay > 0) {
                --this.spawnDelay;
            } else {
                boolean flag = false;
                RandomSource randomsource = level.getRandom();
                SpawnData spawndata = this.getOrCreateNextSpawnData(level, randomsource, pos);

                for (int i = 0; i < this.spawnCount; ++i) {
                    try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(this::toString, BaseSpawner.LOGGER)) {
                        ValueInput valueinput = TagValueInput.create(problemreporter_scopedcollector, level.registryAccess(), spawndata.getEntityToSpawn());
                        Optional<EntityType<?>> optional = EntityType.by(valueinput);

                        if (optional.isEmpty()) {
                            this.delay(level, pos);
                            return;
                        }

                        Vec3 vec3 = (Vec3) valueinput.read("Pos", Vec3.CODEC).orElseGet(() -> {
                            return new Vec3((double) pos.getX() + (randomsource.nextDouble() - randomsource.nextDouble()) * (double) this.spawnRange + 0.5D, (double) (pos.getY() + randomsource.nextInt(3) - 1), (double) pos.getZ() + (randomsource.nextDouble() - randomsource.nextDouble()) * (double) this.spawnRange + 0.5D);
                        });

                        if (level.noCollision(((EntityType) optional.get()).getSpawnAABB(vec3.x, vec3.y, vec3.z))) {
                            BlockPos blockpos1 = BlockPos.containing(vec3);

                            if (spawndata.getCustomSpawnRules().isPresent()) {
                                if (!((EntityType) optional.get()).getCategory().isFriendly() && level.getDifficulty() == Difficulty.PEACEFUL) {
                                    continue;
                                }

                                SpawnData.CustomSpawnRules spawndata_customspawnrules = (SpawnData.CustomSpawnRules) spawndata.getCustomSpawnRules().get();

                                if (!spawndata_customspawnrules.isValidPosition(blockpos1, level)) {
                                    continue;
                                }
                            } else if (!SpawnPlacements.checkSpawnRules((EntityType) optional.get(), level, EntitySpawnReason.SPAWNER, blockpos1, level.getRandom())) {
                                continue;
                            }

                            Entity entity = EntityType.loadEntityRecursive(valueinput, level, EntitySpawnReason.SPAWNER, (entity1) -> {
                                entity1.snapTo(vec3.x, vec3.y, vec3.z, entity1.getYRot(), entity1.getXRot());
                                return entity1;
                            });

                            if (entity == null) {
                                this.delay(level, pos);
                                return;
                            }

                            int j = level.getEntities(EntityTypeTest.forExactClass(entity.getClass()), (new AABB((double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), (double) (pos.getX() + 1), (double) (pos.getY() + 1), (double) (pos.getZ() + 1))).inflate((double) this.spawnRange), EntitySelector.NO_SPECTATORS).size();

                            if (j >= this.maxNearbyEntities) {
                                this.delay(level, pos);
                                return;
                            }

                            entity.snapTo(entity.getX(), entity.getY(), entity.getZ(), randomsource.nextFloat() * 360.0F, 0.0F);
                            if (entity instanceof Mob) {
                                Mob mob = (Mob) entity;

                                if (spawndata.getCustomSpawnRules().isEmpty() && !mob.checkSpawnRules(level, EntitySpawnReason.SPAWNER) || !mob.checkSpawnObstruction(level)) {
                                    continue;
                                }

                                boolean flag1 = spawndata.getEntityToSpawn().size() == 1 && spawndata.getEntityToSpawn().getString("id").isPresent();

                                if (flag1) {
                                    ((Mob) entity).finalizeSpawn(level, level.getCurrentDifficultyAt(entity.blockPosition()), EntitySpawnReason.SPAWNER, (SpawnGroupData) null);
                                }

                                Optional optional1 = spawndata.getEquipment();

                                Objects.requireNonNull(mob);
                                optional1.ifPresent(mob::equip);
                            }

                            if (!level.tryAddFreshEntityWithPassengers(entity)) {
                                this.delay(level, pos);
                                return;
                            }

                            level.levelEvent(2004, pos, 0);
                            level.gameEvent(entity, (Holder) GameEvent.ENTITY_PLACE, blockpos1);
                            if (entity instanceof Mob) {
                                ((Mob) entity).spawnAnim();
                            }

                            flag = true;
                        }
                    }
                }

                if (flag) {
                    this.delay(level, pos);
                }

            }
        }
    }

    private void delay(Level level, BlockPos pos) {
        RandomSource randomsource = level.random;

        if (this.maxSpawnDelay <= this.minSpawnDelay) {
            this.spawnDelay = this.minSpawnDelay;
        } else {
            this.spawnDelay = this.minSpawnDelay + randomsource.nextInt(this.maxSpawnDelay - this.minSpawnDelay);
        }

        this.spawnPotentials.getRandom(randomsource).ifPresent((spawndata) -> {
            this.setNextSpawnData(level, pos, spawndata);
        });
        this.broadcastEvent(level, pos, 1);
    }

    public void load(@Nullable Level level, BlockPos pos, ValueInput input) {
        this.spawnDelay = input.getShortOr("Delay", (short) 20);
        input.read("SpawnData", SpawnData.CODEC).ifPresent((spawndata) -> {
            this.setNextSpawnData(level, pos, spawndata);
        });
        this.spawnPotentials = (WeightedList) input.read("SpawnPotentials", SpawnData.LIST_CODEC).orElseGet(() -> {
            return WeightedList.of(this.nextSpawnData != null ? this.nextSpawnData : new SpawnData());
        });
        this.minSpawnDelay = input.getIntOr("MinSpawnDelay", 200);
        this.maxSpawnDelay = input.getIntOr("MaxSpawnDelay", 800);
        this.spawnCount = input.getIntOr("SpawnCount", 4);
        this.maxNearbyEntities = input.getIntOr("MaxNearbyEntities", 6);
        this.requiredPlayerRange = input.getIntOr("RequiredPlayerRange", 16);
        this.spawnRange = input.getIntOr("SpawnRange", 4);
        this.displayEntity = null;
    }

    public void save(ValueOutput output) {
        output.putShort("Delay", (short) this.spawnDelay);
        output.putShort("MinSpawnDelay", (short) this.minSpawnDelay);
        output.putShort("MaxSpawnDelay", (short) this.maxSpawnDelay);
        output.putShort("SpawnCount", (short) this.spawnCount);
        output.putShort("MaxNearbyEntities", (short) this.maxNearbyEntities);
        output.putShort("RequiredPlayerRange", (short) this.requiredPlayerRange);
        output.putShort("SpawnRange", (short) this.spawnRange);
        output.storeNullable("SpawnData", SpawnData.CODEC, this.nextSpawnData);
        output.store("SpawnPotentials", SpawnData.LIST_CODEC, this.spawnPotentials);
    }

    public @Nullable Entity getOrCreateDisplayEntity(Level level, BlockPos pos) {
        if (this.displayEntity == null) {
            CompoundTag compoundtag = this.getOrCreateNextSpawnData(level, level.getRandom(), pos).getEntityToSpawn();

            if (compoundtag.getString("id").isEmpty()) {
                return null;
            }

            this.displayEntity = EntityType.loadEntityRecursive(compoundtag, level, EntitySpawnReason.SPAWNER, EntityProcessor.NOP);
            if (compoundtag.size() == 1 && this.displayEntity instanceof Mob) {
                ;
            }
        }

        return this.displayEntity;
    }

    public boolean onEventTriggered(Level level, int id) {
        if (id == 1) {
            if (level.isClientSide()) {
                this.spawnDelay = this.minSpawnDelay;
            }

            return true;
        } else {
            return false;
        }
    }

    protected void setNextSpawnData(@Nullable Level level, BlockPos pos, SpawnData nextSpawnData) {
        this.nextSpawnData = nextSpawnData;
    }

    private SpawnData getOrCreateNextSpawnData(@Nullable Level level, RandomSource random, BlockPos pos) {
        if (this.nextSpawnData != null) {
            return this.nextSpawnData;
        } else {
            this.setNextSpawnData(level, pos, (SpawnData) this.spawnPotentials.getRandom(random).orElseGet(SpawnData::new));
            return this.nextSpawnData;
        }
    }

    public abstract void broadcastEvent(Level level, BlockPos pos, int id);

    public double getSpin() {
        return this.spin;
    }

    public double getOSpin() {
        return this.oSpin;
    }
}
