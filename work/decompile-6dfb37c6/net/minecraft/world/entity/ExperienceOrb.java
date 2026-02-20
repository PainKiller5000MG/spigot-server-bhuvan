package net.minecraft.world.entity;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class ExperienceOrb extends Entity {

    protected static final EntityDataAccessor<Integer> DATA_VALUE = SynchedEntityData.<Integer>defineId(ExperienceOrb.class, EntityDataSerializers.INT);
    private static final int LIFETIME = 6000;
    private static final int ENTITY_SCAN_PERIOD = 20;
    private static final int MAX_FOLLOW_DIST = 8;
    private static final int ORB_GROUPS_PER_AREA = 40;
    private static final double ORB_MERGE_DISTANCE = 0.5D;
    private static final short DEFAULT_HEALTH = 5;
    private static final short DEFAULT_AGE = 0;
    private static final short DEFAULT_VALUE = 0;
    private static final int DEFAULT_COUNT = 1;
    private int age;
    private int health;
    private int count;
    private @Nullable Player followingPlayer;
    private final InterpolationHandler interpolation;

    public ExperienceOrb(Level level, double x, double y, double z, int value) {
        this(level, new Vec3(x, y, z), Vec3.ZERO, value);
    }

    public ExperienceOrb(Level level, Vec3 pos, Vec3 roughly, int value) {
        this(EntityType.EXPERIENCE_ORB, level);
        this.setPos(pos);
        if (!level.isClientSide()) {
            this.setYRot(this.random.nextFloat() * 360.0F);
            Vec3 vec32 = new Vec3((this.random.nextDouble() * 0.2D - 0.1D) * 2.0D, this.random.nextDouble() * 0.2D * 2.0D, (this.random.nextDouble() * 0.2D - 0.1D) * 2.0D);

            if (roughly.lengthSqr() > 0.0D && roughly.dot(vec32) < 0.0D) {
                vec32 = vec32.scale(-1.0D);
            }

            double d0 = this.getBoundingBox().getSize();

            this.setPos(pos.add(roughly.normalize().scale(d0 * 0.5D)));
            this.setDeltaMovement(vec32);
            if (!level.noCollision(this.getBoundingBox())) {
                this.unstuckIfPossible(d0);
            }
        }

        this.setValue(value);
    }

    public ExperienceOrb(EntityType<? extends ExperienceOrb> type, Level level) {
        super(type, level);
        this.age = 0;
        this.health = 5;
        this.count = 1;
        this.interpolation = new InterpolationHandler(this);
    }

    protected void unstuckIfPossible(double maxDistance) {
        Vec3 vec3 = this.position().add(0.0D, (double) this.getBbHeight() / 2.0D, 0.0D);
        VoxelShape voxelshape = Shapes.create(AABB.ofSize(vec3, maxDistance, maxDistance, maxDistance));

        this.level().findFreePosition(this, voxelshape, vec3, (double) this.getBbWidth(), (double) this.getBbHeight(), (double) this.getBbWidth()).ifPresent((vec31) -> {
            this.setPos(vec31.add(0.0D, (double) (-this.getBbHeight()) / 2.0D, 0.0D));
        });
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(ExperienceOrb.DATA_VALUE, 0);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.03D;
    }

    @Override
    public void tick() {
        this.interpolation.interpolate();
        if (this.firstTick && this.level().isClientSide()) {
            this.firstTick = false;
        } else {
            super.tick();
            boolean flag = !this.level().noCollision(this.getBoundingBox());

            if (this.isEyeInFluid(FluidTags.WATER)) {
                this.setUnderwaterMovement();
            } else if (!flag) {
                this.applyGravity();
            }

            if (this.level().getFluidState(this.blockPosition()).is(FluidTags.LAVA)) {
                this.setDeltaMovement((double) ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F), (double) 0.2F, (double) ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F));
            }

            if (this.tickCount % 20 == 1) {
                this.scanForMerges();
            }

            this.followNearbyPlayer();
            if (this.followingPlayer == null && !this.level().isClientSide() && flag) {
                boolean flag1 = !this.level().noCollision(this.getBoundingBox().move(this.getDeltaMovement()));

                if (flag1) {
                    this.moveTowardsClosestSpace(this.getX(), (this.getBoundingBox().minY + this.getBoundingBox().maxY) / 2.0D, this.getZ());
                    this.needsSync = true;
                }
            }

            double d0 = this.getDeltaMovement().y;

            this.move(MoverType.SELF, this.getDeltaMovement());
            this.applyEffectsFromBlocks();
            float f = 0.98F;

            if (this.onGround()) {
                f = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction() * 0.98F;
            }

            this.setDeltaMovement(this.getDeltaMovement().scale((double) f));
            if (this.verticalCollisionBelow && d0 < -this.getGravity()) {
                this.setDeltaMovement(new Vec3(this.getDeltaMovement().x, -d0 * 0.4D, this.getDeltaMovement().z));
            }

            ++this.age;
            if (this.age >= 6000) {
                this.discard();
            }

        }
    }

    private void followNearbyPlayer() {
        if (this.followingPlayer == null || this.followingPlayer.isSpectator() || this.followingPlayer.distanceToSqr((Entity) this) > 64.0D) {
            Player player = this.level().getNearestPlayer(this, 8.0D);

            if (player != null && !player.isSpectator() && !player.isDeadOrDying()) {
                this.followingPlayer = player;
            } else {
                this.followingPlayer = null;
            }
        }

        if (this.followingPlayer != null) {
            Vec3 vec3 = new Vec3(this.followingPlayer.getX() - this.getX(), this.followingPlayer.getY() + (double) this.followingPlayer.getEyeHeight() / 2.0D - this.getY(), this.followingPlayer.getZ() - this.getZ());
            double d0 = vec3.lengthSqr();
            double d1 = 1.0D - Math.sqrt(d0) / 8.0D;

            this.setDeltaMovement(this.getDeltaMovement().add(vec3.normalize().scale(d1 * d1 * 0.1D)));
        }

    }

    @Override
    public BlockPos getBlockPosBelowThatAffectsMyMovement() {
        return this.getOnPos(0.999999F);
    }

    private void scanForMerges() {
        if (this.level() instanceof ServerLevel) {
            for (ExperienceOrb experienceorb : this.level().getEntities(EntityTypeTest.forClass(ExperienceOrb.class), this.getBoundingBox().inflate(0.5D), this::canMerge)) {
                this.merge(experienceorb);
            }
        }

    }

    public static void award(ServerLevel level, Vec3 pos, int amount) {
        awardWithDirection(level, pos, Vec3.ZERO, amount);
    }

    public static void awardWithDirection(ServerLevel level, Vec3 pos, Vec3 roughDirection, int amount) {
        while (amount > 0) {
            int j = getExperienceValue(amount);

            amount -= j;
            if (!tryMergeToExisting(level, pos, j)) {
                level.addFreshEntity(new ExperienceOrb(level, pos, roughDirection, j));
            }
        }

    }

    private static boolean tryMergeToExisting(ServerLevel level, Vec3 pos, int value) {
        AABB aabb = AABB.ofSize(pos, 1.0D, 1.0D, 1.0D);
        int j = level.getRandom().nextInt(40);
        List<ExperienceOrb> list = level.getEntities(EntityTypeTest.forClass(ExperienceOrb.class), aabb, (experienceorb) -> {
            return canMerge(experienceorb, j, value);
        });

        if (!list.isEmpty()) {
            ExperienceOrb experienceorb = (ExperienceOrb) list.get(0);

            ++experienceorb.count;
            experienceorb.age = 0;
            return true;
        } else {
            return false;
        }
    }

    private boolean canMerge(ExperienceOrb orb) {
        return orb != this && canMerge(orb, this.getId(), this.getValue());
    }

    private static boolean canMerge(ExperienceOrb orb, int id, int value) {
        return !orb.isRemoved() && (orb.getId() - id) % 40 == 0 && orb.getValue() == value;
    }

    private void merge(ExperienceOrb orb) {
        this.count += orb.count;
        this.age = Math.min(this.age, orb.age);
        orb.discard();
    }

    private void setUnderwaterMovement() {
        Vec3 vec3 = this.getDeltaMovement();

        this.setDeltaMovement(vec3.x * (double) 0.99F, Math.min(vec3.y + (double) 5.0E-4F, (double) 0.06F), vec3.z * (double) 0.99F);
    }

    @Override
    protected void doWaterSplashEffect() {}

    @Override
    public final boolean hurtClient(DamageSource source) {
        return !this.isInvulnerableToBase(source);
    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isInvulnerableToBase(source)) {
            return false;
        } else {
            this.markHurt();
            this.health = (int) ((float) this.health - damage);
            if (this.health <= 0) {
                this.discard();
            }

            return true;
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putShort("Health", (short) this.health);
        output.putShort("Age", (short) this.age);
        output.putShort("Value", (short) this.getValue());
        output.putInt("Count", this.count);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.health = input.getShortOr("Health", (short) 5);
        this.age = input.getShortOr("Age", (short) 0);
        this.setValue(input.getShortOr("Value", (short) 0));
        this.count = (Integer) input.read("Count", ExtraCodecs.POSITIVE_INT).orElse(1);
    }

    @Override
    public void playerTouch(Player player) {
        if (player instanceof ServerPlayer serverplayer) {
            if (player.takeXpDelay == 0) {
                player.takeXpDelay = 2;
                player.take(this, 1);
                int i = this.repairPlayerItems(serverplayer, this.getValue());

                if (i > 0) {
                    player.giveExperiencePoints(i);
                }

                --this.count;
                if (this.count == 0) {
                    this.discard();
                }
            }

        }
    }

    private int repairPlayerItems(ServerPlayer player, int amount) {
        Optional<EnchantedItemInUse> optional = EnchantmentHelper.getRandomItemWith(EnchantmentEffectComponents.REPAIR_WITH_XP, player, ItemStack::isDamaged);

        if (optional.isPresent()) {
            ItemStack itemstack = ((EnchantedItemInUse) optional.get()).itemStack();
            int j = EnchantmentHelper.modifyDurabilityToRepairFromXp(player.level(), itemstack, amount);
            int k = Math.min(j, itemstack.getDamageValue());

            itemstack.setDamageValue(itemstack.getDamageValue() - k);
            if (k > 0) {
                int l = amount - k * amount / j;

                if (l > 0) {
                    return this.repairPlayerItems(player, l);
                }
            }

            return 0;
        } else {
            return amount;
        }
    }

    public int getValue() {
        return (Integer) this.entityData.get(ExperienceOrb.DATA_VALUE);
    }

    public void setValue(int value) {
        this.entityData.set(ExperienceOrb.DATA_VALUE, value);
    }

    public int getIcon() {
        int i = this.getValue();

        return i >= 2477 ? 10 : (i >= 1237 ? 9 : (i >= 617 ? 8 : (i >= 307 ? 7 : (i >= 149 ? 6 : (i >= 73 ? 5 : (i >= 37 ? 4 : (i >= 17 ? 3 : (i >= 7 ? 2 : (i >= 3 ? 1 : 0)))))))));
    }

    public static int getExperienceValue(int maxValue) {
        return maxValue >= 2477 ? 2477 : (maxValue >= 1237 ? 1237 : (maxValue >= 617 ? 617 : (maxValue >= 307 ? 307 : (maxValue >= 149 ? 149 : (maxValue >= 73 ? 73 : (maxValue >= 37 ? 37 : (maxValue >= 17 ? 17 : (maxValue >= 7 ? 7 : (maxValue >= 3 ? 3 : 1)))))))));
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.AMBIENT;
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return this.interpolation;
    }
}
