package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceOrIdArgument;
import net.minecraft.commands.arguments.SlotArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.SlotProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class ItemCommands {

    static final Dynamic3CommandExceptionType ERROR_TARGET_NOT_A_CONTAINER = new Dynamic3CommandExceptionType((object, object1, object2) -> {
        return Component.translatableEscape("commands.item.target.not_a_container", object, object1, object2);
    });
    static final Dynamic3CommandExceptionType ERROR_SOURCE_NOT_A_CONTAINER = new Dynamic3CommandExceptionType((object, object1, object2) -> {
        return Component.translatableEscape("commands.item.source.not_a_container", object, object1, object2);
    });
    static final DynamicCommandExceptionType ERROR_TARGET_INAPPLICABLE_SLOT = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.item.target.no_such_slot", object);
    });
    private static final DynamicCommandExceptionType ERROR_SOURCE_INAPPLICABLE_SLOT = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.item.source.no_such_slot", object);
    });
    private static final DynamicCommandExceptionType ERROR_TARGET_NO_CHANGES = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.item.target.no_changes", object);
    });
    private static final Dynamic2CommandExceptionType ERROR_TARGET_NO_CHANGES_KNOWN_ITEM = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("commands.item.target.no_changed.known_item", object, object1);
    });

    public ItemCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("item").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(((LiteralArgumentBuilder) Commands.literal("replace").then(Commands.literal("block").then(Commands.argument("pos", BlockPosArgument.blockPos()).then(((RequiredArgumentBuilder) Commands.argument("slot", SlotArgument.slot()).then(Commands.literal("with").then(((RequiredArgumentBuilder) Commands.argument("item", ItemArgument.item(context)).executes((commandcontext) -> {
            return setBlockItem((CommandSourceStack) commandcontext.getSource(), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), SlotArgument.getSlot(commandcontext, "slot"), ItemArgument.getItem(commandcontext, "item").createItemStack(1, false));
        })).then(Commands.argument("count", IntegerArgumentType.integer(1, 99)).executes((commandcontext) -> {
            return setBlockItem((CommandSourceStack) commandcontext.getSource(), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), SlotArgument.getSlot(commandcontext, "slot"), ItemArgument.getItem(commandcontext, "item").createItemStack(IntegerArgumentType.getInteger(commandcontext, "count"), true));
        }))))).then(((LiteralArgumentBuilder) Commands.literal("from").then(Commands.literal("block").then(Commands.argument("source", BlockPosArgument.blockPos()).then(((RequiredArgumentBuilder) Commands.argument("sourceSlot", SlotArgument.slot()).executes((commandcontext) -> {
            return blockToBlock((CommandSourceStack) commandcontext.getSource(), BlockPosArgument.getLoadedBlockPos(commandcontext, "source"), SlotArgument.getSlot(commandcontext, "sourceSlot"), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), SlotArgument.getSlot(commandcontext, "slot"));
        })).then(Commands.argument("modifier", ResourceOrIdArgument.lootModifier(context)).executes((commandcontext) -> {
            return blockToBlock((CommandSourceStack) commandcontext.getSource(), BlockPosArgument.getLoadedBlockPos(commandcontext, "source"), SlotArgument.getSlot(commandcontext, "sourceSlot"), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), SlotArgument.getSlot(commandcontext, "slot"), ResourceOrIdArgument.getLootModifier(commandcontext, "modifier"));
        })))))).then(Commands.literal("entity").then(Commands.argument("source", EntityArgument.entity()).then(((RequiredArgumentBuilder) Commands.argument("sourceSlot", SlotArgument.slot()).executes((commandcontext) -> {
            return entityToBlock((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntity(commandcontext, "source"), SlotArgument.getSlot(commandcontext, "sourceSlot"), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), SlotArgument.getSlot(commandcontext, "slot"));
        })).then(Commands.argument("modifier", ResourceOrIdArgument.lootModifier(context)).executes((commandcontext) -> {
            return entityToBlock((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntity(commandcontext, "source"), SlotArgument.getSlot(commandcontext, "sourceSlot"), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), SlotArgument.getSlot(commandcontext, "slot"), ResourceOrIdArgument.getLootModifier(commandcontext, "modifier"));
        })))))))))).then(Commands.literal("entity").then(Commands.argument("targets", EntityArgument.entities()).then(((RequiredArgumentBuilder) Commands.argument("slot", SlotArgument.slot()).then(Commands.literal("with").then(((RequiredArgumentBuilder) Commands.argument("item", ItemArgument.item(context)).executes((commandcontext) -> {
            return setEntityItem((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), SlotArgument.getSlot(commandcontext, "slot"), ItemArgument.getItem(commandcontext, "item").createItemStack(1, false));
        })).then(Commands.argument("count", IntegerArgumentType.integer(1, 99)).executes((commandcontext) -> {
            return setEntityItem((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), SlotArgument.getSlot(commandcontext, "slot"), ItemArgument.getItem(commandcontext, "item").createItemStack(IntegerArgumentType.getInteger(commandcontext, "count"), true));
        }))))).then(((LiteralArgumentBuilder) Commands.literal("from").then(Commands.literal("block").then(Commands.argument("source", BlockPosArgument.blockPos()).then(((RequiredArgumentBuilder) Commands.argument("sourceSlot", SlotArgument.slot()).executes((commandcontext) -> {
            return blockToEntities((CommandSourceStack) commandcontext.getSource(), BlockPosArgument.getLoadedBlockPos(commandcontext, "source"), SlotArgument.getSlot(commandcontext, "sourceSlot"), EntityArgument.getEntities(commandcontext, "targets"), SlotArgument.getSlot(commandcontext, "slot"));
        })).then(Commands.argument("modifier", ResourceOrIdArgument.lootModifier(context)).executes((commandcontext) -> {
            return blockToEntities((CommandSourceStack) commandcontext.getSource(), BlockPosArgument.getLoadedBlockPos(commandcontext, "source"), SlotArgument.getSlot(commandcontext, "sourceSlot"), EntityArgument.getEntities(commandcontext, "targets"), SlotArgument.getSlot(commandcontext, "slot"), ResourceOrIdArgument.getLootModifier(commandcontext, "modifier"));
        })))))).then(Commands.literal("entity").then(Commands.argument("source", EntityArgument.entity()).then(((RequiredArgumentBuilder) Commands.argument("sourceSlot", SlotArgument.slot()).executes((commandcontext) -> {
            return entityToEntities((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntity(commandcontext, "source"), SlotArgument.getSlot(commandcontext, "sourceSlot"), EntityArgument.getEntities(commandcontext, "targets"), SlotArgument.getSlot(commandcontext, "slot"));
        })).then(Commands.argument("modifier", ResourceOrIdArgument.lootModifier(context)).executes((commandcontext) -> {
            return entityToEntities((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntity(commandcontext, "source"), SlotArgument.getSlot(commandcontext, "sourceSlot"), EntityArgument.getEntities(commandcontext, "targets"), SlotArgument.getSlot(commandcontext, "slot"), ResourceOrIdArgument.getLootModifier(commandcontext, "modifier"));
        }))))))))))).then(((LiteralArgumentBuilder) Commands.literal("modify").then(Commands.literal("block").then(Commands.argument("pos", BlockPosArgument.blockPos()).then(Commands.argument("slot", SlotArgument.slot()).then(Commands.argument("modifier", ResourceOrIdArgument.lootModifier(context)).executes((commandcontext) -> {
            return modifyBlockItem((CommandSourceStack) commandcontext.getSource(), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), SlotArgument.getSlot(commandcontext, "slot"), ResourceOrIdArgument.getLootModifier(commandcontext, "modifier"));
        })))))).then(Commands.literal("entity").then(Commands.argument("targets", EntityArgument.entities()).then(Commands.argument("slot", SlotArgument.slot()).then(Commands.argument("modifier", ResourceOrIdArgument.lootModifier(context)).executes((commandcontext) -> {
            return modifyEntityItem((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), SlotArgument.getSlot(commandcontext, "slot"), ResourceOrIdArgument.getLootModifier(commandcontext, "modifier"));
        })))))));
    }

    private static int modifyBlockItem(CommandSourceStack source, BlockPos pos, int slot, Holder<LootItemFunction> modifier) throws CommandSyntaxException {
        Container container = getContainer(source, pos, ItemCommands.ERROR_TARGET_NOT_A_CONTAINER);

        if (slot >= 0 && slot < container.getContainerSize()) {
            ItemStack itemstack = applyModifier(source, modifier, container.getItem(slot));

            container.setItem(slot, itemstack);
            source.sendSuccess(() -> {
                return Component.translatable("commands.item.block.set.success", pos.getX(), pos.getY(), pos.getZ(), itemstack.getDisplayName());
            }, true);
            return 1;
        } else {
            throw ItemCommands.ERROR_TARGET_INAPPLICABLE_SLOT.create(slot);
        }
    }

    private static int modifyEntityItem(CommandSourceStack source, Collection<? extends Entity> entities, int slot, Holder<LootItemFunction> modifier) throws CommandSyntaxException {
        Map<Entity, ItemStack> map = Maps.newHashMapWithExpectedSize(entities.size());

        for (Entity entity : entities) {
            SlotAccess slotaccess = entity.getSlot(slot);

            if (slotaccess != null) {
                ItemStack itemstack = applyModifier(source, modifier, slotaccess.get().copy());

                if (slotaccess.set(itemstack)) {
                    map.put(entity, itemstack);
                    if (entity instanceof ServerPlayer) {
                        ServerPlayer serverplayer = (ServerPlayer) entity;

                        serverplayer.containerMenu.broadcastChanges();
                    }
                }
            }
        }

        if (map.isEmpty()) {
            throw ItemCommands.ERROR_TARGET_NO_CHANGES.create(slot);
        } else {
            if (map.size() == 1) {
                Map.Entry<Entity, ItemStack> map_entry = (Entry) map.entrySet().iterator().next();

                source.sendSuccess(() -> {
                    return Component.translatable("commands.item.entity.set.success.single", ((Entity) map_entry.getKey()).getDisplayName(), ((ItemStack) map_entry.getValue()).getDisplayName());
                }, true);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.item.entity.set.success.multiple", map.size());
                }, true);
            }

            return map.size();
        }
    }

    private static int setBlockItem(CommandSourceStack source, BlockPos pos, int slot, ItemStack itemStack) throws CommandSyntaxException {
        Container container = getContainer(source, pos, ItemCommands.ERROR_TARGET_NOT_A_CONTAINER);

        if (slot >= 0 && slot < container.getContainerSize()) {
            container.setItem(slot, itemStack);
            source.sendSuccess(() -> {
                return Component.translatable("commands.item.block.set.success", pos.getX(), pos.getY(), pos.getZ(), itemStack.getDisplayName());
            }, true);
            return 1;
        } else {
            throw ItemCommands.ERROR_TARGET_INAPPLICABLE_SLOT.create(slot);
        }
    }

    static Container getContainer(CommandSourceStack source, BlockPos pos, Dynamic3CommandExceptionType exceptionType) throws CommandSyntaxException {
        BlockEntity blockentity = source.getLevel().getBlockEntity(pos);

        if (blockentity instanceof Container container) {
            return container;
        } else {
            throw exceptionType.create(pos.getX(), pos.getY(), pos.getZ());
        }
    }

    private static int setEntityItem(CommandSourceStack source, Collection<? extends Entity> entities, int slot, ItemStack itemStack) throws CommandSyntaxException {
        List<Entity> list = Lists.newArrayListWithCapacity(entities.size());

        for (Entity entity : entities) {
            SlotAccess slotaccess = entity.getSlot(slot);

            if (slotaccess != null && slotaccess.set(itemStack.copy())) {
                list.add(entity);
                if (entity instanceof ServerPlayer) {
                    ServerPlayer serverplayer = (ServerPlayer) entity;

                    serverplayer.containerMenu.broadcastChanges();
                }
            }
        }

        if (list.isEmpty()) {
            throw ItemCommands.ERROR_TARGET_NO_CHANGES_KNOWN_ITEM.create(itemStack.getDisplayName(), slot);
        } else {
            if (list.size() == 1) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.item.entity.set.success.single", ((Entity) list.getFirst()).getDisplayName(), itemStack.getDisplayName());
                }, true);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.item.entity.set.success.multiple", list.size(), itemStack.getDisplayName());
                }, true);
            }

            return list.size();
        }
    }

    private static int blockToEntities(CommandSourceStack source, BlockPos sourcePos, int sourceSlot, Collection<? extends Entity> targetEntities, int targetSlot) throws CommandSyntaxException {
        return setEntityItem(source, targetEntities, targetSlot, getBlockItem(source, sourcePos, sourceSlot));
    }

    private static int blockToEntities(CommandSourceStack source, BlockPos sourcePos, int sourceSlot, Collection<? extends Entity> targetEntities, int targetSlot, Holder<LootItemFunction> modifier) throws CommandSyntaxException {
        return setEntityItem(source, targetEntities, targetSlot, applyModifier(source, modifier, getBlockItem(source, sourcePos, sourceSlot)));
    }

    private static int blockToBlock(CommandSourceStack source, BlockPos sourcePos, int sourceSlot, BlockPos targetPos, int targetSlot) throws CommandSyntaxException {
        return setBlockItem(source, targetPos, targetSlot, getBlockItem(source, sourcePos, sourceSlot));
    }

    private static int blockToBlock(CommandSourceStack source, BlockPos sourcePos, int sourceSlot, BlockPos targetPos, int targetSlot, Holder<LootItemFunction> modifier) throws CommandSyntaxException {
        return setBlockItem(source, targetPos, targetSlot, applyModifier(source, modifier, getBlockItem(source, sourcePos, sourceSlot)));
    }

    private static int entityToBlock(CommandSourceStack source, Entity sourceEntity, int sourceSlot, BlockPos targetPos, int targetSlot) throws CommandSyntaxException {
        return setBlockItem(source, targetPos, targetSlot, getItemInSlot(sourceEntity, sourceSlot));
    }

    private static int entityToBlock(CommandSourceStack source, Entity sourceEntity, int sourceSlot, BlockPos targetPos, int targetSlot, Holder<LootItemFunction> modifier) throws CommandSyntaxException {
        return setBlockItem(source, targetPos, targetSlot, applyModifier(source, modifier, getItemInSlot(sourceEntity, sourceSlot)));
    }

    private static int entityToEntities(CommandSourceStack source, Entity sourceEntity, int sourceSlot, Collection<? extends Entity> targetEntities, int targetSlot) throws CommandSyntaxException {
        return setEntityItem(source, targetEntities, targetSlot, getItemInSlot(sourceEntity, sourceSlot));
    }

    private static int entityToEntities(CommandSourceStack source, Entity sourceEntity, int sourceSlot, Collection<? extends Entity> targetEntities, int targetSlot, Holder<LootItemFunction> modifier) throws CommandSyntaxException {
        return setEntityItem(source, targetEntities, targetSlot, applyModifier(source, modifier, getItemInSlot(sourceEntity, sourceSlot)));
    }

    private static ItemStack applyModifier(CommandSourceStack source, Holder<LootItemFunction> modifier, ItemStack item) {
        ServerLevel serverlevel = source.getLevel();
        LootParams lootparams = (new LootParams.Builder(serverlevel)).withParameter(LootContextParams.ORIGIN, source.getPosition()).withOptionalParameter(LootContextParams.THIS_ENTITY, source.getEntity()).create(LootContextParamSets.COMMAND);
        LootContext lootcontext = (new LootContext.Builder(lootparams)).create(Optional.empty());

        lootcontext.pushVisitedElement(LootContext.createVisitedEntry(modifier.value()));
        ItemStack itemstack1 = (ItemStack) ((LootItemFunction) modifier.value()).apply(item, lootcontext);

        itemstack1.limitSize(itemstack1.getMaxStackSize());
        return itemstack1;
    }

    private static ItemStack getItemInSlot(SlotProvider slotProvider, int slot) throws CommandSyntaxException {
        SlotAccess slotaccess = slotProvider.getSlot(slot);

        if (slotaccess == null) {
            throw ItemCommands.ERROR_SOURCE_INAPPLICABLE_SLOT.create(slot);
        } else {
            return slotaccess.get().copy();
        }
    }

    private static ItemStack getBlockItem(CommandSourceStack source, BlockPos pos, int slot) throws CommandSyntaxException {
        Container container = getContainer(source, pos, ItemCommands.ERROR_SOURCE_NOT_A_CONTAINER);

        return getItemInSlot(container, slot);
    }
}
