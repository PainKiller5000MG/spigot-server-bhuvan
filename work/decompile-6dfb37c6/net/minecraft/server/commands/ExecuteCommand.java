package net.minecraft.server.commands;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.HeightmapTypeArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.ObjectiveArgument;
import net.minecraft.commands.arguments.RangeArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.ResourceOrIdArgument;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.commands.arguments.SlotsArgument;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.SwizzleArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.CustomModifierExecutor;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.tasks.BuildContexts;
import net.minecraft.commands.execution.tasks.CallFunction;
import net.minecraft.commands.execution.tasks.FallthroughTask;
import net.minecraft.commands.execution.tasks.IsolatedCall;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.commands.data.DataAccessor;
import net.minecraft.server.commands.data.DataCommands;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.Stopwatch;
import net.minecraft.world.Stopwatches;
import net.minecraft.world.entity.Attackable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.SlotProvider;
import net.minecraft.world.entity.Targeting;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.inventory.SlotRange;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ExecuteCommand {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_TEST_AREA = 32768;
    private static final Dynamic2CommandExceptionType ERROR_AREA_TOO_LARGE = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("commands.execute.blocks.toobig", object, object1);
    });
    private static final SimpleCommandExceptionType ERROR_CONDITIONAL_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.execute.conditional.fail"));
    private static final DynamicCommandExceptionType ERROR_CONDITIONAL_FAILED_COUNT = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.execute.conditional.fail_count", object);
    });
    @VisibleForTesting
    public static final Dynamic2CommandExceptionType ERROR_FUNCTION_CONDITION_INSTANTATION_FAILURE = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("commands.execute.function.instantiationFailure", object, object1);
    });

    public ExecuteCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        LiteralCommandNode<CommandSourceStack> literalcommandnode = dispatcher.register((LiteralArgumentBuilder) Commands.literal("execute").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)));

        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("execute").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.literal("run").redirect(dispatcher.getRoot()))).then(addConditionals(literalcommandnode, Commands.literal("if"), true, context))).then(addConditionals(literalcommandnode, Commands.literal("unless"), false, context))).then(Commands.literal("as").then(Commands.argument("targets", EntityArgument.entities()).fork(literalcommandnode, (commandcontext) -> {
            List<CommandSourceStack> list = Lists.newArrayList();

            for (Entity entity : EntityArgument.getOptionalEntities(commandcontext, "targets")) {
                list.add(((CommandSourceStack) commandcontext.getSource()).withEntity(entity));
            }

            return list;
        })))).then(Commands.literal("at").then(Commands.argument("targets", EntityArgument.entities()).fork(literalcommandnode, (commandcontext) -> {
            List<CommandSourceStack> list = Lists.newArrayList();

            for (Entity entity : EntityArgument.getOptionalEntities(commandcontext, "targets")) {
                list.add(((CommandSourceStack) commandcontext.getSource()).withLevel((ServerLevel) entity.level()).withPosition(entity.position()).withRotation(entity.getRotationVector()));
            }

            return list;
        })))).then(((LiteralArgumentBuilder) Commands.literal("store").then(wrapStores(literalcommandnode, Commands.literal("result"), true))).then(wrapStores(literalcommandnode, Commands.literal("success"), false)))).then(((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("positioned").then(Commands.argument("pos", Vec3Argument.vec3()).redirect(literalcommandnode, (commandcontext) -> {
            return ((CommandSourceStack) commandcontext.getSource()).withPosition(Vec3Argument.getVec3(commandcontext, "pos")).withAnchor(EntityAnchorArgument.Anchor.FEET);
        }))).then(Commands.literal("as").then(Commands.argument("targets", EntityArgument.entities()).fork(literalcommandnode, (commandcontext) -> {
            List<CommandSourceStack> list = Lists.newArrayList();

            for (Entity entity : EntityArgument.getOptionalEntities(commandcontext, "targets")) {
                list.add(((CommandSourceStack) commandcontext.getSource()).withPosition(entity.position()));
            }

            return list;
        })))).then(Commands.literal("over").then(Commands.argument("heightmap", HeightmapTypeArgument.heightmap()).redirect(literalcommandnode, (commandcontext) -> {
            Vec3 vec3 = ((CommandSourceStack) commandcontext.getSource()).getPosition();
            ServerLevel serverlevel = ((CommandSourceStack) commandcontext.getSource()).getLevel();
            double d0 = vec3.x();
            double d1 = vec3.z();

            if (!serverlevel.hasChunk(SectionPos.blockToSectionCoord(d0), SectionPos.blockToSectionCoord(d1))) {
                throw BlockPosArgument.ERROR_NOT_LOADED.create();
            } else {
                int i = serverlevel.getHeight(HeightmapTypeArgument.getHeightmap(commandcontext, "heightmap"), Mth.floor(d0), Mth.floor(d1));

                return ((CommandSourceStack) commandcontext.getSource()).withPosition(new Vec3(d0, (double) i, d1));
            }
        }))))).then(((LiteralArgumentBuilder) Commands.literal("rotated").then(Commands.argument("rot", RotationArgument.rotation()).redirect(literalcommandnode, (commandcontext) -> {
            return ((CommandSourceStack) commandcontext.getSource()).withRotation(RotationArgument.getRotation(commandcontext, "rot").getRotation((CommandSourceStack) commandcontext.getSource()));
        }))).then(Commands.literal("as").then(Commands.argument("targets", EntityArgument.entities()).fork(literalcommandnode, (commandcontext) -> {
            List<CommandSourceStack> list = Lists.newArrayList();

            for (Entity entity : EntityArgument.getOptionalEntities(commandcontext, "targets")) {
                list.add(((CommandSourceStack) commandcontext.getSource()).withRotation(entity.getRotationVector()));
            }

            return list;
        }))))).then(((LiteralArgumentBuilder) Commands.literal("facing").then(Commands.literal("entity").then(Commands.argument("targets", EntityArgument.entities()).then(Commands.argument("anchor", EntityAnchorArgument.anchor()).fork(literalcommandnode, (commandcontext) -> {
            List<CommandSourceStack> list = Lists.newArrayList();
            EntityAnchorArgument.Anchor entityanchorargument_anchor = EntityAnchorArgument.getAnchor(commandcontext, "anchor");

            for (Entity entity : EntityArgument.getOptionalEntities(commandcontext, "targets")) {
                list.add(((CommandSourceStack) commandcontext.getSource()).facing(entity, entityanchorargument_anchor));
            }

            return list;
        }))))).then(Commands.argument("pos", Vec3Argument.vec3()).redirect(literalcommandnode, (commandcontext) -> {
            return ((CommandSourceStack) commandcontext.getSource()).facing(Vec3Argument.getVec3(commandcontext, "pos"));
        })))).then(Commands.literal("align").then(Commands.argument("axes", SwizzleArgument.swizzle()).redirect(literalcommandnode, (commandcontext) -> {
            return ((CommandSourceStack) commandcontext.getSource()).withPosition(((CommandSourceStack) commandcontext.getSource()).getPosition().align(SwizzleArgument.getSwizzle(commandcontext, "axes")));
        })))).then(Commands.literal("anchored").then(Commands.argument("anchor", EntityAnchorArgument.anchor()).redirect(literalcommandnode, (commandcontext) -> {
            return ((CommandSourceStack) commandcontext.getSource()).withAnchor(EntityAnchorArgument.getAnchor(commandcontext, "anchor"));
        })))).then(Commands.literal("in").then(Commands.argument("dimension", DimensionArgument.dimension()).redirect(literalcommandnode, (commandcontext) -> {
            return ((CommandSourceStack) commandcontext.getSource()).withLevel(DimensionArgument.getDimension(commandcontext, "dimension"));
        })))).then(Commands.literal("summon").then(Commands.argument("entity", ResourceArgument.resource(context, Registries.ENTITY_TYPE)).suggests(SuggestionProviders.cast(SuggestionProviders.SUMMONABLE_ENTITIES)).redirect(literalcommandnode, (commandcontext) -> {
            return spawnEntityAndRedirect((CommandSourceStack) commandcontext.getSource(), ResourceArgument.getSummonableEntityType(commandcontext, "entity"));
        })))).then(createRelationOperations(literalcommandnode, Commands.literal("on"))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> wrapStores(LiteralCommandNode<CommandSourceStack> execute, LiteralArgumentBuilder<CommandSourceStack> literal, boolean storeResult) {
        literal.then(Commands.literal("score").then(Commands.argument("targets", ScoreHolderArgument.scoreHolders()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS).then(Commands.argument("objective", ObjectiveArgument.objective()).redirect(execute, (commandcontext) -> {
            return storeValue((CommandSourceStack) commandcontext.getSource(), ScoreHolderArgument.getNamesWithDefaultWildcard(commandcontext, "targets"), ObjectiveArgument.getObjective(commandcontext, "objective"), storeResult);
        }))));
        literal.then(Commands.literal("bossbar").then(((RequiredArgumentBuilder) Commands.argument("id", IdentifierArgument.id()).suggests(BossBarCommands.SUGGEST_BOSS_BAR).then(Commands.literal("value").redirect(execute, (commandcontext) -> {
            return storeValue((CommandSourceStack) commandcontext.getSource(), BossBarCommands.getBossBar(commandcontext), true, storeResult);
        }))).then(Commands.literal("max").redirect(execute, (commandcontext) -> {
            return storeValue((CommandSourceStack) commandcontext.getSource(), BossBarCommands.getBossBar(commandcontext), false, storeResult);
        }))));

        for (DataCommands.DataProvider datacommands_dataprovider : DataCommands.TARGET_PROVIDERS) {
            datacommands_dataprovider.wrap(literal, (argumentbuilder) -> {
                return argumentbuilder.then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument("path", NbtPathArgument.nbtPath()).then(Commands.literal("int").then(Commands.argument("scale", DoubleArgumentType.doubleArg()).redirect(execute, (commandcontext) -> {
                    return storeData((CommandSourceStack) commandcontext.getSource(), datacommands_dataprovider.access(commandcontext), NbtPathArgument.getPath(commandcontext, "path"), (i) -> {
                        return IntTag.valueOf((int) ((double) i * DoubleArgumentType.getDouble(commandcontext, "scale")));
                    }, storeResult);
                })))).then(Commands.literal("float").then(Commands.argument("scale", DoubleArgumentType.doubleArg()).redirect(execute, (commandcontext) -> {
                    return storeData((CommandSourceStack) commandcontext.getSource(), datacommands_dataprovider.access(commandcontext), NbtPathArgument.getPath(commandcontext, "path"), (i) -> {
                        return FloatTag.valueOf((float) ((double) i * DoubleArgumentType.getDouble(commandcontext, "scale")));
                    }, storeResult);
                })))).then(Commands.literal("short").then(Commands.argument("scale", DoubleArgumentType.doubleArg()).redirect(execute, (commandcontext) -> {
                    return storeData((CommandSourceStack) commandcontext.getSource(), datacommands_dataprovider.access(commandcontext), NbtPathArgument.getPath(commandcontext, "path"), (i) -> {
                        return ShortTag.valueOf((short) ((int) ((double) i * DoubleArgumentType.getDouble(commandcontext, "scale"))));
                    }, storeResult);
                })))).then(Commands.literal("long").then(Commands.argument("scale", DoubleArgumentType.doubleArg()).redirect(execute, (commandcontext) -> {
                    return storeData((CommandSourceStack) commandcontext.getSource(), datacommands_dataprovider.access(commandcontext), NbtPathArgument.getPath(commandcontext, "path"), (i) -> {
                        return LongTag.valueOf((long) ((double) i * DoubleArgumentType.getDouble(commandcontext, "scale")));
                    }, storeResult);
                })))).then(Commands.literal("double").then(Commands.argument("scale", DoubleArgumentType.doubleArg()).redirect(execute, (commandcontext) -> {
                    return storeData((CommandSourceStack) commandcontext.getSource(), datacommands_dataprovider.access(commandcontext), NbtPathArgument.getPath(commandcontext, "path"), (i) -> {
                        return DoubleTag.valueOf((double) i * DoubleArgumentType.getDouble(commandcontext, "scale"));
                    }, storeResult);
                })))).then(Commands.literal("byte").then(Commands.argument("scale", DoubleArgumentType.doubleArg()).redirect(execute, (commandcontext) -> {
                    return storeData((CommandSourceStack) commandcontext.getSource(), datacommands_dataprovider.access(commandcontext), NbtPathArgument.getPath(commandcontext, "path"), (i) -> {
                        return ByteTag.valueOf((byte) ((int) ((double) i * DoubleArgumentType.getDouble(commandcontext, "scale"))));
                    }, storeResult);
                }))));
            });
        }

        return literal;
    }

    private static CommandSourceStack storeValue(CommandSourceStack source, Collection<ScoreHolder> names, Objective objective, boolean storeResult) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        return source.withCallback((flag1, i) -> {
            for (ScoreHolder scoreholder : names) {
                ScoreAccess scoreaccess = scoreboard.getOrCreatePlayerScore(scoreholder, objective);
                int j = storeResult ? i : (flag1 ? 1 : 0);

                scoreaccess.set(j);
            }

        }, CommandResultCallback::chain);
    }

    private static CommandSourceStack storeValue(CommandSourceStack source, CustomBossEvent event, boolean storeIntoValue, boolean storeResult) {
        return source.withCallback((flag2, i) -> {
            int j = storeResult ? i : (flag2 ? 1 : 0);

            if (storeIntoValue) {
                event.setValue(j);
            } else {
                event.setMax(j);
            }

        }, CommandResultCallback::chain);
    }

    private static CommandSourceStack storeData(CommandSourceStack source, DataAccessor accessor, NbtPathArgument.NbtPath path, IntFunction<Tag> constructor, boolean storeResult) {
        return source.withCallback((flag1, i) -> {
            try {
                CompoundTag compoundtag = accessor.getData();
                int j = storeResult ? i : (flag1 ? 1 : 0);

                path.set(compoundtag, (Tag) constructor.apply(j));
                accessor.setData(compoundtag);
            } catch (CommandSyntaxException commandsyntaxexception) {
                ;
            }

        }, CommandResultCallback::chain);
    }

    private static boolean isChunkLoaded(ServerLevel level, BlockPos pos) {
        ChunkPos chunkpos = new ChunkPos(pos);
        LevelChunk levelchunk = level.getChunkSource().getChunkNow(chunkpos.x, chunkpos.z);

        return levelchunk == null ? false : levelchunk.getFullStatus() == FullChunkStatus.ENTITY_TICKING && level.areEntitiesLoaded(chunkpos.toLong());
    }

    private static ArgumentBuilder<CommandSourceStack, ?> addConditionals(CommandNode<CommandSourceStack> execute, LiteralArgumentBuilder<CommandSourceStack> parent, boolean expected, CommandBuildContext context) {
        ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) parent.then(Commands.literal("block").then(Commands.argument("pos", BlockPosArgument.blockPos()).then(addConditional(execute, Commands.argument("block", BlockPredicateArgument.blockPredicate(context)), expected, (commandcontext) -> {
            return BlockPredicateArgument.getBlockPredicate(commandcontext, "block").test(new BlockInWorld(((CommandSourceStack) commandcontext.getSource()).getLevel(), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), true));
        }))))).then(Commands.literal("biome").then(Commands.argument("pos", BlockPosArgument.blockPos()).then(addConditional(execute, Commands.argument("biome", ResourceOrTagArgument.resourceOrTag(context, Registries.BIOME)), expected, (commandcontext) -> {
            return ResourceOrTagArgument.getResourceOrTag(commandcontext, "biome", Registries.BIOME).test(((CommandSourceStack) commandcontext.getSource()).getLevel().getBiome(BlockPosArgument.getLoadedBlockPos(commandcontext, "pos")));
        }))))).then(Commands.literal("loaded").then(addConditional(execute, Commands.argument("pos", BlockPosArgument.blockPos()), expected, (commandcontext) -> {
            return isChunkLoaded(((CommandSourceStack) commandcontext.getSource()).getLevel(), BlockPosArgument.getBlockPos(commandcontext, "pos"));
        })))).then(Commands.literal("dimension").then(addConditional(execute, Commands.argument("dimension", DimensionArgument.dimension()), expected, (commandcontext) -> {
            return DimensionArgument.getDimension(commandcontext, "dimension") == ((CommandSourceStack) commandcontext.getSource()).getLevel();
        })))).then(Commands.literal("score").then(Commands.argument("target", ScoreHolderArgument.scoreHolder()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS).then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument("targetObjective", ObjectiveArgument.objective()).then(Commands.literal("=").then(Commands.argument("source", ScoreHolderArgument.scoreHolder()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS).then(addConditional(execute, Commands.argument("sourceObjective", ObjectiveArgument.objective()), expected, (commandcontext) -> {
            return checkScore(commandcontext, (i, j) -> {
                return i == j;
            });
        }))))).then(Commands.literal("<").then(Commands.argument("source", ScoreHolderArgument.scoreHolder()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS).then(addConditional(execute, Commands.argument("sourceObjective", ObjectiveArgument.objective()), expected, (commandcontext) -> {
            return checkScore(commandcontext, (i, j) -> {
                return i < j;
            });
        }))))).then(Commands.literal("<=").then(Commands.argument("source", ScoreHolderArgument.scoreHolder()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS).then(addConditional(execute, Commands.argument("sourceObjective", ObjectiveArgument.objective()), expected, (commandcontext) -> {
            return checkScore(commandcontext, (i, j) -> {
                return i <= j;
            });
        }))))).then(Commands.literal(">").then(Commands.argument("source", ScoreHolderArgument.scoreHolder()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS).then(addConditional(execute, Commands.argument("sourceObjective", ObjectiveArgument.objective()), expected, (commandcontext) -> {
            return checkScore(commandcontext, (i, j) -> {
                return i > j;
            });
        }))))).then(Commands.literal(">=").then(Commands.argument("source", ScoreHolderArgument.scoreHolder()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS).then(addConditional(execute, Commands.argument("sourceObjective", ObjectiveArgument.objective()), expected, (commandcontext) -> {
            return checkScore(commandcontext, (i, j) -> {
                return i >= j;
            });
        }))))).then(Commands.literal("matches").then(addConditional(execute, Commands.argument("range", RangeArgument.intRange()), expected, (commandcontext) -> {
            return checkScore(commandcontext, RangeArgument.Ints.getRange(commandcontext, "range"));
        }))))))).then(Commands.literal("blocks").then(Commands.argument("start", BlockPosArgument.blockPos()).then(Commands.argument("end", BlockPosArgument.blockPos()).then(((RequiredArgumentBuilder) Commands.argument("destination", BlockPosArgument.blockPos()).then(addIfBlocksConditional(execute, Commands.literal("all"), expected, false))).then(addIfBlocksConditional(execute, Commands.literal("masked"), expected, true))))))).then(Commands.literal("entity").then(((RequiredArgumentBuilder) Commands.argument("entities", EntityArgument.entities()).fork(execute, (commandcontext) -> {
            return expect(commandcontext, expected, !EntityArgument.getOptionalEntities(commandcontext, "entities").isEmpty());
        })).executes(createNumericConditionalHandler(expected, (commandcontext) -> {
            return EntityArgument.getOptionalEntities(commandcontext, "entities").size();
        }))))).then(Commands.literal("predicate").then(addConditional(execute, Commands.argument("predicate", ResourceOrIdArgument.lootPredicate(context)), expected, (commandcontext) -> {
            return checkCustomPredicate((CommandSourceStack) commandcontext.getSource(), ResourceOrIdArgument.getLootPredicate(commandcontext, "predicate"));
        })))).then(Commands.literal("function").then(Commands.argument("name", FunctionArgument.functions()).suggests(FunctionCommand.SUGGEST_FUNCTION).fork(execute, new ExecuteCommand.ExecuteIfFunctionCustomModifier(expected))))).then(((LiteralArgumentBuilder) Commands.literal("items").then(Commands.literal("entity").then(Commands.argument("entities", EntityArgument.entities()).then(Commands.argument("slots", SlotsArgument.slots()).then(((RequiredArgumentBuilder) Commands.argument("item_predicate", ItemPredicateArgument.itemPredicate(context)).fork(execute, (commandcontext) -> {
            return expect(commandcontext, expected, countItems(EntityArgument.getEntities(commandcontext, "entities"), SlotsArgument.getSlots(commandcontext, "slots"), ItemPredicateArgument.getItemPredicate(commandcontext, "item_predicate")) > 0);
        })).executes(createNumericConditionalHandler(expected, (commandcontext) -> {
            return countItems(EntityArgument.getEntities(commandcontext, "entities"), SlotsArgument.getSlots(commandcontext, "slots"), ItemPredicateArgument.getItemPredicate(commandcontext, "item_predicate"));
        }))))))).then(Commands.literal("block").then(Commands.argument("pos", BlockPosArgument.blockPos()).then(Commands.argument("slots", SlotsArgument.slots()).then(((RequiredArgumentBuilder) Commands.argument("item_predicate", ItemPredicateArgument.itemPredicate(context)).fork(execute, (commandcontext) -> {
            return expect(commandcontext, expected, countItems((CommandSourceStack) commandcontext.getSource(), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), SlotsArgument.getSlots(commandcontext, "slots"), ItemPredicateArgument.getItemPredicate(commandcontext, "item_predicate")) > 0);
        })).executes(createNumericConditionalHandler(expected, (commandcontext) -> {
            return countItems((CommandSourceStack) commandcontext.getSource(), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), SlotsArgument.getSlots(commandcontext, "slots"), ItemPredicateArgument.getItemPredicate(commandcontext, "item_predicate"));
        })))))))).then(Commands.literal("stopwatch").then(Commands.argument("id", IdentifierArgument.id()).suggests(StopwatchCommand.SUGGEST_STOPWATCHES).then(addConditional(execute, Commands.argument("range", RangeArgument.floatRange()), expected, (commandcontext) -> {
            return checkStopwatch(commandcontext, RangeArgument.Floats.getRange(commandcontext, "range"));
        }))));

        for (DataCommands.DataProvider datacommands_dataprovider : DataCommands.SOURCE_PROVIDERS) {
            parent.then(datacommands_dataprovider.wrap(Commands.literal("data"), (argumentbuilder) -> {
                return argumentbuilder.then(((RequiredArgumentBuilder) Commands.argument("path", NbtPathArgument.nbtPath()).fork(execute, (commandcontext) -> {
                    return expect(commandcontext, expected, checkMatchingData(datacommands_dataprovider.access(commandcontext), NbtPathArgument.getPath(commandcontext, "path")) > 0);
                })).executes(createNumericConditionalHandler(expected, (commandcontext) -> {
                    return checkMatchingData(datacommands_dataprovider.access(commandcontext), NbtPathArgument.getPath(commandcontext, "path"));
                })));
            }));
        }

        return parent;
    }

    private static int countItems(Iterable<? extends SlotProvider> sources, SlotRange slotRange, Predicate<ItemStack> predicate) {
        int i = 0;

        for (SlotProvider slotprovider : sources) {
            IntList intlist = slotRange.slots();

            for (int j = 0; j < intlist.size(); ++j) {
                int k = intlist.getInt(j);
                SlotAccess slotaccess = slotprovider.getSlot(k);

                if (slotaccess != null) {
                    ItemStack itemstack = slotaccess.get();

                    if (predicate.test(itemstack)) {
                        i += itemstack.getCount();
                    }
                }
            }
        }

        return i;
    }

    private static int countItems(CommandSourceStack source, BlockPos pos, SlotRange slotRange, Predicate<ItemStack> predicate) throws CommandSyntaxException {
        int i = 0;
        Container container = ItemCommands.getContainer(source, pos, ItemCommands.ERROR_SOURCE_NOT_A_CONTAINER);
        int j = container.getContainerSize();
        IntList intlist = slotRange.slots();

        for (int k = 0; k < intlist.size(); ++k) {
            int l = intlist.getInt(k);

            if (l >= 0 && l < j) {
                ItemStack itemstack = container.getItem(l);

                if (predicate.test(itemstack)) {
                    i += itemstack.getCount();
                }
            }
        }

        return i;
    }

    private static Command<CommandSourceStack> createNumericConditionalHandler(boolean expected, ExecuteCommand.CommandNumericPredicate condition) {
        return expected ? (commandcontext) -> {
            int i = condition.test(commandcontext);

            if (i > 0) {
                ((CommandSourceStack) commandcontext.getSource()).sendSuccess(() -> {
                    return Component.translatable("commands.execute.conditional.pass_count", i);
                }, false);
                return i;
            } else {
                throw ExecuteCommand.ERROR_CONDITIONAL_FAILED.create();
            }
        } : (commandcontext) -> {
            int i = condition.test(commandcontext);

            if (i == 0) {
                ((CommandSourceStack) commandcontext.getSource()).sendSuccess(() -> {
                    return Component.translatable("commands.execute.conditional.pass");
                }, false);
                return 1;
            } else {
                throw ExecuteCommand.ERROR_CONDITIONAL_FAILED_COUNT.create(i);
            }
        };
    }

    private static int checkMatchingData(DataAccessor accessor, NbtPathArgument.NbtPath path) throws CommandSyntaxException {
        return path.countMatching(accessor.getData());
    }

    private static boolean checkScore(CommandContext<CommandSourceStack> context, ExecuteCommand.IntBiPredicate operation) throws CommandSyntaxException {
        ScoreHolder scoreholder = ScoreHolderArgument.getName(context, "target");
        Objective objective = ObjectiveArgument.getObjective(context, "targetObjective");
        ScoreHolder scoreholder1 = ScoreHolderArgument.getName(context, "source");
        Objective objective1 = ObjectiveArgument.getObjective(context, "sourceObjective");
        Scoreboard scoreboard = ((CommandSourceStack) context.getSource()).getServer().getScoreboard();
        ReadOnlyScoreInfo readonlyscoreinfo = scoreboard.getPlayerScoreInfo(scoreholder, objective);
        ReadOnlyScoreInfo readonlyscoreinfo1 = scoreboard.getPlayerScoreInfo(scoreholder1, objective1);

        return readonlyscoreinfo != null && readonlyscoreinfo1 != null ? operation.test(readonlyscoreinfo.value(), readonlyscoreinfo1.value()) : false;
    }

    private static boolean checkScore(CommandContext<CommandSourceStack> context, MinMaxBounds.Ints range) throws CommandSyntaxException {
        ScoreHolder scoreholder = ScoreHolderArgument.getName(context, "target");
        Objective objective = ObjectiveArgument.getObjective(context, "targetObjective");
        Scoreboard scoreboard = ((CommandSourceStack) context.getSource()).getServer().getScoreboard();
        ReadOnlyScoreInfo readonlyscoreinfo = scoreboard.getPlayerScoreInfo(scoreholder, objective);

        return readonlyscoreinfo == null ? false : range.matches(readonlyscoreinfo.value());
    }

    private static boolean checkStopwatch(CommandContext<CommandSourceStack> context, MinMaxBounds.Doubles range) throws CommandSyntaxException {
        Identifier identifier = IdentifierArgument.getId(context, "id");
        Stopwatches stopwatches = ((CommandSourceStack) context.getSource()).getServer().getStopwatches();
        Stopwatch stopwatch = stopwatches.get(identifier);

        if (stopwatch == null) {
            throw StopwatchCommand.ERROR_DOES_NOT_EXIST.create(identifier);
        } else {
            long i = Stopwatches.currentTime();
            double d0 = stopwatch.elapsedSeconds(i);

            return range.matches(d0);
        }
    }

    private static boolean checkCustomPredicate(CommandSourceStack source, Holder<LootItemCondition> predicate) {
        ServerLevel serverlevel = source.getLevel();
        LootParams lootparams = (new LootParams.Builder(serverlevel)).withParameter(LootContextParams.ORIGIN, source.getPosition()).withOptionalParameter(LootContextParams.THIS_ENTITY, source.getEntity()).create(LootContextParamSets.COMMAND);
        LootContext lootcontext = (new LootContext.Builder(lootparams)).create(Optional.empty());

        lootcontext.pushVisitedElement(LootContext.createVisitedEntry(predicate.value()));
        return ((LootItemCondition) predicate.value()).test(lootcontext);
    }

    private static Collection<CommandSourceStack> expect(CommandContext<CommandSourceStack> context, boolean expected, boolean result) {
        return (Collection<CommandSourceStack>) (result == expected ? Collections.singleton((CommandSourceStack) context.getSource()) : Collections.emptyList());
    }

    private static ArgumentBuilder<CommandSourceStack, ?> addConditional(CommandNode<CommandSourceStack> root, ArgumentBuilder<CommandSourceStack, ?> argument, boolean expected, ExecuteCommand.CommandPredicate predicate) {
        return argument.fork(root, (commandcontext) -> {
            return expect(commandcontext, expected, predicate.test(commandcontext));
        }).executes((commandcontext) -> {
            if (expected == predicate.test(commandcontext)) {
                ((CommandSourceStack) commandcontext.getSource()).sendSuccess(() -> {
                    return Component.translatable("commands.execute.conditional.pass");
                }, false);
                return 1;
            } else {
                throw ExecuteCommand.ERROR_CONDITIONAL_FAILED.create();
            }
        });
    }

    private static ArgumentBuilder<CommandSourceStack, ?> addIfBlocksConditional(CommandNode<CommandSourceStack> root, ArgumentBuilder<CommandSourceStack, ?> argument, boolean expected, boolean skipAir) {
        return argument.fork(root, (commandcontext) -> {
            return expect(commandcontext, expected, checkRegions(commandcontext, skipAir).isPresent());
        }).executes(expected ? (commandcontext) -> {
            return checkIfRegions(commandcontext, skipAir);
        } : (commandcontext) -> {
            return checkUnlessRegions(commandcontext, skipAir);
        });
    }

    private static int checkIfRegions(CommandContext<CommandSourceStack> context, boolean skipAir) throws CommandSyntaxException {
        OptionalInt optionalint = checkRegions(context, skipAir);

        if (optionalint.isPresent()) {
            ((CommandSourceStack) context.getSource()).sendSuccess(() -> {
                return Component.translatable("commands.execute.conditional.pass_count", optionalint.getAsInt());
            }, false);
            return optionalint.getAsInt();
        } else {
            throw ExecuteCommand.ERROR_CONDITIONAL_FAILED.create();
        }
    }

    private static int checkUnlessRegions(CommandContext<CommandSourceStack> context, boolean skipAir) throws CommandSyntaxException {
        OptionalInt optionalint = checkRegions(context, skipAir);

        if (optionalint.isPresent()) {
            throw ExecuteCommand.ERROR_CONDITIONAL_FAILED_COUNT.create(optionalint.getAsInt());
        } else {
            ((CommandSourceStack) context.getSource()).sendSuccess(() -> {
                return Component.translatable("commands.execute.conditional.pass");
            }, false);
            return 1;
        }
    }

    private static OptionalInt checkRegions(CommandContext<CommandSourceStack> context, boolean skipAir) throws CommandSyntaxException {
        return checkRegions(((CommandSourceStack) context.getSource()).getLevel(), BlockPosArgument.getLoadedBlockPos(context, "start"), BlockPosArgument.getLoadedBlockPos(context, "end"), BlockPosArgument.getLoadedBlockPos(context, "destination"), skipAir);
    }

    private static OptionalInt checkRegions(ServerLevel level, BlockPos startPos, BlockPos endPos, BlockPos destPos, boolean skipAir) throws CommandSyntaxException {
        BoundingBox boundingbox = BoundingBox.fromCorners(startPos, endPos);
        BoundingBox boundingbox1 = BoundingBox.fromCorners(destPos, destPos.offset(boundingbox.getLength()));
        BlockPos blockpos3 = new BlockPos(boundingbox1.minX() - boundingbox.minX(), boundingbox1.minY() - boundingbox.minY(), boundingbox1.minZ() - boundingbox.minZ());
        int i = boundingbox.getXSpan() * boundingbox.getYSpan() * boundingbox.getZSpan();

        if (i > 32768) {
            throw ExecuteCommand.ERROR_AREA_TOO_LARGE.create(32768, i);
        } else {
            int j = 0;
            RegistryAccess registryaccess = level.registryAccess();

            try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(ExecuteCommand.LOGGER)) {
                for (int k = boundingbox.minZ(); k <= boundingbox.maxZ(); ++k) {
                    for (int l = boundingbox.minY(); l <= boundingbox.maxY(); ++l) {
                        for (int i1 = boundingbox.minX(); i1 <= boundingbox.maxX(); ++i1) {
                            BlockPos blockpos4 = new BlockPos(i1, l, k);
                            BlockPos blockpos5 = blockpos4.offset(blockpos3);
                            BlockState blockstate = level.getBlockState(blockpos4);

                            if (!skipAir || !blockstate.is(Blocks.AIR)) {
                                if (blockstate != level.getBlockState(blockpos5)) {
                                    return OptionalInt.empty();
                                }

                                BlockEntity blockentity = level.getBlockEntity(blockpos4);
                                BlockEntity blockentity1 = level.getBlockEntity(blockpos5);

                                if (blockentity != null) {
                                    if (blockentity1 == null) {
                                        return OptionalInt.empty();
                                    }

                                    if (blockentity1.getType() != blockentity.getType()) {
                                        return OptionalInt.empty();
                                    }

                                    if (!blockentity.components().equals(blockentity1.components())) {
                                        return OptionalInt.empty();
                                    }

                                    TagValueOutput tagvalueoutput = TagValueOutput.createWithContext(problemreporter_scopedcollector.forChild(blockentity.problemPath()), registryaccess);

                                    blockentity.saveCustomOnly((ValueOutput) tagvalueoutput);
                                    CompoundTag compoundtag = tagvalueoutput.buildResult();
                                    TagValueOutput tagvalueoutput1 = TagValueOutput.createWithContext(problemreporter_scopedcollector.forChild(blockentity1.problemPath()), registryaccess);

                                    blockentity1.saveCustomOnly((ValueOutput) tagvalueoutput1);
                                    CompoundTag compoundtag1 = tagvalueoutput1.buildResult();

                                    if (!compoundtag.equals(compoundtag1)) {
                                        return OptionalInt.empty();
                                    }
                                }

                                ++j;
                            }
                        }
                    }
                }
            }

            return OptionalInt.of(j);
        }
    }

    private static RedirectModifier<CommandSourceStack> expandOneToOneEntityRelation(Function<Entity, Optional<Entity>> unpacker) {
        return (commandcontext) -> {
            CommandSourceStack commandsourcestack = (CommandSourceStack) commandcontext.getSource();
            Entity entity = commandsourcestack.getEntity();

            return (Collection) (entity == null ? List.of() : (Collection) ((Optional) unpacker.apply(entity)).filter((entity1) -> {
                return !entity1.isRemoved();
            }).map((entity1) -> {
                return List.of(commandsourcestack.withEntity(entity1));
            }).orElse(List.of()));
        };
    }

    private static RedirectModifier<CommandSourceStack> expandOneToManyEntityRelation(Function<Entity, Stream<Entity>> unpacker) {
        return (commandcontext) -> {
            CommandSourceStack commandsourcestack = (CommandSourceStack) commandcontext.getSource();
            Entity entity = commandsourcestack.getEntity();

            if (entity == null) {
                return List.of();
            } else {
                Stream stream = ((Stream) unpacker.apply(entity)).filter((entity1) -> {
                    return !entity1.isRemoved();
                });

                Objects.requireNonNull(commandsourcestack);
                return stream.map(commandsourcestack::withEntity).toList();
            }
        };
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createRelationOperations(CommandNode<CommandSourceStack> execute, LiteralArgumentBuilder<CommandSourceStack> on) {
        return (LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) on.then(Commands.literal("owner").fork(execute, expandOneToOneEntityRelation((entity) -> {
            Optional optional;

            if (entity instanceof OwnableEntity ownableentity) {
                optional = Optional.ofNullable(ownableentity.getOwner());
            } else {
                optional = Optional.empty();
            }

            return optional;
        })))).then(Commands.literal("leasher").fork(execute, expandOneToOneEntityRelation((entity) -> {
            Optional optional;

            if (entity instanceof Leashable leashable) {
                optional = Optional.ofNullable(leashable.getLeashHolder());
            } else {
                optional = Optional.empty();
            }

            return optional;
        })))).then(Commands.literal("target").fork(execute, expandOneToOneEntityRelation((entity) -> {
            Optional optional;

            if (entity instanceof Targeting targeting) {
                optional = Optional.ofNullable(targeting.getTarget());
            } else {
                optional = Optional.empty();
            }

            return optional;
        })))).then(Commands.literal("attacker").fork(execute, expandOneToOneEntityRelation((entity) -> {
            Optional optional;

            if (entity instanceof Attackable attackable) {
                optional = Optional.ofNullable(attackable.getLastAttacker());
            } else {
                optional = Optional.empty();
            }

            return optional;
        })))).then(Commands.literal("vehicle").fork(execute, expandOneToOneEntityRelation((entity) -> {
            return Optional.ofNullable(entity.getVehicle());
        })))).then(Commands.literal("controller").fork(execute, expandOneToOneEntityRelation((entity) -> {
            return Optional.ofNullable(entity.getControllingPassenger());
        })))).then(Commands.literal("origin").fork(execute, expandOneToOneEntityRelation((entity) -> {
            Optional optional;

            if (entity instanceof TraceableEntity traceableentity) {
                optional = Optional.ofNullable(traceableentity.getOwner());
            } else {
                optional = Optional.empty();
            }

            return optional;
        })))).then(Commands.literal("passengers").fork(execute, expandOneToManyEntityRelation((entity) -> {
            return entity.getPassengers().stream();
        })));
    }

    private static CommandSourceStack spawnEntityAndRedirect(CommandSourceStack source, Holder.Reference<EntityType<?>> type) throws CommandSyntaxException {
        Entity entity = SummonCommand.createEntity(source, type, source.getPosition(), new CompoundTag(), true);

        return source.withEntity(entity);
    }

    public static <T extends ExecutionCommandSource<T>> void scheduleFunctionConditionsAndTest(T originalSource, List<T> currentSources, Function<T, T> functionContextModifier, IntPredicate check, ContextChain<T> currentStep, @Nullable CompoundTag parameters, ExecutionControl<T> output, InCommandFunction<CommandContext<T>, Collection<CommandFunction<T>>> functionGetter, ChainModifiers modifiers) {
        List<T> list1 = new ArrayList(currentSources.size());

        Collection<CommandFunction<T>> collection;

        try {
            collection = functionGetter.apply(currentStep.getTopContext().copyFor(originalSource));
        } catch (CommandSyntaxException commandsyntaxexception) {
            originalSource.handleError(commandsyntaxexception, modifiers.isForked(), output.tracer());
            return;
        }

        int i = collection.size();

        if (i != 0) {
            List<InstantiatedFunction<T>> list2 = new ArrayList(i);

            try {
                for (CommandFunction<T> commandfunction : collection) {
                    try {
                        list2.add(commandfunction.instantiate(parameters, originalSource.dispatcher()));
                    } catch (FunctionInstantiationException functioninstantiationexception) {
                        throw ExecuteCommand.ERROR_FUNCTION_CONDITION_INSTANTATION_FAILURE.create(commandfunction.id(), functioninstantiationexception.messageComponent());
                    }
                }
            } catch (CommandSyntaxException commandsyntaxexception1) {
                originalSource.handleError(commandsyntaxexception1, modifiers.isForked(), output.tracer());
            }

            for (T t1 : currentSources) {
                T t2 = (T) (functionContextModifier.apply(t1.clearCallbacks()));
                CommandResultCallback commandresultcallback = (flag, j) -> {
                    if (check.test(j)) {
                        list1.add(t1);
                    }

                };

                output.queueNext(new IsolatedCall((executioncontrol1) -> {
                    for (InstantiatedFunction<T> instantiatedfunction : list2) {
                        executioncontrol1.queueNext((new CallFunction(instantiatedfunction, executioncontrol1.currentFrame().returnValueConsumer(), true)).bind(t2));
                    }

                    executioncontrol1.queueNext(FallthroughTask.instance());
                }, commandresultcallback));
            }

            ContextChain<T> contextchain1 = currentStep.nextStage();
            String s = currentStep.getTopContext().getInput();

            output.queueNext(new BuildContexts.Continuation(s, contextchain1, modifiers, originalSource, list1));
        }
    }

    private static class ExecuteIfFunctionCustomModifier implements CustomModifierExecutor.ModifierAdapter<CommandSourceStack> {

        private final IntPredicate check;

        private ExecuteIfFunctionCustomModifier(boolean check) {
            this.check = check ? (i) -> {
                return i != 0;
            } : (i) -> {
                return i == 0;
            };
        }

        public void apply(CommandSourceStack originalSource, List<CommandSourceStack> currentSources, ContextChain<CommandSourceStack> currentStep, ChainModifiers modifiers, ExecutionControl<CommandSourceStack> output) {
            ExecuteCommand.scheduleFunctionConditionsAndTest(originalSource, currentSources, FunctionCommand::modifySenderForExecution, this.check, currentStep, (CompoundTag) null, output, (commandcontext) -> {
                return FunctionArgument.getFunctions(commandcontext, "name");
            }, modifiers);
        }
    }

    @FunctionalInterface
    private interface CommandNumericPredicate {

        int test(CommandContext<CommandSourceStack> c) throws CommandSyntaxException;
    }

    @FunctionalInterface
    private interface CommandPredicate {

        boolean test(CommandContext<CommandSourceStack> c) throws CommandSyntaxException;
    }

    @FunctionalInterface
    private interface IntBiPredicate {

        boolean test(int a, int b);
    }
}
