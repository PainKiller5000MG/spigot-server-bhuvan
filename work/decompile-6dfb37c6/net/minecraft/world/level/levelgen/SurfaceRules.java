package net.minecraft.world.level.levelgen;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.jspecify.annotations.Nullable;

public class SurfaceRules {

    public static final SurfaceRules.ConditionSource ON_FLOOR = stoneDepthCheck(0, false, CaveSurface.FLOOR);
    public static final SurfaceRules.ConditionSource UNDER_FLOOR = stoneDepthCheck(0, true, CaveSurface.FLOOR);
    public static final SurfaceRules.ConditionSource DEEP_UNDER_FLOOR = stoneDepthCheck(0, true, 6, CaveSurface.FLOOR);
    public static final SurfaceRules.ConditionSource VERY_DEEP_UNDER_FLOOR = stoneDepthCheck(0, true, 30, CaveSurface.FLOOR);
    public static final SurfaceRules.ConditionSource ON_CEILING = stoneDepthCheck(0, false, CaveSurface.CEILING);
    public static final SurfaceRules.ConditionSource UNDER_CEILING = stoneDepthCheck(0, true, CaveSurface.CEILING);

    public SurfaceRules() {}

    public static SurfaceRules.ConditionSource stoneDepthCheck(int offset, boolean addSurfaceDepth1, CaveSurface surfaceType) {
        return new SurfaceRules.StoneDepthCheck(offset, addSurfaceDepth1, 0, surfaceType);
    }

    public static SurfaceRules.ConditionSource stoneDepthCheck(int offset, boolean addSurfaceDepth1, int secondaryDepthRange, CaveSurface surfaceType) {
        return new SurfaceRules.StoneDepthCheck(offset, addSurfaceDepth1, secondaryDepthRange, surfaceType);
    }

    public static SurfaceRules.ConditionSource not(SurfaceRules.ConditionSource target) {
        return new SurfaceRules.NotConditionSource(target);
    }

    public static SurfaceRules.ConditionSource yBlockCheck(VerticalAnchor anchor, int surfaceDepthMultiplier) {
        return new SurfaceRules.YConditionSource(anchor, surfaceDepthMultiplier, false);
    }

    public static SurfaceRules.ConditionSource yStartCheck(VerticalAnchor anchor, int surfaceDepthMultiplier) {
        return new SurfaceRules.YConditionSource(anchor, surfaceDepthMultiplier, true);
    }

    public static SurfaceRules.ConditionSource waterBlockCheck(int offset, int surfaceDepthMultiplier) {
        return new SurfaceRules.WaterConditionSource(offset, surfaceDepthMultiplier, false);
    }

    public static SurfaceRules.ConditionSource waterStartCheck(int offset, int surfaceDepthMultiplier) {
        return new SurfaceRules.WaterConditionSource(offset, surfaceDepthMultiplier, true);
    }

    @SafeVarargs
    public static SurfaceRules.ConditionSource isBiome(ResourceKey<Biome>... target) {
        return isBiome(List.of(target));
    }

    private static SurfaceRules.BiomeConditionSource isBiome(List<ResourceKey<Biome>> target) {
        return new SurfaceRules.BiomeConditionSource(target);
    }

    public static SurfaceRules.ConditionSource noiseCondition(ResourceKey<NormalNoise.NoiseParameters> noise, double minRange) {
        return noiseCondition(noise, minRange, Double.MAX_VALUE);
    }

    public static SurfaceRules.ConditionSource noiseCondition(ResourceKey<NormalNoise.NoiseParameters> noise, double minRange, double maxRange) {
        return new SurfaceRules.NoiseThresholdConditionSource(noise, minRange, maxRange);
    }

    public static SurfaceRules.ConditionSource verticalGradient(String randomName, VerticalAnchor trueAtAndBelow, VerticalAnchor falseAtAndAbove) {
        return new SurfaceRules.VerticalGradientConditionSource(Identifier.parse(randomName), trueAtAndBelow, falseAtAndAbove);
    }

    public static SurfaceRules.ConditionSource steep() {
        return SurfaceRules.Steep.INSTANCE;
    }

    public static SurfaceRules.ConditionSource hole() {
        return SurfaceRules.Hole.INSTANCE;
    }

    public static SurfaceRules.ConditionSource abovePreliminarySurface() {
        return SurfaceRules.AbovePreliminarySurface.INSTANCE;
    }

    public static SurfaceRules.ConditionSource temperature() {
        return SurfaceRules.Temperature.INSTANCE;
    }

    public static SurfaceRules.RuleSource ifTrue(SurfaceRules.ConditionSource condition, SurfaceRules.RuleSource next) {
        return new SurfaceRules.TestRuleSource(condition, next);
    }

