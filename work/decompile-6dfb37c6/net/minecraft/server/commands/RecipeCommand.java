package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import java.util.Collections;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;

public class RecipeCommand {

    private static final SimpleCommandExceptionType ERROR_GIVE_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.recipe.give.failed"));
    private static final SimpleCommandExceptionType ERROR_TAKE_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.recipe.take.failed"));

    public RecipeCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("recipe").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.literal("give").then(((RequiredArgumentBuilder) Commands.argument("targets", EntityArgument.players()).then(Commands.argument("recipe", ResourceKeyArgument.key(Registries.RECIPE)).executes((commandcontext) -> {
            return giveRecipes((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), Collections.singleton(ResourceKeyArgument.getRecipe(commandcontext, "recipe")));
        }))).then(Commands.literal("*").executes((commandcontext) -> {
            return giveRecipes((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), ((CommandSourceStack) commandcontext.getSource()).getServer().getRecipeManager().getRecipes());
        }))))).then(Commands.literal("take").then(((RequiredArgumentBuilder) Commands.argument("targets", EntityArgument.players()).then(Commands.argument("recipe", ResourceKeyArgument.key(Registries.RECIPE)).executes((commandcontext) -> {
            return takeRecipes((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), Collections.singleton(ResourceKeyArgument.getRecipe(commandcontext, "recipe")));
        }))).then(Commands.literal("*").executes((commandcontext) -> {
            return takeRecipes((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), ((CommandSourceStack) commandcontext.getSource()).getServer().getRecipeManager().getRecipes());
        })))));
    }

    private static int giveRecipes(CommandSourceStack source, Collection<ServerPlayer> players, Collection<RecipeHolder<?>> recipes) throws CommandSyntaxException {
        int i = 0;

        for (ServerPlayer serverplayer : players) {
            i += serverplayer.awardRecipes(recipes);
        }

        if (i == 0) {
            throw RecipeCommand.ERROR_GIVE_FAILED.create();
        } else {
            if (players.size() == 1) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.recipe.give.success.single", recipes.size(), ((ServerPlayer) players.iterator().next()).getDisplayName());
                }, true);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.recipe.give.success.multiple", recipes.size(), players.size());
                }, true);
            }

            return i;
        }
    }

    private static int takeRecipes(CommandSourceStack source, Collection<ServerPlayer> players, Collection<RecipeHolder<?>> recipes) throws CommandSyntaxException {
        int i = 0;

        for (ServerPlayer serverplayer : players) {
            i += serverplayer.resetRecipes(recipes);
        }

        if (i == 0) {
            throw RecipeCommand.ERROR_TAKE_FAILED.create();
        } else {
            if (players.size() == 1) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.recipe.take.success.single", recipes.size(), ((ServerPlayer) players.iterator().next()).getDisplayName());
                }, true);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.recipe.take.success.multiple", recipes.size(), players.size());
                }, true);
            }

            return i;
        }
    }
}
