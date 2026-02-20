package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.commands.arguments.coordinates.Vec2Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec2;

public class WorldBorderCommand {

    private static final SimpleCommandExceptionType ERROR_SAME_CENTER = new SimpleCommandExceptionType(Component.translatable("commands.worldborder.center.failed"));
    private static final SimpleCommandExceptionType ERROR_SAME_SIZE = new SimpleCommandExceptionType(Component.translatable("commands.worldborder.set.failed.nochange"));
    private static final SimpleCommandExceptionType ERROR_TOO_SMALL = new SimpleCommandExceptionType(Component.translatable("commands.worldborder.set.failed.small"));
    private static final SimpleCommandExceptionType ERROR_TOO_BIG = new SimpleCommandExceptionType(Component.translatable("commands.worldborder.set.failed.big", (double) 5.999997E7F));
    private static final SimpleCommandExceptionType ERROR_TOO_FAR_OUT = new SimpleCommandExceptionType(Component.translatable("commands.worldborder.set.failed.far", 2.9999984E7D));
    private static final SimpleCommandExceptionType ERROR_SAME_WARNING_TIME = new SimpleCommandExceptionType(Component.translatable("commands.worldborder.warning.time.failed"));
    private static final SimpleCommandExceptionType ERROR_SAME_WARNING_DISTANCE = new SimpleCommandExceptionType(Component.translatable("commands.worldborder.warning.distance.failed"));
    private static final SimpleCommandExceptionType ERROR_SAME_DAMAGE_BUFFER = new SimpleCommandExceptionType(Component.translatable("commands.worldborder.damage.buffer.failed"));
    private static final SimpleCommandExceptionType ERROR_SAME_DAMAGE_AMOUNT = new SimpleCommandExceptionType(Component.translatable("commands.worldborder.damage.amount.failed"));

