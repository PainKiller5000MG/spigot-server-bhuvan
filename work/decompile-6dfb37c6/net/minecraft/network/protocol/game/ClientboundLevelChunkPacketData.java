package net.minecraft.network.protocol.game;

import com.google.common.collect.Lists;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

public class ClientboundLevelChunkPacketData {

    private static final StreamCodec<ByteBuf, Map<Heightmap.Types, long[]>> HEIGHTMAPS_STREAM_CODEC = ByteBufCodecs.map((i) -> {
        return new EnumMap(Heightmap.Types.class);
    }, Heightmap.Types.STREAM_CODEC, ByteBufCodecs.LONG_ARRAY);
    private static final int TWO_MEGABYTES = 2097152;
    private final Map<Heightmap.Types, long[]> heightmaps;
    private final byte[] buffer;
    private final List<ClientboundLevelChunkPacketData.BlockEntityInfo> blockEntitiesData;

    public ClientboundLevelChunkPacketData(LevelChunk levelChunk) {
        this.heightmaps = (Map) levelChunk.getHeightmaps().stream().filter((entry) -> {
            return ((Heightmap.Types) entry.getKey()).sendToClient();
        }).collect(Collectors.toMap(Entry::getKey, (entry) -> {
            return (long[]) ((Heightmap) entry.getValue()).getRawData().clone();
        }));
        this.buffer = new byte[calculateChunkSize(levelChunk)];
        extractChunkData(new FriendlyByteBuf(this.getWriteBuffer()), levelChunk);
        this.blockEntitiesData = Lists.newArrayList();

        for (Map.Entry<BlockPos, BlockEntity> map_entry : levelChunk.getBlockEntities().entrySet()) {
            this.blockEntitiesData.add(ClientboundLevelChunkPacketData.BlockEntityInfo.create((BlockEntity) map_entry.getValue()));
        }

    }

    public ClientboundLevelChunkPacketData(RegistryFriendlyByteBuf input, int x, int z) {
        this.heightmaps = (Map) ClientboundLevelChunkPacketData.HEIGHTMAPS_STREAM_CODEC.decode(input);
        int k = input.readVarInt();

        if (k > 2097152) {
            throw new RuntimeException("Chunk Packet trying to allocate too much memory on read.");
        } else {
            this.buffer = new byte[k];
            input.readBytes(this.buffer);
            this.blockEntitiesData = (List) ClientboundLevelChunkPacketData.BlockEntityInfo.LIST_STREAM_CODEC.decode(input);
        }
    }

    public void write(RegistryFriendlyByteBuf output) {
        ClientboundLevelChunkPacketData.HEIGHTMAPS_STREAM_CODEC.encode(output, this.heightmaps);
        output.writeVarInt(this.buffer.length);
        output.writeBytes(this.buffer);
        ClientboundLevelChunkPacketData.BlockEntityInfo.LIST_STREAM_CODEC.encode(output, this.blockEntitiesData);
    }

    private static int calculateChunkSize(LevelChunk chunk) {
        int i = 0;

        for (LevelChunkSection levelchunksection : chunk.getSections()) {
            i += levelchunksection.getSerializedSize();
        }

        return i;
    }

    private ByteBuf getWriteBuffer() {
        ByteBuf bytebuf = Unpooled.wrappedBuffer(this.buffer);

        bytebuf.writerIndex(0);
        return bytebuf;
    }

    public static void extractChunkData(FriendlyByteBuf buffer, LevelChunk chunk) {
        for (LevelChunkSection levelchunksection : chunk.getSections()) {
            levelchunksection.write(buffer);
        }

        if (buffer.writerIndex() != buffer.capacity()) {
            int i = buffer.capacity();

            throw new IllegalStateException("Didn't fill chunk buffer: expected " + i + " bytes, got " + buffer.writerIndex());
        }
    }

