package net.minecraft.world.level.levelgen.feature;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;

public class FeatureCountTracker {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final LoadingCache<ServerLevel, FeatureCountTracker.LevelData> data = CacheBuilder.newBuilder().weakKeys().expireAfterAccess(5L, TimeUnit.MINUTES).build(new CacheLoader<ServerLevel, FeatureCountTracker.LevelData>() {
        public FeatureCountTracker.LevelData load(ServerLevel level) {
            return new FeatureCountTracker.LevelData(Object2IntMaps.synchronize(new Object2IntOpenHashMap()), new MutableInt(0));
        }
    });

    public FeatureCountTracker() {}

    public static void chunkDecorated(ServerLevel level) {
        try {
            ((FeatureCountTracker.LevelData) FeatureCountTracker.data.get(level)).chunksWithFeatures().increment();
        } catch (Exception exception) {
            FeatureCountTracker.LOGGER.error("Failed to increment chunk count", exception);
        }

    }

    public static void featurePlaced(ServerLevel level, ConfiguredFeature<?, ?> feature, Optional<PlacedFeature> topFeature) {
        try {
            ((FeatureCountTracker.LevelData) FeatureCountTracker.data.get(level)).featureData().computeInt(new FeatureCountTracker.FeatureData(feature, topFeature), (featurecounttracker_featuredata, integer) -> {
                return integer == null ? 1 : integer + 1;
            });
        } catch (Exception exception) {
            FeatureCountTracker.LOGGER.error("Failed to increment feature count", exception);
        }

    }

    public static void clearCounts() {
        FeatureCountTracker.data.invalidateAll();
        FeatureCountTracker.LOGGER.debug("Cleared feature counts");
    }

    public static void logCounts() {
        FeatureCountTracker.LOGGER.debug("Logging feature counts:");
        FeatureCountTracker.data.asMap().forEach((serverlevel, featurecounttracker_leveldata) -> {
            String s = serverlevel.dimension().identifier().toString();
            boolean flag = serverlevel.getServer().isRunning();
            Registry<PlacedFeature> registry = serverlevel.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
            String s1 = (flag ? "running" : "dead") + " " + s;
            int i = featurecounttracker_leveldata.chunksWithFeatures().intValue();

            FeatureCountTracker.LOGGER.debug("{} total_chunks: {}", s1, i);
            featurecounttracker_leveldata.featureData().forEach((featurecounttracker_featuredata, j) -> {
                Logger logger = FeatureCountTracker.LOGGER;
                Object[] aobject = new Object[]{s1, String.format(Locale.ROOT, "%10d", j), String.format(Locale.ROOT, "%10f", (double) j / (double) i), null, null, null};
                Optional optional = featurecounttracker_featuredata.topFeature();

                Objects.requireNonNull(registry);
                aobject[3] = optional.flatMap(registry::getResourceKey).map(ResourceKey::identifier);
                aobject[4] = featurecounttracker_featuredata.feature().feature();
                aobject[5] = featurecounttracker_featuredata.feature();
                logger.debug("{} {} {} {} {} {}", aobject);
            });
        });
    }

    private static record FeatureData(ConfiguredFeature<?, ?> feature, Optional<PlacedFeature> topFeature) {

    }

    private static record LevelData(Object2IntMap<FeatureCountTracker.FeatureData> featureData, MutableInt chunksWithFeatures) {

    }
}
