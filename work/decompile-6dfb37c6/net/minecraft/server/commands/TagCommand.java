package net.minecraft.server.commands;

import com.google.common.collect.Sets;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.world.entity.Entity;

public class TagCommand {

    private static final SimpleCommandExceptionType ERROR_ADD_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.tag.add.failed"));
    private static final SimpleCommandExceptionType ERROR_REMOVE_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.tag.remove.failed"));

    public TagCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("tag").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument("targets", EntityArgument.entities()).then(Commands.literal("add").then(Commands.argument("name", StringArgumentType.word()).executes((commandcontext) -> {
            return addTag((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), StringArgumentType.getString(commandcontext, "name"));
        })))).then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.word()).suggests((commandcontext, suggestionsbuilder) -> {
            return SharedSuggestionProvider.suggest(getTags(EntityArgument.getEntities(commandcontext, "targets")), suggestionsbuilder);
        }).executes((commandcontext) -> {
            return removeTag((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), StringArgumentType.getString(commandcontext, "name"));
        })))).then(Commands.literal("list").executes((commandcontext) -> {
            return listTags((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"));
        }))));
    }

    private static Collection<String> getTags(Collection<? extends Entity> entities) {
        Set<String> set = Sets.newHashSet();

        for (Entity entity : entities) {
            set.addAll(entity.getTags());
        }

        return set;
    }

    private static int addTag(CommandSourceStack source, Collection<? extends Entity> targets, String name) throws CommandSyntaxException {
        int i = 0;

        for (Entity entity : targets) {
            if (entity.addTag(name)) {
                ++i;
            }
        }

        if (i == 0) {
            throw TagCommand.ERROR_ADD_FAILED.create();
        } else {
            if (targets.size() == 1) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.tag.add.success.single", name, ((Entity) targets.iterator().next()).getDisplayName());
                }, true);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.tag.add.success.multiple", name, targets.size());
                }, true);
            }

            return i;
        }
    }

    private static int removeTag(CommandSourceStack source, Collection<? extends Entity> targets, String name) throws CommandSyntaxException {
        int i = 0;

        for (Entity entity : targets) {
            if (entity.removeTag(name)) {
                ++i;
            }
        }

        if (i == 0) {
            throw TagCommand.ERROR_REMOVE_FAILED.create();
        } else {
            if (targets.size() == 1) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.tag.remove.success.single", name, ((Entity) targets.iterator().next()).getDisplayName());
                }, true);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.tag.remove.success.multiple", name, targets.size());
                }, true);
            }

            return i;
        }
    }

    private static int listTags(CommandSourceStack source, Collection<? extends Entity> targets) {
        Set<String> set = Sets.newHashSet();

        for (Entity entity : targets) {
            set.addAll(entity.getTags());
        }

        if (targets.size() == 1) {
            Entity entity1 = (Entity) targets.iterator().next();

            if (set.isEmpty()) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.tag.list.single.empty", entity1.getDisplayName());
                }, false);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.tag.list.single.success", entity1.getDisplayName(), set.size(), ComponentUtils.formatList(set));
                }, false);
            }
        } else if (set.isEmpty()) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.tag.list.multiple.empty", targets.size());
            }, false);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.tag.list.multiple.success", targets.size(), set.size(), ComponentUtils.formatList(set));
            }, false);
        }

        return set.size();
    }
}
