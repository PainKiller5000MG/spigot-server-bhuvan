package net.minecraft.world.level.block.entity;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.apache.commons.lang3.mutable.MutableInt;

public class BellBlockEntity extends BlockEntity {

    private static final int DURATION = 50;
    private static final int GLOW_DURATION = 60;
    private static final int MIN_TICKS_BETWEEN_SEARCHES = 60;
    private static final int MAX_RESONATION_TICKS = 40;
    private static final int TICKS_BEFORE_RESONATION = 5;
    private static final int SEARCH_RADIUS = 48;
    private static final int HEAR_BELL_RADIUS = 32;
    private static final int HIGHLIGHT_RAIDERS_RADIUS = 48;
    private long lastRingTimestamp;
    public int ticks;
    public boolean shaking;
    public Direction clickDirection;
    private List<LivingEntity> nearbyEntities;
    public boolean resonating;
    public int resonationTicks;

    public BellBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(BlockEntityType.BELL, worldPosition, blockState);
    }

    @Override
    public boolean triggerEvent(int b0, int b1) {
        if (b0 == 1) {
            this.updateEntities();
            this.resonationTicks = 0;
            this.clickDirection = Direction.from3DDataValue(b1);
            this.ticks = 0;
            this.shaking = true;
            return true;
        } else {
            return super.triggerEvent(b0, b1);
        }
    }

    private static void tick(Level level, BlockPos pos, BlockState state, BellBlockEntity entity, BellBlockEntity.ResonationEndAction onResonationEnd) {
        if (entity.shaking) {
            ++entity.ticks;
        }

        if (entity.ticks >= 50) {
            entity.shaking = false;
            entity.ticks = 0;
        }

        if (entity.ticks >= 5 && entity.resonationTicks == 0 && areRaidersNearby(pos, entity.nearbyEntities)) {
            entity.resonating = true;
            level.playSound((Entity) null, pos, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        if (entity.resonating) {
            if (entity.resonationTicks < 40) {
                ++entity.resonationTicks;
            } else {
                onResonationEnd.run(level, pos, entity.nearbyEntities);
                entity.resonating = false;
            }
        }

    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, BellBlockEntity entity) {
        tick(level, pos, state, entity, BellBlockEntity::showBellParticles);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BellBlockEntity entity) {
        tick(level, pos, state, entity, BellBlockEntity::makeRaidersGlow);
    }

    public void onHit(Direction clickDirection) {
        BlockPos blockpos = this.getBlockPos();

        this.clickDirection = clickDirection;
        if (this.shaking) {
            this.ticks = 0;
        } else {
            this.shaking = true;
        }

        this.level.blockEvent(blockpos, this.getBlockState().getBlock(), 1, clickDirection.get3DDataValue());
    }

    private void updateEntities() {
        BlockPos blockpos = this.getBlockPos();

        if (this.level.getGameTime() > this.lastRingTimestamp + 60L || this.nearbyEntities == null) {
            this.lastRingTimestamp = this.level.getGameTime();
            AABB aabb = (new AABB(blockpos)).inflate(48.0D);

            this.nearbyEntities = this.level.<LivingEntity>getEntitiesOfClass(LivingEntity.class, aabb);
        }

        if (!this.level.isClientSide()) {
            for (LivingEntity livingentity : this.nearbyEntities) {
                if (livingentity.isAlive() && !livingentity.isRemoved() && blockpos.closerToCenterThan(livingentity.position(), 32.0D)) {
                    livingentity.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, this.level.getGameTime());
                }
            }
        }

    }

    private static boolean areRaidersNearby(BlockPos bellPos, List<LivingEntity> nearbyEntities) {
        for (LivingEntity livingentity : nearbyEntities) {
            if (livingentity.isAlive() && !livingentity.isRemoved() && bellPos.closerToCenterThan(livingentity.position(), 32.0D) && livingentity.getType().is(EntityTypeTags.RAIDERS)) {
                return true;
            }
        }

        return false;
    }

    private static void makeRaidersGlow(Level level, BlockPos blockPos, List<LivingEntity> nearbyEntities) {
        nearbyEntities.stream().filter((livingentity) -> {
            return isRaiderWithinRange(blockPos, livingentity);
        }).forEach(BellBlockEntity::glow);
    }

    private static void showBellParticles(Level level, BlockPos bellPos, List<LivingEntity> nearbyEntities) {
        MutableInt mutableint = new MutableInt(16700985);
        int i = (int) nearbyEntities.stream().filter((livingentity) -> {
            return bellPos.closerToCenterThan(livingentity.position(), 48.0D);
        }).count();

        nearbyEntities.stream().filter((livingentity) -> {
            return isRaiderWithinRange(bellPos, livingentity);
        }).forEach((livingentity) -> {
            float f = 1.0F;
            double d0 = Math.sqrt((livingentity.getX() - (double) bellPos.getX()) * (livingentity.getX() - (double) bellPos.getX()) + (livingentity.getZ() - (double) bellPos.getZ()) * (livingentity.getZ() - (double) bellPos.getZ()));
            double d1 = (double) ((float) bellPos.getX() + 0.5F) + 1.0D / d0 * (livingentity.getX() - (double) bellPos.getX());
            double d2 = (double) ((float) bellPos.getZ() + 0.5F) + 1.0D / d0 * (livingentity.getZ() - (double) bellPos.getZ());
            int j = Mth.clamp((i - 21) / -2, 3, 15);

            for (int k = 0; k < j; ++k) {
                int l = mutableint.addAndGet(5);

                level.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, l), d1, (double) ((float) bellPos.getY() + 0.5F), d2, 0.0D, 0.0D, 0.0D);
            }

        });
    }

    private static boolean isRaiderWithinRange(BlockPos blockPos, LivingEntity entity) {
        return entity.isAlive() && !entity.isRemoved() && blockPos.closerToCenterThan(entity.position(), 48.0D) && entity.getType().is(EntityTypeTags.RAIDERS);
    }

    private static void glow(LivingEntity raider) {
        raider.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60));
    }

    @FunctionalInterface
    private interface ResonationEndAction {

        void run(Level level, BlockPos pos, List<LivingEntity> nearbyEntities);
    }
}
