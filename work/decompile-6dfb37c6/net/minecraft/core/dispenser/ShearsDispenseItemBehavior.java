package net.minecraft.core.dispenser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

public class ShearsDispenseItemBehavior extends OptionalDispenseItemBehavior {

    public ShearsDispenseItemBehavior() {}

    @Override
    protected ItemStack execute(BlockSource source, ItemStack dispensed) {
        ServerLevel serverlevel = source.level();

        if (!serverlevel.isClientSide()) {
            BlockPos blockpos = source.pos().relative((Direction) source.state().getValue(DispenserBlock.FACING));

            this.setSuccess(tryShearBeehive(serverlevel, dispensed, blockpos) || tryShearEntity(serverlevel, blockpos, dispensed));
            if (this.isSuccess()) {
                dispensed.hurtAndBreak(1, serverlevel, (ServerPlayer) null, (item) -> {
                });
            }
        }

        return dispensed;
    }

    private static boolean tryShearBeehive(ServerLevel level, ItemStack tool, BlockPos pos) {
        BlockState blockstate = level.getBlockState(pos);

        if (blockstate.is(BlockTags.BEEHIVES, (blockbehaviour_blockstatebase) -> {
            return blockbehaviour_blockstatebase.hasProperty(BeehiveBlock.HONEY_LEVEL) && blockbehaviour_blockstatebase.getBlock() instanceof BeehiveBlock;
        })) {
            int i = (Integer) blockstate.getValue(BeehiveBlock.HONEY_LEVEL);

            if (i >= 5) {
                level.playSound((Entity) null, pos, SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
                BeehiveBlock.dropHoneycomb(level, tool, blockstate, level.getBlockEntity(pos), (Entity) null, pos);
                ((BeehiveBlock) blockstate.getBlock()).releaseBeesAndResetHoneyLevel(level, blockstate, pos, (Player) null, BeehiveBlockEntity.BeeReleaseStatus.BEE_RELEASED);
                level.gameEvent((Entity) null, (Holder) GameEvent.SHEAR, pos);
                return true;
            }
        }

        return false;
    }

    private static boolean tryShearEntity(ServerLevel level, BlockPos pos, ItemStack tool) {
        for (Entity entity : level.getEntitiesOfClass(Entity.class, new AABB(pos), EntitySelector.NO_SPECTATORS)) {
            if (entity.shearOffAllLeashConnections((Player) null)) {
                return true;
            }

            if (entity instanceof Shearable shearable) {
                if (shearable.readyForShearing()) {
                    shearable.shear(level, SoundSource.BLOCKS, tool);
                    level.gameEvent((Entity) null, (Holder) GameEvent.SHEAR, pos);
                    return true;
                }
            }
        }

        return false;
    }
}
