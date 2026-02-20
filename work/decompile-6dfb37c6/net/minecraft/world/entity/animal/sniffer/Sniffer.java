package net.minecraft.world.entity.animal.sniffer;

import com.mojang.serialization.Dynamic;
import io.netty.buffer.ByteBuf;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.Vec3;

public class Sniffer extends Animal {

    private static final int DIGGING_PARTICLES_DELAY_TICKS = 1700;
    private static final int DIGGING_PARTICLES_DURATION_TICKS = 6000;
    private static final int DIGGING_PARTICLES_AMOUNT = 30;
    private static final int DIGGING_DROP_SEED_OFFSET_TICKS = 120;
    private static final int SNIFFER_BABY_AGE_TICKS = 48000;
    private static final float DIGGING_BB_HEIGHT_OFFSET = 0.4F;
    private static final EntityDimensions DIGGING_DIMENSIONS = EntityDimensions.scalable(EntityType.SNIFFER.getWidth(), EntityType.SNIFFER.getHeight() - 0.4F).withEyeHeight(0.81F);
    private static final EntityDataAccessor<Sniffer.State> DATA_STATE = SynchedEntityData.<Sniffer.State>defineId(Sniffer.class, EntityDataSerializers.SNIFFER_STATE);
    private static final EntityDataAccessor<Integer> DATA_DROP_SEED_AT_TICK = SynchedEntityData.<Integer>defineId(Sniffer.class, EntityDataSerializers.INT);
    public final AnimationState feelingHappyAnimationState = new AnimationState();
    public final AnimationState scentingAnimationState = new AnimationState();
    public final AnimationState sniffingAnimationState = new AnimationState();
    public final AnimationState diggingAnimationState = new AnimationState();
    public final AnimationState risingAnimationState = new AnimationState();

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MOVEMENT_SPEED, (double) 0.1F).add(Attributes.MAX_HEALTH, 14.0D);
    }

    public Sniffer(EntityType<? extends Animal> type, Level level) {
        super(type, level);
        this.getNavigation().setCanFloat(true);
        this.setPathfindingMalus(PathType.WATER, -1.0F);
        this.setPathfindingMalus(PathType.DANGER_POWDER_SNOW, -1.0F);
        this.setPathfindingMalus(PathType.DAMAGE_CAUTIOUS, -1.0F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(Sniffer.DATA_STATE, Sniffer.State.IDLING);
        entityData.define(Sniffer.DATA_DROP_SEED_AT_TICK, 0);
    }

    @Override
    public void onPathfindingStart() {
        super.onPathfindingStart();
        if (this.isOnFire() || this.isInWater()) {
            this.setPathfindingMalus(PathType.WATER, 0.0F);
        }

    }

    @Override
    public void onPathfindingDone() {
        this.setPathfindingMalus(PathType.WATER, -1.0F);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.getState() == Sniffer.State.DIGGING ? Sniffer.DIGGING_DIMENSIONS.scale(this.getAgeScale()) : super.getDefaultDimensions(pose);
    }

    public boolean isSearching() {
        return this.getState() == Sniffer.State.SEARCHING;
    }

    public boolean isTempted() {
        return (Boolean) this.brain.getMemory(MemoryModuleType.IS_TEMPTED).orElse(false);
    }

    public boolean canSniff() {
        return !this.isTempted() && !this.isPanicking() && !this.isInWater() && !this.isInLove() && this.onGround() && !this.isPassenger() && !this.isLeashed();
    }

    public boolean canPlayDiggingSound() {
        return this.getState() == Sniffer.State.DIGGING || this.getState() == Sniffer.State.SEARCHING;
    }

    private BlockPos getHeadBlock() {
        Vec3 vec3 = this.getHeadPosition();

        return BlockPos.containing(vec3.x(), this.getY() + (double) 0.2F, vec3.z());
    }

    private Vec3 getHeadPosition() {
        return this.position().add(this.getForward().scale(2.25D));
    }

    @Override
    public boolean supportQuadLeash() {
        return true;
    }

    @Override
    public Vec3[] getQuadLeashOffsets() {
        return Leashable.createQuadLeashOffsets(this, -0.01D, 0.63D, 0.38D, 1.15D);
    }

    public Sniffer.State getState() {
        return (Sniffer.State) this.entityData.get(Sniffer.DATA_STATE);
    }

    private Sniffer setState(Sniffer.State state) {
        this.entityData.set(Sniffer.DATA_STATE, state);
        return this;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        if (Sniffer.DATA_STATE.equals(accessor)) {
            Sniffer.State sniffer_state = this.getState();

            this.resetAnimations();
            switch (sniffer_state.ordinal()) {
                case 1:
                    this.feelingHappyAnimationState.startIfStopped(this.tickCount);
                    break;
                case 2:
                    this.scentingAnimationState.startIfStopped(this.tickCount);
                    break;
                case 3:
                    this.sniffingAnimationState.startIfStopped(this.tickCount);
                case 4:
                default:
                    break;
                case 5:
                    this.diggingAnimationState.startIfStopped(this.tickCount);
                    break;
                case 6:
                    this.risingAnimationState.startIfStopped(this.tickCount);
            }

            this.refreshDimensions();
        }

        super.onSyncedDataUpdated(accessor);
    }

    private void resetAnimations() {
        this.diggingAnimationState.stop();
        this.sniffingAnimationState.stop();
        this.risingAnimationState.stop();
        this.feelingHappyAnimationState.stop();
        this.scentingAnimationState.stop();
    }

    public Sniffer transitionTo(Sniffer.State state) {
        switch (state.ordinal()) {
            case 0:
                this.setState(Sniffer.State.IDLING);
                break;
            case 1:
                this.playSound(SoundEvents.SNIFFER_HAPPY, 1.0F, 1.0F);
                this.setState(Sniffer.State.FEELING_HAPPY);
                break;
            case 2:
                this.setState(Sniffer.State.SCENTING).onScentingStart();
                break;
            case 3:
                this.playSound(SoundEvents.SNIFFER_SNIFFING, 1.0F, 1.0F);
                this.setState(Sniffer.State.SNIFFING);
                break;
            case 4:
                this.setState(Sniffer.State.SEARCHING);
                break;
            case 5:
                this.setState(Sniffer.State.DIGGING).onDiggingStart();
                break;
            case 6:
                this.playSound(SoundEvents.SNIFFER_DIGGING_STOP, 1.0F, 1.0F);
                this.setState(Sniffer.State.RISING);
        }

        return this;
    }

    private Sniffer onScentingStart() {
        this.playSound(SoundEvents.SNIFFER_SCENTING, 1.0F, this.isBaby() ? 1.3F : 1.0F);
        return this;
    }

    private Sniffer onDiggingStart() {
        this.entityData.set(Sniffer.DATA_DROP_SEED_AT_TICK, this.tickCount + 120);
        this.level().broadcastEntityEvent(this, (byte) 63);
        return this;
    }

    public Sniffer onDiggingComplete(boolean success) {
        if (success) {
            this.storeExploredPosition(this.getOnPos());
        }

        return this;
    }

    public Optional<BlockPos> calculateDigPosition() {
        return IntStream.range(0, 5).mapToObj((i) -> {
            return LandRandomPos.getPos(this, 10 + 2 * i, 3);
        }).filter(Objects::nonNull).map(BlockPos::containing).filter((blockpos) -> {
            return this.level().getWorldBorder().isWithinBounds(blockpos);
        }).map(BlockPos::below).filter(this::canDig).findFirst();
    }

    public boolean canDig() {
        return !this.isPanicking() && !this.isTempted() && !this.isBaby() && !this.isInWater() && this.onGround() && !this.isPassenger() && this.canDig(this.getHeadBlock().below());
    }

    private boolean canDig(BlockPos position) {
        return this.level().getBlockState(position).is(BlockTags.SNIFFER_DIGGABLE_BLOCK) && this.getExploredPositions().noneMatch((globalpos) -> {
            return GlobalPos.of(this.level().dimension(), position).equals(globalpos);
        }) && (Boolean) Optional.ofNullable(this.getNavigation().createPath(position, 1)).map(Path::canReach).orElse(false);
    }

    private void dropSeed() {
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            if ((Integer) this.entityData.get(Sniffer.DATA_DROP_SEED_AT_TICK) == this.tickCount) {
                BlockPos blockpos = this.getHeadBlock();

                this.dropFromGiftLootTable(serverlevel, BuiltInLootTables.SNIFFER_DIGGING, (serverlevel1, itemstack) -> {
                    ItemEntity itementity = new ItemEntity(this.level(), (double) blockpos.getX(), (double) blockpos.getY(), (double) blockpos.getZ(), itemstack);

                    itementity.setDefaultPickUpDelay();
                    serverlevel1.addFreshEntity(itementity);
                });
                this.playSound(SoundEvents.SNIFFER_DROP_SEED, 1.0F, 1.0F);
                return;
            }
        }

    }

    private Sniffer emitDiggingParticles(AnimationState state) {
        boolean flag = state.getTimeInMillis((float) this.tickCount) > 1700L && state.getTimeInMillis((float) this.tickCount) < 6000L;

        if (flag) {
            BlockPos blockpos = this.getHeadBlock();
            BlockState blockstate = this.level().getBlockState(blockpos.below());

            if (blockstate.getRenderShape() != RenderShape.INVISIBLE) {
                for (int i = 0; i < 30; ++i) {
                    Vec3 vec3 = Vec3.atCenterOf(blockpos).add(0.0D, (double) -0.65F, 0.0D);

                    this.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, blockstate), vec3.x, vec3.y, vec3.z, 0.0D, 0.0D, 0.0D);
                }

                if (this.tickCount % 10 == 0) {
                    this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), blockstate.getSoundType().getHitSound(), this.getSoundSource(), 0.5F, 0.5F, false);
                }
            }
        }

        if (this.tickCount % 10 == 0) {
            this.level().gameEvent(GameEvent.ENTITY_ACTION, this.getHeadBlock(), GameEvent.Context.of((Entity) this));
        }

        return this;
    }

    public Sniffer storeExploredPosition(BlockPos position) {
        List<GlobalPos> list = (List) this.getExploredPositions().limit(20L).collect(Collectors.toList());

        list.add(0, GlobalPos.of(this.level().dimension(), position));
        this.getBrain().setMemory(MemoryModuleType.SNIFFER_EXPLORED_POSITIONS, list);
        return this;
    }

    public Stream<GlobalPos> getExploredPositions() {
        return this.getBrain().getMemory(MemoryModuleType.SNIFFER_EXPLORED_POSITIONS).stream().flatMap(Collection::stream);
    }

    @Override
    public void jumpFromGround() {
        super.jumpFromGround();
        double d0 = this.moveControl.getSpeedModifier();

        if (d0 > 0.0D) {
            double d1 = this.getDeltaMovement().horizontalDistanceSqr();

            if (d1 < 0.01D) {
                this.moveRelative(0.1F, new Vec3(0.0D, 0.0D, 1.0D));
            }
        }

    }

    @Override
    public void spawnChildFromBreeding(ServerLevel level, Animal partner) {
        ItemStack itemstack = new ItemStack(Items.SNIFFER_EGG);
        ItemEntity itementity = new ItemEntity(level, this.position().x(), this.position().y(), this.position().z(), itemstack);

        itementity.setDefaultPickUpDelay();
        this.finalizeSpawnChildFromBreeding(level, partner, (AgeableMob) null);
        this.playSound(SoundEvents.SNIFFER_EGG_PLOP, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 0.5F);
        level.addFreshEntity(itementity);
    }

    @Override
    public void die(DamageSource source) {
        this.transitionTo(Sniffer.State.IDLING);
        super.die(source);
    }

    @Override
    public void tick() {
        switch (this.getState().ordinal()) {
            case 4:
                this.playSearchingSound();
                break;
            case 5:
                this.emitDiggingParticles(this.diggingAnimationState).dropSeed();
        }

        super.tick();
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        boolean flag = this.isFood(itemstack);
        InteractionResult interactionresult = super.mobInteract(player, hand);

        if (interactionresult.consumesAction() && flag) {
            this.playEatingSound();
        }

        return interactionresult;
    }

    @Override
    protected void playEatingSound() {
        this.level().playSound((Entity) null, (Entity) this, SoundEvents.SNIFFER_EAT, SoundSource.NEUTRAL, 1.0F, Mth.randomBetween(this.level().random, 0.8F, 1.2F));
    }

    private void playSearchingSound() {
        if (this.level().isClientSide() && this.tickCount % 20 == 0) {
            this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.SNIFFER_SEARCHING, this.getSoundSource(), 1.0F, 1.0F, false);
        }

    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockState) {
        this.playSound(SoundEvents.SNIFFER_STEP, 0.15F, 1.0F);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return Set.of(Sniffer.State.DIGGING, Sniffer.State.SEARCHING).contains(this.getState()) ? null : SoundEvents.SNIFFER_IDLE;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SNIFFER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SNIFFER_DEATH;
    }

    @Override
    public int getMaxHeadYRot() {
        return 50;
    }

    @Override
    public void setBaby(boolean baby) {
        this.setAge(baby ? -48000 : 0);
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return EntityType.SNIFFER.create(level, EntitySpawnReason.BREEDING);
    }

    @Override
    public boolean canMate(Animal partner) {
        if (!(partner instanceof Sniffer sniffer)) {
            return false;
        } else {
            Set<Sniffer.State> set = Set.of(Sniffer.State.IDLING, Sniffer.State.SCENTING, Sniffer.State.FEELING_HAPPY);

            return set.contains(this.getState()) && set.contains(sniffer.getState()) && super.canMate(partner);
        }
    }

    @Override
    public boolean isFood(ItemStack itemStack) {
        return itemStack.is(ItemTags.SNIFFER_FOOD);
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> input) {
        return SnifferAi.makeBrain(this.brainProvider().makeBrain(input));
    }

    @Override
    public Brain<Sniffer> getBrain() {
        return super.getBrain();
    }

    @Override
    protected Brain.Provider<Sniffer> brainProvider() {
        return Brain.<Sniffer>provider(SnifferAi.MEMORY_TYPES, SnifferAi.SENSOR_TYPES);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("snifferBrain");
        this.getBrain().tick(level, this);
        profilerfiller.popPush("snifferActivityUpdate");
        SnifferAi.updateActivity(this);
        profilerfiller.pop();
        super.customServerAiStep(level);
    }

    public static enum State {

        IDLING(0), FEELING_HAPPY(1), SCENTING(2), SNIFFING(3), SEARCHING(4), DIGGING(5), RISING(6);

        public static final IntFunction<Sniffer.State> BY_ID = ByIdMap.<Sniffer.State>continuous(Sniffer.State::id, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        public static final StreamCodec<ByteBuf, Sniffer.State> STREAM_CODEC = ByteBufCodecs.idMapper(Sniffer.State.BY_ID, Sniffer.State::id);
        private final int id;

        private State(int id) {
            this.id = id;
        }

        public int id() {
            return this.id;
        }
    }
}
