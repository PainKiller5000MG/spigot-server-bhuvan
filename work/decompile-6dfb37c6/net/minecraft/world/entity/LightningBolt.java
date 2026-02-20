package net.minecraft.world.entity;

import com.google.common.collect.BiMap;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class LightningBolt extends Entity {

    private static final int START_LIFE = 2;
    private static final double DAMAGE_RADIUS = 3.0D;
    private static final double DETECTION_RADIUS = 15.0D;
    public int life = 2;
    public long seed;
    public int flashes;
    public boolean visualOnly;
    private @Nullable ServerPlayer cause;
    private final Set<Entity> hitEntities = Sets.newHashSet();
    private int blocksSetOnFire;

    public LightningBolt(EntityType<? extends LightningBolt> type, Level level) {
        super(type, level);
        this.seed = this.random.nextLong();
        this.flashes = this.random.nextInt(3) + 1;
    }

    public void setVisualOnly(boolean visualOnly) {
        this.visualOnly = visualOnly;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.WEATHER;
    }

    public @Nullable ServerPlayer getCause() {
        return this.cause;
    }

    public void setCause(@Nullable ServerPlayer cause) {
        this.cause = cause;
    }

    private void powerLightningRod() {
        BlockPos blockpos = this.getStrikePosition();
        BlockState blockstate = this.level().getBlockState(blockpos);
        Block block = blockstate.getBlock();

        if (block instanceof LightningRodBlock lightningrodblock) {
            lightningrodblock.onLightningStrike(blockstate, this.level(), blockpos);
        }

    }

    @Override
    public void tick() {
        super.tick();
        if (this.life == 2) {
            if (this.level().isClientSide()) {
                this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 10000.0F, 0.8F + this.random.nextFloat() * 0.2F, false);
                this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 2.0F, 0.5F + this.random.nextFloat() * 0.2F, false);
            } else {
                Difficulty difficulty = this.level().getDifficulty();

                if (difficulty == Difficulty.NORMAL || difficulty == Difficulty.HARD) {
                    this.spawnFire(4);
                }

                this.powerLightningRod();
                clearCopperOnLightningStrike(this.level(), this.getStrikePosition());
                this.gameEvent(GameEvent.LIGHTNING_STRIKE);
            }
        }

        --this.life;
        if (this.life < 0) {
            if (this.flashes == 0) {
                if (this.level() instanceof ServerLevel) {
                    List<Entity> list = this.level().getEntities(this, new AABB(this.getX() - 15.0D, this.getY() - 15.0D, this.getZ() - 15.0D, this.getX() + 15.0D, this.getY() + 6.0D + 15.0D, this.getZ() + 15.0D), (entity) -> {
                        return entity.isAlive() && !this.hitEntities.contains(entity);
                    });

                    for (ServerPlayer serverplayer : ((ServerLevel) this.level()).getPlayers((serverplayer1) -> {
                        return serverplayer1.distanceTo(this) < 256.0F;
                    })) {
                        CriteriaTriggers.LIGHTNING_STRIKE.trigger(serverplayer, this, list);
                    }
                }

                this.discard();
            } else if (this.life < -this.random.nextInt(10)) {
                --this.flashes;
                this.life = 1;
                this.seed = this.random.nextLong();
                this.spawnFire(0);
            }
        }

        if (this.life >= 0) {
            if (!(this.level() instanceof ServerLevel)) {
                this.level().setSkyFlashTime(2);
            } else if (!this.visualOnly) {
                List<Entity> list1 = this.level().getEntities(this, new AABB(this.getX() - 3.0D, this.getY() - 3.0D, this.getZ() - 3.0D, this.getX() + 3.0D, this.getY() + 6.0D + 3.0D, this.getZ() + 3.0D), Entity::isAlive);

                for (Entity entity : list1) {
                    entity.thunderHit((ServerLevel) this.level(), this);
                }

                this.hitEntities.addAll(list1);
                if (this.cause != null) {
                    CriteriaTriggers.CHANNELED_LIGHTNING.trigger(this.cause, list1);
                }
            }
        }

    }

    private BlockPos getStrikePosition() {
        Vec3 vec3 = this.position();

        return BlockPos.containing(vec3.x, vec3.y - 1.0E-6D, vec3.z);
    }

    private void spawnFire(int additionalSources) {
        if (!this.visualOnly) {
            Level level = this.level();

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;
                BlockPos blockpos = this.blockPosition();

                if (!serverlevel.canSpreadFireAround(blockpos)) {
                    return;
                }

                BlockState blockstate = BaseFireBlock.getState(serverlevel, blockpos);

                if (serverlevel.getBlockState(blockpos).isAir() && blockstate.canSurvive(serverlevel, blockpos)) {
                    serverlevel.setBlockAndUpdate(blockpos, blockstate);
                    ++this.blocksSetOnFire;
                }

                for (int j = 0; j < additionalSources; ++j) {
                    BlockPos blockpos1 = blockpos.offset(this.random.nextInt(3) - 1, this.random.nextInt(3) - 1, this.random.nextInt(3) - 1);

                    blockstate = BaseFireBlock.getState(serverlevel, blockpos1);
                    if (serverlevel.getBlockState(blockpos1).isAir() && blockstate.canSurvive(serverlevel, blockpos1)) {
                        serverlevel.setBlockAndUpdate(blockpos1, blockstate);
                        ++this.blocksSetOnFire;
                    }
                }

                return;
            }
        }

    }

    private static void clearCopperOnLightningStrike(Level level, BlockPos struckPos) {
        BlockState blockstate = level.getBlockState(struckPos);
        boolean flag = ((BiMap) HoneycombItem.WAX_OFF_BY_BLOCK.get()).get(blockstate.getBlock()) != null;
        boolean flag1 = blockstate.getBlock() instanceof WeatheringCopper;

        if (flag1 || flag) {
            if (flag1) {
                level.setBlockAndUpdate(struckPos, WeatheringCopper.getFirst(level.getBlockState(struckPos)));
            }

            BlockPos.MutableBlockPos blockpos_mutableblockpos = struckPos.mutable();
            int i = level.random.nextInt(3) + 3;

            for (int j = 0; j < i; ++j) {
                int k = level.random.nextInt(8) + 1;

                randomWalkCleaningCopper(level, struckPos, blockpos_mutableblockpos, k);
            }

        }
    }

    private static void randomWalkCleaningCopper(Level level, BlockPos originalStrikePos, BlockPos.MutableBlockPos workPos, int stepCount) {
        workPos.set(originalStrikePos);

        for (int j = 0; j < stepCount; ++j) {
            Optional<BlockPos> optional = randomStepCleaningCopper(level, workPos);

            if (optional.isEmpty()) {
                break;
            }

            workPos.set((Vec3i) optional.get());
        }

    }

    private static Optional<BlockPos> randomStepCleaningCopper(Level level, BlockPos pos) {
        for (BlockPos blockpos1 : BlockPos.randomInCube(level.random, 10, pos, 1)) {
            BlockState blockstate = level.getBlockState(blockpos1);

            if (blockstate.getBlock() instanceof WeatheringCopper) {
                WeatheringCopper.getPrevious(blockstate).ifPresent((blockstate1) -> {
                    level.setBlockAndUpdate(blockpos1, blockstate1);
                });
                level.levelEvent(3002, blockpos1, -1);
                return Optional.of(blockpos1);
            }
        }

        return Optional.empty();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d1 = 64.0D * getViewScale();

        return distance < d1 * d1;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {}

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}

    public int getBlocksSetOnFire() {
        return this.blocksSetOnFire;
    }

    public Stream<Entity> getHitEntities() {
        return this.hitEntities.stream().filter(Entity::isAlive);
    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }
}
