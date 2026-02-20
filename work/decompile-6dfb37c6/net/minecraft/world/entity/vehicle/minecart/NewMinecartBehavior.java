package net.minecraft.world.entity.vehicle.minecart;

import com.mojang.datafixers.util.Pair;
import io.netty.buffer.ByteBuf;
import java.util.LinkedList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class NewMinecartBehavior extends MinecartBehavior {

    public static final int POS_ROT_LERP_TICKS = 3;
    public static final double ON_RAIL_Y_OFFSET = 0.1D;
    public static final double OPPOSING_SLOPES_REST_AT_SPEED_THRESHOLD = 0.005D;
    private NewMinecartBehavior.@Nullable StepPartialTicks cacheIndexAlpha;
    private int cachedLerpDelay;
    private float cachedPartialTick;
    private int lerpDelay = 0;
    public final List<NewMinecartBehavior.MinecartStep> lerpSteps = new LinkedList();
    public final List<NewMinecartBehavior.MinecartStep> currentLerpSteps = new LinkedList();
    public double currentLerpStepsTotalWeight = 0.0D;
    public NewMinecartBehavior.MinecartStep oldLerp;

    public NewMinecartBehavior(AbstractMinecart minecart) {
        super(minecart);
        this.oldLerp = NewMinecartBehavior.MinecartStep.ZERO;
    }

    @Override
    public void tick() {
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            BlockPos blockpos = this.minecart.getCurrentBlockPosOrRailBelow();
            BlockState blockstate = this.level().getBlockState(blockpos);

            if (this.minecart.isFirstTick()) {
                this.minecart.setOnRails(BaseRailBlock.isRail(blockstate));
                this.adjustToRails(blockpos, blockstate, true);
            }

            this.minecart.applyGravity();
            this.minecart.moveAlongTrack(serverlevel);
        } else {
            this.lerpClientPositionAndRotation();
            boolean flag = BaseRailBlock.isRail(this.level().getBlockState(this.minecart.getCurrentBlockPosOrRailBelow()));

            this.minecart.setOnRails(flag);
        }
    }

    private void lerpClientPositionAndRotation() {
        if (--this.lerpDelay <= 0) {
            this.setOldLerpValues();
            this.currentLerpSteps.clear();
            if (!this.lerpSteps.isEmpty()) {
                this.currentLerpSteps.addAll(this.lerpSteps);
                this.lerpSteps.clear();
                this.currentLerpStepsTotalWeight = 0.0D;

                for (NewMinecartBehavior.MinecartStep newminecartbehavior_minecartstep : this.currentLerpSteps) {
                    this.currentLerpStepsTotalWeight += (double) newminecartbehavior_minecartstep.weight;
                }

                this.lerpDelay = this.currentLerpStepsTotalWeight == 0.0D ? 0 : 3;
            }
        }

        if (this.cartHasPosRotLerp()) {
            this.setPos(this.getCartLerpPosition(1.0F));
            this.setDeltaMovement(this.getCartLerpMovements(1.0F));
            this.setXRot(this.getCartLerpXRot(1.0F));
            this.setYRot(this.getCartLerpYRot(1.0F));
        }

    }

    public void setOldLerpValues() {
        this.oldLerp = new NewMinecartBehavior.MinecartStep(this.position(), this.getDeltaMovement(), this.getYRot(), this.getXRot(), 0.0F);
    }

    public boolean cartHasPosRotLerp() {
        return !this.currentLerpSteps.isEmpty();
    }

    public float getCartLerpXRot(float partialTicks) {
        NewMinecartBehavior.StepPartialTicks newminecartbehavior_steppartialticks = this.getCurrentLerpStep(partialTicks);

        return Mth.rotLerp(newminecartbehavior_steppartialticks.partialTicksInStep, newminecartbehavior_steppartialticks.previousStep.xRot, newminecartbehavior_steppartialticks.currentStep.xRot);
    }

    public float getCartLerpYRot(float partialTicks) {
        NewMinecartBehavior.StepPartialTicks newminecartbehavior_steppartialticks = this.getCurrentLerpStep(partialTicks);

        return Mth.rotLerp(newminecartbehavior_steppartialticks.partialTicksInStep, newminecartbehavior_steppartialticks.previousStep.yRot, newminecartbehavior_steppartialticks.currentStep.yRot);
    }

    public Vec3 getCartLerpPosition(float partialTicks) {
        NewMinecartBehavior.StepPartialTicks newminecartbehavior_steppartialticks = this.getCurrentLerpStep(partialTicks);

        return Mth.lerp((double) newminecartbehavior_steppartialticks.partialTicksInStep, newminecartbehavior_steppartialticks.previousStep.position, newminecartbehavior_steppartialticks.currentStep.position);
    }

    public Vec3 getCartLerpMovements(float partialTicks) {
        NewMinecartBehavior.StepPartialTicks newminecartbehavior_steppartialticks = this.getCurrentLerpStep(partialTicks);

        return Mth.lerp((double) newminecartbehavior_steppartialticks.partialTicksInStep, newminecartbehavior_steppartialticks.previousStep.movement, newminecartbehavior_steppartialticks.currentStep.movement);
    }

    private NewMinecartBehavior.StepPartialTicks getCurrentLerpStep(float partialTick) {
        if (partialTick == this.cachedPartialTick && this.lerpDelay == this.cachedLerpDelay && this.cacheIndexAlpha != null) {
            return this.cacheIndexAlpha;
        } else {
            float f1 = ((float) (3 - this.lerpDelay) + partialTick) / 3.0F;
            float f2 = 0.0F;
            float f3 = 1.0F;
            boolean flag = false;

            int i;

            for (i = 0; i < this.currentLerpSteps.size(); ++i) {
                float f4 = ((NewMinecartBehavior.MinecartStep) this.currentLerpSteps.get(i)).weight;

                if (f4 > 0.0F) {
                    f2 += f4;
                    if ((double) f2 >= this.currentLerpStepsTotalWeight * (double) f1) {
                        float f5 = f2 - f4;

                        f3 = (float) (((double) f1 * this.currentLerpStepsTotalWeight - (double) f5) / (double) f4);
                        flag = true;
                        break;
                    }
                }
            }

            if (!flag) {
                i = this.currentLerpSteps.size() - 1;
            }

            NewMinecartBehavior.MinecartStep newminecartbehavior_minecartstep = (NewMinecartBehavior.MinecartStep) this.currentLerpSteps.get(i);
            NewMinecartBehavior.MinecartStep newminecartbehavior_minecartstep1 = i > 0 ? (NewMinecartBehavior.MinecartStep) this.currentLerpSteps.get(i - 1) : this.oldLerp;

            this.cacheIndexAlpha = new NewMinecartBehavior.StepPartialTicks(f3, newminecartbehavior_minecartstep, newminecartbehavior_minecartstep1);
            this.cachedLerpDelay = this.lerpDelay;
            this.cachedPartialTick = partialTick;
            return this.cacheIndexAlpha;
        }
    }

    public void adjustToRails(BlockPos targetBlockPos, BlockState currentState, boolean instant) {
        if (BaseRailBlock.isRail(currentState)) {
            RailShape railshape = (RailShape) currentState.getValue(((BaseRailBlock) currentState.getBlock()).getShapeProperty());
            Pair<Vec3i, Vec3i> pair = AbstractMinecart.exits(railshape);
            Vec3 vec3 = (new Vec3((Vec3i) pair.getFirst())).scale(0.5D);
            Vec3 vec31 = (new Vec3((Vec3i) pair.getSecond())).scale(0.5D);
            Vec3 vec32 = vec3.horizontal();
            Vec3 vec33 = vec31.horizontal();

            if (this.getDeltaMovement().length() > (double) 1.0E-5F && this.getDeltaMovement().dot(vec32) < this.getDeltaMovement().dot(vec33) || this.isDecending(vec33, railshape)) {
                Vec3 vec34 = vec32;

                vec32 = vec33;
                vec33 = vec34;
            }

            float f = 180.0F - (float) (Math.atan2(vec32.z, vec32.x) * 180.0D / Math.PI);

            f += this.minecart.isFlipped() ? 180.0F : 0.0F;
            Vec3 vec35 = this.position();
            boolean flag1 = vec3.x() != vec31.x() && vec3.z() != vec31.z();
            Vec3 vec36;

            if (flag1) {
                Vec3 vec37 = vec31.subtract(vec3);
                Vec3 vec38 = vec35.subtract(targetBlockPos.getBottomCenter()).subtract(vec3);
                Vec3 vec39 = vec37.scale(vec37.dot(vec38) / vec37.dot(vec37));

                vec36 = targetBlockPos.getBottomCenter().add(vec3).add(vec39);
                f = 180.0F - (float) (Math.atan2(vec39.z, vec39.x) * 180.0D / Math.PI);
                f += this.minecart.isFlipped() ? 180.0F : 0.0F;
            } else {
                boolean flag2 = vec3.subtract(vec31).x != 0.0D;
                boolean flag3 = vec3.subtract(vec31).z != 0.0D;

                vec36 = new Vec3(flag3 ? targetBlockPos.getCenter().x : vec35.x, (double) targetBlockPos.getY(), flag2 ? targetBlockPos.getCenter().z : vec35.z);
            }

            Vec3 vec310 = vec36.subtract(vec35);

            this.setPos(vec35.add(vec310));
            float f1 = 0.0F;
            boolean flag4 = vec3.y() != vec31.y();

            if (flag4) {
                Vec3 vec311 = targetBlockPos.getBottomCenter().add(vec33);
                double d0 = vec311.distanceTo(this.position());

                this.setPos(this.position().add(0.0D, d0 + 0.1D, 0.0D));
                f1 = this.minecart.isFlipped() ? 45.0F : -45.0F;
            } else {
                this.setPos(this.position().add(0.0D, 0.1D, 0.0D));
            }

            this.setRotation(f, f1);
            double d1 = vec35.distanceTo(this.position());

            if (d1 > 0.0D) {
                this.lerpSteps.add(new NewMinecartBehavior.MinecartStep(this.position(), this.getDeltaMovement(), this.getYRot(), this.getXRot(), instant ? 0.0F : (float) d1));
            }

        }
    }

    private void setRotation(float yRot, float xRot) {
        double d0 = (double) Math.abs(yRot - this.getYRot());

        if (d0 >= 175.0D && d0 <= 185.0D) {
            this.minecart.setFlipped(!this.minecart.isFlipped());
            yRot -= 180.0F;
            xRot *= -1.0F;
        }

        xRot = Math.clamp(xRot, -45.0F, 45.0F);
        this.setXRot(xRot % 360.0F);
        this.setYRot(yRot % 360.0F);
    }

    @Override
    public void moveAlongTrack(ServerLevel level) {
        for (NewMinecartBehavior.TrackIteration newminecartbehavior_trackiteration = new NewMinecartBehavior.TrackIteration(); newminecartbehavior_trackiteration.shouldIterate() && this.minecart.isAlive(); newminecartbehavior_trackiteration.firstIteration = false) {
            Vec3 vec3 = this.getDeltaMovement();
            BlockPos blockpos = this.minecart.getCurrentBlockPosOrRailBelow();
            BlockState blockstate = this.level().getBlockState(blockpos);
            boolean flag = BaseRailBlock.isRail(blockstate);

            if (this.minecart.isOnRails() != flag) {
                this.minecart.setOnRails(flag);
                this.adjustToRails(blockpos, blockstate, false);
            }

            if (flag) {
                this.minecart.resetFallDistance();
                this.minecart.setOldPosAndRot();
                if (blockstate.is(Blocks.ACTIVATOR_RAIL)) {
                    this.minecart.activateMinecart(level, blockpos.getX(), blockpos.getY(), blockpos.getZ(), (Boolean) blockstate.getValue(PoweredRailBlock.POWERED));
                }

                RailShape railshape = (RailShape) blockstate.getValue(((BaseRailBlock) blockstate.getBlock()).getShapeProperty());
                Vec3 vec31 = this.calculateTrackSpeed(level, vec3.horizontal(), newminecartbehavior_trackiteration, blockpos, blockstate, railshape);

                if (newminecartbehavior_trackiteration.firstIteration) {
                    newminecartbehavior_trackiteration.movementLeft = vec31.horizontalDistance();
                } else {
                    newminecartbehavior_trackiteration.movementLeft += vec31.horizontalDistance() - vec3.horizontalDistance();
                }

                this.setDeltaMovement(vec31);
                newminecartbehavior_trackiteration.movementLeft = this.minecart.makeStepAlongTrack(blockpos, railshape, newminecartbehavior_trackiteration.movementLeft);
            } else {
                this.minecart.comeOffTrack(level);
                newminecartbehavior_trackiteration.movementLeft = 0.0D;
            }

            Vec3 vec32 = this.position();
            Vec3 vec33 = vec32.subtract(this.minecart.oldPosition());
            double d0 = vec33.length();

            if (d0 > (double) 1.0E-5F) {
                if (vec33.horizontalDistanceSqr() <= (double) 1.0E-5F) {
                    if (!this.minecart.isOnRails()) {
                        this.setXRot(this.minecart.onGround() ? 0.0F : Mth.rotLerp(0.2F, this.getXRot(), 0.0F));
                    }
                } else {
                    float f = 180.0F - (float) (Math.atan2(vec33.z, vec33.x) * 180.0D / Math.PI);
                    float f1 = this.minecart.onGround() && !this.minecart.isOnRails() ? 0.0F : 90.0F - (float) (Math.atan2(vec33.horizontalDistance(), vec33.y) * 180.0D / Math.PI);

                    f += this.minecart.isFlipped() ? 180.0F : 0.0F;
                    f1 *= this.minecart.isFlipped() ? -1.0F : 1.0F;
                    this.setRotation(f, f1);
                }

                this.lerpSteps.add(new NewMinecartBehavior.MinecartStep(vec32, this.getDeltaMovement(), this.getYRot(), this.getXRot(), (float) Math.min(d0, this.getMaxSpeed(level))));
            } else if (vec3.horizontalDistanceSqr() > 0.0D) {
                this.lerpSteps.add(new NewMinecartBehavior.MinecartStep(vec32, this.getDeltaMovement(), this.getYRot(), this.getXRot(), 1.0F));
            }

            if (d0 > (double) 1.0E-5F || newminecartbehavior_trackiteration.firstIteration) {
                this.minecart.applyEffectsFromBlocks();
                this.minecart.applyEffectsFromBlocks();
            }
        }

    }

    private Vec3 calculateTrackSpeed(ServerLevel level, Vec3 deltaMovement, NewMinecartBehavior.TrackIteration trackIteration, BlockPos currentPos, BlockState currentState, RailShape shape) {
        Vec3 vec31 = deltaMovement;

        if (!trackIteration.hasGainedSlopeSpeed) {
            Vec3 vec32 = this.calculateSlopeSpeed(deltaMovement, shape);

            if (vec32.horizontalDistanceSqr() != deltaMovement.horizontalDistanceSqr()) {
                trackIteration.hasGainedSlopeSpeed = true;
                vec31 = vec32;
            }
        }

        if (trackIteration.firstIteration) {
            Vec3 vec33 = this.calculatePlayerInputSpeed(vec31);

            if (vec33.horizontalDistanceSqr() != vec31.horizontalDistanceSqr()) {
                trackIteration.hasHalted = true;
                vec31 = vec33;
            }
        }

        if (!trackIteration.hasHalted) {
            Vec3 vec34 = this.calculateHaltTrackSpeed(vec31, currentState);

            if (vec34.horizontalDistanceSqr() != vec31.horizontalDistanceSqr()) {
                trackIteration.hasHalted = true;
                vec31 = vec34;
            }
        }

        if (trackIteration.firstIteration) {
            vec31 = this.minecart.applyNaturalSlowdown(vec31);
            if (vec31.lengthSqr() > 0.0D) {
                double d0 = Math.min(vec31.length(), this.minecart.getMaxSpeed(level));

                vec31 = vec31.normalize().scale(d0);
            }
        }

        if (!trackIteration.hasBoosted) {
            Vec3 vec35 = this.calculateBoostTrackSpeed(vec31, currentPos, currentState);

            if (vec35.horizontalDistanceSqr() != vec31.horizontalDistanceSqr()) {
                trackIteration.hasBoosted = true;
                vec31 = vec35;
            }
        }

        return vec31;
    }

    private Vec3 calculateSlopeSpeed(Vec3 deltaMovement, RailShape shape) {
        double d0 = Math.max(0.0078125D, deltaMovement.horizontalDistance() * 0.02D);

        if (this.minecart.isInWater()) {
            d0 *= 0.2D;
        }

        Vec3 vec31;

        switch (shape) {
            case ASCENDING_EAST:
                vec31 = deltaMovement.add(-d0, 0.0D, 0.0D);
                break;
            case ASCENDING_WEST:
                vec31 = deltaMovement.add(d0, 0.0D, 0.0D);
                break;
            case ASCENDING_NORTH:
                vec31 = deltaMovement.add(0.0D, 0.0D, d0);
                break;
            case ASCENDING_SOUTH:
                vec31 = deltaMovement.add(0.0D, 0.0D, -d0);
                break;
            default:
                vec31 = deltaMovement;
        }

        return vec31;
    }

    private Vec3 calculatePlayerInputSpeed(Vec3 deltaMovement) {
        Entity entity = this.minecart.getFirstPassenger();

        if (entity instanceof ServerPlayer serverplayer) {
            Vec3 vec31 = serverplayer.getLastClientMoveIntent();

            if (vec31.lengthSqr() > 0.0D) {
                Vec3 vec32 = vec31.normalize();
                double d0 = deltaMovement.horizontalDistanceSqr();

                if (vec32.lengthSqr() > 0.0D && d0 < 0.01D) {
                    return deltaMovement.add((new Vec3(vec32.x, 0.0D, vec32.z)).normalize().scale(0.001D));
                }
            }

            return deltaMovement;
        } else {
            return deltaMovement;
        }
    }

    private Vec3 calculateHaltTrackSpeed(Vec3 deltaMovement, BlockState state) {
        return state.is(Blocks.POWERED_RAIL) && !(Boolean) state.getValue(PoweredRailBlock.POWERED) ? (deltaMovement.length() < 0.03D ? Vec3.ZERO : deltaMovement.scale(0.5D)) : deltaMovement;
    }

    private Vec3 calculateBoostTrackSpeed(Vec3 deltaMovement, BlockPos pos, BlockState state) {
        if (state.is(Blocks.POWERED_RAIL) && (Boolean) state.getValue(PoweredRailBlock.POWERED)) {
            if (deltaMovement.length() > 0.01D) {
                return deltaMovement.normalize().scale(deltaMovement.length() + 0.06D);
            } else {
                Vec3 vec31 = this.minecart.getRedstoneDirection(pos);

                return vec31.lengthSqr() <= 0.0D ? deltaMovement : vec31.scale(deltaMovement.length() + 0.2D);
            }
        } else {
            return deltaMovement;
        }
    }

    @Override
    public double stepAlongTrack(BlockPos pos, RailShape shape, double movementLeft) {
        if (movementLeft < (double) 1.0E-5F) {
            return 0.0D;
        } else {
            Vec3 vec3 = this.position();
            Pair<Vec3i, Vec3i> pair = AbstractMinecart.exits(shape);
            Vec3i vec3i = (Vec3i) pair.getFirst();
            Vec3i vec3i1 = (Vec3i) pair.getSecond();
            Vec3 vec31 = this.getDeltaMovement().horizontal();

            if (vec31.length() < (double) 1.0E-5F) {
                this.setDeltaMovement(Vec3.ZERO);
                return 0.0D;
            } else {
                boolean flag = vec3i.getY() != vec3i1.getY();
                Vec3 vec32 = (new Vec3(vec3i1)).scale(0.5D).horizontal();
                Vec3 vec33 = (new Vec3(vec3i)).scale(0.5D).horizontal();

                if (vec31.dot(vec33) < vec31.dot(vec32)) {
                    vec33 = vec32;
                }

                Vec3 vec34 = pos.getBottomCenter().add(vec33).add(0.0D, 0.1D, 0.0D).add(vec33.normalize().scale((double) 1.0E-5F));

                if (flag && !this.isDecending(vec31, shape)) {
                    vec34 = vec34.add(0.0D, 1.0D, 0.0D);
                }

                Vec3 vec35 = vec34.subtract(this.position()).normalize();

                vec31 = vec35.scale(vec31.length() / vec35.horizontalDistance());
                Vec3 vec36 = vec3.add(vec31.normalize().scale(movementLeft * (double) (flag ? Mth.SQRT_OF_TWO : 1.0F)));

                if (vec3.distanceToSqr(vec34) <= vec3.distanceToSqr(vec36)) {
                    movementLeft = vec34.subtract(vec36).horizontalDistance();
                    vec36 = vec34;
                } else {
                    movementLeft = 0.0D;
                }

                this.minecart.move(MoverType.SELF, vec36.subtract(vec3));
                BlockState blockstate = this.level().getBlockState(BlockPos.containing(vec36));

                if (flag) {
                    if (BaseRailBlock.isRail(blockstate)) {
                        RailShape railshape1 = (RailShape) blockstate.getValue(((BaseRailBlock) blockstate.getBlock()).getShapeProperty());

                        if (this.restAtVShape(shape, railshape1)) {
                            return 0.0D;
                        }
                    }

                    double d1 = vec34.horizontal().distanceTo(this.position().horizontal());
                    double d2 = vec34.y + (this.isDecending(vec31, shape) ? d1 : -d1);

                    if (this.position().y < d2) {
                        this.setPos(this.position().x, d2, this.position().z);
                    }
                }

                if (this.position().distanceTo(vec3) < (double) 1.0E-5F && vec36.distanceTo(vec3) > (double) 1.0E-5F) {
                    this.setDeltaMovement(Vec3.ZERO);
                    return 0.0D;
                } else {
                    this.setDeltaMovement(vec31);
                    return movementLeft;
                }
            }
        }
    }

    private boolean restAtVShape(RailShape currentRailShape, RailShape newRailShape) {
        if (this.getDeltaMovement().lengthSqr() < 0.005D && newRailShape.isSlope() && this.isDecending(this.getDeltaMovement(), currentRailShape) && !this.isDecending(this.getDeltaMovement(), newRailShape)) {
            this.setDeltaMovement(Vec3.ZERO);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public double getMaxSpeed(ServerLevel level) {
        return (double) (Integer) level.getGameRules().get(GameRules.MAX_MINECART_SPEED) * (this.minecart.isInWater() ? 0.5D : 1.0D) / 20.0D;
    }

    private boolean isDecending(Vec3 movement, RailShape shape) {
        boolean flag;

        switch (shape) {
            case ASCENDING_EAST:
                flag = movement.x < 0.0D;
                break;
            case ASCENDING_WEST:
                flag = movement.x > 0.0D;
                break;
            case ASCENDING_NORTH:
                flag = movement.z > 0.0D;
                break;
            case ASCENDING_SOUTH:
                flag = movement.z < 0.0D;
                break;
            default:
                flag = false;
        }

        return flag;
    }

    @Override
    public double getSlowdownFactor() {
        return this.minecart.isVehicle() ? 0.997D : 0.975D;
    }

    @Override
    public boolean pushAndPickupEntities() {
        boolean flag = this.pickupEntities(this.minecart.getBoundingBox().inflate(0.2D, 0.0D, 0.2D));

        if (!this.minecart.horizontalCollision && !this.minecart.verticalCollision) {
            return false;
        } else {
            boolean flag1 = this.pushEntities(this.minecart.getBoundingBox().inflate(1.0E-7D));

            return flag && !flag1;
        }
    }

    public boolean pickupEntities(AABB hitbox) {
        if (this.minecart.isRideable() && !this.minecart.isVehicle()) {
            List<Entity> list = this.level().getEntities(this.minecart, hitbox, EntitySelector.pushableBy(this.minecart));

            if (!list.isEmpty()) {
                for (Entity entity : list) {
                    if (!(entity instanceof Player) && !(entity instanceof IronGolem) && !(entity instanceof AbstractMinecart) && !this.minecart.isVehicle() && !entity.isPassenger()) {
                        boolean flag = entity.startRiding(this.minecart);

                        if (flag) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    public boolean pushEntities(AABB hitbox) {
        boolean flag = false;

        if (this.minecart.isRideable()) {
            List<Entity> list = this.level().getEntities(this.minecart, hitbox, EntitySelector.pushableBy(this.minecart));

            if (!list.isEmpty()) {
                for (Entity entity : list) {
                    if (entity instanceof Player || entity instanceof IronGolem || entity instanceof AbstractMinecart || this.minecart.isVehicle() || entity.isPassenger()) {
                        entity.push((Entity) this.minecart);
                        flag = true;
                    }
                }
            }
        } else {
            for (Entity entity1 : this.level().getEntities(this.minecart, hitbox)) {
                if (!this.minecart.hasPassenger(entity1) && entity1.isPushable() && entity1 instanceof AbstractMinecart) {
                    entity1.push((Entity) this.minecart);
                    flag = true;
                }
            }
        }

        return flag;
    }

    public static record MinecartStep(Vec3 position, Vec3 movement, float yRot, float xRot, float weight) {

        public static final StreamCodec<ByteBuf, NewMinecartBehavior.MinecartStep> STREAM_CODEC = StreamCodec.composite(Vec3.STREAM_CODEC, NewMinecartBehavior.MinecartStep::position, Vec3.STREAM_CODEC, NewMinecartBehavior.MinecartStep::movement, ByteBufCodecs.ROTATION_BYTE, NewMinecartBehavior.MinecartStep::yRot, ByteBufCodecs.ROTATION_BYTE, NewMinecartBehavior.MinecartStep::xRot, ByteBufCodecs.FLOAT, NewMinecartBehavior.MinecartStep::weight, NewMinecartBehavior.MinecartStep::new);
        public static NewMinecartBehavior.MinecartStep ZERO = new NewMinecartBehavior.MinecartStep(Vec3.ZERO, Vec3.ZERO, 0.0F, 0.0F, 0.0F);
    }

    private static class TrackIteration {

        double movementLeft = 0.0D;
        boolean firstIteration = true;
        boolean hasGainedSlopeSpeed = false;
        boolean hasHalted = false;
        boolean hasBoosted = false;

        private TrackIteration() {}

        public boolean shouldIterate() {
            return this.firstIteration || this.movementLeft > (double) 1.0E-5F;
        }
    }

    private static record StepPartialTicks(float partialTicksInStep, NewMinecartBehavior.MinecartStep currentStep, NewMinecartBehavior.MinecartStep previousStep) {

    }
}