    public static SurfaceRules.RuleSource sequence(SurfaceRules.RuleSource... rules) {
        if (rules.length == 0) {
            throw new IllegalArgumentException("Need at least 1 rule for a sequence");
        } else {
            return new SurfaceRules.SequenceRuleSource(Arrays.asList(rules));
        }
    }

    public static SurfaceRules.RuleSource state(BlockState state) {
        return new SurfaceRules.BlockRuleSource(state);
    }

    public static SurfaceRules.RuleSource bandlands() {
        return SurfaceRules.Bandlands.INSTANCE;
    }

    private static <A> MapCodec<? extends A> register(Registry<MapCodec<? extends A>> registry, String name, KeyDispatchDataCodec<? extends A> codec) {
        return (MapCodec) Registry.register(registry, name, codec.codec());
    }

    protected static final class Context {

        private static final int HOW_FAR_BELOW_PRELIMINARY_SURFACE_LEVEL_TO_BUILD_SURFACE = 8;
        private static final int SURFACE_CELL_BITS = 4;
        private static final int SURFACE_CELL_SIZE = 16;
        private static final int SURFACE_CELL_MASK = 15;
        private final SurfaceSystem system;
        private final SurfaceRules.Condition temperature = new SurfaceRules.Context.TemperatureHelperCondition(this);
        private final SurfaceRules.Condition steep = new SurfaceRules.Context.SteepMaterialCondition(this);
        private final SurfaceRules.Condition hole = new SurfaceRules.Context.HoleCondition(this);
        private final SurfaceRules.Condition abovePreliminarySurface = new SurfaceRules.Context.AbovePreliminarySurfaceCondition();
        private final RandomState randomState;
        private final ChunkAccess chunk;
        private final NoiseChunk noiseChunk;
        private final Function<BlockPos, Holder<Biome>> biomeGetter;
        private final WorldGenerationContext context;
        private long lastPreliminarySurfaceCellOrigin = Long.MAX_VALUE;
        private final int[] preliminarySurfaceCache = new int[4];
        private long lastUpdateXZ = -9223372036854775807L;
        private int blockX;
        private int blockZ;
        private int surfaceDepth;
        private long lastSurfaceDepth2Update;
        private double surfaceSecondary;
        private long lastMinSurfaceLevelUpdate;
        private int minSurfaceLevel;
        private long lastUpdateY;
        private final BlockPos.MutableBlockPos pos;
        private Supplier<Holder<Biome>> biome;
        private int blockY;
        private int waterHeight;
        private int stoneDepthBelow;
        private int stoneDepthAbove;

        protected Context(SurfaceSystem system, RandomState randomState, ChunkAccess chunk, NoiseChunk noiseChunk, Function<BlockPos, Holder<Biome>> biomeGetter, Registry<Biome> biomes, WorldGenerationContext context) {
            this.lastSurfaceDepth2Update = this.lastUpdateXZ - 1L;
            this.lastMinSurfaceLevelUpdate = this.lastUpdateXZ - 1L;
            this.lastUpdateY = -9223372036854775807L;
            this.pos = new BlockPos.MutableBlockPos();
            this.system = system;
            this.randomState = randomState;
            this.chunk = chunk;
            this.noiseChunk = noiseChunk;
            this.biomeGetter = biomeGetter;
            this.context = context;
        }

        protected void updateXZ(int blockX, int blockZ) {
            ++this.lastUpdateXZ;
            ++this.lastUpdateY;
            this.blockX = blockX;
            this.blockZ = blockZ;
            this.surfaceDepth = this.system.getSurfaceDepth(blockX, blockZ);
        }

        protected void updateY(int stoneDepthAbove, int stoneDepthBelow, int waterHeight, int blockX, int blockY, int blockZ) {
            ++this.lastUpdateY;
            this.biome = Suppliers.memoize(() -> {
                return (Holder) this.biomeGetter.apply(this.pos.set(blockX, blockY, blockZ));
            });
            this.blockY = blockY;
            this.waterHeight = waterHeight;
            this.stoneDepthBelow = stoneDepthBelow;
            this.stoneDepthAbove = stoneDepthAbove;
        }

        protected double getSurfaceSecondary() {
            if (this.lastSurfaceDepth2Update != this.lastUpdateXZ) {
                this.lastSurfaceDepth2Update = this.lastUpdateXZ;
                this.surfaceSecondary = this.system.getSurfaceSecondary(this.blockX, this.blockZ);
            }

            return this.surfaceSecondary;
        }

