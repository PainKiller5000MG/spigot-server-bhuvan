package net.minecraft.world.entity.decoration;

import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;

public abstract class HangingEntity extends BlockAttachedEntity {

    private static final EntityDataAccessor<Direction> DATA_DIRECTION = SynchedEntityData.<Direction>defineId(HangingEntity.class, EntityDataSerializers.DIRECTION);
    private static final Direction DEFAULT_DIRECTION = Direction.SOUTH;

    protected HangingEntity(EntityType<? extends HangingEntity> type, Level level) {
        super(type, level);
    }

    protected HangingEntity(EntityType<? extends HangingEntity> type, Level level, BlockPos pos) {
        this(type, level);
        this.pos = pos;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(HangingEntity.DATA_DIRECTION, HangingEntity.DEFAULT_DIRECTION);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (accessor.equals(HangingEntity.DATA_DIRECTION)) {
            this.setDirection(this.getDirection());
        }

    }

    @Override
    public Direction getDirection() {
        return (Direction) this.entityData.get(HangingEntity.DATA_DIRECTION);
    }

    protected void setDirectionRaw(Direction direction) {
        this.entityData.set(HangingEntity.DATA_DIRECTION, direction);
    }

    public void setDirection(Direction direction) {
        Objects.requireNonNull(direction);
        Validate.isTrue(direction.getAxis().isHorizontal());
        this.setDirectionRaw(direction);
        this.setYRot((float) (direction.get2DDataValue() * 90));
        this.yRotO = this.getYRot();
        this.recalculateBoundingBox();
    }

    @Override
    protected void recalculateBoundingBox() {
        if (this.getDirection() != null) {
            AABB aabb = this.calculateBoundingBox(this.pos, this.getDirection());
            Vec3 vec3 = aabb.getCenter();

            this.setPosRaw(vec3.x, vec3.y, vec3.z);
            this.setBoundingBox(aabb);
        }
    }

    protected abstract AABB calculateBoundingBox(BlockPos pos, Direction direction);

    @Override
    public boolean survives() {
        if (this.hasLevelCollision(this.getPopBox())) {
            return false;
        } else {
            boolean flag = BlockPos.betweenClosedStream(this.calculateSupportBox()).allMatch((blockpos) -> {
                BlockState blockstate = this.level().getBlockState(blockpos);

                return blockstate.isSolid() || DiodeBlock.isDiode(blockstate);
            });

            return flag && this.canCoexist(false);
        }
    }

    protected AABB calculateSupportBox() {
        return this.getBoundingBox().move(this.getDirection().step().mul(-0.5F)).deflate(1.0E-7D);
    }

    protected boolean canCoexist(boolean allowIntersectingSameType) {
        Predicate<HangingEntity> predicate = (hangingentity) -> {
            boolean flag1 = !allowIntersectingSameType && hangingentity.getType() == this.getType();
            boolean flag2 = hangingentity.getDirection() == this.getDirection();

            return hangingentity != this && (flag1 || flag2);
        };

        return !this.level().hasEntities(EntityTypeTest.forClass(HangingEntity.class), this.getPopBox(), predicate);
    }

    protected boolean hasLevelCollision(AABB popBox) {
        Level level = this.level();

        return !level.noBlockCollision(this, popBox) || !level.noBorderCollision(this, popBox);
    }

    protected AABB getPopBox() {
        return this.getBoundingBox();
    }

    public abstract void playPlacementSound();

    @Override
    public ItemEntity spawnAtLocation(ServerLevel level, ItemStack itemStack, float yOffs) {
        ItemEntity itementity = new ItemEntity(this.level(), this.getX() + (double) ((float) this.getDirection().getStepX() * 0.15F), this.getY() + (double) yOffs, this.getZ() + (double) ((float) this.getDirection().getStepZ() * 0.15F), itemStack);

        itementity.setDefaultPickUpDelay();
        this.level().addFreshEntity(itementity);
        return itementity;
    }

    @Override
    public float rotate(Rotation rotation) {
        Direction direction = this.getDirection();

        if (direction.getAxis() != Direction.Axis.Y) {
            switch (rotation) {
                case CLOCKWISE_180:
                    direction = direction.getOpposite();
                    break;
                case COUNTERCLOCKWISE_90:
                    direction = direction.getCounterClockWise();
                    break;
                case CLOCKWISE_90:
                    direction = direction.getClockWise();
            }

            this.setDirection(direction);
        }

        float f = Mth.wrapDegrees(this.getYRot());
        float f1;

        switch (rotation) {
            case CLOCKWISE_180:
                f1 = f + 180.0F;
                break;
            case COUNTERCLOCKWISE_90:
                f1 = f + 90.0F;
                break;
            case CLOCKWISE_90:
                f1 = f + 270.0F;
                break;
            default:
                f1 = f;
        }

        return f1;
    }

    @Override
    public float mirror(Mirror mirror) {
        return this.rotate(mirror.getRotation(this.getDirection()));
    }
}