    public Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> getBlockEntitiesTagsConsumer(int x, int z) {
        return (clientboundlevelchunkpacketdata_blockentitytagoutput) -> {
            this.getBlockEntitiesTags(clientboundlevelchunkpacketdata_blockentitytagoutput, x, z);
        };
    }

    private void getBlockEntitiesTags(ClientboundLevelChunkPacketData.BlockEntityTagOutput output, int x, int z) {
        int k = 16 * x;
        int l = 16 * z;
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

        for (ClientboundLevelChunkPacketData.BlockEntityInfo clientboundlevelchunkpacketdata_blockentityinfo : this.blockEntitiesData) {
            int i1 = k + SectionPos.sectionRelative(clientboundlevelchunkpacketdata_blockentityinfo.packedXZ >> 4);
            int j1 = l + SectionPos.sectionRelative(clientboundlevelchunkpacketdata_blockentityinfo.packedXZ);

            blockpos_mutableblockpos.set(i1, clientboundlevelchunkpacketdata_blockentityinfo.y, j1);
            output.accept(blockpos_mutableblockpos, clientboundlevelchunkpacketdata_blockentityinfo.type, clientboundlevelchunkpacketdata_blockentityinfo.tag);
        }

    }

    public FriendlyByteBuf getReadBuffer() {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(this.buffer));
    }

    public Map<Heightmap.Types, long[]> getHeightmaps() {
        return this.heightmaps;
    }

    private static class BlockEntityInfo {

        public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundLevelChunkPacketData.BlockEntityInfo> STREAM_CODEC = StreamCodec.<RegistryFriendlyByteBuf, ClientboundLevelChunkPacketData.BlockEntityInfo>ofMember(ClientboundLevelChunkPacketData.BlockEntityInfo::write, ClientboundLevelChunkPacketData.BlockEntityInfo::new);
        public static final StreamCodec<RegistryFriendlyByteBuf, List<ClientboundLevelChunkPacketData.BlockEntityInfo>> LIST_STREAM_CODEC = ClientboundLevelChunkPacketData.BlockEntityInfo.STREAM_CODEC.apply(ByteBufCodecs.list());
        private final int packedXZ;
        private final int y;
        private final BlockEntityType<?> type;
        private final @Nullable CompoundTag tag;

        private BlockEntityInfo(int packedXZ, int y, BlockEntityType<?> type, @Nullable CompoundTag tag) {
            this.packedXZ = packedXZ;
            this.y = y;
            this.type = type;
            this.tag = tag;
        }

        private BlockEntityInfo(RegistryFriendlyByteBuf input) {
            this.packedXZ = input.readByte();
            this.y = input.readShort();
            this.type = (BlockEntityType) ByteBufCodecs.registry(Registries.BLOCK_ENTITY_TYPE).decode(input);
            this.tag = input.readNbt();
        }

        private void write(RegistryFriendlyByteBuf output) {
            output.writeByte(this.packedXZ);
            output.writeShort(this.y);
            ByteBufCodecs.registry(Registries.BLOCK_ENTITY_TYPE).encode(output, this.type);
            output.writeNbt(this.tag);
        }

        private static ClientboundLevelChunkPacketData.BlockEntityInfo create(BlockEntity blockEntity) {
            CompoundTag compoundtag = blockEntity.getUpdateTag(blockEntity.getLevel().registryAccess());
            BlockPos blockpos = blockEntity.getBlockPos();
            int i = SectionPos.sectionRelative(blockpos.getX()) << 4 | SectionPos.sectionRelative(blockpos.getZ());

            return new ClientboundLevelChunkPacketData.BlockEntityInfo(i, blockpos.getY(), blockEntity.getType(), compoundtag.isEmpty() ? null : compoundtag);
        }
    }

    @FunctionalInterface
    public interface BlockEntityTagOutput {

        void accept(BlockPos pos, BlockEntityType<?> type, @Nullable CompoundTag tag);
    }
}
