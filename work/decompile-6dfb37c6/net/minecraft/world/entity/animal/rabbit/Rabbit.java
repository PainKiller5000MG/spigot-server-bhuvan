package net.minecraft.world.entity.animal.rabbit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.PrimitiveCodec;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Util;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.ClimbOnTopOfPowderSnowGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarrotBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Rabbit extends Animal {

    public static final double STROLL_SPEED_MOD = 0.6D;
    public static final double BREED_SPEED_MOD = 0.8D;
    public static final double FOLLOW_SPEED_MOD = 1.0D;
    public static final double FLEE_SPEED_MOD = 2.2D;
    public static final double ATTACK_SPEED_MOD = 1.4D;
    private static final EntityDataAccessor<Integer> DATA_TYPE_ID = SynchedEntityData.<Integer>defineId(Rabbit.class, EntityDataSerializers.INT);
    private static final int DEFAULT_MORE_CARROT_TICKS = 0;
    private static final Identifier KILLER_BUNNY = Identifier.withDefaultNamespace("killer_bunny");
    private static final int DEFAULT_ATTACK_POWER = 3;
    private static final int EVIL_ATTACK_POWER_INCREMENT = 5;
    private static final Identifier EVIL_ATTACK_POWER_MODIFIER = Identifier.withDefaultNamespace("evil");
    private static final int EVIL_ARMOR_VALUE = 8;
    private static final int MORE_CARROTS_DELAY = 40;
    private int jumpTicks;
    private int jumpDuration;
    private boolean wasOnGround;
    private int jumpDelayTicks;
    private int moreCarrotTicks = 0;

    public Rabbit(EntityType<? extends Rabbit> type, Level level) {
        super(type, level);
        this.jumpControl = new Rabbit.RabbitJumpControl(this);
        this.moveControl = new Rabbit.RabbitMoveControl(this);
        this.setSpeedModifier(0.0D);
    }

    @Override
    public void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(1, new ClimbOnTopOfPowderSnowGoal(this, this.level()));
        this.goalSelector.addGoal(1, new Rabbit.RabbitPanicGoal(this, 2.2D));
        this.goalSelector.addGoal(2, new BreedGoal(this, 0.8D));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.0D, (itemstack) -> {
            return itemstack.is(ItemTags.RABBIT_FOOD);
        }, false));
        this.goalSelector.addGoal(4, new Rabbit.RabbitAvoidEntityGoal(this, Player.class, 8.0F, 2.2D, 2.2D));
        this.goalSelector.addGoal(4, new Rabbit.RabbitAvoidEntityGoal(this, Wolf.class, 10.0F, 2.2D, 2.2D));
        this.goalSelector.addGoal(4, new Rabbit.RabbitAvoidEntityGoal(this, Monster.class, 4.0F, 2.2D, 2.2D));
        this.goalSelector.addGoal(5, new Rabbit.RaidGardenGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 10.0F));
    }

    @Override
    protected float getJumpPower() {
        float f = 0.3F;

        if (this.moveControl.getSpeedModifier() <= 0.6D) {
            f = 0.2F;
        }

        Path path = this.navigation.getPath();

        if (path != null && !path.isDone()) {
            Vec3 vec3 = path.getNextEntityPos(this);

            if (vec3.y > this.getY() + 0.5D) {
                f = 0.5F;
            }
        }

        if (this.horizontalCollision || this.jumping && this.moveControl.getWantedY() > this.getY() + 0.5D) {
            f = 0.5F;
        }

        return super.getJumpPower(f / 0.42F);
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

        if (!this.level().isClientSide()) {
            this.level().broadcastEntityEvent(this, (byte) 1);
        }

    }

    public float getJumpCompletion(float a) {
        return this.jumpDuration == 0 ? 0.0F : ((float) this.jumpTicks + a) / (float) this.jumpDuration;
    }

    public void setSpeedModifier(double speed) {
        this.getNavigation().setSpeedModifier(speed);
        this.moveControl.setWantedPosition(this.moveControl.getWantedX(), this.moveControl.getWantedY(), this.moveControl.getWantedZ(), speed);
    }

    @Override
    public void setJumping(boolean jump) {
        super.setJumping(jump);
        if (jump) {
            this.playSound(this.getJumpSound(), this.getSoundVolume(), ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F) * 0.8F);
        }

    }

    public void startJumping() {
        this.setJumping(true);
        this.jumpDuration = 10;
        this.jumpTicks = 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(Rabbit.DATA_TYPE_ID, Rabbit.Variant.DEFAULT.id);
    }

    @Override
    public void customServerAiStep(ServerLevel level) {
        if (this.jumpDelayTicks > 0) {
            --this.jumpDelayTicks;
        }

        if (this.moreCarrotTicks > 0) {
            this.moreCarrotTicks -= this.random.nextInt(3);
            if (this.moreCarrotTicks < 0) {
                this.moreCarrotTicks = 0;
            }
        }

        if (this.onGround()) {
            if (!this.wasOnGround) {
                this.setJumping(false);
                this.checkLandingDelay();
            }

            if (this.getVariant() == Rabbit.Variant.EVIL && this.jumpDelayTicks == 0) {
                LivingEntity livingentity = this.getTarget();

                if (livingentity != null && this.distanceToSqr((Entity) livingentity) < 16.0D) {
                    this.facePoint(livingentity.getX(), livingentity.getZ());
                    this.moveControl.setWantedPosition(livingentity.getX(), livingentity.getY(), livingentity.getZ(), this.moveControl.getSpeedModifier());
                    this.startJumping();
                    this.wasOnGround = true;
                }
            }

            Rabbit.RabbitJumpControl rabbit_rabbitjumpcontrol = (Rabbit.RabbitJumpControl) this.jumpControl;

            if (!rabbit_rabbitjumpcontrol.wantJump()) {
                if (this.moveControl.hasWanted() && this.jumpDelayTicks == 0) {
                    Path path = this.navigation.getPath();
                    Vec3 vec3 = new Vec3(this.moveControl.getWantedX(), this.moveControl.getWantedY(), this.moveControl.getWantedZ());

                    if (path != null && !path.isDone()) {
                        vec3 = path.getNextEntityPos(this);
                    }

                    this.facePoint(vec3.x, vec3.z);
                    this.startJumping();
                }
            } else if (!rabbit_rabbitjumpcontrol.canJump()) {
                this.enableJumpControl();
            }
        }

        this.wasOnGround = this.onGround();
    }

    @Override
    public boolean canSpawnSprintParticle() {
        return false;
    }

    private void facePoint(double faceX, double faceZ) {
        this.setYRot((float) (Mth.atan2(faceZ - this.getZ(), faceX - this.getX()) * (double) (180F / (float) Math.PI)) - 90.0F);
    }

    private void enableJumpControl() {
        ((Rabbit.RabbitJumpControl) this.jumpControl).setCanJump(true);
    }

    private void disableJumpControl() {
        ((Rabbit.RabbitJumpControl) this.jumpControl).setCanJump(false);
    }

    private void setLandingDelay() {
        if (this.moveControl.getSpeedModifier() < 2.2D) {
            this.jumpDelayTicks = 10;
        } else {
            this.jumpDelayTicks = 1;
        }

    }

    private void checkLandingDelay() {
        this.setLandingDelay();
        this.disableJumpControl();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.jumpTicks != this.jumpDuration) {
            ++this.jumpTicks;
        } else if (this.jumpDuration != 0) {
            this.jumpTicks = 0;
            this.jumpDuration = 0;
            this.setJumping(false);
        }

    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 3.0D).add(Attributes.MOVEMENT_SPEED, (double) 0.3F).add(Attributes.ATTACK_DAMAGE, 3.0D);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("RabbitType", Rabbit.Variant.LEGACY_CODEC, this.getVariant());
        output.putInt("MoreCarrotTicks", this.moreCarrotTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setVariant((Rabbit.Variant) input.read("RabbitType", Rabbit.Variant.LEGACY_CODEC).orElse(Rabbit.Variant.DEFAULT));
        this.moreCarrotTicks = input.getIntOr("MoreCarrotTicks", 0);
    }

    protected SoundEvent getJumpSound() {
        return SoundEvents.RABBIT_JUMP;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.RABBIT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RABBIT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RABBIT_DEATH;
    }

    @Override
    public void playAttackSound() {
        if (this.getVariant() == Rabbit.Variant.EVIL) {
            this.playSound(SoundEvents.RABBIT_ATTACK, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
        }

    }

    @Override
    public SoundSource getSoundSource() {
        return this.getVariant() == Rabbit.Variant.EVIL ? SoundSource.HOSTILE : SoundSource.NEUTRAL;
    }

    @Override
    public @Nullable Rabbit getBreedOffspring(ServerLevel level, AgeableMob partner) {
        Rabbit rabbit = EntityType.RABBIT.create(level, EntitySpawnReason.BREEDING);

        if (rabbit != null) {
            Rabbit.Variant rabbit_variant = getRandomRabbitVariant(level, this.blockPosition());

            if (this.random.nextInt(20) != 0) {
                label22:
                {
                    if (partner instanceof Rabbit) {
                        Rabbit rabbit1 = (Rabbit) partner;

                        if (this.random.nextBoolean()) {
                            rabbit_variant = rabbit1.getVariant();
                            break label22;
                        }
                    }

                    rabbit_variant = this.getVariant();
                }
            }

            rabbit.setVariant(rabbit_variant);
        }

        return rabbit;
    }

    @Override
    public boolean isFood(ItemStack itemStack) {
        return itemStack.is(ItemTags.RABBIT_FOOD);
    }

    public Rabbit.Variant getVariant() {
        return Rabbit.Variant.byId((Integer) this.entityData.get(Rabbit.DATA_TYPE_ID));
    }

    public void setVariant(Rabbit.Variant rabbit_variant) {
        if (rabbit_variant == Rabbit.Variant.EVIL) {
            this.getAttribute(Attributes.ARMOR).setBaseValue(8.0D);
            this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.4D, true));
            this.targetSelector.addGoal(1, (new HurtByTargetGoal(this, new Class[0])).setAlertOthers());
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, true));
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Wolf.class, true));
            this.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(new AttributeModifier(Rabbit.EVIL_ATTACK_POWER_MODIFIER, 5.0D, AttributeModifier.Operation.ADD_VALUE));
            if (!this.hasCustomName()) {
                this.setCustomName(Component.translatable(Util.makeDescriptionId("entity", Rabbit.KILLER_BUNNY)));
            }
        } else {
            this.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(Rabbit.EVIL_ATTACK_POWER_MODIFIER);
        }

        this.entityData.set(Rabbit.DATA_TYPE_ID, rabbit_variant.id);
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> type) {
        return (T) (type == DataComponents.RABBIT_VARIANT ? castComponentValue(type, this.getVariant()) : super.get(type));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        this.applyImplicitComponentIfPresent(components, DataComponents.RABBIT_VARIANT);
        super.applyImplicitComponents(components);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> type, T value) {
        if (type == DataComponents.RABBIT_VARIANT) {
            this.setVariant((Rabbit.Variant) castComponentValue(DataComponents.RABBIT_VARIANT, value));
            return true;
        } else {
            return super.applyImplicitComponent(type, value);
        }
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        Rabbit.Variant rabbit_variant = getRandomRabbitVariant(level, this.blockPosition());

        if (groupData instanceof Rabbit.RabbitGroupData) {
            rabbit_variant = ((Rabbit.RabbitGroupData) groupData).variant;
        } else {
            groupData = new Rabbit.RabbitGroupData(rabbit_variant);
        }

        this.setVariant(rabbit_variant);
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    private static Rabbit.Variant getRandomRabbitVariant(LevelAccessor level, BlockPos pos) {
        Holder<Biome> holder = level.getBiome(pos);
        int i = level.getRandom().nextInt(100);

        return holder.is(BiomeTags.SPAWNS_WHITE_RABBITS) ? (i < 80 ? Rabbit.Variant.WHITE : Rabbit.Variant.WHITE_SPLOTCHED) : (holder.is(BiomeTags.SPAWNS_GOLD_RABBITS) ? Rabbit.Variant.GOLD : (i < 50 ? Rabbit.Variant.BROWN : (i < 90 ? Rabbit.Variant.SALT : Rabbit.Variant.BLACK)));
    }

    public static boolean checkRabbitSpawnRules(EntityType<Rabbit> type, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.RABBITS_SPAWNABLE_ON) && isBrightEnoughToSpawn(level, pos);
    }

    private boolean wantsMoreFood() {
        return this.moreCarrotTicks <= 0;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 1) {
            this.spawnSprintParticle();
            this.jumpDuration = 10;
            this.jumpTicks = 0;
        } else {
            super.handleEntityEvent(id);
        }

    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0D, (double) (0.6F * this.getEyeHeight()), (double) (this.getBbWidth() * 0.4F));
    }

    public static enum Variant implements StringRepresentable {

        BROWN(0, "brown"), WHITE(1, "white"), BLACK(2, "black"), WHITE_SPLOTCHED(3, "white_splotched"), GOLD(4, "gold"), SALT(5, "salt"), EVIL(99, "evil");

        public static final Rabbit.Variant DEFAULT = Rabbit.Variant.BROWN;
        private static final IntFunction<Rabbit.Variant> BY_ID = ByIdMap.<Rabbit.Variant>sparse(Rabbit.Variant::id, values(), Rabbit.Variant.DEFAULT);
        public static final Codec<Rabbit.Variant> CODEC = StringRepresentable.<Rabbit.Variant>fromEnum(Rabbit.Variant::values);
        /** @deprecated */
        @Deprecated
        public static final Codec<Rabbit.Variant> LEGACY_CODEC;
        public static final StreamCodec<ByteBuf, Rabbit.Variant> STREAM_CODEC;
        private final int id;
        private final String name;

        private Variant(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public int id() {
            return this.id;
        }

        public static Rabbit.Variant byId(int id) {
            return (Rabbit.Variant) Rabbit.Variant.BY_ID.apply(id);
        }

        static {
            PrimitiveCodec primitivecodec = Codec.INT;
            IntFunction intfunction = Rabbit.Variant.BY_ID;

            Objects.requireNonNull(intfunction);
            LEGACY_CODEC = primitivecodec.xmap(intfunction::apply, Rabbit.Variant::id);
            STREAM_CODEC = ByteBufCodecs.idMapper(Rabbit.Variant.BY_ID, Rabbit.Variant::id);
        }
    }

    public static class RabbitGroupData extends AgeableMob.AgeableMobGroupData {

        public final Rabbit.Variant variant;

        public RabbitGroupData(Rabbit.Variant rabbit_variant) {
            super(1.0F);
            this.variant = rabbit_variant;
        }
    }

    public static class RabbitJumpControl extends JumpControl {

        private final Rabbit rabbit;
        private boolean canJump;

        public RabbitJumpControl(Rabbit rabbit) {
            super(rabbit);
            this.rabbit = rabbit;
        }

        public boolean wantJump() {
            return this.jump;
        }

        public boolean canJump() {
            return this.canJump;
        }

        public void setCanJump(boolean canJump) {
            this.canJump = canJump;
        }

        @Override
        public void tick() {
            if (this.jump) {
                this.rabbit.startJumping();
                this.jump = false;
            }

        }
    }

    private static class RabbitMoveControl extends MoveControl {

        private final Rabbit rabbit;
        private double nextJumpSpeed;

        public RabbitMoveControl(Rabbit rabbit) {
            super(rabbit);
            this.rabbit = rabbit;
        }

        @Override
        public void tick() {
            if (this.rabbit.onGround() && !this.rabbit.jumping && !((Rabbit.RabbitJumpControl) this.rabbit.jumpControl).wantJump()) {
                this.rabbit.setSpeedModifier(0.0D);
            } else if (this.hasWanted() || this.operation == MoveControl.Operation.JUMPING) {
                this.rabbit.setSpeedModifier(this.nextJumpSpeed);
            }

            super.tick();
        }

        @Override
        public void setWantedPosition(double x, double y, double z, double speedModifier) {
            if (this.rabbit.isInWater()) {
                speedModifier = 1.5D;
            }

            super.setWantedPosition(x, y, z, speedModifier);
            if (speedModifier > 0.0D) {
                this.nextJumpSpeed = speedModifier;
            }

        }
    }

    private static class RabbitAvoidEntityGoal<T extends LivingEntity> extends AvoidEntityGoal<T> {

        private final Rabbit rabbit;

        public RabbitAvoidEntityGoal(Rabbit rabbit, Class<T> avoidClass, float maxDist, double walkSpeedModifier, double sprintSpeedModifier) {
            super(rabbit, avoidClass, maxDist, walkSpeedModifier, sprintSpeedModifier);
            this.rabbit = rabbit;
        }

        @Override
        public boolean canUse() {
            return this.rabbit.getVariant() != Rabbit.Variant.EVIL && super.canUse();
        }
    }

    private static class RaidGardenGoal extends MoveToBlockGoal {

        private final Rabbit rabbit;
        private boolean wantsToRaid;
        private boolean canRaid;

        public RaidGardenGoal(Rabbit rabbit) {
            super(rabbit, (double) 0.7F, 16);
            this.rabbit = rabbit;
        }

        @Override
        public boolean canUse() {
            if (this.nextStartTick <= 0) {
                if (!(Boolean) getServerLevel((Entity) this.rabbit).getGameRules().get(GameRules.MOB_GRIEFING)) {
                    return false;
                }

                this.canRaid = false;
                this.wantsToRaid = this.rabbit.wantsMoreFood();
            }

            return super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canRaid && super.canContinueToUse();
        }

        @Override
        public void tick() {
            super.tick();
            this.rabbit.getLookControl().setLookAt((double) this.blockPos.getX() + 0.5D, (double) (this.blockPos.getY() + 1), (double) this.blockPos.getZ() + 0.5D, 10.0F, (float) this.rabbit.getMaxHeadXRot());
            if (this.isReachedTarget()) {
                Level level = this.rabbit.level();
                BlockPos blockpos = this.blockPos.above();
                BlockState blockstate = level.getBlockState(blockpos);
                Block block = blockstate.getBlock();

                if (this.canRaid && block instanceof CarrotBlock) {
                    int i = (Integer) blockstate.getValue(CarrotBlock.AGE);

                    if (i == 0) {
                        level.setBlock(blockpos, Blocks.AIR.defaultBlockState(), 2);
                        level.destroyBlock(blockpos, true, this.rabbit);
                    } else {
                        level.setBlock(blockpos, (BlockState) blockstate.setValue(CarrotBlock.AGE, i - 1), 2);
                        level.gameEvent(GameEvent.BLOCK_CHANGE, blockpos, GameEvent.Context.of((Entity) this.rabbit));
                        level.levelEvent(2001, blockpos, Block.getId(blockstate));
                    }

                    this.rabbit.moreCarrotTicks = 40;
                }

                this.canRaid = false;
                this.nextStartTick = 10;
            }

        }

        @Override
        protected boolean isValidTarget(LevelReader level, BlockPos pos) {
            BlockState blockstate = level.getBlockState(pos);

            if (blockstate.is(Blocks.FARMLAND) && this.wantsToRaid && !this.canRaid) {
                blockstate = level.getBlockState(pos.above());
                if (blockstate.getBlock() instanceof CarrotBlock && ((CarrotBlock) blockstate.getBlock()).isMaxAge(blockstate)) {
                    this.canRaid = true;
                    return true;
                }
            }

            return false;
        }
    }

    private static class RabbitPanicGoal extends PanicGoal {

        private final Rabbit rabbit;

        public RabbitPanicGoal(Rabbit rabbit, double speedModifier) {
            super(rabbit, speedModifier);
            this.rabbit = rabbit;
        }

        @Override
        public void tick() {
            super.tick();
            this.rabbit.setSpeedModifier(this.speedModifier);
        }
    }
}
