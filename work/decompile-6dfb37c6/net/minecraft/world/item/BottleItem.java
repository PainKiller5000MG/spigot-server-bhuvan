package net.minecraft.world.item;

import java.util.List;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class BottleItem extends Item {

    public BottleItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        List<AreaEffectCloud> list = level.<AreaEffectCloud>getEntitiesOfClass(AreaEffectCloud.class, player.getBoundingBox().inflate(2.0D), (areaeffectcloud) -> {
            return areaeffectcloud.isAlive() && areaeffectcloud.getOwner() instanceof EnderDragon;
        });
        ItemStack itemstack = player.getItemInHand(hand);

        if (!list.isEmpty()) {
            AreaEffectCloud areaeffectcloud = (AreaEffectCloud) list.get(0);

            areaeffectcloud.setRadius(areaeffectcloud.getRadius() - 0.5F);
            level.playSound((Entity) null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL_DRAGONBREATH, SoundSource.NEUTRAL, 1.0F, 1.0F);
            level.gameEvent(player, (Holder) GameEvent.FLUID_PICKUP, player.position());
            if (player instanceof ServerPlayer) {
                ServerPlayer serverplayer = (ServerPlayer) player;

                CriteriaTriggers.PLAYER_INTERACTED_WITH_ENTITY.trigger(serverplayer, itemstack, areaeffectcloud);
            }

            return InteractionResult.SUCCESS.heldItemTransformedTo(this.turnBottleIntoItem(itemstack, player, new ItemStack(Items.DRAGON_BREATH)));
        } else {
            BlockHitResult blockhitresult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);

            if (blockhitresult.getType() == HitResult.Type.MISS) {
                return InteractionResult.PASS;
            } else {
                if (blockhitresult.getType() == HitResult.Type.BLOCK) {
                    BlockPos blockpos = blockhitresult.getBlockPos();

                    if (!level.mayInteract(player, blockpos)) {
                        return InteractionResult.PASS;
                    }

                    if (level.getFluidState(blockpos).is(FluidTags.WATER)) {
                        level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1.0F, 1.0F);
                        level.gameEvent(player, (Holder) GameEvent.FLUID_PICKUP, blockpos);
                        return InteractionResult.SUCCESS.heldItemTransformedTo(this.turnBottleIntoItem(itemstack, player, PotionContents.createItemStack(Items.POTION, Potions.WATER)));
                    }
                }

                return InteractionResult.PASS;
            }
        }
    }

    protected ItemStack turnBottleIntoItem(ItemStack itemStack, Player player, ItemStack itemStackToTurnInto) {
        player.awardStat(Stats.ITEM_USED.get(this));
        return ItemUtils.createFilledResult(itemStack, player, itemStackToTurnInto);
    }
}
