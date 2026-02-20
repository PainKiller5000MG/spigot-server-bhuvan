package net.minecraft.server.commands;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

public class EffectCommands {

    private static final SimpleCommandExceptionType ERROR_GIVE_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.effect.give.failed"));
    private static final SimpleCommandExceptionType ERROR_CLEAR_EVERYTHING_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.effect.clear.everything.failed"));
    private static final SimpleCommandExceptionType ERROR_CLEAR_SPECIFIC_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.effect.clear.specific.failed"));

    public EffectCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("effect").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(((LiteralArgumentBuilder) Commands.literal("clear").executes((commandcontext) -> {
            return clearEffects((CommandSourceStack) commandcontext.getSource(), ImmutableList.of(((CommandSourceStack) commandcontext.getSource()).getEntityOrException()));
        })).then(((RequiredArgumentBuilder) Commands.argument("targets", EntityArgument.entities()).executes((commandcontext) -> {
            return clearEffects((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"));
        })).then(Commands.argument("effect", ResourceArgument.resource(context, Registries.MOB_EFFECT)).executes((commandcontext) -> {
            return clearEffect((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ResourceArgument.getMobEffect(commandcontext, "effect"));
        }))))).then(Commands.literal("give").then(Commands.argument("targets", EntityArgument.entities()).then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument("effect", ResourceArgument.resource(context, Registries.MOB_EFFECT)).executes((commandcontext) -> {
            return giveEffect((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ResourceArgument.getMobEffect(commandcontext, "effect"), (Integer) null, 0, true);
        })).then(((RequiredArgumentBuilder) Commands.argument("seconds", IntegerArgumentType.integer(1, 1000000)).executes((commandcontext) -> {
            return giveEffect((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ResourceArgument.getMobEffect(commandcontext, "effect"), IntegerArgumentType.getInteger(commandcontext, "seconds"), 0, true);
        })).then(((RequiredArgumentBuilder) Commands.argument("amplifier", IntegerArgumentType.integer(0, 255)).executes((commandcontext) -> {
            return giveEffect((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ResourceArgument.getMobEffect(commandcontext, "effect"), IntegerArgumentType.getInteger(commandcontext, "seconds"), IntegerArgumentType.getInteger(commandcontext, "amplifier"), true);
        })).then(Commands.argument("hideParticles", BoolArgumentType.bool()).executes((commandcontext) -> {
            return giveEffect((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ResourceArgument.getMobEffect(commandcontext, "effect"), IntegerArgumentType.getInteger(commandcontext, "seconds"), IntegerArgumentType.getInteger(commandcontext, "amplifier"), !BoolArgumentType.getBool(commandcontext, "hideParticles"));
        }))))).then(((LiteralArgumentBuilder) Commands.literal("infinite").executes((commandcontext) -> {
            return giveEffect((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ResourceArgument.getMobEffect(commandcontext, "effect"), -1, 0, true);
        })).then(((RequiredArgumentBuilder) Commands.argument("amplifier", IntegerArgumentType.integer(0, 255)).executes((commandcontext) -> {
            return giveEffect((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ResourceArgument.getMobEffect(commandcontext, "effect"), -1, IntegerArgumentType.getInteger(commandcontext, "amplifier"), true);
        })).then(Commands.argument("hideParticles", BoolArgumentType.bool()).executes((commandcontext) -> {
            return giveEffect((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ResourceArgument.getMobEffect(commandcontext, "effect"), -1, IntegerArgumentType.getInteger(commandcontext, "amplifier"), !BoolArgumentType.getBool(commandcontext, "hideParticles"));
        }))))))));
    }

    private static int giveEffect(CommandSourceStack source, Collection<? extends Entity> entities, Holder<MobEffect> effectHolder, @Nullable Integer seconds, int amplifier, boolean particles) throws CommandSyntaxException {
        MobEffect mobeffect = effectHolder.value();
        int j = 0;
        int k;

        if (seconds != null) {
            if (mobeffect.isInstantenous()) {
                k = seconds;
            } else if (seconds == -1) {
                k = -1;
            } else {
                k = seconds * 20;
            }
        } else if (mobeffect.isInstantenous()) {
            k = 1;
        } else {
            k = 600;
        }

        for (Entity entity : entities) {
            if (entity instanceof LivingEntity) {
                MobEffectInstance mobeffectinstance = new MobEffectInstance(effectHolder, k, amplifier, false, particles);

                if (((LivingEntity) entity).addEffect(mobeffectinstance, source.getEntity())) {
                    ++j;
                }
            }
        }

        if (j == 0) {
            throw EffectCommands.ERROR_GIVE_FAILED.create();
        } else {
            if (entities.size() == 1) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.effect.give.success.single", mobeffect.getDisplayName(), ((Entity) entities.iterator().next()).getDisplayName(), k / 20);
                }, true);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.effect.give.success.multiple", mobeffect.getDisplayName(), entities.size(), k / 20);
                }, true);
            }

            return j;
        }
    }

    private static int clearEffects(CommandSourceStack source, Collection<? extends Entity> entities) throws CommandSyntaxException {
        int i = 0;

        for (Entity entity : entities) {
            if (entity instanceof LivingEntity && ((LivingEntity) entity).removeAllEffects()) {
                ++i;
            }
        }

        if (i == 0) {
            throw EffectCommands.ERROR_CLEAR_EVERYTHING_FAILED.create();
        } else {
            if (entities.size() == 1) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.effect.clear.everything.success.single", ((Entity) entities.iterator().next()).getDisplayName());
                }, true);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.effect.clear.everything.success.multiple", entities.size());
                }, true);
            }

            return i;
        }
    }

    private static int clearEffect(CommandSourceStack source, Collection<? extends Entity> entities, Holder<MobEffect> effectHolder) throws CommandSyntaxException {
        MobEffect mobeffect = effectHolder.value();
        int i = 0;

        for (Entity entity : entities) {
            if (entity instanceof LivingEntity && ((LivingEntity) entity).removeEffect(effectHolder)) {
                ++i;
            }
        }

        if (i == 0) {
            throw EffectCommands.ERROR_CLEAR_SPECIFIC_FAILED.create();
        } else {
            if (entities.size() == 1) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.effect.clear.specific.success.single", mobeffect.getDisplayName(), ((Entity) entities.iterator().next()).getDisplayName());
                }, true);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.effect.clear.specific.success.multiple", mobeffect.getDisplayName(), entities.size());
                }, true);
            }

            return i;
        }
    }
}
