package net.minecraft.world.level;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.NetherFortressStructure;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class NaturalSpawner {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MIN_SPAWN_DISTANCE = 24;
    public static final int SPAWN_DISTANCE_CHUNK = 8;
    public static final int SPAWN_DISTANCE_BLOCK = 128;
    public static final int INSCRIBED_SQUARE_SPAWN_DISTANCE_CHUNK = Mth.floor(8.0F / Mth.SQRT_OF_TWO);
    private static final int MAGIC_NUMBER = (int) Math.pow(17.0D, 2.0D);
    private static final MobCategory[] SPAWNING_CATEGORIES = (MobCategory[]) Stream.of(MobCategory.values()).filter((mobcategory) -> {
        return mobcategory != MobCategory.MISC;
    }).toArray((i) -> {
        return new MobCategory[i];
    });

    private NaturalSpawner() {}

    public static NaturalSpawner.SpawnState createState(int spawnableChunkCount, Iterable<Entity> entities, NaturalSpawner.ChunkGetter chunkGetter, LocalMobCapCalculator localMobCapCalculator) {
        PotentialCalculator potentialcalculator = new PotentialCalculator();
        Object2IntOpenHashMap<MobCategory> object2intopenhashmap = new Object2IntOpenHashMap();

        for (Entity entity : entities) {
            if (entity instanceof Mob mob) {
                if (mob.isPersistenceRequired() || mob.requiresCustomPersistence()) {
                    continue;
                }
            }

            MobCategory mobcategory = entity.getType().getCategory();

            if (mobcategory != MobCategory.MISC) {
                BlockPos blockpos = entity.blockPosition();

                chunkGetter.query(ChunkPos.asLong(blockpos), (levelchunk) -> {
                    MobSpawnSettings.MobSpawnCost mobspawnsettings_mobspawncost = getRoughBiome(blockpos, levelchunk).getMobSettings().getMobSpawnCost(entity.getType());

                    if (mobspawnsettings_mobspawncost != null) {
                        potentialcalculator.addCharge(entity.blockPosition(), mobspawnsettings_mobspawncost.charge());
                    }

                    if (entity instanceof Mob) {
                        localMobCapCalculator.addMob(levelchunk.getPos(), mobcategory);
                    }

                    object2intopenhashmap.addTo(mobcategory, 1);
                });
            }
        }

        return new NaturalSpawner.SpawnState(spawnableChunkCount, object2intopenhashmap, potentialcalculator, localMobCapCalculator);
    }

    private static Biome getRoughBiome(BlockPos pos, ChunkAccess chunk) {
        return (Biome) chunk.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ())).value();
    }

    public static List<MobCategory> getFilteredSpawningCategories(NaturalSpawner.SpawnState state, boolean spawnFriendlies, boolean spawnEnemies, boolean spawnPersistent) {
        List<MobCategory> list = new ArrayList(NaturalSpawner.SPAWNING_CATEGORIES.length);

        for (MobCategory mobcategory : NaturalSpawner.SPAWNING_CATEGORIES) {
            if ((spawnFriendlies || !mobcategory.isFriendly()) && (spawnEnemies || mobcategory.isFriendly()) && (spawnPersistent || !mobcategory.isPersistent()) && state.canSpawnForCategoryGlobal(mobcategory)) {
                list.add(mobcategory);
            }
        }

        return list;
    }

    public static void spawnForChunk(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState state, List<MobCategory> spawningCategories) {
        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("spawner");

        for (MobCategory mobcategory : spawningCategories) {
            if (state.canSpawnForCategoryLocal(mobcategory, chunk.getPos())) {
                Objects.requireNonNull(state);
                NaturalSpawner.SpawnPredicate naturalspawner_spawnpredicate = state::canSpawn;

                Objects.requireNonNull(state);
                spawnCategoryForChunk(mobcategory, level, chunk, naturalspawner_spawnpredicate, state::afterSpawn);
            }
        }

        profilerfiller.pop();
    }

    public static void spawnCategoryForChunk(MobCategory mobCategory, ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnPredicate extraTest, NaturalSpawner.AfterSpawnCallback spawnCallback) {
        BlockPos blockpos = getRandomPosWithin(level, chunk);

        if (blockpos.getY() >= level.getMinY() + 1) {
            spawnCategoryForPosition(mobCategory, level, chunk, blockpos, extraTest, spawnCallback);
        }
    }

    @VisibleForDebug
    public static void spawnCategoryForPosition(MobCategory mobCategory, ServerLevel level, BlockPos start) {
        spawnCategoryForPosition(mobCategory, level, level.getChunk(start), start, (entitytype, blockpos1, chunkaccess) -> {
            return true;
        }, (mob, chunkaccess) -> {
        });
    }

    public static void spawnCategoryForPosition(MobCategory mobCategory, ServerLevel level, ChunkAccess chunk, BlockPos start, NaturalSpawner.SpawnPredicate extraTest, NaturalSpawner.AfterSpawnCallback spawnCallback) {
        StructureManager structuremanager = level.structureManager();
        ChunkGenerator chunkgenerator = level.getChunkSource().getGenerator();
        int i = start.getY();
        BlockState blockstate = chunk.getBlockState(start);

        if (!blockstate.isRedstoneConductor(chunk, start)) {
            BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
            int j = 0;

            for (int k = 0; k < 3; ++k) {
                int l = start.getX();
                int i1 = start.getZ();
                int j1 = 6;
                MobSpawnSettings.SpawnerData mobspawnsettings_spawnerdata = null;
                SpawnGroupData spawngroupdata = null;
                int k1 = Mth.ceil(level.random.nextFloat() * 4.0F);
                int l1 = 0;

                for (int i2 = 0; i2 < k1; ++i2) {
                    l += level.random.nextInt(6) - level.random.nextInt(6);
                    i1 += level.random.nextInt(6) - level.random.nextInt(6);
                    blockpos_mutableblockpos.set(l, i, i1);
                    double d0 = (double) l + 0.5D;
                    double d1 = (double) i1 + 0.5D;
                    Player player = level.getNearestPlayer(d0, (double) i, d1, -1.0D, false);

                    if (player != null) {
                        double d2 = player.distanceToSqr(d0, (double) i, d1);

                        if (isRightDistanceToPlayerAndSpawnPoint(level, chunk, blockpos_mutableblockpos, d2)) {
                            if (mobspawnsettings_spawnerdata == null) {
                                Optional<MobSpawnSettings.SpawnerData> optional = getRandomSpawnMobAt(level, structuremanager, chunkgenerator, mobCategory, level.random, blockpos_mutableblockpos);

                                if (optional.isEmpty()) {
                                    break;
                                }

                                mobspawnsettings_spawnerdata = (MobSpawnSettings.SpawnerData) optional.get();
                                k1 = mobspawnsettings_spawnerdata.minCount() + level.random.nextInt(1 + mobspawnsettings_spawnerdata.maxCount() - mobspawnsettings_spawnerdata.minCount());
                            }

                            if (isValidSpawnPostitionForType(level, mobCategory, structuremanager, chunkgenerator, mobspawnsettings_spawnerdata, blockpos_mutableblockpos, d2) && extraTest.test(mobspawnsettings_spawnerdata.type(), blockpos_mutableblockpos, chunk)) {
                                Mob mob = getMobForSpawn(level, mobspawnsettings_spawnerdata.type());

                                if (mob == null) {
                                    return;
                                }

                                mob.snapTo(d0, (double) i, d1, level.random.nextFloat() * 360.0F, 0.0F);
                                if (isValidPositionForMob(level, mob, d2)) {
                                    spawngroupdata = mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.NATURAL, spawngroupdata);
                                    ++j;
                                    ++l1;
                                    level.addFreshEntityWithPassengers(mob);
                                    spawnCallback.run(mob, chunk);
                                    if (j >= mob.getMaxSpawnClusterSize()) {
                                        return;
                                    }

                                    if (mob.isMaxGroupSizeReached(l1)) {
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }
    }

    private static boolean isRightDistanceToPlayerAndSpawnPoint(ServerLevel level, ChunkAccess chunk, BlockPos.MutableBlockPos pos, double nearestPlayerDistanceSqr) {
        if (nearestPlayerDistanceSqr <= 576.0D) {
            return false;
        } else {
            LevelData.RespawnData leveldata_respawndata = level.getRespawnData();

            if (leveldata_respawndata.dimension() == level.dimension() && leveldata_respawndata.pos().closerToCenterThan(new Vec3((double) pos.getX() + 0.5D, (double) pos.getY(), (double) pos.getZ() + 0.5D), 24.0D)) {
                return false;
            } else {
                ChunkPos chunkpos = new ChunkPos(pos);

                return Objects.equals(chunkpos, chunk.getPos()) || level.canSpawnEntitiesInChunk(chunkpos);
            }
        }
    }

    private static boolean isValidSpawnPostitionForType(ServerLevel level, MobCategory mobCategory, StructureManager structureManager, ChunkGenerator generator, MobSpawnSettings.SpawnerData currentSpawnData, BlockPos.MutableBlockPos pos, double nearestPlayerDistanceSqr) {
        EntityType<?> entitytype = currentSpawnData.type();

        return entitytype.getCategory() == MobCategory.MISC ? false : (!entitytype.canSpawnFarFromPlayer() && nearestPlayerDistanceSqr > (double) (entitytype.getCategory().getDespawnDistance() * entitytype.getCategory().getDespawnDistance()) ? false : (entitytype.canSummon() && canSpawnMobAt(level, structureManager, generator, mobCategory, currentSpawnData, pos) ? (!SpawnPlacements.isSpawnPositionOk(entitytype, level, pos) ? false : (!SpawnPlacements.checkSpawnRules(entitytype, level, EntitySpawnReason.NATURAL, pos, level.random) ? false : level.noCollision(entitytype.getSpawnAABB((double) pos.getX() + 0.5D, (double) pos.getY(), (double) pos.getZ() + 0.5D)))) : false));
    }

    private static @Nullable Mob getMobForSpawn(ServerLevel level, EntityType<?> type) {
        try {
            Entity entity = type.create(level, EntitySpawnReason.NATURAL);

            if (entity instanceof Mob mob) {
                return mob;
            }

            NaturalSpawner.LOGGER.warn("Can't spawn entity of type: {}", BuiltInRegistries.ENTITY_TYPE.getKey(type));
        } catch (Exception exception) {
            NaturalSpawner.LOGGER.warn("Failed to create mob", exception);
        }

        return null;
    }

    private static boolean isValidPositionForMob(ServerLevel level, Mob mob, double nearestPlayerDistanceSqr) {
        return nearestPlayerDistanceSqr > (double) (mob.getType().getCategory().getDespawnDistance() * mob.getType().getCategory().getDespawnDistance()) && mob.removeWhenFarAway(nearestPlayerDistanceSqr) ? false : mob.checkSpawnRules(level, EntitySpawnReason.NATURAL) && mob.checkSpawnObstruction(level);
    }

    private static Optional<MobSpawnSettings.SpawnerData> getRandomSpawnMobAt(ServerLevel level, StructureManager structureManager, ChunkGenerator generator, MobCategory mobCategory, RandomSource random, BlockPos pos) {
        Holder<Biome> holder = level.getBiome(pos);

        return mobCategory == MobCategory.WATER_AMBIENT && holder.is(BiomeTags.REDUCED_WATER_AMBIENT_SPAWNS) && random.nextFloat() < 0.98F ? Optional.empty() : mobsAt(level, structureManager, generator, mobCategory, pos, holder).getRandom(random);
    }

    private static boolean canSpawnMobAt(ServerLevel level, StructureManager structureManager, ChunkGenerator generator, MobCategory mobCategory, MobSpawnSettings.SpawnerData spawnerData, BlockPos pos) {
        return mobsAt(level, structureManager, generator, mobCategory, pos, (Holder) null).contains(spawnerData);
    }

    private static WeightedList<MobSpawnSettings.SpawnerData> mobsAt(ServerLevel level, StructureManager structureManager, ChunkGenerator generator, MobCategory mobCategory, BlockPos pos, @Nullable Holder<Biome> biome) {
        return isInNetherFortressBounds(pos, level, mobCategory, structureManager) ? NetherFortressStructure.FORTRESS_ENEMIES : generator.getMobsAt(biome != null ? biome : level.getBiome(pos), structureManager, mobCategory, pos);
    }

    public static boolean isInNetherFortressBounds(BlockPos pos, ServerLevel level, MobCategory category, StructureManager structureManager) {
        if (category == MobCategory.MONSTER && level.getBlockState(pos.below()).is(Blocks.NETHER_BRICKS)) {
            Structure structure = (Structure) structureManager.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(BuiltinStructures.FORTRESS);

            return structure == null ? false : structureManager.getStructureAt(pos, structure).isValid();
        } else {
            return false;
        }
    }

    private static BlockPos getRandomPosWithin(Level level, LevelChunk chunk) {
        ChunkPos chunkpos = chunk.getPos();
        int i = chunkpos.getMinBlockX() + level.random.nextInt(16);
        int j = chunkpos.getMinBlockZ() + level.random.nextInt(16);
        int k = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, i, j) + 1;
        int l = Mth.randomBetweenInclusive(level.random, level.getMinY(), k);

        return new BlockPos(i, l, j);
    }

    public static boolean isValidEmptySpawnBlock(BlockGetter level, BlockPos pos, BlockState blockState, FluidState fluidState, EntityType<?> type) {
        return blockState.isCollisionShapeFullBlock(level, pos) ? false : (blockState.isSignalSource() ? false : (!fluidState.isEmpty() ? false : (blockState.is(BlockTags.PREVENT_MOB_SPAWNING_INSIDE) ? false : !type.isBlockDangerous(blockState))));
    }

    public static void spawnMobsForChunkGeneration(ServerLevelAccessor level, Holder<Biome> biome, ChunkPos chunkPos, RandomSource random) {
        MobSpawnSettings mobspawnsettings = ((Biome) biome.value()).getMobSettings();
        WeightedList<MobSpawnSettings.SpawnerData> weightedlist = mobspawnsettings.getMobs(MobCategory.CREATURE);

        if (!weightedlist.isEmpty() && (Boolean) level.getLevel().getGameRules().get(GameRules.SPAWN_MOBS)) {
            int i = chunkPos.getMinBlockX();
            int j = chunkPos.getMinBlockZ();

            while (random.nextFloat() < mobspawnsettings.getCreatureProbability()) {
                Optional<MobSpawnSettings.SpawnerData> optional = weightedlist.getRandom(random);

                if (!optional.isEmpty()) {
                    MobSpawnSettings.SpawnerData mobspawnsettings_spawnerdata = (MobSpawnSettings.SpawnerData) optional.get();
                    int k = mobspawnsettings_spawnerdata.minCount() + random.nextInt(1 + mobspawnsettings_spawnerdata.maxCount() - mobspawnsettings_spawnerdata.minCount());
                    SpawnGroupData spawngroupdata = null;
                    int l = i + random.nextInt(16);
                    int i1 = j + random.nextInt(16);
                    int j1 = l;
                    int k1 = i1;

                    for (int l1 = 0; l1 < k; ++l1) {
                        boolean flag = false;

                        for (int i2 = 0; !flag && i2 < 4; ++i2) {
                            BlockPos blockpos = getTopNonCollidingPos(level, mobspawnsettings_spawnerdata.type(), l, i1);

                            if (mobspawnsettings_spawnerdata.type().canSummon() && SpawnPlacements.isSpawnPositionOk(mobspawnsettings_spawnerdata.type(), level, blockpos)) {
                                float f = mobspawnsettings_spawnerdata.type().getWidth();
                                double d0 = Mth.clamp((double) l, (double) i + (double) f, (double) i + 16.0D - (double) f);
                                double d1 = Mth.clamp((double) i1, (double) j + (double) f, (double) j + 16.0D - (double) f);

                                if (!level.noCollision(mobspawnsettings_spawnerdata.type().getSpawnAABB(d0, (double) blockpos.getY(), d1)) || !SpawnPlacements.checkSpawnRules(mobspawnsettings_spawnerdata.type(), level, EntitySpawnReason.CHUNK_GENERATION, BlockPos.containing(d0, (double) blockpos.getY(), d1), level.getRandom())) {
                                    continue;
                                }

                                Entity entity;

                                try {
                                    entity = mobspawnsettings_spawnerdata.type().create(level.getLevel(), EntitySpawnReason.NATURAL);
                                } catch (Exception exception) {
                                    NaturalSpawner.LOGGER.warn("Failed to create mob", exception);
                                    continue;
                                }

                                if (entity == null) {
                                    continue;
                                }

                                entity.snapTo(d0, (double) blockpos.getY(), d1, random.nextFloat() * 360.0F, 0.0F);
                                if (entity instanceof Mob) {
                                    Mob mob = (Mob) entity;

                                    if (mob.checkSpawnRules(level, EntitySpawnReason.CHUNK_GENERATION) && mob.checkSpawnObstruction(level)) {
                                        spawngroupdata = mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.CHUNK_GENERATION, spawngroupdata);
                                        level.addFreshEntityWithPassengers(mob);
                                        flag = true;
                                    }
                                }
                            }

                            l += random.nextInt(5) - random.nextInt(5);

                            for (i1 += random.nextInt(5) - random.nextInt(5); l < i || l >= i + 16 || i1 < j || i1 >= j + 16; i1 = k1 + random.nextInt(5) - random.nextInt(5)) {
                                l = j1 + random.nextInt(5) - random.nextInt(5);
                            }
                        }
                    }
                }
            }

        }
    }

    private static BlockPos getTopNonCollidingPos(LevelReader level, EntityType<?> type, int x, int z) {
        int k = level.getHeight(SpawnPlacements.getHeightmapType(type), x, z);
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos(x, k, z);

        if (level.dimensionType().hasCeiling()) {
            do {
                blockpos_mutableblockpos.move(Direction.DOWN);
            } while (!level.getBlockState(blockpos_mutableblockpos).isAir());

            do {
                blockpos_mutableblockpos.move(Direction.DOWN);
            } while (level.getBlockState(blockpos_mutableblockpos).isAir() && blockpos_mutableblockpos.getY() > level.getMinY());
        }

        return SpawnPlacements.getPlacementType(type).adjustSpawnPosition(level, blockpos_mutableblockpos.immutable());
    }

    public static class SpawnState {

        private final int spawnableChunkCount;
        private final Object2IntOpenHashMap<MobCategory> mobCategoryCounts;
        private final PotentialCalculator spawnPotential;
        private final Object2IntMap<MobCategory> unmodifiableMobCategoryCounts;
        private final LocalMobCapCalculator localMobCapCalculator;
        private @Nullable BlockPos lastCheckedPos;
        private @Nullable EntityType<?> lastCheckedType;
        private double lastCharge;

        private SpawnState(int spawnableChunkCount, Object2IntOpenHashMap<MobCategory> mobCategoryCounts, PotentialCalculator spawnPotential, LocalMobCapCalculator localMobCapCalculator) {
            this.spawnableChunkCount = spawnableChunkCount;
            this.mobCategoryCounts = mobCategoryCounts;
            this.spawnPotential = spawnPotential;
            this.localMobCapCalculator = localMobCapCalculator;
            this.unmodifiableMobCategoryCounts = Object2IntMaps.unmodifiable(mobCategoryCounts);
        }

        private boolean canSpawn(EntityType<?> type, BlockPos testPos, ChunkAccess chunk) {
            this.lastCheckedPos = testPos;
            this.lastCheckedType = type;
            MobSpawnSettings.MobSpawnCost mobspawnsettings_mobspawncost = NaturalSpawner.getRoughBiome(testPos, chunk).getMobSettings().getMobSpawnCost(type);

            if (mobspawnsettings_mobspawncost == null) {
                this.lastCharge = 0.0D;
                return true;
            } else {
                double d0 = mobspawnsettings_mobspawncost.charge();

                this.lastCharge = d0;
                double d1 = this.spawnPotential.getPotentialEnergyChange(testPos, d0);

                return d1 <= mobspawnsettings_mobspawncost.energyBudget();
            }
        }

        private void afterSpawn(Mob mob, ChunkAccess chunk) {
            EntityType<?> entitytype = mob.getType();
            BlockPos blockpos = mob.blockPosition();
            double d0;

            if (blockpos.equals(this.lastCheckedPos) && entitytype == this.lastCheckedType) {
                d0 = this.lastCharge;
            } else {
                MobSpawnSettings.MobSpawnCost mobspawnsettings_mobspawncost = NaturalSpawner.getRoughBiome(blockpos, chunk).getMobSettings().getMobSpawnCost(entitytype);

                if (mobspawnsettings_mobspawncost != null) {
                    d0 = mobspawnsettings_mobspawncost.charge();
                } else {
                    d0 = 0.0D;
                }
            }

            this.spawnPotential.addCharge(blockpos, d0);
            MobCategory mobcategory = entitytype.getCategory();

            this.mobCategoryCounts.addTo(mobcategory, 1);
            this.localMobCapCalculator.addMob(new ChunkPos(blockpos), mobcategory);
        }

        public int getSpawnableChunkCount() {
            return this.spawnableChunkCount;
        }

        public Object2IntMap<MobCategory> getMobCategoryCounts() {
            return this.unmodifiableMobCategoryCounts;
        }

        private boolean canSpawnForCategoryGlobal(MobCategory mobCategory) {
            int i = mobCategory.getMaxInstancesPerChunk() * this.spawnableChunkCount / NaturalSpawner.MAGIC_NUMBER;

            return this.mobCategoryCounts.getInt(mobCategory) < i;
        }

        private boolean canSpawnForCategoryLocal(MobCategory mobCategory, ChunkPos chunkPos) {
            return this.localMobCapCalculator.canSpawn(mobCategory, chunkPos) || SharedConstants.DEBUG_IGNORE_LOCAL_MOB_CAP;
        }
    }

    @FunctionalInterface
    public interface AfterSpawnCallback {

        void run(Mob mob, ChunkAccess levelChunk);
    }

    @FunctionalInterface
    public interface ChunkGetter {

        void query(long chunkKey, Consumer<LevelChunk> output);
    }

    @FunctionalInterface
    public interface SpawnPredicate {

        boolean test(EntityType<?> type, BlockPos blockPos, ChunkAccess levelChunk);
    }
}
