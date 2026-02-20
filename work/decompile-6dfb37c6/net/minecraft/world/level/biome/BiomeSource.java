package net.minecraft.world.level.biome;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import org.jspecify.annotations.Nullable;

public abstract class BiomeSource implements BiomeResolver {

    public static final Codec<BiomeSource> CODEC = BuiltInRegistries.BIOME_SOURCE.byNameCodec().dispatchStable(BiomeSource::codec, Function.identity());
    private final Supplier<Set<Holder<Biome>>> possibleBiomes = Suppliers.memoize(() -> {
        return (Set) this.collectPossibleBiomes().distinct().collect(ImmutableSet.toImmutableSet());
    });

    protected BiomeSource() {}

    protected abstract MapCodec<? extends BiomeSource> codec();

    protected abstract Stream<Holder<Biome>> collectPossibleBiomes();

    public Set<Holder<Biome>> possibleBiomes() {
        return (Set) this.possibleBiomes.get();
    }

    public Set<Holder<Biome>> getBiomesWithin(int x, int y, int z, int r, Climate.Sampler sampler) {
        int i1 = QuartPos.fromBlock(x - r);
        int j1 = QuartPos.fromBlock(y - r);
        int k1 = QuartPos.fromBlock(z - r);
        int l1 = QuartPos.fromBlock(x + r);
        int i2 = QuartPos.fromBlock(y + r);
        int j2 = QuartPos.fromBlock(z + r);
        int k2 = l1 - i1 + 1;
        int l2 = i2 - j1 + 1;
        int i3 = j2 - k1 + 1;
        Set<Holder<Biome>> set = Sets.newHashSet();

        for (int j3 = 0; j3 < i3; ++j3) {
            for (int k3 = 0; k3 < k2; ++k3) {
                for (int l3 = 0; l3 < l2; ++l3) {
                    int i4 = i1 + k3;
                    int j4 = j1 + l3;
                    int k4 = k1 + j3;

                    set.add(this.getNoiseBiome(i4, j4, k4, sampler));
                }
            }
        }

        return set;
    }

    public @Nullable Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(int x, int y, int z, int searchRadius, Predicate<Holder<Biome>> allowed, RandomSource random, Climate.Sampler sampler) {
        return this.findBiomeHorizontal(x, y, z, searchRadius, 1, allowed, random, false, sampler);
    }

    public @Nullable Pair<BlockPos, Holder<Biome>> findClosestBiome3d(BlockPos origin, int searchRadius, int sampleResolutionHorizontal, int sampleResolutionVertical, Predicate<Holder<Biome>> allowed, Climate.Sampler sampler, LevelReader level) {
        Set<Holder<Biome>> set = (Set) this.possibleBiomes().stream().filter(allowed).collect(Collectors.toUnmodifiableSet());

        if (set.isEmpty()) {
            return null;
        } else {
            int l = Math.floorDiv(searchRadius, sampleResolutionHorizontal);
            int[] aint = Mth.outFromOrigin(origin.getY(), level.getMinY() + 1, level.getMaxY() + 1, sampleResolutionVertical).toArray();

            for (BlockPos.MutableBlockPos blockpos_mutableblockpos : BlockPos.spiralAround(BlockPos.ZERO, l, Direction.EAST, Direction.SOUTH)) {
                int i1 = origin.getX() + blockpos_mutableblockpos.getX() * sampleResolutionHorizontal;
                int j1 = origin.getZ() + blockpos_mutableblockpos.getZ() * sampleResolutionHorizontal;
                int k1 = QuartPos.fromBlock(i1);
                int l1 = QuartPos.fromBlock(j1);

                for (int i2 : aint) {
                    int j2 = QuartPos.fromBlock(i2);
                    Holder<Biome> holder = this.getNoiseBiome(k1, j2, l1, sampler);

                    if (set.contains(holder)) {
                        return Pair.of(new BlockPos(i1, i2, j1), holder);
                    }
                }
            }

            return null;
        }
    }

    public @Nullable Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(int originX, int originY, int originZ, int searchRadius, int skipSteps, Predicate<Holder<Biome>> allowed, RandomSource random, boolean findClosest, Climate.Sampler sampler) {
        int j1 = QuartPos.fromBlock(originX);
        int k1 = QuartPos.fromBlock(originZ);
        int l1 = QuartPos.fromBlock(searchRadius);
        int i2 = QuartPos.fromBlock(originY);
        Pair<BlockPos, Holder<Biome>> pair = null;
        int j2 = 0;
        int k2 = findClosest ? 0 : l1;

        for (int l2 = k2; l2 <= l1; l2 += skipSteps) {
            for (int i3 = !SharedConstants.DEBUG_ONLY_GENERATE_HALF_THE_WORLD && !SharedConstants.debugGenerateSquareTerrainWithoutNoise ? -l2 : 0; i3 <= l2; i3 += skipSteps) {
                boolean flag1 = Math.abs(i3) == l2;

                for (int j3 = -l2; j3 <= l2; j3 += skipSteps) {
                    if (findClosest) {
                        boolean flag2 = Math.abs(j3) == l2;

                        if (!flag2 && !flag1) {
                            continue;
                        }
                    }

                    int k3 = j1 + j3;
                    int l3 = k1 + i3;
                    Holder<Biome> holder = this.getNoiseBiome(k3, i2, l3, sampler);

                    if (allowed.test(holder)) {
                        if (pair == null || random.nextInt(j2 + 1) == 0) {
                            BlockPos blockpos = new BlockPos(QuartPos.toBlock(k3), originY, QuartPos.toBlock(l3));

                            if (findClosest) {
                                return Pair.of(blockpos, holder);
                            }

                            pair = Pair.of(blockpos, holder);
                        }

                        ++j2;
                    }
                }
            }
        }

        return pair;
    }

    @Override
    public abstract Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler);

    public void addDebugInfo(List<String> result, BlockPos feetPos, Climate.Sampler sampler) {}
}
