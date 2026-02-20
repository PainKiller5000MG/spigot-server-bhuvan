package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class HoneyBlock extends HalfTransparentBlock {

    public static final MapCodec<HoneyBlock> CODEC = simpleCodec(HoneyBlock::new);
    private static final double SLIDE_STARTS_WHEN_VERTICAL_SPEED_IS_AT_LEAST = 0.13D;
    private static final double MIN_FALL_SPEED_TO_BE_CONSIDERED_SLIDING = 0.08D;
    private static final double THROTTLE_SLIDE_SPEED_TO = 0.05D;
    private static final int SLIDE_ADVANCEMENT_CHECK_INTERVAL = 20;
    private static final VoxelShape SHAPE = Block.column(14.0D, 0.0D, 15.0D);

    @Override
    public MapCodec<HoneyBlock> codec() {
        return HoneyBlock.CODEC;
    }

    public HoneyBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    private static boolean doesEntityDoHoneyBlockSlideEffects(Entity entity) {
        return entity instanceof LivingEntity || entity instanceof AbstractMinecart || entity instanceof PrimedTnt || entity instanceof AbstractBoat;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return HoneyBlock.SHAPE;
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
        entity.playSound(SoundEvents.HONEY_BLOCK_SLIDE, 1.0F, 1.0F);
        if (!level.isClientSide()) {
            level.broadcastEntityEvent(entity, (byte) 54);
        }

        if (entity.causeFallDamage(fallDistance, 0.2F, level.damageSources().fall())) {
            entity.playSound(this.soundType.getFallSound(), this.soundType.getVolume() * 0.5F, this.soundType.getPitch() * 0.75F);
        }

    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (this.isSlidingDown(pos, entity)) {
            this.maybeDoSlideAchievement(entity, pos);
            this.doSlideMovement(entity);
            this.maybeDoSlideEffects(level, entity);
        }

        super.entityInside(state, level, pos, entity, effectApplier, isPrecise);
    }

    private static double getOldDeltaY(double deltaY) {
        return deltaY / (double) 0.98F + 0.08D;
    }

    private static double getNewDeltaY(double deltaY) {
        return (deltaY - 0.08D) * (double) 0.98F;
    }

    private boolean isSlidingDown(BlockPos pos, Entity entity) {
        if (entity.onGround()) {
            return false;
        } else if (entity.getY() > (double) pos.getY() + 0.9375D - 1.0E-7D) {
            return false;
        } else if (getOldDeltaY(entity.getDeltaMovement().y) >= -0.08D) {
            return false;
        } else {
            double d0 = Math.abs((double) pos.getX() + 0.5D - entity.getX());
            double d1 = Math.abs((double) pos.getZ() + 0.5D - entity.getZ());
            double d2 = 0.4375D + (double) (entity.getBbWidth() / 2.0F);

            return d0 + 1.0E-7D > d2 || d1 + 1.0E-7D > d2;
        }
    }

    private void maybeDoSlideAchievement(Entity entity, BlockPos pos) {
        if (entity instanceof ServerPlayer && entity.level().getGameTime() % 20L == 0L) {
            CriteriaTriggers.HONEY_BLOCK_SLIDE.trigger((ServerPlayer) entity, entity.level().getBlockState(pos));
        }

    }

    private void doSlideMovement(Entity entity) {
        Vec3 vec3 = entity.getDeltaMovement();

        if (getOldDeltaY(entity.getDeltaMovement().y) < -0.13D) {
            double d0 = -0.05D / getOldDeltaY(entity.getDeltaMovement().y);

            entity.setDeltaMovement(new Vec3(vec3.x * d0, getNewDeltaY(-0.05D), vec3.z * d0));
        } else {
            entity.setDeltaMovement(new Vec3(vec3.x, getNewDeltaY(-0.05D), vec3.z));
        }

        entity.resetFallDistance();
    }

    private void maybeDoSlideEffects(Level level, Entity entity) {
        if (doesEntityDoHoneyBlockSlideEffects(entity)) {
            if (level.random.nextInt(5) == 0) {
                entity.playSound(SoundEvents.HONEY_BLOCK_SLIDE, 1.0F, 1.0F);
            }

            if (!level.isClientSide() && level.random.nextInt(5) == 0) {
                level.broadcastEntityEvent(entity, (byte) 53);
            }
        }

    }

    public static void showSlideParticles(Entity entity) {
        showParticles(entity, 5);
    }

    public static void showJumpParticles(Entity entity) {
        showParticles(entity, 10);
    }

    private static void showParticles(Entity entity, int count) {
        if (entity.level().isClientSide()) {
            BlockState blockstate = Blocks.HONEY_BLOCK.defaultBlockState();

            for (int j = 0; j < count; ++j) {
                entity.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, blockstate), entity.getX(), entity.getY(), entity.getZ(), 0.0D, 0.0D, 0.0D);
            }

        }
    }
}
