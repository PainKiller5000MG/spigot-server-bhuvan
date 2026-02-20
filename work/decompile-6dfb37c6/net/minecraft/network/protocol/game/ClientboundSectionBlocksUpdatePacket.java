package net.minecraft.network.protocol.game;

import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

public class ClientboundSectionBlocksUpdatePacket implements Packet<ClientGamePacketListener> {

    public static final StreamCodec<FriendlyByteBuf, ClientboundSectionBlocksUpdatePacket> STREAM_CODEC = Packet.<FriendlyByteBuf, ClientboundSectionBlocksUpdatePacket>codec(ClientboundSectionBlocksUpdatePacket::write, ClientboundSectionBlocksUpdatePacket::new);
    private static final int POS_IN_SECTION_BITS = 12;
    private final SectionPos sectionPos;
    private final short[] positions;
    private final BlockState[] states;

    public ClientboundSectionBlocksUpdatePacket(SectionPos sectionPos, ShortSet changes, LevelChunkSection section) {
        this.sectionPos = sectionPos;
        int i = changes.size();

        this.positions = new short[i];
        this.states = new BlockState[i];
        int j = 0;

        for (ShortIterator shortiterator = changes.iterator(); shortiterator.hasNext(); ++j) {
            short short0 = (Short) shortiterator.next();

            this.positions[j] = short0;
            this.states[j] = section.getBlockState(SectionPos.sectionRelativeX(short0), SectionPos.sectionRelativeY(short0), SectionPos.sectionRelativeZ(short0));
        }

    }

    private ClientboundSectionBlocksUpdatePacket(FriendlyByteBuf input) {
        this.sectionPos = (SectionPos) SectionPos.STREAM_CODEC.decode(input);
        int i = input.readVarInt();

        this.positions = new short[i];
        this.states = new BlockState[i];

        for (int j = 0; j < i; ++j) {
            long k = input.readVarLong();

            this.positions[j] = (short) ((int) (k & 4095L));
            this.states[j] = (BlockState) Block.BLOCK_STATE_REGISTRY.byId((int) (k >>> 12));
        }

    }

    private void write(FriendlyByteBuf output) {
        SectionPos.STREAM_CODEC.encode(output, this.sectionPos);
        output.writeVarInt(this.positions.length);

        for (int i = 0; i < this.positions.length; ++i) {
            output.writeVarLong((long) Block.getId(this.states[i]) << 12 | (long) this.positions[i]);
        }

    }

    @Override
    public PacketType<ClientboundSectionBlocksUpdatePacket> type() {
        return GamePacketTypes.CLIENTBOUND_SECTION_BLOCKS_UPDATE;
    }

    public void handle(ClientGamePacketListener listener) {
        listener.handleChunkBlocksUpdate(this);
    }

    public void runUpdates(BiConsumer<BlockPos, BlockState> updateFunction) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < this.positions.length; ++i) {
            short short0 = this.positions[i];

            blockpos_mutableblockpos.set(this.sectionPos.relativeToBlockX(short0), this.sectionPos.relativeToBlockY(short0), this.sectionPos.relativeToBlockZ(short0));
            updateFunction.accept(blockpos_mutableblockpos, this.states[i]);
        }

    }
}
