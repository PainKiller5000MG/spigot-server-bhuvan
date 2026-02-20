package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

public class HarvestFarmland extends Behavior<Villager> {

    private static final int HARVEST_DURATION = 200;
    public static final float SPEED_MODIFIER = 0.5F;
    private @Nullable BlockPos aboveFarmlandPos;
    private long nextOkStartTime;
    private int timeWorkedSoFar;
    private final List<BlockPos> validFarmlandAroundVillager = Lists.newArrayList();

    public HarvestFarmland() {
        super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.SECONDARY_JOB_SITE, MemoryStatus.VALUE_PRESENT));
    }

    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (!(Boolean) level.getGameRules().get(GameRules.MOB_GRIEFING)) {
            return false;
        } else if (!body.getVillagerData().profession().is(VillagerProfession.FARMER)) {
            return false;
        } else {
            BlockPos.MutableBlockPos blockpos_mutableblockpos = body.blockPosition().mutable();

            this.validFarmlandAroundVillager.clear();

            for (int i = -1; i <= 1; ++i) {
                for (int j = -1; j <= 1; ++j) {
                    for (int k = -1; k <= 1; ++k) {
                        blockpos_mutableblockpos.set(body.getX() + (double) i, body.getY() + (double) j, body.getZ() + (double) k);
                        if (this.validPos(blockpos_mutableblockpos, level)) {
                            this.validFarmlandAroundVillager.add(new BlockPos(blockpos_mutableblockpos));
                        }
                    }
                }
            }

            this.aboveFarmlandPos = this.getValidFarmland(level);
            return this.aboveFarmlandPos != null;
        }
    }

    private @Nullable BlockPos getValidFarmland(ServerLevel level) {
        return this.validFarmlandAroundVillager.isEmpty() ? null : (BlockPos) this.validFarmlandAroundVillager.get(level.getRandom().nextInt(this.validFarmlandAroundVillager.size()));
    }

    private boolean validPos(BlockPos blockPos, ServerLevel level) {
        BlockState blockstate = level.getBlockState(blockPos);
        Block block = blockstate.getBlock();
        Block block1 = level.getBlockState(blockPos.below()).getBlock();

        return block instanceof CropBlock && ((CropBlock) block).isMaxAge(blockstate) || blockstate.isAir() && block1 instanceof FarmBlock;
    }

    protected void start(ServerLevel level, Villager body, long timestamp) {
        if (timestamp > this.nextOkStartTime && this.aboveFarmlandPos != null) {
            body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(this.aboveFarmlandPos));
            body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(this.aboveFarmlandPos), 0.5F, 1));
        }

    }

    protected void stop(ServerLevel level, Villager body, long timestamp) {
        body.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        this.timeWorkedSoFar = 0;
        this.nextOkStartTime = timestamp + 40L;
    }

    protected void tick(ServerLevel level, Villager body, long timestamp) {
        if (this.aboveFarmlandPos == null || this.aboveFarmlandPos.closerToCenterThan(body.position(), 1.0D)) {
            if (this.aboveFarmlandPos != null && timestamp > this.nextOkStartTime) {
                BlockState blockstate = level.getBlockState(this.aboveFarmlandPos);
                Block block = blockstate.getBlock();
                Block block1 = level.getBlockState(this.aboveFarmlandPos.below()).getBlock();

                if (block instanceof CropBlock && ((CropBlock) block).isMaxAge(blockstate)) {
                    level.destroyBlock(this.aboveFarmlandPos, true, body);
                }

                if (blockstate.isAir() && block1 instanceof FarmBlock && body.hasFarmSeeds()) {
                    SimpleContainer simplecontainer = body.getInventory();

                    for (int j = 0; j < simplecontainer.getContainerSize(); ++j) {
                        ItemStack itemstack = simplecontainer.getItem(j);
                        boolean flag = false;

                        if (!itemstack.isEmpty() && itemstack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS)) {
                            Item item = itemstack.getItem();

                            if (item instanceof BlockItem) {
                                BlockItem blockitem = (BlockItem) item;
                                BlockState blockstate1 = blockitem.getBlock().defaultBlockState();

                                level.setBlockAndUpdate(this.aboveFarmlandPos, blockstate1);
                                level.gameEvent(GameEvent.BLOCK_PLACE, this.aboveFarmlandPos, GameEvent.Context.of(body, blockstate1));
                                flag = true;
                            }
                        }

                        if (flag) {
                            level.playSound((Entity) null, (double) this.aboveFarmlandPos.getX(), (double) this.aboveFarmlandPos.getY(), (double) this.aboveFarmlandPos.getZ(), SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
                            itemstack.shrink(1);
                            if (itemstack.isEmpty()) {
                                simplecontainer.setItem(j, ItemStack.EMPTY);
                            }
                            break;
                        }
                    }
                }

                if (block instanceof CropBlock && !((CropBlock) block).isMaxAge(blockstate)) {
                    this.validFarmlandAroundVillager.remove(this.aboveFarmlandPos);
                    this.aboveFarmlandPos = this.getValidFarmland(level);
                    if (this.aboveFarmlandPos != null) {
                        this.nextOkStartTime = timestamp + 20L;
                        body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(this.aboveFarmlandPos), 0.5F, 1));
                        body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(this.aboveFarmlandPos));
                    }
                }
            }

            ++this.timeWorkedSoFar;
        }
    }

    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return this.timeWorkedSoFar < 200;
    }
}
