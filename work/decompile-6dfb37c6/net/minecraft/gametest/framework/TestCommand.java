package net.minecraft.gametest.framework;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.ResourceSelectorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundGameTestHighlightPosPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.commands.InCommandFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import org.apache.commons.lang3.mutable.MutableInt;

public class TestCommand {

    public static final int TEST_NEARBY_SEARCH_RADIUS = 15;
    public static final int TEST_FULL_SEARCH_RADIUS = 250;
    public static final int VERIFY_TEST_GRID_AXIS_SIZE = 10;
    public static final int VERIFY_TEST_BATCH_SIZE = 100;
    private static final int DEFAULT_CLEAR_RADIUS = 250;
    private static final int MAX_CLEAR_RADIUS = 1024;
    private static final int TEST_POS_Z_OFFSET_FROM_PLAYER = 3;
    private static final int DEFAULT_X_SIZE = 5;
    private static final int DEFAULT_Y_SIZE = 5;
    private static final int DEFAULT_Z_SIZE = 5;
    private static final SimpleCommandExceptionType CLEAR_NO_TESTS = new SimpleCommandExceptionType(Component.translatable("commands.test.clear.error.no_tests"));
    private static final SimpleCommandExceptionType RESET_NO_TESTS = new SimpleCommandExceptionType(Component.translatable("commands.test.reset.error.no_tests"));
    private static final SimpleCommandExceptionType TEST_INSTANCE_COULD_NOT_BE_FOUND = new SimpleCommandExceptionType(Component.translatable("commands.test.error.test_instance_not_found"));
    private static final SimpleCommandExceptionType NO_STRUCTURES_TO_EXPORT = new SimpleCommandExceptionType(Component.literal("Could not find any structures to export"));
    private static final SimpleCommandExceptionType NO_TEST_INSTANCES = new SimpleCommandExceptionType(Component.translatable("commands.test.error.no_test_instances"));
    private static final Dynamic3CommandExceptionType NO_TEST_CONTAINING = new Dynamic3CommandExceptionType((object, object1, object2) -> {
        return Component.translatableEscape("commands.test.error.no_test_containing_pos", object, object1, object2);
    });
    private static final DynamicCommandExceptionType TOO_LARGE = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.test.error.too_large", object);
    });

    public TestCommand() {}

    private static int reset(TestFinder finder) throws CommandSyntaxException {
        stopTests();
        int i = toGameTestInfos(finder.source(), RetryOptions.noRetries(), finder).map((gametestinfo) -> {
            return resetGameTestInfo(finder.source(), gametestinfo);
        }).toList().size();

        if (i == 0) {
            throw TestCommand.CLEAR_NO_TESTS.create();
        } else {
            finder.source().sendSuccess(() -> {
                return Component.translatable("commands.test.reset.success", i);
            }, true);
            return i;
        }
    }

    private static int clear(TestFinder finder) throws CommandSyntaxException {
        stopTests();
        CommandSourceStack commandsourcestack = finder.source();
        ServerLevel serverlevel = commandsourcestack.getLevel();
        List<TestInstanceBlockEntity> list = finder.findTestPos().flatMap((blockpos) -> {
            return serverlevel.getBlockEntity(blockpos, BlockEntityType.TEST_INSTANCE_BLOCK).stream();
        }).toList();

        for (TestInstanceBlockEntity testinstanceblockentity : list) {
            StructureUtils.clearSpaceForStructure(testinstanceblockentity.getStructureBoundingBox(), serverlevel);
            testinstanceblockentity.removeBarriers();
            serverlevel.destroyBlock(testinstanceblockentity.getBlockPos(), false);
        }

        if (list.isEmpty()) {
            throw TestCommand.CLEAR_NO_TESTS.create();
        } else {
            commandsourcestack.sendSuccess(() -> {
                return Component.translatable("commands.test.clear.success", list.size());
            }, true);
            return list.size();
        }
    }

    private static int export(TestFinder finder) throws CommandSyntaxException {
        CommandSourceStack commandsourcestack = finder.source();
        ServerLevel serverlevel = commandsourcestack.getLevel();
        int i = 0;
        boolean flag = true;

        for (Iterator<BlockPos> iterator = finder.findTestPos().iterator(); iterator.hasNext(); ++i) {
            BlockPos blockpos = (BlockPos) iterator.next();
            BlockEntity blockentity = serverlevel.getBlockEntity(blockpos);

            if (!(blockentity instanceof TestInstanceBlockEntity)) {
                throw TestCommand.TEST_INSTANCE_COULD_NOT_BE_FOUND.create();
            }

            TestInstanceBlockEntity testinstanceblockentity = (TestInstanceBlockEntity) blockentity;

            Objects.requireNonNull(commandsourcestack);
            if (!testinstanceblockentity.exportTest(commandsourcestack::sendSystemMessage)) {
                flag = false;
            }
        }

        if (i == 0) {
            throw TestCommand.NO_STRUCTURES_TO_EXPORT.create();
        } else {
            String s = "Exported " + i + " structures";

            finder.source().sendSuccess(() -> {
                return Component.literal(s);
            }, true);
            return flag ? 0 : 1;
        }
    }

    private static int verify(TestFinder finder) {
        stopTests();
        CommandSourceStack commandsourcestack = finder.source();
        ServerLevel serverlevel = commandsourcestack.getLevel();
        BlockPos blockpos = createTestPositionAround(commandsourcestack);
        Collection<GameTestInfo> collection = Stream.concat(toGameTestInfos(commandsourcestack, RetryOptions.noRetries(), finder), toGameTestInfo(commandsourcestack, RetryOptions.noRetries(), finder, 0)).toList();

        FailedTestTracker.forgetFailedTests();
        Collection<GameTestBatch> collection1 = new ArrayList();

        for (GameTestInfo gametestinfo : collection) {
            for (Rotation rotation : Rotation.values()) {
                Collection<GameTestInfo> collection2 = new ArrayList();

                for (int i = 0; i < 100; ++i) {
                    GameTestInfo gametestinfo1 = new GameTestInfo(gametestinfo.getTestHolder(), rotation, serverlevel, new RetryOptions(1, true));

                    gametestinfo1.setTestBlockPos(gametestinfo.getTestBlockPos());
                    collection2.add(gametestinfo1);
                }

                GameTestBatch gametestbatch = GameTestBatchFactory.toGameTestBatch(collection2, gametestinfo.getTest().batch(), rotation.ordinal());

                collection1.add(gametestbatch);
            }
        }

        StructureGridSpawner structuregridspawner = new StructureGridSpawner(blockpos, 10, true);
        GameTestRunner gametestrunner = GameTestRunner.Builder.fromBatches(collection1, serverlevel).batcher(GameTestBatchFactory.fromGameTestInfo(100)).newStructureSpawner(structuregridspawner).existingStructureSpawner(structuregridspawner).haltOnError().clearBetweenBatches().build();

        return trackAndStartRunner(commandsourcestack, gametestrunner);
    }

    private static int run(TestFinder finder, RetryOptions retryOptions, int extraRotationSteps, int testsPerRow) {
        stopTests();
        CommandSourceStack commandsourcestack = finder.source();
        ServerLevel serverlevel = commandsourcestack.getLevel();
        BlockPos blockpos = createTestPositionAround(commandsourcestack);
        Collection<GameTestInfo> collection = Stream.concat(toGameTestInfos(commandsourcestack, retryOptions, finder), toGameTestInfo(commandsourcestack, retryOptions, finder, extraRotationSteps)).toList();

        if (collection.isEmpty()) {
            commandsourcestack.sendSuccess(() -> {
                return Component.translatable("commands.test.no_tests");
            }, false);
            return 0;
        } else {
            FailedTestTracker.forgetFailedTests();
            commandsourcestack.sendSuccess(() -> {
                return Component.translatable("commands.test.run.running", collection.size());
            }, false);
            GameTestRunner gametestrunner = GameTestRunner.Builder.fromInfo(collection, serverlevel).newStructureSpawner(new StructureGridSpawner(blockpos, testsPerRow, false)).build();

            return trackAndStartRunner(commandsourcestack, gametestrunner);
        }
    }

    private static int locate(TestFinder finder) throws CommandSyntaxException {
        finder.source().sendSystemMessage(Component.translatable("commands.test.locate.started"));
        MutableInt mutableint = new MutableInt(0);
        BlockPos blockpos = BlockPos.containing(finder.source().getPosition());

        finder.findTestPos().forEach((blockpos1) -> {
            BlockEntity blockentity = finder.source().getLevel().getBlockEntity(blockpos1);

            if (blockentity instanceof TestInstanceBlockEntity testinstanceblockentity) {
                Direction direction = testinstanceblockentity.getRotation().rotate(Direction.NORTH);
                BlockPos blockpos2 = testinstanceblockentity.getBlockPos().relative(direction, 2);
                int i = (int) direction.getOpposite().toYRot();
                String s = String.format(Locale.ROOT, "/tp @s %d %d %d %d 0", blockpos2.getX(), blockpos2.getY(), blockpos2.getZ(), i);
                int j = blockpos.getX() - blockpos1.getX();
                int k = blockpos.getZ() - blockpos1.getZ();
                int l = Mth.floor(Mth.sqrt((float) (j * j + k * k)));
                MutableComponent mutablecomponent = ComponentUtils.wrapInSquareBrackets(Component.translatable("chat.coordinates", blockpos1.getX(), blockpos1.getY(), blockpos1.getZ())).withStyle((style) -> {
                    return style.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent.SuggestCommand(s)).withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.coordinates.tooltip")));
                });

                finder.source().sendSuccess(() -> {
                    return Component.translatable("commands.test.locate.found", mutablecomponent, l);
                }, false);
                mutableint.increment();
            }
        });
        int i = mutableint.intValue();

        if (i == 0) {
            throw TestCommand.NO_TEST_INSTANCES.create();
        } else {
            finder.source().sendSuccess(() -> {
                return Component.translatable("commands.test.locate.done", i);
            }, true);
            return i;
        }
    }

    private static ArgumentBuilder<CommandSourceStack, ?> runWithRetryOptions(ArgumentBuilder<CommandSourceStack, ?> runArgument, InCommandFunction<CommandContext<CommandSourceStack>, TestFinder> finder, Function<ArgumentBuilder<CommandSourceStack, ?>, ArgumentBuilder<CommandSourceStack, ?>> then) {
        return runArgument.executes((commandcontext) -> {
            return run(finder.apply(commandcontext), RetryOptions.noRetries(), 0, 8);
        }).then(((RequiredArgumentBuilder) Commands.argument("numberOfTimes", IntegerArgumentType.integer(0)).executes((commandcontext) -> {
            return run(finder.apply(commandcontext), new RetryOptions(IntegerArgumentType.getInteger(commandcontext, "numberOfTimes"), false), 0, 8);
        })).then((ArgumentBuilder) then.apply(Commands.argument("untilFailed", BoolArgumentType.bool()).executes((commandcontext) -> {
            return run(finder.apply(commandcontext), new RetryOptions(IntegerArgumentType.getInteger(commandcontext, "numberOfTimes"), BoolArgumentType.getBool(commandcontext, "untilFailed")), 0, 8);
        }))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> runWithRetryOptions(ArgumentBuilder<CommandSourceStack, ?> runArgument, InCommandFunction<CommandContext<CommandSourceStack>, TestFinder> finder) {
        return runWithRetryOptions(runArgument, finder, (argumentbuilder1) -> {
            return argumentbuilder1;
        });
    }

    private static ArgumentBuilder<CommandSourceStack, ?> runWithRetryOptionsAndBuildInfo(ArgumentBuilder<CommandSourceStack, ?> runArgument, InCommandFunction<CommandContext<CommandSourceStack>, TestFinder> finder) {
        return runWithRetryOptions(runArgument, finder, (argumentbuilder1) -> {
            return argumentbuilder1.then(((RequiredArgumentBuilder) Commands.argument("rotationSteps", IntegerArgumentType.integer()).executes((commandcontext) -> {
                return run(finder.apply(commandcontext), new RetryOptions(IntegerArgumentType.getInteger(commandcontext, "numberOfTimes"), BoolArgumentType.getBool(commandcontext, "untilFailed")), IntegerArgumentType.getInteger(commandcontext, "rotationSteps"), 8);
            })).then(Commands.argument("testsPerRow", IntegerArgumentType.integer()).executes((commandcontext) -> {
                return run(finder.apply(commandcontext), new RetryOptions(IntegerArgumentType.getInteger(commandcontext, "numberOfTimes"), BoolArgumentType.getBool(commandcontext, "untilFailed")), IntegerArgumentType.getInteger(commandcontext, "rotationSteps"), IntegerArgumentType.getInteger(commandcontext, "testsPerRow"));
            })));
        });
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        ArgumentBuilder<CommandSourceStack, ?> argumentbuilder = runWithRetryOptionsAndBuildInfo(Commands.argument("onlyRequiredTests", BoolArgumentType.bool()), (commandcontext) -> {
            return TestFinder.builder().failedTests(commandcontext, BoolArgumentType.getBool(commandcontext, "onlyRequiredTests"));
        });
        LiteralArgumentBuilder literalargumentbuilder = (LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("test").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.literal("run").then(runWithRetryOptionsAndBuildInfo(Commands.argument("tests", ResourceSelectorArgument.resourceSelector(context, Registries.TEST_INSTANCE)), (commandcontext) -> {
            return TestFinder.builder().byResourceSelection(commandcontext, ResourceSelectorArgument.getSelectedResources(commandcontext, "tests"));
        })))).then(Commands.literal("runmultiple").then(((RequiredArgumentBuilder) Commands.argument("tests", ResourceSelectorArgument.resourceSelector(context, Registries.TEST_INSTANCE)).executes((commandcontext) -> {
            return run(TestFinder.builder().byResourceSelection(commandcontext, ResourceSelectorArgument.getSelectedResources(commandcontext, "tests")), RetryOptions.noRetries(), 0, 8);
        })).then(Commands.argument("amount", IntegerArgumentType.integer()).executes((commandcontext) -> {
            return run(TestFinder.builder().createMultipleCopies(IntegerArgumentType.getInteger(commandcontext, "amount")).byResourceSelection(commandcontext, ResourceSelectorArgument.getSelectedResources(commandcontext, "tests")), RetryOptions.noRetries(), 0, 8);
        }))));
        LiteralArgumentBuilder literalargumentbuilder1 = Commands.literal("runthese");
        TestFinder.Builder testfinder_builder = TestFinder.builder();

        Objects.requireNonNull(testfinder_builder);
        literalargumentbuilder = (LiteralArgumentBuilder) literalargumentbuilder.then(runWithRetryOptions(literalargumentbuilder1, testfinder_builder::allNearby));
        literalargumentbuilder1 = Commands.literal("runclosest");
        testfinder_builder = TestFinder.builder();
        Objects.requireNonNull(testfinder_builder);
        literalargumentbuilder = (LiteralArgumentBuilder) literalargumentbuilder.then(runWithRetryOptions(literalargumentbuilder1, testfinder_builder::nearest));
        literalargumentbuilder1 = Commands.literal("runthat");
        testfinder_builder = TestFinder.builder();
        Objects.requireNonNull(testfinder_builder);
        literalargumentbuilder = (LiteralArgumentBuilder) literalargumentbuilder.then(runWithRetryOptions(literalargumentbuilder1, testfinder_builder::lookedAt));
        ArgumentBuilder argumentbuilder1 = Commands.literal("runfailed").then(argumentbuilder);

        testfinder_builder = TestFinder.builder();
        Objects.requireNonNull(testfinder_builder);
        LiteralArgumentBuilder<CommandSourceStack> literalargumentbuilder2 = (LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) literalargumentbuilder.then(runWithRetryOptionsAndBuildInfo(argumentbuilder1, testfinder_builder::failedTests))).then(Commands.literal("verify").then(Commands.argument("tests", ResourceSelectorArgument.resourceSelector(context, Registries.TEST_INSTANCE)).executes((commandcontext) -> {
            return verify(TestFinder.builder().byResourceSelection(commandcontext, ResourceSelectorArgument.getSelectedResources(commandcontext, "tests")));
        })))).then(Commands.literal("locate").then(Commands.argument("tests", ResourceSelectorArgument.resourceSelector(context, Registries.TEST_INSTANCE)).executes((commandcontext) -> {
            return locate(TestFinder.builder().byResourceSelection(commandcontext, ResourceSelectorArgument.getSelectedResources(commandcontext, "tests")));
        })))).then(Commands.literal("resetclosest").executes((commandcontext) -> {
            return reset(TestFinder.builder().nearest(commandcontext));
        }))).then(Commands.literal("resetthese").executes((commandcontext) -> {
            return reset(TestFinder.builder().allNearby(commandcontext));
        }))).then(Commands.literal("resetthat").executes((commandcontext) -> {
            return reset(TestFinder.builder().lookedAt(commandcontext));
        }))).then(Commands.literal("clearthat").executes((commandcontext) -> {
            return clear(TestFinder.builder().lookedAt(commandcontext));
        }))).then(Commands.literal("clearthese").executes((commandcontext) -> {
            return clear(TestFinder.builder().allNearby(commandcontext));
        }))).then(((LiteralArgumentBuilder) Commands.literal("clearall").executes((commandcontext) -> {
            return clear(TestFinder.builder().radius(commandcontext, 250));
        })).then(Commands.argument("radius", IntegerArgumentType.integer()).executes((commandcontext) -> {
            return clear(TestFinder.builder().radius(commandcontext, Mth.clamp(IntegerArgumentType.getInteger(commandcontext, "radius"), 0, 1024)));
        })))).then(Commands.literal("stop").executes((commandcontext) -> {
            return stopTests();
        }))).then(((LiteralArgumentBuilder) Commands.literal("pos").executes((commandcontext) -> {
            return showPos((CommandSourceStack) commandcontext.getSource(), "pos");
        })).then(Commands.argument("var", StringArgumentType.word()).executes((commandcontext) -> {
            return showPos((CommandSourceStack) commandcontext.getSource(), StringArgumentType.getString(commandcontext, "var"));
        })))).then(Commands.literal("create").then(((RequiredArgumentBuilder) Commands.argument("id", IdentifierArgument.id()).suggests(TestCommand::suggestTestFunction).executes((commandcontext) -> {
            return createNewStructure((CommandSourceStack) commandcontext.getSource(), IdentifierArgument.getId(commandcontext, "id"), 5, 5, 5);
        })).then(((RequiredArgumentBuilder) Commands.argument("width", IntegerArgumentType.integer()).executes((commandcontext) -> {
            return createNewStructure((CommandSourceStack) commandcontext.getSource(), IdentifierArgument.getId(commandcontext, "id"), IntegerArgumentType.getInteger(commandcontext, "width"), IntegerArgumentType.getInteger(commandcontext, "width"), IntegerArgumentType.getInteger(commandcontext, "width"));
        })).then(Commands.argument("height", IntegerArgumentType.integer()).then(Commands.argument("depth", IntegerArgumentType.integer()).executes((commandcontext) -> {
            return createNewStructure((CommandSourceStack) commandcontext.getSource(), IdentifierArgument.getId(commandcontext, "id"), IntegerArgumentType.getInteger(commandcontext, "width"), IntegerArgumentType.getInteger(commandcontext, "height"), IntegerArgumentType.getInteger(commandcontext, "depth"));
        }))))));

        if (SharedConstants.IS_RUNNING_IN_IDE) {
            literalargumentbuilder2 = (LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) literalargumentbuilder2.then(Commands.literal("export").then(Commands.argument("test", ResourceArgument.resource(context, Registries.TEST_INSTANCE)).executes((commandcontext) -> {
                return exportTestStructure((CommandSourceStack) commandcontext.getSource(), ResourceArgument.getResource(commandcontext, "test", Registries.TEST_INSTANCE));
            })))).then(Commands.literal("exportclosest").executes((commandcontext) -> {
                return export(TestFinder.builder().nearest(commandcontext));
            }))).then(Commands.literal("exportthese").executes((commandcontext) -> {
                return export(TestFinder.builder().allNearby(commandcontext));
            }))).then(Commands.literal("exportthat").executes((commandcontext) -> {
                return export(TestFinder.builder().lookedAt(commandcontext));
            }));
        }

        dispatcher.register(literalargumentbuilder2);
    }

    public static CompletableFuture<Suggestions> suggestTestFunction(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        Stream<String> stream = ((CommandSourceStack) context.getSource()).registryAccess().lookupOrThrow(Registries.TEST_FUNCTION).listElements().map(Holder::getRegisteredName);

        return SharedSuggestionProvider.suggest(stream, builder);
    }

    private static int resetGameTestInfo(CommandSourceStack source, GameTestInfo testInfo) {
        TestInstanceBlockEntity testinstanceblockentity = testInfo.getTestInstanceBlockEntity();

        Objects.requireNonNull(source);
        testinstanceblockentity.resetTest(source::sendSystemMessage);
        return 1;
    }

    private static Stream<GameTestInfo> toGameTestInfos(CommandSourceStack source, RetryOptions retryOptions, TestPosFinder finder) {
        return finder.findTestPos().map((blockpos) -> {
            return createGameTestInfo(blockpos, source, retryOptions);
        }).flatMap(Optional::stream);
    }

    private static Stream<GameTestInfo> toGameTestInfo(CommandSourceStack source, RetryOptions retryOptions, TestInstanceFinder finder, int rotationSteps) {
        return finder.findTests().filter((holder_reference) -> {
            return verifyStructureExists(source, ((GameTestInstance) holder_reference.value()).structure());
        }).map((holder_reference) -> {
            return new GameTestInfo(holder_reference, StructureUtils.getRotationForRotationSteps(rotationSteps), source.getLevel(), retryOptions);
        });
    }

    private static Optional<GameTestInfo> createGameTestInfo(BlockPos testBlockPos, CommandSourceStack source, RetryOptions retryOptions) {
        ServerLevel serverlevel = source.getLevel();
        BlockEntity blockentity = serverlevel.getBlockEntity(testBlockPos);

        if (blockentity instanceof TestInstanceBlockEntity testinstanceblockentity) {
            Optional optional = testinstanceblockentity.test();
            Registry registry = source.registryAccess().lookupOrThrow(Registries.TEST_INSTANCE);

            Objects.requireNonNull(registry);
            Optional<Holder.Reference<GameTestInstance>> optional1 = optional.flatMap(registry::get);

            if (optional1.isEmpty()) {
                source.sendFailure(Component.translatable("commands.test.error.non_existant_test", testinstanceblockentity.getTestName()));
                return Optional.empty();
            } else {
                Holder.Reference<GameTestInstance> holder_reference = (Holder.Reference) optional1.get();
                GameTestInfo gametestinfo = new GameTestInfo(holder_reference, testinstanceblockentity.getRotation(), serverlevel, retryOptions);

                gametestinfo.setTestBlockPos(testBlockPos);
                return !verifyStructureExists(source, gametestinfo.getStructure()) ? Optional.empty() : Optional.of(gametestinfo);
            }
        } else {
            source.sendFailure(Component.translatable("commands.test.error.test_instance_not_found.position", testBlockPos.getX(), testBlockPos.getY(), testBlockPos.getZ()));
            return Optional.empty();
        }
    }

    private static int createNewStructure(CommandSourceStack source, Identifier id, int xSize, int ySize, int zSize) throws CommandSyntaxException {
        if (xSize <= 48 && ySize <= 48 && zSize <= 48) {
            ServerLevel serverlevel = source.getLevel();
            BlockPos blockpos = createTestPositionAround(source);
            TestInstanceBlockEntity testinstanceblockentity = StructureUtils.createNewEmptyTest(id, blockpos, new Vec3i(xSize, ySize, zSize), Rotation.NONE, serverlevel);
            BlockPos blockpos1 = testinstanceblockentity.getStructurePos();
            BlockPos blockpos2 = blockpos1.offset(xSize - 1, 0, zSize - 1);

            BlockPos.betweenClosedStream(blockpos1, blockpos2).forEach((blockpos3) -> {
                serverlevel.setBlockAndUpdate(blockpos3, Blocks.BEDROCK.defaultBlockState());
            });
            source.sendSuccess(() -> {
                return Component.translatable("commands.test.create.success", testinstanceblockentity.getTestName());
            }, true);
            return 1;
        } else {
            throw TestCommand.TOO_LARGE.create(48);
        }
    }

    private static int showPos(CommandSourceStack source, String s) throws CommandSyntaxException {
        ServerPlayer serverplayer = source.getPlayerOrException();
        BlockHitResult blockhitresult = (BlockHitResult) serverplayer.pick(10.0D, 1.0F, false);
        BlockPos blockpos = blockhitresult.getBlockPos();
        ServerLevel serverlevel = source.getLevel();
        Optional<BlockPos> optional = StructureUtils.findTestContainingPos(blockpos, 15, serverlevel);

        if (optional.isEmpty()) {
            optional = StructureUtils.findTestContainingPos(blockpos, 250, serverlevel);
        }

        if (optional.isEmpty()) {
            throw TestCommand.NO_TEST_CONTAINING.create(blockpos.getX(), blockpos.getY(), blockpos.getZ());
        } else {
            BlockEntity blockentity = serverlevel.getBlockEntity((BlockPos) optional.get());

            if (blockentity instanceof TestInstanceBlockEntity) {
                TestInstanceBlockEntity testinstanceblockentity = (TestInstanceBlockEntity) blockentity;
                BlockPos blockpos1 = testinstanceblockentity.getStructurePos();
                BlockPos blockpos2 = blockpos.subtract(blockpos1);
                int i = blockpos2.getX();
                String s1 = i + ", " + blockpos2.getY() + ", " + blockpos2.getZ();
                String s2 = testinstanceblockentity.getTestName().getString();
                MutableComponent mutablecomponent = Component.translatable("commands.test.coordinates", blockpos2.getX(), blockpos2.getY(), blockpos2.getZ()).setStyle(Style.EMPTY.withBold(true).withColor(ChatFormatting.GREEN).withHoverEvent(new HoverEvent.ShowText(Component.translatable("commands.test.coordinates.copy"))).withClickEvent(new ClickEvent.CopyToClipboard("final BlockPos " + s + " = new BlockPos(" + s1 + ");")));

                source.sendSuccess(() -> {
                    return Component.translatable("commands.test.relative_position", s2, mutablecomponent);
                }, false);
                serverplayer.connection.send(new ClientboundGameTestHighlightPosPacket(blockpos, blockpos2));
                return 1;
            } else {
                throw TestCommand.TEST_INSTANCE_COULD_NOT_BE_FOUND.create();
            }
        }
    }

    private static int stopTests() {
        GameTestTicker.SINGLETON.clear();
        return 1;
    }

    public static int trackAndStartRunner(CommandSourceStack source, GameTestRunner runner) {
        runner.addListener(new TestCommand.TestBatchSummaryDisplayer(source));
        MultipleTestTracker multipletesttracker = new MultipleTestTracker(runner.getTestInfos());

        multipletesttracker.addListener(new TestCommand.TestSummaryDisplayer(source, multipletesttracker));
        multipletesttracker.addFailureListener((gametestinfo) -> {
            FailedTestTracker.rememberFailedTest(gametestinfo.getTestHolder());
        });
        runner.start();
        return 1;
    }

    private static int exportTestStructure(CommandSourceStack source, Holder<GameTestInstance> test) {
        ServerLevel serverlevel = source.getLevel();
        Identifier identifier = ((GameTestInstance) test.value()).structure();

        Objects.requireNonNull(source);
        return !TestInstanceBlockEntity.export(serverlevel, identifier, source::sendSystemMessage) ? 0 : 1;
    }

    private static boolean verifyStructureExists(CommandSourceStack source, Identifier structure) {
        if (source.getLevel().getStructureManager().get(structure).isEmpty()) {
            source.sendFailure(Component.translatable("commands.test.error.structure_not_found", Component.translationArg(structure)));
            return false;
        } else {
            return true;
        }
    }

    private static BlockPos createTestPositionAround(CommandSourceStack source) {
        BlockPos blockpos = BlockPos.containing(source.getPosition());
        int i = source.getLevel().getHeightmapPos(Heightmap.Types.WORLD_SURFACE, blockpos).getY();

        return new BlockPos(blockpos.getX(), i, blockpos.getZ() + 3);
    }

    public static record TestSummaryDisplayer(CommandSourceStack source, MultipleTestTracker tracker) implements GameTestListener {

        @Override
        public void testStructureLoaded(GameTestInfo testInfo) {}

        @Override
        public void testPassed(GameTestInfo testInfo, GameTestRunner runner) {
            this.showTestSummaryIfAllDone();
        }

        @Override
        public void testFailed(GameTestInfo testInfo, GameTestRunner runner) {
            this.showTestSummaryIfAllDone();
        }

        @Override
        public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {
            this.tracker.addTestToTrack(copy);
        }

        private void showTestSummaryIfAllDone() {
            if (this.tracker.isDone()) {
                this.source.sendSuccess(() -> {
                    return Component.translatable("commands.test.summary", this.tracker.getTotalCount()).withStyle(ChatFormatting.WHITE);
                }, true);
                if (this.tracker.hasFailedRequired()) {
                    this.source.sendFailure(Component.translatable("commands.test.summary.failed", this.tracker.getFailedRequiredCount()));
                } else {
                    this.source.sendSuccess(() -> {
                        return Component.translatable("commands.test.summary.all_required_passed").withStyle(ChatFormatting.GREEN);
                    }, true);
                }

                if (this.tracker.hasFailedOptional()) {
                    this.source.sendSystemMessage(Component.translatable("commands.test.summary.optional_failed", this.tracker.getFailedOptionalCount()));
                }
            }

        }
    }

    private static record TestBatchSummaryDisplayer(CommandSourceStack source) implements GameTestBatchListener {

        @Override
        public void testBatchStarting(GameTestBatch batch) {
            this.source.sendSuccess(() -> {
                return Component.translatable("commands.test.batch.starting", batch.environment().getRegisteredName(), batch.index());
            }, true);
        }

        @Override
        public void testBatchFinished(GameTestBatch batch) {}
    }
}
