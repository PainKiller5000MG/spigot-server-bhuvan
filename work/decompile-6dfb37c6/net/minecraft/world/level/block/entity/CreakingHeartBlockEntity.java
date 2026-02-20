package net.minecraft.world.level.block.entity;

import com.mojang.datafixers.util.Either;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SpawnUtil;
import net.minecraft.util.Util;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CreakingHeartBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.CreakingHeartState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jspecify.annotations.Nullable;

public class CreakingHeartBlockEntity extends BlockEntity {

    private static final int PLAYER_DETECTION_RANGE = 32;
    public static final int CREAKING_ROAMING_RADIUS = 32;
    private static final int DISTANCE_CREAKING_TOO_FAR = 34;
    private static final int SPAWN_RANGE_XZ = 16;
    private static final int SPAWN_RANGE_Y = 8;
    private static final int ATTEMPTS_PER_SPAWN = 5;
    private static final int UPDATE_TICKS = 20;
    private static final int UPDATE_TICKS_VARIANCE = 5;
    private static final int HURT_CALL_TOTAL_TICKS = 100;
    private static final int NUMBER_OF_HURT_CALLS = 10;
    private static final int HURT_CALL_INTERVAL = 10;
    private static final int HURT_CALL_PARTICLE_TICKS = 50;
    private static final int MAX_DEPTH = 2;
    private static final int MAX_COUNT = 64;
    private static final int TICKS_GRACE_PERIOD = 30;
    private static final Optional<Creaking> NO_CREAKING = Optional.empty();
    private @Nullable Either<Creaking, UUID> creakingInfo;
    private long ticksExisted;
    private int ticker;
    private int emitter;
    private @Nullable Vec3 emitterTarget;
    private int outputSignal;

    public CreakingHeartBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(BlockEntityType.CREAKING_HEART, worldPosition, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CreakingHeartBlockEntity entity) {
        ++entity.ticksExisted;
        if (level instanceof ServerLevel serverlevel) {
            int i = entity.computeAnalogOutputSignal();

            if (entity.outputSignal != i) {
                entity.outputSignal = i;
                level.updateNeighbourForOutputSignal(pos, Blocks.CREAKING_HEART);
            }

            if (entity.emitter > 0) {
                if (entity.emitter > 50) {
                    entity.emitParticles(serverlevel, 1, true);
                    entity.emitParticles(serverlevel, 1, false);
                }

                if (entity.emitter % 10 == 0 && entity.emitterTarget != null) {
                    entity.getCreakingProtector().ifPresent((creaking) -> {
                        entity.emitterTarget = creaking.getBoundingBox().getCenter();
                    });
                    Vec3 vec3 = Vec3.atCenterOf(pos);
                    float f = 0.2F + 0.8F * (float) (100 - entity.emitter) / 100.0F;
                    Vec3 vec31 = vec3.subtract(entity.emitterTarget).scale((double) f).add(entity.emitterTarget);
                    BlockPos blockpos1 = BlockPos.containing(vec31);
                    float f1 = (float) entity.emitter / 2.0F / 100.0F + 0.5F;

                    serverlevel.playSound((Entity) null, blockpos1, SoundEvents.CREAKING_HEART_HURT, SoundSource.BLOCKS, f1, 1.0F);
                }

                --entity.emitter;
            }

            if (entity.ticker-- < 0) {
                entity.ticker = entity.level == null ? 20 : entity.level.random.nextInt(5) + 20;
                BlockState blockstate1 = updateCreakingState(level, state, pos, entity);

                if (blockstate1 != state) {
                    level.setBlock(pos, blockstate1, 3);
                    if (blockstate1.getValue(CreakingHeartBlock.STATE) == CreakingHeartState.UPROOTED) {
                        return;
                    }
                }

                if (entity.creakingInfo == null) {
                    if (blockstate1.getValue(CreakingHeartBlock.STATE) == CreakingHeartState.AWAKE) {
                        if (serverlevel.isSpawningMonsters()) {
                            Player player = level.getNearestPlayer((double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), 32.0D, false);

                            if (player != null) {
                                Creaking creaking = spawnProtector(serverlevel, entity);

                                if (creaking != null) {
                                    entity.setCreakingInfo(creaking);
                                    creaking.makeSound(SoundEvents.CREAKING_SPAWN);
                                    level.playSound((Entity) null, entity.getBlockPos(), SoundEvents.CREAKING_HEART_SPAWN, SoundSource.BLOCKS, 1.0F, 1.0F);
                                }
                            }

                        }
                    }
                } else {
                    Optional<Creaking> optional = entity.getCreakingProtector();

                    if (optional.isPresent()) {
                        Creaking creaking1 = (Creaking) optional.get();

                        if (!(Boolean) level.environmentAttributes().getValue(EnvironmentAttributes.CREAKING_ACTIVE, pos) && !creaking1.isPersistenceRequired() || entity.distanceToCreaking() > 34.0D || creaking1.playerIsStuckInYou()) {
                            entity.removeProtector((DamageSource) null);
                        }
                    }

                }
            }
        }
    }

