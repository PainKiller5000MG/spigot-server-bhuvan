package net.minecraft.world.level;

import com.google.common.base.Suppliers;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class PathNavigationRegion implements CollisionGetter {

    protected final int centerX;
    protected final int centerZ;
    protected final ChunkAccess[][] chunks;
    protected boolean allEmpty;
    protected final Level level;
    private final Supplier<Holder<Biome>> plains;

    public PathNavigationRegion(Level level, BlockPos start, BlockPos end) {
        this.level = level;
        this.plains = Suppliers.memoize(() -> {
            return level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        });
        this.centerX = SectionPos.blockToSectionCoord(start.getX());
        this.centerZ = SectionPos.blockToSectionCoord(start.getZ());
        int i = SectionPos.blockToSectionCoord(end.getX());
        int j = SectionPos.blockToSectionCoord(end.getZ());

        this.chunks = new ChunkAccess[i - this.centerX + 1][j - this.centerZ + 1];
        ChunkSource chunksource = level.getChunkSource();

        this.allEmpty = true;

        for (int k = this.centerX; k <= i; ++k) {
            for (int l = this.centerZ; l <= j; ++l) {
                this.chunks[k - this.centerX][l - this.centerZ] = chunksource.getChunkNow(k, l);
            }
        }

        for (int i1 = SectionPos.blockToSectionCoord(start.getX()); i1 <= SectionPos.blockToSectionCoord(end.getX()); ++i1) {
            for (int j1 = SectionPos.blockToSectionCoord(start.getZ()); j1 <= SectionPos.blockToSectionCoord(end.getZ()); ++j1) {
                ChunkAccess chunkaccess = this.chunks[i1 - this.centerX][j1 - this.centerZ];

                if (chunkaccess != null && !chunkaccess.isYSpaceEmpty(start.getY(), end.getY())) {
                    this.allEmpty = false;
                    return;
                }
            }
        }

    }

    private ChunkAccess getChunk(BlockPos pos) {
        return this.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    private ChunkAccess getChunk(int chunkX, int chunkZ) {
        int k = chunkX - this.centerX;
        int l = chunkZ - this.centerZ;

        if (k >= 0 && k < this.chunks.length && l >= 0 && l < this.chunks[k].length) {
            ChunkAccess chunkaccess = this.chunks[k][l];

            return (ChunkAccess) (chunkaccess != null ? chunkaccess : new EmptyLevelChunk(this.level, new ChunkPos(chunkX, chunkZ), (Holder) this.plains.get()));
        } else {
            return new EmptyLevelChunk(this.level, new ChunkPos(chunkX, chunkZ), (Holder) this.plains.get());
        }
    }

    @Override
    public WorldBorder getWorldBorder() {
        return this.level.getWorldBorder();
    }

    @Override
    public BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ);
    }

    @Override
    public List<VoxelShape> getEntityCollisions(@Nullable Entity source, AABB testArea) {
        return List.of();
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        ChunkAccess chunkaccess = this.getChunk(pos);

        return chunkaccess.getBlockEntity(pos);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (this.isOutsideBuildHeight(pos)) {
            return Blocks.AIR.defaultBlockState();
        } else {
            ChunkAccess chunkaccess = this.getChunk(pos);

            return chunkaccess.getBlockState(pos);
        }
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        if (this.isOutsideBuildHeight(pos)) {
            return Fluids.EMPTY.defaultFluidState();
        } else {
            ChunkAccess chunkaccess = this.getChunk(pos);

            return chunkaccess.getFluidState(pos);
        }
    }

    @Override
    public int getMinY() {
        return this.level.getMinY();
    }

    @Override
    public int getHeight() {
        return this.level.getHeight();
    }
}