        public int getSeaLevel() {
            return this.system.getSeaLevel();
        }

        private static int blockCoordToSurfaceCell(int blockCoord) {
            return blockCoord >> 4;
        }

        private static int surfaceCellToBlockCoord(int cellCoord) {
            return cellCoord << 4;
        }

        protected int getMinSurfaceLevel() {
            if (this.lastMinSurfaceLevelUpdate != this.lastUpdateXZ) {
                this.lastMinSurfaceLevelUpdate = this.lastUpdateXZ;
                int i = blockCoordToSurfaceCell(this.blockX);
                int j = blockCoordToSurfaceCell(this.blockZ);
                long k = ChunkPos.asLong(i, j);

                if (this.lastPreliminarySurfaceCellOrigin != k) {
                    this.lastPreliminarySurfaceCellOrigin = k;
                    this.preliminarySurfaceCache[0] = this.noiseChunk.preliminarySurfaceLevel(surfaceCellToBlockCoord(i), surfaceCellToBlockCoord(j));
                    this.preliminarySurfaceCache[1] = this.noiseChunk.preliminarySurfaceLevel(surfaceCellToBlockCoord(i + 1), surfaceCellToBlockCoord(j));
                    this.preliminarySurfaceCache[2] = this.noiseChunk.preliminarySurfaceLevel(surfaceCellToBlockCoord(i), surfaceCellToBlockCoord(j + 1));
                    this.preliminarySurfaceCache[3] = this.noiseChunk.preliminarySurfaceLevel(surfaceCellToBlockCoord(i + 1), surfaceCellToBlockCoord(j + 1));
                }

                int l = Mth.floor(Mth.lerp2((double) ((float) (this.blockX & 15) / 16.0F), (double) ((float) (this.blockZ & 15) / 16.0F), (double) this.preliminarySurfaceCache[0], (double) this.preliminarySurfaceCache[1], (double) this.preliminarySurfaceCache[2], (double) this.preliminarySurfaceCache[3]));

                this.minSurfaceLevel = l + this.surfaceDepth - 8;
            }

            return this.minSurfaceLevel;
        }

        private static final class HoleCondition extends SurfaceRules.LazyXZCondition {

            private HoleCondition(SurfaceRules.Context context) {
                super(context);
            }

            @Override
            protected boolean compute() {
                return this.context.surfaceDepth <= 0;
            }
        }

        private final class AbovePreliminarySurfaceCondition implements SurfaceRules.Condition {

            private AbovePreliminarySurfaceCondition() {}

            @Override
            public boolean test() {
                return Context.this.blockY >= Context.this.getMinSurfaceLevel();
            }
        }

        private static class TemperatureHelperCondition extends SurfaceRules.LazyYCondition {

            private TemperatureHelperCondition(SurfaceRules.Context context) {
                super(context);
            }

            @Override
            protected boolean compute() {
                return ((Biome) ((Holder) this.context.biome.get()).value()).coldEnoughToSnow(this.context.pos.set(this.context.blockX, this.context.blockY, this.context.blockZ), this.context.getSeaLevel());
            }
        }

        private static class SteepMaterialCondition extends SurfaceRules.LazyXZCondition {

            private SteepMaterialCondition(SurfaceRules.Context context) {
                super(context);
            }

            @Override
            protected boolean compute() {
                int i = this.context.blockX & 15;
                int j = this.context.blockZ & 15;
                int k = Math.max(j - 1, 0);
                int l = Math.min(j + 1, 15);
                ChunkAccess chunkaccess = this.context.chunk;
                int i1 = chunkaccess.getHeight(Heightmap.Types.WORLD_SURFACE_WG, i, k);
                int j1 = chunkaccess.getHeight(Heightmap.Types.WORLD_SURFACE_WG, i, l);

                if (j1 >= i1 + 4) {
                    return true;
                } else {
                    int k1 = Math.max(i - 1, 0);
                    int l1 = Math.min(i + 1, 15);
                    int i2 = chunkaccess.getHeight(Heightmap.Types.WORLD_SURFACE_WG, k1, j);
                    int j2 = chunkaccess.getHeight(Heightmap.Types.WORLD_SURFACE_WG, l1, j);

                    return i2 >= j2 + 4;
                }
            }
        }
    }

    private abstract static class LazyCondition implements SurfaceRules.Condition {

        protected final SurfaceRules.Context context;
        private long lastUpdate;
        @Nullable
        Boolean result;

        protected LazyCondition(SurfaceRules.Context context) {
            this.context = context;
            this.lastUpdate = this.getContextLastUpdate() - 1L;
        }

