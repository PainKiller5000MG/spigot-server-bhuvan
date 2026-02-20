package net.minecraft.gametest.framework;

import com.mojang.brigadier.context.CommandContext;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;

public class TestFinder implements TestInstanceFinder, TestPosFinder {

    private static final TestInstanceFinder NO_FUNCTIONS = Stream::empty;
    private static final TestPosFinder NO_STRUCTURES = Stream::empty;
    private final TestInstanceFinder testInstanceFinder;
    private final TestPosFinder testPosFinder;
    private final CommandSourceStack source;

    @Override
    public Stream<BlockPos> findTestPos() {
        return this.testPosFinder.findTestPos();
    }

    public static TestFinder.Builder builder() {
        return new TestFinder.Builder();
    }

    private TestFinder(CommandSourceStack source, TestInstanceFinder testInstanceFinder, TestPosFinder testPosFinder) {
        this.source = source;
        this.testInstanceFinder = testInstanceFinder;
        this.testPosFinder = testPosFinder;
    }

    public CommandSourceStack source() {
        return this.source;
    }

    @Override
    public Stream<Holder.Reference<GameTestInstance>> findTests() {
        return this.testInstanceFinder.findTests();
    }

    public static class Builder {

        private final UnaryOperator<Supplier<Stream<Holder.Reference<GameTestInstance>>>> testFinderWrapper;
        private final UnaryOperator<Supplier<Stream<BlockPos>>> structureBlockPosFinderWrapper;

        public Builder() {
            this.testFinderWrapper = (supplier) -> {
                return supplier;
            };
            this.structureBlockPosFinderWrapper = (supplier) -> {
                return supplier;
            };
        }

        private Builder(UnaryOperator<Supplier<Stream<Holder.Reference<GameTestInstance>>>> testFinderWrapper, UnaryOperator<Supplier<Stream<BlockPos>>> structureBlockPosFinderWrapper) {
            this.testFinderWrapper = testFinderWrapper;
            this.structureBlockPosFinderWrapper = structureBlockPosFinderWrapper;
        }

        public TestFinder.Builder createMultipleCopies(int amount) {
            return new TestFinder.Builder(createCopies(amount), createCopies(amount));
        }

        private static <Q> UnaryOperator<Supplier<Stream<Q>>> createCopies(int amount) {
            return (supplier) -> {
                List<Q> list = new LinkedList();
                List<Q> list1 = ((Stream) supplier.get()).toList();

                for (int j = 0; j < amount; ++j) {
                    list.addAll(list1);
                }

                Objects.requireNonNull(list);
                return list::stream;
            };
        }

        private TestFinder build(CommandSourceStack source, TestInstanceFinder testInstanceFinder, TestPosFinder testPosFinder) {
            UnaryOperator unaryoperator = this.testFinderWrapper;

            Objects.requireNonNull(testInstanceFinder);
            Supplier supplier = (Supplier) unaryoperator.apply(testInstanceFinder::findTests);

            Objects.requireNonNull(supplier);
            TestInstanceFinder testinstancefinder1 = supplier::get;
            UnaryOperator unaryoperator1 = this.structureBlockPosFinderWrapper;

            Objects.requireNonNull(testPosFinder);
            Supplier supplier1 = (Supplier) unaryoperator1.apply(testPosFinder::findTestPos);

            Objects.requireNonNull(supplier1);
            return new TestFinder(source, testinstancefinder1, supplier1::get);
        }

        public TestFinder radius(CommandContext<CommandSourceStack> sourceStack, int radius) {
            CommandSourceStack commandsourcestack = (CommandSourceStack) sourceStack.getSource();
            BlockPos blockpos = BlockPos.containing(commandsourcestack.getPosition());

            return this.build(commandsourcestack, TestFinder.NO_FUNCTIONS, () -> {
                return StructureUtils.findTestBlocks(blockpos, radius, commandsourcestack.getLevel());
            });
        }

        public TestFinder nearest(CommandContext<CommandSourceStack> sourceStack) {
            CommandSourceStack commandsourcestack = (CommandSourceStack) sourceStack.getSource();
            BlockPos blockpos = BlockPos.containing(commandsourcestack.getPosition());

            return this.build(commandsourcestack, TestFinder.NO_FUNCTIONS, () -> {
                return StructureUtils.findNearestTest(blockpos, 15, commandsourcestack.getLevel()).stream();
            });
        }

        public TestFinder allNearby(CommandContext<CommandSourceStack> sourceStack) {
            CommandSourceStack commandsourcestack = (CommandSourceStack) sourceStack.getSource();
            BlockPos blockpos = BlockPos.containing(commandsourcestack.getPosition());

            return this.build(commandsourcestack, TestFinder.NO_FUNCTIONS, () -> {
                return StructureUtils.findTestBlocks(blockpos, 250, commandsourcestack.getLevel());
            });
        }

        public TestFinder lookedAt(CommandContext<CommandSourceStack> sourceStack) {
            CommandSourceStack commandsourcestack = (CommandSourceStack) sourceStack.getSource();

            return this.build(commandsourcestack, TestFinder.NO_FUNCTIONS, () -> {
                return StructureUtils.lookedAtTestPos(BlockPos.containing(commandsourcestack.getPosition()), commandsourcestack.getPlayer().getCamera(), commandsourcestack.getLevel());
            });
        }

        public TestFinder failedTests(CommandContext<CommandSourceStack> sourceStack, boolean onlyRequiredTests) {
            return this.build((CommandSourceStack) sourceStack.getSource(), () -> {
                return FailedTestTracker.getLastFailedTests().filter((holder_reference) -> {
                    return !onlyRequiredTests || ((GameTestInstance) holder_reference.value()).required();
                });
            }, TestFinder.NO_STRUCTURES);
        }

        public TestFinder byResourceSelection(CommandContext<CommandSourceStack> sourceStack, Collection<Holder.Reference<GameTestInstance>> holders) {
            CommandSourceStack commandsourcestack = (CommandSourceStack) sourceStack.getSource();

            Objects.requireNonNull(holders);
            return this.build(commandsourcestack, holders::stream, TestFinder.NO_STRUCTURES);
        }

        public TestFinder failedTests(CommandContext<CommandSourceStack> sourceStack) {
            return this.failedTests(sourceStack, false);
        }
    }
}
