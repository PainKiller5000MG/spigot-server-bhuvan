package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ColorArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.HexColorArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.WaypointArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.waypoints.Waypoint;
import net.minecraft.world.waypoints.WaypointStyleAsset;
import net.minecraft.world.waypoints.WaypointStyleAssets;
import net.minecraft.world.waypoints.WaypointTransmitter;

public class WaypointCommand {

    public WaypointCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("waypoint").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.literal("list").executes((commandcontext) -> {
            return listWaypoints((CommandSourceStack) commandcontext.getSource());
        }))).then(Commands.literal("modify").then(((RequiredArgumentBuilder) Commands.argument("waypoint", EntityArgument.entity()).then(((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("color").then(Commands.argument("color", ColorArgument.color()).executes((commandcontext) -> {
            return setWaypointColor((CommandSourceStack) commandcontext.getSource(), WaypointArgument.getWaypoint(commandcontext, "waypoint"), ColorArgument.getColor(commandcontext, "color"));
        }))).then(Commands.literal("hex").then(Commands.argument("color", HexColorArgument.hexColor()).executes((commandcontext) -> {
            return setWaypointColor((CommandSourceStack) commandcontext.getSource(), WaypointArgument.getWaypoint(commandcontext, "waypoint"), HexColorArgument.getHexColor(commandcontext, "color"));
        })))).then(Commands.literal("reset").executes((commandcontext) -> {
            return resetWaypointColor((CommandSourceStack) commandcontext.getSource(), WaypointArgument.getWaypoint(commandcontext, "waypoint"));
        })))).then(((LiteralArgumentBuilder) Commands.literal("style").then(Commands.literal("reset").executes((commandcontext) -> {
            return setWaypointStyle((CommandSourceStack) commandcontext.getSource(), WaypointArgument.getWaypoint(commandcontext, "waypoint"), WaypointStyleAssets.DEFAULT);
        }))).then(Commands.literal("set").then(Commands.argument("style", IdentifierArgument.id()).executes((commandcontext) -> {
            return setWaypointStyle((CommandSourceStack) commandcontext.getSource(), WaypointArgument.getWaypoint(commandcontext, "waypoint"), ResourceKey.create(WaypointStyleAssets.ROOT_ID, IdentifierArgument.getId(commandcontext, "style")));
        })))))));
    }

    private static int setWaypointStyle(CommandSourceStack source, WaypointTransmitter waypoint, ResourceKey<WaypointStyleAsset> style) {
        mutateIcon(source, waypoint, (waypoint_icon) -> {
            waypoint_icon.style = style;
        });
        source.sendSuccess(() -> {
            return Component.translatable("commands.waypoint.modify.style");
        }, false);
        return 0;
    }

    private static int setWaypointColor(CommandSourceStack source, WaypointTransmitter waypoint, ChatFormatting color) {
        mutateIcon(source, waypoint, (waypoint_icon) -> {
            waypoint_icon.color = Optional.of(color.getColor());
        });
        source.sendSuccess(() -> {
            return Component.translatable("commands.waypoint.modify.color", Component.literal(color.getName()).withStyle(color));
        }, false);
        return 0;
    }

    private static int setWaypointColor(CommandSourceStack source, WaypointTransmitter waypoint, Integer color) {
        mutateIcon(source, waypoint, (waypoint_icon) -> {
            waypoint_icon.color = Optional.of(color);
        });
        source.sendSuccess(() -> {
            return Component.translatable("commands.waypoint.modify.color", Component.literal(HexFormat.of().withUpperCase().toHexDigits((long) ARGB.color(0, color), 6)).withColor(color));
        }, false);
        return 0;
    }

    private static int resetWaypointColor(CommandSourceStack source, WaypointTransmitter waypoint) {
        mutateIcon(source, waypoint, (waypoint_icon) -> {
            waypoint_icon.color = Optional.empty();
        });
        source.sendSuccess(() -> {
            return Component.translatable("commands.waypoint.modify.color.reset");
        }, false);
        return 0;
    }

    private static int listWaypoints(CommandSourceStack source) {
        ServerLevel serverlevel = source.getLevel();
        Set<WaypointTransmitter> set = serverlevel.getWaypointManager().transmitters();
        String s = serverlevel.dimension().identifier().toString();

        if (set.isEmpty()) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.waypoint.list.empty", s);
            }, false);
            return 0;
        } else {
            Component component = ComponentUtils.formatList(set.stream().map((waypointtransmitter) -> {
                if (waypointtransmitter instanceof LivingEntity livingentity) {
                    BlockPos blockpos = livingentity.blockPosition();

                    return livingentity.getFeedbackDisplayName().copy().withStyle((style) -> {
                        return style.withClickEvent(new ClickEvent.SuggestCommand("/execute in " + s + " run tp @s " + blockpos.getX() + " " + blockpos.getY() + " " + blockpos.getZ())).withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.coordinates.tooltip"))).withColor((Integer) waypointtransmitter.waypointIcon().color.orElse(-1));
                    });
                } else {
                    return Component.literal(waypointtransmitter.toString());
                }
            }).toList(), Function.identity());

            source.sendSuccess(() -> {
                return Component.translatable("commands.waypoint.list.success", set.size(), s, component);
            }, false);
            return set.size();
        }
    }

    private static void mutateIcon(CommandSourceStack source, WaypointTransmitter waypoint, Consumer<Waypoint.Icon> iconConsumer) {
        ServerLevel serverlevel = source.getLevel();

        serverlevel.getWaypointManager().untrackWaypoint(waypoint);
        iconConsumer.accept(waypoint.waypointIcon());
        serverlevel.getWaypointManager().trackWaypoint(waypoint);
    }
}
