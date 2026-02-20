package net.minecraft.world.phys.shapes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.jspecify.annotations.Nullable;

public class MinecartCollisionContext extends EntityCollisionContext {

    private @Nullable BlockPos ingoreBelow;
    private @Nullable BlockPos slopeIgnore;

    protected MinecartCollisionContext(AbstractMinecart entity, boolean alwaysStandOnFluid) {
        super(entity, alwaysStandOnFluid, false);
        this.setupContext(entity);
    }

    private void setupContext(AbstractMinecart entity) {
        BlockPos blockpos = entity.getCurrentBlockPosOrRailBelow();
        BlockState blockstate = entity.level().getBlockState(blockpos);
        boolean flag = BaseRailBlock.isRail(blockstate);

        if (flag) {
            this.ingoreBelow = blockpos.below();
            RailShape railshape = (RailShape) blockstate.getValue(((BaseRailBlock) blockstate.getBlock()).getShapeProperty());

            if (railshape.isSlope()) {
                BlockPos blockpos1;

                switch (railshape) {
                    case ASCENDING_EAST:
                        blockpos1 = blockpos.east();
                        break;
                    case ASCENDING_WEST:
                        blockpos1 = blockpos.west();
                        break;
                    case ASCENDING_NORTH:
                        blockpos1 = blockpos.north();
                        break;
                    case ASCENDING_SOUTH:
                        blockpos1 = blockpos.south();
                        break;
                    default:
                        blockpos1 = null;
                }

                this.slopeIgnore = blockpos1;
            }
        }

    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, CollisionGetter collisionGetter, BlockPos pos) {
        return !pos.equals(this.ingoreBelow) && !pos.equals(this.slopeIgnore) ? super.getCollisionShape(state, collisionGetter, pos) : Shapes.empty();
    }
}
