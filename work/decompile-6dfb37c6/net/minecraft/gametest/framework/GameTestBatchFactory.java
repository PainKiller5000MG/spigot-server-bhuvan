package net.minecraft.gametest.framework;

import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

public class GameTestBatchFactory {

    private static final int MAX_TESTS_PER_BATCH = 50;
    public static final GameTestBatchFactory.TestDecorator DIRECT = (holder_reference, serverlevel) -> {
        return Stream.of(new GameTestInfo(holder_reference, Rotation.NONE, serverlevel, RetryOptions.noRetries()));
    };

    public GameTestBatchFactory() {}

    public static List<GameTestBatch> divideIntoBatches(Collection<Holder.Reference<GameTestInstance>> allTests, GameTestBatchFactory.TestDecorator decorator, ServerLevel level) {
        Map<Holder<TestEnvironmentDefinition>, List<GameTestInfo>> map = (Map) allTests.stream().flatMap((holder_reference) -> {
            return decorator.decorate(holder_reference, level);
        }).collect(Collectors.groupingBy((gametestinfo) -> {
            return gametestinfo.getTest().batch();
        }));

        return map.entrySet().stream().flatMap((entry) -> {
            Holder<TestEnvironmentDefinition> holder = (Holder) entry.getKey();
            List<GameTestInfo> list = (List) entry.getValue();

            return Streams.mapWithIndex(Lists.partition(list, 50).stream(), (list1, i) -> {
                return toGameTestBatch(list1, holder, (int) i);
            });
        }).toList();
    }

    public static GameTestRunner.GameTestBatcher fromGameTestInfo() {
        return fromGameTestInfo(50);
    }

    public static GameTestRunner.GameTestBatcher fromGameTestInfo(int maxTestsPerBatch) {
        return (collection) -> {
            Map<Holder<TestEnvironmentDefinition>, List<GameTestInfo>> map = (Map) collection.stream().filter(Objects::nonNull).collect(Collectors.groupingBy((gametestinfo) -> {
                return gametestinfo.getTest().batch();
            }));

            return map.entrySet().stream().flatMap((entry) -> {
                Holder<TestEnvironmentDefinition> holder = (Holder) entry.getKey();
                List<GameTestInfo> list = (List) entry.getValue();

                return Streams.mapWithIndex(Lists.partition(list, maxTestsPerBatch).stream(), (list1, j) -> {
                    return toGameTestBatch(List.copyOf(list1), holder, (int) j);
                });
            }).toList();
        };
    }

    public static GameTestBatch toGameTestBatch(Collection<GameTestInfo> tests, Holder<TestEnvironmentDefinition> batch, int counter) {
        return new GameTestBatch(counter, tests, batch);
    }

    @FunctionalInterface
    public interface TestDecorator {

        Stream<GameTestInfo> decorate(Holder.Reference<GameTestInstance> test, ServerLevel level);
    }
}
