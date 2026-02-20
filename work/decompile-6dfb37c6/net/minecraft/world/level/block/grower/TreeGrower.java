package net.minecraft.world.level.block.grower;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.jspecify.annotations.Nullable;

public final class TreeGrower {

    private static final Map<String, TreeGrower> GROWERS = new Object2ObjectArrayMap();
    public static final Codec<TreeGrower> CODEC;
    public static final TreeGrower OAK;
    public static final TreeGrower SPRUCE;
    public static final TreeGrower MANGROVE;
    public static final TreeGrower AZALEA;
    public static final TreeGrower BIRCH;
    public static final TreeGrower JUNGLE;
    public static final TreeGrower ACACIA;
    public static final TreeGrower CHERRY;
    public static final TreeGrower DARK_OAK;
    public static final TreeGrower PALE_OAK;
    private final String name;
    private final float secondaryChance;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> megaTree;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> secondaryMegaTree;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> tree;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> secondaryTree;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> flowers;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> secondaryFlowers;

    public TreeGrower(String name, Optional<ResourceKey<ConfiguredFeature<?, ?>>> megaTree, Optional<ResourceKey<ConfiguredFeature<?, ?>>> tree, Optional<ResourceKey<ConfiguredFeature<?, ?>>> flowers) {
        this(name, 0.0F, megaTree, Optional.empty(), tree, Optional.empty(), flowers, Optional.empty());
    }

    public TreeGrower(String name, float secondaryChance, Optional<ResourceKey<ConfiguredFeature<?, ?>>> megaTree, Optional<ResourceKey<ConfiguredFeature<?, ?>>> secondaryMegaTree, Optional<ResourceKey<ConfiguredFeature<?, ?>>> tree, Optional<ResourceKey<ConfiguredFeature<?, ?>>> secondaryTree, Optional<ResourceKey<ConfiguredFeature<?, ?>>> flowers, Optional<ResourceKey<ConfiguredFeature<?, ?>>> secondaryFlowers) {
        this.name = name;
        this.secondaryChance = secondaryChance;
        this.megaTree = megaTree;
        this.secondaryMegaTree = secondaryMegaTree;
        this.tree = tree;
        this.secondaryTree = secondaryTree;
        this.flowers = flowers;
        this.secondaryFlowers = secondaryFlowers;
        TreeGrower.GROWERS.put(name, this);
    }

    private @Nullable ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(RandomSource random, boolean hasFlowers) {
        if (random.nextFloat() < this.secondaryChance) {
            if (hasFlowers && this.secondaryFlowers.isPresent()) {
                return (ResourceKey) this.secondaryFlowers.get();
            }

            if (this.secondaryTree.isPresent()) {
                return (ResourceKey) this.secondaryTree.get();
            }
        }

        return hasFlowers && this.flowers.isPresent() ? (ResourceKey) this.flowers.get() : (ResourceKey) this.tree.orElse((Object) null);
    }

    private @Nullable ResourceKey<ConfiguredFeature<?, ?>> getConfiguredMegaFeature(RandomSource random) {
        return this.secondaryMegaTree.isPresent() && random.nextFloat() < this.secondaryChance ? (ResourceKey) this.secondaryMegaTree.get() : (ResourceKey) this.megaTree.orElse((Object) null);
    }

    public boolean growTree(ServerLevel level, ChunkGenerator generator, BlockPos pos, BlockState state, RandomSource random) {
        ResourceKey<ConfiguredFeature<?, ?>> resourcekey = this.getConfiguredMegaFeature(random);

        if (resourcekey != null) {
            Holder<ConfiguredFeature<?, ?>> holder = (Holder) level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).get(resourcekey).orElse((Object) null);

            if (holder != null) {
                for (int i = 0; i >= -1; --i) {
                    for (int j = 0; j >= -1; --j) {
                        if (isTwoByTwoSapling(state, level, pos, i, j)) {
                            ConfiguredFeature<?, ?> configuredfeature = holder.value();
                            BlockState blockstate1 = Blocks.AIR.defaultBlockState();

                            level.setBlock(pos.offset(i, 0, j), blockstate1, 260);
                            level.setBlock(pos.offset(i + 1, 0, j), blockstate1, 260);
                            level.setBlock(pos.offset(i, 0, j + 1), blockstate1, 260);
                            level.setBlock(pos.offset(i + 1, 0, j + 1), blockstate1, 260);
                            if (configuredfeature.place(level, generator, random, pos.offset(i, 0, j))) {
                                return true;
                            }

                            level.setBlock(pos.offset(i, 0, j), state, 260);
                            level.setBlock(pos.offset(i + 1, 0, j), state, 260);
                            level.setBlock(pos.offset(i, 0, j + 1), state, 260);
                            level.setBlock(pos.offset(i + 1, 0, j + 1), state, 260);
                            return false;
                        }
                    }
                }
            }
        }

