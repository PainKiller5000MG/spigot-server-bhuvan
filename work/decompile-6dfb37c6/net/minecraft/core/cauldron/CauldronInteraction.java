package net.minecraft.core.cauldron;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;

public interface CauldronInteraction {

    Map<String, CauldronInteraction.InteractionMap> INTERACTIONS = new Object2ObjectArrayMap();
    Codec<CauldronInteraction.InteractionMap> CODEC;
    CauldronInteraction.InteractionMap EMPTY;
    CauldronInteraction.InteractionMap WATER;
    CauldronInteraction.InteractionMap LAVA;
    CauldronInteraction.InteractionMap POWDER_SNOW;

    static CauldronInteraction.InteractionMap newInteractionMap(String name) {
        Object2ObjectOpenHashMap<Item, CauldronInteraction> object2objectopenhashmap = new Object2ObjectOpenHashMap();

        object2objectopenhashmap.defaultReturnValue((CauldronInteraction) (blockstate, level, blockpos, player, interactionhand, itemstack) -> {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        });
        CauldronInteraction.InteractionMap cauldroninteraction_interactionmap = new CauldronInteraction.InteractionMap(name, object2objectopenhashmap);

        CauldronInteraction.INTERACTIONS.put(name, cauldroninteraction_interactionmap);
        return cauldroninteraction_interactionmap;
    }

    InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack itemInHand);

    static void bootStrap() {
        Map<Item, CauldronInteraction> map = CauldronInteraction.EMPTY.map();

        addDefaultInteractions(map);
        map.put(Items.POTION, (CauldronInteraction) (blockstate, level, blockpos, player, interactionhand, itemstack) -> {
            PotionContents potioncontents = (PotionContents) itemstack.get(DataComponents.POTION_CONTENTS);

            if (potioncontents != null && potioncontents.is(Potions.WATER)) {
                if (!level.isClientSide()) {
                    Item item = itemstack.getItem();

                    player.setItemInHand(interactionhand, ItemUtils.createFilledResult(itemstack, player, new ItemStack(Items.GLASS_BOTTLE)));
                    player.awardStat(Stats.USE_CAULDRON);
                    player.awardStat(Stats.ITEM_USED.get(item));
                    level.setBlockAndUpdate(blockpos, Blocks.WATER_CAULDRON.defaultBlockState());
                    level.playSound((Entity) null, blockpos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                    level.gameEvent((Entity) null, (Holder) GameEvent.FLUID_PLACE, blockpos);
                }

                return InteractionResult.SUCCESS;
            } else {
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            }
        });
        Map<Item, CauldronInteraction> map1 = CauldronInteraction.WATER.map();

        addDefaultInteractions(map1);
        map1.put(Items.BUCKET, (CauldronInteraction) (blockstate, level, blockpos, player, interactionhand, itemstack) -> {
            return fillBucket(blockstate, level, blockpos, player, interactionhand, itemstack, new ItemStack(Items.WATER_BUCKET), (blockstate1) -> {
                return (Integer) blockstate1.getValue(LayeredCauldronBlock.LEVEL) == 3;
            }, SoundEvents.BUCKET_FILL);
        });
        map1.put(Items.GLASS_BOTTLE, (CauldronInteraction) (blockstate, level, blockpos, player, interactionhand, itemstack) -> {
            if (!level.isClientSide()) {
                Item item = itemstack.getItem();

                player.setItemInHand(interactionhand, ItemUtils.createFilledResult(itemstack, player, PotionContents.createItemStack(Items.POTION, Potions.WATER)));
                player.awardStat(Stats.USE_CAULDRON);
                player.awardStat(Stats.ITEM_USED.get(item));
                LayeredCauldronBlock.lowerFillLevel(blockstate, level, blockpos);
                level.playSound((Entity) null, blockpos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.gameEvent((Entity) null, (Holder) GameEvent.FLUID_PICKUP, blockpos);
            }

            return InteractionResult.SUCCESS;
        });
        map1.put(Items.POTION, (CauldronInteraction) (blockstate, level, blockpos, player, interactionhand, itemstack) -> {
            if ((Integer) blockstate.getValue(LayeredCauldronBlock.LEVEL) == 3) {
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            } else {
                PotionContents potioncontents = (PotionContents) itemstack.get(DataComponents.POTION_CONTENTS);

                if (potioncontents != null && potioncontents.is(Potions.WATER)) {
                    if (!level.isClientSide()) {
                        player.setItemInHand(interactionhand, ItemUtils.createFilledResult(itemstack, player, new ItemStack(Items.GLASS_BOTTLE)));
                        player.awardStat(Stats.USE_CAULDRON);
                        player.awardStat(Stats.ITEM_USED.get(itemstack.getItem()));
                        level.setBlockAndUpdate(blockpos, (BlockState) blockstate.cycle(LayeredCauldronBlock.LEVEL));
                        level.playSound((Entity) null, blockpos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                        level.gameEvent((Entity) null, (Holder) GameEvent.FLUID_PLACE, blockpos);
                    }

                    return InteractionResult.SUCCESS;
                } else {
                    return InteractionResult.TRY_WITH_EMPTY_HAND;
                }
            }
        });
        map1.put(Items.LEATHER_BOOTS, CauldronInteraction::dyedItemIteration);
        map1.put(Items.LEATHER_LEGGINGS, CauldronInteraction::dyedItemIteration);
        map1.put(Items.LEATHER_CHESTPLATE, CauldronInteraction::dyedItemIteration);
        map1.put(Items.LEATHER_HELMET, CauldronInteraction::dyedItemIteration);
        map1.put(Items.LEATHER_HORSE_ARMOR, CauldronInteraction::dyedItemIteration);
        map1.put(Items.WOLF_ARMOR, CauldronInteraction::dyedItemIteration);
        map1.put(Items.WHITE_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.GRAY_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.BLACK_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.BLUE_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.BROWN_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.CYAN_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.GREEN_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.LIGHT_BLUE_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.LIGHT_GRAY_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.LIME_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.MAGENTA_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.ORANGE_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.PINK_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.PURPLE_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.RED_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.YELLOW_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.WHITE_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.GRAY_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.BLACK_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.BLUE_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.BROWN_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.CYAN_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.GREEN_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.LIGHT_BLUE_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.LIGHT_GRAY_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.LIME_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.MAGENTA_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.ORANGE_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.PINK_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.PURPLE_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.RED_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.YELLOW_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        Map<Item, CauldronInteraction> map2 = CauldronInteraction.LAVA.map();

        map2.put(Items.BUCKET, (CauldronInteraction) (blockstate, level, blockpos, player, interactionhand, itemstack) -> {
            return fillBucket(blockstate, level, blockpos, player, interactionhand, itemstack, new ItemStack(Items.LAVA_BUCKET), (blockstate1) -> {
                return true;
            }, SoundEvents.BUCKET_FILL_LAVA);
        });
        addDefaultInteractions(map2);
        Map<Item, CauldronInteraction> map3 = CauldronInteraction.POWDER_SNOW.map();

        map3.put(Items.BUCKET, (CauldronInteraction) (blockstate, level, blockpos, player, interactionhand, itemstack) -> {
            return fillBucket(blockstate, level, blockpos, player, interactionhand, itemstack, new ItemStack(Items.POWDER_SNOW_BUCKET), (blockstate1) -> {
                return (Integer) blockstate1.getValue(LayeredCauldronBlock.LEVEL) == 3;
            }, SoundEvents.BUCKET_FILL_POWDER_SNOW);
        });
        addDefaultInteractions(map3);
    }

    static void addDefaultInteractions(Map<Item, CauldronInteraction> interactionMap) {
        interactionMap.put(Items.LAVA_BUCKET, CauldronInteraction::fillLavaInteraction);
        interactionMap.put(Items.WATER_BUCKET, CauldronInteraction::fillWaterInteraction);
        interactionMap.put(Items.POWDER_SNOW_BUCKET, CauldronInteraction::fillPowderSnowInteraction);
    }

    static InteractionResult fillBucket(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack itemInHand, ItemStack newItem, Predicate<BlockState> canFill, SoundEvent soundEvent) {
        if (!canFill.test(state)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        } else {
            if (!level.isClientSide()) {
                Item item = itemInHand.getItem();

                player.setItemInHand(hand, ItemUtils.createFilledResult(itemInHand, player, newItem));
                player.awardStat(Stats.USE_CAULDRON);
                player.awardStat(Stats.ITEM_USED.get(item));
                level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
                level.playSound((Entity) null, pos, soundEvent, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.gameEvent((Entity) null, (Holder) GameEvent.FLUID_PICKUP, pos);
            }

            return InteractionResult.SUCCESS;
        }
    }

    static InteractionResult emptyBucket(Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack itemInHand, BlockState newState, SoundEvent soundEvent) {
        if (!level.isClientSide()) {
            Item item = itemInHand.getItem();

            player.setItemInHand(hand, ItemUtils.createFilledResult(itemInHand, player, new ItemStack(Items.BUCKET)));
            player.awardStat(Stats.FILL_CAULDRON);
            player.awardStat(Stats.ITEM_USED.get(item));
            level.setBlockAndUpdate(pos, newState);
            level.playSound((Entity) null, pos, soundEvent, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent((Entity) null, (Holder) GameEvent.FLUID_PLACE, pos);
        }

        return InteractionResult.SUCCESS;
    }

    private static InteractionResult fillWaterInteraction(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack itemInHand) {
        return emptyBucket(level, pos, player, hand, itemInHand, (BlockState) Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), SoundEvents.BUCKET_EMPTY);
    }

    private static InteractionResult fillLavaInteraction(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack itemInHand) {
        return (InteractionResult) (isUnderWater(level, pos) ? InteractionResult.CONSUME : emptyBucket(level, pos, player, hand, itemInHand, Blocks.LAVA_CAULDRON.defaultBlockState(), SoundEvents.BUCKET_EMPTY_LAVA));
    }

    private static InteractionResult fillPowderSnowInteraction(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack itemInHand) {
        return (InteractionResult) (isUnderWater(level, pos) ? InteractionResult.CONSUME : emptyBucket(level, pos, player, hand, itemInHand, (BlockState) Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), SoundEvents.BUCKET_EMPTY_POWDER_SNOW));
    }

    private static InteractionResult shulkerBoxInteraction(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack itemInHand) {
        Block block = Block.byItem(itemInHand.getItem());

        if (!(block instanceof ShulkerBoxBlock)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        } else {
            if (!level.isClientSide()) {
                ItemStack itemstack1 = itemInHand.transmuteCopy(Blocks.SHULKER_BOX, 1);

                player.setItemInHand(hand, ItemUtils.createFilledResult(itemInHand, player, itemstack1, false));
                player.awardStat(Stats.CLEAN_SHULKER_BOX);
                LayeredCauldronBlock.lowerFillLevel(state, level, pos);
            }

            return InteractionResult.SUCCESS;
        }
    }

    private static InteractionResult bannerInteraction(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack itemInHand) {
        BannerPatternLayers bannerpatternlayers = (BannerPatternLayers) itemInHand.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);

        if (bannerpatternlayers.layers().isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        } else {
            if (!level.isClientSide()) {
                ItemStack itemstack1 = itemInHand.copyWithCount(1);

                itemstack1.set(DataComponents.BANNER_PATTERNS, bannerpatternlayers.removeLast());
                player.setItemInHand(hand, ItemUtils.createFilledResult(itemInHand, player, itemstack1, false));
                player.awardStat(Stats.CLEAN_BANNER);
                LayeredCauldronBlock.lowerFillLevel(state, level, pos);
            }

            return InteractionResult.SUCCESS;
        }
    }

    private static InteractionResult dyedItemIteration(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack itemInHand) {
        if (!itemInHand.is(ItemTags.DYEABLE)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        } else if (!itemInHand.has(DataComponents.DYED_COLOR)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        } else {
            if (!level.isClientSide()) {
                itemInHand.remove(DataComponents.DYED_COLOR);
                player.awardStat(Stats.CLEAN_ARMOR);
                LayeredCauldronBlock.lowerFillLevel(state, level, pos);
            }

            return InteractionResult.SUCCESS;
        }
    }

    private static boolean isUnderWater(Level level, BlockPos pos) {
        FluidState fluidstate = level.getFluidState(pos.above());

        return fluidstate.is(FluidTags.WATER);
    }

    static {
        Function function = CauldronInteraction.InteractionMap::name;
        Map map = CauldronInteraction.INTERACTIONS;

        Objects.requireNonNull(map);
        CODEC = Codec.stringResolver(function, map::get);
        EMPTY = newInteractionMap("empty");
        WATER = newInteractionMap("water");
        LAVA = newInteractionMap("lava");
        POWDER_SNOW = newInteractionMap("powder_snow");
    }

    public static record InteractionMap(String name, Map<Item, CauldronInteraction> map) {

    }
}