        @Override
        public boolean test() {
            long i = this.getContextLastUpdate();

            if (i == this.lastUpdate) {
                if (this.result == null) {
                    throw new IllegalStateException("Update triggered but the result is null");
                } else {
                    return this.result;
                }
            } else {
                this.lastUpdate = i;
                this.result = this.compute();
                return this.result;
            }
        }

        protected abstract long getContextLastUpdate();

        protected abstract boolean compute();
    }

    private abstract static class LazyXZCondition extends SurfaceRules.LazyCondition {

        protected LazyXZCondition(SurfaceRules.Context context) {
            super(context);
        }

        @Override
        protected long getContextLastUpdate() {
            return this.context.lastUpdateXZ;
        }
    }

    private abstract static class LazyYCondition extends SurfaceRules.LazyCondition {

        protected LazyYCondition(SurfaceRules.Context context) {
            super(context);
        }

        @Override
        protected long getContextLastUpdate() {
            return this.context.lastUpdateY;
        }
    }

    private static record NotCondition(SurfaceRules.Condition target) implements SurfaceRules.Condition {

        @Override
        public boolean test() {
            return !this.target.test();
        }
    }

    private static record StateRule(BlockState state) implements SurfaceRules.SurfaceRule {

        @Override
        public BlockState tryApply(int blockX, int blockY, int blockZ) {
            return this.state;
        }
    }

    private static record TestRule(SurfaceRules.Condition condition, SurfaceRules.SurfaceRule followup) implements SurfaceRules.SurfaceRule {

        @Override
        public @Nullable BlockState tryApply(int blockX, int blockY, int blockZ) {
            return !this.condition.test() ? null : this.followup.tryApply(blockX, blockY, blockZ);
        }
    }

    private static record SequenceRule(List<SurfaceRules.SurfaceRule> rules) implements SurfaceRules.SurfaceRule {

        @Override
        public @Nullable BlockState tryApply(int blockX, int blockY, int blockZ) {
            for (SurfaceRules.SurfaceRule surfacerules_surfacerule : this.rules) {
                BlockState blockstate = surfacerules_surfacerule.tryApply(blockX, blockY, blockZ);

                if (blockstate != null) {
                    return blockstate;
                }
            }

            return null;
        }
    }

    public interface ConditionSource extends Function<SurfaceRules.Context, SurfaceRules.Condition> {

        Codec<SurfaceRules.ConditionSource> CODEC = BuiltInRegistries.MATERIAL_CONDITION.byNameCodec().dispatch((surfacerules_conditionsource) -> {
            return surfacerules_conditionsource.codec().codec();
        }, Function.identity());

        static MapCodec<? extends SurfaceRules.ConditionSource> bootstrap(Registry<MapCodec<? extends SurfaceRules.ConditionSource>> registry) {
            SurfaceRules.register(registry, "biome", SurfaceRules.BiomeConditionSource.CODEC);
            SurfaceRules.register(registry, "noise_threshold", SurfaceRules.NoiseThresholdConditionSource.CODEC);
            SurfaceRules.register(registry, "vertical_gradient", SurfaceRules.VerticalGradientConditionSource.CODEC);
            SurfaceRules.register(registry, "y_above", SurfaceRules.YConditionSource.CODEC);
            SurfaceRules.register(registry, "water", SurfaceRules.WaterConditionSource.CODEC);
            SurfaceRules.register(registry, "temperature", SurfaceRules.Temperature.CODEC);
            SurfaceRules.register(registry, "steep", SurfaceRules.Steep.CODEC);
            SurfaceRules.register(registry, "not", SurfaceRules.NotConditionSource.CODEC);
            SurfaceRules.register(registry, "hole", SurfaceRules.Hole.CODEC);
            SurfaceRules.register(registry, "above_preliminary_surface", SurfaceRules.AbovePreliminarySurface.CODEC);
            return SurfaceRules.<SurfaceRules.ConditionSource>register(registry, "stone_depth", SurfaceRules.StoneDepthCheck.CODEC);
        }

        KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec();
    }

    public interface RuleSource extends Function<SurfaceRules.Context, SurfaceRules.SurfaceRule> {

        Codec<SurfaceRules.RuleSource> CODEC = BuiltInRegistries.MATERIAL_RULE.byNameCodec().dispatch((surfacerules_rulesource) -> {
            return surfacerules_rulesource.codec().codec();
        }, Function.identity());

