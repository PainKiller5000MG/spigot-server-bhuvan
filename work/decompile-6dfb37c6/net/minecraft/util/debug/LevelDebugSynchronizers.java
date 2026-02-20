package net.minecraft.util.debug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundDebugBlockValuePacket;
import net.minecraft.network.protocol.game.ClientboundDebugEntityValuePacket;
import net.minecraft.network.protocol.game.ClientboundDebugEventPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

public class LevelDebugSynchronizers {

    private final ServerLevel level;
    private final List<TrackingDebugSynchronizer<?>> allSynchronizers = new ArrayList();
    private final Map<DebugSubscription<?>, TrackingDebugSynchronizer.SourceSynchronizer<?>> sourceSynchronizers = new HashMap();
    private final TrackingDebugSynchronizer.PoiSynchronizer poiSynchronizer = new TrackingDebugSynchronizer.PoiSynchronizer();
    private final TrackingDebugSynchronizer.VillageSectionSynchronizer villageSectionSynchronizer = new TrackingDebugSynchronizer.VillageSectionSynchronizer();
    private boolean sleeping = true;
    private Set<DebugSubscription<?>> enabledSubscriptions = Set.of();

    public LevelDebugSynchronizers(ServerLevel level) {
        this.level = level;

        for (DebugSubscription<?> debugsubscription : BuiltInRegistries.DEBUG_SUBSCRIPTION) {
            if (debugsubscription.valueStreamCodec() != null) {
                this.sourceSynchronizers.put(debugsubscription, new TrackingDebugSynchronizer.SourceSynchronizer(debugsubscription));
            }
        }

        this.allSynchronizers.addAll(this.sourceSynchronizers.values());
        this.allSynchronizers.add(this.poiSynchronizer);
        this.allSynchronizers.add(this.villageSectionSynchronizer);
    }

    public void tick(ServerDebugSubscribers serverSubscribers) {
        this.enabledSubscriptions = serverSubscribers.enabledSubscriptions();
        boolean flag = this.enabledSubscriptions.isEmpty();

        if (this.sleeping != flag) {
            this.sleeping = flag;
            if (flag) {
                for (TrackingDebugSynchronizer<?> trackingdebugsynchronizer : this.allSynchronizers) {
                    trackingdebugsynchronizer.clear();
                }
            } else {
                this.wakeUp();
            }
        }

        if (!this.sleeping) {
            for (TrackingDebugSynchronizer<?> trackingdebugsynchronizer1 : this.allSynchronizers) {
                trackingdebugsynchronizer1.tick(this.level);
            }
        }

    }

    private void wakeUp() {
        ChunkMap chunkmap = this.level.getChunkSource().chunkMap;

        chunkmap.forEachReadyToSendChunk(this::registerChunk);

        for (Entity entity : this.level.getAllEntities()) {
            if (chunkmap.isTrackedByAnyPlayer(entity)) {
                this.registerEntity(entity);
            }
        }

    }

    private <T> TrackingDebugSynchronizer.SourceSynchronizer<T> getSourceSynchronizer(DebugSubscription<T> subscription) {
        return (TrackingDebugSynchronizer.SourceSynchronizer) this.sourceSynchronizers.get(subscription);
    }

    public void registerChunk(final LevelChunk chunk) {
        if (!this.sleeping) {
            chunk.registerDebugValues(this.level, new DebugValueSource.Registration() {
                @Override
                public <T> void register(DebugSubscription<T> subscription, DebugValueSource.ValueGetter<T> getter) {
                    LevelDebugSynchronizers.this.getSourceSynchronizer(subscription).registerChunk(chunk.getPos(), getter);
                }
            });
            chunk.getBlockEntities().values().forEach(this::registerBlockEntity);
        }
    }

    public void dropChunk(ChunkPos chunkPos) {
        if (!this.sleeping) {
            for (TrackingDebugSynchronizer.SourceSynchronizer<?> trackingdebugsynchronizer_sourcesynchronizer : this.sourceSynchronizers.values()) {
                trackingdebugsynchronizer_sourcesynchronizer.dropChunk(chunkPos);
            }

        }
    }

    public void registerBlockEntity(final BlockEntity blockEntity) {
        if (!this.sleeping) {
            blockEntity.registerDebugValues(this.level, new DebugValueSource.Registration() {
                @Override
                public <T> void register(DebugSubscription<T> subscription, DebugValueSource.ValueGetter<T> getter) {
                    LevelDebugSynchronizers.this.getSourceSynchronizer(subscription).registerBlockEntity(blockEntity.getBlockPos(), getter);
                }
            });
        }
    }

    public void dropBlockEntity(BlockPos blockPos) {
        if (!this.sleeping) {
            for (TrackingDebugSynchronizer.SourceSynchronizer<?> trackingdebugsynchronizer_sourcesynchronizer : this.sourceSynchronizers.values()) {
                trackingdebugsynchronizer_sourcesynchronizer.dropBlockEntity(this.level, blockPos);
            }

        }
    }