    private static BlockState updateCreakingState(Level level, BlockState state, BlockPos pos, CreakingHeartBlockEntity entity) {
        if (!CreakingHeartBlock.hasRequiredLogs(state, level, pos) && entity.creakingInfo == null) {
            return (BlockState) state.setValue(CreakingHeartBlock.STATE, CreakingHeartState.UPROOTED);
        } else {
            CreakingHeartState creakingheartstate = (Boolean) level.environmentAttributes().getValue(EnvironmentAttributes.CREAKING_ACTIVE, pos) ? CreakingHeartState.AWAKE : CreakingHeartState.DORMANT;

            return (BlockState) state.setValue(CreakingHeartBlock.STATE, creakingheartstate);
        }
    }

    private double distanceToCreaking() {
        return (Double) this.getCreakingProtector().map((creaking) -> {
            return Math.sqrt(creaking.distanceToSqr(Vec3.atBottomCenterOf(this.getBlockPos())));
        }).orElse(0.0D);
    }

    private void clearCreakingInfo() {
        this.creakingInfo = null;
        this.setChanged();
    }

    public void setCreakingInfo(Creaking creaking) {
        this.creakingInfo = Either.left(creaking);
        this.setChanged();
    }

    public void setCreakingInfo(UUID uuid) {
        this.creakingInfo = Either.right(uuid);
        this.ticksExisted = 0L;
        this.setChanged();
    }

