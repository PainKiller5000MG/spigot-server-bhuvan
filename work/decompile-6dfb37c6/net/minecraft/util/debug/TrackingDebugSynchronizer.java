package net.minecraft.util.debug;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundDebugBlockValuePacket;
import net.minecraft.network.protocol.game.ClientboundDebugChunkValuePacket;
import net.minecraft.network.protocol.game.ClientboundDebugEntityValuePacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

public abstract class TrackingDebugSynchronizer<T> {

    protected final DebugSubscription<T> subscription;
    private final Set<UUID> subscribedPlayers = new ObjectOpenHashSet();

    public TrackingDebugSynchronizer(DebugSubscription<T> subscription) {
        this.subscription = subscription;
    }

    public final void tick(ServerLevel level) {
        for (ServerPlayer serverplayer : level.players()) {
            boolean flag = this.subscribedPlayers.contains(serverplayer.getUUID());
            boolean flag1 = serverplayer.debugSubscriptions().contains(this.subscription);

            if (flag1 != flag) {
                if (flag1) {
                    this.addSubscriber(serverplayer);
                } else {
                    this.subscribedPlayers.remove(serverplayer.getUUID());
                }
            }
        }

        this.subscribedPlayers.removeIf((uuid) -> {
            return level.getPlayerByUUID(uuid) == null;
        });
        if (!this.subscribedPlayers.isEmpty()) {
            this.pollAndSendUpdates(level);
        }

    }

    private void addSubscriber(ServerPlayer player) {
        this.subscribedPlayers.add(player.getUUID());
        player.getChunkTrackingView().forEach((chunkpos) -> {
            if (!player.connection.chunkSender.isPending(chunkpos.toLong())) {
                this.startTrackingChunk(player, chunkpos);
            }

        });
        player.level().getChunkSource().chunkMap.forEachEntityTrackedBy(player, (entity) -> {
            this.startTrackingEntity(player, entity);
        });
    }

