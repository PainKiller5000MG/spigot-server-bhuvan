package net.minecraft.world.level.block.entity.trialspawner;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.TrialSpawnerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.slf4j.Logger;

public final class TrialSpawner {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int DETECT_PLAYER_SPAWN_BUFFER = 40;
    private static final int DEFAULT_TARGET_COOLDOWN_LENGTH = 36000;
    private static final int DEFAULT_PLAYER_SCAN_RANGE = 14;
    private static final int MAX_MOB_TRACKING_DISTANCE = 47;
    private static final int MAX_MOB_TRACKING_DISTANCE_SQR = Mth.square(47);
    private static final float SPAWNING_AMBIENT_SOUND_CHANCE = 0.02F;
    private final TrialSpawnerStateData data = new TrialSpawnerStateData();
    public TrialSpawner.FullConfig config;
    public final TrialSpawner.StateAccessor stateAccessor;
    private PlayerDetector playerDetector;
    private final PlayerDetector.EntitySelector entitySelector;
    private boolean overridePeacefulAndMobSpawnRule;
    public boolean isOminous;

    public TrialSpawner(TrialSpawner.FullConfig config, TrialSpawner.StateAccessor stateAccessor, PlayerDetector playerDetector, PlayerDetector.EntitySelector entitySelector) {
        this.config = config;
        this.stateAccessor = stateAccessor;
        this.playerDetector = playerDetector;
        this.entitySelector = entitySelector;
    }

    public TrialSpawnerConfig activeConfig() {
        return this.isOminous ? (TrialSpawnerConfig) this.config.ominous().value() : (TrialSpawnerConfig) this.config.normal.value();
    }

    public TrialSpawnerConfig normalConfig() {
        return this.config.normal.value();
    }

    public TrialSpawnerConfig ominousConfig() {
        return this.config.ominous.value();
    }

    public void load(ValueInput input) {
        Optional optional = input.read(TrialSpawnerStateData.Packed.MAP_CODEC);
        TrialSpawnerStateData trialspawnerstatedata = this.data;

        Objects.requireNonNull(this.data);
        optional.ifPresent(trialspawnerstatedata::apply);
        this.config = (TrialSpawner.FullConfig) input.read(TrialSpawner.FullConfig.MAP_CODEC).orElse(TrialSpawner.FullConfig.DEFAULT);
    }

    public void store(ValueOutput output) {
        output.store(TrialSpawnerStateData.Packed.MAP_CODEC, this.data.pack());
        output.store(TrialSpawner.FullConfig.MAP_CODEC, this.config);
    }

    public void applyOminous(ServerLevel level, BlockPos spawnerPos) {
        level.setBlock(spawnerPos, (BlockState) level.getBlockState(spawnerPos).setValue(TrialSpawnerBlock.OMINOUS, true), 3);
        level.levelEvent(3020, spawnerPos, 1);
        this.isOminous = true;
        this.data.resetAfterBecomingOminous(this, level);
    }

    public void removeOminous(ServerLevel level, BlockPos spawnerPos) {
        level.setBlock(spawnerPos, (BlockState) level.getBlockState(spawnerPos).setValue(TrialSpawnerBlock.OMINOUS, false), 3);
        this.isOminous = false;
    }

    public boolean isOminous() {
        return this.isOminous;
    }

    public int getTargetCooldownLength() {
        return this.config.targetCooldownLength;
    }

    public int getRequiredPlayerRange() {
        return this.config.requiredPlayerRange;
    }

    public TrialSpawnerState getState() {
        return this.stateAccessor.getState();
    }

    public TrialSpawnerStateData getStateData() {
        return this.data;
    }

    public void setState(Level level, TrialSpawnerState state) {
        this.stateAccessor.setState(level, state);
    }

    public void markUpdated() {
        this.stateAccessor.markUpdated();
    }

    public PlayerDetector getPlayerDetector() {
        return this.playerDetector;
    }

    public PlayerDetector.EntitySelector getEntitySelector() {
        return this.entitySelector;
    }

    public boolean canSpawnInLevel(ServerLevel level) {
        return !(Boolean) level.getGameRules().get(GameRules.SPAWNER_BLOCKS_WORK) ? false : (this.overridePeacefulAndMobSpawnRule ? true : (level.getDifficulty() == Difficulty.PEACEFUL ? false : (Boolean) level.getGameRules().get(GameRules.SPAWN_MOBS)));
    }

