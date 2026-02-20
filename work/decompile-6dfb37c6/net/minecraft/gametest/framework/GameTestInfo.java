package net.minecraft.gametest.framework;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public class GameTestInfo {

    private final Holder.Reference<GameTestInstance> test;
    private @Nullable BlockPos testBlockPos;
    private final ServerLevel level;
    private final Collection<GameTestListener> listeners = Lists.newArrayList();
    private final int timeoutTicks;
    private final Collection<GameTestSequence> sequences = Lists.newCopyOnWriteArrayList();
    private final Object2LongMap<Runnable> runAtTickTimeMap = new Object2LongOpenHashMap();
    private boolean placedStructure;
    private boolean chunksLoaded;
    private int tickCount;
    private boolean started;
    private final RetryOptions retryOptions;
    private final Stopwatch timer = Stopwatch.createUnstarted();
    private boolean done;
    private final Rotation extraRotation;
    private @Nullable GameTestException error;
    private @Nullable TestInstanceBlockEntity testInstanceBlockEntity;

    public GameTestInfo(Holder.Reference<GameTestInstance> test, Rotation extraRotation, ServerLevel level, RetryOptions retryOptions) {
        this.test = test;
        this.level = level;
        this.retryOptions = retryOptions;
        this.timeoutTicks = ((GameTestInstance) test.value()).maxTicks();
        this.extraRotation = extraRotation;
    }

    public void setTestBlockPos(@Nullable BlockPos testBlockPos) {
        this.testBlockPos = testBlockPos;
    }

    public GameTestInfo startExecution(int tickDelay) {
        this.tickCount = -(((GameTestInstance) this.test.value()).setupTicks() + tickDelay + 1);
        return this;
    }

    public void placeStructure() {
        if (!this.placedStructure) {
            TestInstanceBlockEntity testinstanceblockentity = this.getTestInstanceBlockEntity();

            if (!testinstanceblockentity.placeStructure()) {
                this.fail((Component) Component.translatable("test.error.structure.failure", testinstanceblockentity.getTestName().getString()));
            }

            this.placedStructure = true;
            testinstanceblockentity.encaseStructure();
            BoundingBox boundingbox = testinstanceblockentity.getStructureBoundingBox();

            this.level.getBlockTicks().clearArea(boundingbox);
            this.level.clearBlockEvents(boundingbox);
            this.listeners.forEach((gametestlistener) -> {
                gametestlistener.testStructureLoaded(this);
            });
        }
    }

    public void tick(GameTestRunner runner) {
        if (!this.isDone()) {
            if (!this.placedStructure) {
                this.fail((Component) Component.translatable("test.error.ticking_without_structure"));
            }

            if (this.testInstanceBlockEntity == null) {
                this.fail((Component) Component.translatable("test.error.missing_block_entity"));
            }

            if (this.error != null) {
                this.finish();
            }

            if (!this.chunksLoaded) {
                Stream stream = this.testInstanceBlockEntity.getStructureBoundingBox().intersectingChunks();
                ServerLevel serverlevel = this.level;

                Objects.requireNonNull(this.level);
                if (!stream.allMatch(serverlevel::areEntitiesActuallyLoadedAndTicking)) {
                    return;
                }
            }

            this.chunksLoaded = true;
            this.tickInternal();
            if (this.isDone()) {
                if (this.error != null) {
                    this.listeners.forEach((gametestlistener) -> {
                        gametestlistener.testFailed(this, runner);
                    });
                } else {
                    this.listeners.forEach((gametestlistener) -> {
                        gametestlistener.testPassed(this, runner);
                    });
                }
            }

        }
    }

    private void tickInternal() {
        ++this.tickCount;
        if (this.tickCount >= 0) {
            if (!this.started) {
                this.startTest();
            }

            ObjectIterator<Object2LongMap.Entry<Runnable>> objectiterator = this.runAtTickTimeMap.object2LongEntrySet().iterator();

            while (objectiterator.hasNext()) {
                Object2LongMap.Entry<Runnable> object2longmap_entry = (Entry) objectiterator.next();

                if (object2longmap_entry.getLongValue() <= (long) this.tickCount) {
                    try {
                        ((Runnable) object2longmap_entry.getKey()).run();
                    } catch (GameTestException gametestexception) {
                        this.fail(gametestexception);
                    } catch (Exception exception) {
                        this.fail((GameTestException) (new UnknownGameTestException(exception)));
                    }

                    objectiterator.remove();
                }
            }

            if (this.tickCount > this.timeoutTicks) {
                if (this.sequences.isEmpty()) {
                    this.fail((GameTestException) (new GameTestTimeoutException(Component.translatable("test.error.timeout.no_result", ((GameTestInstance) this.test.value()).maxTicks()))));
                } else {
                    this.sequences.forEach((gametestsequence) -> {
                        gametestsequence.tickAndFailIfNotComplete(this.tickCount);
                    });
                    if (this.error == null) {
                        this.fail((GameTestException) (new GameTestTimeoutException(Component.translatable("test.error.timeout.no_sequences_finished", ((GameTestInstance) this.test.value()).maxTicks()))));
                    }
                }
            } else {
                this.sequences.forEach((gametestsequence) -> {
                    gametestsequence.tickAndContinue(this.tickCount);
                });
            }

        }
    }

    private void startTest() {
        if (!this.started) {
            this.started = true;
            this.timer.start();
            this.getTestInstanceBlockEntity().setRunning();

            try {
                ((GameTestInstance) this.test.value()).run(new GameTestHelper(this));
            } catch (GameTestException gametestexception) {
                this.fail(gametestexception);
            } catch (Exception exception) {
                this.fail((GameTestException) (new UnknownGameTestException(exception)));
            }

        }
    }

    public void setRunAtTickTime(long time, Runnable assertAtTickTime) {
        this.runAtTickTimeMap.put(assertAtTickTime, time);
    }

    public Identifier id() {
        return this.test.key().identifier();
    }

    public @Nullable BlockPos getTestBlockPos() {
        return this.testBlockPos;
    }

    public BlockPos getTestOrigin() {
        return this.testInstanceBlockEntity.getStartCorner();
    }

    public AABB getStructureBounds() {
        TestInstanceBlockEntity testinstanceblockentity = this.getTestInstanceBlockEntity();

        return testinstanceblockentity.getStructureBounds();
    }

    public TestInstanceBlockEntity getTestInstanceBlockEntity() {
        if (this.testInstanceBlockEntity == null) {
            if (this.testBlockPos == null) {
                throw new IllegalStateException("This GameTestInfo has no position");
            }

            BlockEntity blockentity = this.level.getBlockEntity(this.testBlockPos);

            if (blockentity instanceof TestInstanceBlockEntity) {
                TestInstanceBlockEntity testinstanceblockentity = (TestInstanceBlockEntity) blockentity;

                this.testInstanceBlockEntity = testinstanceblockentity;
            }

            if (this.testInstanceBlockEntity == null) {
                throw new IllegalStateException("Could not find a test instance block entity at the given coordinate " + String.valueOf(this.testBlockPos));
            }
        }

        return this.testInstanceBlockEntity;
    }

    public ServerLevel getLevel() {
        return this.level;
    }

    public boolean hasSucceeded() {
        return this.done && this.error == null;
    }

    public boolean hasFailed() {
        return this.error != null;
    }

    public boolean hasStarted() {
        return this.started;
    }

    public boolean isDone() {
        return this.done;
    }

    public long getRunTime() {
        return this.timer.elapsed(TimeUnit.MILLISECONDS);
    }

    private void finish() {
        if (!this.done) {
            this.done = true;
            if (this.timer.isRunning()) {
                this.timer.stop();
            }
        }

    }

    public void succeed() {
        if (this.error == null) {
            this.finish();
            AABB aabb = this.getStructureBounds();
            List<Entity> list = this.getLevel().<Entity>getEntitiesOfClass(Entity.class, aabb.inflate(1.0D), (entity) -> {
                return !(entity instanceof Player);
            });

            list.forEach((entity) -> {
                entity.remove(Entity.RemovalReason.DISCARDED);
            });
        }

    }

    public void fail(Component message) {
        this.fail((GameTestException) (new GameTestAssertException(message, this.tickCount)));
    }

    public void fail(GameTestException error) {
        this.error = error;
    }

    public @Nullable GameTestException getError() {
        return this.error;
    }

    public String toString() {
        return this.id().toString();
    }

    public void addListener(GameTestListener listener) {
        this.listeners.add(listener);
    }

    public @Nullable GameTestInfo prepareTestStructure() {
        TestInstanceBlockEntity testinstanceblockentity = this.createTestInstanceBlock((BlockPos) Objects.requireNonNull(this.testBlockPos), this.extraRotation, this.level);

        if (testinstanceblockentity != null) {
            this.testInstanceBlockEntity = testinstanceblockentity;
            this.placeStructure();
            return this;
        } else {
            return null;
        }
    }

    private @Nullable TestInstanceBlockEntity createTestInstanceBlock(BlockPos testPos, Rotation rotation, ServerLevel level) {
        level.setBlockAndUpdate(testPos, Blocks.TEST_INSTANCE_BLOCK.defaultBlockState());
        BlockEntity blockentity = level.getBlockEntity(testPos);

        if (blockentity instanceof TestInstanceBlockEntity testinstanceblockentity) {
            ResourceKey<GameTestInstance> resourcekey = this.getTestHolder().key();
            Vec3i vec3i = (Vec3i) TestInstanceBlockEntity.getStructureSize(level, resourcekey).orElse(new Vec3i(1, 1, 1));

            testinstanceblockentity.set(new TestInstanceBlockEntity.Data(Optional.of(resourcekey), vec3i, rotation, false, TestInstanceBlockEntity.Status.CLEARED, Optional.empty()));
            return testinstanceblockentity;
        } else {
            return null;
        }
    }

    int getTick() {
        return this.tickCount;
    }

    GameTestSequence createSequence() {
        GameTestSequence gametestsequence = new GameTestSequence(this);

        this.sequences.add(gametestsequence);
        return gametestsequence;
    }

    public boolean isRequired() {
        return ((GameTestInstance) this.test.value()).required();
    }

    public boolean isOptional() {
        return !((GameTestInstance) this.test.value()).required();
    }

    public Identifier getStructure() {
        return ((GameTestInstance) this.test.value()).structure();
    }

    public Rotation getRotation() {
        return ((GameTestInstance) this.test.value()).info().rotation().getRotated(this.extraRotation);
    }

    public GameTestInstance getTest() {
        return (GameTestInstance) this.test.value();
    }

    public Holder.Reference<GameTestInstance> getTestHolder() {
        return this.test;
    }

    public int getTimeoutTicks() {
        return this.timeoutTicks;
    }

    public boolean isFlaky() {
        return ((GameTestInstance) this.test.value()).maxAttempts() > 1;
    }

    public int maxAttempts() {
        return ((GameTestInstance) this.test.value()).maxAttempts();
    }

    public int requiredSuccesses() {
        return ((GameTestInstance) this.test.value()).requiredSuccesses();
    }

    public RetryOptions retryOptions() {
        return this.retryOptions;
    }

    public Stream<GameTestListener> getListeners() {
        return this.listeners.stream();
    }

    public GameTestInfo copyReset() {
        GameTestInfo gametestinfo = new GameTestInfo(this.test, this.extraRotation, this.level, this.retryOptions());

        if (this.testBlockPos != null) {
            gametestinfo.setTestBlockPos(this.testBlockPos);
        }

        return gametestinfo;
    }
}
