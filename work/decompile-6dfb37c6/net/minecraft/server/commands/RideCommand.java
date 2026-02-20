package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

public class RideCommand {

    private static final DynamicCommandExceptionType ERROR_NOT_RIDING = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.ride.not_riding", object);
    });
    private static final Dynamic2CommandExceptionType ERROR_ALREADY_RIDING = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("commands.ride.already_riding", object, object1);
    });
    private static final Dynamic2CommandExceptionType ERROR_MOUNT_FAILED = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("commands.ride.mount.failure.generic", object, object1);
    });
    private static final SimpleCommandExceptionType ERROR_MOUNTING_PLAYER = new SimpleCommandExceptionType(Component.translatable("commands.ride.mount.failure.cant_ride_players"));
    private static final SimpleCommandExceptionType ERROR_MOUNTING_LOOP = new SimpleCommandExceptionType(Component.translatable("commands.ride.mount.failure.loop"));
    private static final SimpleCommandExceptionType ERROR_WRONG_DIMENSION = new SimpleCommandExceptionType(Component.translatable("commands.ride.mount.failure.wrong_dimension"));

    public RideCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("ride").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(((RequiredArgumentBuilder) Commands.argument("target", EntityArgument.entity()).then(Commands.literal("mount").then(Commands.argument("vehicle", EntityArgument.entity()).executes((commandcontext) -> {
            return mount((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntity(commandcontext, "target"), EntityArgument.getEntity(commandcontext, "vehicle"));
        })))).then(Commands.literal("dismount").executes((commandcontext) -> {
            return dismount((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntity(commandcontext, "target"));
        }))));
    }

    private static int mount(CommandSourceStack source, Entity target, Entity vehicle) throws CommandSyntaxException {
        Entity entity2 = target.getVehicle();

        if (entity2 != null) {
            throw RideCommand.ERROR_ALREADY_RIDING.create(target.getDisplayName(), entity2.getDisplayName());
        } else if (vehicle.getType() == EntityType.PLAYER) {
            throw RideCommand.ERROR_MOUNTING_PLAYER.create();
        } else if (target.getSelfAndPassengers().anyMatch((entity3) -> {
            return entity3 == vehicle;
        })) {
            throw RideCommand.ERROR_MOUNTING_LOOP.create();
        } else if (target.level() != vehicle.level()) {
            throw RideCommand.ERROR_WRONG_DIMENSION.create();
        } else if (!target.startRiding(vehicle, true, true)) {
            throw RideCommand.ERROR_MOUNT_FAILED.create(target.getDisplayName(), vehicle.getDisplayName());
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.ride.mount.success", target.getDisplayName(), vehicle.getDisplayName());
            }, true);
            return 1;
        }
    }

    private static int dismount(CommandSourceStack source, Entity target) throws CommandSyntaxException {
        Entity entity1 = target.getVehicle();

        if (entity1 == null) {
            throw RideCommand.ERROR_NOT_RIDING.create(target.getDisplayName());
        } else {
            target.stopRiding();
            source.sendSuccess(() -> {
                return Component.translatable("commands.ride.dismount.success", target.getDisplayName(), entity1.getDisplayName());
            }, true);
            return 1;
        }
    }
}
