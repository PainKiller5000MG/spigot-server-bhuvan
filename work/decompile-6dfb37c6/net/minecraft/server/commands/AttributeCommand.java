package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.stream.Stream;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public class AttributeCommand {

    private static final DynamicCommandExceptionType ERROR_NOT_LIVING_ENTITY = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.attribute.failed.entity", object);
    });
    private static final Dynamic2CommandExceptionType ERROR_NO_SUCH_ATTRIBUTE = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("commands.attribute.failed.no_attribute", object, object1);
    });
    private static final Dynamic3CommandExceptionType ERROR_NO_SUCH_MODIFIER = new Dynamic3CommandExceptionType((object, object1, object2) -> {
        return Component.translatableEscape("commands.attribute.failed.no_modifier", object1, object, object2);
    });
    private static final Dynamic3CommandExceptionType ERROR_MODIFIER_ALREADY_PRESENT = new Dynamic3CommandExceptionType((object, object1, object2) -> {
        return Component.translatableEscape("commands.attribute.failed.modifier_already_present", object2, object1, object);
    });

    public AttributeCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("attribute").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.argument("target", EntityArgument.entity()).then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument("attribute", ResourceArgument.resource(context, Registries.ATTRIBUTE)).then(((LiteralArgumentBuilder) Commands.literal("get").executes((commandcontext) -> {
            return getAttributeValue((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntity(commandcontext, "target"), ResourceArgument.getAttribute(commandcontext, "attribute"), 1.0D);
        })).then(Commands.argument("scale", DoubleArgumentType.doubleArg()).executes((commandcontext) -> {
            return getAttributeValue((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntity(commandcontext, "target"), ResourceArgument.getAttribute(commandcontext, "attribute"), DoubleArgumentType.getDouble(commandcontext, "scale"));
        })))).then(((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("base").then(Commands.literal("set").then(Commands.argument("value", DoubleArgumentType.doubleArg()).executes((commandcontext) -> {
            return setAttributeBase((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntity(commandcontext, "target"), ResourceArgument.getAttribute(commandcontext, "attribute"), DoubleArgumentType.getDouble(commandcontext, "value"));
        })))).then(((LiteralArgumentBuilder) Commands.literal("get").executes((commandcontext) -> {
            return getAttributeBase((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntity(commandcontext, "target"), ResourceArgument.getAttribute(commandcontext, "attribute"), 1.0D);
        })).then(Commands.argument("scale", DoubleArgumentType.doubleArg()).executes((commandcontext) -> {
            return getAttributeBase((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntity(commandcontext, "target"), ResourceArgument.getAttribute(commandcontext, "attribute"), DoubleArgumentType.getDouble(commandcontext, "scale"));
        })))).then(Commands.literal("reset").executes((commandcontext) -> {
            return resetAttributeBase((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntity(commandcontext, "target"), ResourceArgument.getAttribute(commandcontext, "attribute"));
        })))).then(((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("modifier").then(Commands.literal("add").then(Commands.argument("id", IdentifierArgument.id()).then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument("value", DoubleArgumentType.doubleArg()).then(Commands.literal("add_value").executes((commandcontext) -> {
            return addModifier((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntity(commandcontext, "target"), ResourceArgument.getAttribute(commandcontext, "attribute"), IdentifierArgument.getId(commandcontext, "id"), DoubleArgumentType.getDouble(commandcontext, "value"), AttributeModifier.Operation.ADD_VALUE);
        }))).then(Commands.literal("add_multiplied_base").executes((commandcontext) -> {
            return addModifier((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntity(commandcontext, "target"), ResourceArgument.getAttribute(commandcontext, "attribute"), IdentifierArgument.getId(commandcontext, "id"), DoubleArgumentType.getDouble(commandcontext, "value"), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        }))).then(Commands.literal("add_multiplied_total").executes((commandcontext) -> {
            return addModifier((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntity(commandcontext, "target"), ResourceArgument.getAttribute(commandcontext, "attribute"), IdentifierArgument.getId(commandcontext, "id"), DoubleArgumentType.getDouble(commandcontext, "value"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        })))))).then(Commands.literal("remove").then(Commands.argument("id", IdentifierArgument.id()).suggests((commandcontext, suggestionsbuilder) -> {
            return SharedSuggestionProvider.suggestResource(getAttributeModifiers(EntityArgument.getEntity(commandcontext, "target"), ResourceArgument.getAttribute(commandcontext, "attribute")), suggestionsbuilder);
        }).executes((commandcontext) -> {
            return removeModifier((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntity(commandcontext, "target"), ResourceArgument.getAttribute(commandcontext, "attribute"), IdentifierArgument.getId(commandcontext, "id"));
        })))).then(Commands.literal("value").then(Commands.literal("get").then(((RequiredArgumentBuilder) Commands.argument("id", IdentifierArgument.id()).suggests((commandcontext, suggestionsbuilder) -> {
            return SharedSuggestionProvider.suggestResource(getAttributeModifiers(EntityArgument.getEntity(commandcontext, "target"), ResourceArgument.getAttribute(commandcontext, "attribute")), suggestionsbuilder);
        }).executes((commandcontext) -> {
            return getAttributeModifier((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntity(commandcontext, "target"), ResourceArgument.getAttribute(commandcontext, "attribute"), IdentifierArgument.getId(commandcontext, "id"), 1.0D);
        })).then(Commands.argument("scale", DoubleArgumentType.doubleArg()).executes((commandcontext) -> {
            return getAttributeModifier((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntity(commandcontext, "target"), ResourceArgument.getAttribute(commandcontext, "attribute"), IdentifierArgument.getId(commandcontext, "id"), DoubleArgumentType.getDouble(commandcontext, "scale"));
        })))))))));
    }

    private static AttributeInstance getAttributeInstance(Entity target, Holder<Attribute> attribute) throws CommandSyntaxException {
        AttributeInstance attributeinstance = getLivingEntity(target).getAttributes().getInstance(attribute);

        if (attributeinstance == null) {
            throw AttributeCommand.ERROR_NO_SUCH_ATTRIBUTE.create(target.getName(), getAttributeDescription(attribute));
        } else {
            return attributeinstance;
        }
    }

    private static LivingEntity getLivingEntity(Entity target) throws CommandSyntaxException {
        if (!(target instanceof LivingEntity)) {
            throw AttributeCommand.ERROR_NOT_LIVING_ENTITY.create(target.getName());
        } else {
            return (LivingEntity) target;
        }
    }

    private static LivingEntity getEntityWithAttribute(Entity target, Holder<Attribute> attribute) throws CommandSyntaxException {
        LivingEntity livingentity = getLivingEntity(target);

        if (!livingentity.getAttributes().hasAttribute(attribute)) {
            throw AttributeCommand.ERROR_NO_SUCH_ATTRIBUTE.create(target.getName(), getAttributeDescription(attribute));
        } else {
            return livingentity;
        }
    }

    private static int getAttributeValue(CommandSourceStack source, Entity target, Holder<Attribute> attribute, double scale) throws CommandSyntaxException {
        LivingEntity livingentity = getEntityWithAttribute(target, attribute);
        double d1 = livingentity.getAttributeValue(attribute);

        source.sendSuccess(() -> {
            return Component.translatable("commands.attribute.value.get.success", getAttributeDescription(attribute), target.getName(), d1);
        }, false);
        return (int) (d1 * scale);
    }

    private static int getAttributeBase(CommandSourceStack source, Entity target, Holder<Attribute> attribute, double scale) throws CommandSyntaxException {
        LivingEntity livingentity = getEntityWithAttribute(target, attribute);
        double d1 = livingentity.getAttributeBaseValue(attribute);

        source.sendSuccess(() -> {
            return Component.translatable("commands.attribute.base_value.get.success", getAttributeDescription(attribute), target.getName(), d1);
        }, false);
        return (int) (d1 * scale);
    }

    private static int getAttributeModifier(CommandSourceStack source, Entity target, Holder<Attribute> attribute, Identifier id, double scale) throws CommandSyntaxException {
        LivingEntity livingentity = getEntityWithAttribute(target, attribute);
        AttributeMap attributemap = livingentity.getAttributes();

        if (!attributemap.hasModifier(attribute, id)) {
            throw AttributeCommand.ERROR_NO_SUCH_MODIFIER.create(target.getName(), getAttributeDescription(attribute), id);
        } else {
            double d1 = attributemap.getModifierValue(attribute, id);

            source.sendSuccess(() -> {
                return Component.translatable("commands.attribute.modifier.value.get.success", Component.translationArg(id), getAttributeDescription(attribute), target.getName(), d1);
            }, false);
            return (int) (d1 * scale);
        }
    }

    private static Stream<Identifier> getAttributeModifiers(Entity target, Holder<Attribute> attribute) throws CommandSyntaxException {
        AttributeInstance attributeinstance = getAttributeInstance(target, attribute);

        return attributeinstance.getModifiers().stream().map(AttributeModifier::id);
    }

    private static int setAttributeBase(CommandSourceStack source, Entity target, Holder<Attribute> attribute, double value) throws CommandSyntaxException {
        getAttributeInstance(target, attribute).setBaseValue(value);
        source.sendSuccess(() -> {
            return Component.translatable("commands.attribute.base_value.set.success", getAttributeDescription(attribute), target.getName(), value);
        }, false);
        return 1;
    }

    private static int resetAttributeBase(CommandSourceStack source, Entity target, Holder<Attribute> attribute) throws CommandSyntaxException {
        LivingEntity livingentity = getLivingEntity(target);

        if (!livingentity.getAttributes().resetBaseValue(attribute)) {
            throw AttributeCommand.ERROR_NO_SUCH_ATTRIBUTE.create(target.getName(), getAttributeDescription(attribute));
        } else {
            double d0 = livingentity.getAttributeBaseValue(attribute);

            source.sendSuccess(() -> {
                return Component.translatable("commands.attribute.base_value.reset.success", getAttributeDescription(attribute), target.getName(), d0);
            }, false);
            return 1;
        }
    }

    private static int addModifier(CommandSourceStack source, Entity target, Holder<Attribute> attribute, Identifier id, double value, AttributeModifier.Operation operation) throws CommandSyntaxException {
        AttributeInstance attributeinstance = getAttributeInstance(target, attribute);
        AttributeModifier attributemodifier = new AttributeModifier(id, value, operation);

        if (attributeinstance.hasModifier(id)) {
            throw AttributeCommand.ERROR_MODIFIER_ALREADY_PRESENT.create(target.getName(), getAttributeDescription(attribute), id);
        } else {
            attributeinstance.addPermanentModifier(attributemodifier);
            source.sendSuccess(() -> {
                return Component.translatable("commands.attribute.modifier.add.success", Component.translationArg(id), getAttributeDescription(attribute), target.getName());
            }, false);
            return 1;
        }
    }

    private static int removeModifier(CommandSourceStack source, Entity target, Holder<Attribute> attribute, Identifier id) throws CommandSyntaxException {
        AttributeInstance attributeinstance = getAttributeInstance(target, attribute);

        if (attributeinstance.removeModifier(id)) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.attribute.modifier.remove.success", Component.translationArg(id), getAttributeDescription(attribute), target.getName());
            }, false);
            return 1;
        } else {
            throw AttributeCommand.ERROR_NO_SUCH_MODIFIER.create(target.getName(), getAttributeDescription(attribute), id);
        }
    }

    private static Component getAttributeDescription(Holder<Attribute> attribute) {
        return Component.translatable(((Attribute) attribute.value()).getDescriptionId());
    }
}
