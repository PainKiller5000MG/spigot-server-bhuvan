package net.minecraft.world.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class EmptyMapItem extends Item {

    public EmptyMapItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (level instanceof ServerLevel serverlevel) {
            itemstack.consume(1, player);
            player.awardStat(Stats.ITEM_USED.get(this));
            serverlevel.playSound((Entity) null, (Entity) player, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, player.getSoundSource(), 1.0F, 1.0F);
            ItemStack itemstack1 = MapItem.create(serverlevel, player.getBlockX(), player.getBlockZ(), (byte) 0, true, false);

            if (itemstack.isEmpty()) {
                return InteractionResult.SUCCESS.heldItemTransformedTo(itemstack1);
            } else {
                if (!player.getInventory().add(itemstack1.copy())) {
                    player.drop(itemstack1, false);
                }

                return InteractionResult.SUCCESS;
            }
        } else {
            return InteractionResult.SUCCESS;
        }
    }
}
