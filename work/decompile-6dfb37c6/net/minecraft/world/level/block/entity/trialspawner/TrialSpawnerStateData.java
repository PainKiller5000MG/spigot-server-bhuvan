package net.minecraft.world.level.block.entity.trialspawner;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.jspecify.annotations.Nullable;

public class TrialSpawnerStateData {

    private static final String TAG_SPAWN_DATA = "spawn_data";
    private static final String TAG_NEXT_MOB_SPAWNS_AT = "next_mob_spawns_at";
    private static final int DELAY_BETWEEN_PLAYER_SCANS = 20;
    private static final int TRIAL_OMEN_PER_BAD_OMEN_LEVEL = 18000;
    public final Set<UUID> detectedPlayers = new HashSet();
    public final Set<UUID> currentMobs = new HashSet();
    long cooldownEndsAt;
    long nextMobSpawnsAt;
    int totalMobsSpawned;
    public Optional<SpawnData> nextSpawnData = Optional.empty();
    Optional<ResourceKey<LootTable>> ejectingLootTable = Optional.empty();
    private @Nullable Entity displayEntity;
    private @Nullable WeightedList<ItemStack> dispensing;
    double spin;
    double oSpin;

    public TrialSpawnerStateData() {}

    public TrialSpawnerStateData.Packed pack() {
        return new TrialSpawnerStateData.Packed(Set.copyOf(this.detectedPlayers), Set.copyOf(this.currentMobs), this.cooldownEndsAt, this.nextMobSpawnsAt, this.totalMobsSpawned, this.nextSpawnData, this.ejectingLootTable);
    }

    public void apply(TrialSpawnerStateData.Packed packed) {
        this.detectedPlayers.clear();
        this.detectedPlayers.addAll(packed.detectedPlayers);
        this.currentMobs.clear();
        this.currentMobs.addAll(packed.currentMobs);
        this.cooldownEndsAt = packed.cooldownEndsAt;
        this.nextMobSpawnsAt = packed.nextMobSpawnsAt;
        this.totalMobsSpawned = packed.totalMobsSpawned;
        this.nextSpawnData = packed.nextSpawnData;
        this.ejectingLootTable = packed.ejectingLootTable;
    }

    public void reset() {
        this.currentMobs.clear();
        this.nextSpawnData = Optional.empty();
        this.resetStatistics();
    }

    public void resetStatistics() {
        this.detectedPlayers.clear();
        this.totalMobsSpawned = 0;
        this.nextMobSpawnsAt = 0L;
        this.cooldownEndsAt = 0L;
    }

    public boolean hasMobToSpawn(TrialSpawner trialSpawner, RandomSource random) {
        boolean flag = this.getOrCreateNextSpawnData(trialSpawner, random).getEntityToSpawn().getString("id").isPresent();

        return flag || !trialSpawner.activeConfig().spawnPotentialsDefinition().isEmpty();
    }

    public boolean hasFinishedSpawningAllMobs(TrialSpawnerConfig config, int additionalPlayers) {
        return this.totalMobsSpawned >= config.calculateTargetTotalMobs(additionalPlayers);
    }

    public boolean haveAllCurrentMobsDied() {
        return this.currentMobs.isEmpty();
    }

    public boolean isReadyToSpawnNextMob(ServerLevel serverLevel, TrialSpawnerConfig config, int additionalPlayers) {
        return serverLevel.getGameTime() >= this.nextMobSpawnsAt && this.currentMobs.size() < config.calculateTargetSimultaneousMobs(additionalPlayers);
    }

    public int countAdditionalPlayers(BlockPos pos) {
        if (this.detectedPlayers.isEmpty()) {
            Util.logAndPauseIfInIde("Trial Spawner at " + String.valueOf(pos) + " has no detected players");
        }

        return Math.max(0, this.detectedPlayers.size() - 1);
    }

