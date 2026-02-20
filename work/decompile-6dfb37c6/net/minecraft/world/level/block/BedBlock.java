package net.minecraft.world.level.block;

import com.mojang.math.OctahedralGroup;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.ArrayUtils;
import org.jspecify.annotations.Nullable;

public class BedBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<BedBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(DyeColor.CODEC.fieldOf("color").forGetter(BedBlock::getColor), propertiesCodec()).apply(instance, BedBlock::new);
    });
    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;
    public static final BooleanProperty OCCUPIED = BlockStateProperties.OCCUPIED;
    private static final Map<Direction, VoxelShape> SHAPES = (Map) Util.make(() -> {
        VoxelShape voxelshape = Block.box(0.0D, 0.0D, 0.0D, 3.0D, 3.0D, 3.0D);
        VoxelShape voxelshape1 = Shapes.rotate(voxelshape, OctahedralGroup.BLOCK_ROT_Y_90);

        return Shapes.rotateHorizontal(Shapes.or(Block.column(16.0D, 3.0D, 9.0D), voxelshape, voxelshape1));
    });
    private final DyeColor color;

    @Override
    public MapCodec<BedBlock> codec() {
        return BedBlock.CODEC;
    }

    public BedBlock(DyeColor color, BlockBehaviour.Properties properties) {
        super(properties);
        this.color = color;
        this.registerDefaultState((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(BedBlock.PART, BedPart.FOOT)).setValue(BedBlock.OCCUPIED, false));
    }

    public static @Nullable Direction getBedOrientation(BlockGetter level, BlockPos pos) {
        BlockState blockstate = level.getBlockState(pos);

        return blockstate.getBlock() instanceof BedBlock ? (Direction) blockstate.getValue(BedBlock.FACING) : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS_SERVER;
        } else {
            if (state.getValue(BedBlock.PART) != BedPart.HEAD) {
                pos = pos.relative((Direction) state.getValue(BedBlock.FACING));
                state = level.getBlockState(pos);
                if (!state.is(this)) {
                    return InteractionResult.CONSUME;
                }
            }

            BedRule bedrule = (BedRule) level.environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, pos);

            if (bedrule.explodes()) {
                bedrule.errorMessage().ifPresent((component) -> {
                    player.displayClientMessage(component, true);
                });
                level.removeBlock(pos, false);
                BlockPos blockpos1 = pos.relative(((Direction) state.getValue(BedBlock.FACING)).getOpposite());

                if (level.getBlockState(blockpos1).is(this)) {
                    level.removeBlock(blockpos1, false);
                }

                Vec3 vec3 = pos.getCenter();

                level.explode((Entity) null, level.damageSources().badRespawnPointExplosion(vec3), (ExplosionDamageCalculator) null, vec3, 5.0F, true, Level.ExplosionInteraction.BLOCK);
                return InteractionResult.SUCCESS_SERVER;
            } else if ((Boolean) state.getValue(BedBlock.OCCUPIED)) {
                if (!this.kickVillagerOutOfBed(level, pos)) {
                    player.displayClientMessage(Component.translatable("block.minecraft.bed.occupied"), true);
                }

                return InteractionResult.SUCCESS_SERVER;
            } else {
                player.startSleepInBed(pos).ifLeft((player_bedsleepingproblem) -> {
                    if (player_bedsleepingproblem.message() != null) {
                        player.displayClientMessage(player_bedsleepingproblem.message(), true);
                    }

                });
                return InteractionResult.SUCCESS_SERVER;
            }
        }
    }

    private boolean kickVillagerOutOfBed(Level level, BlockPos pos) {
        List<Villager> list = level.<Villager>getEntitiesOfClass(Villager.class, new AABB(pos), LivingEntity::isSleeping);

        if (list.isEmpty()) {
            return false;
        } else {
            ((Villager) list.get(0)).stopSleeping();
            return true;
        }
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
        super.fallOn(level, state, pos, entity, fallDistance * 0.5D);
    }

    @Override
    public void updateEntityMovementAfterFallOn(BlockGetter level, Entity entity) {
        if (entity.isSuppressingBounce()) {
            super.updateEntityMovementAfterFallOn(level, entity);
        } else {
            this.bounceUp(entity);
        }

    }

    private void bounceUp(Entity entity) {
        Vec3 vec3 = entity.getDeltaMovement();

        if (vec3.y < 0.0D) {
            double d0 = entity instanceof LivingEntity ? 1.0D : 0.8D;

            entity.setDeltaMovement(vec3.x, -vec3.y * (double) 0.66F * d0, vec3.z);
        }

    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return directionToNeighbour == getNeighbourDirection((BedPart) state.getValue(BedBlock.PART), (Direction) state.getValue(BedBlock.FACING)) ? (neighbourState.is(this) && neighbourState.getValue(BedBlock.PART) != state.getValue(BedBlock.PART) ? (BlockState) state.setValue(BedBlock.OCCUPIED, (Boolean) neighbourState.getValue(BedBlock.OCCUPIED)) : Blocks.AIR.defaultBlockState()) : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    private static Direction getNeighbourDirection(BedPart part, Direction facing) {
        return part == BedPart.FOOT ? facing : facing.getOpposite();
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.preventsBlockDrops()) {
            BedPart bedpart = (BedPart) state.getValue(BedBlock.PART);

            if (bedpart == BedPart.FOOT) {
                BlockPos blockpos1 = pos.relative(getNeighbourDirection(bedpart, (Direction) state.getValue(BedBlock.FACING)));
                BlockState blockstate1 = level.getBlockState(blockpos1);

                if (blockstate1.is(this) && blockstate1.getValue(BedBlock.PART) == BedPart.HEAD) {
                    level.setBlock(blockpos1, Blocks.AIR.defaultBlockState(), 35);
                    level.levelEvent(player, 2001, blockpos1, Block.getId(blockstate1));
                }
            }
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction direction = context.getHorizontalDirection();
        BlockPos blockpos = context.getClickedPos();
        BlockPos blockpos1 = blockpos.relative(direction);
        Level level = context.getLevel();

        return level.getBlockState(blockpos1).canBeReplaced(context) && level.getWorldBorder().isWithinBounds(blockpos1) ? (BlockState) this.defaultBlockState().setValue(BedBlock.FACING, direction) : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (VoxelShape) BedBlock.SHAPES.get(getConnectedDirection(state).getOpposite());
    }

    public static Direction getConnectedDirection(BlockState state) {
        Direction direction = (Direction) state.getValue(BedBlock.FACING);

        return state.getValue(BedBlock.PART) == BedPart.HEAD ? direction.getOpposite() : direction;
    }

    public static DoubleBlockCombiner.BlockType getBlockType(BlockState state) {
        BedPart bedpart = (BedPart) state.getValue(BedBlock.PART);

        return bedpart == BedPart.HEAD ? DoubleBlockCombiner.BlockType.FIRST : DoubleBlockCombiner.BlockType.SECOND;
    }

    private static boolean isBunkBed(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos.below()).getBlock() instanceof BedBlock;
    }

    public static Optional<Vec3> findStandUpPosition(EntityType<?> type, CollisionGetter level, BlockPos pos, Direction forward, float yaw) {
        Direction direction1 = forward.getClockWise();
        Direction direction2 = direction1.isFacingAngle(yaw) ? direction1.getOpposite() : direction1;

        if (isBunkBed(level, pos)) {
            return findBunkBedStandUpPosition(type, level, pos, forward, direction2);
        } else {
            int[][] aint = bedStandUpOffsets(forward, direction2);
            Optional<Vec3> optional = findStandUpPositionAtOffset(type, level, pos, aint, true);

            return optional.isPresent() ? optional : findStandUpPositionAtOffset(type, level, pos, aint, false);
        }
    }

    private static Optional<Vec3> findBunkBedStandUpPosition(EntityType<?> type, CollisionGetter level, BlockPos pos, Direction forward, Direction side) {
        int[][] aint = bedSurroundStandUpOffsets(forward, side);
        Optional<Vec3> optional = findStandUpPositionAtOffset(type, level, pos, aint, true);

        if (optional.isPresent()) {
            return optional;
        } else {
            BlockPos blockpos1 = pos.below();
            Optional<Vec3> optional1 = findStandUpPositionAtOffset(type, level, blockpos1, aint, true);

            if (optional1.isPresent()) {
                return optional1;
            } else {
                int[][] aint1 = bedAboveStandUpOffsets(forward);
                Optional<Vec3> optional2 = findStandUpPositionAtOffset(type, level, pos, aint1, true);

                if (optional2.isPresent()) {
                    return optional2;
                } else {
                    Optional<Vec3> optional3 = findStandUpPositionAtOffset(type, level, pos, aint, false);

                    if (optional3.isPresent()) {
                        return optional3;
                    } else {
                        Optional<Vec3> optional4 = findStandUpPositionAtOffset(type, level, blockpos1, aint, false);

                        return optional4.isPresent() ? optional4 : findStandUpPositionAtOffset(type, level, pos, aint1, false);
                    }
                }
            }
        }
    }

    private static Optional<Vec3> findStandUpPositionAtOffset(EntityType<?> type, CollisionGetter level, BlockPos pos, int[][] offsets, boolean checkDangerous) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

        for (int[] aint1 : offsets) {
            blockpos_mutableblockpos.set(pos.getX() + aint1[0], pos.getY(), pos.getZ() + aint1[1]);
            Vec3 vec3 = DismountHelper.findSafeDismountLocation(type, level, blockpos_mutableblockpos, checkDangerous);

            if (vec3 != null) {
                return Optional.of(vec3);
            }
        }

        return Optional.empty();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BedBlock.FACING, BedBlock.PART, BedBlock.OCCUPIED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new BedBlockEntity(worldPosition, blockState, this.color);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, by, itemStack);
        if (!level.isClientSide()) {
            BlockPos blockpos1 = pos.relative((Direction) state.getValue(BedBlock.FACING));

            level.setBlock(blockpos1, (BlockState) state.setValue(BedBlock.PART, BedPart.HEAD), 3);
            level.updateNeighborsAt(pos, Blocks.AIR);
            state.updateNeighbourShapes(level, pos, 3);
        }

    }

    public DyeColor getColor() {
        return this.color;
    }

    @Override
    protected long getSeed(BlockState state, BlockPos pos) {
        BlockPos blockpos1 = pos.relative((Direction) state.getValue(BedBlock.FACING), state.getValue(BedBlock.PART) == BedPart.HEAD ? 0 : 1);

        return Mth.getSeed(blockpos1.getX(), pos.getY(), blockpos1.getZ());
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    private static int[][] bedStandUpOffsets(Direction forward, Direction side) {
        return (int[][]) ArrayUtils.addAll(bedSurroundStandUpOffsets(forward, side), bedAboveStandUpOffsets(forward));
    }

    private static int[][] bedSurroundStandUpOffsets(Direction forward, Direction side) {
        return new int[][]{{side.getStepX(), side.getStepZ()}, {side.getStepX() - forward.getStepX(), side.getStepZ() - forward.getStepZ()}, {side.getStepX() - forward.getStepX() * 2, side.getStepZ() - forward.getStepZ() * 2}, {-forward.getStepX() * 2, -forward.getStepZ() * 2}, {-side.getStepX() - forward.getStepX() * 2, -side.getStepZ() - forward.getStepZ() * 2}, {-side.getStepX() - forward.getStepX(), -side.getStepZ() - forward.getStepZ()}, {-side.getStepX(), -side.getStepZ()}, {-side.getStepX() + forward.getStepX(), -side.getStepZ() + forward.getStepZ()}, {forward.getStepX(), forward.getStepZ()}, {side.getStepX() + forward.getStepX(), side.getStepZ() + forward.getStepZ()}};
    }

    private static int[][] bedAboveStandUpOffsets(Direction forward) {
        return new int[][]{{0, 0}, {-forward.getStepX(), -forward.getStepZ()}};
    }
}
