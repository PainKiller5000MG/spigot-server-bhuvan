package net.minecraft.world.level.block.entity;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ConduitBlockEntity extends BlockEntity {

    private static final int BLOCK_REFRESH_RATE = 2;
    private static final int EFFECT_DURATION = 13;
    private static final float ROTATION_SPEED = -0.0375F;
    private static final int MIN_ACTIVE_SIZE = 16;
    private static final int MIN_KILL_SIZE = 42;
    private static final int KILL_RANGE = 8;
    private static final Block[] VALID_BLOCKS = new Block[]{Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.SEA_LANTERN, Blocks.DARK_PRISMARINE};
    public int tickCount;
    private float activeRotation;
    private boolean isActive;
    private boolean isHunting;
    public final List<BlockPos> effectBlocks = Lists.newArrayList();
    public @Nullable EntityReference<LivingEntity> destroyTarget;
    private long nextAmbientSoundActivation;

    public ConduitBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(BlockEntityType.CONDUIT, worldPosition, blockState);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.destroyTarget = EntityReference.<LivingEntity>read(input, "Target");
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        EntityReference.store(this.destroyTarget, output, "Target");
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, ConduitBlockEntity entity) {
        ++entity.tickCount;
        long i = level.getGameTime();
        List<BlockPos> list = entity.effectBlocks;

        if (i % 40L == 0L) {
            entity.isActive = updateShape(level, pos, list);
            updateHunting(entity, list);
        }

        LivingEntity livingentity = EntityReference.getLivingEntity(entity.destroyTarget, level);

        animationTick(level, pos, list, livingentity, entity.tickCount);
        if (entity.isActive()) {
            ++entity.activeRotation;
        }

    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ConduitBlockEntity entity) {
        ++entity.tickCount;
        long i = level.getGameTime();
        List<BlockPos> list = entity.effectBlocks;

        if (i % 40L == 0L) {
            boolean flag = updateShape(level, pos, list);

            if (flag != entity.isActive) {
                SoundEvent soundevent = flag ? SoundEvents.CONDUIT_ACTIVATE : SoundEvents.CONDUIT_DEACTIVATE;

                level.playSound((Entity) null, pos, soundevent, SoundSource.BLOCKS, 1.0F, 1.0F);
            }

            entity.isActive = flag;
            updateHunting(entity, list);
            if (flag) {
                applyEffects(level, pos, list);
                updateAndAttackTarget((ServerLevel) level, pos, state, entity, list.size() >= 42);
            }
        }

        if (entity.isActive()) {
            if (i % 80L == 0L) {
                level.playSound((Entity) null, pos, SoundEvents.CONDUIT_AMBIENT, SoundSource.BLOCKS, 1.0F, 1.0F);
            }

            if (i > entity.nextAmbientSoundActivation) {
                entity.nextAmbientSoundActivation = i + 60L + (long) level.getRandom().nextInt(40);
                level.playSound((Entity) null, pos, SoundEvents.CONDUIT_AMBIENT_SHORT, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }

    }

    private static void updateHunting(ConduitBlockEntity entity, List<BlockPos> effectBlocks) {
        entity.setHunting(effectBlocks.size() >= 42);
    }

    private static boolean updateShape(Level level, BlockPos worldPosition, List<BlockPos> effectBlocks) {
        effectBlocks.clear();

        for (int i = -1; i <= 1; ++i) {
            for (int j = -1; j <= 1; ++j) {
                for (int k = -1; k <= 1; ++k) {
                    BlockPos blockpos1 = worldPosition.offset(i, j, k);

                    if (!level.isWaterAt(blockpos1)) {
                        return false;
                    }
                }
            }
        }

        for (int l = -2; l <= 2; ++l) {
            for (int i1 = -2; i1 <= 2; ++i1) {
                for (int j1 = -2; j1 <= 2; ++j1) {
                    int k1 = Math.abs(l);
                    int l1 = Math.abs(i1);
                    int i2 = Math.abs(j1);

                    if ((k1 > 1 || l1 > 1 || i2 > 1) && (l == 0 && (l1 == 2 || i2 == 2) || i1 == 0 && (k1 == 2 || i2 == 2) || j1 == 0 && (k1 == 2 || l1 == 2))) {
                        BlockPos blockpos2 = worldPosition.offset(l, i1, j1);
                        BlockState blockstate = level.getBlockState(blockpos2);

                        for (Block block : ConduitBlockEntity.VALID_BLOCKS) {
                            if (blockstate.is(block)) {
                                effectBlocks.add(blockpos2);
                            }
                        }
                    }
                }
            }
        }

        return effectBlocks.size() >= 16;
    }

    private static void applyEffects(Level level, BlockPos worldPosition, List<BlockPos> effectBlocks) {
        int i = effectBlocks.size();
        int j = i / 7 * 16;
        int k = worldPosition.getX();
        int l = worldPosition.getY();
        int i1 = worldPosition.getZ();
        AABB aabb = (new AABB((double) k, (double) l, (double) i1, (double) (k + 1), (double) (l + 1), (double) (i1 + 1))).inflate((double) j).expandTowards(0.0D, (double) level.getHeight(), 0.0D);
        List<Player> list1 = level.<Player>getEntitiesOfClass(Player.class, aabb);

        if (!list1.isEmpty()) {
            for (Player player : list1) {
                if (worldPosition.closerThan(player.blockPosition(), (double) j) && player.isInWaterOrRain()) {
                    player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 260, 0, true, true));
                }
            }

        }
    }

    private static void updateAndAttackTarget(ServerLevel level, BlockPos worldPosition, BlockState blockState, ConduitBlockEntity entity, boolean isActive) {
        EntityReference<LivingEntity> entityreference = updateDestroyTarget(entity.destroyTarget, level, worldPosition, isActive);
        LivingEntity livingentity = EntityReference.getLivingEntity(entityreference, level);

        if (livingentity != null) {
            level.playSound((Entity) null, livingentity.getX(), livingentity.getY(), livingentity.getZ(), SoundEvents.CONDUIT_ATTACK_TARGET, SoundSource.BLOCKS, 1.0F, 1.0F);
            livingentity.hurtServer(level, level.damageSources().magic(), 4.0F);
        }

        if (!Objects.equals(entityreference, entity.destroyTarget)) {
            entity.destroyTarget = entityreference;
            level.sendBlockUpdated(worldPosition, blockState, blockState, 2);
        }

    }

    private static @Nullable EntityReference<LivingEntity> updateDestroyTarget(@Nullable EntityReference<LivingEntity> target, ServerLevel level, BlockPos pos, boolean isActive) {
        if (!isActive) {
            return null;
        } else if (target == null) {
            return selectNewTarget(level, pos);
        } else {
            LivingEntity livingentity = EntityReference.getLivingEntity(target, level);

            return livingentity != null && livingentity.isAlive() && pos.closerThan(livingentity.blockPosition(), 8.0D) ? target : null;
        }
    }

    private static @Nullable EntityReference<LivingEntity> selectNewTarget(ServerLevel level, BlockPos pos) {
        List<LivingEntity> list = level.<LivingEntity>getEntitiesOfClass(LivingEntity.class, getDestroyRangeAABB(pos), (livingentity) -> {
            return livingentity instanceof Enemy && livingentity.isInWaterOrRain();
        });

        return list.isEmpty() ? null : EntityReference.of((LivingEntity) Util.getRandom(list, level.random));
    }

    public static AABB getDestroyRangeAABB(BlockPos worldPosition) {
        return (new AABB(worldPosition)).inflate(8.0D);
    }

    private static void animationTick(Level level, BlockPos worldPosition, List<BlockPos> effectBlocks, @Nullable Entity destroyTarget, int tickCount) {
        RandomSource randomsource = level.random;
        double d0 = (double) (Mth.sin((double) ((float) (tickCount + 35) * 0.1F)) / 2.0F + 0.5F);

        d0 = (d0 * d0 + d0) * (double) 0.3F;
        Vec3 vec3 = new Vec3((double) worldPosition.getX() + 0.5D, (double) worldPosition.getY() + 1.5D + d0, (double) worldPosition.getZ() + 0.5D);

        for (BlockPos blockpos1 : effectBlocks) {
            if (randomsource.nextInt(50) == 0) {
                BlockPos blockpos2 = blockpos1.subtract(worldPosition);
                float f = -0.5F + randomsource.nextFloat() + (float) blockpos2.getX();
                float f1 = -2.0F + randomsource.nextFloat() + (float) blockpos2.getY();
                float f2 = -0.5F + randomsource.nextFloat() + (float) blockpos2.getZ();

                level.addParticle(ParticleTypes.NAUTILUS, vec3.x, vec3.y, vec3.z, (double) f, (double) f1, (double) f2);
            }
        }

        if (destroyTarget != null) {
            Vec3 vec31 = new Vec3(destroyTarget.getX(), destroyTarget.getEyeY(), destroyTarget.getZ());
            float f3 = (-0.5F + randomsource.nextFloat()) * (3.0F + destroyTarget.getBbWidth());
            float f4 = -1.0F + randomsource.nextFloat() * destroyTarget.getBbHeight();
            float f5 = (-0.5F + randomsource.nextFloat()) * (3.0F + destroyTarget.getBbWidth());
            Vec3 vec32 = new Vec3((double) f3, (double) f4, (double) f5);

            level.addParticle(ParticleTypes.NAUTILUS, vec31.x, vec31.y, vec31.z, vec32.x, vec32.y, vec32.z);
        }

    }

    public boolean isActive() {
        return this.isActive;
    }

    public boolean isHunting() {
        return this.isHunting;
    }

    private void setHunting(boolean hunting) {
        this.isHunting = hunting;
    }

    public float getActiveRotation(float a) {
        return (this.activeRotation + a) * -0.0375F;
    }
}
