package net.minecraft.world.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class ScaffoldingBlockItem extends BlockItem {

    public ScaffoldingBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public @Nullable BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
        BlockPos blockpos = context.getClickedPos();
        Level level = context.getLevel();
        BlockState blockstate = level.getBlockState(blockpos);
        Block block = this.getBlock();

        if (!blockstate.is(block)) {
            return ScaffoldingBlock.getDistance(level, blockpos) == 7 ? null : context;
        } else {
            Direction direction;

            if (context.isSecondaryUseActive()) {
                direction = context.isInside() ? context.getClickedFace().getOpposite() : context.getClickedFace();
            } else {
                direction = context.getClickedFace() == Direction.UP ? context.getHorizontalDirection() : Direction.UP;
            }

            int i = 0;
            BlockPos.MutableBlockPos blockpos_mutableblockpos = blockpos.mutable().move(direction);

            while (i < 7) {
                if (!level.isClientSide() && !level.isInWorldBounds(blockpos_mutableblockpos)) {
                    Player player = context.getPlayer();
                    int j = level.getMaxY();

                    if (player instanceof ServerPlayer && blockpos_mutableblockpos.getY() > j) {
                        ((ServerPlayer) player).sendSystemMessage(Component.translatable("build.tooHigh", j).withStyle(ChatFormatting.RED), true);
                    }
                    break;
                }

                blockstate = level.getBlockState(blockpos_mutableblockpos);
                if (!blockstate.is(this.getBlock())) {
                    if (blockstate.canBeReplaced(context)) {
                        return BlockPlaceContext.at(context, blockpos_mutableblockpos, direction);
                    }
                    break;
                }

                blockpos_mutableblockpos.move(direction);
                if (direction.getAxis().isHorizontal()) {
                    ++i;
                }
            }

            return null;
        }
    }

    @Override
    protected boolean mustSurvive() {
        return false;
    }
}