        ResourceKey<ConfiguredFeature<?, ?>> resourcekey1 = this.getConfiguredFeature(random, this.hasFlowers(level, pos));

        if (resourcekey1 == null) {
            return false;
        } else {
            Holder<ConfiguredFeature<?, ?>> holder1 = (Holder) level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).get(resourcekey1).orElse((Object) null);

            if (holder1 == null) {
                return false;
            } else {
                ConfiguredFeature<?, ?> configuredfeature1 = holder1.value();
                BlockState blockstate2 = level.getFluidState(pos).createLegacyBlock();

                level.setBlock(pos, blockstate2, 260);
                if (configuredfeature1.place(level, generator, random, pos)) {
                    if (level.getBlockState(pos) == blockstate2) {
                        level.sendBlockUpdated(pos, state, blockstate2, 2);
                    }

                    return true;
                } else {
                    level.setBlock(pos, state, 260);
                    return false;
                }
            }
        }
    }

    private static boolean isTwoByTwoSapling(BlockState state, BlockGetter level, BlockPos pos, int ox, int oz) {
        Block block = state.getBlock();

        return level.getBlockState(pos.offset(ox, 0, oz)).is(block) && level.getBlockState(pos.offset(ox + 1, 0, oz)).is(block) && level.getBlockState(pos.offset(ox, 0, oz + 1)).is(block) && level.getBlockState(pos.offset(ox + 1, 0, oz + 1)).is(block);
    }

    private boolean hasFlowers(LevelAccessor level, BlockPos pos) {
        for (BlockPos blockpos1 : BlockPos.MutableBlockPos.betweenClosed(pos.below().north(2).west(2), pos.above().south(2).east(2))) {
            if (level.getBlockState(blockpos1).is(BlockTags.FLOWERS)) {
                return true;
            }
        }

        return false;
    }

    static {
        Function function = (treegrower) -> {
            return treegrower.name;
        };
        Map map = TreeGrower.GROWERS;

        Objects.requireNonNull(map);
        CODEC = Codec.stringResolver(function, map::get);
        OAK = new TreeGrower("oak", 0.1F, Optional.empty(), Optional.empty(), Optional.of(TreeFeatures.OAK), Optional.of(TreeFeatures.FANCY_OAK), Optional.of(TreeFeatures.OAK_BEES_005), Optional.of(TreeFeatures.FANCY_OAK_BEES_005));
        SPRUCE = new TreeGrower("spruce", 0.5F, Optional.of(TreeFeatures.MEGA_SPRUCE), Optional.of(TreeFeatures.MEGA_PINE), Optional.of(TreeFeatures.SPRUCE), Optional.empty(), Optional.empty(), Optional.empty());
        MANGROVE = new TreeGrower("mangrove", 0.85F, Optional.empty(), Optional.empty(), Optional.of(TreeFeatures.MANGROVE), Optional.of(TreeFeatures.TALL_MANGROVE), Optional.empty(), Optional.empty());
        AZALEA = new TreeGrower("azalea", Optional.empty(), Optional.of(TreeFeatures.AZALEA_TREE), Optional.empty());
        BIRCH = new TreeGrower("birch", Optional.empty(), Optional.of(TreeFeatures.BIRCH), Optional.of(TreeFeatures.BIRCH_BEES_005));
        JUNGLE = new TreeGrower("jungle", Optional.of(TreeFeatures.MEGA_JUNGLE_TREE), Optional.of(TreeFeatures.JUNGLE_TREE_NO_VINE), Optional.empty());
        ACACIA = new TreeGrower("acacia", Optional.empty(), Optional.of(TreeFeatures.ACACIA), Optional.empty());
        CHERRY = new TreeGrower("cherry", Optional.empty(), Optional.of(TreeFeatures.CHERRY), Optional.of(TreeFeatures.CHERRY_BEES_005));
        DARK_OAK = new TreeGrower("dark_oak", Optional.of(TreeFeatures.DARK_OAK), Optional.empty(), Optional.empty());
        PALE_OAK = new TreeGrower("pale_oak", Optional.of(TreeFeatures.PALE_OAK_BONEMEAL), Optional.empty(), Optional.empty());
    }
}
