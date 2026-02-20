package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public class EnchantCommand {

    private static final DynamicCommandExceptionType ERROR_NOT_LIVING_ENTITY = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.enchant.failed.entity", object);
    });
    private static final DynamicCommandExceptionType ERROR_NO_ITEM = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.enchant.failed.itemless", object);
    });
    private static final DynamicCommandExceptionType ERROR_INCOMPATIBLE = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.enchant.failed.incompatible", object);
    });
    private static final Dynamic2CommandExceptionType ERROR_LEVEL_TOO_HIGH = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("commands.enchant.failed.level", object, object1);
    });
    private static final SimpleCommandExceptionType ERROR_NOTHING_HAPPENED = new SimpleCommandExceptionType(Component.translatable("commands.enchant.failed"));

    public EnchantCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("enchant").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.argument("targets", EntityArgument.entities()).then(((RequiredArgumentBuilder) Commands.argument("enchantment", ResourceArgument.resource(context, Registries.ENCHANTMENT)).executes((commandcontext) -> {
            return enchant((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ResourceArgument.getEnchantment(commandcontext, "enchantment"), 1);
        })).then(Commands.argument("level", IntegerArgumentType.integer(0)).executes((commandcontext) -> {
            return enchant((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ResourceArgument.getEnchantment(commandcontext, "enchantment"), IntegerArgumentType.getInteger(commandcontext, "level"));
        })))));
    }

    private static int enchant(CommandSourceStack source, Collection<? extends Entity> targets, Holder<Enchantment> enchantmentHolder, int level) throws CommandSyntaxException {
        Enchantment enchantment = enchantmentHolder.value();

        if (level > enchantment.getMaxLevel()) {
            throw EnchantCommand.ERROR_LEVEL_TOO_HIGH.create(level, enchantment.getMaxLevel());
        } else {
            int j = 0;

            for (Entity entity : targets) {
                if (entity instanceof LivingEntity) {
                    LivingEntity livingentity = (LivingEntity) entity;
                    ItemStack itemstack = livingentity.getMainHandItem();

                    if (!itemstack.isEmpty()) {
                        if (enchantment.canEnchant(itemstack) && EnchantmentHelper.isEnchantmentCompatible(EnchantmentHelper.getEnchantmentsForCrafting(itemstack).keySet(), enchantmentHolder)) {
                            itemstack.enchant(enchantmentHolder, level);
                            ++j;
                        } else if (targets.size() == 1) {
                            throw EnchantCommand.ERROR_INCOMPATIBLE.create(itemstack.getHoverName().getString());
                        }
                    } else if (targets.size() == 1) {
                        throw EnchantCommand.ERROR_NO_ITEM.create(livingentity.getName().getString());
                    }
                } else if (targets.size() == 1) {
                    throw EnchantCommand.ERROR_NOT_LIVING_ENTITY.create(entity.getName().getString());
                }
            }

            if (j == 0) {
                throw EnchantCommand.ERROR_NOTHING_HAPPENED.create();
            } else {
                if (targets.size() == 1) {
                    source.sendSuccess(() -> {
                        return Component.translatable("commands.enchant.success.single", Enchantment.getFullname(enchantmentHolder, level), ((Entity) targets.iterator().next()).getDisplayName());
                    }, true);
                } else {
                    source.sendSuccess(() -> {
                        return Component.translatable("commands.enchant.success.multiple", Enchantment.getFullname(enchantmentHolder, level), targets.size());
                    }, true);
                }

                return j;
            }
        }
    }
}
