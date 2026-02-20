package net.minecraft.world.entity.vehicle.minecart;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class OldMinecartBehavior extends MinecartBehavior {

    private static final double MINECART_RIDABLE_THRESHOLD = 0.01D;
    private static final double MAX_SPEED_IN_WATER = 0.2D;
    private static final double MAX_SPEED_ON_LAND = 0.4D;
    private static final double ABSOLUTE_MAX_SPEED = 0.4D;
    private final InterpolationHandler interpolation;
    private Vec3 targetDeltaMovement;

    public OldMinecartBehavior(AbstractMinecart minecart) {
        super(minecart);
        this.targetDeltaMovement = Vec3.ZERO;
        this.interpolation = new InterpolationHandler(minecart, this::onInterpolation);
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return this.interpolation;
    }

    public void onInterpolation(InterpolationHandler interpolation) {
        this.setDeltaMovement(this.targetDeltaMovement);
    }

    @Override
    public void lerpMotion(Vec3 movement) {
        this.targetDeltaMovement = movement;
        this.setDeltaMovement(this.targetDeltaMovement);
    }

    @Override
    public void tick() {
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            this.minecart.applyGravity();
            BlockPos blockpos = this.minecart.getCurrentBlockPosOrRailBelow();
            BlockState blockstate = this.level().getBlockState(blockpos);
            boolean flag = BaseRailBlock.isRail(blockstate);

            this.minecart.setOnRails(flag);
            if (flag) {
                this.moveAlongTrack(serverlevel);
                if (blockstate.is(Blocks.ACTIVATOR_RAIL)) {
                    this.minecart.activateMinecart(serverlevel, blockpos.getX(), blockpos.getY(), blockpos.getZ(), (Boolean) blockstate.getValue(PoweredRailBlock.POWERED));
                }
            } else {
                this.minecart.comeOffTrack(serverlevel);
            }

            this.minecart.applyEffectsFromBlocks();
            this.setXRot(0.0F);
            double d0 = this.minecart.xo - this.getX();
            double d1 = this.minecart.zo - this.getZ();

            if (d0 * d0 + d1 * d1 > 0.001D) {
                this.setYRot((float) (Mth.atan2(d1, d0) * 180.0D / Math.PI));
                if (this.minecart.isFlipped()) {
                    this.setYRot(this.getYRot() + 180.0F);
                }
            }

            double d2 = (double) Mth.wrapDegrees(this.getYRot() - this.minecart.yRotO);

            if (d2 < -170.0D || d2 >= 170.0D) {
                this.setYRot(this.getYRot() + 180.0F);
                this.minecart.setFlipped(!this.minecart.isFlipped());
            }

            this.setXRot(this.getXRot() % 360.0F);
            this.setYRot(this.getYRot() % 360.0F);
            this.pushAndPickupEntities();
        } else {
            if (this.interpolation.hasActiveInterpolation()) {
                this.interpolation.interpolate();
            } else {
                this.minecart.reapplyPosition();
                this.setXRot(this.getXRot() % 360.0F);
                this.setYRot(this.getYRot() % 360.0F);
            }

        }
    }

    @Override
    public void moveAlongTrack(ServerLevel level) {
        BlockPos blockpos = this.minecart.getCurrentBlockPosOrRailBelow();
        BlockState blockstate = this.level().getBlockState(blockpos);

        this.minecart.resetFallDistance();
        double d0 = this.minecart.getX();
        double d1 = this.minecart.getY();
        double d2 = this.minecart.getZ();
        Vec3 vec3 = this.getPos(d0, d1, d2);

        d1 = (double) blockpos.getY();
        boolean flag = false;
        boolean flag1 = false;

        if (blockstate.is(Blocks.POWERED_RAIL)) {
            flag = (Boolean) blockstate.getValue(PoweredRailBlock.POWERED);
            flag1 = !flag;
        }

        double d3 = 0.0078125D;

        if (this.minecart.isInWater()) {
            d3 *= 0.2D;
        }

        Vec3 vec31 = this.getDeltaMovement();
        RailShape railshape = (RailShape) blockstate.getValue(((BaseRailBlock) blockstate.getBlock()).getShapeProperty());

        switch (railshape) {
            case ASCENDING_EAST:
                this.setDeltaMovement(vec31.add(-d3, 0.0D, 0.0D));
                ++d1;
                break;
            case ASCENDING_WEST:
                this.setDeltaMovement(vec31.add(d3, 0.0D, 0.0D));
                ++d1;
                break;
            case ASCENDING_NORTH:
                this.setDeltaMovement(vec31.add(0.0D, 0.0D, d3));
                ++d1;
                break;
            case ASCENDING_SOUTH:
                this.setDeltaMovement(vec31.add(0.0D, 0.0D, -d3));
                ++d1;
        }

        vec31 = this.getDeltaMovement();
        Pair<Vec3i, Vec3i> pair = AbstractMinecart.exits(railshape);
        Vec3i vec3i = (Vec3i) pair.getFirst();
        Vec3i vec3i1 = (Vec3i) pair.getSecond();
        double d4 = (double) (vec3i1.getX() - vec3i.getX());
        double d5 = (double) (vec3i1.getZ() - vec3i.getZ());
        double d6 = Math.sqrt(d4 * d4 + d5 * d5);
        double d7 = vec31.x * d4 + vec31.z * d5;

        if (d7 < 0.0D) {
            d4 = -d4;
            d5 = -d5;
        }

        double d8 = Math.min(2.0D, vec31.horizontalDistance());

        vec31 = new Vec3(d8 * d4 / d6, vec31.y, d8 * d5 / d6);
        this.setDeltaMovement(vec31);
        Entity entity = this.minecart.getFirstPassenger();
        Entity entity1 = this.minecart.getFirstPassenger();
        Vec3 vec32;

        if (entity1 instanceof ServerPlayer serverplayer) {
            vec32 = serverplayer.getLastClientMoveIntent();
        } else {
            vec32 = Vec3.ZERO;
        }

        if (entity instanceof Player && vec32.lengthSqr() > 0.0D) {
            Vec3 vec33 = vec32.normalize();
            double d9 = this.getDeltaMovement().horizontalDistanceSqr();

            if (vec33.lengthSqr() > 0.0D && d9 < 0.01D) {
                this.setDeltaMovement(this.getDeltaMovement().add(vec32.x * 0.001D, 0.0D, vec32.z * 0.001D));
                flag1 = false;
            }
        }

        if (flag1) {
            double d10 = this.getDeltaMovement().horizontalDistance();

            if (d10 < 0.03D) {
                this.setDeltaMovement(Vec3.ZERO);
            } else {
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.5D, 0.0D, 0.5D));
            }
        }

        double d11 = (double) blockpos.getX() + 0.5D + (double) vec3i.getX() * 0.5D;
        double d12 = (double) blockpos.getZ() + 0.5D + (double) vec3i.getZ() * 0.5D;
        double d13 = (double) blockpos.getX() + 0.5D + (double) vec3i1.getX() * 0.5D;
        double d14 = (double) blockpos.getZ() + 0.5D + (double) vec3i1.getZ() * 0.5D;

        d4 = d13 - d11;
        d5 = d14 - d12;
        double d15;

        if (d4 == 0.0D) {
            d15 = d2 - (double) blockpos.getZ();
        } else if (d5 == 0.0D) {
            d15 = d0 - (double) blockpos.getX();
        } else {
            double d16 = d0 - d11;
            double d17 = d2 - d12;

            d15 = (d16 * d4 + d17 * d5) * 2.0D;
        }

        d0 = d11 + d4 * d15;
        d2 = d12 + d5 * d15;
        this.setPos(d0, d1, d2);
        double d18 = this.minecart.isVehicle() ? 0.75D : 1.0D;
        double d19 = this.minecart.getMaxSpeed(level);

        vec31 = this.getDeltaMovement();
        this.minecart.move(MoverType.SELF, new Vec3(Mth.clamp(d18 * vec31.x, -d19, d19), 0.0D, Mth.clamp(d18 * vec31.z, -d19, d19)));
        if (vec3i.getY() != 0 && Mth.floor(this.minecart.getX()) - blockpos.getX() == vec3i.getX() && Mth.floor(this.minecart.getZ()) - blockpos.getZ() == vec3i.getZ()) {
            this.setPos(this.minecart.getX(), this.minecart.getY() + (double) vec3i.getY(), this.minecart.getZ());
        } else if (vec3i1.getY() != 0 && Mth.floor(this.minecart.getX()) - blockpos.getX() == vec3i1.getX() && Mth.floor(this.minecart.getZ()) - blockpos.getZ() == vec3i1.getZ()) {
            this.setPos(this.minecart.getX(), this.minecart.getY() + (double) vec3i1.getY(), this.minecart.getZ());
        }

        this.setDeltaMovement(this.minecart.applyNaturalSlowdown(this.getDeltaMovement()));
        Vec3 vec34 = this.getPos(this.minecart.getX(), this.minecart.getY(), this.minecart.getZ());

        if (vec34 != null && vec3 != null) {
            double d20 = (vec3.y - vec34.y) * 0.05D;
            Vec3 vec35 = this.getDeltaMovement();
            double d21 = vec35.horizontalDistance();

            if (d21 > 0.0D) {
                this.setDeltaMovement(vec35.multiply((d21 + d20) / d21, 1.0D, (d21 + d20) / d21));
            }

            this.setPos(this.minecart.getX(), vec34.y, this.minecart.getZ());
        }

        int i = Mth.floor(this.minecart.getX());
        int j = Mth.floor(this.minecart.getZ());

        if (i != blockpos.getX() || j != blockpos.getZ()) {
            Vec3 vec36 = this.getDeltaMovement();
            double d22 = vec36.horizontalDistance();

            this.setDeltaMovement(d22 * (double) (i - blockpos.getX()), vec36.y, d22 * (double) (j - blockpos.getZ()));
        }

        if (flag) {
            Vec3 vec37 = this.getDeltaMovement();
            double d23 = vec37.horizontalDistance();

            if (d23 > 0.01D) {
                double d24 = 0.06D;

                this.setDeltaMovement(vec37.add(vec37.x / d23 * 0.06D, 0.0D, vec37.z / d23 * 0.06D));
            } else {
                Vec3 vec38 = this.getDeltaMovement();
                double d25 = vec38.x;
                double d26 = vec38.z;

                if (railshape == RailShape.EAST_WEST) {
                    if (this.minecart.isRedstoneConductor(blockpos.west())) {
                        d25 = 0.02D;
                    } else if (this.minecart.isRedstoneConductor(blockpos.east())) {
                        d25 = -0.02D;
                    }
                } else {
                    if (railshape != RailShape.NORTH_SOUTH) {
                        return;
                    }

                    if (this.minecart.isRedstoneConductor(blockpos.north())) {
                        d26 = 0.02D;
                    } else if (this.minecart.isRedstoneConductor(blockpos.south())) {
                        d26 = -0.02D;
                    }
                }

                this.setDeltaMovement(d25, vec38.y, d26);
            }
        }

    }

    public @Nullable Vec3 getPosOffs(double x, double y, double z, double offs) {
        int i = Mth.floor(x);
        int j = Mth.floor(y);
        int k = Mth.floor(z);

        if (this.level().getBlockState(new BlockPos(i, j - 1, k)).is(BlockTags.RAILS)) {
            --j;
        }

        BlockState blockstate = this.level().getBlockState(new BlockPos(i, j, k));

        if (BaseRailBlock.isRail(blockstate)) {
            RailShape railshape = (RailShape) blockstate.getValue(((BaseRailBlock) blockstate.getBlock()).getShapeProperty());

            y = (double) j;
            if (railshape.isSlope()) {
                y = (double) (j + 1);
            }

            Pair<Vec3i, Vec3i> pair = AbstractMinecart.exits(railshape);
            Vec3i vec3i = (Vec3i) pair.getFirst();
            Vec3i vec3i1 = (Vec3i) pair.getSecond();
            double d4 = (double) (vec3i1.getX() - vec3i.getX());
            double d5 = (double) (vec3i1.getZ() - vec3i.getZ());
            double d6 = Math.sqrt(d4 * d4 + d5 * d5);

            d4 /= d6;
            d5 /= d6;
            x += d4 * offs;
            z += d5 * offs;
            if (vec3i.getY() != 0 && Mth.floor(x) - i == vec3i.getX() && Mth.floor(z) - k == vec3i.getZ()) {
                y += (double) vec3i.getY();
            } else if (vec3i1.getY() != 0 && Mth.floor(x) - i == vec3i1.getX() && Mth.floor(z) - k == vec3i1.getZ()) {
                y += (double) vec3i1.getY();
            }

            return this.getPos(x, y, z);
        } else {
            return null;
        }
    }

    public @Nullable Vec3 getPos(double x, double y, double z) {
        int i = Mth.floor(x);
        int j = Mth.floor(y);
        int k = Mth.floor(z);

        if (this.level().getBlockState(new BlockPos(i, j - 1, k)).is(BlockTags.RAILS)) {
            --j;
        }

        BlockState blockstate = this.level().getBlockState(new BlockPos(i, j, k));

        if (BaseRailBlock.isRail(blockstate)) {
            RailShape railshape = (RailShape) blockstate.getValue(((BaseRailBlock) blockstate.getBlock()).getShapeProperty());
            Pair<Vec3i, Vec3i> pair = AbstractMinecart.exits(railshape);
            Vec3i vec3i = (Vec3i) pair.getFirst();
            Vec3i vec3i1 = (Vec3i) pair.getSecond();
            double d3 = (double) i + 0.5D + (double) vec3i.getX() * 0.5D;
            double d4 = (double) j + 0.0625D + (double) vec3i.getY() * 0.5D;
            double d5 = (double) k + 0.5D + (double) vec3i.getZ() * 0.5D;
            double d6 = (double) i + 0.5D + (double) vec3i1.getX() * 0.5D;
            double d7 = (double) j + 0.0625D + (double) vec3i1.getY() * 0.5D;
            double d8 = (double) k + 0.5D + (double) vec3i1.getZ() * 0.5D;
            double d9 = d6 - d3;
            double d10 = (d7 - d4) * 2.0D;
            double d11 = d8 - d5;
            double d12;

            if (d9 == 0.0D) {
                d12 = z - (double) k;
            } else if (d11 == 0.0D) {
                d12 = x - (double) i;
            } else {
                double d13 = x - d3;
                double d14 = z - d5;

                d12 = (d13 * d9 + d14 * d11) * 2.0D;
            }

            x = d3 + d9 * d12;
            y = d4 + d10 * d12;
            z = d5 + d11 * d12;
            if (d10 < 0.0D) {
                ++y;
            } else if (d10 > 0.0D) {
                y += 0.5D;
            }

            return new Vec3(x, y, z);
        } else {
            return null;
        }
    }

    @Override
    public double stepAlongTrack(BlockPos pos, RailShape shape, double movementLeft) {
        return 0.0D;
    }

    @Override
    public boolean pushAndPickupEntities() {
        AABB aabb = this.minecart.getBoundingBox().inflate((double) 0.2F, 0.0D, (double) 0.2F);

        if (this.minecart.isRideable() && this.getDeltaMovement().horizontalDistanceSqr() >= 0.01D) {
            List<Entity> list = this.level().getEntities(this.minecart, aabb, EntitySelector.pushableBy(this.minecart));

            if (!list.isEmpty()) {
                for (Entity entity : list) {
                    if (!(entity instanceof Player) && !(entity instanceof IronGolem) && !(entity instanceof AbstractMinecart) && !this.minecart.isVehicle() && !entity.isPassenger()) {
                        entity.startRiding(this.minecart);
                    } else {
                        entity.push((Entity) this.minecart);
                    }
                }
            }
        } else {
            for (Entity entity1 : this.level().getEntities(this.minecart, aabb)) {
                if (!this.minecart.hasPassenger(entity1) && entity1.isPushable() && entity1 instanceof AbstractMinecart) {
                    entity1.push((Entity) this.minecart);
                }
            }
        }

        return false;
    }

    @Override
    public Direction getMotionDirection() {
        return this.minecart.isFlipped() ? this.minecart.getDirection().getOpposite().getClockWise() : this.minecart.getDirection().getClockWise();
    }

    @Override
    public Vec3 getKnownMovement(Vec3 knownMovement) {
        return !Double.isNaN(knownMovement.x) && !Double.isNaN(knownMovement.y) && !Double.isNaN(knownMovement.z) ? new Vec3(Mth.clamp(knownMovement.x, -0.4D, 0.4D), knownMovement.y, Mth.clamp(knownMovement.z, -0.4D, 0.4D)) : Vec3.ZERO;
    }

    @Override
    public double getMaxSpeed(ServerLevel level) {
        return this.minecart.isInWater() ? 0.2D : 0.4D;
    }

    @Override
    public double getSlowdownFactor() {
        return this.minecart.isVehicle() ? 0.997D : 0.96D;
    }
}
