package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

public class DamageCommand {

    private static final SimpleCommandExceptionType ERROR_INVULNERABLE = new SimpleCommandExceptionType(Component.translatable("commands.damage.invulnerable"));

    public DamageCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("damage").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.argument("target", EntityArgument.entity()).then(((RequiredArgumentBuilder) Commands.argument("amount", FloatArgumentType.floatArg(0.0F)).executes((commandcontext) -> {
            return damage((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntity(commandcontext, "target"), FloatArgumentType.getFloat(commandcontext, "amount"), ((CommandSourceStack) commandcontext.getSource()).getLevel().damageSources().generic());
        })).then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument("damageType", ResourceArgument.resource(context, Registries.DAMAGE_TYPE)).executes((commandcontext) -> {
            return damage((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntity(commandcontext, "target"), FloatArgumentType.getFloat(commandcontext, "amount"), new DamageSource(ResourceArgument.getResource(commandcontext, "damageType", Registries.DAMAGE_TYPE)));
        })).then(Commands.literal("at").then(Commands.argument("location", Vec3Argument.vec3()).executes((commandcontext) -> {
            return damage((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntity(commandcontext, "target"), FloatArgumentType.getFloat(commandcontext, "amount"), new DamageSource(ResourceArgument.getResource(commandcontext, "damageType", Registries.DAMAGE_TYPE), Vec3Argument.getVec3(commandcontext, "location")));
        })))).then(Commands.literal("by").then(((RequiredArgumentBuilder) Commands.argument("entity", EntityArgument.entity()).executes((commandcontext) -> {
            return damage((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntity(commandcontext, "target"), FloatArgumentType.getFloat(commandcontext, "amount"), new DamageSource(ResourceArgument.getResource(commandcontext, "damageType", Registries.DAMAGE_TYPE), EntityArgument.getEntity(commandcontext, "entity")));
        })).then(Commands.literal("from").then(Commands.argument("cause", EntityArgument.entity()).executes((commandcontext) -> {
            return damage((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntity(commandcontext, "target"), FloatArgumentType.getFloat(commandcontext, "amount"), new DamageSource(ResourceArgument.getResource(commandcontext, "damageType", Registries.DAMAGE_TYPE), EntityArgument.getEntity(commandcontext, "entity"), EntityArgument.getEntity(commandcontext, "cause")));
        })))))))));
    }

    private static int damage(CommandSourceStack stack, Entity target, float amount, DamageSource source) throws CommandSyntaxException {
        if (target.hurtServer(stack.getLevel(), source, amount)) {
            stack.sendSuccess(() -> {
                return Component.translatable("commands.damage.success", amount, target.getDisplayName());
            }, true);
            return 1;
        } else {
            throw DamageCommand.ERROR_INVULNERABLE.create();
        }
    }
}
