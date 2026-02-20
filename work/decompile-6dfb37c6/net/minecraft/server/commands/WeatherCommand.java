package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.IntProvider;

public class WeatherCommand {

    private static final int DEFAULT_TIME = -1;

    public WeatherCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("weather").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(((LiteralArgumentBuilder) Commands.literal("clear").executes((commandcontext) -> {
            return setClear((CommandSourceStack) commandcontext.getSource(), -1);
        })).then(Commands.argument("duration", TimeArgument.time(1)).executes((commandcontext) -> {
            return setClear((CommandSourceStack) commandcontext.getSource(), IntegerArgumentType.getInteger(commandcontext, "duration"));
        })))).then(((LiteralArgumentBuilder) Commands.literal("rain").executes((commandcontext) -> {
            return setRain((CommandSourceStack) commandcontext.getSource(), -1);
        })).then(Commands.argument("duration", TimeArgument.time(1)).executes((commandcontext) -> {
            return setRain((CommandSourceStack) commandcontext.getSource(), IntegerArgumentType.getInteger(commandcontext, "duration"));
        })))).then(((LiteralArgumentBuilder) Commands.literal("thunder").executes((commandcontext) -> {
            return setThunder((CommandSourceStack) commandcontext.getSource(), -1);
        })).then(Commands.argument("duration", TimeArgument.time(1)).executes((commandcontext) -> {
            return setThunder((CommandSourceStack) commandcontext.getSource(), IntegerArgumentType.getInteger(commandcontext, "duration"));
        }))));
    }

    private static int getDuration(CommandSourceStack source, int input, IntProvider defaultDistribution) {
        return input == -1 ? defaultDistribution.sample(source.getServer().overworld().getRandom()) : input;
    }

    private static int setClear(CommandSourceStack source, int duration) {
        source.getServer().overworld().setWeatherParameters(getDuration(source, duration, ServerLevel.RAIN_DELAY), 0, false, false);
        source.sendSuccess(() -> {
            return Component.translatable("commands.weather.set.clear");
        }, true);
        return duration;
    }

    private static int setRain(CommandSourceStack source, int duration) {
        source.getServer().overworld().setWeatherParameters(0, getDuration(source, duration, ServerLevel.RAIN_DURATION), true, false);
        source.sendSuccess(() -> {
            return Component.translatable("commands.weather.set.rain");
        }, true);
        return duration;
    }

    private static int setThunder(CommandSourceStack source, int duration) {
        source.getServer().overworld().setWeatherParameters(0, getDuration(source, duration, ServerLevel.THUNDER_DURATION), true, true);
        source.sendSuccess(() -> {
            return Component.translatable("commands.weather.set.thunder");
        }, true);
        return duration;
    }
}
