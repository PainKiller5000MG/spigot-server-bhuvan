package net.minecraft.server.network;

import com.google.common.collect.Comparators;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

public class PlayerChunkSender {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final float MIN_CHUNKS_PER_TICK = 0.01F;
    public static final float MAX_CHUNKS_PER_TICK = 64.0F;
    private static final float START_CHUNKS_PER_TICK = 9.0F;
    private static final int MAX_UNACKNOWLEDGED_BATCHES = 10;
    private final LongSet pendingChunks = new LongOpenHashSet();
    private final boolean memoryConnection;
    private float desiredChunksPerTick = 9.0F;
    private float batchQuota;
    private int unacknowledgedBatches;
    private int maxUnacknowledgedBatches = 1;

    public PlayerChunkSender(boolean memoryConnection) {
        this.memoryConnection = memoryConnection;
    }

    public void markChunkPendingToSend(LevelChunk chunk) {
        this.pendingChunks.add(chunk.getPos().toLong());
    }

    public void dropChunk(ServerPlayer player, ChunkPos pos) {
        if (!this.pendingChunks.remove(pos.toLong()) && player.isAlive()) {
            player.connection.send(new ClientboundForgetLevelChunkPacket(pos));
        }

    }

    public void sendNextChunks(ServerPlayer player) {
        if (this.unacknowledgedBatches < this.maxUnacknowledgedBatches) {
            float f = Math.max(1.0F, this.desiredChunksPerTick);

            this.batchQuota = Math.min(this.batchQuota + this.desiredChunksPerTick, f);
            if (this.batchQuota >= 1.0F) {
                if (!this.pendingChunks.isEmpty()) {
                    ServerLevel serverlevel = player.level();
                    ChunkMap chunkmap = serverlevel.getChunkSource().chunkMap;
                    List<LevelChunk> list = this.collectChunksToSend(chunkmap, player.chunkPosition());

                    if (!list.isEmpty()) {
                        ServerGamePacketListenerImpl servergamepacketlistenerimpl = player.connection;

                        ++this.unacknowledgedBatches;
                        servergamepacketlistenerimpl.send(ClientboundChunkBatchStartPacket.INSTANCE);

                        for (LevelChunk levelchunk : list) {
                            sendChunk(servergamepacketlistenerimpl, serverlevel, levelchunk);
                        }

                        servergamepacketlistenerimpl.send(new ClientboundChunkBatchFinishedPacket(list.size()));
                        this.batchQuota -= (float) list.size();
                    }
                }
            }
        }
    }

    private static void sendChunk(ServerGamePacketListenerImpl connection, ServerLevel level, LevelChunk chunk) {
        connection.send(new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), (BitSet) null, (BitSet) null));
        ChunkPos chunkpos = chunk.getPos();

        if (SharedConstants.DEBUG_VERBOSE_SERVER_EVENTS) {
            PlayerChunkSender.LOGGER.debug("SEN {}", chunkpos);
        }

        level.debugSynchronizers().startTrackingChunk(connection.player, chunk.getPos());
    }

    private List<LevelChunk> collectChunksToSend(ChunkMap chunkMap, ChunkPos playerPos) {
        int i = Mth.floor(this.batchQuota);
        List<LevelChunk> list;

        if (!this.memoryConnection && this.pendingChunks.size() > i) {
            Stream stream = this.pendingChunks.stream();

            Objects.requireNonNull(playerPos);
            LongStream longstream = ((List) stream.collect(Comparators.least(i, Comparator.comparingInt(playerPos::distanceSquared)))).stream().mapToLong(Long::longValue);

            Objects.requireNonNull(chunkMap);
            list = longstream.mapToObj(chunkMap::getChunkToSend).filter(Objects::nonNull).toList();
        } else {
            LongStream longstream1 = this.pendingChunks.longStream();

            Objects.requireNonNull(chunkMap);
            list = longstream1.mapToObj(chunkMap::getChunkToSend).filter(Objects::nonNull).sorted(Comparator.comparingInt((levelchunk) -> {
                return playerPos.distanceSquared(levelchunk.getPos());
            })).toList();
        }

        for (LevelChunk levelchunk : list) {
            this.pendingChunks.remove(levelchunk.getPos().toLong());
        }

        return list;
    }

    public void onChunkBatchReceivedByClient(float desiredChunksPerTick) {
        --this.unacknowledgedBatches;
        this.desiredChunksPerTick = Double.isNaN((double) desiredChunksPerTick) ? 0.01F : Mth.clamp(desiredChunksPerTick, 0.01F, 64.0F);
        if (this.unacknowledgedBatches == 0) {
            this.batchQuota = 1.0F;
        }

        this.maxUnacknowledgedBatches = 10;
    }

    public boolean isPending(long pos) {
        return this.pendingChunks.contains(pos);
    }
}
