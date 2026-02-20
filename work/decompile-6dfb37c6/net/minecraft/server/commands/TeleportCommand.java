package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class TeleportCommand {

    private static final SimpleCommandExceptionType INVALID_POSITION = new SimpleCommandExceptionType(Component.translatable("commands.teleport.invalidPosition"));

    public TeleportCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> literalcommandnode = dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("teleport").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.argument("location", Vec3Argument.vec3()).executes((commandcontext) -> {
            return teleportToPos((CommandSourceStack) commandcontext.getSource(), Collections.singleton(((CommandSourceStack) commandcontext.getSource()).getEntityOrException()), ((CommandSourceStack) commandcontext.getSource()).getLevel(), Vec3Argument.getCoordinates(commandcontext, "location"), (Coordinates) null, (LookAt) null);
        }))).then(Commands.argument("destination", EntityArgument.entity()).executes((commandcontext) -> {
            return teleportToEntity((CommandSourceStack) commandcontext.getSource(), Collections.singleton(((CommandSourceStack) commandcontext.getSource()).getEntityOrException()), EntityArgument.getEntity(commandcontext, "destination"));
        }))).then(((RequiredArgumentBuilder) Commands.argument("targets", EntityArgument.entities()).then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument("location", Vec3Argument.vec3()).executes((commandcontext) -> {
            return teleportToPos((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ((CommandSourceStack) commandcontext.getSource()).getLevel(), Vec3Argument.getCoordinates(commandcontext, "location"), (Coordinates) null, (LookAt) null);
        })).then(Commands.argument("rotation", RotationArgument.rotation()).executes((commandcontext) -> {
            return teleportToPos((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ((CommandSourceStack) commandcontext.getSource()).getLevel(), Vec3Argument.getCoordinates(commandcontext, "location"), RotationArgument.getRotation(commandcontext, "rotation"), (LookAt) null);
        }))).then(((LiteralArgumentBuilder) Commands.literal("facing").then(Commands.literal("entity").then(((RequiredArgumentBuilder) Commands.argument("facingEntity", EntityArgument.entity()).executes((commandcontext) -> {
            return teleportToPos((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ((CommandSourceStack) commandcontext.getSource()).getLevel(), Vec3Argument.getCoordinates(commandcontext, "location"), (Coordinates) null, new LookAt.LookAtEntity(EntityArgument.getEntity(commandcontext, "facingEntity"), EntityAnchorArgument.Anchor.FEET));
        })).then(Commands.argument("facingAnchor", EntityAnchorArgument.anchor()).executes((commandcontext) -> {
            return teleportToPos((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ((CommandSourceStack) commandcontext.getSource()).getLevel(), Vec3Argument.getCoordinates(commandcontext, "location"), (Coordinates) null, new LookAt.LookAtEntity(EntityArgument.getEntity(commandcontext, "facingEntity"), EntityAnchorArgument.getAnchor(commandcontext, "facingAnchor")));
        }))))).then(Commands.argument("facingLocation", Vec3Argument.vec3()).executes((commandcontext) -> {
            return teleportToPos((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ((CommandSourceStack) commandcontext.getSource()).getLevel(), Vec3Argument.getCoordinates(commandcontext, "location"), (Coordinates) null, new LookAt.LookAtPosition(Vec3Argument.getVec3(commandcontext, "facingLocation")));
        }))))).then(Commands.argument("destination", EntityArgument.entity()).executes((commandcontext) -> {
            return teleportToEntity((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), EntityArgument.getEntity(commandcontext, "destination"));
        }))));

        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("tp").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).redirect(literalcommandnode));
    }

    private static int teleportToEntity(CommandSourceStack source, Collection<? extends Entity> entities, Entity destination) throws CommandSyntaxException {
        for (Entity entity1 : entities) {
            performTeleport(source, entity1, (ServerLevel) destination.level(), destination.getX(), destination.getY(), destination.getZ(), EnumSet.noneOf(Relative.class), destination.getYRot(), destination.getXRot(), (LookAt) null);
        }

        if (entities.size() == 1) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.teleport.success.entity.single", ((Entity) entities.iterator().next()).getDisplayName(), destination.getDisplayName());
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.teleport.success.entity.multiple", entities.size(), destination.getDisplayName());
            }, true);
        }

        return entities.size();
    }

    private static int teleportToPos(CommandSourceStack source, Collection<? extends Entity> entities, ServerLevel level, Coordinates destination, @Nullable Coordinates rotation, @Nullable LookAt lookAt) throws CommandSyntaxException {
        Vec3 vec3 = destination.getPosition(source);
        Vec2 vec2 = rotation == null ? null : rotation.getRotation(source);

        for (Entity entity : entities) {
            Set<Relative> set = getRelatives(destination, rotation, entity.level().dimension() == level.dimension());

            if (vec2 == null) {
                performTeleport(source, entity, level, vec3.x, vec3.y, vec3.z, set, entity.getYRot(), entity.getXRot(), lookAt);
            } else {
                performTeleport(source, entity, level, vec3.x, vec3.y, vec3.z, set, vec2.y, vec2.x, lookAt);
            }
        }

        if (entities.size() == 1) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.teleport.success.location.single", ((Entity) entities.iterator().next()).getDisplayName(), formatDouble(vec3.x), formatDouble(vec3.y), formatDouble(vec3.z));
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.teleport.success.location.multiple", entities.size(), formatDouble(vec3.x), formatDouble(vec3.y), formatDouble(vec3.z));
            }, true);
        }

        return entities.size();
    }

    private static Set<Relative> getRelatives(Coordinates destination, @Nullable Coordinates rotation, boolean sameDimension) {
        Set<Relative> set = Relative.direction(destination.isXRelative(), destination.isYRelative(), destination.isZRelative());
        Set<Relative> set1 = sameDimension ? Relative.position(destination.isXRelative(), destination.isYRelative(), destination.isZRelative()) : Set.of();
        Set<Relative> set2 = rotation == null ? Relative.ROTATION : Relative.rotation(rotation.isYRelative(), rotation.isXRelative());

        return Relative.union(set, set1, set2);
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%f", value);
    }

    private static void performTeleport(CommandSourceStack source, Entity victim, ServerLevel level, double x, double y, double z, Set<Relative> relatives, float yRot, float xRot, @Nullable LookAt lookAt) throws CommandSyntaxException {
        BlockPos blockpos = BlockPos.containing(x, y, z);

        if (!Level.isInSpawnableBounds(blockpos)) {
            throw TeleportCommand.INVALID_POSITION.create();
        } else {
            double d3 = relatives.contains(Relative.X) ? x - victim.getX() : x;
            double d4 = relatives.contains(Relative.Y) ? y - victim.getY() : y;
            double d5 = relatives.contains(Relative.Z) ? z - victim.getZ() : z;
            float f2 = relatives.contains(Relative.Y_ROT) ? yRot - victim.getYRot() : yRot;
            float f3 = relatives.contains(Relative.X_ROT) ? xRot - victim.getXRot() : xRot;
            float f4 = Mth.wrapDegrees(f2);
            float f5 = Mth.wrapDegrees(f3);

            if (victim.teleportTo(level, d3, d4, d5, relatives, f4, f5, true)) {
                if (lookAt != null) {
                    lookAt.perform(source, victim);
                }

                label46:
                {
                    if (victim instanceof LivingEntity) {
                        LivingEntity livingentity = (LivingEntity) victim;

                        if (livingentity.isFallFlying()) {
                            break label46;
                        }
                    }

                    victim.setDeltaMovement(victim.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
                    victim.setOnGround(true);
                }

                if (victim instanceof PathfinderMob) {
                    PathfinderMob pathfindermob = (PathfinderMob) victim;

                    pathfindermob.getNavigation().stop();
                }

            }
        }
    }
}
