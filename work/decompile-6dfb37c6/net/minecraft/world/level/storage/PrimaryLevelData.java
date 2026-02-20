package net.minecraft.world.level.storage;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.OptionalDynamic;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.CrashReportCategory;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.timers.TimerCallbacks;
import net.minecraft.world.level.timers.TimerQueue;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class PrimaryLevelData implements ServerLevelData, WorldData {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String LEVEL_NAME = "LevelName";
    protected static final String PLAYER = "Player";
    protected static final String WORLD_GEN_SETTINGS = "WorldGenSettings";
    public LevelSettings settings;
    private final WorldOptions worldOptions;
    private final PrimaryLevelData.SpecialWorldProperty specialWorldProperty;
    private final Lifecycle worldGenSettingsLifecycle;
    private LevelData.RespawnData respawnData;
    private long gameTime;
    private long dayTime;
    private final @Nullable CompoundTag loadedPlayerTag;
    private final int version;
    private int clearWeatherTime;
    private boolean raining;
    private int rainTime;
    private boolean thundering;
    private int thunderTime;
    private boolean initialized;
    private boolean difficultyLocked;
    /** @deprecated */
    @Deprecated
    private Optional<WorldBorder.Settings> legacyWorldBorderSettings;
    private EndDragonFight.Data endDragonFightData;
    private @Nullable CompoundTag customBossEvents;
    private int wanderingTraderSpawnDelay;
    private int wanderingTraderSpawnChance;
    private @Nullable UUID wanderingTraderId;
    private final Set<String> knownServerBrands;
    private boolean wasModded;
    private final Set<String> removedFeatureFlags;
    private final TimerQueue<MinecraftServer> scheduledEvents;

    private PrimaryLevelData(@Nullable CompoundTag loadedPlayerTag, boolean wasModded, LevelData.RespawnData respawnData, long gameTime, long dayTime, int version, int clearWeatherTime, int rainTime, boolean raining, int thunderTime, boolean thundering, boolean initialized, boolean difficultyLocked, Optional<WorldBorder.Settings> legacyWorldBorderSettings, int wanderingTraderSpawnDelay, int wanderingTraderSpawnChance, @Nullable UUID wanderingTraderId, Set<String> knownServerBrands, Set<String> removedFeatureFlags, TimerQueue<MinecraftServer> scheduledEvents, @Nullable CompoundTag customBossEvents, EndDragonFight.Data endDragonFightData, LevelSettings settings, WorldOptions worldOptions, PrimaryLevelData.SpecialWorldProperty specialWorldProperty, Lifecycle worldGenSettingsLifecycle) {
        this.wasModded = wasModded;
        this.respawnData = respawnData;
        this.gameTime = gameTime;
        this.dayTime = dayTime;
        this.version = version;
        this.clearWeatherTime = clearWeatherTime;
        this.rainTime = rainTime;
        this.raining = raining;
        this.thunderTime = thunderTime;
        this.thundering = thundering;
        this.initialized = initialized;
        this.difficultyLocked = difficultyLocked;
        this.legacyWorldBorderSettings = legacyWorldBorderSettings;
        this.wanderingTraderSpawnDelay = wanderingTraderSpawnDelay;
        this.wanderingTraderSpawnChance = wanderingTraderSpawnChance;
        this.wanderingTraderId = wanderingTraderId;
        this.knownServerBrands = knownServerBrands;
        this.removedFeatureFlags = removedFeatureFlags;
        this.loadedPlayerTag = loadedPlayerTag;
        this.scheduledEvents = scheduledEvents;
        this.customBossEvents = customBossEvents;
        this.endDragonFightData = endDragonFightData;
        this.settings = settings;
        this.worldOptions = worldOptions;
        this.specialWorldProperty = specialWorldProperty;
        this.worldGenSettingsLifecycle = worldGenSettingsLifecycle;
    }

    public PrimaryLevelData(LevelSettings levelSettings, WorldOptions worldOptions, PrimaryLevelData.SpecialWorldProperty specialWorldProperty, Lifecycle lifecycle) {
        this((CompoundTag) null, false, LevelData.RespawnData.DEFAULT, 0L, 0L, 19133, 0, 0, false, 0, false, false, false, Optional.empty(), 0, 0, (UUID) null, Sets.newLinkedHashSet(), new HashSet(), new TimerQueue(TimerCallbacks.SERVER_CALLBACKS), (CompoundTag) null, EndDragonFight.Data.DEFAULT, levelSettings.copy(), worldOptions, specialWorldProperty, lifecycle);
    }

    public static <T> PrimaryLevelData parse(Dynamic<T> input, LevelSettings settings, PrimaryLevelData.SpecialWorldProperty specialWorldProperty, WorldOptions worldOptions, Lifecycle worldGenSettingsLifecycle) {
        long i = input.get("Time").asLong(0L);
        OptionalDynamic optionaldynamic = input.get("Player");
        Codec codec = CompoundTag.CODEC;

        Objects.requireNonNull(codec);
        CompoundTag compoundtag = (CompoundTag) optionaldynamic.flatMap(codec::parse).result().orElse((Object) null);
        boolean flag = input.get("WasModded").asBoolean(false);
        LevelData.RespawnData leveldata_respawndata = (LevelData.RespawnData) input.get("spawn").read(LevelData.RespawnData.CODEC).result().orElse(LevelData.RespawnData.DEFAULT);
        long j = input.get("DayTime").asLong(i);
        int k = LevelVersion.parse(input).levelDataVersion();
        int l = input.get("clearWeatherTime").asInt(0);
        int i1 = input.get("rainTime").asInt(0);
        boolean flag1 = input.get("raining").asBoolean(false);
        int j1 = input.get("thunderTime").asInt(0);
        boolean flag2 = input.get("thundering").asBoolean(false);
        boolean flag3 = input.get("initialized").asBoolean(true);
        boolean flag4 = input.get("DifficultyLocked").asBoolean(false);
        Optional optional = WorldBorder.Settings.CODEC.parse(input.get("world_border").orElseEmptyMap()).result();
        int k1 = input.get("WanderingTraderSpawnDelay").asInt(0);
        int l1 = input.get("WanderingTraderSpawnChance").asInt(0);
        UUID uuid = (UUID) input.get("WanderingTraderId").read(UUIDUtil.CODEC).result().orElse((Object) null);
        Set set = (Set) input.get("ServerBrands").asStream().flatMap((dynamic1) -> {
            return dynamic1.asString().result().stream();
        }).collect(Collectors.toCollection(Sets::newLinkedHashSet));
        Set set1 = (Set) input.get("removed_features").asStream().flatMap((dynamic1) -> {
            return dynamic1.asString().result().stream();
        }).collect(Collectors.toSet());
        TimerQueue timerqueue = new TimerQueue(TimerCallbacks.SERVER_CALLBACKS, input.get("ScheduledEvents").asStream());
        CompoundTag compoundtag1 = (CompoundTag) input.get("CustomBossEvents").orElseEmptyMap().getValue();
        DataResult dataresult = input.get("DragonFight").read(EndDragonFight.Data.CODEC);
        Logger logger = PrimaryLevelData.LOGGER;

        Objects.requireNonNull(logger);
        return new PrimaryLevelData(compoundtag, flag, leveldata_respawndata, i, j, k, l, i1, flag1, j1, flag2, flag3, flag4, optional, k1, l1, uuid, set, set1, timerqueue, compoundtag1, (EndDragonFight.Data) dataresult.resultOrPartial(logger::error).orElse(EndDragonFight.Data.DEFAULT), settings, worldOptions, specialWorldProperty, worldGenSettingsLifecycle);
    }

    @Override
    public CompoundTag createTag(RegistryAccess registryAccess, @Nullable CompoundTag playerData) {
        if (playerData == null) {
            playerData = this.loadedPlayerTag;
        }

        CompoundTag compoundtag1 = new CompoundTag();

        this.setTagData(registryAccess, compoundtag1, playerData);
        return compoundtag1;
    }

    private void setTagData(RegistryAccess registryAccess, CompoundTag tag, @Nullable CompoundTag playerTag) {
        tag.put("ServerBrands", stringCollectionToTag(this.knownServerBrands));
        tag.putBoolean("WasModded", this.wasModded);
        if (!this.removedFeatureFlags.isEmpty()) {
            tag.put("removed_features", stringCollectionToTag(this.removedFeatureFlags));
        }

        CompoundTag compoundtag2 = new CompoundTag();

        compoundtag2.putString("Name", SharedConstants.getCurrentVersion().name());
        compoundtag2.putInt("Id", SharedConstants.getCurrentVersion().dataVersion().version());
        compoundtag2.putBoolean("Snapshot", !SharedConstants.getCurrentVersion().stable());
        compoundtag2.putString("Series", SharedConstants.getCurrentVersion().dataVersion().series());
        tag.put("Version", compoundtag2);
        NbtUtils.addCurrentDataVersion(tag);
        DynamicOps<Tag> dynamicops = registryAccess.<Tag>createSerializationContext(NbtOps.INSTANCE);
        DataResult dataresult = WorldGenSettings.encode(dynamicops, this.worldOptions, registryAccess);
        Logger logger = PrimaryLevelData.LOGGER;

        Objects.requireNonNull(logger);
        dataresult.resultOrPartial(Util.prefix("WorldGenSettings: ", logger::error)).ifPresent((tag1) -> {
            tag.put("WorldGenSettings", tag1);
        });
        tag.putInt("GameType", this.settings.gameType().getId());
        tag.store("spawn", LevelData.RespawnData.CODEC, this.respawnData);
        tag.putLong("Time", this.gameTime);
        tag.putLong("DayTime", this.dayTime);
        tag.putLong("LastPlayed", Util.getEpochMillis());
        tag.putString("LevelName", this.settings.levelName());
        tag.putInt("version", 19133);
        tag.putInt("clearWeatherTime", this.clearWeatherTime);
        tag.putInt("rainTime", this.rainTime);
        tag.putBoolean("raining", this.raining);
        tag.putInt("thunderTime", this.thunderTime);
        tag.putBoolean("thundering", this.thundering);
        tag.putBoolean("hardcore", this.settings.hardcore());
        tag.putBoolean("allowCommands", this.settings.allowCommands());
        tag.putBoolean("initialized", this.initialized);
        this.legacyWorldBorderSettings.ifPresent((worldborder_settings) -> {
            tag.store("world_border", WorldBorder.Settings.CODEC, worldborder_settings);
        });
        tag.putByte("Difficulty", (byte) this.settings.difficulty().getId());
        tag.putBoolean("DifficultyLocked", this.difficultyLocked);
        tag.store("game_rules", GameRules.codec(this.enabledFeatures()), this.settings.gameRules());
        tag.store("DragonFight", EndDragonFight.Data.CODEC, this.endDragonFightData);
        if (playerTag != null) {
            tag.put("Player", playerTag);
        }

        tag.store(WorldDataConfiguration.MAP_CODEC, this.settings.getDataConfiguration());
        if (this.customBossEvents != null) {
            tag.put("CustomBossEvents", this.customBossEvents);
        }

        tag.put("ScheduledEvents", this.scheduledEvents.store());
        tag.putInt("WanderingTraderSpawnDelay", this.wanderingTraderSpawnDelay);
        tag.putInt("WanderingTraderSpawnChance", this.wanderingTraderSpawnChance);
        tag.storeNullable("WanderingTraderId", UUIDUtil.CODEC, this.wanderingTraderId);
    }

    private static ListTag stringCollectionToTag(Set<String> values) {
        ListTag listtag = new ListTag();
        Stream stream = values.stream().map(StringTag::valueOf);

        Objects.requireNonNull(listtag);
        stream.forEach(listtag::add);
        return listtag;
    }

    @Override
    public LevelData.RespawnData getRespawnData() {
        return this.respawnData;
    }

    @Override
    public long getGameTime() {
        return this.gameTime;
    }

    @Override
    public long getDayTime() {
        return this.dayTime;
    }

    @Override
    public @Nullable CompoundTag getLoadedPlayerTag() {
        return this.loadedPlayerTag;
    }

    @Override
    public void setGameTime(long time) {
        this.gameTime = time;
    }

    @Override
    public void setDayTime(long time) {
        this.dayTime = time;
    }

    @Override
    public void setSpawn(LevelData.RespawnData respawnData) {
        this.respawnData = respawnData;
    }

    @Override
    public String getLevelName() {
        return this.settings.levelName();
    }

    @Override
    public int getVersion() {
        return this.version;
    }

    @Override
    public int getClearWeatherTime() {
        return this.clearWeatherTime;
    }

    @Override
    public void setClearWeatherTime(int clearWeatherTime) {
        this.clearWeatherTime = clearWeatherTime;
    }

    @Override
    public boolean isThundering() {
        return this.thundering;
    }

    @Override
    public void setThundering(boolean thundering) {
        this.thundering = thundering;
    }

    @Override
    public int getThunderTime() {
        return this.thunderTime;
    }

    @Override
    public void setThunderTime(int thunderTime) {
        this.thunderTime = thunderTime;
    }

    @Override
    public boolean isRaining() {
        return this.raining;
    }

    @Override
    public void setRaining(boolean raining) {
        this.raining = raining;
    }

    @Override
    public int getRainTime() {
        return this.rainTime;
    }

    @Override
    public void setRainTime(int rainTime) {
        this.rainTime = rainTime;
    }

    @Override
    public GameType getGameType() {
        return this.settings.gameType();
    }

    @Override
    public void setGameType(GameType gameType) {
        this.settings = this.settings.withGameType(gameType);
    }

    @Override
    public boolean isHardcore() {
        return this.settings.hardcore();
    }

    @Override
    public boolean isAllowCommands() {
        return this.settings.allowCommands();
    }

    @Override
    public boolean isInitialized() {
        return this.initialized;
    }

    @Override
    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    @Override
    public GameRules getGameRules() {
        return this.settings.gameRules();
    }

    @Override
    public Optional<WorldBorder.Settings> getLegacyWorldBorderSettings() {
        return this.legacyWorldBorderSettings;
    }

    @Override
    public void setLegacyWorldBorderSettings(Optional<WorldBorder.Settings> settings) {
        this.legacyWorldBorderSettings = settings;
    }

    @Override
    public Difficulty getDifficulty() {
        return this.settings.difficulty();
    }

    @Override
    public void setDifficulty(Difficulty difficulty) {
        this.settings = this.settings.withDifficulty(difficulty);
    }

    @Override
    public boolean isDifficultyLocked() {
        return this.difficultyLocked;
    }

    @Override
    public void setDifficultyLocked(boolean difficultyLocked) {
        this.difficultyLocked = difficultyLocked;
    }

    @Override
    public TimerQueue<MinecraftServer> getScheduledEvents() {
        return this.scheduledEvents;
    }

    @Override
    public void fillCrashReportCategory(CrashReportCategory category, LevelHeightAccessor levelHeightAccessor) {
        ServerLevelData.super.fillCrashReportCategory(category, levelHeightAccessor);
        WorldData.super.fillCrashReportCategory(category);
    }

    @Override
    public WorldOptions worldGenOptions() {
        return this.worldOptions;
    }

    @Override
    public boolean isFlatWorld() {
        return this.specialWorldProperty == PrimaryLevelData.SpecialWorldProperty.FLAT;
    }

    @Override
    public boolean isDebugWorld() {
        return this.specialWorldProperty == PrimaryLevelData.SpecialWorldProperty.DEBUG;
    }

    @Override
    public Lifecycle worldGenSettingsLifecycle() {
        return this.worldGenSettingsLifecycle;
    }

    @Override
    public EndDragonFight.Data endDragonFightData() {
        return this.endDragonFightData;
    }

    @Override
    public void setEndDragonFightData(EndDragonFight.Data data) {
        this.endDragonFightData = data;
    }

    @Override
    public WorldDataConfiguration getDataConfiguration() {
        return this.settings.getDataConfiguration();
    }

    @Override
    public void setDataConfiguration(WorldDataConfiguration dataConfiguration) {
        this.settings = this.settings.withDataConfiguration(dataConfiguration);
    }

    @Override
    public @Nullable CompoundTag getCustomBossEvents() {
        return this.customBossEvents;
    }

    @Override
    public void setCustomBossEvents(@Nullable CompoundTag customBossEvents) {
        this.customBossEvents = customBossEvents;
    }

    @Override
    public int getWanderingTraderSpawnDelay() {
        return this.wanderingTraderSpawnDelay;
    }

    @Override
    public void setWanderingTraderSpawnDelay(int wanderingTraderSpawnDelay) {
        this.wanderingTraderSpawnDelay = wanderingTraderSpawnDelay;
    }

    @Override
    public int getWanderingTraderSpawnChance() {
        return this.wanderingTraderSpawnChance;
    }

    @Override
    public void setWanderingTraderSpawnChance(int wanderingTraderSpawnChance) {
        this.wanderingTraderSpawnChance = wanderingTraderSpawnChance;
    }

    @Override
    public @Nullable UUID getWanderingTraderId() {
        return this.wanderingTraderId;
    }

    @Override
    public void setWanderingTraderId(UUID wanderingTraderId) {
        this.wanderingTraderId = wanderingTraderId;
    }

    @Override
    public void setModdedInfo(String serverBrand, boolean isModded) {
        this.knownServerBrands.add(serverBrand);
        this.wasModded |= isModded;
    }

    @Override
    public boolean wasModded() {
        return this.wasModded;
    }

    @Override
    public Set<String> getKnownServerBrands() {
        return ImmutableSet.copyOf(this.knownServerBrands);
    }

    @Override
    public Set<String> getRemovedFeatureFlags() {
        return Set.copyOf(this.removedFeatureFlags);
    }

    @Override
    public ServerLevelData overworldData() {
        return this;
    }

    @Override
    public LevelSettings getLevelSettings() {
        return this.settings.copy();
    }

    /** @deprecated */
    @Deprecated
    public static enum SpecialWorldProperty {

        NONE, FLAT, DEBUG;

        private SpecialWorldProperty() {}
    }
}
