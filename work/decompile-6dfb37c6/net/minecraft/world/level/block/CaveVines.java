package net.minecraft.world.level.block;

import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface CaveVines {

    VoxelShape SHAPE = Block.column(14.0D, 0.0D, 16.0D);
    BooleanProperty BERRIES = BlockStateProperties.BERRIES;

    static InteractionResult use(Entity sourceEntity, BlockState state, Level level, BlockPos pos) {
        if ((Boolean) state.getValue(CaveVines.BERRIES)) {
            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                Block.dropFromBlockInteractLootTable(serverlevel, BuiltInLootTables.HARVEST_CAVE_VINE, state, level.getBlockEntity(pos), (ItemStack) null, sourceEntity, (serverlevel1, itemstack) -> {
                    Block.popResource(serverlevel1, pos, itemstack);
                });
                float f = Mth.randomBetween(serverlevel.random, 0.8F, 1.2F);

                serverlevel.playSound((Entity) null, pos, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, f);
                BlockState blockstate1 = (BlockState) state.setValue(CaveVines.BERRIES, false);

                serverlevel.setBlock(pos, blockstate1, 2);
                serverlevel.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(sourceEntity, blockstate1));
            }

            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.PASS;
        }
    }

    static boolean hasGlowBerries(BlockState state) {
        return state.hasProperty(CaveVines.BERRIES) && (Boolean) state.getValue(CaveVines.BERRIES);
    }

    static ToIntFunction<BlockState> emission(int lightEmission) {
        return (blockstate) -> {
            return (Boolean) blockstate.getValue(BlockStateProperties.BERRIES) ? lightEmission : 0;
        };
    }
}
