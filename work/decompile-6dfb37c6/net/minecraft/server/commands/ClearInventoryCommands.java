package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Predicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class ClearInventoryCommands {

    private static final DynamicCommandExceptionType ERROR_SINGLE = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("clear.failed.single", object);
    });
    private static final DynamicCommandExceptionType ERROR_MULTIPLE = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("clear.failed.multiple", object);
    });

    public ClearInventoryCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("clear").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).executes((commandcontext) -> {
            return clearUnlimited((CommandSourceStack) commandcontext.getSource(), Collections.singleton(((CommandSourceStack) commandcontext.getSource()).getPlayerOrException()), (itemstack) -> {
                return true;
            });
        })).then(((RequiredArgumentBuilder) Commands.argument("targets", EntityArgument.players()).executes((commandcontext) -> {
            return clearUnlimited((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), (itemstack) -> {
                return true;
            });
        })).then(((RequiredArgumentBuilder) Commands.argument("item", ItemPredicateArgument.itemPredicate(context)).executes((commandcontext) -> {
            return clearUnlimited((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), ItemPredicateArgument.getItemPredicate(commandcontext, "item"));
        })).then(Commands.argument("maxCount", IntegerArgumentType.integer(0)).executes((commandcontext) -> {
            return clearInventory((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), ItemPredicateArgument.getItemPredicate(commandcontext, "item"), IntegerArgumentType.getInteger(commandcontext, "maxCount"));
        })))));
    }

    private static int clearUnlimited(CommandSourceStack source, Collection<ServerPlayer> players, Predicate<ItemStack> predicate) throws CommandSyntaxException {
        return clearInventory(source, players, predicate, -1);
    }

    private static int clearInventory(CommandSourceStack source, Collection<ServerPlayer> players, Predicate<ItemStack> predicate, int maxCount) throws CommandSyntaxException {
        int j = 0;

        for (ServerPlayer serverplayer : players) {
            j += serverplayer.getInventory().clearOrCountMatchingItems(predicate, maxCount, serverplayer.inventoryMenu.getCraftSlots());
            serverplayer.containerMenu.broadcastChanges();
            serverplayer.inventoryMenu.slotsChanged(serverplayer.getInventory());
        }

        if (j == 0) {
            if (players.size() == 1) {
                throw ClearInventoryCommands.ERROR_SINGLE.create(((ServerPlayer) players.iterator().next()).getName());
            } else {
                throw ClearInventoryCommands.ERROR_MULTIPLE.create(players.size());
            }
        } else {
            if (maxCount == 0) {
                if (players.size() == 1) {
                    source.sendSuccess(() -> {
                        return Component.translatable("commands.clear.test.single", j, ((ServerPlayer) players.iterator().next()).getDisplayName());
                    }, true);
                } else {
                    source.sendSuccess(() -> {
                        return Component.translatable("commands.clear.test.multiple", j, players.size());
                    }, true);
                }
            } else if (players.size() == 1) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.clear.success.single", j, ((ServerPlayer) players.iterator().next()).getDisplayName());
                }, true);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.clear.success.multiple", j, players.size());
                }, true);
            }

            return j;
        }
    }
}
