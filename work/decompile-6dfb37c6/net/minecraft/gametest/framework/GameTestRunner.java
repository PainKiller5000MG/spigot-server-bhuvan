package net.minecraft.gametest.framework;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class GameTestRunner {

    public static final int DEFAULT_TESTS_PER_ROW = 8;
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ServerLevel level;
    private final GameTestTicker testTicker;
    private final List<GameTestInfo> allTestInfos;
    private ImmutableList<GameTestBatch> batches;
    private final List<GameTestBatchListener> batchListeners = Lists.newArrayList();
    private final List<GameTestInfo> scheduledForRerun = Lists.newArrayList();
    private final GameTestRunner.GameTestBatcher testBatcher;
    private boolean stopped = true;
    private @Nullable Holder<TestEnvironmentDefinition> currentEnvironment;
    private final GameTestRunner.StructureSpawner existingStructureSpawner;
    private final GameTestRunner.StructureSpawner newStructureSpawner;
    private final boolean haltOnError;
    private final boolean clearBetweenBatches;

    protected GameTestRunner(GameTestRunner.GameTestBatcher batcher, Collection<GameTestBatch> batches, ServerLevel level, GameTestTicker testTicker, GameTestRunner.StructureSpawner existingStructureSpawner, GameTestRunner.StructureSpawner newStructureSpawner, boolean haltOnError, boolean clearBetweenBatches) {
        this.level = level;
        this.testTicker = testTicker;
        this.testBatcher = batcher;
        this.existingStructureSpawner = existingStructureSpawner;
        this.newStructureSpawner = newStructureSpawner;
        this.batches = ImmutableList.copyOf(batches);
        this.haltOnError = haltOnError;
        this.clearBetweenBatches = clearBetweenBatches;
        this.allTestInfos = (List) this.batches.stream().flatMap((gametestbatch) -> {
            return gametestbatch.gameTestInfos().stream();
        }).collect(Util.toMutableList());
        testTicker.setRunner(this);
        this.allTestInfos.forEach((gametestinfo) -> {
            gametestinfo.addListener(new ReportGameListener());
        });
    }

    public List<GameTestInfo> getTestInfos() {
        return this.allTestInfos;
    }

    public void start() {
        this.stopped = false;
        this.runBatch(0);
    }

    public void stop() {
        this.stopped = true;
        if (this.currentEnvironment != null) {
            this.endCurrentEnvironment();
        }

    }

    public void rerunTest(GameTestInfo info) {
        GameTestInfo gametestinfo1 = info.copyReset();

        info.getListeners().forEach((gametestlistener) -> {
            gametestlistener.testAddedForRerun(info, gametestinfo1, this);
        });
        this.allTestInfos.add(gametestinfo1);
        this.scheduledForRerun.add(gametestinfo1);
        if (this.stopped) {
            this.runScheduledRerunTests();
        }

    }

    private void runBatch(final int batchIndex) {
        if (batchIndex >= this.batches.size()) {
            this.endCurrentEnvironment();
            this.runScheduledRerunTests();
        } else {
            if (batchIndex > 0 && this.clearBetweenBatches) {
                GameTestBatch gametestbatch = (GameTestBatch) this.batches.get(batchIndex - 1);

                gametestbatch.gameTestInfos().forEach((gametestinfo) -> {
                    TestInstanceBlockEntity testinstanceblockentity = gametestinfo.getTestInstanceBlockEntity();

                    StructureUtils.clearSpaceForStructure(testinstanceblockentity.getStructureBoundingBox(), this.level);
                    this.level.destroyBlock(testinstanceblockentity.getBlockPos(), false);
                });
            }

            final GameTestBatch gametestbatch1 = (GameTestBatch) this.batches.get(batchIndex);

            this.existingStructureSpawner.onBatchStart(this.level);
            this.newStructureSpawner.onBatchStart(this.level);
            Collection<GameTestInfo> collection = this.createStructuresForBatch(gametestbatch1.gameTestInfos());

            GameTestRunner.LOGGER.info("Running test environment '{}' batch {} ({} tests)...", new Object[]{gametestbatch1.environment().getRegisteredName(), gametestbatch1.index(), collection.size()});
            this.endCurrentEnvironment();
            this.currentEnvironment = gametestbatch1.environment();
            ((TestEnvironmentDefinition) this.currentEnvironment.value()).setup(this.level);
            this.batchListeners.forEach((gametestbatchlistener) -> {
                gametestbatchlistener.testBatchStarting(gametestbatch1);
            });
            final MultipleTestTracker multipletesttracker = new MultipleTestTracker();

            Objects.requireNonNull(multipletesttracker);
            collection.forEach(multipletesttracker::addTestToTrack);
            multipletesttracker.addListener(new GameTestListener() {
                private void testCompleted(GameTestInfo testInfo) {
                    testInfo.getTestInstanceBlockEntity().removeBarriers();
                    if (multipletesttracker.isDone()) {
                        GameTestRunner.this.batchListeners.forEach((gametestbatchlistener) -> {
                            gametestbatchlistener.testBatchFinished(gametestbatch1);
                        });
                        LongSet longset = new LongArraySet(GameTestRunner.this.level.getForceLoadedChunks());

                        longset.forEach((j) -> {
                            GameTestRunner.this.level.setChunkForced(ChunkPos.getX(j), ChunkPos.getZ(j), false);
                        });
                        GameTestRunner.this.runBatch(batchIndex + 1);
                    }

                }

                @Override
                public void testStructureLoaded(GameTestInfo testInfo) {}

                @Override
                public void testPassed(GameTestInfo testInfo, GameTestRunner runner) {
                    this.testCompleted(testInfo);
                }

                @Override
                public void testFailed(GameTestInfo testInfo, GameTestRunner runner) {
                    if (GameTestRunner.this.haltOnError) {
                        GameTestRunner.this.endCurrentEnvironment();
                        LongSet longset = new LongArraySet(GameTestRunner.this.level.getForceLoadedChunks());

                        longset.forEach((j) -> {
                            GameTestRunner.this.level.setChunkForced(ChunkPos.getX(j), ChunkPos.getZ(j), false);
                        });
                        GameTestTicker.SINGLETON.clear();
                        testInfo.getTestInstanceBlockEntity().removeBarriers();
                    } else {
                        this.testCompleted(testInfo);
                    }

                }

                @Override
                public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {}
            });
            GameTestTicker gametestticker = this.testTicker;

            Objects.requireNonNull(this.testTicker);
            collection.forEach(gametestticker::add);
        }
    }

    private void endCurrentEnvironment() {
        if (this.currentEnvironment != null) {
            ((TestEnvironmentDefinition) this.currentEnvironment.value()).teardown(this.level);
            this.currentEnvironment = null;
        }

    }

    private void runScheduledRerunTests() {
        if (!this.scheduledForRerun.isEmpty()) {
            GameTestRunner.LOGGER.info("Starting re-run of tests: {}", this.scheduledForRerun.stream().map((gametestinfo) -> {
                return gametestinfo.id().toString();
            }).collect(Collectors.joining(", ")));
            this.batches = ImmutableList.copyOf(this.testBatcher.batch(this.scheduledForRerun));
            this.scheduledForRerun.clear();
            this.stopped = false;
            this.runBatch(0);
        } else {
            this.batches = ImmutableList.of();
            this.stopped = true;
        }

    }

    public void addListener(GameTestBatchListener listener) {
        this.batchListeners.add(listener);
    }

    private Collection<GameTestInfo> createStructuresForBatch(Collection<GameTestInfo> batch) {
        return batch.stream().map(this::spawn).flatMap(Optional::stream).toList();
    }

    private Optional<GameTestInfo> spawn(GameTestInfo testInfo) {
        return testInfo.getTestBlockPos() == null ? this.newStructureSpawner.spawnStructure(testInfo) : this.existingStructureSpawner.spawnStructure(testInfo);
    }

    public interface StructureSpawner {

        GameTestRunner.StructureSpawner IN_PLACE = (gametestinfo) -> {
            return Optional.ofNullable(gametestinfo.prepareTestStructure()).map((gametestinfo1) -> {
                return gametestinfo1.startExecution(1);
            });
        };
        GameTestRunner.StructureSpawner NOT_SET = (gametestinfo) -> {
            return Optional.empty();
        };

        Optional<GameTestInfo> spawnStructure(GameTestInfo testInfo);

        default void onBatchStart(ServerLevel level) {}
    }

    public static class Builder {

        private final ServerLevel level;
        private final GameTestTicker testTicker;
        private GameTestRunner.GameTestBatcher batcher;
        private GameTestRunner.StructureSpawner existingStructureSpawner;
        private GameTestRunner.StructureSpawner newStructureSpawner;
        private final Collection<GameTestBatch> batches;
        private boolean haltOnError;
        private boolean clearBetweenBatches;

        private Builder(Collection<GameTestBatch> batches, ServerLevel level) {
            this.testTicker = GameTestTicker.SINGLETON;
            this.batcher = GameTestBatchFactory.fromGameTestInfo();
            this.existingStructureSpawner = GameTestRunner.StructureSpawner.IN_PLACE;
            this.newStructureSpawner = GameTestRunner.StructureSpawner.NOT_SET;
            this.haltOnError = false;
            this.clearBetweenBatches = false;
            this.batches = batches;
            this.level = level;
        }

        public static GameTestRunner.Builder fromBatches(Collection<GameTestBatch> batches, ServerLevel level) {
            return new GameTestRunner.Builder(batches, level);
        }

        public static GameTestRunner.Builder fromInfo(Collection<GameTestInfo> tests, ServerLevel level) {
            return fromBatches(GameTestBatchFactory.fromGameTestInfo().batch(tests), level);
        }

        public GameTestRunner.Builder haltOnError() {
            this.haltOnError = true;
            return this;
        }

        public GameTestRunner.Builder clearBetweenBatches() {
            this.clearBetweenBatches = true;
            return this;
        }

        public GameTestRunner.Builder newStructureSpawner(GameTestRunner.StructureSpawner structureSpawner) {
            this.newStructureSpawner = structureSpawner;
            return this;
        }

        public GameTestRunner.Builder existingStructureSpawner(StructureGridSpawner spawner) {
            this.existingStructureSpawner = spawner;
            return this;
        }

        public GameTestRunner.Builder batcher(GameTestRunner.GameTestBatcher batcher) {
            this.batcher = batcher;
            return this;
        }

        public GameTestRunner build() {
            return new GameTestRunner(this.batcher, this.batches, this.level, this.testTicker, this.existingStructureSpawner, this.newStructureSpawner, this.haltOnError, this.clearBetweenBatches);
        }
    }

    public interface GameTestBatcher {

        Collection<GameTestBatch> batch(Collection<GameTestInfo> infos);
    }
}
