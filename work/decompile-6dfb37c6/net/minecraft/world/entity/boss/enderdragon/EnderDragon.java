package net.minecraft.world.entity.boss.enderdragon;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhaseManager;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.level.pathfinder.BinaryHeap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class EnderDragon extends Mob implements Enemy {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.<Integer>defineId(EnderDragon.class, EntityDataSerializers.INT);
    private static final TargetingConditions CRYSTAL_DESTROY_TARGETING = TargetingConditions.forCombat().range(64.0D);
    private static final int GROWL_INTERVAL_MIN = 200;
    private static final int GROWL_INTERVAL_MAX = 400;
    private static final float SITTING_ALLOWED_DAMAGE_PERCENTAGE = 0.25F;
    private static final String DRAGON_DEATH_TIME_KEY = "DragonDeathTime";
    private static final String DRAGON_PHASE_KEY = "DragonPhase";
    private static final int DEFAULT_DEATH_TIME = 0;
    public final DragonFlightHistory flightHistory = new DragonFlightHistory();
    public final EnderDragonPart[] subEntities;
    public final EnderDragonPart head;
    private final EnderDragonPart neck;
    private final EnderDragonPart body;
    private final EnderDragonPart tail1;
    private final EnderDragonPart tail2;
    private final EnderDragonPart tail3;
    private final EnderDragonPart wing1;
    private final EnderDragonPart wing2;
    public float oFlapTime;
    public float flapTime;
    public boolean inWall;
    public int dragonDeathTime = 0;
    public float yRotA;
    public @Nullable EndCrystal nearestCrystal;
    private @Nullable EndDragonFight dragonFight;
    private BlockPos fightOrigin;
    private final EnderDragonPhaseManager phaseManager;
    private int growlTime;
    private float sittingDamageReceived;
    private final Node[] nodes;
    private final int[] nodeAdjacency;
    private final BinaryHeap openSet;

    public EnderDragon(EntityType<? extends EnderDragon> type, Level level) {
        super(EntityType.ENDER_DRAGON, level);
        this.fightOrigin = BlockPos.ZERO;
        this.growlTime = 100;
        this.nodes = new Node[24];
        this.nodeAdjacency = new int[24];
        this.openSet = new BinaryHeap();
        this.head = new EnderDragonPart(this, "head", 1.0F, 1.0F);
        this.neck = new EnderDragonPart(this, "neck", 3.0F, 3.0F);
        this.body = new EnderDragonPart(this, "body", 5.0F, 3.0F);
        this.tail1 = new EnderDragonPart(this, "tail", 2.0F, 2.0F);
        this.tail2 = new EnderDragonPart(this, "tail", 2.0F, 2.0F);
        this.tail3 = new EnderDragonPart(this, "tail", 2.0F, 2.0F);
        this.wing1 = new EnderDragonPart(this, "wing", 4.0F, 2.0F);
        this.wing2 = new EnderDragonPart(this, "wing", 4.0F, 2.0F);
        this.subEntities = new EnderDragonPart[]{this.head, this.neck, this.body, this.tail1, this.tail2, this.tail3, this.wing1, this.wing2};
        this.setHealth(this.getMaxHealth());
        this.noPhysics = true;
        this.phaseManager = new EnderDragonPhaseManager(this);
    }

    public void setDragonFight(EndDragonFight fight) {
        this.dragonFight = fight;
    }

    public void setFightOrigin(BlockPos fightOrigin) {
        this.fightOrigin = fightOrigin;
    }

    public BlockPos getFightOrigin() {
        return this.fightOrigin;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 200.0D).add(Attributes.CAMERA_DISTANCE, 16.0D);
    }

    @Override
    public boolean isFlapping() {
        float f = Mth.cos((double) (this.flapTime * ((float) Math.PI * 2F)));
        float f1 = Mth.cos((double) (this.oFlapTime * ((float) Math.PI * 2F)));

        return f1 <= -0.3F && f >= -0.3F;
    }

    @Override
    public void onFlap() {
        if (this.level().isClientSide() && !this.isSilent()) {
            this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.ENDER_DRAGON_FLAP, this.getSoundSource(), 5.0F, 0.8F + this.random.nextFloat() * 0.3F, false);
        }

    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(EnderDragon.DATA_PHASE, EnderDragonPhase.HOVERING.getId());
    }

    @Override
    public void aiStep() {
        this.processFlappingMovement();
        if (this.level().isClientSide()) {
            this.setHealth(this.getHealth());
            if (!this.isSilent() && !this.phaseManager.getCurrentPhase().isSitting() && --this.growlTime < 0) {
                this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.ENDER_DRAGON_GROWL, this.getSoundSource(), 2.5F, 0.8F + this.random.nextFloat() * 0.3F, false);
                this.growlTime = 200 + this.random.nextInt(200);
            }
        }

        if (this.dragonFight == null) {
            Level level = this.level();

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;
                EndDragonFight enddragonfight = serverlevel.getDragonFight();

                if (enddragonfight != null && this.getUUID().equals(enddragonfight.getDragonUUID())) {
                    this.dragonFight = enddragonfight;
                }
            }
        }

        this.oFlapTime = this.flapTime;
        if (this.isDeadOrDying()) {
            float f = (this.random.nextFloat() - 0.5F) * 8.0F;
            float f1 = (this.random.nextFloat() - 0.5F) * 4.0F;
            float f2 = (this.random.nextFloat() - 0.5F) * 8.0F;

            this.level().addParticle(ParticleTypes.EXPLOSION, this.getX() + (double) f, this.getY() + 2.0D + (double) f1, this.getZ() + (double) f2, 0.0D, 0.0D, 0.0D);
        } else {
            this.checkCrystals();
            Vec3 vec3 = this.getDeltaMovement();
            float f3 = 0.2F / ((float) vec3.horizontalDistance() * 10.0F + 1.0F);

            f3 *= (float) Math.pow(2.0D, vec3.y);
            if (this.phaseManager.getCurrentPhase().isSitting()) {
                this.flapTime += 0.1F;
            } else if (this.inWall) {
                this.flapTime += f3 * 0.5F;
            } else {
                this.flapTime += f3;
            }

            this.setYRot(Mth.wrapDegrees(this.getYRot()));
            if (this.isNoAi()) {
                this.flapTime = 0.5F;
            } else {
                this.flightHistory.record(this.getY(), this.getYRot());
                Level level1 = this.level();

                if (level1 instanceof ServerLevel) {
                    ServerLevel serverlevel1 = (ServerLevel) level1;
                    DragonPhaseInstance dragonphaseinstance = this.phaseManager.getCurrentPhase();

                    dragonphaseinstance.doServerTick(serverlevel1);
                    if (this.phaseManager.getCurrentPhase() != dragonphaseinstance) {
                        dragonphaseinstance = this.phaseManager.getCurrentPhase();
                        dragonphaseinstance.doServerTick(serverlevel1);
                    }

                    Vec3 vec31 = dragonphaseinstance.getFlyTargetLocation();

                    if (vec31 != null) {
                        double d0 = vec31.x - this.getX();
                        double d1 = vec31.y - this.getY();
                        double d2 = vec31.z - this.getZ();
                        double d3 = d0 * d0 + d1 * d1 + d2 * d2;
                        float f4 = dragonphaseinstance.getFlySpeed();
                        double d4 = Math.sqrt(d0 * d0 + d2 * d2);

                        if (d4 > 0.0D) {
                            d1 = Mth.clamp(d1 / d4, (double) (-f4), (double) f4);
                        }

                        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, d1 * 0.01D, 0.0D));
                        this.setYRot(Mth.wrapDegrees(this.getYRot()));
                        Vec3 vec32 = vec31.subtract(this.getX(), this.getY(), this.getZ()).normalize();
                        Vec3 vec33 = (new Vec3((double) Mth.sin((double) (this.getYRot() * ((float) Math.PI / 180F))), this.getDeltaMovement().y, (double) (-Mth.cos((double) (this.getYRot() * ((float) Math.PI / 180F)))))).normalize();
                        float f5 = Math.max(((float) vec33.dot(vec32) + 0.5F) / 1.5F, 0.0F);

                        if (Math.abs(d0) > (double) 1.0E-5F || Math.abs(d2) > (double) 1.0E-5F) {
                            float f6 = Mth.clamp(Mth.wrapDegrees(180.0F - (float) Mth.atan2(d0, d2) * (180F / (float) Math.PI) - this.getYRot()), -50.0F, 50.0F);

                            this.yRotA *= 0.8F;
                            this.yRotA += f6 * dragonphaseinstance.getTurnSpeed();
                            this.setYRot(this.getYRot() + this.yRotA * 0.1F);
                        }

                        float f7 = (float) (2.0D / (d3 + 1.0D));
                        float f8 = 0.06F;

                        this.moveRelative(0.06F * (f5 * f7 + (1.0F - f7)), new Vec3(0.0D, 0.0D, -1.0D));
                        if (this.inWall) {
                            this.move(MoverType.SELF, this.getDeltaMovement().scale((double) 0.8F));
                        } else {
                            this.move(MoverType.SELF, this.getDeltaMovement());
                        }

                        Vec3 vec34 = this.getDeltaMovement().normalize();
                        double d5 = 0.8D + 0.15D * (vec34.dot(vec33) + 1.0D) / 2.0D;

                        this.setDeltaMovement(this.getDeltaMovement().multiply(d5, (double) 0.91F, d5));
                    }
                } else {
                    this.interpolation.interpolate();
                    this.phaseManager.getCurrentPhase().doClientTick();
                }

                if (!this.level().isClientSide()) {
                    this.applyEffectsFromBlocks();
                }

                this.yBodyRot = this.getYRot();
                Vec3[] avec3 = new Vec3[this.subEntities.length];

                for (int i = 0; i < this.subEntities.length; ++i) {
                    avec3[i] = new Vec3(this.subEntities[i].getX(), this.subEntities[i].getY(), this.subEntities[i].getZ());
                }

                float f9 = (float) (this.flightHistory.get(5).y() - this.flightHistory.get(10).y()) * 10.0F * ((float) Math.PI / 180F);
                float f10 = Mth.cos((double) f9);
                float f11 = Mth.sin((double) f9);
                float f12 = this.getYRot() * ((float) Math.PI / 180F);
                float f13 = Mth.sin((double) f12);
                float f14 = Mth.cos((double) f12);

                this.tickPart(this.body, (double) (f13 * 0.5F), 0.0D, (double) (-f14 * 0.5F));
                this.tickPart(this.wing1, (double) (f14 * 4.5F), 2.0D, (double) (f13 * 4.5F));
                this.tickPart(this.wing2, (double) (f14 * -4.5F), 2.0D, (double) (f13 * -4.5F));
                Level level2 = this.level();

                if (level2 instanceof ServerLevel) {
                    ServerLevel serverlevel2 = (ServerLevel) level2;

                    if (this.hurtTime == 0) {
                        this.knockBack(serverlevel2, serverlevel2.getEntities(this, this.wing1.getBoundingBox().inflate(4.0D, 2.0D, 4.0D).move(0.0D, -2.0D, 0.0D), EntitySelector.NO_CREATIVE_OR_SPECTATOR));
                        this.knockBack(serverlevel2, serverlevel2.getEntities(this, this.wing2.getBoundingBox().inflate(4.0D, 2.0D, 4.0D).move(0.0D, -2.0D, 0.0D), EntitySelector.NO_CREATIVE_OR_SPECTATOR));
                        this.hurt(serverlevel2, serverlevel2.getEntities(this, this.head.getBoundingBox().inflate(1.0D), EntitySelector.NO_CREATIVE_OR_SPECTATOR));
                        this.hurt(serverlevel2, serverlevel2.getEntities(this, this.neck.getBoundingBox().inflate(1.0D), EntitySelector.NO_CREATIVE_OR_SPECTATOR));
                    }
                }

                float f15 = Mth.sin((double) (this.getYRot() * ((float) Math.PI / 180F) - this.yRotA * 0.01F));
                float f16 = Mth.cos((double) (this.getYRot() * ((float) Math.PI / 180F) - this.yRotA * 0.01F));
                float f17 = this.getHeadYOffset();

                this.tickPart(this.head, (double) (f15 * 6.5F * f10), (double) (f17 + f11 * 6.5F), (double) (-f16 * 6.5F * f10));
                this.tickPart(this.neck, (double) (f15 * 5.5F * f10), (double) (f17 + f11 * 5.5F), (double) (-f16 * 5.5F * f10));
                DragonFlightHistory.Sample dragonflighthistory_sample = this.flightHistory.get(5);

                for (int j = 0; j < 3; ++j) {
                    EnderDragonPart enderdragonpart = null;

                    if (j == 0) {
                        enderdragonpart = this.tail1;
                    }

                    if (j == 1) {
                        enderdragonpart = this.tail2;
                    }

                    if (j == 2) {
                        enderdragonpart = this.tail3;
                    }

                    DragonFlightHistory.Sample dragonflighthistory_sample1 = this.flightHistory.get(12 + j * 2);
                    float f18 = this.getYRot() * ((float) Math.PI / 180F) + this.rotWrap((double) (dragonflighthistory_sample1.yRot() - dragonflighthistory_sample.yRot())) * ((float) Math.PI / 180F);
                    float f19 = Mth.sin((double) f18);
                    float f20 = Mth.cos((double) f18);
                    float f21 = 1.5F;
                    float f22 = (float) (j + 1) * 2.0F;

                    this.tickPart(enderdragonpart, (double) (-(f13 * 1.5F + f19 * f22) * f10), dragonflighthistory_sample1.y() - dragonflighthistory_sample.y() - (double) ((f22 + 1.5F) * f11) + 1.5D, (double) ((f14 * 1.5F + f20 * f22) * f10));
                }

                Level level3 = this.level();

                if (level3 instanceof ServerLevel) {
                    ServerLevel serverlevel3 = (ServerLevel) level3;

                    this.inWall = this.checkWalls(serverlevel3, this.head.getBoundingBox()) | this.checkWalls(serverlevel3, this.neck.getBoundingBox()) | this.checkWalls(serverlevel3, this.body.getBoundingBox());
                    if (this.dragonFight != null) {
                        this.dragonFight.updateDragon(this);
                    }
                }

                for (int k = 0; k < this.subEntities.length; ++k) {
                    this.subEntities[k].xo = avec3[k].x;
                    this.subEntities[k].yo = avec3[k].y;
                    this.subEntities[k].zo = avec3[k].z;
                    this.subEntities[k].xOld = avec3[k].x;
                    this.subEntities[k].yOld = avec3[k].y;
                    this.subEntities[k].zOld = avec3[k].z;
                }

            }
        }
    }

    private void tickPart(EnderDragonPart part, double x, double y, double z) {
        part.setPos(this.getX() + x, this.getY() + y, this.getZ() + z);
    }

    private float getHeadYOffset() {
        if (this.phaseManager.getCurrentPhase().isSitting()) {
            return -1.0F;
        } else {
            DragonFlightHistory.Sample dragonflighthistory_sample = this.flightHistory.get(5);
            DragonFlightHistory.Sample dragonflighthistory_sample1 = this.flightHistory.get(0);

            return (float) (dragonflighthistory_sample.y() - dragonflighthistory_sample1.y());
        }
    }

    private void checkCrystals() {
        if (this.nearestCrystal != null) {
            if (this.nearestCrystal.isRemoved()) {
                this.nearestCrystal = null;
            } else if (this.tickCount % 10 == 0 && this.getHealth() < this.getMaxHealth()) {
                this.setHealth(this.getHealth() + 1.0F);
            }
        }

        if (this.random.nextInt(10) == 0) {
            List<EndCrystal> list = this.level().<EndCrystal>getEntitiesOfClass(EndCrystal.class, this.getBoundingBox().inflate(32.0D));
            EndCrystal endcrystal = null;
            double d0 = Double.MAX_VALUE;

            for (EndCrystal endcrystal1 : list) {
                double d1 = endcrystal1.distanceToSqr((Entity) this);

                if (d1 < d0) {
                    d0 = d1;
                    endcrystal = endcrystal1;
                }
            }

            this.nearestCrystal = endcrystal;
        }

    }

    private void knockBack(ServerLevel serverLevel, List<Entity> entities) {
        double d0 = (this.body.getBoundingBox().minX + this.body.getBoundingBox().maxX) / 2.0D;
        double d1 = (this.body.getBoundingBox().minZ + this.body.getBoundingBox().maxZ) / 2.0D;

        for (Entity entity : entities) {
            if (entity instanceof LivingEntity livingentity) {
                double d2 = entity.getX() - d0;
                double d3 = entity.getZ() - d1;
                double d4 = Math.max(d2 * d2 + d3 * d3, 0.1D);

                entity.push(d2 / d4 * 4.0D, (double) 0.2F, d3 / d4 * 4.0D);
                if (!this.phaseManager.getCurrentPhase().isSitting() && livingentity.getLastHurtByMobTimestamp() < entity.tickCount - 2) {
                    DamageSource damagesource = this.damageSources().mobAttack(this);

                    entity.hurtServer(serverLevel, damagesource, 5.0F);
                    EnchantmentHelper.doPostAttackEffects(serverLevel, entity, damagesource);
                }
            }
        }

    }

    private void hurt(ServerLevel level, List<Entity> entities) {
        for (Entity entity : entities) {
            if (entity instanceof LivingEntity) {
                DamageSource damagesource = this.damageSources().mobAttack(this);

                entity.hurtServer(level, damagesource, 10.0F);
                EnchantmentHelper.doPostAttackEffects(level, entity, damagesource);
            }
        }

    }

    private float rotWrap(double d) {
        return (float) Mth.wrapDegrees(d);
    }

    private boolean checkWalls(ServerLevel level, AABB bb) {
        int i = Mth.floor(bb.minX);
        int j = Mth.floor(bb.minY);
        int k = Mth.floor(bb.minZ);
        int l = Mth.floor(bb.maxX);
        int i1 = Mth.floor(bb.maxY);
        int j1 = Mth.floor(bb.maxZ);
        boolean flag = false;
        boolean flag1 = false;

        for (int k1 = i; k1 <= l; ++k1) {
            for (int l1 = j; l1 <= i1; ++l1) {
                for (int i2 = k; i2 <= j1; ++i2) {
                    BlockPos blockpos = new BlockPos(k1, l1, i2);
                    BlockState blockstate = level.getBlockState(blockpos);

                    if (!blockstate.isAir() && !blockstate.is(BlockTags.DRAGON_TRANSPARENT)) {
                        if ((Boolean) level.getGameRules().get(GameRules.MOB_GRIEFING) && !blockstate.is(BlockTags.DRAGON_IMMUNE)) {
                            flag1 = level.removeBlock(blockpos, false) || flag1;
                        } else {
                            flag = true;
                        }
                    }
                }
            }
        }

        if (flag1) {
            BlockPos blockpos1 = new BlockPos(i + this.random.nextInt(l - i + 1), j + this.random.nextInt(i1 - j + 1), k + this.random.nextInt(j1 - k + 1));

            level.levelEvent(2008, blockpos1, 0);
        }

        return flag;
    }

    public boolean hurt(ServerLevel level, EnderDragonPart part, DamageSource source, float damage) {
        if (this.phaseManager.getCurrentPhase().getPhase() == EnderDragonPhase.DYING) {
            return false;
        } else {
            damage = this.phaseManager.getCurrentPhase().onHurt(source, damage);
            if (part != this.head) {
                damage = damage / 4.0F + Math.min(damage, 1.0F);
            }

            if (damage < 0.01F) {
                return false;
            } else {
                if (source.getEntity() instanceof Player || source.is(DamageTypeTags.ALWAYS_HURTS_ENDER_DRAGONS)) {
                    float f1 = this.getHealth();

                    this.reallyHurt(level, source, damage);
                    if (this.isDeadOrDying() && !this.phaseManager.getCurrentPhase().isSitting()) {
                        this.setHealth(1.0F);
                        this.phaseManager.setPhase(EnderDragonPhase.DYING);
                    }

                    if (this.phaseManager.getCurrentPhase().isSitting()) {
                        this.sittingDamageReceived = this.sittingDamageReceived + f1 - this.getHealth();
                        if (this.sittingDamageReceived > 0.25F * this.getMaxHealth()) {
                            this.sittingDamageReceived = 0.0F;
                            this.phaseManager.setPhase(EnderDragonPhase.TAKEOFF);
                        }
                    }
                }

                return true;
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return this.hurt(level, this.body, source, damage);
    }

    protected void reallyHurt(ServerLevel level, DamageSource source, float damage) {
        super.hurtServer(level, source, damage);
    }

    @Override
    public void kill(ServerLevel level) {
        this.remove(Entity.RemovalReason.KILLED);
        this.gameEvent(GameEvent.ENTITY_DIE);
        if (this.dragonFight != null) {
            this.dragonFight.updateDragon(this);
            this.dragonFight.setDragonKilled(this);
        }

    }

    @Override
    protected void tickDeath() {
        if (this.dragonFight != null) {
            this.dragonFight.updateDragon(this);
        }

        ++this.dragonDeathTime;
        if (this.dragonDeathTime >= 180 && this.dragonDeathTime <= 200) {
            float f = (this.random.nextFloat() - 0.5F) * 8.0F;
            float f1 = (this.random.nextFloat() - 0.5F) * 4.0F;
            float f2 = (this.random.nextFloat() - 0.5F) * 8.0F;

            this.level().addParticle(ParticleTypes.EXPLOSION_EMITTER, this.getX() + (double) f, this.getY() + 2.0D + (double) f1, this.getZ() + (double) f2, 0.0D, 0.0D, 0.0D);
        }

        int i = 500;

        if (this.dragonFight != null && !this.dragonFight.hasPreviouslyKilledDragon()) {
            i = 12000;
        }

        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            if (this.dragonDeathTime > 150 && this.dragonDeathTime % 5 == 0 && (Boolean) serverlevel.getGameRules().get(GameRules.MOB_DROPS)) {
                ExperienceOrb.award(serverlevel, this.position(), Mth.floor((float) i * 0.08F));
            }

            if (this.dragonDeathTime == 1 && !this.isSilent()) {
                serverlevel.globalLevelEvent(1028, this.blockPosition(), 0);
            }
        }

        Vec3 vec3 = new Vec3(0.0D, (double) 0.1F, 0.0D);

        this.move(MoverType.SELF, vec3);

        for (EnderDragonPart enderdragonpart : this.subEntities) {
            enderdragonpart.setOldPosAndRot();
            enderdragonpart.setPos(enderdragonpart.position().add(vec3));
        }

        if (this.dragonDeathTime == 200) {
            Level level1 = this.level();

            if (level1 instanceof ServerLevel) {
                ServerLevel serverlevel1 = (ServerLevel) level1;

                if ((Boolean) serverlevel1.getGameRules().get(GameRules.MOB_DROPS)) {
                    ExperienceOrb.award(serverlevel1, this.position(), Mth.floor((float) i * 0.2F));
                }

                if (this.dragonFight != null) {
                    this.dragonFight.setDragonKilled(this);
                }

                this.remove(Entity.RemovalReason.KILLED);
                this.gameEvent(GameEvent.ENTITY_DIE);
            }
        }

    }

    public int findClosestNode() {
        if (this.nodes[0] == null) {
            for (int i = 0; i < 24; ++i) {
                int j = 5;
                int k;
                int l;

                if (i < 12) {
                    k = Mth.floor(60.0F * Mth.cos((double) (2.0F * (-(float) Math.PI + 0.2617994F * (float) i))));
                    l = Mth.floor(60.0F * Mth.sin((double) (2.0F * (-(float) Math.PI + 0.2617994F * (float) i))));
                } else if (i < 20) {
                    int i1 = i - 12;

                    k = Mth.floor(40.0F * Mth.cos((double) (2.0F * (-(float) Math.PI + ((float) Math.PI / 8F) * (float) i1))));
                    l = Mth.floor(40.0F * Mth.sin((double) (2.0F * (-(float) Math.PI + ((float) Math.PI / 8F) * (float) i1))));
                    j += 10;
                } else {
                    int j1 = i - 20;

                    k = Mth.floor(20.0F * Mth.cos((double) (2.0F * (-(float) Math.PI + ((float) Math.PI / 4F) * (float) j1))));
                    l = Mth.floor(20.0F * Mth.sin((double) (2.0F * (-(float) Math.PI + ((float) Math.PI / 4F) * (float) j1))));
                }

                int k1 = Math.max(73, this.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(k, 0, l)).getY() + j);

                this.nodes[i] = new Node(k, k1, l);
            }

            this.nodeAdjacency[0] = 6146;
            this.nodeAdjacency[1] = 8197;
            this.nodeAdjacency[2] = 8202;
            this.nodeAdjacency[3] = 16404;
            this.nodeAdjacency[4] = 32808;
            this.nodeAdjacency[5] = 32848;
            this.nodeAdjacency[6] = 65696;
            this.nodeAdjacency[7] = 131392;
            this.nodeAdjacency[8] = 131712;
            this.nodeAdjacency[9] = 263424;
            this.nodeAdjacency[10] = 526848;
            this.nodeAdjacency[11] = 525313;
            this.nodeAdjacency[12] = 1581057;
            this.nodeAdjacency[13] = 3166214;
            this.nodeAdjacency[14] = 2138120;
            this.nodeAdjacency[15] = 6373424;
            this.nodeAdjacency[16] = 4358208;
            this.nodeAdjacency[17] = 12910976;
            this.nodeAdjacency[18] = 9044480;
            this.nodeAdjacency[19] = 9706496;
            this.nodeAdjacency[20] = 15216640;
            this.nodeAdjacency[21] = 13688832;
            this.nodeAdjacency[22] = 11763712;
            this.nodeAdjacency[23] = 8257536;
        }

        return this.findClosestNode(this.getX(), this.getY(), this.getZ());
    }

    public int findClosestNode(double tX, double tY, double tZ) {
        float f = 10000.0F;
        int i = 0;
        Node node = new Node(Mth.floor(tX), Mth.floor(tY), Mth.floor(tZ));
        int j = 0;

        if (this.dragonFight == null || this.dragonFight.getCrystalsAlive() == 0) {
            j = 12;
        }

        for (int k = j; k < 24; ++k) {
            if (this.nodes[k] != null) {
                float f1 = this.nodes[k].distanceToSqr(node);

                if (f1 < f) {
                    f = f1;
                    i = k;
                }
            }
        }

        return i;
    }

    public @Nullable Path findPath(int startIndex, int endIndex, @Nullable Node finalNode) {
        for (int k = 0; k < 24; ++k) {
            Node node1 = this.nodes[k];

            node1.closed = false;
            node1.f = 0.0F;
            node1.g = 0.0F;
            node1.h = 0.0F;
            node1.cameFrom = null;
            node1.heapIdx = -1;
        }

        Node node2 = this.nodes[startIndex];
        Node node3 = this.nodes[endIndex];

        node2.g = 0.0F;
        node2.h = node2.distanceTo(node3);
        node2.f = node2.h;
        this.openSet.clear();
        this.openSet.insert(node2);
        Node node4 = node2;
        int l = 0;

        if (this.dragonFight == null || this.dragonFight.getCrystalsAlive() == 0) {
            l = 12;
        }

        while (!this.openSet.isEmpty()) {
            Node node5 = this.openSet.pop();

            if (node5.equals(node3)) {
                if (finalNode != null) {
                    finalNode.cameFrom = node3;
                    node3 = finalNode;
                }

                return this.reconstructPath(node2, node3);
            }

            if (node5.distanceTo(node3) < node4.distanceTo(node3)) {
                node4 = node5;
            }

            node5.closed = true;
            int i1 = 0;

            for (int j1 = 0; j1 < 24; ++j1) {
                if (this.nodes[j1] == node5) {
                    i1 = j1;
                    break;
                }
            }

            for (int k1 = l; k1 < 24; ++k1) {
                if ((this.nodeAdjacency[i1] & 1 << k1) > 0) {
                    Node node6 = this.nodes[k1];

                    if (!node6.closed) {
                        float f = node5.g + node5.distanceTo(node6);

                        if (!node6.inOpenSet() || f < node6.g) {
                            node6.cameFrom = node5;
                            node6.g = f;
                            node6.h = node6.distanceTo(node3);
                            if (node6.inOpenSet()) {
                                this.openSet.changeCost(node6, node6.g + node6.h);
                            } else {
                                node6.f = node6.g + node6.h;
                                this.openSet.insert(node6);
                            }
                        }
                    }
                }
            }
        }

        if (node4 == node2) {
            return null;
        } else {
            EnderDragon.LOGGER.debug("Failed to find path from {} to {}", startIndex, endIndex);
            if (finalNode != null) {
                finalNode.cameFrom = node4;
                node4 = finalNode;
            }

            return this.reconstructPath(node2, node4);
        }
    }

    private Path reconstructPath(Node from, Node to) {
        List<Node> list = Lists.newArrayList();
        Node node2 = to;

        list.add(0, to);

        while (node2.cameFrom != null) {
            node2 = node2.cameFrom;
            list.add(0, node2);
        }

        return new Path(list, new BlockPos(to.x, to.y, to.z), true);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("DragonPhase", this.phaseManager.getCurrentPhase().getPhase().getId());
        output.putInt("DragonDeathTime", this.dragonDeathTime);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.getInt("DragonPhase").ifPresent((integer) -> {
            this.phaseManager.setPhase(EnderDragonPhase.getById(integer));
        });
        this.dragonDeathTime = input.getIntOr("DragonDeathTime", 0);
    }

    @Override
    public void checkDespawn() {}

    public EnderDragonPart[] getSubEntities() {
        return this.subEntities;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENDER_DRAGON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENDER_DRAGON_HURT;
    }

    @Override
    protected float getSoundVolume() {
        return 5.0F;
    }

    public Vec3 getHeadLookVector(float a) {
        DragonPhaseInstance dragonphaseinstance = this.phaseManager.getCurrentPhase();
        EnderDragonPhase<? extends DragonPhaseInstance> enderdragonphase = dragonphaseinstance.getPhase();
        Vec3 vec3;

        if (enderdragonphase != EnderDragonPhase.LANDING && enderdragonphase != EnderDragonPhase.TAKEOFF) {
            if (dragonphaseinstance.isSitting()) {
                float f1 = this.getXRot();
                float f2 = 1.5F;

                this.setXRot(-45.0F);
                vec3 = this.getViewVector(a);
                this.setXRot(f1);
            } else {
                vec3 = this.getViewVector(a);
            }
        } else {
            BlockPos blockpos = this.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EndPodiumFeature.getLocation(this.fightOrigin));
            float f3 = Math.max((float) Math.sqrt(blockpos.distToCenterSqr(this.position())) / 4.0F, 1.0F);
            float f4 = 6.0F / f3;
            float f5 = this.getXRot();
            float f6 = 1.5F;

            this.setXRot(-f4 * 1.5F * 5.0F);
            vec3 = this.getViewVector(a);
            this.setXRot(f5);
        }

        return vec3;
    }

    public void onCrystalDestroyed(ServerLevel level, EndCrystal crystal, BlockPos pos, DamageSource source) {
        Entity entity = source.getEntity();
        Player player;

        if (entity instanceof Player player1) {
            player = player1;
        } else {
            player = level.getNearestPlayer(EnderDragon.CRYSTAL_DESTROY_TARGETING, (double) pos.getX(), (double) pos.getY(), (double) pos.getZ());
        }

        if (crystal == this.nearestCrystal) {
            this.hurt(level, this.head, this.damageSources().explosion(crystal, player), 10.0F);
        }

        this.phaseManager.getCurrentPhase().onCrystalDestroyed(crystal, pos, source, player);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        if (EnderDragon.DATA_PHASE.equals(accessor) && this.level().isClientSide()) {
            this.phaseManager.setPhase(EnderDragonPhase.getById((Integer) this.getEntityData().get(EnderDragon.DATA_PHASE)));
        }

        super.onSyncedDataUpdated(accessor);
    }

    public EnderDragonPhaseManager getPhaseManager() {
        return this.phaseManager;
    }

    public @Nullable EndDragonFight getDragonFight() {
        return this.dragonFight;
    }

    @Override
    public boolean addEffect(MobEffectInstance newEffect, @Nullable Entity source) {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean ignorePassenger) {
        return false;
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        EnderDragonPart[] aenderdragonpart = this.getSubEntities();

        for (int i = 0; i < aenderdragonpart.length; ++i) {
            aenderdragonpart[i].setId(i + packet.getId() + 1);
        }

    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return target.canBeSeenAsEnemy();
    }

    @Override
    protected float sanitizeScale(float scale) {
        return 1.0F;
    }
}