    public WorldBorderCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("worldborder").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.literal("add").then(((RequiredArgumentBuilder) Commands.argument("distance", DoubleArgumentType.doubleArg((double) -5.999997E7F, (double) 5.999997E7F)).executes((commandcontext) -> {
            return setSize((CommandSourceStack) commandcontext.getSource(), ((CommandSourceStack) commandcontext.getSource()).getLevel().getWorldBorder().getSize() + DoubleArgumentType.getDouble(commandcontext, "distance"), 0L);
        })).then(Commands.argument("time", TimeArgument.time(0)).executes((commandcontext) -> {
            return setSize((CommandSourceStack) commandcontext.getSource(), ((CommandSourceStack) commandcontext.getSource()).getLevel().getWorldBorder().getSize() + DoubleArgumentType.getDouble(commandcontext, "distance"), ((CommandSourceStack) commandcontext.getSource()).getLevel().getWorldBorder().getLerpTime() + (long) IntegerArgumentType.getInteger(commandcontext, "time"));
        }))))).then(Commands.literal("set").then(((RequiredArgumentBuilder) Commands.argument("distance", DoubleArgumentType.doubleArg((double) -5.999997E7F, (double) 5.999997E7F)).executes((commandcontext) -> {
            return setSize((CommandSourceStack) commandcontext.getSource(), DoubleArgumentType.getDouble(commandcontext, "distance"), 0L);
        })).then(Commands.argument("time", TimeArgument.time(0)).executes((commandcontext) -> {
            return setSize((CommandSourceStack) commandcontext.getSource(), DoubleArgumentType.getDouble(commandcontext, "distance"), (long) IntegerArgumentType.getInteger(commandcontext, "time"));
        }))))).then(Commands.literal("center").then(Commands.argument("pos", Vec2Argument.vec2()).executes((commandcontext) -> {
            return setCenter((CommandSourceStack) commandcontext.getSource(), Vec2Argument.getVec2(commandcontext, "pos"));
        })))).then(((LiteralArgumentBuilder) Commands.literal("damage").then(Commands.literal("amount").then(Commands.argument("damagePerBlock", FloatArgumentType.floatArg(0.0F)).executes((commandcontext) -> {
            return setDamageAmount((CommandSourceStack) commandcontext.getSource(), FloatArgumentType.getFloat(commandcontext, "damagePerBlock"));
        })))).then(Commands.literal("buffer").then(Commands.argument("distance", FloatArgumentType.floatArg(0.0F)).executes((commandcontext) -> {
            return setDamageBuffer((CommandSourceStack) commandcontext.getSource(), FloatArgumentType.getFloat(commandcontext, "distance"));
        }))))).then(Commands.literal("get").executes((commandcontext) -> {
            return getSize((CommandSourceStack) commandcontext.getSource());
        }))).then(((LiteralArgumentBuilder) Commands.literal("warning").then(Commands.literal("distance").then(Commands.argument("distance", IntegerArgumentType.integer(0)).executes((commandcontext) -> {
            return setWarningDistance((CommandSourceStack) commandcontext.getSource(), IntegerArgumentType.getInteger(commandcontext, "distance"));
        })))).then(Commands.literal("time").then(Commands.argument("time", TimeArgument.time(0)).executes((commandcontext) -> {
            return setWarningTime((CommandSourceStack) commandcontext.getSource(), IntegerArgumentType.getInteger(commandcontext, "time"));
        })))));
    }

    private static int setDamageBuffer(CommandSourceStack source, float distance) throws CommandSyntaxException {
        WorldBorder worldborder = source.getLevel().getWorldBorder();

        if (worldborder.getSafeZone() == (double) distance) {
            throw WorldBorderCommand.ERROR_SAME_DAMAGE_BUFFER.create();
        } else {
            worldborder.setSafeZone((double) distance);
            source.sendSuccess(() -> {
                return Component.translatable("commands.worldborder.damage.buffer.success", String.format(Locale.ROOT, "%.2f", distance));
            }, true);
            return (int) distance;
        }
    }

    private static int setDamageAmount(CommandSourceStack source, float damagePerBlock) throws CommandSyntaxException {
        WorldBorder worldborder = source.getLevel().getWorldBorder();

        if (worldborder.getDamagePerBlock() == (double) damagePerBlock) {
            throw WorldBorderCommand.ERROR_SAME_DAMAGE_AMOUNT.create();
        } else {
            worldborder.setDamagePerBlock((double) damagePerBlock);
            source.sendSuccess(() -> {
                return Component.translatable("commands.worldborder.damage.amount.success", String.format(Locale.ROOT, "%.2f", damagePerBlock));
            }, true);
            return (int) damagePerBlock;
        }
    }

    private static int setWarningTime(CommandSourceStack source, int ticks) throws CommandSyntaxException {
        WorldBorder worldborder = source.getLevel().getWorldBorder();

        if (worldborder.getWarningTime() == ticks) {
            throw WorldBorderCommand.ERROR_SAME_WARNING_TIME.create();
        } else {
            worldborder.setWarningTime(ticks);
            source.sendSuccess(() -> {
                return Component.translatable("commands.worldborder.warning.time.success", formatTicksToSeconds((long) ticks));
            }, true);
            return ticks;
        }
    }

    private static int setWarningDistance(CommandSourceStack source, int distance) throws CommandSyntaxException {
        WorldBorder worldborder = source.getLevel().getWorldBorder();

        if (worldborder.getWarningBlocks() == distance) {
            throw WorldBorderCommand.ERROR_SAME_WARNING_DISTANCE.create();
        } else {
            worldborder.setWarningBlocks(distance);
            source.sendSuccess(() -> {
                return Component.translatable("commands.worldborder.warning.distance.success", distance);
            }, true);
            return distance;
        }
    }

    private static int getSize(CommandSourceStack source) {
        double d0 = source.getLevel().getWorldBorder().getSize();

        source.sendSuccess(() -> {
            return Component.translatable("commands.worldborder.get", String.format(Locale.ROOT, "%.0f", d0));
        }, false);
        return Mth.floor(d0 + 0.5D);
    }

    private static int setCenter(CommandSourceStack source, Vec2 center) throws CommandSyntaxException {
        WorldBorder worldborder = source.getLevel().getWorldBorder();

        if (worldborder.getCenterX() == (double) center.x && worldborder.getCenterZ() == (double) center.y) {
            throw WorldBorderCommand.ERROR_SAME_CENTER.create();
        } else if ((double) Math.abs(center.x) <= 2.9999984E7D && (double) Math.abs(center.y) <= 2.9999984E7D) {
            worldborder.setCenter((double) center.x, (double) center.y);
            source.sendSuccess(() -> {
                return Component.translatable("commands.worldborder.center.success", String.format(Locale.ROOT, "%.2f", center.x), String.format(Locale.ROOT, "%.2f", center.y));
            }, true);
            return 0;
        } else {
            throw WorldBorderCommand.ERROR_TOO_FAR_OUT.create();
        }
    }

    private static int setSize(CommandSourceStack source, double distance, long ticks) throws CommandSyntaxException {
        ServerLevel serverlevel = source.getLevel();
        WorldBorder worldborder = serverlevel.getWorldBorder();
        double d1 = worldborder.getSize();

        if (d1 == distance) {
            throw WorldBorderCommand.ERROR_SAME_SIZE.create();
        } else if (distance < 1.0D) {
            throw WorldBorderCommand.ERROR_TOO_SMALL.create();
        } else if (distance > (double) 5.999997E7F) {
            throw WorldBorderCommand.ERROR_TOO_BIG.create();
        } else {
            String s = String.format(Locale.ROOT, "%.1f", distance);

            if (ticks > 0L) {
                worldborder.lerpSizeBetween(d1, distance, ticks, serverlevel.getGameTime());
                if (distance > d1) {
                    source.sendSuccess(() -> {
                        return Component.translatable("commands.worldborder.set.grow", s, formatTicksToSeconds(ticks));
                    }, true);
                } else {
                    source.sendSuccess(() -> {
                        return Component.translatable("commands.worldborder.set.shrink", s, formatTicksToSeconds(ticks));
                    }, true);
                }
            } else {
                worldborder.setSize(distance);
                source.sendSuccess(() -> {
                    return Component.translatable("commands.worldborder.set.immediate", s);
                }, true);
            }

            return (int) (distance - d1);
        }
    }

    private static String formatTicksToSeconds(long ticks) {
        return String.format(Locale.ROOT, "%.2f", (double) ticks / 20.0D);
    }
}
