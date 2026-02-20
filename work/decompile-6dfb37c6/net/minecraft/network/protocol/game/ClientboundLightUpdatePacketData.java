package net.minecraft.network.protocol.game;

import com.google.common.collect.Lists;
import io.netty.buffer.ByteBuf;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jspecify.annotations.Nullable;

public class ClientboundLightUpdatePacketData {

    private static final StreamCodec<ByteBuf, byte[]> DATA_LAYER_STREAM_CODEC = ByteBufCodecs.byteArray(2048);
    private final BitSet skyYMask;
    private final BitSet blockYMask;
    private final BitSet emptySkyYMask;
    private final BitSet emptyBlockYMask;
    private final List<byte[]> skyUpdates;
    private final List<byte[]> blockUpdates;

    public ClientboundLightUpdatePacketData(ChunkPos chunkPos, LevelLightEngine lightEngine, @Nullable BitSet skyChangedLightSectionFilter, @Nullable BitSet blockChangedLightSectionFilter) {
        this.skyYMask = new BitSet();
        this.blockYMask = new BitSet();
        this.emptySkyYMask = new BitSet();
        this.emptyBlockYMask = new BitSet();
        this.skyUpdates = Lists.newArrayList();
        this.blockUpdates = Lists.newArrayList();

        for (int i = 0; i < lightEngine.getLightSectionCount(); ++i) {
            if (skyChangedLightSectionFilter == null || skyChangedLightSectionFilter.get(i)) {
                this.prepareSectionData(chunkPos, lightEngine, LightLayer.SKY, i, this.skyYMask, this.emptySkyYMask, this.skyUpdates);
            }

            if (blockChangedLightSectionFilter == null || blockChangedLightSectionFilter.get(i)) {
                this.prepareSectionData(chunkPos, lightEngine, LightLayer.BLOCK, i, this.blockYMask, this.emptyBlockYMask, this.blockUpdates);
            }
        }

    }

    public ClientboundLightUpdatePacketData(FriendlyByteBuf input, int x, int z) {
        this.skyYMask = input.readBitSet();
        this.blockYMask = input.readBitSet();
        this.emptySkyYMask = input.readBitSet();
        this.emptyBlockYMask = input.readBitSet();
        this.skyUpdates = input.<byte[]>readList(ClientboundLightUpdatePacketData.DATA_LAYER_STREAM_CODEC);
        this.blockUpdates = input.<byte[]>readList(ClientboundLightUpdatePacketData.DATA_LAYER_STREAM_CODEC);
    }

    public void write(FriendlyByteBuf output) {
        output.writeBitSet(this.skyYMask);
        output.writeBitSet(this.blockYMask);
        output.writeBitSet(this.emptySkyYMask);
        output.writeBitSet(this.emptyBlockYMask);
        output.writeCollection(this.skyUpdates, ClientboundLightUpdatePacketData.DATA_LAYER_STREAM_CODEC);
        output.writeCollection(this.blockUpdates, ClientboundLightUpdatePacketData.DATA_LAYER_STREAM_CODEC);
    }

    private void prepareSectionData(ChunkPos pos, LevelLightEngine lightEngine, LightLayer layer, int sectionIndex, BitSet mask, BitSet emptyMask, List<byte[]> updates) {
        DataLayer datalayer = lightEngine.getLayerListener(layer).getDataLayerData(SectionPos.of(pos, lightEngine.getMinLightSection() + sectionIndex));

        if (datalayer != null) {
            if (datalayer.isEmpty()) {
                emptyMask.set(sectionIndex);
            } else {
                mask.set(sectionIndex);
                updates.add(datalayer.copy().getData());
            }
        }

    }

    public BitSet getSkyYMask() {
        return this.skyYMask;
    }

    public BitSet getEmptySkyYMask() {
        return this.emptySkyYMask;
    }

    public List<byte[]> getSkyUpdates() {
        return this.skyUpdates;
    }

    public BitSet getBlockYMask() {
        return this.blockYMask;
    }

    public BitSet getEmptyBlockYMask() {
        return this.emptyBlockYMask;
    }

    public List<byte[]> getBlockUpdates() {
        return this.blockUpdates;
    }
}
