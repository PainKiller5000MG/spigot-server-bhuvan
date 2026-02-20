package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;

public class WorkAtComposter extends WorkAtPoi {

    private static final List<Item> COMPOSTABLE_ITEMS = ImmutableList.of(Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS);

    public WorkAtComposter() {}

    @Override
    protected void useWorkstation(ServerLevel level, Villager body) {
        Optional<GlobalPos> optional = body.getBrain().<GlobalPos>getMemory(MemoryModuleType.JOB_SITE);

        if (!optional.isEmpty()) {
            GlobalPos globalpos = (GlobalPos) optional.get();
            BlockState blockstate = level.getBlockState(globalpos.pos());

            if (blockstate.is(Blocks.COMPOSTER)) {
                this.makeBread(level, body);
                this.compostItems(level, body, globalpos, blockstate);
            }

        }
    }

    private void compostItems(ServerLevel level, Villager body, GlobalPos jobSitePos, BlockState blockState) {
        BlockPos blockpos = jobSitePos.pos();

        if ((Integer) blockState.getValue(ComposterBlock.LEVEL) == 8) {
            blockState = ComposterBlock.extractProduce(body, blockState, level, blockpos);
        }

        int i = 20;
        int j = 10;
        int[] aint = new int[WorkAtComposter.COMPOSTABLE_ITEMS.size()];
        SimpleContainer simplecontainer = body.getInventory();
        int k = simplecontainer.getContainerSize();
        BlockState blockstate1 = blockState;

        for (int l = k - 1; l >= 0 && i > 0; --l) {
            ItemStack itemstack = simplecontainer.getItem(l);
            int i1 = WorkAtComposter.COMPOSTABLE_ITEMS.indexOf(itemstack.getItem());

            if (i1 != -1) {
                int j1 = itemstack.getCount();
                int k1 = aint[i1] + j1;

                aint[i1] = k1;
                int l1 = Math.min(Math.min(k1 - 10, i), j1);

                if (l1 > 0) {
                    i -= l1;

                    for (int i2 = 0; i2 < l1; ++i2) {
                        blockstate1 = ComposterBlock.insertItem(body, blockstate1, level, itemstack, blockpos);
                        if ((Integer) blockstate1.getValue(ComposterBlock.LEVEL) == 7) {
                            this.spawnComposterFillEffects(level, blockState, blockpos, blockstate1);
                            return;
                        }
                    }
                }
            }
        }

        this.spawnComposterFillEffects(level, blockState, blockpos, blockstate1);
    }

    private void spawnComposterFillEffects(ServerLevel level, BlockState blockState, BlockPos pos, BlockState newState) {
        level.levelEvent(1500, pos, newState != blockState ? 1 : 0);
    }

    private void makeBread(ServerLevel level, Villager body) {
        SimpleContainer simplecontainer = body.getInventory();

        if (simplecontainer.countItem(Items.BREAD) <= 36) {
            int i = simplecontainer.countItem(Items.WHEAT);
            int j = 3;
            int k = 3;
            int l = Math.min(3, i / 3);

            if (l != 0) {
                int i1 = l * 3;

                simplecontainer.removeItemType(Items.WHEAT, i1);
                ItemStack itemstack = simplecontainer.addItem(new ItemStack(Items.BREAD, l));

                if (!itemstack.isEmpty()) {
                    body.spawnAtLocation(level, itemstack, 0.5F);
                }

            }
        }
    }
}