    public Optional<UUID> spawnMob(ServerLevel level, BlockPos spawnerPos) {
        RandomSource randomsource = level.getRandom();
        SpawnData spawndata = this.data.getOrCreateNextSpawnData(this, level.getRandom());

        try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(() -> {
            return "spawner@" + String.valueOf(spawnerPos);
        }, TrialSpawner.LOGGER)) {
            ValueInput valueinput = TagValueInput.create(problemreporter_scopedcollector, level.registryAccess(), spawndata.entityToSpawn());
            Optional<EntityType<?>> optional = EntityType.by(valueinput);

            if (optional.isEmpty()) {
                return Optional.empty();
            } else {
                Vec3 vec3 = (Vec3) valueinput.read("Pos", Vec3.CODEC).orElseGet(() -> {
                    TrialSpawnerConfig trialspawnerconfig = this.activeConfig();

                    return new Vec3((double) spawnerPos.getX() + (randomsource.nextDouble() - randomsource.nextDouble()) * (double) trialspawnerconfig.spawnRange() + 0.5D, (double) (spawnerPos.getY() + randomsource.nextInt(3) - 1), (double) spawnerPos.getZ() + (randomsource.nextDouble() - randomsource.nextDouble()) * (double) trialspawnerconfig.spawnRange() + 0.5D);
                });

                if (!level.noCollision(((EntityType) optional.get()).getSpawnAABB(vec3.x, vec3.y, vec3.z))) {
                    return Optional.empty();
                } else if (!inLineOfSight(level, spawnerPos.getCenter(), vec3)) {
                    return Optional.empty();
                } else {
                    BlockPos blockpos1 = BlockPos.containing(vec3);

                    if (!SpawnPlacements.checkSpawnRules((EntityType) optional.get(), level, EntitySpawnReason.TRIAL_SPAWNER, blockpos1, level.getRandom())) {
                        return Optional.empty();
                    } else {
                        if (spawndata.getCustomSpawnRules().isPresent()) {
                            SpawnData.CustomSpawnRules spawndata_customspawnrules = (SpawnData.CustomSpawnRules) spawndata.getCustomSpawnRules().get();

                            if (!spawndata_customspawnrules.isValidPosition(blockpos1, level)) {
                                return Optional.empty();
                            }
                        }

                        Entity entity = EntityType.loadEntityRecursive(valueinput, level, EntitySpawnReason.TRIAL_SPAWNER, (entity1) -> {
                            entity1.snapTo(vec3.x, vec3.y, vec3.z, randomsource.nextFloat() * 360.0F, 0.0F);
                            return entity1;
                        });

                        if (entity == null) {
                            return Optional.empty();
                        } else {
                            if (entity instanceof Mob) {
                                Mob mob = (Mob) entity;

                                if (!mob.checkSpawnObstruction(level)) {
                                    return Optional.empty();
                                }

                                boolean flag = spawndata.getEntityToSpawn().size() == 1 && spawndata.getEntityToSpawn().getString("id").isPresent();

                                if (flag) {
                                    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.TRIAL_SPAWNER, (SpawnGroupData) null);
                                }

                                mob.setPersistenceRequired();
                                Optional optional1 = spawndata.getEquipment();

                                Objects.requireNonNull(mob);
                                optional1.ifPresent(mob::equip);
                            }

                            if (!level.tryAddFreshEntityWithPassengers(entity)) {
                                return Optional.empty();
                            } else {
                                TrialSpawner.FlameParticle trialspawner_flameparticle = this.isOminous ? TrialSpawner.FlameParticle.OMINOUS : TrialSpawner.FlameParticle.NORMAL;

                                level.levelEvent(3011, spawnerPos, trialspawner_flameparticle.encode());
                                level.levelEvent(3012, blockpos1, trialspawner_flameparticle.encode());
                                level.gameEvent(entity, (Holder) GameEvent.ENTITY_PLACE, blockpos1);
                                return Optional.of(entity.getUUID());
                            }
                        }
                    }
                }
            }
        }
    }

    public void ejectReward(ServerLevel level, BlockPos pos, ResourceKey<LootTable> ejectingLootTable) {
        LootTable loottable = level.getServer().reloadableRegistries().getLootTable(ejectingLootTable);
        LootParams lootparams = (new LootParams.Builder(level)).create(LootContextParamSets.EMPTY);
        ObjectArrayList<ItemStack> objectarraylist = loottable.getRandomItems(lootparams);

        if (!objectarraylist.isEmpty()) {
            ObjectListIterator objectlistiterator = objectarraylist.iterator();

            while (objectlistiterator.hasNext()) {
                ItemStack itemstack = (ItemStack) objectlistiterator.next();

                DefaultDispenseItemBehavior.spawnItem(level, itemstack, 2, Direction.UP, Vec3.atBottomCenterOf(pos).relative(Direction.UP, 1.2D));
            }

            level.levelEvent(3014, pos, 0);
        }

    }

    public void tickClient(Level level, BlockPos spawnerPos, boolean isOminous) {
        TrialSpawnerState trialspawnerstate = this.getState();

        trialspawnerstate.emitParticles(level, spawnerPos, isOminous);
        if (trialspawnerstate.hasSpinningMob()) {
            double d0 = (double) Math.max(0L, this.data.nextMobSpawnsAt - level.getGameTime());

            this.data.oSpin = this.data.spin;
            this.data.spin = (this.data.spin + trialspawnerstate.spinningMobSpeed() / (d0 + 200.0D)) % 360.0D;
        }

        if (trialspawnerstate.isCapableOfSpawning()) {
            RandomSource randomsource = level.getRandom();

            if (randomsource.nextFloat() <= 0.02F) {
                SoundEvent soundevent = isOminous ? SoundEvents.TRIAL_SPAWNER_AMBIENT_OMINOUS : SoundEvents.TRIAL_SPAWNER_AMBIENT;

                level.playLocalSound(spawnerPos, soundevent, SoundSource.BLOCKS, randomsource.nextFloat() * 0.25F + 0.75F, randomsource.nextFloat() + 0.5F, false);
            }
        }

    }

    public void tickServer(ServerLevel serverLevel, BlockPos spawnerPos, boolean isOminous) {
        this.isOminous = isOminous;
        TrialSpawnerState trialspawnerstate = this.getState();

        if (this.data.currentMobs.removeIf((uuid) -> {
            return shouldMobBeUntracked(serverLevel, spawnerPos, uuid);
        })) {
            this.data.nextMobSpawnsAt = serverLevel.getGameTime() + (long) this.activeConfig().ticksBetweenSpawn();
        }

        TrialSpawnerState trialspawnerstate1 = trialspawnerstate.tickAndGetNext(spawnerPos, this, serverLevel);

        if (trialspawnerstate1 != trialspawnerstate) {
            this.setState(serverLevel, trialspawnerstate1);
        }

    }

    private static boolean shouldMobBeUntracked(ServerLevel serverLevel, BlockPos spawnerPos, UUID id) {
        Entity entity = serverLevel.getEntity(id);

        return entity == null || !entity.isAlive() || !entity.level().dimension().equals(serverLevel.dimension()) || entity.blockPosition().distSqr(spawnerPos) > (double) TrialSpawner.MAX_MOB_TRACKING_DISTANCE_SQR;
    }

    private static boolean inLineOfSight(Level level, Vec3 origin, Vec3 dest) {
        BlockHitResult blockhitresult = level.clip(new ClipContext(dest, origin, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));

        return blockhitresult.getBlockPos().equals(BlockPos.containing(origin)) || blockhitresult.getType() == HitResult.Type.MISS;
    }

    public static void addSpawnParticles(Level level, BlockPos pos, RandomSource random, SimpleParticleType particleType) {
        for (int i = 0; i < 20; ++i) {
            double d0 = (double) pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 2.0D;
            double d1 = (double) pos.getY() + 0.5D + (random.nextDouble() - 0.5D) * 2.0D;
            double d2 = (double) pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 2.0D;

            level.addParticle(ParticleTypes.SMOKE, d0, d1, d2, 0.0D, 0.0D, 0.0D);
            level.addParticle(particleType, d0, d1, d2, 0.0D, 0.0D, 0.0D);
        }

    }

    public static void addBecomeOminousParticles(Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 20; ++i) {
            double d0 = (double) pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 2.0D;
            double d1 = (double) pos.getY() + 0.5D + (random.nextDouble() - 0.5D) * 2.0D;
            double d2 = (double) pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 2.0D;
            double d3 = random.nextGaussian() * 0.02D;
            double d4 = random.nextGaussian() * 0.02D;
            double d5 = random.nextGaussian() * 0.02D;

            level.addParticle(ParticleTypes.TRIAL_OMEN, d0, d1, d2, d3, d4, d5);
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, d0, d1, d2, d3, d4, d5);
        }

    }

    public static void addDetectPlayerParticles(Level level, BlockPos pos, RandomSource random, int data, ParticleOptions type) {
        for (int j = 0; j < 30 + Math.min(data, 10) * 5; ++j) {
            double d0 = (double) (2.0F * random.nextFloat() - 1.0F) * 0.65D;
            double d1 = (double) (2.0F * random.nextFloat() - 1.0F) * 0.65D;
            double d2 = (double) pos.getX() + 0.5D + d0;
            double d3 = (double) pos.getY() + 0.1D + (double) random.nextFloat() * 0.8D;
            double d4 = (double) pos.getZ() + 0.5D + d1;

            level.addParticle(type, d2, d3, d4, 0.0D, 0.0D, 0.0D);
        }

    }

    public static void addEjectItemParticles(Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 20; ++i) {
            double d0 = (double) pos.getX() + 0.4D + random.nextDouble() * 0.2D;
            double d1 = (double) pos.getY() + 0.4D + random.nextDouble() * 0.2D;
            double d2 = (double) pos.getZ() + 0.4D + random.nextDouble() * 0.2D;
            double d3 = random.nextGaussian() * 0.02D;
            double d4 = random.nextGaussian() * 0.02D;
            double d5 = random.nextGaussian() * 0.02D;

            level.addParticle(ParticleTypes.SMALL_FLAME, d0, d1, d2, d3, d4, d5 * 0.25D);
            level.addParticle(ParticleTypes.SMOKE, d0, d1, d2, d3, d4, d5);
        }

    }

    public void overrideEntityToSpawn(EntityType<?> type, Level level) {
        this.data.reset();
        this.config = this.config.overrideEntity(type);
        this.setState(level, TrialSpawnerState.INACTIVE);
    }

    /** @deprecated */
    @Deprecated(forRemoval = true)
    @VisibleForTesting
    public void setPlayerDetector(PlayerDetector playerDetector) {
        this.playerDetector = playerDetector;
    }

    /** @deprecated */
    @Deprecated(forRemoval = true)
    @VisibleForTesting
    public void overridePeacefulAndMobSpawnRule() {
        this.overridePeacefulAndMobSpawnRule = true;
    }

    public static enum FlameParticle {

        NORMAL(ParticleTypes.FLAME), OMINOUS(ParticleTypes.SOUL_FIRE_FLAME);

        public final SimpleParticleType particleType;

        private FlameParticle(SimpleParticleType particleType) {
            this.particleType = particleType;
        }

        public static TrialSpawner.FlameParticle decode(int data) {
            TrialSpawner.FlameParticle[] atrialspawner_flameparticle = values();

            return data <= atrialspawner_flameparticle.length && data >= 0 ? atrialspawner_flameparticle[data] : TrialSpawner.FlameParticle.NORMAL;
        }

        public int encode() {
            return this.ordinal();
        }
    }

    public static record FullConfig(Holder<TrialSpawnerConfig> normal, Holder<TrialSpawnerConfig> ominous, int targetCooldownLength, int requiredPlayerRange) {

        public static final MapCodec<TrialSpawner.FullConfig> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(TrialSpawnerConfig.CODEC.optionalFieldOf("normal_config", Holder.direct(TrialSpawnerConfig.DEFAULT)).forGetter(TrialSpawner.FullConfig::normal), TrialSpawnerConfig.CODEC.optionalFieldOf("ominous_config", Holder.direct(TrialSpawnerConfig.DEFAULT)).forGetter(TrialSpawner.FullConfig::ominous), ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("target_cooldown_length", 36000).forGetter(TrialSpawner.FullConfig::targetCooldownLength), Codec.intRange(1, 128).optionalFieldOf("required_player_range", 14).forGetter(TrialSpawner.FullConfig::requiredPlayerRange)).apply(instance, TrialSpawner.FullConfig::new);
        });
        public static final TrialSpawner.FullConfig DEFAULT = new TrialSpawner.FullConfig(Holder.direct(TrialSpawnerConfig.DEFAULT), Holder.direct(TrialSpawnerConfig.DEFAULT), 36000, 14);

        public TrialSpawner.FullConfig overrideEntity(EntityType<?> type) {
            return new TrialSpawner.FullConfig(Holder.direct((this.normal.value()).withSpawning(type)), Holder.direct((this.ominous.value()).withSpawning(type)), this.targetCooldownLength, this.requiredPlayerRange);
        }
    }

    public interface StateAccessor {

        void setState(Level level, TrialSpawnerState state);

        TrialSpawnerState getState();

        void markUpdated();
    }
}