    private Optional<Creaking> getCreakingProtector() {
        if (this.creakingInfo == null) {
            return CreakingHeartBlockEntity.NO_CREAKING;
        } else {
            if (this.creakingInfo.left().isPresent()) {
                Creaking creaking = (Creaking) this.creakingInfo.left().get();

                if (!creaking.isRemoved()) {
                    return Optional.of(creaking);
                }

                this.setCreakingInfo(creaking.getUUID());
            }

            Level level = this.level;

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                if (this.creakingInfo.right().isPresent()) {
                    UUID uuid = (UUID) this.creakingInfo.right().get();
                    Entity entity = serverlevel.getEntity(uuid);

                    if (entity instanceof Creaking) {
                        Creaking creaking1 = (Creaking) entity;

                        this.setCreakingInfo(creaking1);
                        return Optional.of(creaking1);
                    }

                    if (this.ticksExisted >= 30L) {
                        this.clearCreakingInfo();
                    }

                    return CreakingHeartBlockEntity.NO_CREAKING;
                }
            }

            return CreakingHeartBlockEntity.NO_CREAKING;
        }
    }

    private static @Nullable Creaking spawnProtector(ServerLevel level, CreakingHeartBlockEntity entity) {
        BlockPos blockpos = entity.getBlockPos();
        Optional<Creaking> optional = SpawnUtil.<Creaking>trySpawnMob(EntityType.CREAKING, EntitySpawnReason.SPAWNER, level, blockpos, 5, 16, 8, SpawnUtil.Strategy.ON_TOP_OF_COLLIDER_NO_LEAVES, true);

        if (optional.isEmpty()) {
            return null;
        } else {
            Creaking creaking = (Creaking) optional.get();

            level.gameEvent(creaking, (Holder) GameEvent.ENTITY_PLACE, creaking.position());
            level.broadcastEntityEvent(creaking, (byte) 60);
            creaking.setTransient(blockpos);
            return creaking;
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    public void creakingHurt() {
        Object object = this.getCreakingProtector().orElse((Object) null);

        if (object instanceof Creaking creaking) {
            Level level = this.level;

            if (level instanceof ServerLevel serverlevel) {
                if (this.emitter <= 0) {
                    this.emitParticles(serverlevel, 20, false);
                    if (this.getBlockState().getValue(CreakingHeartBlock.STATE) == CreakingHeartState.AWAKE) {
                        int i = this.level.getRandom().nextIntBetweenInclusive(2, 3);

                        for (int j = 0; j < i; ++j) {
                            this.spreadResin(serverlevel).ifPresent((blockpos) -> {
                                this.level.playSound((Entity) null, blockpos, SoundEvents.RESIN_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                                this.level.gameEvent(GameEvent.BLOCK_PLACE, blockpos, GameEvent.Context.of(this.getBlockState()));
                            });
                        }
                    }

                    this.emitter = 100;
                    this.emitterTarget = creaking.getBoundingBox().getCenter();
                }
            }
        }
    }

    private Optional<BlockPos> spreadResin(ServerLevel level) {
        Mutable<BlockPos> mutable = new MutableObject((Object) null);

        BlockPos.breadthFirstTraversal(this.worldPosition, 2, 64, (blockpos, consumer) -> {
            for (Direction direction : Util.shuffledCopy(Direction.values(), level.random)) {
                BlockPos blockpos1 = blockpos.relative(direction);

                if (level.getBlockState(blockpos1).is(BlockTags.PALE_OAK_LOGS)) {
                    consumer.accept(blockpos1);
                }
            }

        }, (blockpos) -> {
            if (!level.getBlockState(blockpos).is(BlockTags.PALE_OAK_LOGS)) {
                return BlockPos.TraversalNodeStatus.ACCEPT;
            } else {
                for (Direction direction : Util.shuffledCopy(Direction.values(), level.random)) {
                    BlockPos blockpos1 = blockpos.relative(direction);
                    BlockState blockstate = level.getBlockState(blockpos1);
                    Direction direction1 = direction.getOpposite();

                    if (blockstate.isAir()) {
                        blockstate = Blocks.RESIN_CLUMP.defaultBlockState();
                    } else if (blockstate.is(Blocks.WATER) && blockstate.getFluidState().isSource()) {
                        blockstate = (BlockState) Blocks.RESIN_CLUMP.defaultBlockState().setValue(MultifaceBlock.WATERLOGGED, true);
                    }

                    if (blockstate.is(Blocks.RESIN_CLUMP) && !MultifaceBlock.hasFace(blockstate, direction1)) {
                        level.setBlock(blockpos1, (BlockState) blockstate.setValue(MultifaceBlock.getFaceProperty(direction1), true), 3);
                        mutable.setValue(blockpos1);
                        return BlockPos.TraversalNodeStatus.STOP;
                    }
                }

                return BlockPos.TraversalNodeStatus.ACCEPT;
            }
        });
        return Optional.ofNullable((BlockPos) mutable.get());
    }

    private void emitParticles(ServerLevel serverLevel, int count, boolean towardsCreaking) {
        Object object = this.getCreakingProtector().orElse((Object) null);

        if (object instanceof Creaking creaking) {
            int j = towardsCreaking ? 16545810 : 6250335;
            RandomSource randomsource = serverLevel.random;

            for (double d0 = 0.0D; d0 < (double) count; ++d0) {
                AABB aabb = creaking.getBoundingBox();
                Vec3 vec3 = aabb.getMinPosition().add(randomsource.nextDouble() * aabb.getXsize(), randomsource.nextDouble() * aabb.getYsize(), randomsource.nextDouble() * aabb.getZsize());
                Vec3 vec31 = Vec3.atLowerCornerOf(this.getBlockPos()).add(randomsource.nextDouble(), randomsource.nextDouble(), randomsource.nextDouble());

                if (towardsCreaking) {
                    Vec3 vec32 = vec3;

                    vec3 = vec31;
                    vec31 = vec32;
                }

                TrailParticleOption trailparticleoption = new TrailParticleOption(vec31, j, randomsource.nextInt(40) + 10);

                serverLevel.sendParticles(trailparticleoption, true, true, vec3.x, vec3.y, vec3.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }

        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        this.removeProtector((DamageSource) null);
    }

    public void removeProtector(@Nullable DamageSource damageSource) {
        Object object = this.getCreakingProtector().orElse((Object) null);

        if (object instanceof Creaking creaking) {
            if (damageSource == null) {
                creaking.tearDown();
            } else {
                creaking.creakingDeathEffects(damageSource);
                creaking.setTearingDown();
                creaking.setHealth(0.0F);
            }

            this.clearCreakingInfo();
        }

    }

    public boolean isProtector(Creaking creaking) {
        return (Boolean) this.getCreakingProtector().map((creaking1) -> {
            return creaking1 == creaking;
        }).orElse(false);
    }

    public int getAnalogOutputSignal() {
        return this.outputSignal;
    }

    public int computeAnalogOutputSignal() {
        if (this.creakingInfo != null && !this.getCreakingProtector().isEmpty()) {
            double d0 = this.distanceToCreaking();
            double d1 = Math.clamp(d0, 0.0D, 32.0D) / 32.0D;

            return 15 - (int) Math.floor(d1 * 15.0D);
        } else {
            return 0;
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.read("creaking", UUIDUtil.CODEC).ifPresentOrElse(this::setCreakingInfo, this::clearCreakingInfo);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (this.creakingInfo != null) {
            output.store("creaking", UUIDUtil.CODEC, (UUID) this.creakingInfo.map(Entity::getUUID, (uuid) -> {
                return uuid;
            }));
        }

    }
}