    public void registerEntity(final Entity entity) {
        if (!this.sleeping) {
            entity.registerDebugValues(this.level, new DebugValueSource.Registration() {
                @Override
                public <T> void register(DebugSubscription<T> subscription, DebugValueSource.ValueGetter<T> getter) {
                    LevelDebugSynchronizers.this.getSourceSynchronizer(subscription).registerEntity(entity.getUUID(), getter);
                }
            });
        }
    }

    public void dropEntity(Entity entity) {
        if (!this.sleeping) {
            for (TrackingDebugSynchronizer.SourceSynchronizer<?> trackingdebugsynchronizer_sourcesynchronizer : this.sourceSynchronizers.values()) {
                trackingdebugsynchronizer_sourcesynchronizer.dropEntity(entity);
            }

        }
    }

    public void startTrackingChunk(ServerPlayer player, ChunkPos chunkPos) {
        if (!this.sleeping) {
            for (TrackingDebugSynchronizer<?> trackingdebugsynchronizer : this.allSynchronizers) {
                trackingdebugsynchronizer.startTrackingChunk(player, chunkPos);
            }

        }
    }

    public void startTrackingEntity(ServerPlayer player, Entity entity) {
        if (!this.sleeping) {
            for (TrackingDebugSynchronizer<?> trackingdebugsynchronizer : this.allSynchronizers) {
                trackingdebugsynchronizer.startTrackingEntity(player, entity);
            }

        }
    }

    public void registerPoi(PoiRecord poi) {
        if (!this.sleeping) {
            this.poiSynchronizer.onPoiAdded(this.level, poi);
            this.villageSectionSynchronizer.onPoiAdded(this.level, poi);
        }
    }

    public void updatePoi(BlockPos pos) {
        if (!this.sleeping) {
            this.poiSynchronizer.onPoiTicketCountChanged(this.level, pos);
        }
    }

    public void dropPoi(BlockPos pos) {
        if (!this.sleeping) {
            this.poiSynchronizer.onPoiRemoved(this.level, pos);
            this.villageSectionSynchronizer.onPoiRemoved(this.level, pos);
        }
    }

    public boolean hasAnySubscriberFor(DebugSubscription<?> subscription) {
        return this.enabledSubscriptions.contains(subscription);
    }

    public <T> void sendBlockValue(BlockPos blockPos, DebugSubscription<T> subscription, T value) {
        if (this.hasAnySubscriberFor(subscription)) {
            this.broadcastToTracking(new ChunkPos(blockPos), subscription, new ClientboundDebugBlockValuePacket(blockPos, subscription.packUpdate(value)));
        }

    }

    public <T> void clearBlockValue(BlockPos blockPos, DebugSubscription<T> subscription) {
        if (this.hasAnySubscriberFor(subscription)) {
            this.broadcastToTracking(new ChunkPos(blockPos), subscription, new ClientboundDebugBlockValuePacket(blockPos, subscription.emptyUpdate()));
        }

    }

    public <T> void sendEntityValue(Entity entity, DebugSubscription<T> subscription, T value) {
        if (this.hasAnySubscriberFor(subscription)) {
            this.broadcastToTracking(entity, subscription, new ClientboundDebugEntityValuePacket(entity.getId(), subscription.packUpdate(value)));
        }

    }

    public <T> void clearEntityValue(Entity entity, DebugSubscription<T> subscription) {
        if (this.hasAnySubscriberFor(subscription)) {
            this.broadcastToTracking(entity, subscription, new ClientboundDebugEntityValuePacket(entity.getId(), subscription.emptyUpdate()));
        }

    }

    public <T> void broadcastEventToTracking(BlockPos blockPos, DebugSubscription<T> subscription, T value) {
        if (this.hasAnySubscriberFor(subscription)) {
            this.broadcastToTracking(new ChunkPos(blockPos), subscription, new ClientboundDebugEventPacket(subscription.packEvent(value)));
        }

    }

    private void broadcastToTracking(ChunkPos trackedChunk, DebugSubscription<?> subscription, Packet<? super ClientGamePacketListener> packet) {
        ChunkMap chunkmap = this.level.getChunkSource().chunkMap;

        for (ServerPlayer serverplayer : chunkmap.getPlayers(trackedChunk, false)) {
            if (serverplayer.debugSubscriptions().contains(subscription)) {
                serverplayer.connection.send(packet);
            }
        }

    }

    private void broadcastToTracking(Entity trackedEntity, DebugSubscription<?> subscription, Packet<? super ClientGamePacketListener> packet) {
        ChunkMap chunkmap = this.level.getChunkSource().chunkMap;

        chunkmap.sendToTrackingPlayersFiltered(trackedEntity, packet, (serverplayer) -> {
            return serverplayer.debugSubscriptions().contains(subscription);
        });
    }
}
