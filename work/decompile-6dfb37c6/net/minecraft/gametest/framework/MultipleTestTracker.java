package net.minecraft.gametest.framework;

import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MultipleTestTracker {

    private static final char NOT_STARTED_TEST_CHAR = ' ';
    private static final char ONGOING_TEST_CHAR = '_';
    private static final char SUCCESSFUL_TEST_CHAR = '+';
    private static final char FAILED_OPTIONAL_TEST_CHAR = 'x';
    private static final char FAILED_REQUIRED_TEST_CHAR = 'X';
    private final Collection<GameTestInfo> tests = Lists.newArrayList();
    private final Collection<GameTestListener> listeners = Lists.newArrayList();

    public MultipleTestTracker() {}

    public MultipleTestTracker(Collection<GameTestInfo> tests) {
        this.tests.addAll(tests);
    }

    public void addTestToTrack(GameTestInfo testInfo) {
        this.tests.add(testInfo);
        Collection collection = this.listeners;

        Objects.requireNonNull(testInfo);
        collection.forEach(testInfo::addListener);
    }

    public void addListener(GameTestListener listener) {
        this.listeners.add(listener);
        this.tests.forEach((gametestinfo) -> {
            gametestinfo.addListener(listener);
        });
    }

    public void addFailureListener(final Consumer<GameTestInfo> listener) {
        this.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo testInfo) {}

            @Override
            public void testPassed(GameTestInfo testInfo, GameTestRunner runner) {}

            @Override
            public void testFailed(GameTestInfo testInfo, GameTestRunner runner) {
                listener.accept(testInfo);
            }

            @Override
            public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {}
        });
    }

    public int getFailedRequiredCount() {
        return (int) this.tests.stream().filter(GameTestInfo::hasFailed).filter(GameTestInfo::isRequired).count();
    }

    public int getFailedOptionalCount() {
        return (int) this.tests.stream().filter(GameTestInfo::hasFailed).filter(GameTestInfo::isOptional).count();
    }

    public int getDoneCount() {
        return (int) this.tests.stream().filter(GameTestInfo::isDone).count();
    }

    public boolean hasFailedRequired() {
        return this.getFailedRequiredCount() > 0;
    }

    public boolean hasFailedOptional() {
        return this.getFailedOptionalCount() > 0;
    }

    public Collection<GameTestInfo> getFailedRequired() {
        return (Collection) this.tests.stream().filter(GameTestInfo::hasFailed).filter(GameTestInfo::isRequired).collect(Collectors.toList());
    }

    public Collection<GameTestInfo> getFailedOptional() {
        return (Collection) this.tests.stream().filter(GameTestInfo::hasFailed).filter(GameTestInfo::isOptional).collect(Collectors.toList());
    }

    public int getTotalCount() {
        return this.tests.size();
    }

    public boolean isDone() {
        return this.getDoneCount() == this.getTotalCount();
    }

    public String getProgressBar() {
        StringBuffer stringbuffer = new StringBuffer();

        stringbuffer.append('[');
        this.tests.forEach((gametestinfo) -> {
            if (!gametestinfo.hasStarted()) {
                stringbuffer.append(' ');
            } else if (gametestinfo.hasSucceeded()) {
                stringbuffer.append('+');
            } else if (gametestinfo.hasFailed()) {
                stringbuffer.append((char) (gametestinfo.isRequired() ? 'X' : 'x'));
            } else {
                stringbuffer.append('_');
            }

        });
        stringbuffer.append(']');
        return stringbuffer.toString();
    }

    public String toString() {
        return this.getProgressBar();
    }

    public void remove(GameTestInfo testInfo) {
        this.tests.remove(testInfo);
    }
}
