package net.minecraft.commands.arguments;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.waypoints.WaypointTransmitter;

public class WaypointArgument {

    public static final SimpleCommandExceptionType ERROR_NOT_A_WAYPOINT = new SimpleCommandExceptionType(Component.translatable("argument.waypoint.invalid"));

    public WaypointArgument() {}

    public static WaypointTransmitter getWaypoint(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        Entity entity = ((EntitySelector) context.getArgument(name, EntitySelector.class)).findSingleEntity((CommandSourceStack) context.getSource());

        if (entity instanceof WaypointTransmitter waypointtransmitter) {
            return waypointtransmitter;
        } else {
            throw WaypointArgument.ERROR_NOT_A_WAYPOINT.create();
        }
    }
}
