package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public class GiveCommand {

    public static final int MAX_ALLOWED_ITEMSTACKS = 100;

    public GiveCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.argument("targets", EntityArgument.players()).then(((RequiredArgumentBuilder) Commands.argument("item", ItemArgument.item(context)).executes((commandcontext) -> {
            return giveItem((CommandSourceStack) commandcontext.getSource(), ItemArgument.getItem(commandcontext, "item"), EntityArgument.getPlayers(commandcontext, "targets"), 1);
        })).then(Commands.argument("count", IntegerArgumentType.integer(1)).executes((commandcontext) -> {
            return giveItem((CommandSourceStack) commandcontext.getSource(), ItemArgument.getItem(commandcontext, "item"), EntityArgument.getPlayers(commandcontext, "targets"), IntegerArgumentType.getInteger(commandcontext, "count"));
        })))));
    }

    private static int giveItem(CommandSourceStack source, ItemInput input, Collection<ServerPlayer> players, int count) throws CommandSyntaxException {
        ItemStack itemstack = input.createItemStack(1, false);
        int j = itemstack.getMaxStackSize();
        int k = j * 100;

        if (count > k) {
            source.sendFailure(Component.translatable("commands.give.failed.toomanyitems", k, itemstack.getDisplayName()));
            return 0;
        } else {
            for (ServerPlayer serverplayer : players) {
                int l = count;

                while (l > 0) {
                    int i1 = Math.min(j, l);

                    l -= i1;
                    ItemStack itemstack1 = input.createItemStack(i1, false);
                    boolean flag = serverplayer.getInventory().add(itemstack1);

                    if (flag && itemstack1.isEmpty()) {
                        ItemEntity itementity = serverplayer.drop(itemstack, false);

                        if (itementity != null) {
                            itementity.makeFakeItem();
                        }

                        serverplayer.level().playSound((Entity) null, serverplayer.getX(), serverplayer.getY(), serverplayer.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F, ((serverplayer.getRandom().nextFloat() - serverplayer.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F);
                        serverplayer.containerMenu.broadcastChanges();
                    } else {
                        ItemEntity itementity1 = serverplayer.drop(itemstack1, false);

                        if (itementity1 != null) {
                            itementity1.setNoPickUpDelay();
                            itementity1.setTarget(serverplayer.getUUID());
                        }
                    }
                }
            }

            if (players.size() == 1) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.give.success.single", count, itemstack.getDisplayName(), ((ServerPlayer) players.iterator().next()).getDisplayName());
                }, true);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.give.success.single", count, itemstack.getDisplayName(), players.size());
                }, true);
            }

            return players.size();
        }
    }
}