    protected final void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos trackedChunk, Packet<? super ClientGamePacketListener> packet) {
        ChunkMap chunkmap = level.getChunkSource().chunkMap;

        for (UUID uuid : this.subscribedPlayers) {
            Player player = level.getPlayerByUUID(uuid);

            if (player instanceof ServerPlayer serverplayer) {
                if (chunkmap.isChunkTracked(serverplayer, trackedChunk.x, trackedChunk.z)) {
                    serverplayer.connection.send(packet);
                }
            }
        }

    }

    protected final void sendToPlayersTrackingEntity(ServerLevel level, Entity trackedEntity, Packet<? super ClientGamePacketListener> packet) {
        ChunkMap chunkmap = level.getChunkSource().chunkMap;

        chunkmap.sendToTrackingPlayersFiltered(trackedEntity, packet, (serverplayer) -> {
            return this.subscribedPlayers.contains(serverplayer.getUUID());
        });
    }

    public final void startTrackingChunk(ServerPlayer player, ChunkPos chunkPos) {
        if (this.subscribedPlayers.contains(player.getUUID())) {
            this.sendInitialChunk(player, chunkPos);
        }

    }

    public final void startTrackingEntity(ServerPlayer player, Entity entity) {
        if (this.subscribedPlayers.contains(player.getUUID())) {
            this.sendInitialEntity(player, entity);
        }

    }

    protected void clear() {}

    protected void pollAndSendUpdates(ServerLevel level) {}

    protected void sendInitialChunk(ServerPlayer player, ChunkPos chunkPos) {}

    protected void sendInitialEntity(ServerPlayer player, Entity entity) {}

    public static class SourceSynchronizer<T> extends TrackingDebugSynchronizer<T> {

        private final Map<ChunkPos, TrackingDebugSynchronizer.ValueSource<T>> chunkSources = new HashMap();
        private final Map<BlockPos, TrackingDebugSynchronizer.ValueSource<T>> blockEntitySources = new HashMap();
        private final Map<UUID, TrackingDebugSynchronizer.ValueSource<T>> entitySources = new HashMap();

        public SourceSynchronizer(DebugSubscription<T> subscription) {
            super(subscription);
        }

        @Override
        protected void clear() {
            this.chunkSources.clear();
            this.blockEntitySources.clear();
            this.entitySources.clear();
        }

        @Override
        protected void pollAndSendUpdates(ServerLevel level) {
            for (Map.Entry<ChunkPos, TrackingDebugSynchronizer.ValueSource<T>> map_entry : this.chunkSources.entrySet()) {
                DebugSubscription.Update<T> debugsubscription_update = ((TrackingDebugSynchronizer.ValueSource) map_entry.getValue()).pollUpdate(this.subscription);

                if (debugsubscription_update != null) {
                    ChunkPos chunkpos = (ChunkPos) map_entry.getKey();

                    this.sendToPlayersTrackingChunk(level, chunkpos, new ClientboundDebugChunkValuePacket(chunkpos, debugsubscription_update));
                }
            }

            for (Map.Entry<BlockPos, TrackingDebugSynchronizer.ValueSource<T>> map_entry1 : this.blockEntitySources.entrySet()) {
                DebugSubscription.Update<T> debugsubscription_update1 = ((TrackingDebugSynchronizer.ValueSource) map_entry1.getValue()).pollUpdate(this.subscription);

                if (debugsubscription_update1 != null) {
                    BlockPos blockpos = (BlockPos) map_entry1.getKey();
                    ChunkPos chunkpos1 = new ChunkPos(blockpos);

                    this.sendToPlayersTrackingChunk(level, chunkpos1, new ClientboundDebugBlockValuePacket(blockpos, debugsubscription_update1));
                }
            }

            for (Map.Entry<UUID, TrackingDebugSynchronizer.ValueSource<T>> map_entry2 : this.entitySources.entrySet()) {
                DebugSubscription.Update<T> debugsubscription_update2 = ((TrackingDebugSynchronizer.ValueSource) map_entry2.getValue()).pollUpdate(this.subscription);

                if (debugsubscription_update2 != null) {
                    Entity entity = (Entity) Objects.requireNonNull(level.getEntity((UUID) map_entry2.getKey()));

                    this.sendToPlayersTrackingEntity(level, entity, new ClientboundDebugEntityValuePacket(entity.getId(), debugsubscription_update2));
                }
            }

        }

        public void registerChunk(ChunkPos chunkPos, DebugValueSource.ValueGetter<T> getter) {
            this.chunkSources.put(chunkPos, new TrackingDebugSynchronizer.ValueSource(getter));
        }

        public void registerBlockEntity(BlockPos blockPos, DebugValueSource.ValueGetter<T> getter) {
            this.blockEntitySources.put(blockPos, new TrackingDebugSynchronizer.ValueSource(getter));
        }

        public void registerEntity(UUID entityId, DebugValueSource.ValueGetter<T> getter) {
            this.entitySources.put(entityId, new TrackingDebugSynchronizer.ValueSource(getter));
        }

        public void dropChunk(ChunkPos chunkPos) {
            this.chunkSources.remove(chunkPos);
            Set set = this.blockEntitySources.keySet();

            Objects.requireNonNull(chunkPos);
            set.removeIf(chunkPos::contains);
        }

        public void dropBlockEntity(ServerLevel level, BlockPos blockPos) {
            TrackingDebugSynchronizer.ValueSource<T> trackingdebugsynchronizer_valuesource = (TrackingDebugSynchronizer.ValueSource) this.blockEntitySources.remove(blockPos);

            if (trackingdebugsynchronizer_valuesource != null) {
                ChunkPos chunkpos = new ChunkPos(blockPos);

                this.sendToPlayersTrackingChunk(level, chunkpos, new ClientboundDebugBlockValuePacket(blockPos, this.subscription.emptyUpdate()));
            }

        }

        public void dropEntity(Entity entity) {
            this.entitySources.remove(entity.getUUID());
        }

        @Override
        protected void sendInitialChunk(ServerPlayer player, ChunkPos chunkPos) {
            TrackingDebugSynchronizer.ValueSource<T> trackingdebugsynchronizer_valuesource = (TrackingDebugSynchronizer.ValueSource) this.chunkSources.get(chunkPos);

            if (trackingdebugsynchronizer_valuesource != null && trackingdebugsynchronizer_valuesource.lastSyncedValue != null) {
                player.connection.send(new ClientboundDebugChunkValuePacket(chunkPos, this.subscription.packUpdate(trackingdebugsynchronizer_valuesource.lastSyncedValue)));
            }

            for (Map.Entry<BlockPos, TrackingDebugSynchronizer.ValueSource<T>> map_entry : this.blockEntitySources.entrySet()) {
                T t0 = ((TrackingDebugSynchronizer.ValueSource) map_entry.getValue()).lastSyncedValue;

                if (t0 != null) {
                    BlockPos blockpos = (BlockPos) map_entry.getKey();

                    if (chunkPos.contains(blockpos)) {
                        player.connection.send(new ClientboundDebugBlockValuePacket(blockpos, this.subscription.packUpdate(t0)));
                    }
                }
            }

        }

        @Override
        protected void sendInitialEntity(ServerPlayer player, Entity entity) {
            TrackingDebugSynchronizer.ValueSource<T> trackingdebugsynchronizer_valuesource = (TrackingDebugSynchronizer.ValueSource) this.entitySources.get(entity.getUUID());

            if (trackingdebugsynchronizer_valuesource != null && trackingdebugsynchronizer_valuesource.lastSyncedValue != null) {
                player.connection.send(new ClientboundDebugEntityValuePacket(entity.getId(), this.subscription.packUpdate(trackingdebugsynchronizer_valuesource.lastSyncedValue)));
            }

        }
    }

    private static class ValueSource<T> {

        private final DebugValueSource.ValueGetter<T> getter;
        private @Nullable T lastSyncedValue;

        private ValueSource(DebugValueSource.ValueGetter<T> getter) {
            this.getter = getter;
        }

        public DebugSubscription.@Nullable Update<T> pollUpdate(DebugSubscription<T> subscription) {
            T t0 = this.getter.get();

            if (!Objects.equals(t0, this.lastSyncedValue)) {
                this.lastSyncedValue = t0;
                return subscription.packUpdate(t0);
            } else {
                return null;
            }
        }
    }

    public static class PoiSynchronizer extends TrackingDebugSynchronizer<DebugPoiInfo> {

        public PoiSynchronizer() {
            super(DebugSubscriptions.POIS);
        }

        @Override
        protected void sendInitialChunk(ServerPlayer player, ChunkPos chunkPos) {
            ServerLevel serverlevel = player.level();
            PoiManager poimanager = serverlevel.getPoiManager();

            poimanager.getInChunk((holder) -> {
                return true;
            }, chunkPos, PoiManager.Occupancy.ANY).forEach((poirecord) -> {
                player.connection.send(new ClientboundDebugBlockValuePacket(poirecord.getPos(), this.subscription.packUpdate(new DebugPoiInfo(poirecord))));
            });
        }

        public void onPoiAdded(ServerLevel level, PoiRecord record) {
            this.sendToPlayersTrackingChunk(level, new ChunkPos(record.getPos()), new ClientboundDebugBlockValuePacket(record.getPos(), this.subscription.packUpdate(new DebugPoiInfo(record))));
        }

        public void onPoiRemoved(ServerLevel level, BlockPos poiPos) {
            this.sendToPlayersTrackingChunk(level, new ChunkPos(poiPos), new ClientboundDebugBlockValuePacket(poiPos, this.subscription.emptyUpdate()));
        }

        public void onPoiTicketCountChanged(ServerLevel level, BlockPos poiPos) {
            this.sendToPlayersTrackingChunk(level, new ChunkPos(poiPos), new ClientboundDebugBlockValuePacket(poiPos, this.subscription.packUpdate(level.getPoiManager().getDebugPoiInfo(poiPos))));
        }
    }

    public static class VillageSectionSynchronizer extends TrackingDebugSynchronizer<Unit> {

        public VillageSectionSynchronizer() {
            super(DebugSubscriptions.VILLAGE_SECTIONS);
        }

        @Override
        protected void sendInitialChunk(ServerPlayer player, ChunkPos chunkPos) {
            ServerLevel serverlevel = player.level();
            PoiManager poimanager = serverlevel.getPoiManager();

            poimanager.getInChunk((holder) -> {
                return true;
            }, chunkPos, PoiManager.Occupancy.ANY).forEach((poirecord) -> {
                SectionPos sectionpos = SectionPos.of(poirecord.getPos());

                forEachVillageSectionUpdate(serverlevel, sectionpos, (sectionpos1, obool) -> {
                    BlockPos blockpos = sectionpos1.center();

                    player.connection.send(new ClientboundDebugBlockValuePacket(blockpos, this.subscription.packUpdate(obool ? Unit.INSTANCE : null)));
                });
            });
        }

        public void onPoiAdded(ServerLevel level, PoiRecord record) {
            this.sendVillageSectionsPacket(level, record.getPos());
        }

        public void onPoiRemoved(ServerLevel level, BlockPos poiPos) {
            this.sendVillageSectionsPacket(level, poiPos);
        }

        private void sendVillageSectionsPacket(ServerLevel level, BlockPos poiPos) {
            forEachVillageSectionUpdate(level, SectionPos.of(poiPos), (sectionpos, obool) -> {
                BlockPos blockpos1 = sectionpos.center();

                if (obool) {
                    this.sendToPlayersTrackingChunk(level, new ChunkPos(blockpos1), new ClientboundDebugBlockValuePacket(blockpos1, this.subscription.packUpdate(Unit.INSTANCE)));
                } else {
                    this.sendToPlayersTrackingChunk(level, new ChunkPos(blockpos1), new ClientboundDebugBlockValuePacket(blockpos1, this.subscription.emptyUpdate()));
                }

            });
        }

        private static void forEachVillageSectionUpdate(ServerLevel level, SectionPos centerSection, BiConsumer<SectionPos, Boolean> consumer) {
            for (int i = -1; i <= 1; ++i) {
                for (int j = -1; j <= 1; ++j) {
                    for (int k = -1; k <= 1; ++k) {
                        SectionPos sectionpos1 = centerSection.offset(j, k, i);

                        if (level.isVillage(sectionpos1.center())) {
                            consumer.accept(sectionpos1, true);
                        } else {
                            consumer.accept(sectionpos1, false);
                        }
                    }
                }
            }

        }
    }
}
