package net.minecraft.world.level.dimension.end;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.EndFeatures;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.block.state.pattern.BlockPatternBuilder;
import net.minecraft.world.level.block.state.predicate.BlockPredicate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class EndDragonFight {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_TICKS_BEFORE_DRAGON_RESPAWN = 1200;
    private static final int TIME_BETWEEN_CRYSTAL_SCANS = 100;
    public static final int TIME_BETWEEN_PLAYER_SCANS = 20;
    private static final int ARENA_SIZE_CHUNKS = 8;
    public static final int ARENA_TICKET_LEVEL = 9;
    private static final int GATEWAY_COUNT = 20;
    private static final int GATEWAY_DISTANCE = 96;
    public static final int DRAGON_SPAWN_Y = 128;
    private final Predicate<Entity> validPlayer;
    public final ServerBossEvent dragonEvent;
    public final ServerLevel level;
    private final BlockPos origin;
    private final ObjectArrayList<Integer> gateways;
    private final BlockPattern exitPortalPattern;
    private int ticksSinceDragonSeen;
    private int crystalsAlive;
    private int ticksSinceCrystalsScanned;
    private int ticksSinceLastPlayerScan;
    private boolean dragonKilled;
    public boolean previouslyKilled;
    private boolean skipArenaLoadedCheck;
    public @Nullable UUID dragonUUID;
    private boolean needsStateScanning;
    public @Nullable BlockPos portalLocation;
    public @Nullable DragonRespawnAnimation respawnStage;
    private int respawnTime;
    private @Nullable List<EndCrystal> respawnCrystals;

    public EndDragonFight(ServerLevel level, long seed, EndDragonFight.Data dragonFightData) {
        this(level, seed, dragonFightData, BlockPos.ZERO);
    }

    public EndDragonFight(ServerLevel level, long seed, EndDragonFight.Data dragonFightData, BlockPos origin) {
        this.dragonEvent = (ServerBossEvent) (new ServerBossEvent(Component.translatable("entity.minecraft.ender_dragon"), BossEvent.BossBarColor.PINK, BossEvent.BossBarOverlay.PROGRESS)).setPlayBossMusic(true).setCreateWorldFog(true);
        this.gateways = new ObjectArrayList();
        this.ticksSinceLastPlayerScan = 21;
        this.skipArenaLoadedCheck = false;
        this.needsStateScanning = true;
        this.level = level;
        this.origin = origin;
        this.validPlayer = EntitySelector.ENTITY_STILL_ALIVE.and(EntitySelector.withinDistance((double) origin.getX(), (double) (128 + origin.getY()), (double) origin.getZ(), 192.0D));
        this.needsStateScanning = dragonFightData.needsStateScanning;
        this.dragonUUID = (UUID) dragonFightData.dragonUUID.orElse((Object) null);
        this.dragonKilled = dragonFightData.dragonKilled;
        this.previouslyKilled = dragonFightData.previouslyKilled;
        if (dragonFightData.isRespawning) {
            this.respawnStage = DragonRespawnAnimation.START;
        }

        this.portalLocation = (BlockPos) dragonFightData.exitPortalLocation.orElse((Object) null);
        this.gateways.addAll((Collection) dragonFightData.gateways.orElseGet(() -> {
            ObjectArrayList<Integer> objectarraylist = new ObjectArrayList(ContiguousSet.create(Range.closedOpen(0, 20), DiscreteDomain.integers()));

            Util.shuffle(objectarraylist, RandomSource.create(seed));
            return objectarraylist;
        }));
        this.exitPortalPattern = BlockPatternBuilder.start().aisle("       ", "       ", "       ", "   #   ", "       ", "       ", "       ").aisle("       ", "       ", "       ", "   #   ", "       ", "       ", "       ").aisle("       ", "       ", "       ", "   #   ", "       ", "       ", "       ").aisle("  ###  ", " #   # ", "#     #", "#  #  #", "#     #", " #   # ", "  ###  ").aisle("       ", "  ###  ", " ##### ", " ##### ", " ##### ", "  ###  ", "       ").where('#', BlockInWorld.hasState(BlockPredicate.forBlock(Blocks.BEDROCK))).build();
    }

    /** @deprecated */
    @Deprecated
    @VisibleForTesting
    public void skipArenaLoadedCheck() {
        this.skipArenaLoadedCheck = true;
    }

    public EndDragonFight.Data saveData() {
        return new EndDragonFight.Data(this.needsStateScanning, this.dragonKilled, this.previouslyKilled, false, Optional.ofNullable(this.dragonUUID), Optional.ofNullable(this.portalLocation), Optional.of(this.gateways));
    }

    public void tick() {
        this.dragonEvent.setVisible(!this.dragonKilled);
        if (++this.ticksSinceLastPlayerScan >= 20) {
            this.updatePlayers();
            this.ticksSinceLastPlayerScan = 0;
        }

        if (!this.dragonEvent.getPlayers().isEmpty()) {
            this.level.getChunkSource().addTicketWithRadius(TicketType.DRAGON, new ChunkPos(0, 0), 9);
            boolean flag = this.isArenaLoaded();

            if (this.needsStateScanning && flag) {
                this.scanState();
                this.needsStateScanning = false;
            }

            if (this.respawnStage != null) {
                if (this.respawnCrystals == null && flag) {
                    this.respawnStage = null;
                    this.tryRespawn();
                }

                this.respawnStage.tick(this.level, this, this.respawnCrystals, this.respawnTime++, this.portalLocation);
            }

            if (!this.dragonKilled) {
                if ((this.dragonUUID == null || ++this.ticksSinceDragonSeen >= 1200) && flag) {
                    this.findOrCreateDragon();
                    this.ticksSinceDragonSeen = 0;
                }

                if (++this.ticksSinceCrystalsScanned >= 100 && flag) {
                    this.updateCrystalCount();
                    this.ticksSinceCrystalsScanned = 0;
                }
            }
        } else {
            this.level.getChunkSource().removeTicketWithRadius(TicketType.DRAGON, new ChunkPos(0, 0), 9);
        }

    }

    private void scanState() {
        EndDragonFight.LOGGER.info("Scanning for legacy world dragon fight...");
        boolean flag = this.hasActiveExitPortal();

        if (flag) {
            EndDragonFight.LOGGER.info("Found that the dragon has been killed in this world already.");
            this.previouslyKilled = true;
        } else {
            EndDragonFight.LOGGER.info("Found that the dragon has not yet been killed in this world.");
            this.previouslyKilled = false;
            if (this.findExitPortal() == null) {
                this.spawnExitPortal(false);
            }
        }

        List<? extends EnderDragon> list = this.level.getDragons();

        if (list.isEmpty()) {
            this.dragonKilled = true;
        } else {
            EnderDragon enderdragon = (EnderDragon) list.get(0);

            this.dragonUUID = enderdragon.getUUID();
            EndDragonFight.LOGGER.info("Found that there's a dragon still alive ({})", enderdragon);
            this.dragonKilled = false;
            if (!flag) {
                EndDragonFight.LOGGER.info("But we didn't have a portal, let's remove it.");
                enderdragon.discard();
                this.dragonUUID = null;
            }
        }

        if (!this.previouslyKilled && this.dragonKilled) {
            this.dragonKilled = false;
        }

    }

    private void findOrCreateDragon() {
        List<? extends EnderDragon> list = this.level.getDragons();

        if (list.isEmpty()) {
            EndDragonFight.LOGGER.debug("Haven't seen the dragon, respawning it");
            this.createNewDragon();
        } else {
            EndDragonFight.LOGGER.debug("Haven't seen our dragon, but found another one to use.");
            this.dragonUUID = ((EnderDragon) list.get(0)).getUUID();
        }

    }

    public void setRespawnStage(DragonRespawnAnimation stage) {
        if (this.respawnStage == null) {
            throw new IllegalStateException("Dragon respawn isn't in progress, can't skip ahead in the animation.");
        } else {
            this.respawnTime = 0;
            if (stage == DragonRespawnAnimation.END) {
                this.respawnStage = null;
                this.dragonKilled = false;
                EnderDragon enderdragon = this.createNewDragon();

                if (enderdragon != null) {
                    for (ServerPlayer serverplayer : this.dragonEvent.getPlayers()) {
                        CriteriaTriggers.SUMMONED_ENTITY.trigger(serverplayer, enderdragon);
                    }
                }
            } else {
                this.respawnStage = stage;
            }

        }
    }

    private boolean hasActiveExitPortal() {
        for (int i = -8; i <= 8; ++i) {
            for (int j = -8; j <= 8; ++j) {
                LevelChunk levelchunk = this.level.getChunk(i, j);

                for (BlockEntity blockentity : levelchunk.getBlockEntities().values()) {
                    if (blockentity instanceof TheEndPortalBlockEntity) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public BlockPattern.@Nullable BlockPatternMatch findExitPortal() {
        ChunkPos chunkpos = new ChunkPos(this.origin);

        for (int i = -8 + chunkpos.x; i <= 8 + chunkpos.x; ++i) {
            for (int j = -8 + chunkpos.z; j <= 8 + chunkpos.z; ++j) {
                LevelChunk levelchunk = this.level.getChunk(i, j);

                for (BlockEntity blockentity : levelchunk.getBlockEntities().values()) {
                    if (blockentity instanceof TheEndPortalBlockEntity) {
                        BlockPattern.BlockPatternMatch blockpattern_blockpatternmatch = this.exitPortalPattern.find(this.level, blockentity.getBlockPos());

                        if (blockpattern_blockpatternmatch != null) {
                            BlockPos blockpos = blockpattern_blockpatternmatch.getBlock(3, 3, 3).getPos();

                            if (this.portalLocation == null) {
                                this.portalLocation = blockpos;
                            }

                            return blockpattern_blockpatternmatch;
                        }
                    }
                }
            }
        }

        BlockPos blockpos1 = EndPodiumFeature.getLocation(this.origin);
        int k = this.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, blockpos1).getY();

        for (int l = k; l >= this.level.getMinY(); --l) {
            BlockPattern.BlockPatternMatch blockpattern_blockpatternmatch1 = this.exitPortalPattern.find(this.level, new BlockPos(blockpos1.getX(), l, blockpos1.getZ()));

            if (blockpattern_blockpatternmatch1 != null) {
                if (this.portalLocation == null) {
                    this.portalLocation = blockpattern_blockpatternmatch1.getBlock(3, 3, 3).getPos();
                }

                return blockpattern_blockpatternmatch1;
            }
        }

        return null;
    }

    private boolean isArenaLoaded() {
        if (this.skipArenaLoadedCheck) {
            return true;
        } else {
            ChunkPos chunkpos = new ChunkPos(this.origin);

            for (int i = -8 + chunkpos.x; i <= 8 + chunkpos.x; ++i) {
                for (int j = 8 + chunkpos.z; j <= 8 + chunkpos.z; ++j) {
                    ChunkAccess chunkaccess = this.level.getChunk(i, j, ChunkStatus.FULL, false);

                    if (!(chunkaccess instanceof LevelChunk)) {
                        return false;
                    }

                    FullChunkStatus fullchunkstatus = ((LevelChunk) chunkaccess).getFullStatus();

                    if (!fullchunkstatus.isOrAfter(FullChunkStatus.BLOCK_TICKING)) {
                        return false;
                    }
                }
            }

            return true;
        }
    }

    private void updatePlayers() {
        Set<ServerPlayer> set = Sets.newHashSet();

        for (ServerPlayer serverplayer : this.level.getPlayers(this.validPlayer)) {
            this.dragonEvent.addPlayer(serverplayer);
            set.add(serverplayer);
        }

        Set<ServerPlayer> set1 = Sets.newHashSet(this.dragonEvent.getPlayers());

        set1.removeAll(set);

        for (ServerPlayer serverplayer1 : set1) {
            this.dragonEvent.removePlayer(serverplayer1);
        }

    }

    private void updateCrystalCount() {
        this.ticksSinceCrystalsScanned = 0;
        this.crystalsAlive = 0;

        for (SpikeFeature.EndSpike spikefeature_endspike : SpikeFeature.getSpikesForLevel(this.level)) {
            this.crystalsAlive += this.level.getEntitiesOfClass(EndCrystal.class, spikefeature_endspike.getTopBoundingBox()).size();
        }

        EndDragonFight.LOGGER.debug("Found {} end crystals still alive", this.crystalsAlive);
    }

    public void setDragonKilled(EnderDragon dragon) {
        if (dragon.getUUID().equals(this.dragonUUID)) {
            this.dragonEvent.setProgress(0.0F);
            this.dragonEvent.setVisible(false);
            this.spawnExitPortal(true);
            this.spawnNewGateway();
            if (!this.previouslyKilled) {
                this.level.setBlockAndUpdate(this.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, EndPodiumFeature.getLocation(this.origin)), Blocks.DRAGON_EGG.defaultBlockState());
            }

            this.previouslyKilled = true;
            this.dragonKilled = true;
        }

    }

    /** @deprecated */
    @Deprecated
    @VisibleForTesting
    public void removeAllGateways() {
        this.gateways.clear();
    }

    private void spawnNewGateway() {
        if (!this.gateways.isEmpty()) {
            int i = (Integer) this.gateways.remove(this.gateways.size() - 1);
            int j = Mth.floor(96.0D * Math.cos(2.0D * (-Math.PI + 0.15707963267948966D * (double) i)));
            int k = Mth.floor(96.0D * Math.sin(2.0D * (-Math.PI + 0.15707963267948966D * (double) i)));

            this.spawnNewGateway(new BlockPos(j, 75, k));
        }
    }

    private void spawnNewGateway(BlockPos pos) {
        this.level.levelEvent(3000, pos, 0);
        this.level.registryAccess().lookup(Registries.CONFIGURED_FEATURE).flatMap((registry) -> {
            return registry.get(EndFeatures.END_GATEWAY_DELAYED);
        }).ifPresent((holder_reference) -> {
            ((ConfiguredFeature) holder_reference.value()).place(this.level, this.level.getChunkSource().getGenerator(), RandomSource.create(), pos);
        });
    }

    public void spawnExitPortal(boolean activated) {
        EndPodiumFeature endpodiumfeature = new EndPodiumFeature(activated);

        if (this.portalLocation == null) {
            for (this.portalLocation = this.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EndPodiumFeature.getLocation(this.origin)).below(); this.level.getBlockState(this.portalLocation).is(Blocks.BEDROCK) && this.portalLocation.getY() > 63; this.portalLocation = this.portalLocation.below()) {
                ;
            }

            this.portalLocation = this.portalLocation.atY(Math.max(this.level.getMinY() + 1, this.portalLocation.getY()));
        }

        if (endpodiumfeature.place(FeatureConfiguration.NONE, this.level, this.level.getChunkSource().getGenerator(), RandomSource.create(), this.portalLocation)) {
            int i = Mth.positiveCeilDiv(4, 16);

            this.level.getChunkSource().chunkMap.waitForLightBeforeSending(new ChunkPos(this.portalLocation), i);
        }

    }

    private @Nullable EnderDragon createNewDragon() {
        this.level.getChunkAt(new BlockPos(this.origin.getX(), 128 + this.origin.getY(), this.origin.getZ()));
        EnderDragon enderdragon = EntityType.ENDER_DRAGON.create(this.level, EntitySpawnReason.EVENT);

        if (enderdragon != null) {
            enderdragon.setDragonFight(this);
            enderdragon.setFightOrigin(this.origin);
            enderdragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
            enderdragon.snapTo((double) this.origin.getX(), (double) (128 + this.origin.getY()), (double) this.origin.getZ(), this.level.random.nextFloat() * 360.0F, 0.0F);
            this.level.addFreshEntity(enderdragon);
            this.dragonUUID = enderdragon.getUUID();
        }

        return enderdragon;
    }

    public void updateDragon(EnderDragon dragon) {
        if (dragon.getUUID().equals(this.dragonUUID)) {
            this.dragonEvent.setProgress(dragon.getHealth() / dragon.getMaxHealth());
            this.ticksSinceDragonSeen = 0;
            if (dragon.hasCustomName()) {
                this.dragonEvent.setName(dragon.getDisplayName());
            }
        }

    }

    public int getCrystalsAlive() {
        return this.crystalsAlive;
    }

    public void onCrystalDestroyed(EndCrystal crystal, DamageSource source) {
        if (this.respawnStage != null && this.respawnCrystals.contains(crystal)) {
            EndDragonFight.LOGGER.debug("Aborting respawn sequence");
            this.respawnStage = null;
            this.respawnTime = 0;
            this.resetSpikeCrystals();
            this.spawnExitPortal(true);
        } else {
            this.updateCrystalCount();
            Entity entity = this.level.getEntity(this.dragonUUID);

            if (entity instanceof EnderDragon) {
                EnderDragon enderdragon = (EnderDragon) entity;

                enderdragon.onCrystalDestroyed(this.level, crystal, crystal.blockPosition(), source);
            }
        }

    }

    public boolean hasPreviouslyKilledDragon() {
        return this.previouslyKilled;
    }

    public void tryRespawn() {
        if (this.dragonKilled && this.respawnStage == null) {
            BlockPos blockpos = this.portalLocation;

            if (blockpos == null) {
                EndDragonFight.LOGGER.debug("Tried to respawn, but need to find the portal first.");
                BlockPattern.BlockPatternMatch blockpattern_blockpatternmatch = this.findExitPortal();

                if (blockpattern_blockpatternmatch == null) {
                    EndDragonFight.LOGGER.debug("Couldn't find a portal, so we made one.");
                    this.spawnExitPortal(true);
                } else {
                    EndDragonFight.LOGGER.debug("Found the exit portal & saved its location for next time.");
                }

                blockpos = this.portalLocation;
            }

            List<EndCrystal> list = Lists.newArrayList();
            BlockPos blockpos1 = blockpos.above(1);

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                List<EndCrystal> list1 = this.level.<EndCrystal>getEntitiesOfClass(EndCrystal.class, new AABB(blockpos1.relative(direction, 2)));

                if (list1.isEmpty()) {
                    return;
                }

                list.addAll(list1);
            }

            EndDragonFight.LOGGER.debug("Found all crystals, respawning dragon.");
            this.respawnDragon(list);
        }

    }

    public void respawnDragon(List<EndCrystal> crystals) {
        if (this.dragonKilled && this.respawnStage == null) {
            for (BlockPattern.BlockPatternMatch blockpattern_blockpatternmatch = this.findExitPortal(); blockpattern_blockpatternmatch != null; blockpattern_blockpatternmatch = this.findExitPortal()) {
                for (int i = 0; i < this.exitPortalPattern.getWidth(); ++i) {
                    for (int j = 0; j < this.exitPortalPattern.getHeight(); ++j) {
                        for (int k = 0; k < this.exitPortalPattern.getDepth(); ++k) {
                            BlockInWorld blockinworld = blockpattern_blockpatternmatch.getBlock(i, j, k);

                            if (blockinworld.getState().is(Blocks.BEDROCK) || blockinworld.getState().is(Blocks.END_PORTAL)) {
                                this.level.setBlockAndUpdate(blockinworld.getPos(), Blocks.END_STONE.defaultBlockState());
                            }
                        }
                    }
                }
            }

            this.respawnStage = DragonRespawnAnimation.START;
            this.respawnTime = 0;
            this.spawnExitPortal(false);
            this.respawnCrystals = crystals;
        }

    }

    public void resetSpikeCrystals() {
        for (SpikeFeature.EndSpike spikefeature_endspike : SpikeFeature.getSpikesForLevel(this.level)) {
            for (EndCrystal endcrystal : this.level.getEntitiesOfClass(EndCrystal.class, spikefeature_endspike.getTopBoundingBox())) {
                endcrystal.setInvulnerable(false);
                endcrystal.setBeamTarget((BlockPos) null);
            }
        }

    }

    public @Nullable UUID getDragonUUID() {
        return this.dragonUUID;
    }

    public static record Data(boolean needsStateScanning, boolean dragonKilled, boolean previouslyKilled, boolean isRespawning, Optional<UUID> dragonUUID, Optional<BlockPos> exitPortalLocation, Optional<List<Integer>> gateways) {

        public static final Codec<EndDragonFight.Data> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(Codec.BOOL.fieldOf("NeedsStateScanning").orElse(true).forGetter(EndDragonFight.Data::needsStateScanning), Codec.BOOL.fieldOf("DragonKilled").orElse(false).forGetter(EndDragonFight.Data::dragonKilled), Codec.BOOL.fieldOf("PreviouslyKilled").orElse(false).forGetter(EndDragonFight.Data::previouslyKilled), Codec.BOOL.lenientOptionalFieldOf("IsRespawning", false).forGetter(EndDragonFight.Data::isRespawning), UUIDUtil.CODEC.lenientOptionalFieldOf("Dragon").forGetter(EndDragonFight.Data::dragonUUID), BlockPos.CODEC.lenientOptionalFieldOf("ExitPortalLocation").forGetter(EndDragonFight.Data::exitPortalLocation), Codec.list(Codec.INT).lenientOptionalFieldOf("Gateways").forGetter(EndDragonFight.Data::gateways)).apply(instance, EndDragonFight.Data::new);
        });
        public static final EndDragonFight.Data DEFAULT = new EndDragonFight.Data(true, false, false, false, Optional.empty(), Optional.empty(), Optional.empty());
    }
}
