package net.minecraft.world.level.block;

import com.mojang.math.OctahedralGroup;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class GrindstoneBlock extends FaceAttachedHorizontalDirectionalBlock {

    public static final MapCodec<GrindstoneBlock> CODEC = simpleCodec(GrindstoneBlock::new);
    private static final Component CONTAINER_TITLE = Component.translatable("container.grindstone_title");
    private final Function<BlockState, VoxelShape> shapes;

    @Override
    public MapCodec<GrindstoneBlock> codec() {
        return GrindstoneBlock.CODEC;
    }

    protected GrindstoneBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(GrindstoneBlock.FACING, Direction.NORTH)).setValue(GrindstoneBlock.FACE, AttachFace.WALL));
        this.shapes = this.makeShapes();
    }

    private Function<BlockState, VoxelShape> makeShapes() {
        VoxelShape voxelshape = Shapes.or(Block.box(2.0D, 6.0D, 7.0D, 4.0D, 10.0D, 16.0D), Block.box(2.0D, 5.0D, 3.0D, 4.0D, 11.0D, 9.0D));
        VoxelShape voxelshape1 = Shapes.rotate(voxelshape, OctahedralGroup.INVERT_X);
        VoxelShape voxelshape2 = Shapes.or(Block.boxZ(8.0D, 2.0D, 14.0D, 0.0D, 12.0D), voxelshape, voxelshape1);
        Map<AttachFace, Map<Direction, VoxelShape>> map = Shapes.rotateAttachFace(voxelshape2);

        return this.getShapeForEachState((blockstate) -> {
            return (VoxelShape) ((Map) map.get(blockstate.getValue(GrindstoneBlock.FACE))).get(blockstate.getValue(GrindstoneBlock.FACING));
        });
    }

    private VoxelShape getVoxelShape(BlockState state) {
        return (VoxelShape) this.shapes.apply(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getVoxelShape(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getVoxelShape(state);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            player.openMenu(state.getMenuProvider(level, pos));
            player.awardStat(Stats.INTERACT_WITH_GRINDSTONE);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider((i, inventory, player) -> {
            return new GrindstoneMenu(i, inventory, ContainerLevelAccess.create(level, pos));
        }, GrindstoneBlock.CONTAINER_TITLE);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(GrindstoneBlock.FACING, rotation.rotate((Direction) state.getValue(GrindstoneBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(GrindstoneBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GrindstoneBlock.FACING, GrindstoneBlock.FACE);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
