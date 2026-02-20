package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.util.TimeUtil;

public class TickCommand {

    private static final float MAX_TICKRATE = 10000.0F;
    private static final String DEFAULT_TICKRATE = String.valueOf(20);

    public TickCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("tick").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))).then(Commands.literal("query").executes((commandcontext) -> {
            return tickQuery((CommandSourceStack) commandcontext.getSource());
        }))).then(Commands.literal("rate").then(Commands.argument("rate", FloatArgumentType.floatArg(1.0F, 10000.0F)).suggests((commandcontext, suggestionsbuilder) -> {
            return SharedSuggestionProvider.suggest(new String[]{TickCommand.DEFAULT_TICKRATE}, suggestionsbuilder);
        }).executes((commandcontext) -> {
            return setTickingRate((CommandSourceStack) commandcontext.getSource(), FloatArgumentType.getFloat(commandcontext, "rate"));
        })))).then(((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("step").executes((commandcontext) -> {
            return step((CommandSourceStack) commandcontext.getSource(), 1);
        })).then(Commands.literal("stop").executes((commandcontext) -> {
            return stopStepping((CommandSourceStack) commandcontext.getSource());
        }))).then(Commands.argument("time", TimeArgument.time(1)).suggests((commandcontext, suggestionsbuilder) -> {
            return SharedSuggestionProvider.suggest(new String[]{"1t", "1s"}, suggestionsbuilder);
        }).executes((commandcontext) -> {
            return step((CommandSourceStack) commandcontext.getSource(), IntegerArgumentType.getInteger(commandcontext, "time"));
        })))).then(((LiteralArgumentBuilder) Commands.literal("sprint").then(Commands.literal("stop").executes((commandcontext) -> {
            return stopSprinting((CommandSourceStack) commandcontext.getSource());
        }))).then(Commands.argument("time", TimeArgument.time(1)).suggests((commandcontext, suggestionsbuilder) -> {
            return SharedSuggestionProvider.suggest(new String[]{"60s", "1d", "3d"}, suggestionsbuilder);
        }).executes((commandcontext) -> {
            return sprint((CommandSourceStack) commandcontext.getSource(), IntegerArgumentType.getInteger(commandcontext, "time"));
        })))).then(Commands.literal("unfreeze").executes((commandcontext) -> {
            return setFreeze((CommandSourceStack) commandcontext.getSource(), false);
        }))).then(Commands.literal("freeze").executes((commandcontext) -> {
            return setFreeze((CommandSourceStack) commandcontext.getSource(), true);
        })));
    }

    private static String nanosToMilisString(long nanos) {
        return String.format(Locale.ROOT, "%.1f", (float) nanos / (float) TimeUtil.NANOSECONDS_PER_MILLISECOND);
    }

    private static int setTickingRate(CommandSourceStack source, float rate) {
        ServerTickRateManager servertickratemanager = source.getServer().tickRateManager();

        servertickratemanager.setTickRate(rate);
        String s = String.format(Locale.ROOT, "%.1f", rate);

        source.sendSuccess(() -> {
            return Component.translatable("commands.tick.rate.success", s);
        }, true);
        return (int) rate;
    }

    private static int tickQuery(CommandSourceStack source) {
        ServerTickRateManager servertickratemanager = source.getServer().tickRateManager();
        String s = nanosToMilisString(source.getServer().getAverageTickTimeNanos());
        float f = servertickratemanager.tickrate();
        String s1 = String.format(Locale.ROOT, "%.1f", f);

        if (servertickratemanager.isSprinting()) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.tick.status.sprinting");
            }, false);
            source.sendSuccess(() -> {
                return Component.translatable("commands.tick.query.rate.sprinting", s1, s);
            }, false);
        } else {
            if (servertickratemanager.isFrozen()) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.tick.status.frozen");
                }, false);
            } else if (servertickratemanager.nanosecondsPerTick() < source.getServer().getAverageTickTimeNanos()) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.tick.status.lagging");
                }, false);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.tick.status.running");
                }, false);
            }

            String s2 = nanosToMilisString(servertickratemanager.nanosecondsPerTick());

            source.sendSuccess(() -> {
                return Component.translatable("commands.tick.query.rate.running", s1, s, s2);
            }, false);
        }

        long[] along = Arrays.copyOf(source.getServer().getTickTimesNanos(), source.getServer().getTickTimesNanos().length);

        Arrays.sort(along);
        String s3 = nanosToMilisString(along[along.length / 2]);
        String s4 = nanosToMilisString(along[(int) ((double) along.length * 0.95D)]);
        String s5 = nanosToMilisString(along[(int) ((double) along.length * 0.99D)]);

        source.sendSuccess(() -> {
            return Component.translatable("commands.tick.query.percentiles", s3, s4, s5, along.length);
        }, false);
        return (int) f;
    }

    private static int sprint(CommandSourceStack source, int time) {
        boolean flag = source.getServer().tickRateManager().requestGameToSprint(time);

        if (flag) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.tick.sprint.stop.success");
            }, true);
        }

        source.sendSuccess(() -> {
            return Component.translatable("commands.tick.status.sprinting");
        }, true);
        return 1;
    }

    private static int setFreeze(CommandSourceStack source, boolean freeze) {
        ServerTickRateManager servertickratemanager = source.getServer().tickRateManager();

        if (freeze) {
            if (servertickratemanager.isSprinting()) {
                servertickratemanager.stopSprinting();
            }

            if (servertickratemanager.isSteppingForward()) {
                servertickratemanager.stopStepping();
            }
        }

        servertickratemanager.setFrozen(freeze);
        if (freeze) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.tick.status.frozen");
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.tick.status.running");
            }, true);
        }

        return freeze ? 1 : 0;
    }

    private static int step(CommandSourceStack source, int advance) {
        ServerTickRateManager servertickratemanager = source.getServer().tickRateManager();
        boolean flag = servertickratemanager.stepGameIfPaused(advance);

        if (flag) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.tick.step.success", advance);
            }, true);
        } else {
            source.sendFailure(Component.translatable("commands.tick.step.fail"));
        }

        return 1;
    }

    private static int stopStepping(CommandSourceStack source) {
        ServerTickRateManager servertickratemanager = source.getServer().tickRateManager();
        boolean flag = servertickratemanager.stopStepping();

        if (flag) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.tick.step.stop.success");
            }, true);
            return 1;
        } else {
            source.sendFailure(Component.translatable("commands.tick.step.stop.fail"));
            return 0;
        }
    }

    private static int stopSprinting(CommandSourceStack source) {
        ServerTickRateManager servertickratemanager = source.getServer().tickRateManager();
        boolean flag = servertickratemanager.stopSprinting();

        if (flag) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.tick.sprint.stop.success");
            }, true);
            return 1;
        } else {
            source.sendFailure(Component.translatable("commands.tick.sprint.stop.fail"));
            return 0;
        }
    }
}
