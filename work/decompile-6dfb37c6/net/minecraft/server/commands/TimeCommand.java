package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class TimeCommand {

    public TimeCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("time").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("set").then(Commands.literal("day").executes((commandcontext) -> {
            return setTime((CommandSourceStack) commandcontext.getSource(), 1000);
        }))).then(Commands.literal("noon").executes((commandcontext) -> {
            return setTime((CommandSourceStack) commandcontext.getSource(), 6000);
        }))).then(Commands.literal("night").executes((commandcontext) -> {
            return setTime((CommandSourceStack) commandcontext.getSource(), 13000);
        }))).then(Commands.literal("midnight").executes((commandcontext) -> {
            return setTime((CommandSourceStack) commandcontext.getSource(), 18000);
        }))).then(Commands.argument("time", TimeArgument.time()).executes((commandcontext) -> {
            return setTime((CommandSourceStack) commandcontext.getSource(), IntegerArgumentType.getInteger(commandcontext, "time"));
        })))).then(Commands.literal("add").then(Commands.argument("time", TimeArgument.time()).executes((commandcontext) -> {
            return addTime((CommandSourceStack) commandcontext.getSource(), IntegerArgumentType.getInteger(commandcontext, "time"));
        })))).then(((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("query").then(Commands.literal("daytime").executes((commandcontext) -> {
            return queryTime((CommandSourceStack) commandcontext.getSource(), getDayTime(((CommandSourceStack) commandcontext.getSource()).getLevel()));
        }))).then(Commands.literal("gametime").executes((commandcontext) -> {
            return queryTime((CommandSourceStack) commandcontext.getSource(), (int) (((CommandSourceStack) commandcontext.getSource()).getLevel().getGameTime() % 2147483647L));
        }))).then(Commands.literal("day").executes((commandcontext) -> {
            return queryTime((CommandSourceStack) commandcontext.getSource(), (int) (((CommandSourceStack) commandcontext.getSource()).getLevel().getDayCount() % 2147483647L));
        }))));
    }

    private static int getDayTime(ServerLevel level) {
        return (int) (level.getDayTime() % 24000L);
    }

    private static int queryTime(CommandSourceStack source, int time) {
        source.sendSuccess(() -> {
            return Component.translatable("commands.time.query", time);
        }, false);
        return time;
    }

    public static int setTime(CommandSourceStack source, int time) {
        for (ServerLevel serverlevel : source.getServer().getAllLevels()) {
            serverlevel.setDayTime((long) time);
        }

        source.getServer().forceTimeSynchronization();
        source.sendSuccess(() -> {
            return Component.translatable("commands.time.set", time);
        }, true);
        return getDayTime(source.getLevel());
    }

    public static int addTime(CommandSourceStack source, int time) {
        for (ServerLevel serverlevel : source.getServer().getAllLevels()) {
            serverlevel.setDayTime(serverlevel.getDayTime() + (long) time);
        }

        source.getServer().forceTimeSynchronization();
        int j = getDayTime(source.getLevel());

        source.sendSuccess(() -> {
            return Component.translatable("commands.time.set", j);
        }, true);
        return j;
    }
}
