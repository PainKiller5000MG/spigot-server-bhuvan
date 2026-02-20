package net.minecraft.world.level.redstone;

import com.google.common.collect.Sets;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class DefaultRedstoneWireEvaluator extends RedstoneWireEvaluator {

    public DefaultRedstoneWireEvaluator(RedStoneWireBlock wireBlock) {
        super(wireBlock);
    }

    @Override
    public void updatePowerStrength(Level level, BlockPos pos, BlockState state, @Nullable Orientation orientation, boolean skipShapeUpdates) {
        int i = this.calculateTargetStrength(level, pos);

        if ((Integer) state.getValue(RedStoneWireBlock.POWER) != i) {
            if (level.getBlockState(pos) == state) {
                level.setBlock(pos, (BlockState) state.setValue(RedStoneWireBlock.POWER, i), 2);
            }

            Set<BlockPos> set = Sets.newHashSet();

            set.add(pos);

            for (Direction direction : Direction.values()) {
                set.add(pos.relative(direction));
            }

            for (BlockPos blockpos1 : set) {
                level.updateNeighborsAt(blockpos1, this.wireBlock);
            }
        }

    }

    private int calculateTargetStrength(Level level, BlockPos pos) {
        int i = this.getBlockSignal(level, pos);

        return i == 15 ? i : Math.max(i, this.getIncomingWireSignal(level, pos));
    }
}
