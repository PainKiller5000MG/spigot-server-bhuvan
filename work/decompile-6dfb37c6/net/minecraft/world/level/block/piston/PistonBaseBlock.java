package net.minecraft.world.level.block.piston;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class PistonBaseBlock extends DirectionalBlock {

    public static final MapCodec<PistonBaseBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.BOOL.fieldOf("sticky").forGetter((pistonbaseblock) -> {
            return pistonbaseblock.isSticky;
        }), propertiesCodec()).apply(instance, PistonBaseBlock::new);
    });
    public static final BooleanProperty EXTENDED = BlockStateProperties.EXTENDED;
    public static final int TRIGGER_EXTEND = 0;
    public static final int TRIGGER_CONTRACT = 1;
    public static final int TRIGGER_DROP = 2;
    public static final int PLATFORM_THICKNESS = 4;
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateAll(Block.boxZ(16.0D, 4.0D, 16.0D));
    private final boolean isSticky;

    @Override
    public MapCodec<PistonBaseBlock> codec() {
        return PistonBaseBlock.CODEC;
    }

    public PistonBaseBlock(boolean isSticky, BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(PistonBaseBlock.FACING, Direction.NORTH)).setValue(PistonBaseBlock.EXTENDED, false));
        this.isSticky = isSticky;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (Boolean) state.getValue(PistonBaseBlock.EXTENDED) ? (VoxelShape) PistonBaseBlock.SHAPES.get(state.getValue(PistonBaseBlock.FACING)) : Shapes.block();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        if (!level.isClientSide()) {
            this.checkIfExtend(level, pos, state);
        }

    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide()) {
            this.checkIfExtend(level, pos, state);
        }

    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock())) {
            if (!level.isClientSide() && level.getBlockEntity(pos) == null) {
                this.checkIfExtend(level, pos, state);
            }

        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState) ((BlockState) this.defaultBlockState().setValue(PistonBaseBlock.FACING, context.getNearestLookingDirection().getOpposite())).setValue(PistonBaseBlock.EXTENDED, false);
    }

    private void checkIfExtend(Level level, BlockPos pos, BlockState state) {
        Direction direction = (Direction) state.getValue(PistonBaseBlock.FACING);
        boolean flag = this.getNeighborSignal(level, pos, direction);

        if (flag && !(Boolean) state.getValue(PistonBaseBlock.EXTENDED)) {
            if ((new PistonStructureResolver(level, pos, direction, true)).resolve()) {
                level.blockEvent(pos, this, 0, direction.get3DDataValue());
            }
        } else if (!flag && (Boolean) state.getValue(PistonBaseBlock.EXTENDED)) {
            BlockPos blockpos1 = pos.relative(direction, 2);
            BlockState blockstate1 = level.getBlockState(blockpos1);
            int i = 1;

            if (blockstate1.is(Blocks.MOVING_PISTON) && blockstate1.getValue(PistonBaseBlock.FACING) == direction) {
                BlockEntity blockentity = level.getBlockEntity(blockpos1);

                if (blockentity instanceof PistonMovingBlockEntity) {
                    PistonMovingBlockEntity pistonmovingblockentity = (PistonMovingBlockEntity) blockentity;

                    if (pistonmovingblockentity.isExtending() && (pistonmovingblockentity.getProgress(0.0F) < 0.5F || level.getGameTime() == pistonmovingblockentity.getLastTicked() || ((ServerLevel) level).isHandlingTick())) {
                        i = 2;
                    }
                }
            }

            level.blockEvent(pos, this, i, direction.get3DDataValue());
        }

    }

    private boolean getNeighborSignal(SignalGetter level, BlockPos pos, Direction pushDirection) {
        for (Direction direction1 : Direction.values()) {
            if (direction1 != pushDirection && level.hasSignal(pos.relative(direction1), direction1)) {
                return true;
            }
        }

        if (level.hasSignal(pos, Direction.DOWN)) {
            return true;
        } else {
            BlockPos blockpos1 = pos.above();

            for (Direction direction2 : Direction.values()) {
                if (direction2 != Direction.DOWN && level.hasSignal(blockpos1.relative(direction2), direction2)) {
                    return true;
                }
            }

            return false;
        }
    }

    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int b0, int b1) {
        Direction direction = (Direction) state.getValue(PistonBaseBlock.FACING);
        BlockState blockstate1 = (BlockState) state.setValue(PistonBaseBlock.EXTENDED, true);

        if (!level.isClientSide()) {
            boolean flag = this.getNeighborSignal(level, pos, direction);

            if (flag && (b0 == 1 || b0 == 2)) {
                level.setBlock(pos, blockstate1, 2);
                return false;
            }

            if (!flag && b0 == 0) {
                return false;
            }
        }

        if (b0 == 0) {
            if (!this.moveBlocks(level, pos, direction, true)) {
                return false;
            }

            level.setBlock(pos, blockstate1, 67);
            level.playSound((Entity) null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.25F + 0.6F);
            level.gameEvent(GameEvent.BLOCK_ACTIVATE, pos, GameEvent.Context.of(blockstate1));
        } else if (b0 == 1 || b0 == 2) {
            BlockEntity blockentity = level.getBlockEntity(pos.relative(direction));

            if (blockentity instanceof PistonMovingBlockEntity) {
                ((PistonMovingBlockEntity) blockentity).finalTick();
            }

            BlockState blockstate2 = (BlockState) ((BlockState) Blocks.MOVING_PISTON.defaultBlockState().setValue(MovingPistonBlock.FACING, direction)).setValue(MovingPistonBlock.TYPE, this.isSticky ? PistonType.STICKY : PistonType.DEFAULT);

            level.setBlock(pos, blockstate2, 276);
            level.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(pos, blockstate2, (BlockState) this.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.from3DDataValue(b1 & 7)), direction, false, true));
            level.updateNeighborsAt(pos, blockstate2.getBlock());
            blockstate2.updateNeighbourShapes(level, pos, 2);
            if (this.isSticky) {
                BlockPos blockpos1 = pos.offset(direction.getStepX() * 2, direction.getStepY() * 2, direction.getStepZ() * 2);
                BlockState blockstate3 = level.getBlockState(blockpos1);
                boolean flag1 = false;

                if (blockstate3.is(Blocks.MOVING_PISTON)) {
                    BlockEntity blockentity1 = level.getBlockEntity(blockpos1);

                    if (blockentity1 instanceof PistonMovingBlockEntity) {
                        PistonMovingBlockEntity pistonmovingblockentity = (PistonMovingBlockEntity) blockentity1;

                        if (pistonmovingblockentity.getDirection() == direction && pistonmovingblockentity.isExtending()) {
                            pistonmovingblockentity.finalTick();
                            flag1 = true;
                        }
                    }
                }

                if (!flag1) {
                    if (b0 != 1 || blockstate3.isAir() || !isPushable(blockstate3, level, blockpos1, direction.getOpposite(), false, direction) || blockstate3.getPistonPushReaction() != PushReaction.NORMAL && !blockstate3.is(Blocks.PISTON) && !blockstate3.is(Blocks.STICKY_PISTON)) {
                        level.removeBlock(pos.relative(direction), false);
                    } else {
                        this.moveBlocks(level, pos, direction, false);
                    }
                }
            } else {
                level.removeBlock(pos.relative(direction), false);
            }

            level.playSound((Entity) null, pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.15F + 0.6F);
            level.gameEvent(GameEvent.BLOCK_DEACTIVATE, pos, GameEvent.Context.of(blockstate2));
        }

        return true;
    }

    public static boolean isPushable(BlockState state, Level level, BlockPos pos, Direction direction, boolean allowDestroyable, Direction connectionDirection) {
        if (pos.getY() >= level.getMinY() && pos.getY() <= level.getMaxY() && level.getWorldBorder().isWithinBounds(pos)) {
            if (state.isAir()) {
                return true;
            } else if (!state.is(Blocks.OBSIDIAN) && !state.is(Blocks.CRYING_OBSIDIAN) && !state.is(Blocks.RESPAWN_ANCHOR) && !state.is(Blocks.REINFORCED_DEEPSLATE)) {
                if (direction == Direction.DOWN && pos.getY() == level.getMinY()) {
                    return false;
                } else if (direction == Direction.UP && pos.getY() == level.getMaxY()) {
                    return false;
                } else {
                    if (!state.is(Blocks.PISTON) && !state.is(Blocks.STICKY_PISTON)) {
                        if (state.getDestroySpeed(level, pos) == -1.0F) {
                            return false;
                        }

                        switch (state.getPistonPushReaction()) {
                            case BLOCK:
                                return false;
                            case DESTROY:
                                return allowDestroyable;
                            case PUSH_ONLY:
                                return direction == connectionDirection;
                        }
                    } else if ((Boolean) state.getValue(PistonBaseBlock.EXTENDED)) {
                        return false;
                    }

                    return !state.hasBlockEntity();
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private boolean moveBlocks(Level level, BlockPos pistonPos, Direction direction, boolean extending) {
        BlockPos blockpos1 = pistonPos.relative(direction);

        if (!extending && level.getBlockState(blockpos1).is(Blocks.PISTON_HEAD)) {
            level.setBlock(blockpos1, Blocks.AIR.defaultBlockState(), 276);
        }

        PistonStructureResolver pistonstructureresolver = new PistonStructureResolver(level, pistonPos, direction, extending);

        if (!pistonstructureresolver.resolve()) {
            return false;
        } else {
            Map<BlockPos, BlockState> map = Maps.newHashMap();
            List<BlockPos> list = pistonstructureresolver.getToPush();
            List<BlockState> list1 = Lists.newArrayList();

            for (BlockPos blockpos2 : list) {
                BlockState blockstate = level.getBlockState(blockpos2);

                list1.add(blockstate);
                map.put(blockpos2, blockstate);
            }

            List<BlockPos> list2 = pistonstructureresolver.getToDestroy();
            BlockState[] ablockstate = new BlockState[list.size() + list2.size()];
            Direction direction1 = extending ? direction : direction.getOpposite();
            int i = 0;

            for (int j = list2.size() - 1; j >= 0; --j) {
                BlockPos blockpos3 = (BlockPos) list2.get(j);
                BlockState blockstate1 = level.getBlockState(blockpos3);
                BlockEntity blockentity = blockstate1.hasBlockEntity() ? level.getBlockEntity(blockpos3) : null;

                dropResources(blockstate1, level, blockpos3, blockentity);
                if (!blockstate1.is(BlockTags.FIRE) && level.isClientSide()) {
                    level.levelEvent(2001, blockpos3, getId(blockstate1));
                }

                level.setBlock(blockpos3, Blocks.AIR.defaultBlockState(), 18);
                level.gameEvent(GameEvent.BLOCK_DESTROY, blockpos3, GameEvent.Context.of(blockstate1));
                ablockstate[i++] = blockstate1;
            }

            for (int k = list.size() - 1; k >= 0; --k) {
                BlockPos blockpos4 = (BlockPos) list.get(k);
                BlockState blockstate2 = level.getBlockState(blockpos4);

                blockpos4 = blockpos4.relative(direction1);
                map.remove(blockpos4);
                BlockState blockstate3 = (BlockState) Blocks.MOVING_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, direction);

                level.setBlock(blockpos4, blockstate3, 324);
                level.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(blockpos4, blockstate3, (BlockState) list1.get(k), direction, extending, false));
                ablockstate[i++] = blockstate2;
            }

            if (extending) {
                PistonType pistontype = this.isSticky ? PistonType.STICKY : PistonType.DEFAULT;
                BlockState blockstate4 = (BlockState) ((BlockState) Blocks.PISTON_HEAD.defaultBlockState().setValue(PistonHeadBlock.FACING, direction)).setValue(PistonHeadBlock.TYPE, pistontype);
                BlockState blockstate5 = (BlockState) ((BlockState) Blocks.MOVING_PISTON.defaultBlockState().setValue(MovingPistonBlock.FACING, direction)).setValue(MovingPistonBlock.TYPE, this.isSticky ? PistonType.STICKY : PistonType.DEFAULT);

                map.remove(blockpos1);
                level.setBlock(blockpos1, blockstate5, 324);
                level.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(blockpos1, blockstate5, blockstate4, direction, true, true));
            }

            BlockState blockstate6 = Blocks.AIR.defaultBlockState();

            for (BlockPos blockpos5 : map.keySet()) {
                level.setBlock(blockpos5, blockstate6, 82);
            }

            for (Map.Entry<BlockPos, BlockState> map_entry : map.entrySet()) {
                BlockPos blockpos6 = (BlockPos) map_entry.getKey();
                BlockState blockstate7 = (BlockState) map_entry.getValue();

                blockstate7.updateIndirectNeighbourShapes(level, blockpos6, 2);
                blockstate6.updateNeighbourShapes(level, blockpos6, 2);
                blockstate6.updateIndirectNeighbourShapes(level, blockpos6, 2);
            }

            Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, pistonstructureresolver.getPushDirection(), (Direction) null);

            i = 0;

            for (int l = list2.size() - 1; l >= 0; --l) {
                BlockState blockstate8 = ablockstate[i++];
                BlockPos blockpos7 = (BlockPos) list2.get(l);

                if (level instanceof ServerLevel) {
                    ServerLevel serverlevel = (ServerLevel) level;

                    blockstate8.affectNeighborsAfterRemoval(serverlevel, blockpos7, false);
                }

                blockstate8.updateIndirectNeighbourShapes(level, blockpos7, 2);
                level.updateNeighborsAt(blockpos7, blockstate8.getBlock(), orientation);
            }

            for (int i1 = list.size() - 1; i1 >= 0; --i1) {
                level.updateNeighborsAt((BlockPos) list.get(i1), ablockstate[i++].getBlock(), orientation);
            }

            if (extending) {
                level.updateNeighborsAt(blockpos1, Blocks.PISTON_HEAD, orientation);
            }

            return true;
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(PistonBaseBlock.FACING, rotation.rotate((Direction) state.getValue(PistonBaseBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(PistonBaseBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PistonBaseBlock.FACING, PistonBaseBlock.EXTENDED);
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return (Boolean) state.getValue(PistonBaseBlock.EXTENDED);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
