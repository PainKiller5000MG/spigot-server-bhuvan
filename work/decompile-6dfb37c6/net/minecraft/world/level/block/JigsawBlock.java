package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;

public class JigsawBlock extends Block implements EntityBlock, GameMasterBlock {

    public static final MapCodec<JigsawBlock> CODEC = simpleCodec(JigsawBlock::new);
    public static final EnumProperty<FrontAndTop> ORIENTATION = BlockStateProperties.ORIENTATION;

    @Override
    public MapCodec<JigsawBlock> codec() {
        return JigsawBlock.CODEC;
    }

    protected JigsawBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) (this.stateDefinition.any()).setValue(JigsawBlock.ORIENTATION, FrontAndTop.NORTH_UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(JigsawBlock.ORIENTATION);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(JigsawBlock.ORIENTATION, rotation.rotation().rotate((FrontAndTop) state.getValue(JigsawBlock.ORIENTATION)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return (BlockState) state.setValue(JigsawBlock.ORIENTATION, mirror.rotation().rotate((FrontAndTop) state.getValue(JigsawBlock.ORIENTATION)));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction direction = context.getClickedFace();
        Direction direction1;

        if (direction.getAxis() == Direction.Axis.Y) {
            direction1 = context.getHorizontalDirection().getOpposite();
        } else {
            direction1 = Direction.UP;
        }

        return (BlockState) this.defaultBlockState().setValue(JigsawBlock.ORIENTATION, FrontAndTop.fromFrontAndTop(direction, direction1));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new JigsawBlockEntity(worldPosition, blockState);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof JigsawBlockEntity && player.canUseGameMasterBlocks()) {
            player.openJigsawBlock((JigsawBlockEntity) blockentity);
            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.PASS;
        }
    }

    public static boolean canAttach(StructureTemplate.JigsawBlockInfo source, StructureTemplate.JigsawBlockInfo target) {
        Direction direction = getFrontFacing(source.info().state());
        Direction direction1 = getFrontFacing(target.info().state());
        Direction direction2 = getTopFacing(source.info().state());
        Direction direction3 = getTopFacing(target.info().state());
        JigsawBlockEntity.JointType jigsawblockentity_jointtype = source.jointType();
        boolean flag = jigsawblockentity_jointtype == JigsawBlockEntity.JointType.ROLLABLE;

        return direction == direction1.getOpposite() && (flag || direction2 == direction3) && source.target().equals(target.name());
    }

    public static Direction getFrontFacing(BlockState state) {
        return ((FrontAndTop) state.getValue(JigsawBlock.ORIENTATION)).front();
    }

    public static Direction getTopFacing(BlockState state) {
        return ((FrontAndTop) state.getValue(JigsawBlock.ORIENTATION)).top();
    }
}
