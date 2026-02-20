package net.minecraft.world.item;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public class LeadItem extends Item {

    public LeadItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos blockpos = context.getClickedPos();
        BlockState blockstate = level.getBlockState(blockpos);

        if (blockstate.is(BlockTags.FENCES)) {
            Player player = context.getPlayer();

            if (!level.isClientSide() && player != null) {
                return bindPlayerMobs(player, level, blockpos);
            }
        }

        return InteractionResult.PASS;
    }

    public static InteractionResult bindPlayerMobs(Player player, Level level, BlockPos pos) {
        LeashFenceKnotEntity leashfenceknotentity = null;
        List<Leashable> list = Leashable.leashableInArea(level, Vec3.atCenterOf(pos), (leashable) -> {
            return leashable.getLeashHolder() == player;
        });
        boolean flag = false;

        for (Leashable leashable : list) {
            if (leashfenceknotentity == null) {
                leashfenceknotentity = LeashFenceKnotEntity.getOrCreateKnot(level, pos);
                leashfenceknotentity.playPlacementSound();
            }

            if (leashable.canHaveALeashAttachedTo(leashfenceknotentity)) {
                leashable.setLeashedTo(leashfenceknotentity, true);
                flag = true;
            }
        }

        if (flag) {
            level.gameEvent(GameEvent.BLOCK_ATTACH, pos, GameEvent.Context.of((Entity) player));
            return InteractionResult.SUCCESS_SERVER;
        } else {
            return InteractionResult.PASS;
        }
    }
}