        static MapCodec<? extends SurfaceRules.RuleSource> bootstrap(Registry<MapCodec<? extends SurfaceRules.RuleSource>> registry) {
            SurfaceRules.register(registry, "bandlands", SurfaceRules.Bandlands.CODEC);
            SurfaceRules.register(registry, "block", SurfaceRules.BlockRuleSource.CODEC);
            SurfaceRules.register(registry, "sequence", SurfaceRules.SequenceRuleSource.CODEC);
            return SurfaceRules.<SurfaceRules.RuleSource>register(registry, "condition", SurfaceRules.TestRuleSource.CODEC);
        }

        KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec();
    }

    private static record NotConditionSource(SurfaceRules.ConditionSource target) implements SurfaceRules.ConditionSource {

        private static final KeyDispatchDataCodec<SurfaceRules.NotConditionSource> CODEC = KeyDispatchDataCodec.<SurfaceRules.NotConditionSource>of(SurfaceRules.ConditionSource.CODEC.xmap(SurfaceRules.NotConditionSource::new, SurfaceRules.NotConditionSource::target).fieldOf("invert"));

        @Override
        public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
            return SurfaceRules.NotConditionSource.CODEC;
        }

        public SurfaceRules.Condition apply(SurfaceRules.Context context) {
            return new SurfaceRules.NotCondition((SurfaceRules.Condition) this.target.apply(context));
        }
    }

    private static record StoneDepthCheck(int offset, boolean addSurfaceDepth, int secondaryDepthRange, CaveSurface surfaceType) implements SurfaceRules.ConditionSource {

        private static final KeyDispatchDataCodec<SurfaceRules.StoneDepthCheck> CODEC = KeyDispatchDataCodec.<SurfaceRules.StoneDepthCheck>of(RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Codec.INT.fieldOf("offset").forGetter(SurfaceRules.StoneDepthCheck::offset), Codec.BOOL.fieldOf("add_surface_depth").forGetter(SurfaceRules.StoneDepthCheck::addSurfaceDepth), Codec.INT.fieldOf("secondary_depth_range").forGetter(SurfaceRules.StoneDepthCheck::secondaryDepthRange), CaveSurface.CODEC.fieldOf("surface_type").forGetter(SurfaceRules.StoneDepthCheck::surfaceType)).apply(instance, SurfaceRules.StoneDepthCheck::new);
        }));

        @Override
        public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
            return SurfaceRules.StoneDepthCheck.CODEC;
        }

        public SurfaceRules.Condition apply(final SurfaceRules.Context ruleContext) {
            final boolean flag = this.surfaceType == CaveSurface.CEILING;

            class 1StoneDepthCondition extends SurfaceRules.LazyYCondition {

                private _StoneDepthCondition/* $FF was: 1StoneDepthCondition*/() {
                    super(StoneDepthCheck.this);
                }

                @Override
                protected boolean compute() {
                    int i = flag ? this.context.stoneDepthBelow : this.context.stoneDepthAbove;
                    int j = StoneDepthCheck.this.addSurfaceDepth ? this.context.surfaceDepth : 0;
                    int k = StoneDepthCheck.this.secondaryDepthRange == 0 ? 0 : (int)Mth.map(this.context.getSurfaceSecondary(), -1.0D, 1.0D, 0.0D, (double)StoneDepthCheck.this.secondaryDepthRange);

                    return i <= 1 + StoneDepthCheck.this.offset + j + k;
                }
            }


            return new 1StoneDepthCondition();
        }
    }

    private static enum AbovePreliminarySurface implements SurfaceRules.ConditionSource {

        INSTANCE;

        private static final KeyDispatchDataCodec<SurfaceRules.AbovePreliminarySurface> CODEC = KeyDispatchDataCodec.<SurfaceRules.AbovePreliminarySurface>of(MapCodec.unit(SurfaceRules.AbovePreliminarySurface.INSTANCE));

        private AbovePreliminarySurface() {}

        @Override
        public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
            return SurfaceRules.AbovePreliminarySurface.CODEC;
        }

        public SurfaceRules.Condition apply(SurfaceRules.Context context) {
            return context.abovePreliminarySurface;
        }
    }

    private static enum Hole implements SurfaceRules.ConditionSource {

        INSTANCE;

        private static final KeyDispatchDataCodec<SurfaceRules.Hole> CODEC = KeyDispatchDataCodec.<SurfaceRules.Hole>of(MapCodec.unit(SurfaceRules.Hole.INSTANCE));

        private Hole() {}

        @Override
        public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
            return SurfaceRules.Hole.CODEC;
        }

        public SurfaceRules.Condition apply(SurfaceRules.Context context) {
            return context.hole;
        }
    }

    private static record YConditionSource(VerticalAnchor anchor, int surfaceDepthMultiplier, boolean addStoneDepth) implements SurfaceRules.ConditionSource {

        private static final KeyDispatchDataCodec<SurfaceRules.YConditionSource> CODEC = KeyDispatchDataCodec.<SurfaceRules.YConditionSource>of(RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(VerticalAnchor.CODEC.fieldOf("anchor").forGetter(SurfaceRules.YConditionSource::anchor), Codec.intRange(-20, 20).fieldOf("surface_depth_multiplier").forGetter(SurfaceRules.YConditionSource::surfaceDepthMultiplier), Codec.BOOL.fieldOf("add_stone_depth").forGetter(SurfaceRules.YConditionSource::addStoneDepth)).apply(instance, SurfaceRules.YConditionSource::new);
        }));

        @Override
        public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
            return SurfaceRules.YConditionSource.CODEC;
        }

        public SurfaceRules.Condition apply(final SurfaceRules.Context ruleContext) {
            class 1YCondition extends SurfaceRules.LazyYCondition {

                private _YCondition/* $FF was: 1YCondition*/() {
                    super(YConditionSource.this);
                }

                @Override
                protected boolean compute() {
                    return this.context.blockY + (YConditionSource.this.addStoneDepth ? this.context.stoneDepthAbove : 0) >= YConditionSource.this.anchor.resolveY(this.context.context) + this.context.surfaceDepth * YConditionSource.this.surfaceDepthMultiplier;
                }
            }


            return new 1YCondition();
        }
    }

    private static record WaterConditionSource(int offset, int surfaceDepthMultiplier, boolean addStoneDepth) implements SurfaceRules.ConditionSource {

        private static final KeyDispatchDataCodec<SurfaceRules.WaterConditionSource> CODEC = KeyDispatchDataCodec.<SurfaceRules.WaterConditionSource>of(RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Codec.INT.fieldOf("offset").forGetter(SurfaceRules.WaterConditionSource::offset), Codec.intRange(-20, 20).fieldOf("surface_depth_multiplier").forGetter(SurfaceRules.WaterConditionSource::surfaceDepthMultiplier), Codec.BOOL.fieldOf("add_stone_depth").forGetter(SurfaceRules.WaterConditionSource::addStoneDepth)).apply(instance, SurfaceRules.WaterConditionSource::new);
        }));

        @Override
        public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
            return SurfaceRules.WaterConditionSource.CODEC;
        }

        public SurfaceRules.Condition apply(final SurfaceRules.Context ruleContext) {
            class 1WaterCondition extends SurfaceRules.LazyYCondition {

                private _WaterCondition/* $FF was: 1WaterCondition*/() {
                    super(WaterConditionSource.this);
                }

                @Override
                protected boolean compute() {
                    return this.context.waterHeight == Integer.MIN_VALUE || this.context.blockY + (WaterConditionSource.this.addStoneDepth ? this.context.stoneDepthAbove : 0) >= this.context.waterHeight + WaterConditionSource.this.offset + this.context.surfaceDepth * WaterConditionSource.this.surfaceDepthMultiplier;
                }
            }


            return new 1WaterCondition();
        }
    }

    private static final class BiomeConditionSource implements SurfaceRules.ConditionSource {

        private static final KeyDispatchDataCodec<SurfaceRules.BiomeConditionSource> CODEC = KeyDispatchDataCodec.<SurfaceRules.BiomeConditionSource>of(ResourceKey.codec(Registries.BIOME).listOf().fieldOf("biome_is").xmap(SurfaceRules::isBiome, (surfacerules_biomeconditionsource) -> {
            return surfacerules_biomeconditionsource.biomes;
        }));
        private final List<ResourceKey<Biome>> biomes;
        private final Predicate<ResourceKey<Biome>> biomeNameTest;

        private BiomeConditionSource(List<ResourceKey<Biome>> biomes) {
            this.biomes = biomes;
            Set set = Set.copyOf(biomes);

            Objects.requireNonNull(set);
            this.biomeNameTest = set::contains;
        }

        @Override
        public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
            return SurfaceRules.BiomeConditionSource.CODEC;
        }

        public SurfaceRules.Condition apply(final SurfaceRules.Context ruleContext) {
            class 1BiomeCondition extends SurfaceRules.LazyYCondition {

                private _BiomeCondition/* $FF was: 1BiomeCondition*/() {
                    super(BiomeConditionSource.this);
                }

                @Override
                protected boolean compute() {
                    return ((Holder)this.context.biome.get()).is(BiomeConditionSource.this.biomeNameTest);
                }
            }


            return new 1BiomeCondition();
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            } else if (o instanceof SurfaceRules.BiomeConditionSource) {
                SurfaceRules.BiomeConditionSource surfacerules_biomeconditionsource = (SurfaceRules.BiomeConditionSource) o;

                return this.biomes.equals(surfacerules_biomeconditionsource.biomes);
            } else {
                return false;
            }
        }

        public int hashCode() {
            return this.biomes.hashCode();
        }

        public String toString() {
            return "BiomeConditionSource[biomes=" + String.valueOf(this.biomes) + "]";
        }
    }

    private static record NoiseThresholdConditionSource(ResourceKey<NormalNoise.NoiseParameters> noise, double minThreshold, double maxThreshold) implements SurfaceRules.ConditionSource {

        private static final KeyDispatchDataCodec<SurfaceRules.NoiseThresholdConditionSource> CODEC = KeyDispatchDataCodec.<SurfaceRules.NoiseThresholdConditionSource>of(RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(ResourceKey.codec(Registries.NOISE).fieldOf("noise").forGetter(SurfaceRules.NoiseThresholdConditionSource::noise), Codec.DOUBLE.fieldOf("min_threshold").forGetter(SurfaceRules.NoiseThresholdConditionSource::minThreshold), Codec.DOUBLE.fieldOf("max_threshold").forGetter(SurfaceRules.NoiseThresholdConditionSource::maxThreshold)).apply(instance, SurfaceRules.NoiseThresholdConditionSource::new);
        }));

        @Override
        public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
            return SurfaceRules.NoiseThresholdConditionSource.CODEC;
        }

        public SurfaceRules.Condition apply(final SurfaceRules.Context ruleContext) {
            final NormalNoise normalnoise = ruleContext.randomState.getOrCreateNoise(this.noise);

            class 1NoiseThresholdCondition extends SurfaceRules.LazyXZCondition {

                private _NoiseThresholdCondition/* $FF was: 1NoiseThresholdCondition*/() {
                    super(NoiseThresholdConditionSource.this);
                }

                @Override
                protected boolean compute() {
                    double d0 = normalnoise.getValue((double)this.context.blockX, 0.0D, (double)this.context.blockZ);

                    return d0 >= NoiseThresholdConditionSource.this.minThreshold && d0 <= NoiseThresholdConditionSource.this.maxThreshold;
                }
            }


            return new 1NoiseThresholdCondition();
        }
    }

    private static record VerticalGradientConditionSource(Identifier randomName, VerticalAnchor trueAtAndBelow, VerticalAnchor falseAtAndAbove) implements SurfaceRules.ConditionSource {

        private static final KeyDispatchDataCodec<SurfaceRules.VerticalGradientConditionSource> CODEC = KeyDispatchDataCodec.<SurfaceRules.VerticalGradientConditionSource>of(RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Identifier.CODEC.fieldOf("random_name").forGetter(SurfaceRules.VerticalGradientConditionSource::randomName), VerticalAnchor.CODEC.fieldOf("true_at_and_below").forGetter(SurfaceRules.VerticalGradientConditionSource::trueAtAndBelow), VerticalAnchor.CODEC.fieldOf("false_at_and_above").forGetter(SurfaceRules.VerticalGradientConditionSource::falseAtAndAbove)).apply(instance, SurfaceRules.VerticalGradientConditionSource::new);
        }));

        @Override
        public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
            return SurfaceRules.VerticalGradientConditionSource.CODEC;
        }

        public SurfaceRules.Condition apply(final SurfaceRules.Context ruleContext) {
            final int i = this.trueAtAndBelow().resolveY(ruleContext.context);
            final int j = this.falseAtAndAbove().resolveY(ruleContext.context);
            final PositionalRandomFactory positionalrandomfactory = ruleContext.randomState.getOrCreateRandomFactory(this.randomName());

            class 1VerticalGradientCondition extends SurfaceRules.LazyYCondition {

                private _VerticalGradientCondition/* $FF was: 1VerticalGradientCondition*/() {
                    super(VerticalGradientConditionSource.this);
                }

                @Override
                protected boolean compute() {
                    int k = this.context.blockY;

                    if (k <= i) {
                        return true;
                    } else if (k >= j) {
                        return false;
                    } else {
                        double d0 = Mth.map((double)k, (double)i, (double)j, 1.0D, 0.0D);
                        RandomSource randomsource = positionalrandomfactory.at(this.context.blockX, k, this.context.blockZ);

                        return (double)randomsource.nextFloat() < d0;
                    }
                }
            }


            return new 1VerticalGradientCondition();
        }
    }

    private static enum Temperature implements SurfaceRules.ConditionSource {

        INSTANCE;

        private static final KeyDispatchDataCodec<SurfaceRules.Temperature> CODEC = KeyDispatchDataCodec.<SurfaceRules.Temperature>of(MapCodec.unit(SurfaceRules.Temperature.INSTANCE));

        private Temperature() {}

        @Override
        public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
            return SurfaceRules.Temperature.CODEC;
        }

        public SurfaceRules.Condition apply(SurfaceRules.Context context) {
            return context.temperature;
        }
    }

    private static enum Steep implements SurfaceRules.ConditionSource {

        INSTANCE;

        private static final KeyDispatchDataCodec<SurfaceRules.Steep> CODEC = KeyDispatchDataCodec.<SurfaceRules.Steep>of(MapCodec.unit(SurfaceRules.Steep.INSTANCE));

        private Steep() {}

        @Override
        public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
            return SurfaceRules.Steep.CODEC;
        }

        public SurfaceRules.Condition apply(SurfaceRules.Context context) {
            return context.steep;
        }
    }

    private static record BlockRuleSource(BlockState resultState, SurfaceRules.StateRule rule) implements SurfaceRules.RuleSource {

        private static final KeyDispatchDataCodec<SurfaceRules.BlockRuleSource> CODEC = KeyDispatchDataCodec.<SurfaceRules.BlockRuleSource>of(BlockState.CODEC.xmap(SurfaceRules.BlockRuleSource::new, SurfaceRules.BlockRuleSource::resultState).fieldOf("result_state"));

        private BlockRuleSource(BlockState state) {
            this(state, new SurfaceRules.StateRule(state));
        }

        @Override
        public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
            return SurfaceRules.BlockRuleSource.CODEC;
        }

        public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) {
            return this.rule;
        }
    }

    private static record TestRuleSource(SurfaceRules.ConditionSource ifTrue, SurfaceRules.RuleSource thenRun) implements SurfaceRules.RuleSource {

        private static final KeyDispatchDataCodec<SurfaceRules.TestRuleSource> CODEC = KeyDispatchDataCodec.<SurfaceRules.TestRuleSource>of(RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(SurfaceRules.ConditionSource.CODEC.fieldOf("if_true").forGetter(SurfaceRules.TestRuleSource::ifTrue), SurfaceRules.RuleSource.CODEC.fieldOf("then_run").forGetter(SurfaceRules.TestRuleSource::thenRun)).apply(instance, SurfaceRules.TestRuleSource::new);
        }));

        @Override
        public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
            return SurfaceRules.TestRuleSource.CODEC;
        }

        public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) {
            return new SurfaceRules.TestRule((SurfaceRules.Condition) this.ifTrue.apply(context), (SurfaceRules.SurfaceRule) this.thenRun.apply(context));
        }
    }

    private static record SequenceRuleSource(List<SurfaceRules.RuleSource> sequence) implements SurfaceRules.RuleSource {

        private static final KeyDispatchDataCodec<SurfaceRules.SequenceRuleSource> CODEC = KeyDispatchDataCodec.<SurfaceRules.SequenceRuleSource>of(SurfaceRules.RuleSource.CODEC.listOf().xmap(SurfaceRules.SequenceRuleSource::new, SurfaceRules.SequenceRuleSource::sequence).fieldOf("sequence"));

        @Override
        public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
            return SurfaceRules.SequenceRuleSource.CODEC;
        }

        public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) {
            if (this.sequence.size() == 1) {
                return (SurfaceRules.SurfaceRule) ((SurfaceRules.RuleSource) this.sequence.get(0)).apply(context);
            } else {
                ImmutableList.Builder<SurfaceRules.SurfaceRule> immutablelist_builder = ImmutableList.builder();

                for (SurfaceRules.RuleSource surfacerules_rulesource : this.sequence) {
                    immutablelist_builder.add((SurfaceRules.SurfaceRule) surfacerules_rulesource.apply(context));
                }

                return new SurfaceRules.SequenceRule(immutablelist_builder.build());
            }
        }
    }

    private static enum Bandlands implements SurfaceRules.RuleSource {

        INSTANCE;

        private static final KeyDispatchDataCodec<SurfaceRules.Bandlands> CODEC = KeyDispatchDataCodec.<SurfaceRules.Bandlands>of(MapCodec.unit(SurfaceRules.Bandlands.INSTANCE));

        private Bandlands() {}

        @Override
        public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
            return SurfaceRules.Bandlands.CODEC;
        }

        public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) {
            SurfaceSystem surfacesystem = context.system;

            Objects.requireNonNull(context.system);
            return surfacesystem::getBand;
        }
    }

    private interface Condition {

        boolean test();
    }

    protected interface SurfaceRule {

        @Nullable
        BlockState tryApply(int blockX, int blockY, int blockZ);
    }
}
