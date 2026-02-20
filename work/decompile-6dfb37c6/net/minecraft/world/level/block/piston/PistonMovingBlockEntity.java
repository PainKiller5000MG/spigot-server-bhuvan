package net.minecraft.world.level.block.piston;

import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PistonMovingBlockEntity extends BlockEntity {

    private static final int TICKS_TO_EXTEND = 2;
    private static final double PUSH_OFFSET = 0.01D;
    public static final double TICK_MOVEMENT = 0.51D;
    private static final BlockState DEFAULT_BLOCK_STATE = Blocks.AIR.defaultBlockState();
    private static final float DEFAULT_PROGRESS = 0.0F;
    private static final boolean DEFAULT_EXTENDING = false;
    private static final boolean DEFAULT_SOURCE = false;
    private BlockState movedState;
    private Direction direction;
    private boolean extending;
    private boolean isSourcePiston;
    private static final ThreadLocal<Direction> NOCLIP = ThreadLocal.withInitial(() -> {
        return null;
    });
    private float progress;
    private float progressO;
    private long lastTicked;
    private int deathTicks;

    public PistonMovingBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(BlockEntityType.PISTON, worldPosition, blockState);
        this.movedState = PistonMovingBlockEntity.DEFAULT_BLOCK_STATE;
        this.extending = false;
        this.isSourcePiston = false;
        this.progress = 0.0F;
        this.progressO = 0.0F;
    }

    public PistonMovingBlockEntity(BlockPos worldPosition, BlockState blockState, BlockState movedState, Direction direction, boolean extending, boolean isSourcePiston) {
        this(worldPosition, blockState);
        this.movedState = movedState;
        this.direction = direction;
        this.extending = extending;
        this.isSourcePiston = isSourcePiston;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    public boolean isExtending() {
        return this.extending;
    }

    public Direction getDirection() {
        return this.direction;
    }

    public boolean isSourcePiston() {
        return this.isSourcePiston;
    }

    public float getProgress(float a) {
        if (a > 1.0F) {
            a = 1.0F;
        }

        return Mth.lerp(a, this.progressO, this.progress);
    }

    public float getXOff(float a) {
        return (float) this.direction.getStepX() * this.getExtendedProgress(this.getProgress(a));
    }

    public float getYOff(float a) {
        return (float) this.direction.getStepY() * this.getExtendedProgress(this.getProgress(a));
    }

    public float getZOff(float a) {
        return (float) this.direction.getStepZ() * this.getExtendedProgress(this.getProgress(a));
    }

    private float getExtendedProgress(float progress) {
        return this.extending ? progress - 1.0F : 1.0F - progress;
    }

    private BlockState getCollisionRelatedBlockState() {
        return !this.isExtending() && this.isSourcePiston() && this.movedState.getBlock() instanceof PistonBaseBlock ? (BlockState) ((BlockState) ((BlockState) Blocks.PISTON_HEAD.defaultBlockState().setValue(PistonHeadBlock.SHORT, this.progress > 0.25F)).setValue(PistonHeadBlock.TYPE, this.movedState.is(Blocks.STICKY_PISTON) ? PistonType.STICKY : PistonType.DEFAULT)).setValue(PistonHeadBlock.FACING, (Direction) this.movedState.getValue(PistonBaseBlock.FACING)) : this.movedState;
    }

    private static void moveCollidedEntities(Level level, BlockPos pos, float newProgress, PistonMovingBlockEntity self) {
        Direction direction = self.getMovementDirection();
        double d0 = (double) (newProgress - self.progress);
        VoxelShape voxelshape = self.getCollisionRelatedBlockState().getCollisionShape(level, pos);

        if (!voxelshape.isEmpty()) {
            AABB aabb = moveByPositionAndProgress(pos, voxelshape.bounds(), self);
            List<Entity> list = level.getEntities((Entity) null, PistonMath.getMovementArea(aabb, direction, d0).minmax(aabb));

            if (!list.isEmpty()) {
                List<AABB> list1 = voxelshape.toAabbs();
                boolean flag = self.movedState.is(Blocks.SLIME_BLOCK);
                Iterator iterator = list.iterator();

                while (true) {
                    Entity entity;

                    while (true) {
                        if (!iterator.hasNext()) {
                            return;
                        }

                        entity = (Entity) iterator.next();
                        if (entity.getPistonPushReaction() != PushReaction.IGNORE) {
                            if (!flag) {
                                break;
                            }

                            if (!(entity instanceof ServerPlayer)) {
                                Vec3 vec3 = entity.getDeltaMovement();
                                double d1 = vec3.x;
                                double d2 = vec3.y;
                                double d3 = vec3.z;

                                switch (direction.getAxis()) {
                                    case X:
                                        d1 = (double) direction.getStepX();
                                        break;
                                    case Y:
                                        d2 = (double) direction.getStepY();
                                        break;
                                    case Z:
                                        d3 = (double) direction.getStepZ();
                                }

                                entity.setDeltaMovement(d1, d2, d3);
                                break;
                            }
                        }
                    }

                    double d4 = 0.0D;

                    for (AABB aabb1 : list1) {
                        AABB aabb2 = PistonMath.getMovementArea(moveByPositionAndProgress(pos, aabb1, self), direction, d0);
                        AABB aabb3 = entity.getBoundingBox();

                        if (aabb2.intersects(aabb3)) {
                            d4 = Math.max(d4, getMovement(aabb2, direction, aabb3));
                            if (d4 >= d0) {
                                break;
                            }
                        }
                    }

                    if (d4 > 0.0D) {
                        d4 = Math.min(d4, d0) + 0.01D;
                        moveEntityByPiston(direction, entity, d4, direction);
                        if (!self.extending && self.isSourcePiston) {
                            fixEntityWithinPistonBase(pos, entity, direction, d0);
                        }
                    }
                }
            }
        }
    }

    private static void moveEntityByPiston(Direction pistonDirection, Entity entity, double delta, Direction movement) {
        PistonMovingBlockEntity.NOCLIP.set(pistonDirection);
        Vec3 vec3 = entity.position();

        entity.move(MoverType.PISTON, new Vec3(delta * (double) movement.getStepX(), delta * (double) movement.getStepY(), delta * (double) movement.getStepZ()));
        entity.applyEffectsFromBlocks(vec3, entity.position());
        entity.removeLatestMovementRecording();
        PistonMovingBlockEntity.NOCLIP.set((Object) null);
    }

    private static void moveStuckEntities(Level level, BlockPos pos, float newProgress, PistonMovingBlockEntity self) {
        if (self.isStickyForEntities()) {
            Direction direction = self.getMovementDirection();

            if (direction.getAxis().isHorizontal()) {
                double d0 = self.movedState.getCollisionShape(level, pos).max(Direction.Axis.Y);
                AABB aabb = moveByPositionAndProgress(pos, new AABB(0.0D, d0, 0.0D, 1.0D, 1.5000010000000001D, 1.0D), self);
                double d1 = (double) (newProgress - self.progress);

                for (Entity entity : level.getEntities((Entity) null, aabb, (entity1) -> {
                    return matchesStickyCritera(aabb, entity1, pos);
                })) {
                    moveEntityByPiston(direction, entity, d1, direction);
                }

            }
        }
    }

    private static boolean matchesStickyCritera(AABB aabb, Entity entity, BlockPos pos) {
        return entity.getPistonPushReaction() == PushReaction.NORMAL && entity.onGround() && (entity.isSupportedBy(pos) || entity.getX() >= aabb.minX && entity.getX() <= aabb.maxX && entity.getZ() >= aabb.minZ && entity.getZ() <= aabb.maxZ);
    }

    private boolean isStickyForEntities() {
        return this.movedState.is(Blocks.HONEY_BLOCK);
    }

    public Direction getMovementDirection() {
        return this.extending ? this.direction : this.direction.getOpposite();
    }

    private static double getMovement(AABB aabbToBeOutsideOf, Direction movement, AABB aabb) {
        switch (movement) {
            case EAST:
                return aabbToBeOutsideOf.maxX - aabb.minX;
            case WEST:
                return aabb.maxX - aabbToBeOutsideOf.minX;
            case UP:
            default:
                return aabbToBeOutsideOf.maxY - aabb.minY;
            case DOWN:
                return aabb.maxY - aabbToBeOutsideOf.minY;
            case SOUTH:
                return aabbToBeOutsideOf.maxZ - aabb.minZ;
            case NORTH:
                return aabb.maxZ - aabbToBeOutsideOf.minZ;
        }
    }

    private static AABB moveByPositionAndProgress(BlockPos pos, AABB aabb, PistonMovingBlockEntity entity) {
        double d0 = (double) entity.getExtendedProgress(entity.progress);

        return aabb.move((double) pos.getX() + d0 * (double) entity.direction.getStepX(), (double) pos.getY() + d0 * (double) entity.direction.getStepY(), (double) pos.getZ() + d0 * (double) entity.direction.getStepZ());
    }

    private static void fixEntityWithinPistonBase(BlockPos pos, Entity entity, Direction direction, double deltaProgress) {
        AABB aabb = entity.getBoundingBox();
        AABB aabb1 = Shapes.block().bounds().move(pos);

        if (aabb.intersects(aabb1)) {
            Direction direction1 = direction.getOpposite();
            double d1 = getMovement(aabb1, direction1, aabb) + 0.01D;
            double d2 = getMovement(aabb1, direction1, aabb.intersect(aabb1)) + 0.01D;

            if (Math.abs(d1 - d2) < 0.01D) {
                d1 = Math.min(d1, deltaProgress) + 0.01D;
                moveEntityByPiston(direction, entity, d1, direction1);
            }
        }

    }

    public BlockState getMovedState() {
        return this.movedState;
    }

    public void finalTick() {
        if (this.level != null && (this.progressO < 1.0F || this.level.isClientSide())) {
            this.progress = 1.0F;
            this.progressO = this.progress;
            this.level.removeBlockEntity(this.worldPosition);
            this.setRemoved();
            if (this.level.getBlockState(this.worldPosition).is(Blocks.MOVING_PISTON)) {
                BlockState blockstate;

                if (this.isSourcePiston) {
                    blockstate = Blocks.AIR.defaultBlockState();
                } else {
                    blockstate = Block.updateFromNeighbourShapes(this.movedState, this.level, this.worldPosition);
                }

                this.level.setBlock(this.worldPosition, blockstate, 3);
                this.level.neighborChanged(this.worldPosition, blockstate.getBlock(), ExperimentalRedstoneUtils.initialOrientation(this.level, this.getPushDirection(), (Direction) null));
            }
        }

    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        this.finalTick();
    }

    public Direction getPushDirection() {
        return this.extending ? this.direction : this.direction.getOpposite();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PistonMovingBlockEntity entity) {
        entity.lastTicked = level.getGameTime();
        entity.progressO = entity.progress;
        if (entity.progressO >= 1.0F) {
            if (level.isClientSide() && entity.deathTicks < 5) {
                ++entity.deathTicks;
            } else {
                level.removeBlockEntity(pos);
                entity.setRemoved();
                if (level.getBlockState(pos).is(Blocks.MOVING_PISTON)) {
                    BlockState blockstate1 = Block.updateFromNeighbourShapes(entity.movedState, level, pos);

                    if (blockstate1.isAir()) {
                        level.setBlock(pos, entity.movedState, 340);
                        Block.updateOrDestroy(entity.movedState, blockstate1, level, pos, 3);
                    } else {
                        if (blockstate1.hasProperty(BlockStateProperties.WATERLOGGED) && (Boolean) blockstate1.getValue(BlockStateProperties.WATERLOGGED)) {
                            blockstate1 = (BlockState) blockstate1.setValue(BlockStateProperties.WATERLOGGED, false);
                        }

                        level.setBlock(pos, blockstate1, 67);
                        level.neighborChanged(pos, blockstate1.getBlock(), ExperimentalRedstoneUtils.initialOrientation(level, entity.getPushDirection(), (Direction) null));
                    }
                }

            }
        } else {
            float f = entity.progress + 0.5F;

            moveCollidedEntities(level, pos, f, entity);
            moveStuckEntities(level, pos, f, entity);
            entity.progress = f;
            if (entity.progress >= 1.0F) {
                entity.progress = 1.0F;
            }

        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.movedState = (BlockState) input.read("blockState", BlockState.CODEC).orElse(PistonMovingBlockEntity.DEFAULT_BLOCK_STATE);
        this.direction = (Direction) input.read("facing", Direction.LEGACY_ID_CODEC).orElse(Direction.DOWN);
        this.progress = input.getFloatOr("progress", 0.0F);
        this.progressO = this.progress;
        this.extending = input.getBooleanOr("extending", false);
        this.isSourcePiston = input.getBooleanOr("source", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("blockState", BlockState.CODEC, this.movedState);
        output.store("facing", Direction.LEGACY_ID_CODEC, this.direction);
        output.putFloat("progress", this.progressO);
        output.putBoolean("extending", this.extending);
        output.putBoolean("source", this.isSourcePiston);
    }

    public VoxelShape getCollisionShape(BlockGetter level, BlockPos pos) {
        VoxelShape voxelshape;

        if (!this.extending && this.isSourcePiston && this.movedState.getBlock() instanceof PistonBaseBlock) {
            voxelshape = ((BlockState) this.movedState.setValue(PistonBaseBlock.EXTENDED, true)).getCollisionShape(level, pos);
        } else {
            voxelshape = Shapes.empty();
        }

        Direction direction = (Direction) PistonMovingBlockEntity.NOCLIP.get();

        if ((double) this.progress < 1.0D && direction == this.getMovementDirection()) {
            return voxelshape;
        } else {
            BlockState blockstate;

            if (this.isSourcePiston()) {
                blockstate = (BlockState) ((BlockState) Blocks.PISTON_HEAD.defaultBlockState().setValue(PistonHeadBlock.FACING, this.direction)).setValue(PistonHeadBlock.SHORT, this.extending != 1.0F - this.progress < 0.25F);
            } else {
                blockstate = this.movedState;
            }

            float f = this.getExtendedProgress(this.progress);
            double d0 = (double) ((float) this.direction.getStepX() * f);
            double d1 = (double) ((float) this.direction.getStepY() * f);
            double d2 = (double) ((float) this.direction.getStepZ() * f);

            return Shapes.or(voxelshape, blockstate.getCollisionShape(level, pos).move(d0, d1, d2));
        }
    }

    public long getLastTicked() {
        return this.lastTicked;
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level.holderLookup(Registries.BLOCK).get(this.movedState.getBlock().builtInRegistryHolder().key()).isEmpty()) {
            this.movedState = Blocks.AIR.defaultBlockState();
        }

    }
}
