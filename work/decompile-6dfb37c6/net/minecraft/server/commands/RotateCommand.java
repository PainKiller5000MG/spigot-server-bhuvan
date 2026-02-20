package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;

public class RotateCommand {

    public RotateCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("rotate").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(((RequiredArgumentBuilder) Commands.argument("target", EntityArgument.entity()).then(Commands.argument("rotation", RotationArgument.rotation()).executes((commandcontext) -> {
            return rotate((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntity(commandcontext, "target"), RotationArgument.getRotation(commandcontext, "rotation"));
        }))).then(((LiteralArgumentBuilder) Commands.literal("facing").then(Commands.literal("entity").then(((RequiredArgumentBuilder) Commands.argument("facingEntity", EntityArgument.entity()).executes((commandcontext) -> {
            return rotate((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntity(commandcontext, "target"), (LookAt) (new LookAt.LookAtEntity(EntityArgument.getEntity(commandcontext, "facingEntity"), EntityAnchorArgument.Anchor.FEET)));
        })).then(Commands.argument("facingAnchor", EntityAnchorArgument.anchor()).executes((commandcontext) -> {
            return rotate((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntity(commandcontext, "target"), (LookAt) (new LookAt.LookAtEntity(EntityArgument.getEntity(commandcontext, "facingEntity"), EntityAnchorArgument.getAnchor(commandcontext, "facingAnchor"))));
        }))))).then(Commands.argument("facingLocation", Vec3Argument.vec3()).executes((commandcontext) -> {
            return rotate((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntity(commandcontext, "target"), (LookAt) (new LookAt.LookAtPosition(Vec3Argument.getVec3(commandcontext, "facingLocation"))));
        })))));
    }

    private static int rotate(CommandSourceStack source, Entity entity, Coordinates rotation) {
        Vec2 vec2 = rotation.getRotation(source);
        float f = rotation.isYRelative() ? vec2.y - entity.getYRot() : vec2.y;
        float f1 = rotation.isXRelative() ? vec2.x - entity.getXRot() : vec2.x;

        entity.forceSetRotation(f, rotation.isYRelative(), f1, rotation.isXRelative());
        source.sendSuccess(() -> {
            return Component.translatable("commands.rotate.success", entity.getDisplayName());
        }, true);
        return 1;
    }

    private static int rotate(CommandSourceStack source, Entity entity, LookAt facing) {
        facing.perform(source, entity);
        source.sendSuccess(() -> {
            return Component.translatable("commands.rotate.success", entity.getDisplayName());
        }, true);
        return 1;
    }
}
