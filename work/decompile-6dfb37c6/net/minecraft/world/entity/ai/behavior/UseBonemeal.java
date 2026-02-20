package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

public class UseBonemeal extends Behavior<Villager> {

    private static final int BONEMEALING_DURATION = 80;
    private long nextWorkCycleTime;
    private long lastBonemealingSession;
    private int timeWorkedSoFar;
    private Optional<BlockPos> cropPos = Optional.empty();

    public UseBonemeal() {
        super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
    }

    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (body.tickCount % 10 == 0 && (this.lastBonemealingSession == 0L || this.lastBonemealingSession + 160L <= (long) body.tickCount)) {
            if (body.getInventory().countItem(Items.BONE_MEAL) <= 0) {
                return false;
            } else {
                this.cropPos = this.pickNextTarget(level, body);
                return this.cropPos.isPresent();
            }
        } else {
            return false;
        }
    }

    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return this.timeWorkedSoFar < 80 && this.cropPos.isPresent();
    }

    private Optional<BlockPos> pickNextTarget(ServerLevel level, Villager body) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
        Optional<BlockPos> optional = Optional.empty();
        int i = 0;

        for (int j = -1; j <= 1; ++j) {
            for (int k = -1; k <= 1; ++k) {
                for (int l = -1; l <= 1; ++l) {
                    blockpos_mutableblockpos.setWithOffset(body.blockPosition(), j, k, l);
                    if (this.validPos(blockpos_mutableblockpos, level)) {
                        ++i;
                        if (level.random.nextInt(i) == 0) {
                            optional = Optional.of(blockpos_mutableblockpos.immutable());
                        }
                    }
                }
            }
        }

        return optional;
    }

    private boolean validPos(BlockPos blockPos, ServerLevel level) {
        BlockState blockstate = level.getBlockState(blockPos);
        Block block = blockstate.getBlock();

        return block instanceof CropBlock && !((CropBlock) block).isMaxAge(blockstate);
    }

    protected void start(ServerLevel level, Villager body, long timestamp) {
        this.setCurrentCropAsTarget(body);
        body.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BONE_MEAL));
        this.nextWorkCycleTime = timestamp;
        this.timeWorkedSoFar = 0;
    }

    private void setCurrentCropAsTarget(Villager body) {
        this.cropPos.ifPresent((blockpos) -> {
            BlockPosTracker blockpostracker = new BlockPosTracker(blockpos);

            body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, blockpostracker);
            body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(blockpostracker, 0.5F, 1));
        });
    }

    protected void stop(ServerLevel level, Villager body, long timestamp) {
        body.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        this.lastBonemealingSession = (long) body.tickCount;
    }

    protected void tick(ServerLevel level, Villager body, long timestamp) {
        BlockPos blockpos = (BlockPos) this.cropPos.get();

        if (timestamp >= this.nextWorkCycleTime && blockpos.closerToCenterThan(body.position(), 1.0D)) {
            ItemStack itemstack = ItemStack.EMPTY;
            SimpleContainer simplecontainer = body.getInventory();
            int j = simplecontainer.getContainerSize();

            for (int k = 0; k < j; ++k) {
                ItemStack itemstack1 = simplecontainer.getItem(k);

                if (itemstack1.is(Items.BONE_MEAL)) {
                    itemstack = itemstack1;
                    break;
                }
            }

            if (!itemstack.isEmpty() && BoneMealItem.growCrop(itemstack, level, blockpos)) {
                level.levelEvent(1505, blockpos, 15);
                this.cropPos = this.pickNextTarget(level, body);
                this.setCurrentCropAsTarget(body);
                this.nextWorkCycleTime = timestamp + 40L;
            }

            ++this.timeWorkedSoFar;
        }
    }
}
