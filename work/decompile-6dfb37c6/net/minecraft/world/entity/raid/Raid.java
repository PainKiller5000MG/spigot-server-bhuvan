package net.minecraft.world.entity.raid;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Raid {

    public static final SpawnPlacementType RAVAGER_SPAWN_PLACEMENT_TYPE = SpawnPlacements.getPlacementType(EntityType.RAVAGER);
    public static final MapCodec<Raid> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.BOOL.fieldOf("started").forGetter((raid) -> {
            return raid.started;
        }), Codec.BOOL.fieldOf("active").forGetter((raid) -> {
            return raid.active;
        }), Codec.LONG.fieldOf("ticks_active").forGetter((raid) -> {
            return raid.ticksActive;
        }), Codec.INT.fieldOf("raid_omen_level").forGetter((raid) -> {
            return raid.raidOmenLevel;
        }), Codec.INT.fieldOf("groups_spawned").forGetter((raid) -> {
            return raid.groupsSpawned;
        }), Codec.INT.fieldOf("cooldown_ticks").forGetter((raid) -> {
            return raid.raidCooldownTicks;
        }), Codec.INT.fieldOf("post_raid_ticks").forGetter((raid) -> {
            return raid.postRaidTicks;
        }), Codec.FLOAT.fieldOf("total_health").forGetter((raid) -> {
            return raid.totalHealth;
        }), Codec.INT.fieldOf("group_count").forGetter((raid) -> {
            return raid.numGroups;
        }), Raid.RaidStatus.CODEC.fieldOf("status").forGetter((raid) -> {
            return raid.status;
        }), BlockPos.CODEC.fieldOf("center").forGetter((raid) -> {
            return raid.center;
        }), UUIDUtil.CODEC_SET.fieldOf("heroes_of_the_village").forGetter((raid) -> {
            return raid.heroesOfTheVillage;
        })).apply(instance, Raid::new);
    });
    private static final int ALLOW_SPAWNING_WITHIN_VILLAGE_SECONDS_THRESHOLD = 7;
    private static final int SECTION_RADIUS_FOR_FINDING_NEW_VILLAGE_CENTER = 2;
    private static final int VILLAGE_SEARCH_RADIUS = 32;
    private static final int RAID_TIMEOUT_TICKS = 48000;
    private static final int NUM_SPAWN_ATTEMPTS = 5;
    private static final Component OMINOUS_BANNER_PATTERN_NAME = Component.translatable("block.minecraft.ominous_banner");
    private static final String RAIDERS_REMAINING = "event.minecraft.raid.raiders_remaining";
    public static final int VILLAGE_RADIUS_BUFFER = 16;
    private static final int POST_RAID_TICK_LIMIT = 40;
    private static final int DEFAULT_PRE_RAID_TICKS = 300;
    public static final int MAX_NO_ACTION_TIME = 2400;
    public static final int MAX_CELEBRATION_TICKS = 600;
    private static final int OUTSIDE_RAID_BOUNDS_TIMEOUT = 30;
    public static final int DEFAULT_MAX_RAID_OMEN_LEVEL = 5;
    private static final int LOW_MOB_THRESHOLD = 2;
    private static final Component RAID_NAME_COMPONENT = Component.translatable("event.minecraft.raid");
    private static final Component RAID_BAR_VICTORY_COMPONENT = Component.translatable("event.minecraft.raid.victory.full");
    private static final Component RAID_BAR_DEFEAT_COMPONENT = Component.translatable("event.minecraft.raid.defeat.full");
    private static final int HERO_OF_THE_VILLAGE_DURATION = 48000;
    private static final int VALID_RAID_RADIUS = 96;
    public static final int VALID_RAID_RADIUS_SQR = 9216;
    public static final int RAID_REMOVAL_THRESHOLD_SQR = 12544;
    private final Map<Integer, Raider> groupToLeaderMap = Maps.newHashMap();
    private final Map<Integer, Set<Raider>> groupRaiderMap = Maps.newHashMap();
    public final Set<UUID> heroesOfTheVillage = Sets.newHashSet();
    public long ticksActive;
    private BlockPos center;
    private boolean started;
    public float totalHealth;
    public int raidOmenLevel;
    private boolean active;
    private int groupsSpawned;
    private final ServerBossEvent raidEvent;
    private int postRaidTicks;
    private int raidCooldownTicks;
    private final RandomSource random;
    public final int numGroups;
    private Raid.RaidStatus status;
    private int celebrationTicks;
    private Optional<BlockPos> waveSpawnPos;

    public Raid(BlockPos center, Difficulty difficulty) {
        this.raidEvent = new ServerBossEvent(Raid.RAID_NAME_COMPONENT, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        this.random = RandomSource.create();
        this.waveSpawnPos = Optional.empty();
        this.active = true;
        this.raidCooldownTicks = 300;
        this.raidEvent.setProgress(0.0F);
        this.center = center;
        this.numGroups = this.getNumGroups(difficulty);
        this.status = Raid.RaidStatus.ONGOING;
    }

    private Raid(boolean started, boolean active, long ticksActive, int raidOmenLevel, int groupsSpawned, int raidCooldownTicks, int postRaidTicks, float totalHealth, int numGroups, Raid.RaidStatus status, BlockPos center, Set<UUID> heroesOfTheVillage) {
        this.raidEvent = new ServerBossEvent(Raid.RAID_NAME_COMPONENT, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        this.random = RandomSource.create();
        this.waveSpawnPos = Optional.empty();
        this.started = started;
        this.active = active;
        this.ticksActive = ticksActive;
        this.raidOmenLevel = raidOmenLevel;
        this.groupsSpawned = groupsSpawned;
        this.raidCooldownTicks = raidCooldownTicks;
        this.postRaidTicks = postRaidTicks;
        this.totalHealth = totalHealth;
        this.center = center;
        this.numGroups = numGroups;
        this.status = status;
        this.heroesOfTheVillage.addAll(heroesOfTheVillage);
    }

    public boolean isOver() {
        return this.isVictory() || this.isLoss();
    }

    public boolean isBetweenWaves() {
        return this.hasFirstWaveSpawned() && this.getTotalRaidersAlive() == 0 && this.raidCooldownTicks > 0;
    }

    public boolean hasFirstWaveSpawned() {
        return this.groupsSpawned > 0;
    }

    public boolean isStopped() {
        return this.status == Raid.RaidStatus.STOPPED;
    }

    public boolean isVictory() {
        return this.status == Raid.RaidStatus.VICTORY;
    }

    public boolean isLoss() {
        return this.status == Raid.RaidStatus.LOSS;
    }

    public float getTotalHealth() {
        return this.totalHealth;
    }

    public Set<Raider> getAllRaiders() {
        Set<Raider> set = Sets.newHashSet();

        for (Set<Raider> set1 : this.groupRaiderMap.values()) {
            set.addAll(set1);
        }

        return set;
    }

    public boolean isStarted() {
        return this.started;
    }

    public int getGroupsSpawned() {
        return this.groupsSpawned;
    }

    private Predicate<ServerPlayer> validPlayer() {
        return (serverplayer) -> {
            BlockPos blockpos = serverplayer.blockPosition();

            return serverplayer.isAlive() && serverplayer.level().getRaidAt(blockpos) == this;
        };
    }

    private void updatePlayers(ServerLevel level) {
        Set<ServerPlayer> set = Sets.newHashSet(this.raidEvent.getPlayers());
        List<ServerPlayer> list = level.getPlayers(this.validPlayer());

        for (ServerPlayer serverplayer : list) {
            if (!set.contains(serverplayer)) {
                this.raidEvent.addPlayer(serverplayer);
            }
        }

        for (ServerPlayer serverplayer1 : set) {
            if (!list.contains(serverplayer1)) {
                this.raidEvent.removePlayer(serverplayer1);
            }
        }

    }

    public int getMaxRaidOmenLevel() {
        return 5;
    }

    public int getRaidOmenLevel() {
        return this.raidOmenLevel;
    }

    public void setRaidOmenLevel(int raidOmenLevel) {
        this.raidOmenLevel = raidOmenLevel;
    }

    public boolean absorbRaidOmen(ServerPlayer player) {
        MobEffectInstance mobeffectinstance = player.getEffect(MobEffects.RAID_OMEN);

        if (mobeffectinstance == null) {
            return false;
        } else {
            this.raidOmenLevel += mobeffectinstance.getAmplifier() + 1;
            this.raidOmenLevel = Mth.clamp(this.raidOmenLevel, 0, this.getMaxRaidOmenLevel());
            if (!this.hasFirstWaveSpawned()) {
                player.awardStat(Stats.RAID_TRIGGER);
                CriteriaTriggers.RAID_OMEN.trigger(player);
            }

            return true;
        }
    }

    public void stop() {
        this.active = false;
        this.raidEvent.removeAllPlayers();
        this.status = Raid.RaidStatus.STOPPED;
    }

    public void tick(ServerLevel level) {
        if (!this.isStopped()) {
            if (this.status == Raid.RaidStatus.ONGOING) {
                boolean flag = this.active;

                this.active = level.hasChunkAt(this.center);
                if (level.getDifficulty() == Difficulty.PEACEFUL) {
                    this.stop();
                    return;
                }

                if (flag != this.active) {
                    this.raidEvent.setVisible(this.active);
                }

                if (!this.active) {
                    return;
                }

                if (!level.isVillage(this.center)) {
                    this.moveRaidCenterToNearbyVillageSection(level);
                }

                if (!level.isVillage(this.center)) {
                    if (this.groupsSpawned > 0) {
                        this.status = Raid.RaidStatus.LOSS;
                    } else {
                        this.stop();
                    }
                }

                ++this.ticksActive;
                if (this.ticksActive >= 48000L) {
                    this.stop();
                    return;
                }

                int i = this.getTotalRaidersAlive();

                if (i == 0 && this.hasMoreWaves()) {
                    if (this.raidCooldownTicks <= 0) {
                        if (this.raidCooldownTicks == 0 && this.groupsSpawned > 0) {
                            this.raidCooldownTicks = 300;
                            this.raidEvent.setName(Raid.RAID_NAME_COMPONENT);
                            return;
                        }
                    } else {
                        boolean flag1 = this.waveSpawnPos.isPresent();
                        boolean flag2 = !flag1 && this.raidCooldownTicks % 5 == 0;

                        if (flag1 && !level.isPositionEntityTicking((BlockPos) this.waveSpawnPos.get())) {
                            flag2 = true;
                        }

                        if (flag2) {
                            this.waveSpawnPos = this.getValidSpawnPos(level);
                        }

                        if (this.raidCooldownTicks == 300 || this.raidCooldownTicks % 20 == 0) {
                            this.updatePlayers(level);
                        }

                        --this.raidCooldownTicks;
                        this.raidEvent.setProgress(Mth.clamp((float) (300 - this.raidCooldownTicks) / 300.0F, 0.0F, 1.0F));
                    }
                }

                if (this.ticksActive % 20L == 0L) {
                    this.updatePlayers(level);
                    this.updateRaiders(level);
                    if (i > 0) {
                        if (i <= 2) {
                            this.raidEvent.setName(Raid.RAID_NAME_COMPONENT.copy().append(" - ").append((Component) Component.translatable("event.minecraft.raid.raiders_remaining", i)));
                        } else {
                            this.raidEvent.setName(Raid.RAID_NAME_COMPONENT);
                        }
                    } else {
                        this.raidEvent.setName(Raid.RAID_NAME_COMPONENT);
                    }
                }

                if (SharedConstants.DEBUG_RAIDS) {
                    ServerBossEvent serverbossevent = this.raidEvent;
                    MutableComponent mutablecomponent = Raid.RAID_NAME_COMPONENT.copy().append(" wave: ").append("" + this.groupsSpawned).append(CommonComponents.SPACE).append("Raiders alive: ").append("" + this.getTotalRaidersAlive()).append(CommonComponents.SPACE).append("" + this.getHealthOfLivingRaiders()).append(" / ").append("" + this.totalHealth).append(" Is bonus? ");
                    boolean flag3 = this.hasBonusWave() && this.hasSpawnedBonusWave();

                    serverbossevent.setName(mutablecomponent.append("" + flag3).append(" Status: ").append(this.status.getSerializedName()));
                }

                boolean flag4 = false;
                int j = 0;

                while (this.shouldSpawnGroup()) {
                    BlockPos blockpos = (BlockPos) this.waveSpawnPos.orElseGet(() -> {
                        return this.findRandomSpawnPos(level, 20);
                    });

                    if (blockpos != null) {
                        this.started = true;
                        this.spawnGroup(level, blockpos);
                        if (!flag4) {
                            this.playSound(level, blockpos);
                            flag4 = true;
                        }
                    } else {
                        ++j;
                    }

                    if (j > 5) {
                        this.stop();
                        break;
                    }
                }

                if (this.isStarted() && !this.hasMoreWaves() && i == 0) {
                    if (this.postRaidTicks < 40) {
                        ++this.postRaidTicks;
                    } else {
                        this.status = Raid.RaidStatus.VICTORY;

                        for (UUID uuid : this.heroesOfTheVillage) {
                            Entity entity = level.getEntity(uuid);

                            if (entity instanceof LivingEntity) {
                                LivingEntity livingentity = (LivingEntity) entity;

                                if (!entity.isSpectator()) {
                                    livingentity.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 48000, this.raidOmenLevel - 1, false, false, true));
                                    if (livingentity instanceof ServerPlayer) {
                                        ServerPlayer serverplayer = (ServerPlayer) livingentity;

                                        serverplayer.awardStat(Stats.RAID_WIN);
                                        CriteriaTriggers.RAID_WIN.trigger(serverplayer);
                                    }
                                }
                            }
                        }
                    }
                }

                this.setDirty(level);
            } else if (this.isOver()) {
                ++this.celebrationTicks;
                if (this.celebrationTicks >= 600) {
                    this.stop();
                    return;
                }

                if (this.celebrationTicks % 20 == 0) {
                    this.updatePlayers(level);
                    this.raidEvent.setVisible(true);
                    if (this.isVictory()) {
                        this.raidEvent.setProgress(0.0F);
                        this.raidEvent.setName(Raid.RAID_BAR_VICTORY_COMPONENT);
                    } else {
                        this.raidEvent.setName(Raid.RAID_BAR_DEFEAT_COMPONENT);
                    }
                }
            }

        }
    }

    private void moveRaidCenterToNearbyVillageSection(ServerLevel level) {
        Stream<SectionPos> stream = SectionPos.cube(SectionPos.of(this.center), 2);

        Objects.requireNonNull(level);
        stream.filter(level::isVillage).map(SectionPos::center).min(Comparator.comparingDouble((blockpos) -> {
            return blockpos.distSqr(this.center);
        })).ifPresent(this::setCenter);
    }

    private Optional<BlockPos> getValidSpawnPos(ServerLevel level) {
        BlockPos blockpos = this.findRandomSpawnPos(level, 8);

        return blockpos != null ? Optional.of(blockpos) : Optional.empty();
    }

    private boolean hasMoreWaves() {
        return this.hasBonusWave() ? !this.hasSpawnedBonusWave() : !this.isFinalWave();
    }

    private boolean isFinalWave() {
        return this.getGroupsSpawned() == this.numGroups;
    }

    private boolean hasBonusWave() {
        return this.raidOmenLevel > 1;
    }

    private boolean hasSpawnedBonusWave() {
        return this.getGroupsSpawned() > this.numGroups;
    }

    private boolean shouldSpawnBonusGroup() {
        return this.isFinalWave() && this.getTotalRaidersAlive() == 0 && this.hasBonusWave();
    }

    private void updateRaiders(ServerLevel level) {
        Iterator<Set<Raider>> iterator = this.groupRaiderMap.values().iterator();
        Set<Raider> set = Sets.newHashSet();

        while (iterator.hasNext()) {
            Set<Raider> set1 = (Set) iterator.next();

            for (Raider raider : set1) {
                BlockPos blockpos = raider.blockPosition();

                if (!raider.isRemoved() && raider.level().dimension() == level.dimension() && this.center.distSqr(blockpos) < 12544.0D) {
                    if (raider.tickCount > 600) {
                        if (level.getEntity(raider.getUUID()) == null) {
                            set.add(raider);
                        }

                        if (!level.isVillage(blockpos) && raider.getNoActionTime() > 2400) {
                            raider.setTicksOutsideRaid(raider.getTicksOutsideRaid() + 1);
                        }

                        if (raider.getTicksOutsideRaid() >= 30) {
                            set.add(raider);
                        }
                    }
                } else {
                    set.add(raider);
                }
            }
        }

        for (Raider raider1 : set) {
            this.removeFromRaid(level, raider1, true);
            if (raider1.isPatrolLeader()) {
                this.removeLeader(raider1.getWave());
            }
        }

    }

    private void playSound(ServerLevel level, BlockPos soundOrigin) {
        float f = 13.0F;
        int i = 64;
        Collection<ServerPlayer> collection = this.raidEvent.getPlayers();
        long j = this.random.nextLong();

        for (ServerPlayer serverplayer : level.players()) {
            Vec3 vec3 = serverplayer.position();
            Vec3 vec31 = Vec3.atCenterOf(soundOrigin);
            double d0 = Math.sqrt((vec31.x - vec3.x) * (vec31.x - vec3.x) + (vec31.z - vec3.z) * (vec31.z - vec3.z));
            double d1 = vec3.x + 13.0D / d0 * (vec31.x - vec3.x);
            double d2 = vec3.z + 13.0D / d0 * (vec31.z - vec3.z);

            if (d0 <= 64.0D || collection.contains(serverplayer)) {
                serverplayer.connection.send(new ClientboundSoundPacket(SoundEvents.RAID_HORN, SoundSource.NEUTRAL, d1, serverplayer.getY(), d2, 64.0F, 1.0F, j));
            }
        }

    }

    private void spawnGroup(ServerLevel level, BlockPos pos) {
        boolean flag = false;
        int i = this.groupsSpawned + 1;

        this.totalHealth = 0.0F;
        DifficultyInstance difficultyinstance = level.getCurrentDifficultyAt(pos);
        boolean flag1 = this.shouldSpawnBonusGroup();

        for (Raid.RaiderType raid_raidertype : Raid.RaiderType.VALUES) {
            int j = this.getDefaultNumSpawns(raid_raidertype, i, flag1) + this.getPotentialBonusSpawns(raid_raidertype, this.random, i, difficultyinstance, flag1);
            int k = 0;

            for (int l = 0; l < j; ++l) {
                Raider raider = raid_raidertype.entityType.create(level, EntitySpawnReason.EVENT);

                if (raider == null) {
                    break;
                }

                if (!flag && raider.canBeLeader()) {
                    raider.setPatrolLeader(true);
                    this.setLeader(i, raider);
                    flag = true;
                }

                this.joinRaid(level, i, raider, pos, false);
                if (raid_raidertype.entityType == EntityType.RAVAGER) {
                    Raider raider1 = null;

                    if (i == this.getNumGroups(Difficulty.NORMAL)) {
                        raider1 = EntityType.PILLAGER.create(level, EntitySpawnReason.EVENT);
                    } else if (i >= this.getNumGroups(Difficulty.HARD)) {
                        if (k == 0) {
                            raider1 = EntityType.EVOKER.create(level, EntitySpawnReason.EVENT);
                        } else {
                            raider1 = EntityType.VINDICATOR.create(level, EntitySpawnReason.EVENT);
                        }
                    }

                    ++k;
                    if (raider1 != null) {
                        this.joinRaid(level, i, raider1, pos, false);
                        raider1.snapTo(pos, 0.0F, 0.0F);
                        raider1.startRiding(raider, false, false);
                    }
                }
            }
        }

        this.waveSpawnPos = Optional.empty();
        ++this.groupsSpawned;
        this.updateBossbar();
        this.setDirty(level);
    }

    public void joinRaid(ServerLevel level, int groupNumber, Raider raider, @Nullable BlockPos pos, boolean exists) {
        boolean flag1 = this.addWaveMob(level, groupNumber, raider);

        if (flag1) {
            raider.setCurrentRaid(this);
            raider.setWave(groupNumber);
            raider.setCanJoinRaid(true);
            raider.setTicksOutsideRaid(0);
            if (!exists && pos != null) {
                raider.setPos((double) pos.getX() + 0.5D, (double) pos.getY() + 1.0D, (double) pos.getZ() + 0.5D);
                raider.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, (SpawnGroupData) null);
                raider.applyRaidBuffs(level, groupNumber, false);
                raider.setOnGround(true);
                level.addFreshEntityWithPassengers(raider);
            }
        }

    }

    public void updateBossbar() {
        this.raidEvent.setProgress(Mth.clamp(this.getHealthOfLivingRaiders() / this.totalHealth, 0.0F, 1.0F));
    }

    public float getHealthOfLivingRaiders() {
        float f = 0.0F;

        for (Set<Raider> set : this.groupRaiderMap.values()) {
            for (Raider raider : set) {
                f += raider.getHealth();
            }
        }

        return f;
    }

    private boolean shouldSpawnGroup() {
        return this.raidCooldownTicks == 0 && (this.groupsSpawned < this.numGroups || this.shouldSpawnBonusGroup()) && this.getTotalRaidersAlive() == 0;
    }

    public int getTotalRaidersAlive() {
        return this.groupRaiderMap.values().stream().mapToInt(Set::size).sum();
    }

    public void removeFromRaid(ServerLevel level, Raider raider, boolean removeFromTotalHealth) {
        Set<Raider> set = (Set) this.groupRaiderMap.get(raider.getWave());

        if (set != null) {
            boolean flag1 = set.remove(raider);

            if (flag1) {
                if (removeFromTotalHealth) {
                    this.totalHealth -= raider.getHealth();
                }

                raider.setCurrentRaid((Raid) null);
                this.updateBossbar();
                this.setDirty(level);
            }
        }

    }

    private void setDirty(ServerLevel level) {
        level.getRaids().setDirty();
    }

    public static ItemStack getOminousBannerInstance(HolderGetter<BannerPattern> patternGetter) {
        ItemStack itemstack = new ItemStack(Items.WHITE_BANNER);
        BannerPatternLayers bannerpatternlayers = (new BannerPatternLayers.Builder()).addIfRegistered(patternGetter, BannerPatterns.RHOMBUS_MIDDLE, DyeColor.CYAN).addIfRegistered(patternGetter, BannerPatterns.STRIPE_BOTTOM, DyeColor.LIGHT_GRAY).addIfRegistered(patternGetter, BannerPatterns.STRIPE_CENTER, DyeColor.GRAY).addIfRegistered(patternGetter, BannerPatterns.BORDER, DyeColor.LIGHT_GRAY).addIfRegistered(patternGetter, BannerPatterns.STRIPE_MIDDLE, DyeColor.BLACK).addIfRegistered(patternGetter, BannerPatterns.HALF_HORIZONTAL, DyeColor.LIGHT_GRAY).addIfRegistered(patternGetter, BannerPatterns.CIRCLE_MIDDLE, DyeColor.LIGHT_GRAY).addIfRegistered(patternGetter, BannerPatterns.BORDER, DyeColor.BLACK).build();

        itemstack.set(DataComponents.BANNER_PATTERNS, bannerpatternlayers);
        itemstack.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.BANNER_PATTERNS, true));
        itemstack.set(DataComponents.ITEM_NAME, Raid.OMINOUS_BANNER_PATTERN_NAME);
        itemstack.set(DataComponents.RARITY, Rarity.UNCOMMON);
        return itemstack;
    }

    public @Nullable Raider getLeader(int wave) {
        return (Raider) this.groupToLeaderMap.get(wave);
    }

    private @Nullable BlockPos findRandomSpawnPos(ServerLevel level, int maxTries) {
        int j = this.raidCooldownTicks / 20;
        float f = 0.22F * (float) j - 0.24F;
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
        float f1 = level.random.nextFloat() * ((float) Math.PI * 2F);

        for (int k = 0; k < maxTries; ++k) {
            float f2 = f1 + (float) Math.PI * (float) k / 8.0F;
            int l = this.center.getX() + Mth.floor(Mth.cos((double) f2) * 32.0F * f) + level.random.nextInt(3) * Mth.floor(f);
            int i1 = this.center.getZ() + Mth.floor(Mth.sin((double) f2) * 32.0F * f) + level.random.nextInt(3) * Mth.floor(f);
            int j1 = level.getHeight(Heightmap.Types.WORLD_SURFACE, l, i1);

            if (Mth.abs(j1 - this.center.getY()) <= 96) {
                blockpos_mutableblockpos.set(l, j1, i1);
                if (!level.isVillage((BlockPos) blockpos_mutableblockpos) || j <= 7) {
                    int k1 = 10;

                    if (level.hasChunksAt(blockpos_mutableblockpos.getX() - 10, blockpos_mutableblockpos.getZ() - 10, blockpos_mutableblockpos.getX() + 10, blockpos_mutableblockpos.getZ() + 10) && level.isPositionEntityTicking(blockpos_mutableblockpos) && (Raid.RAVAGER_SPAWN_PLACEMENT_TYPE.isSpawnPositionOk(level, blockpos_mutableblockpos, EntityType.RAVAGER) || level.getBlockState(blockpos_mutableblockpos.below()).is(Blocks.SNOW) && level.getBlockState(blockpos_mutableblockpos).isAir())) {
                        return blockpos_mutableblockpos;
                    }
                }
            }
        }

        return null;
    }

    private boolean addWaveMob(ServerLevel level, int wave, Raider raider) {
        return this.addWaveMob(level, wave, raider, true);
    }

    public boolean addWaveMob(ServerLevel level, int wave, Raider raider, boolean updateHealth) {
        this.groupRaiderMap.computeIfAbsent(wave, (integer) -> {
            return Sets.newHashSet();
        });
        Set<Raider> set = (Set) this.groupRaiderMap.get(wave);
        Raider raider1 = null;

        for (Raider raider2 : set) {
            if (raider2.getUUID().equals(raider.getUUID())) {
                raider1 = raider2;
                break;
            }
        }

        if (raider1 != null) {
            set.remove(raider1);
            set.add(raider);
        }

        set.add(raider);
        if (updateHealth) {
            this.totalHealth += raider.getHealth();
        }

        this.updateBossbar();
        this.setDirty(level);
        return true;
    }

    public void setLeader(int wave, Raider raider) {
        this.groupToLeaderMap.put(wave, raider);
        raider.setItemSlot(EquipmentSlot.HEAD, getOminousBannerInstance(raider.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
        raider.setDropChance(EquipmentSlot.HEAD, 2.0F);
    }

    public void removeLeader(int wave) {
        this.groupToLeaderMap.remove(wave);
    }

    public BlockPos getCenter() {
        return this.center;
    }

    private void setCenter(BlockPos center) {
        this.center = center;
    }

    private int getDefaultNumSpawns(Raid.RaiderType type, int wav, boolean isBonusWave) {
        return isBonusWave ? type.spawnsPerWaveBeforeBonus[this.numGroups] : type.spawnsPerWaveBeforeBonus[wav];
    }

    private int getPotentialBonusSpawns(Raid.RaiderType type, RandomSource random, int wav, DifficultyInstance difficultyInstance, boolean isBonusWave) {
        Difficulty difficulty = difficultyInstance.getDifficulty();
        boolean flag1 = difficulty == Difficulty.EASY;
        boolean flag2 = difficulty == Difficulty.NORMAL;
        int j;

        switch (type.ordinal()) {
            case 0:
            case 2:
                if (flag1) {
                    j = random.nextInt(2);
                } else if (flag2) {
                    j = 1;
                } else {
                    j = 2;
                }
                break;
            case 1:
            default:
                return 0;
            case 3:
                if (flag1 || wav <= 2 || wav == 4) {
                    return 0;
                }

                j = 1;
                break;
            case 4:
                j = !flag1 && isBonusWave ? 1 : 0;
        }

        return j > 0 ? random.nextInt(j + 1) : 0;
    }

    public boolean isActive() {
        return this.active;
    }

    public int getNumGroups(Difficulty difficulty) {
        byte b0;

        switch (difficulty) {
            case PEACEFUL:
                b0 = 0;
                break;
            case EASY:
                b0 = 3;
                break;
            case NORMAL:
                b0 = 5;
                break;
            case HARD:
                b0 = 7;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return b0;
    }

    public float getEnchantOdds() {
        int i = this.getRaidOmenLevel();

        return i == 2 ? 0.1F : (i == 3 ? 0.25F : (i == 4 ? 0.5F : (i == 5 ? 0.75F : 0.0F)));
    }

    public void addHeroOfTheVillage(Entity killer) {
        this.heroesOfTheVillage.add(killer.getUUID());
    }

    private static enum RaidStatus implements StringRepresentable {

        ONGOING("ongoing"), VICTORY("victory"), LOSS("loss"), STOPPED("stopped");

        public static final Codec<Raid.RaidStatus> CODEC = StringRepresentable.<Raid.RaidStatus>fromEnum(Raid.RaidStatus::values);
        private final String name;

        private RaidStatus(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    private static enum RaiderType {

        VINDICATOR(EntityType.VINDICATOR, new int[]{0, 0, 2, 0, 1, 4, 2, 5}), EVOKER(EntityType.EVOKER, new int[]{0, 0, 0, 0, 0, 1, 1, 2}), PILLAGER(EntityType.PILLAGER, new int[]{0, 4, 3, 3, 4, 4, 4, 2}), WITCH(EntityType.WITCH, new int[]{0, 0, 0, 0, 3, 0, 0, 1}), RAVAGER(EntityType.RAVAGER, new int[]{0, 0, 0, 1, 0, 1, 0, 2});

        private static final Raid.RaiderType[] VALUES = values();
        private final EntityType<? extends Raider> entityType;
        private final int[] spawnsPerWaveBeforeBonus;

        private RaiderType(EntityType<? extends Raider> entityType, int[] spawnsPerWaveBeforeBonus) {
            this.entityType = entityType;
            this.spawnsPerWaveBeforeBonus = spawnsPerWaveBeforeBonus;
        }
    }
}