    public void tryDetectPlayers(ServerLevel level, BlockPos pos, TrialSpawner trialSpawner) {
        boolean flag = (pos.asLong() + level.getGameTime()) % 20L != 0L;

        if (!flag) {
            if (!trialSpawner.getState().equals(TrialSpawnerState.COOLDOWN) || !trialSpawner.isOminous()) {
                List<UUID> list = trialSpawner.getPlayerDetector().detect(level, trialSpawner.getEntitySelector(), pos, (double) trialSpawner.getRequiredPlayerRange(), true);
                boolean flag1;

                if (!trialSpawner.isOminous() && !list.isEmpty()) {
                    Optional<Pair<Player, Holder<MobEffect>>> optional = findPlayerWithOminousEffect(level, list);

                    optional.ifPresent((pair) -> {
                        Player player = (Player) pair.getFirst();

                        if (pair.getSecond() == MobEffects.BAD_OMEN) {
                            transformBadOmenIntoTrialOmen(player);
                        }

                        level.levelEvent(3020, BlockPos.containing(player.getEyePosition()), 0);
                        trialSpawner.applyOminous(level, pos);
                    });
                    flag1 = optional.isPresent();
                } else {
                    flag1 = false;
                }

                if (!trialSpawner.getState().equals(TrialSpawnerState.COOLDOWN) || flag1) {
                    boolean flag2 = trialSpawner.getStateData().detectedPlayers.isEmpty();
                    List<UUID> list1 = flag2 ? list : trialSpawner.getPlayerDetector().detect(level, trialSpawner.getEntitySelector(), pos, (double) trialSpawner.getRequiredPlayerRange(), false);

                    if (this.detectedPlayers.addAll(list1)) {
                        this.nextMobSpawnsAt = Math.max(level.getGameTime() + 40L, this.nextMobSpawnsAt);
                        if (!flag1) {
                            int i = trialSpawner.isOminous() ? 3019 : 3013;

                            level.levelEvent(i, pos, this.detectedPlayers.size());
                        }
                    }

                }
            }
        }
    }

    private static Optional<Pair<Player, Holder<MobEffect>>> findPlayerWithOminousEffect(ServerLevel level, List<UUID> inLineOfSightPlayers) {
        Player player = null;

        for (UUID uuid : inLineOfSightPlayers) {
            Player player1 = level.getPlayerByUUID(uuid);

            if (player1 != null) {
                Holder<MobEffect> holder = MobEffects.TRIAL_OMEN;

                if (player1.hasEffect(holder)) {
                    return Optional.of(Pair.of(player1, holder));
                }

                if (player1.hasEffect(MobEffects.BAD_OMEN)) {
                    player = player1;
                }
            }
        }

        return Optional.ofNullable(player).map((player2) -> {
            return Pair.of(player2, MobEffects.BAD_OMEN);
        });
    }

    public void resetAfterBecomingOminous(TrialSpawner trialSpawner, ServerLevel level) {
        Stream stream = this.currentMobs.stream();

        Objects.requireNonNull(level);
        stream.map(level::getEntity).forEach((entity) -> {
            if (entity != null) {
                level.levelEvent(3012, entity.blockPosition(), TrialSpawner.FlameParticle.NORMAL.encode());
                if (entity instanceof Mob) {
                    Mob mob = (Mob) entity;

                    mob.dropPreservedEquipment(level);
                }

                entity.remove(Entity.RemovalReason.DISCARDED);
            }
        });
        if (!trialSpawner.ominousConfig().spawnPotentialsDefinition().isEmpty()) {
            this.nextSpawnData = Optional.empty();
        }

        this.totalMobsSpawned = 0;
        this.currentMobs.clear();
        this.nextMobSpawnsAt = level.getGameTime() + (long) trialSpawner.ominousConfig().ticksBetweenSpawn();
        trialSpawner.markUpdated();
        this.cooldownEndsAt = level.getGameTime() + trialSpawner.ominousConfig().ticksBetweenItemSpawners();
    }

    private static void transformBadOmenIntoTrialOmen(Player player) {
        MobEffectInstance mobeffectinstance = player.getEffect(MobEffects.BAD_OMEN);

        if (mobeffectinstance != null) {
            int i = mobeffectinstance.getAmplifier() + 1;
            int j = 18000 * i;

            player.removeEffect(MobEffects.BAD_OMEN);
            player.addEffect(new MobEffectInstance(MobEffects.TRIAL_OMEN, j, 0));
        }
    }

    public boolean isReadyToOpenShutter(ServerLevel serverLevel, float delayBeforeOpen, int targetCooldownLength) {
        long j = this.cooldownEndsAt - (long) targetCooldownLength;

        return (float) serverLevel.getGameTime() >= (float) j + delayBeforeOpen;
    }

    public boolean isReadyToEjectItems(ServerLevel serverLevel, float timeBetweenEjections, int targetCooldownLength) {
        long j = this.cooldownEndsAt - (long) targetCooldownLength;

        return (float) (serverLevel.getGameTime() - j) % timeBetweenEjections == 0.0F;
    }

    public boolean isCooldownFinished(ServerLevel serverLevel) {
        return serverLevel.getGameTime() >= this.cooldownEndsAt;
    }

    protected SpawnData getOrCreateNextSpawnData(TrialSpawner trialSpawner, RandomSource random) {
        if (this.nextSpawnData.isPresent()) {
            return (SpawnData) this.nextSpawnData.get();
        } else {
            WeightedList<SpawnData> weightedlist = trialSpawner.activeConfig().spawnPotentialsDefinition();
            Optional<SpawnData> optional = weightedlist.isEmpty() ? this.nextSpawnData : weightedlist.getRandom(random);

            this.nextSpawnData = Optional.of((SpawnData) optional.orElseGet(SpawnData::new));
            trialSpawner.markUpdated();
            return (SpawnData) this.nextSpawnData.get();
        }
    }

