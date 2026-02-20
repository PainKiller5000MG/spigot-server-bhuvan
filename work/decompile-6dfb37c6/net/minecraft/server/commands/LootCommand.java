package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceOrIdArgument;
import net.minecraft.commands.arguments.SlotArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

public class LootCommand {

    private static final DynamicCommandExceptionType ERROR_NO_HELD_ITEMS = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.drop.no_held_items", object);
    });
    private static final DynamicCommandExceptionType ERROR_NO_ENTITY_LOOT_TABLE = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.drop.no_loot_table.entity", object);
    });
    private static final DynamicCommandExceptionType ERROR_NO_BLOCK_LOOT_TABLE = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.drop.no_loot_table.block", object);
    });

    public LootCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register((LiteralArgumentBuilder) addTargets((LiteralArgumentBuilder) Commands.literal("loot").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)), (argumentbuilder, lootcommand_dropconsumer) -> {
            return argumentbuilder.then(Commands.literal("fish").then(Commands.argument("loot_table", ResourceOrIdArgument.lootTable(context)).then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument("pos", BlockPosArgument.blockPos()).executes((commandcontext) -> {
                return dropFishingLoot(commandcontext, ResourceOrIdArgument.getLootTable(commandcontext, "loot_table"), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), ItemStack.EMPTY, lootcommand_dropconsumer);
            })).then(Commands.argument("tool", ItemArgument.item(context)).executes((commandcontext) -> {
                return dropFishingLoot(commandcontext, ResourceOrIdArgument.getLootTable(commandcontext, "loot_table"), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), ItemArgument.getItem(commandcontext, "tool").createItemStack(1, false), lootcommand_dropconsumer);
            }))).then(Commands.literal("mainhand").executes((commandcontext) -> {
                return dropFishingLoot(commandcontext, ResourceOrIdArgument.getLootTable(commandcontext, "loot_table"), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), getSourceHandItem((CommandSourceStack) commandcontext.getSource(), EquipmentSlot.MAINHAND), lootcommand_dropconsumer);
            }))).then(Commands.literal("offhand").executes((commandcontext) -> {
                return dropFishingLoot(commandcontext, ResourceOrIdArgument.getLootTable(commandcontext, "loot_table"), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), getSourceHandItem((CommandSourceStack) commandcontext.getSource(), EquipmentSlot.OFFHAND), lootcommand_dropconsumer);
            }))))).then(Commands.literal("loot").then(Commands.argument("loot_table", ResourceOrIdArgument.lootTable(context)).executes((commandcontext) -> {
                return dropChestLoot(commandcontext, ResourceOrIdArgument.getLootTable(commandcontext, "loot_table"), lootcommand_dropconsumer);
            }))).then(Commands.literal("kill").then(Commands.argument("target", EntityArgument.entity()).executes((commandcontext) -> {
                return dropKillLoot(commandcontext, EntityArgument.getEntity(commandcontext, "target"), lootcommand_dropconsumer);
            }))).then(Commands.literal("mine").then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument("pos", BlockPosArgument.blockPos()).executes((commandcontext) -> {
                return dropBlockLoot(commandcontext, BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), ItemStack.EMPTY, lootcommand_dropconsumer);
            })).then(Commands.argument("tool", ItemArgument.item(context)).executes((commandcontext) -> {
                return dropBlockLoot(commandcontext, BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), ItemArgument.getItem(commandcontext, "tool").createItemStack(1, false), lootcommand_dropconsumer);
            }))).then(Commands.literal("mainhand").executes((commandcontext) -> {
                return dropBlockLoot(commandcontext, BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), getSourceHandItem((CommandSourceStack) commandcontext.getSource(), EquipmentSlot.MAINHAND), lootcommand_dropconsumer);
            }))).then(Commands.literal("offhand").executes((commandcontext) -> {
                return dropBlockLoot(commandcontext, BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), getSourceHandItem((CommandSourceStack) commandcontext.getSource(), EquipmentSlot.OFFHAND), lootcommand_dropconsumer);
            }))));
        }));
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T addTargets(T root, LootCommand.TailProvider tail) {
        return (T) root.then(((LiteralArgumentBuilder) Commands.literal("replace").then(Commands.literal("entity").then(Commands.argument("entities", EntityArgument.entities()).then(tail.construct(Commands.argument("slot", SlotArgument.slot()), (commandcontext, list, lootcommand_callback) -> {
            return entityReplace(EntityArgument.getEntities(commandcontext, "entities"), SlotArgument.getSlot(commandcontext, "slot"), list.size(), list, lootcommand_callback);
        }).then(tail.construct(Commands.argument("count", IntegerArgumentType.integer(0)), (commandcontext, list, lootcommand_callback) -> {
            return entityReplace(EntityArgument.getEntities(commandcontext, "entities"), SlotArgument.getSlot(commandcontext, "slot"), IntegerArgumentType.getInteger(commandcontext, "count"), list, lootcommand_callback);
        })))))).then(Commands.literal("block").then(Commands.argument("targetPos", BlockPosArgument.blockPos()).then(tail.construct(Commands.argument("slot", SlotArgument.slot()), (commandcontext, list, lootcommand_callback) -> {
            return blockReplace((CommandSourceStack) commandcontext.getSource(), BlockPosArgument.getLoadedBlockPos(commandcontext, "targetPos"), SlotArgument.getSlot(commandcontext, "slot"), list.size(), list, lootcommand_callback);
        }).then(tail.construct(Commands.argument("count", IntegerArgumentType.integer(0)), (commandcontext, list, lootcommand_callback) -> {
            return blockReplace((CommandSourceStack) commandcontext.getSource(), BlockPosArgument.getLoadedBlockPos(commandcontext, "targetPos"), IntegerArgumentType.getInteger(commandcontext, "slot"), IntegerArgumentType.getInteger(commandcontext, "count"), list, lootcommand_callback);
        })))))).then(Commands.literal("insert").then(tail.construct(Commands.argument("targetPos", BlockPosArgument.blockPos()), (commandcontext, list, lootcommand_callback) -> {
            return blockDistribute((CommandSourceStack) commandcontext.getSource(), BlockPosArgument.getLoadedBlockPos(commandcontext, "targetPos"), list, lootcommand_callback);
        }))).then(Commands.literal("give").then(tail.construct(Commands.argument("players", EntityArgument.players()), (commandcontext, list, lootcommand_callback) -> {
            return playerGive(EntityArgument.getPlayers(commandcontext, "players"), list, lootcommand_callback);
        }))).then(Commands.literal("spawn").then(tail.construct(Commands.argument("targetPos", Vec3Argument.vec3()), (commandcontext, list, lootcommand_callback) -> {
            return dropInWorld((CommandSourceStack) commandcontext.getSource(), Vec3Argument.getVec3(commandcontext, "targetPos"), list, lootcommand_callback);
        })));
    }

    private static Container getContainer(CommandSourceStack source, BlockPos pos) throws CommandSyntaxException {
        BlockEntity blockentity = source.getLevel().getBlockEntity(pos);

        if (!(blockentity instanceof Container)) {
            throw ItemCommands.ERROR_TARGET_NOT_A_CONTAINER.create(pos.getX(), pos.getY(), pos.getZ());
        } else {
            return (Container) blockentity;
        }
    }

    private static int blockDistribute(CommandSourceStack source, BlockPos pos, List<ItemStack> drops, LootCommand.Callback callback) throws CommandSyntaxException {
        Container container = getContainer(source, pos);
        List<ItemStack> list1 = Lists.newArrayListWithCapacity(drops.size());

        for (ItemStack itemstack : drops) {
            if (distributeToContainer(container, itemstack.copy())) {
                container.setChanged();
                list1.add(itemstack);
            }
        }

        callback.accept(list1);
        return list1.size();
    }

    private static boolean distributeToContainer(Container container, ItemStack itemStack) {
        boolean flag = false;

        for (int i = 0; i < container.getContainerSize() && !itemStack.isEmpty(); ++i) {
            ItemStack itemstack1 = container.getItem(i);

            if (container.canPlaceItem(i, itemStack)) {
                if (itemstack1.isEmpty()) {
                    container.setItem(i, itemStack);
                    flag = true;
                    break;
                }

                if (canMergeItems(itemstack1, itemStack)) {
                    int j = itemStack.getMaxStackSize() - itemstack1.getCount();
                    int k = Math.min(itemStack.getCount(), j);

                    itemStack.shrink(k);
                    itemstack1.grow(k);
                    flag = true;
                }
            }
        }

        return flag;
    }

    private static int blockReplace(CommandSourceStack source, BlockPos pos, int startSlot, int slotCount, List<ItemStack> drops, LootCommand.Callback callback) throws CommandSyntaxException {
        Container container = getContainer(source, pos);
        int k = container.getContainerSize();

        if (startSlot >= 0 && startSlot < k) {
            List<ItemStack> list1 = Lists.newArrayListWithCapacity(drops.size());

            for (int l = 0; l < slotCount; ++l) {
                int i1 = startSlot + l;
                ItemStack itemstack = l < drops.size() ? (ItemStack) drops.get(l) : ItemStack.EMPTY;

                if (container.canPlaceItem(i1, itemstack)) {
                    container.setItem(i1, itemstack);
                    list1.add(itemstack);
                }
            }

            callback.accept(list1);
            return list1.size();
        } else {
            throw ItemCommands.ERROR_TARGET_INAPPLICABLE_SLOT.create(startSlot);
        }
    }

    private static boolean canMergeItems(ItemStack a, ItemStack b) {
        return a.getCount() <= a.getMaxStackSize() && ItemStack.isSameItemSameComponents(a, b);
    }

    private static int playerGive(Collection<ServerPlayer> players, List<ItemStack> drops, LootCommand.Callback callback) throws CommandSyntaxException {
        List<ItemStack> list1 = Lists.newArrayListWithCapacity(drops.size());

        for (ItemStack itemstack : drops) {
            for (ServerPlayer serverplayer : players) {
                if (serverplayer.getInventory().add(itemstack.copy())) {
                    list1.add(itemstack);
                }
            }
        }

        callback.accept(list1);
        return list1.size();
    }

    private static void setSlots(Entity entity, List<ItemStack> itemsToSet, int startSlot, int count, List<ItemStack> usedItems) {
        for (int k = 0; k < count; ++k) {
            ItemStack itemstack = k < itemsToSet.size() ? (ItemStack) itemsToSet.get(k) : ItemStack.EMPTY;
            SlotAccess slotaccess = entity.getSlot(startSlot + k);

            if (slotaccess != null && slotaccess.set(itemstack.copy())) {
                usedItems.add(itemstack);
            }
        }

    }

    private static int entityReplace(Collection<? extends Entity> entities, int startSlot, int count, List<ItemStack> drops, LootCommand.Callback callback) throws CommandSyntaxException {
        List<ItemStack> list1 = Lists.newArrayListWithCapacity(drops.size());

        for (Entity entity : entities) {
            if (entity instanceof ServerPlayer serverplayer) {
                setSlots(entity, drops, startSlot, count, list1);
                serverplayer.containerMenu.broadcastChanges();
            } else {
                setSlots(entity, drops, startSlot, count, list1);
            }
        }

        callback.accept(list1);
        return list1.size();
    }

    private static int dropInWorld(CommandSourceStack source, Vec3 pos, List<ItemStack> drops, LootCommand.Callback callback) throws CommandSyntaxException {
        ServerLevel serverlevel = source.getLevel();

        drops.forEach((itemstack) -> {
            ItemEntity itementity = new ItemEntity(serverlevel, pos.x, pos.y, pos.z, itemstack.copy());

            itementity.setDefaultPickUpDelay();
            serverlevel.addFreshEntity(itementity);
        });
        callback.accept(drops);
        return drops.size();
    }

    private static void callback(CommandSourceStack source, List<ItemStack> drops) {
        if (drops.size() == 1) {
            ItemStack itemstack = (ItemStack) drops.get(0);

            source.sendSuccess(() -> {
                return Component.translatable("commands.drop.success.single", itemstack.getCount(), itemstack.getDisplayName());
            }, false);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.drop.success.multiple", drops.size());
            }, false);
        }

    }

    private static void callback(CommandSourceStack source, List<ItemStack> drops, ResourceKey<LootTable> location) {
        if (drops.size() == 1) {
            ItemStack itemstack = (ItemStack) drops.get(0);

            source.sendSuccess(() -> {
                return Component.translatable("commands.drop.success.single_with_table", itemstack.getCount(), itemstack.getDisplayName(), Component.translationArg(location.identifier()));
            }, false);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.drop.success.multiple_with_table", drops.size(), Component.translationArg(location.identifier()));
            }, false);
        }

    }

    private static ItemStack getSourceHandItem(CommandSourceStack source, EquipmentSlot slot) throws CommandSyntaxException {
        Entity entity = source.getEntityOrException();

        if (entity instanceof LivingEntity) {
            return ((LivingEntity) entity).getItemBySlot(slot);
        } else {
            throw LootCommand.ERROR_NO_HELD_ITEMS.create(entity.getDisplayName());
        }
    }

    private static int dropBlockLoot(CommandContext<CommandSourceStack> context, BlockPos pos, ItemStack tool, LootCommand.DropConsumer output) throws CommandSyntaxException {
        CommandSourceStack commandsourcestack = (CommandSourceStack) context.getSource();
        ServerLevel serverlevel = commandsourcestack.getLevel();
        BlockState blockstate = serverlevel.getBlockState(pos);
        BlockEntity blockentity = serverlevel.getBlockEntity(pos);
        Optional<ResourceKey<LootTable>> optional = blockstate.getBlock().getLootTable();

        if (optional.isEmpty()) {
            throw LootCommand.ERROR_NO_BLOCK_LOOT_TABLE.create(blockstate.getBlock().getName());
        } else {
            LootParams.Builder lootparams_builder = (new LootParams.Builder(serverlevel)).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos)).withParameter(LootContextParams.BLOCK_STATE, blockstate).withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockentity).withOptionalParameter(LootContextParams.THIS_ENTITY, commandsourcestack.getEntity()).withParameter(LootContextParams.TOOL, tool);
            List<ItemStack> list = blockstate.getDrops(lootparams_builder);

            return output.accept(context, list, (list1) -> {
                callback(commandsourcestack, list1, (ResourceKey) optional.get());
            });
        }
    }

    private static int dropKillLoot(CommandContext<CommandSourceStack> context, Entity target, LootCommand.DropConsumer output) throws CommandSyntaxException {
        Optional<ResourceKey<LootTable>> optional = target.getLootTable();

        if (optional.isEmpty()) {
            throw LootCommand.ERROR_NO_ENTITY_LOOT_TABLE.create(target.getDisplayName());
        } else {
            CommandSourceStack commandsourcestack = (CommandSourceStack) context.getSource();
            LootParams.Builder lootparams_builder = new LootParams.Builder(commandsourcestack.getLevel());
            Entity entity1 = commandsourcestack.getEntity();

            if (entity1 instanceof Player) {
                Player player = (Player) entity1;

                lootparams_builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player);
            }

            lootparams_builder.withParameter(LootContextParams.DAMAGE_SOURCE, target.damageSources().magic());
            lootparams_builder.withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, entity1);
            lootparams_builder.withOptionalParameter(LootContextParams.ATTACKING_ENTITY, entity1);
            lootparams_builder.withParameter(LootContextParams.THIS_ENTITY, target);
            lootparams_builder.withParameter(LootContextParams.ORIGIN, commandsourcestack.getPosition());
            LootParams lootparams = lootparams_builder.create(LootContextParamSets.ENTITY);
            LootTable loottable = commandsourcestack.getServer().reloadableRegistries().getLootTable((ResourceKey) optional.get());
            List<ItemStack> list = loottable.getRandomItems(lootparams);

            return output.accept(context, list, (list1) -> {
                callback(commandsourcestack, list1, (ResourceKey) optional.get());
            });
        }
    }

    private static int dropChestLoot(CommandContext<CommandSourceStack> context, Holder<LootTable> lootTable, LootCommand.DropConsumer output) throws CommandSyntaxException {
        CommandSourceStack commandsourcestack = (CommandSourceStack) context.getSource();
        LootParams lootparams = (new LootParams.Builder(commandsourcestack.getLevel())).withOptionalParameter(LootContextParams.THIS_ENTITY, commandsourcestack.getEntity()).withParameter(LootContextParams.ORIGIN, commandsourcestack.getPosition()).create(LootContextParamSets.CHEST);

        return drop(context, lootTable, lootparams, output);
    }

    private static int dropFishingLoot(CommandContext<CommandSourceStack> context, Holder<LootTable> lootTable, BlockPos pos, ItemStack tool, LootCommand.DropConsumer output) throws CommandSyntaxException {
        CommandSourceStack commandsourcestack = (CommandSourceStack) context.getSource();
        LootParams lootparams = (new LootParams.Builder(commandsourcestack.getLevel())).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos)).withParameter(LootContextParams.TOOL, tool).withOptionalParameter(LootContextParams.THIS_ENTITY, commandsourcestack.getEntity()).create(LootContextParamSets.FISHING);

        return drop(context, lootTable, lootparams, output);
    }

    private static int drop(CommandContext<CommandSourceStack> context, Holder<LootTable> lootTable, LootParams lootParams, LootCommand.DropConsumer output) throws CommandSyntaxException {
        CommandSourceStack commandsourcestack = (CommandSourceStack) context.getSource();
        List<ItemStack> list = ((LootTable) lootTable.value()).getRandomItems(lootParams);

        return output.accept(context, list, (list1) -> {
            callback(commandsourcestack, list1);
        });
    }

    @FunctionalInterface
    private interface Callback {

        void accept(List<ItemStack> setItems) throws CommandSyntaxException;
    }

    @FunctionalInterface
    private interface DropConsumer {

        int accept(CommandContext<CommandSourceStack> context, List<ItemStack> drops, LootCommand.Callback successCallback) throws CommandSyntaxException;
    }

    @FunctionalInterface
    private interface TailProvider {

        ArgumentBuilder<CommandSourceStack, ?> construct(ArgumentBuilder<CommandSourceStack, ?> root, LootCommand.DropConsumer consumer);
    }
}
