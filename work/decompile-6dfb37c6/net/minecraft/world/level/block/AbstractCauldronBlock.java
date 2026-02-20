package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class AbstractCauldronBlock extends Block {

    protected static final int FLOOR_LEVEL = 4;
    private static final VoxelShape SHAPE_INSIDE = Block.column(12.0D, 4.0D, 16.0D);
    protected static final VoxelShape SHAPE = (VoxelShape) Util.make(() -> {
        int i = 4;
        int j = 3;
        int k = 2;

        return Shapes.join(Shapes.block(), Shapes.or(Block.column(16.0D, 8.0D, 0.0D, 3.0D), Block.column(8.0D, 16.0D, 0.0D, 3.0D), Block.column(12.0D, 0.0D, 3.0D), AbstractCauldronBlock.SHAPE_INSIDE), BooleanOp.ONLY_FIRST);
    });
    protected final CauldronInteraction.InteractionMap interactions;

    @Override
    protected abstract MapCodec<? extends AbstractCauldronBlock> codec();

    public AbstractCauldronBlock(BlockBehaviour.Properties properties, CauldronInteraction.InteractionMap interactions) {
        super(properties);
        this.interactions = interactions;
    }

    protected double getContentHeight(BlockState state) {
        return 0.0D;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        CauldronInteraction cauldroninteraction = (CauldronInteraction) this.interactions.map().get(itemStack.getItem());

        return cauldroninteraction.interact(state, level, pos, player, hand, itemStack);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AbstractCauldronBlock.SHAPE;
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return AbstractCauldronBlock.SHAPE_INSIDE;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    public abstract boolean isFull(BlockState state);

    @Override
    protected void tick(BlockState cauldronState, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos blockpos1 = PointedDripstoneBlock.findStalactiteTipAboveCauldron(level, pos);

        if (blockpos1 != null) {
            Fluid fluid = PointedDripstoneBlock.getCauldronFillFluidType(level, blockpos1);

            if (fluid != Fluids.EMPTY && this.canReceiveStalactiteDrip(fluid)) {
                this.receiveStalactiteDrip(cauldronState, level, pos, fluid);
            }

        }
    }

    protected boolean canReceiveStalactiteDrip(Fluid fluid) {
        return false;
    }

    protected void receiveStalactiteDrip(BlockState state, Level level, BlockPos pos, Fluid fluid) {}
}
