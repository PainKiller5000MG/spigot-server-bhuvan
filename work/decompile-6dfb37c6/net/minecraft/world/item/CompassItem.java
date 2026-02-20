package net.minecraft.world.item;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

public class CompassItem extends Item {

    private static final Component LODESTONE_COMPASS_NAME = Component.translatable("item.minecraft.lodestone_compass");

    public CompassItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack itemStack) {
        return itemStack.has(DataComponents.LODESTONE_TRACKER) || super.isFoil(itemStack);
    }

    @Override
    public void inventoryTick(ItemStack itemStack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        LodestoneTracker lodestonetracker = (LodestoneTracker) itemStack.get(DataComponents.LODESTONE_TRACKER);

        if (lodestonetracker != null) {
            LodestoneTracker lodestonetracker1 = lodestonetracker.tick(level);

            if (lodestonetracker1 != lodestonetracker) {
                itemStack.set(DataComponents.LODESTONE_TRACKER, lodestonetracker1);
            }
        }

    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos blockpos = context.getClickedPos();
        Level level = context.getLevel();

        if (!level.getBlockState(blockpos).is(Blocks.LODESTONE)) {
            return super.useOn(context);
        } else {
            level.playSound((Entity) null, blockpos, SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
            Player player = context.getPlayer();
            ItemStack itemstack = context.getItemInHand();
            boolean flag = !player.hasInfiniteMaterials() && itemstack.getCount() == 1;
            LodestoneTracker lodestonetracker = new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), blockpos)), true);

            if (flag) {
                itemstack.set(DataComponents.LODESTONE_TRACKER, lodestonetracker);
            } else {
                ItemStack itemstack1 = itemstack.transmuteCopy(Items.COMPASS, 1);

                itemstack.consume(1, player);
                itemstack1.set(DataComponents.LODESTONE_TRACKER, lodestonetracker);
                if (!player.getInventory().add(itemstack1)) {
                    player.drop(itemstack1, false);
                }
            }

            return InteractionResult.SUCCESS;
        }
    }

    @Override
    public Component getName(ItemStack itemStack) {
        return itemStack.has(DataComponents.LODESTONE_TRACKER) ? CompassItem.LODESTONE_COMPASS_NAME : super.getName(itemStack);
    }
}
