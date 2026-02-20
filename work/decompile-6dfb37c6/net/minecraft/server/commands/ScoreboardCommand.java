package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.ObjectiveArgument;
import net.minecraft.commands.arguments.ObjectiveCriteriaArgument;
import net.minecraft.commands.arguments.OperationArgument;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.commands.arguments.ScoreboardSlotArgument;
import net.minecraft.commands.arguments.StyleArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jspecify.annotations.Nullable;

public class ScoreboardCommand {

    private static final SimpleCommandExceptionType ERROR_OBJECTIVE_ALREADY_EXISTS = new SimpleCommandExceptionType(Component.translatable("commands.scoreboard.objectives.add.duplicate"));
    private static final SimpleCommandExceptionType ERROR_DISPLAY_SLOT_ALREADY_EMPTY = new SimpleCommandExceptionType(Component.translatable("commands.scoreboard.objectives.display.alreadyEmpty"));
    private static final SimpleCommandExceptionType ERROR_DISPLAY_SLOT_ALREADY_SET = new SimpleCommandExceptionType(Component.translatable("commands.scoreboard.objectives.display.alreadySet"));
    private static final SimpleCommandExceptionType ERROR_TRIGGER_ALREADY_ENABLED = new SimpleCommandExceptionType(Component.translatable("commands.scoreboard.players.enable.failed"));
    private static final SimpleCommandExceptionType ERROR_NOT_TRIGGER = new SimpleCommandExceptionType(Component.translatable("commands.scoreboard.players.enable.invalid"));
    private static final Dynamic2CommandExceptionType ERROR_NO_VALUE = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("commands.scoreboard.players.get.null", object, object1);
    });

    public ScoreboardCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("scoreboard").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("objectives").then(Commands.literal("list").executes((commandcontext) -> {
            return listObjectives((CommandSourceStack) commandcontext.getSource());
        }))).then(Commands.literal("add").then(Commands.argument("objective", StringArgumentType.word()).then(((RequiredArgumentBuilder) Commands.argument("criteria", ObjectiveCriteriaArgument.criteria()).executes((commandcontext) -> {
            return addObjective((CommandSourceStack) commandcontext.getSource(), StringArgumentType.getString(commandcontext, "objective"), ObjectiveCriteriaArgument.getCriteria(commandcontext, "criteria"), Component.literal(StringArgumentType.getString(commandcontext, "objective")));
        })).then(Commands.argument("displayName", ComponentArgument.textComponent(context)).executes((commandcontext) -> {
            return addObjective((CommandSourceStack) commandcontext.getSource(), StringArgumentType.getString(commandcontext, "objective"), ObjectiveCriteriaArgument.getCriteria(commandcontext, "criteria"), ComponentArgument.getResolvedComponent(commandcontext, "displayName"));
        })))))).then(Commands.literal("modify").then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument("objective", ObjectiveArgument.objective()).then(Commands.literal("displayname").then(Commands.argument("displayName", ComponentArgument.textComponent(context)).executes((commandcontext) -> {
            return setDisplayName((CommandSourceStack) commandcontext.getSource(), ObjectiveArgument.getObjective(commandcontext, "objective"), ComponentArgument.getResolvedComponent(commandcontext, "displayName"));
        })))).then(createRenderTypeModify())).then(Commands.literal("displayautoupdate").then(Commands.argument("value", BoolArgumentType.bool()).executes((commandcontext) -> {
            return setDisplayAutoUpdate((CommandSourceStack) commandcontext.getSource(), ObjectiveArgument.getObjective(commandcontext, "objective"), BoolArgumentType.getBool(commandcontext, "value"));
        })))).then(addNumberFormats(context, Commands.literal("numberformat"), (commandcontext, numberformat) -> {
            return setObjectiveFormat((CommandSourceStack) commandcontext.getSource(), ObjectiveArgument.getObjective(commandcontext, "objective"), numberformat);
        }))))).then(Commands.literal("remove").then(Commands.argument("objective", ObjectiveArgument.objective()).executes((commandcontext) -> {
            return removeObjective((CommandSourceStack) commandcontext.getSource(), ObjectiveArgument.getObjective(commandcontext, "objective"));
        })))).then(Commands.literal("setdisplay").then(((RequiredArgumentBuilder) Commands.argument("slot", ScoreboardSlotArgument.displaySlot()).executes((commandcontext) -> {
            return clearDisplaySlot((CommandSourceStack) commandcontext.getSource(), ScoreboardSlotArgument.getDisplaySlot(commandcontext, "slot"));
        })).then(Commands.argument("objective", ObjectiveArgument.objective()).executes((commandcontext) -> {
            return setDisplaySlot((CommandSourceStack) commandcontext.getSource(), ScoreboardSlotArgument.getDisplaySlot(commandcontext, "slot"), ObjectiveArgument.getObjective(commandcontext, "objective"));
        })))))).then(((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("players").then(((LiteralArgumentBuilder) Commands.literal("list").executes((commandcontext) -> {
            return listTrackedPlayers((CommandSourceStack) commandcontext.getSource());
        })).then(Commands.argument("target", ScoreHolderArgument.scoreHolder()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS).executes((commandcontext) -> {
            return listTrackedPlayerScores((CommandSourceStack) commandcontext.getSource(), ScoreHolderArgument.getName(commandcontext, "target"));
        })))).then(Commands.literal("set").then(Commands.argument("targets", ScoreHolderArgument.scoreHolders()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS).then(Commands.argument("objective", ObjectiveArgument.objective()).then(Commands.argument("score", IntegerArgumentType.integer()).executes((commandcontext) -> {
            return setScore((CommandSourceStack) commandcontext.getSource(), ScoreHolderArgument.getNamesWithDefaultWildcard(commandcontext, "targets"), ObjectiveArgument.getWritableObjective(commandcontext, "objective"), IntegerArgumentType.getInteger(commandcontext, "score"));
        })))))).then(Commands.literal("get").then(Commands.argument("target", ScoreHolderArgument.scoreHolder()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS).then(Commands.argument("objective", ObjectiveArgument.objective()).executes((commandcontext) -> {
            return getScore((CommandSourceStack) commandcontext.getSource(), ScoreHolderArgument.getName(commandcontext, "target"), ObjectiveArgument.getObjective(commandcontext, "objective"));
        }))))).then(Commands.literal("add").then(Commands.argument("targets", ScoreHolderArgument.scoreHolders()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS).then(Commands.argument("objective", ObjectiveArgument.objective()).then(Commands.argument("score", IntegerArgumentType.integer(0)).executes((commandcontext) -> {
            return addScore((CommandSourceStack) commandcontext.getSource(), ScoreHolderArgument.getNamesWithDefaultWildcard(commandcontext, "targets"), ObjectiveArgument.getWritableObjective(commandcontext, "objective"), IntegerArgumentType.getInteger(commandcontext, "score"));
        })))))).then(Commands.literal("remove").then(Commands.argument("targets", ScoreHolderArgument.scoreHolders()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS).then(Commands.argument("objective", ObjectiveArgument.objective()).then(Commands.argument("score", IntegerArgumentType.integer(0)).executes((commandcontext) -> {
            return removeScore((CommandSourceStack) commandcontext.getSource(), ScoreHolderArgument.getNamesWithDefaultWildcard(commandcontext, "targets"), ObjectiveArgument.getWritableObjective(commandcontext, "objective"), IntegerArgumentType.getInteger(commandcontext, "score"));
        })))))).then(Commands.literal("reset").then(((RequiredArgumentBuilder) Commands.argument("targets", ScoreHolderArgument.scoreHolders()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS).executes((commandcontext) -> {
            return resetScores((CommandSourceStack) commandcontext.getSource(), ScoreHolderArgument.getNamesWithDefaultWildcard(commandcontext, "targets"));
        })).then(Commands.argument("objective", ObjectiveArgument.objective()).executes((commandcontext) -> {
            return resetScore((CommandSourceStack) commandcontext.getSource(), ScoreHolderArgument.getNamesWithDefaultWildcard(commandcontext, "targets"), ObjectiveArgument.getObjective(commandcontext, "objective"));
        }))))).then(Commands.literal("enable").then(Commands.argument("targets", ScoreHolderArgument.scoreHolders()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS).then(Commands.argument("objective", ObjectiveArgument.objective()).suggests((commandcontext, suggestionsbuilder) -> {
            return suggestTriggers((CommandSourceStack) commandcontext.getSource(), ScoreHolderArgument.getNamesWithDefaultWildcard(commandcontext, "targets"), suggestionsbuilder);
        }).executes((commandcontext) -> {
            return enableTrigger((CommandSourceStack) commandcontext.getSource(), ScoreHolderArgument.getNamesWithDefaultWildcard(commandcontext, "targets"), ObjectiveArgument.getObjective(commandcontext, "objective"));
        }))))).then(((LiteralArgumentBuilder) Commands.literal("display").then(Commands.literal("name").then(Commands.argument("targets", ScoreHolderArgument.scoreHolders()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS).then(((RequiredArgumentBuilder) Commands.argument("objective", ObjectiveArgument.objective()).then(Commands.argument("name", ComponentArgument.textComponent(context)).executes((commandcontext) -> {
            return setScoreDisplay((CommandSourceStack) commandcontext.getSource(), ScoreHolderArgument.getNamesWithDefaultWildcard(commandcontext, "targets"), ObjectiveArgument.getObjective(commandcontext, "objective"), ComponentArgument.getResolvedComponent(commandcontext, "name"));
        }))).executes((commandcontext) -> {
            return setScoreDisplay((CommandSourceStack) commandcontext.getSource(), ScoreHolderArgument.getNamesWithDefaultWildcard(commandcontext, "targets"), ObjectiveArgument.getObjective(commandcontext, "objective"), (Component) null);
        }))))).then(Commands.literal("numberformat").then(Commands.argument("targets", ScoreHolderArgument.scoreHolders()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS).then(addNumberFormats(context, Commands.argument("objective", ObjectiveArgument.objective()), (commandcontext, numberformat) -> {
            return setScoreNumberFormat((CommandSourceStack) commandcontext.getSource(), ScoreHolderArgument.getNamesWithDefaultWildcard(commandcontext, "targets"), ObjectiveArgument.getObjective(commandcontext, "objective"), numberformat);
        })))))).then(Commands.literal("operation").then(Commands.argument("targets", ScoreHolderArgument.scoreHolders()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS).then(Commands.argument("targetObjective", ObjectiveArgument.objective()).then(Commands.argument("operation", OperationArgument.operation()).then(Commands.argument("source", ScoreHolderArgument.scoreHolders()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS).then(Commands.argument("sourceObjective", ObjectiveArgument.objective()).executes((commandcontext) -> {
            return performOperation((CommandSourceStack) commandcontext.getSource(), ScoreHolderArgument.getNamesWithDefaultWildcard(commandcontext, "targets"), ObjectiveArgument.getWritableObjective(commandcontext, "targetObjective"), OperationArgument.getOperation(commandcontext, "operation"), ScoreHolderArgument.getNamesWithDefaultWildcard(commandcontext, "source"), ObjectiveArgument.getObjective(commandcontext, "sourceObjective"));
        })))))))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> addNumberFormats(CommandBuildContext context, ArgumentBuilder<CommandSourceStack, ?> top, ScoreboardCommand.NumberFormatCommandExecutor callback) {
        return top.then(Commands.literal("blank").executes((commandcontext) -> {
            return callback.run(commandcontext, BlankFormat.INSTANCE);
        })).then(Commands.literal("fixed").then(Commands.argument("contents", ComponentArgument.textComponent(context)).executes((commandcontext) -> {
            Component component = ComponentArgument.getResolvedComponent(commandcontext, "contents");

            return callback.run(commandcontext, new FixedFormat(component));
        }))).then(Commands.literal("styled").then(Commands.argument("style", StyleArgument.style(context)).executes((commandcontext) -> {
            Style style = StyleArgument.getStyle(commandcontext, "style");

            return callback.run(commandcontext, new StyledFormat(style));
        }))).executes((commandcontext) -> {
            return callback.run(commandcontext, (NumberFormat) null);
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createRenderTypeModify() {
        LiteralArgumentBuilder<CommandSourceStack> literalargumentbuilder = Commands.literal("rendertype");

        for (ObjectiveCriteria.RenderType objectivecriteria_rendertype : ObjectiveCriteria.RenderType.values()) {
            literalargumentbuilder.then(Commands.literal(objectivecriteria_rendertype.getId()).executes((commandcontext) -> {
                return setRenderType((CommandSourceStack) commandcontext.getSource(), ObjectiveArgument.getObjective(commandcontext, "objective"), objectivecriteria_rendertype);
            }));
        }

        return literalargumentbuilder;
    }

    private static CompletableFuture<Suggestions> suggestTriggers(CommandSourceStack source, Collection<ScoreHolder> targets, SuggestionsBuilder builder) {
        List<String> list = Lists.newArrayList();
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (Objective objective : scoreboard.getObjectives()) {
            if (objective.getCriteria() == ObjectiveCriteria.TRIGGER) {
                boolean flag = false;

                for (ScoreHolder scoreholder : targets) {
                    ReadOnlyScoreInfo readonlyscoreinfo = scoreboard.getPlayerScoreInfo(scoreholder, objective);

                    if (readonlyscoreinfo == null || readonlyscoreinfo.isLocked()) {
                        flag = true;
                        break;
                    }
                }

                if (flag) {
                    list.add(objective.getName());
                }
            }
        }

        return SharedSuggestionProvider.suggest(list, builder);
    }

    private static int getScore(CommandSourceStack source, ScoreHolder target, Objective objective) throws CommandSyntaxException {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        ReadOnlyScoreInfo readonlyscoreinfo = scoreboard.getPlayerScoreInfo(target, objective);

        if (readonlyscoreinfo == null) {
            throw ScoreboardCommand.ERROR_NO_VALUE.create(objective.getName(), target.getFeedbackDisplayName());
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.players.get.success", target.getFeedbackDisplayName(), readonlyscoreinfo.value(), objective.getFormattedDisplayName());
            }, false);
            return readonlyscoreinfo.value();
        }
    }

    private static Component getFirstTargetName(Collection<ScoreHolder> names) {
        return ((ScoreHolder) names.iterator().next()).getFeedbackDisplayName();
    }

    private static int performOperation(CommandSourceStack source, Collection<ScoreHolder> targets, Objective targetObjective, OperationArgument.Operation operation, Collection<ScoreHolder> sources, Objective sourceObjective) throws CommandSyntaxException {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        int i = 0;

        for (ScoreHolder scoreholder : targets) {
            ScoreAccess scoreaccess = scoreboard.getOrCreatePlayerScore(scoreholder, targetObjective);

            for (ScoreHolder scoreholder1 : sources) {
                ScoreAccess scoreaccess1 = scoreboard.getOrCreatePlayerScore(scoreholder1, sourceObjective);

                operation.apply(scoreaccess, scoreaccess1);
            }

            i += scoreaccess.get();
        }

        if (targets.size() == 1) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.players.operation.success.single", targetObjective.getFormattedDisplayName(), getFirstTargetName(targets), i);
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.players.operation.success.multiple", targetObjective.getFormattedDisplayName(), targets.size());
            }, true);
        }

        return i;
    }

    private static int enableTrigger(CommandSourceStack source, Collection<ScoreHolder> names, Objective objective) throws CommandSyntaxException {
        if (objective.getCriteria() != ObjectiveCriteria.TRIGGER) {
            throw ScoreboardCommand.ERROR_NOT_TRIGGER.create();
        } else {
            Scoreboard scoreboard = source.getServer().getScoreboard();
            int i = 0;

            for (ScoreHolder scoreholder : names) {
                ScoreAccess scoreaccess = scoreboard.getOrCreatePlayerScore(scoreholder, objective);

                if (scoreaccess.locked()) {
                    scoreaccess.unlock();
                    ++i;
                }
            }

            if (i == 0) {
                throw ScoreboardCommand.ERROR_TRIGGER_ALREADY_ENABLED.create();
            } else {
                if (names.size() == 1) {
                    source.sendSuccess(() -> {
                        return Component.translatable("commands.scoreboard.players.enable.success.single", objective.getFormattedDisplayName(), getFirstTargetName(names));
                    }, true);
                } else {
                    source.sendSuccess(() -> {
                        return Component.translatable("commands.scoreboard.players.enable.success.multiple", objective.getFormattedDisplayName(), names.size());
                    }, true);
                }

                return i;
            }
        }
    }

    private static int resetScores(CommandSourceStack source, Collection<ScoreHolder> names) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (ScoreHolder scoreholder : names) {
            scoreboard.resetAllPlayerScores(scoreholder);
        }

        if (names.size() == 1) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.players.reset.all.single", getFirstTargetName(names));
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.players.reset.all.multiple", names.size());
            }, true);
        }

        return names.size();
    }

    private static int resetScore(CommandSourceStack source, Collection<ScoreHolder> names, Objective objective) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (ScoreHolder scoreholder : names) {
            scoreboard.resetSinglePlayerScore(scoreholder, objective);
        }

        if (names.size() == 1) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.players.reset.specific.single", objective.getFormattedDisplayName(), getFirstTargetName(names));
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.players.reset.specific.multiple", objective.getFormattedDisplayName(), names.size());
            }, true);
        }

        return names.size();
    }

    private static int setScore(CommandSourceStack source, Collection<ScoreHolder> names, Objective objective, int value) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (ScoreHolder scoreholder : names) {
            scoreboard.getOrCreatePlayerScore(scoreholder, objective).set(value);
        }

        if (names.size() == 1) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.players.set.success.single", objective.getFormattedDisplayName(), getFirstTargetName(names), value);
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.players.set.success.multiple", objective.getFormattedDisplayName(), names.size(), value);
            }, true);
        }

        return value * names.size();
    }

    private static int setScoreDisplay(CommandSourceStack source, Collection<ScoreHolder> names, Objective objective, @Nullable Component display) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (ScoreHolder scoreholder : names) {
            scoreboard.getOrCreatePlayerScore(scoreholder, objective).display(display);
        }

        if (display == null) {
            if (names.size() == 1) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.scoreboard.players.display.name.clear.success.single", getFirstTargetName(names), objective.getFormattedDisplayName());
                }, true);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.scoreboard.players.display.name.clear.success.multiple", names.size(), objective.getFormattedDisplayName());
                }, true);
            }
        } else if (names.size() == 1) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.players.display.name.set.success.single", display, getFirstTargetName(names), objective.getFormattedDisplayName());
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.players.display.name.set.success.multiple", display, names.size(), objective.getFormattedDisplayName());
            }, true);
        }

        return names.size();
    }

    private static int setScoreNumberFormat(CommandSourceStack source, Collection<ScoreHolder> names, Objective objective, @Nullable NumberFormat numberFormat) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (ScoreHolder scoreholder : names) {
            scoreboard.getOrCreatePlayerScore(scoreholder, objective).numberFormatOverride(numberFormat);
        }

        if (numberFormat == null) {
            if (names.size() == 1) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.scoreboard.players.display.numberFormat.clear.success.single", getFirstTargetName(names), objective.getFormattedDisplayName());
                }, true);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.scoreboard.players.display.numberFormat.clear.success.multiple", names.size(), objective.getFormattedDisplayName());
                }, true);
            }
        } else if (names.size() == 1) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.players.display.numberFormat.set.success.single", getFirstTargetName(names), objective.getFormattedDisplayName());
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.players.display.numberFormat.set.success.multiple", names.size(), objective.getFormattedDisplayName());
            }, true);
        }

        return names.size();
    }

    private static int addScore(CommandSourceStack source, Collection<ScoreHolder> names, Objective objective, int value) {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        int j = 0;

        for (ScoreHolder scoreholder : names) {
            ScoreAccess scoreaccess = scoreboard.getOrCreatePlayerScore(scoreholder, objective);

            scoreaccess.set(scoreaccess.get() + value);
            j += scoreaccess.get();
        }

        if (names.size() == 1) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.players.add.success.single", value, objective.getFormattedDisplayName(), getFirstTargetName(names), j);
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.players.add.success.multiple", value, objective.getFormattedDisplayName(), names.size());
            }, true);
        }

        return j;
    }

    private static int removeScore(CommandSourceStack source, Collection<ScoreHolder> names, Objective objective, int value) {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        int j = 0;

        for (ScoreHolder scoreholder : names) {
            ScoreAccess scoreaccess = scoreboard.getOrCreatePlayerScore(scoreholder, objective);

            scoreaccess.set(scoreaccess.get() - value);
            j += scoreaccess.get();
        }

        if (names.size() == 1) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.players.remove.success.single", value, objective.getFormattedDisplayName(), getFirstTargetName(names), j);
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.players.remove.success.multiple", value, objective.getFormattedDisplayName(), names.size());
            }, true);
        }

        return j;
    }

    private static int listTrackedPlayers(CommandSourceStack source) {
        Collection<ScoreHolder> collection = source.getServer().getScoreboard().getTrackedPlayers();

        if (collection.isEmpty()) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.players.list.empty");
            }, false);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.players.list.success", collection.size(), ComponentUtils.formatList(collection, ScoreHolder::getFeedbackDisplayName));
            }, false);
        }

        return collection.size();
    }

    private static int listTrackedPlayerScores(CommandSourceStack source, ScoreHolder entity) {
        Object2IntMap<Objective> object2intmap = source.getServer().getScoreboard().listPlayerScores(entity);

        if (object2intmap.isEmpty()) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.players.list.entity.empty", entity.getFeedbackDisplayName());
            }, false);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.players.list.entity.success", entity.getFeedbackDisplayName(), object2intmap.size());
            }, false);
            Object2IntMaps.fastForEach(object2intmap, (entry) -> {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.scoreboard.players.list.entity.entry", ((Objective) entry.getKey()).getFormattedDisplayName(), entry.getIntValue());
                }, false);
            });
        }

        return object2intmap.size();
    }

    private static int clearDisplaySlot(CommandSourceStack source, DisplaySlot slot) throws CommandSyntaxException {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        if (scoreboard.getDisplayObjective(slot) == null) {
            throw ScoreboardCommand.ERROR_DISPLAY_SLOT_ALREADY_EMPTY.create();
        } else {
            scoreboard.setDisplayObjective(slot, (Objective) null);
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.objectives.display.cleared", slot.getSerializedName());
            }, true);
            return 0;
        }
    }

    private static int setDisplaySlot(CommandSourceStack source, DisplaySlot slot, Objective objective) throws CommandSyntaxException {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        if (scoreboard.getDisplayObjective(slot) == objective) {
            throw ScoreboardCommand.ERROR_DISPLAY_SLOT_ALREADY_SET.create();
        } else {
            scoreboard.setDisplayObjective(slot, objective);
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.objectives.display.set", slot.getSerializedName(), objective.getDisplayName());
            }, true);
            return 0;
        }
    }

    private static int setDisplayName(CommandSourceStack source, Objective objective, Component displayName) {
        if (!objective.getDisplayName().equals(displayName)) {
            objective.setDisplayName(displayName);
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.objectives.modify.displayname", objective.getName(), objective.getFormattedDisplayName());
            }, true);
        }

        return 0;
    }

    private static int setDisplayAutoUpdate(CommandSourceStack source, Objective objective, boolean displayAutoUpdate) {
        if (objective.displayAutoUpdate() != displayAutoUpdate) {
            objective.setDisplayAutoUpdate(displayAutoUpdate);
            if (displayAutoUpdate) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.scoreboard.objectives.modify.displayAutoUpdate.enable", objective.getName(), objective.getFormattedDisplayName());
                }, true);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.scoreboard.objectives.modify.displayAutoUpdate.disable", objective.getName(), objective.getFormattedDisplayName());
                }, true);
            }
        }

        return 0;
    }

    private static int setObjectiveFormat(CommandSourceStack source, Objective objective, @Nullable NumberFormat numberFormat) {
        objective.setNumberFormat(numberFormat);
        if (numberFormat != null) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.objectives.modify.objectiveFormat.set", objective.getName());
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.objectives.modify.objectiveFormat.clear", objective.getName());
            }, true);
        }

        return 0;
    }

    private static int setRenderType(CommandSourceStack source, Objective objective, ObjectiveCriteria.RenderType renderType) {
        if (objective.getRenderType() != renderType) {
            objective.setRenderType(renderType);
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.objectives.modify.rendertype", objective.getFormattedDisplayName());
            }, true);
        }

        return 0;
    }

    private static int removeObjective(CommandSourceStack source, Objective objective) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        scoreboard.removeObjective(objective);
        source.sendSuccess(() -> {
            return Component.translatable("commands.scoreboard.objectives.remove.success", objective.getFormattedDisplayName());
        }, true);
        return scoreboard.getObjectives().size();
    }

    private static int addObjective(CommandSourceStack source, String name, ObjectiveCriteria criteria, Component displayName) throws CommandSyntaxException {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        if (scoreboard.getObjective(name) != null) {
            throw ScoreboardCommand.ERROR_OBJECTIVE_ALREADY_EXISTS.create();
        } else {
            scoreboard.addObjective(name, criteria, displayName, criteria.getDefaultRenderType(), false, (NumberFormat) null);
            Objective objective = scoreboard.getObjective(name);

            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.objectives.add.success", objective.getFormattedDisplayName());
            }, true);
            return scoreboard.getObjectives().size();
        }
    }

    private static int listObjectives(CommandSourceStack source) {
        Collection<Objective> collection = source.getServer().getScoreboard().getObjectives();

        if (collection.isEmpty()) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.objectives.list.empty");
            }, false);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.scoreboard.objectives.list.success", collection.size(), ComponentUtils.formatList(collection, Objective::getFormattedDisplayName));
            }, false);
        }

        return collection.size();
    }

    @FunctionalInterface
    public interface NumberFormatCommandExecutor {

        int run(CommandContext<CommandSourceStack> context, @Nullable NumberFormat format) throws CommandSyntaxException;
    }
}
