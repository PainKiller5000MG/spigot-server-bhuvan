package net.minecraft.world.entity.ai.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

public class CatSitOnBlockGoal extends MoveToBlockGoal {

    private final Cat cat;

    public CatSitOnBlockGoal(Cat cat, double speedModifier) {
        super(cat, speedModifier, 8);
        this.cat = cat;
    }

    @Override
    public boolean canUse() {
        return this.cat.isTame() && !this.cat.isOrderedToSit() && super.canUse();
    }

    @Override
    public void start() {
        super.start();
        this.cat.setInSittingPose(false);
    }

    @Override
    public void stop() {
        super.stop();
        this.cat.setInSittingPose(false);
    }

    @Override
    public void tick() {
        super.tick();
        this.cat.setInSittingPose(this.isReachedTarget());
    }

    @Override
    protected boolean isValidTarget(LevelReader level, BlockPos pos) {
        if (!level.isEmptyBlock(pos.above())) {
            return false;
        } else {
            BlockState blockstate = level.getBlockState(pos);

            return blockstate.is(Blocks.CHEST) ? ChestBlockEntity.getOpenCount(level, pos) < 1 : (blockstate.is(Blocks.FURNACE) && (Boolean) blockstate.getValue(FurnaceBlock.LIT) ? true : blockstate.is(BlockTags.BEDS, (blockbehaviour_blockstatebase) -> {
                return (Boolean) blockbehaviour_blockstatebase.getOptionalValue(BedBlock.PART).map((bedpart) -> {
                    return bedpart != BedPart.HEAD;
                }).orElse(true);
            }));
        }
    }
}
