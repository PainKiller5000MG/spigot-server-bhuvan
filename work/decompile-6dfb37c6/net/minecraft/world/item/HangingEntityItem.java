package net.minecraft.world.item;

import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

public class HangingEntityItem extends Item {

    private static final Component TOOLTIP_RANDOM_VARIANT = Component.translatable("painting.random").withStyle(ChatFormatting.GRAY);
    private final EntityType<? extends HangingEntity> type;

    public HangingEntityItem(EntityType<? extends HangingEntity> type, Item.Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos blockpos = context.getClickedPos();
        Direction direction = context.getClickedFace();
        BlockPos blockpos1 = blockpos.relative(direction);
        Player player = context.getPlayer();
        ItemStack itemstack = context.getItemInHand();

        if (player != null && !this.mayPlace(player, direction, itemstack, blockpos1)) {
            return InteractionResult.FAIL;
        } else {
            Level level = context.getLevel();
            HangingEntity hangingentity;

            if (this.type == EntityType.PAINTING) {
                Optional<Painting> optional = Painting.create(level, blockpos1, direction);

                if (optional.isEmpty()) {
                    return InteractionResult.CONSUME;
                }

                hangingentity = (HangingEntity) optional.get();
            } else if (this.type == EntityType.ITEM_FRAME) {
                hangingentity = new ItemFrame(level, blockpos1, direction);
            } else {
                if (this.type != EntityType.GLOW_ITEM_FRAME) {
                    return InteractionResult.SUCCESS;
                }

                hangingentity = new GlowItemFrame(level, blockpos1, direction);
            }

            EntityType.createDefaultStackConfig(level, itemstack, player).accept(hangingentity);
            if (hangingentity.survives()) {
                if (!level.isClientSide()) {
                    hangingentity.playPlacementSound();
                    level.gameEvent(player, (Holder) GameEvent.ENTITY_PLACE, hangingentity.position());
                    level.addFreshEntity(hangingentity);
                }

                itemstack.shrink(1);
                return InteractionResult.SUCCESS;
            } else {
                return InteractionResult.CONSUME;
            }
        }
    }

    protected boolean mayPlace(Player player, Direction direction, ItemStack itemStack, BlockPos blockPos) {
        return !direction.getAxis().isVertical() && player.mayUseItemAt(blockPos, direction, itemStack);
    }

    @Override
    public void appendHoverText(ItemStack itemStack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
        if (this.type == EntityType.PAINTING && display.shows(DataComponents.PAINTING_VARIANT)) {
            Holder<PaintingVariant> holder = (Holder) itemStack.get(DataComponents.PAINTING_VARIANT);

            if (holder != null) {
                ((PaintingVariant) holder.value()).title().ifPresent(builder);
                ((PaintingVariant) holder.value()).author().ifPresent(builder);
                builder.accept(Component.translatable("painting.dimensions", ((PaintingVariant) holder.value()).width(), ((PaintingVariant) holder.value()).height()));
            } else if (tooltipFlag.isCreative()) {
                builder.accept(HangingEntityItem.TOOLTIP_RANDOM_VARIANT);
            }
        }

    }
}