    public @Nullable Entity getOrCreateDisplayEntity(TrialSpawner trialSpawner, Level level, TrialSpawnerState state) {
        if (!state.hasSpinningMob()) {
            return null;
        } else {
            if (this.displayEntity == null) {
                CompoundTag compoundtag = this.getOrCreateNextSpawnData(trialSpawner, level.getRandom()).getEntityToSpawn();

                if (compoundtag.getString("id").isPresent()) {
                    this.displayEntity = EntityType.loadEntityRecursive(compoundtag, level, EntitySpawnReason.TRIAL_SPAWNER, EntityProcessor.NOP);
                }
            }

            return this.displayEntity;
        }
    }

    public CompoundTag getUpdateTag(TrialSpawnerState state) {
        CompoundTag compoundtag = new CompoundTag();

        if (state == TrialSpawnerState.ACTIVE) {
            compoundtag.putLong("next_mob_spawns_at", this.nextMobSpawnsAt);
        }

        this.nextSpawnData.ifPresent((spawndata) -> {
            compoundtag.store("spawn_data", SpawnData.CODEC, spawndata);
        });
        return compoundtag;
    }

    public double getSpin() {
        return this.spin;
    }

    public double getOSpin() {
        return this.oSpin;
    }

    WeightedList<ItemStack> getDispensingItems(ServerLevel level, TrialSpawnerConfig config, BlockPos pos) {
        if (this.dispensing != null) {
            return this.dispensing;
        } else {
            LootTable loottable = level.getServer().reloadableRegistries().getLootTable(config.itemsToDropWhenOminous());
            LootParams lootparams = (new LootParams.Builder(level)).create(LootContextParamSets.EMPTY);
            long i = lowResolutionPosition(level, pos);
            ObjectArrayList<ItemStack> objectarraylist = loottable.getRandomItems(lootparams, i);

            if (objectarraylist.isEmpty()) {
                return WeightedList.<ItemStack>of();
            } else {
                WeightedList.Builder<ItemStack> weightedlist_builder = WeightedList.<ItemStack>builder();
                ObjectListIterator objectlistiterator = objectarraylist.iterator();

                while (objectlistiterator.hasNext()) {
                    ItemStack itemstack = (ItemStack) objectlistiterator.next();

                    weightedlist_builder.add(itemstack.copyWithCount(1), itemstack.getCount());
                }

                this.dispensing = weightedlist_builder.build();
                return this.dispensing;
            }
        }
    }

    private static long lowResolutionPosition(ServerLevel level, BlockPos pos) {
        BlockPos blockpos1 = new BlockPos(Mth.floor((float) pos.getX() / 30.0F), Mth.floor((float) pos.getY() / 20.0F), Mth.floor((float) pos.getZ() / 30.0F));

        return level.getSeed() + blockpos1.asLong();
    }

    public static record Packed(Set<UUID> detectedPlayers, Set<UUID> currentMobs, long cooldownEndsAt, long nextMobSpawnsAt, int totalMobsSpawned, Optional<SpawnData> nextSpawnData, Optional<ResourceKey<LootTable>> ejectingLootTable) {

        public static final MapCodec<TrialSpawnerStateData.Packed> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(UUIDUtil.CODEC_SET.lenientOptionalFieldOf("registered_players", Set.of()).forGetter(TrialSpawnerStateData.Packed::detectedPlayers), UUIDUtil.CODEC_SET.lenientOptionalFieldOf("current_mobs", Set.of()).forGetter(TrialSpawnerStateData.Packed::currentMobs), Codec.LONG.lenientOptionalFieldOf("cooldown_ends_at", 0L).forGetter(TrialSpawnerStateData.Packed::cooldownEndsAt), Codec.LONG.lenientOptionalFieldOf("next_mob_spawns_at", 0L).forGetter(TrialSpawnerStateData.Packed::nextMobSpawnsAt), Codec.intRange(0, Integer.MAX_VALUE).lenientOptionalFieldOf("total_mobs_spawned", 0).forGetter(TrialSpawnerStateData.Packed::totalMobsSpawned), SpawnData.CODEC.lenientOptionalFieldOf("spawn_data").forGetter(TrialSpawnerStateData.Packed::nextSpawnData), LootTable.KEY_CODEC.lenientOptionalFieldOf("ejecting_loot_table").forGetter(TrialSpawnerStateData.Packed::ejectingLootTable)).apply(instance, TrialSpawnerStateData.Packed::new);
        });
    }
}
