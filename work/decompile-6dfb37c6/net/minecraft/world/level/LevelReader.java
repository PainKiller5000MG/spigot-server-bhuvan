package net.minecraft.world.level;

import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributeReader;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public interface LevelReader extends BlockAndTintGetter, CollisionGetter, SignalGetter, BiomeManager.NoiseBiomeSource {

    @Nullable
    ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus targetStatus, boolean loadOrGenerate);

    /** @deprecated */
    @Deprecated
    boolean hasChunk(int chunkX, int chunkZ);

    int getHeight(Heightmap.Types type, int x, int z);

    default int getHeight(Heightmap.Types type, BlockPos pos) {
        return this.getHeight(type, pos.getX(), pos.getZ());
    }

    int getSkyDarken();

    BiomeManager getBiomeManager();

    default Holder<Biome> getBiome(BlockPos pos) {
        return this.getBiomeManager().getBiome(pos);
    }

    default Stream<BlockState> getBlockStatesIfLoaded(AABB box) {
        int i = Mth.floor(box.minX);
        int j = Mth.floor(box.maxX);
        int k = Mth.floor(box.minY);
        int l = Mth.floor(box.maxY);
        int i1 = Mth.floor(box.minZ);
        int j1 = Mth.floor(box.maxZ);

        return this.hasChunksAt(i, k, i1, j, l, j1) ? this.getBlockStates(box) : Stream.empty();
    }

    @Override
    default int getBlockTint(BlockPos pos, ColorResolver resolver) {
        return resolver.getColor((Biome) this.getBiome(pos).value(), (double) pos.getX(), (double) pos.getZ());
    }

    @Override
    default Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ) {
        ChunkAccess chunkaccess = this.getChunk(QuartPos.toSection(quartX), QuartPos.toSection(quartZ), ChunkStatus.BIOMES, false);

        return chunkaccess != null ? chunkaccess.getNoiseBiome(quartX, quartY, quartZ) : this.getUncachedNoiseBiome(quartX, quartY, quartZ);
    }

    Holder<Biome> getUncachedNoiseBiome(int quartX, int quartY, int quartZ);

    boolean isClientSide();

    int getSeaLevel();

    DimensionType dimensionType();

    @Override
    default int getMinY() {
        return this.dimensionType().minY();
    }

    @Override
    default int getHeight() {
        return this.dimensionType().height();
    }

    default BlockPos getHeightmapPos(Heightmap.Types type, BlockPos pos) {
        return new BlockPos(pos.getX(), this.getHeight(type, pos.getX(), pos.getZ()), pos.getZ());
    }

    default boolean isEmptyBlock(BlockPos pos) {
        return this.getBlockState(pos).isAir();
    }

    default boolean canSeeSkyFromBelowWater(BlockPos pos) {
        if (pos.getY() >= this.getSeaLevel()) {
            return this.canSeeSky(pos);
        } else {
            BlockPos blockpos1 = new BlockPos(pos.getX(), this.getSeaLevel(), pos.getZ());

            if (!this.canSeeSky(blockpos1)) {
                return false;
            } else {
                for (BlockPos blockpos2 = blockpos1.below(); blockpos2.getY() > pos.getY(); blockpos2 = blockpos2.below()) {
                    BlockState blockstate = this.getBlockState(blockpos2);

                    if (blockstate.getLightBlock() > 0 && !blockstate.liquid()) {
                        return false;
                    }
                }

                return true;
            }
        }
    }

    default float getPathfindingCostFromLightLevels(BlockPos pos) {
        return this.getLightLevelDependentMagicValue(pos) - 0.5F;
    }

    /** @deprecated */
    @Deprecated
    default float getLightLevelDependentMagicValue(BlockPos pos) {
        float f = (float) this.getMaxLocalRawBrightness(pos) / 15.0F;
        float f1 = f / (4.0F - 3.0F * f);

        return Mth.lerp(this.dimensionType().ambientLight(), f1, 1.0F);
    }

    default ChunkAccess getChunk(BlockPos pos) {
        return this.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    default ChunkAccess getChunk(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
    }

    default ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus status) {
        return this.getChunk(chunkX, chunkZ, status, true);
    }

    @Override
    default @Nullable BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ, ChunkStatus.EMPTY, false);
    }

    default boolean isWaterAt(BlockPos pos) {
        return this.getFluidState(pos).is(FluidTags.WATER);
    }

    default boolean containsAnyLiquid(AABB box) {
        int i = Mth.floor(box.minX);
        int j = Mth.ceil(box.maxX);
        int k = Mth.floor(box.minY);
        int l = Mth.ceil(box.maxY);
        int i1 = Mth.floor(box.minZ);
        int j1 = Mth.ceil(box.maxZ);
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

        for (int k1 = i; k1 < j; ++k1) {
            for (int l1 = k; l1 < l; ++l1) {
                for (int i2 = i1; i2 < j1; ++i2) {
                    BlockState blockstate = this.getBlockState(blockpos_mutableblockpos.set(k1, l1, i2));

                    if (!blockstate.getFluidState().isEmpty()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    default int getMaxLocalRawBrightness(BlockPos pos) {
        return this.getMaxLocalRawBrightness(pos, this.getSkyDarken());
    }

    default int getMaxLocalRawBrightness(BlockPos pos, int skyDarkening) {
        return pos.getX() >= -30000000 && pos.getZ() >= -30000000 && pos.getX() < 30000000 && pos.getZ() < 30000000 ? this.getRawBrightness(pos, skyDarkening) : 15;
    }

    /** @deprecated */
    @Deprecated
    default boolean hasChunkAt(int blockX, int blockZ) {
        return this.hasChunk(SectionPos.blockToSectionCoord(blockX), SectionPos.blockToSectionCoord(blockZ));
    }

    /** @deprecated */
    @Deprecated
    default boolean hasChunkAt(BlockPos pos) {
        return this.hasChunkAt(pos.getX(), pos.getZ());
    }

    /** @deprecated */
    @Deprecated
    default boolean hasChunksAt(BlockPos pos0, BlockPos pos1) {
        return this.hasChunksAt(pos0.getX(), pos0.getY(), pos0.getZ(), pos1.getX(), pos1.getY(), pos1.getZ());
    }

    /** @deprecated */
    @Deprecated
    default boolean hasChunksAt(int x0, int y0, int z0, int x1, int y1, int z1) {
        return y1 >= this.getMinY() && y0 <= this.getMaxY() ? this.hasChunksAt(x0, z0, x1, z1) : false;
    }

    /** @deprecated */
    @Deprecated
    default boolean hasChunksAt(int x0, int z0, int x1, int z1) {
        int i1 = SectionPos.blockToSectionCoord(x0);
        int j1 = SectionPos.blockToSectionCoord(x1);
        int k1 = SectionPos.blockToSectionCoord(z0);
        int l1 = SectionPos.blockToSectionCoord(z1);

        for (int i2 = i1; i2 <= j1; ++i2) {
            for (int j2 = k1; j2 <= l1; ++j2) {
                if (!this.hasChunk(i2, j2)) {
                    return false;
                }
            }
        }

        return true;
    }

    RegistryAccess registryAccess();

    FeatureFlagSet enabledFeatures();

    default <T> HolderLookup<T> holderLookup(ResourceKey<? extends Registry<? extends T>> key) {
        Registry<T> registry = this.registryAccess().lookupOrThrow(key);

        return registry.filterFeatures(this.enabledFeatures());
    }

    EnvironmentAttributeReader environmentAttributes();
}
