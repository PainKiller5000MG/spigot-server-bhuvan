package net.minecraft.world.entity.monster.illager;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.scores.PlayerTeam;
import org.jspecify.annotations.Nullable;

public class Evoker extends SpellcasterIllager {

    private @Nullable Sheep wololoTarget;

    public Evoker(EntityType<? extends Evoker> type, Level level) {
        super(type, level);
        this.xpReward = 10;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new Evoker.EvokerCastingSpellGoal());
        this.goalSelector.addGoal(2, new AvoidEntityGoal(this, Player.class, 8.0F, 0.6D, 1.0D));
        this.goalSelector.addGoal(3, new AvoidEntityGoal(this, Creaking.class, 8.0F, 0.6D, 1.0D));
        this.goalSelector.addGoal(4, new Evoker.EvokerSummonSpellGoal());
        this.goalSelector.addGoal(5, new Evoker.EvokerAttackSpellGoal());
        this.goalSelector.addGoal(6, new Evoker.EvokerWololoSpellGoal());
        this.goalSelector.addGoal(8, new RandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
        this.targetSelector.addGoal(1, (new HurtByTargetGoal(this, new Class[]{Raider.class})).setAlertOthers());
        this.targetSelector.addGoal(2, (new NearestAttackableTargetGoal(this, Player.class, true)).setUnseenMemoryTicks(300));
        this.targetSelector.addGoal(3, (new NearestAttackableTargetGoal(this, AbstractVillager.class, false)).setUnseenMemoryTicks(300));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, IronGolem.class, false));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MOVEMENT_SPEED, 0.5D).add(Attributes.FOLLOW_RANGE, 12.0D).add(Attributes.MAX_HEALTH, 24.0D);
    }

    @Override
    public SoundEvent getCelebrateSound() {
        return SoundEvents.EVOKER_CELEBRATE;
    }

    @Override
    protected boolean considersEntityAsAlly(Entity other) {
        if (other == this) {
            return true;
        } else if (super.considersEntityAsAlly(other)) {
            return true;
        } else {
            if (other instanceof Vex) {
                Vex vex = (Vex) other;

                if (vex.getOwner() != null) {
                    return this.considersEntityAsAlly(vex.getOwner());
                }
            }

            return false;
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.EVOKER_AMBIENT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.EVOKER_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.EVOKER_HURT;
    }

    private void setWololoTarget(@Nullable Sheep wololoTarget) {
        this.wololoTarget = wololoTarget;
    }

    private @Nullable Sheep getWololoTarget() {
        return this.wololoTarget;
    }

    @Override
    protected SoundEvent getCastingSoundEvent() {
        return SoundEvents.EVOKER_CAST_SPELL;
    }

    @Override
    public void applyRaidBuffs(ServerLevel level, int wave, boolean isCaptain) {}

    private class EvokerCastingSpellGoal extends SpellcasterIllager.SpellcasterCastingSpellGoal {

        private EvokerCastingSpellGoal() {}

        @Override
        public void tick() {
            if (Evoker.this.getTarget() != null) {
                Evoker.this.getLookControl().setLookAt(Evoker.this.getTarget(), (float) Evoker.this.getMaxHeadYRot(), (float) Evoker.this.getMaxHeadXRot());
            } else if (Evoker.this.getWololoTarget() != null) {
                Evoker.this.getLookControl().setLookAt(Evoker.this.getWololoTarget(), (float) Evoker.this.getMaxHeadYRot(), (float) Evoker.this.getMaxHeadXRot());
            }

        }
    }

    private class EvokerAttackSpellGoal extends SpellcasterIllager.SpellcasterUseSpellGoal {

        private EvokerAttackSpellGoal() {}

        @Override
        protected int getCastingTime() {
            return 40;
        }

        @Override
        protected int getCastingInterval() {
            return 100;
        }

        @Override
        protected void performSpellCasting() {
            LivingEntity livingentity = Evoker.this.getTarget();
            double d0 = Math.min(livingentity.getY(), Evoker.this.getY());
            double d1 = Math.max(livingentity.getY(), Evoker.this.getY()) + 1.0D;
            float f = (float) Mth.atan2(livingentity.getZ() - Evoker.this.getZ(), livingentity.getX() - Evoker.this.getX());

            if (Evoker.this.distanceToSqr((Entity) livingentity) < 9.0D) {
                for (int i = 0; i < 5; ++i) {
                    float f1 = f + (float) i * (float) Math.PI * 0.4F;

                    this.createSpellEntity(Evoker.this.getX() + (double) Mth.cos((double) f1) * 1.5D, Evoker.this.getZ() + (double) Mth.sin((double) f1) * 1.5D, d0, d1, f1, 0);
                }

                for (int j = 0; j < 8; ++j) {
                    float f2 = f + (float) j * (float) Math.PI * 2.0F / 8.0F + 1.2566371F;

                    this.createSpellEntity(Evoker.this.getX() + (double) Mth.cos((double) f2) * 2.5D, Evoker.this.getZ() + (double) Mth.sin((double) f2) * 2.5D, d0, d1, f2, 3);
                }
            } else {
                for (int k = 0; k < 16; ++k) {
                    double d2 = 1.25D * (double) (k + 1);
                    int l = 1 * k;

                    this.createSpellEntity(Evoker.this.getX() + (double) Mth.cos((double) f) * d2, Evoker.this.getZ() + (double) Mth.sin((double) f) * d2, d0, d1, f, l);
                }
            }

        }

        private void createSpellEntity(double x, double z, double minY, double maxY, float angle, int delayTicks) {
            BlockPos blockpos = BlockPos.containing(x, maxY, z);
            boolean flag = false;
            double d4 = 0.0D;

            do {
                BlockPos blockpos1 = blockpos.below();
                BlockState blockstate = Evoker.this.level().getBlockState(blockpos1);

                if (blockstate.isFaceSturdy(Evoker.this.level(), blockpos1, Direction.UP)) {
                    if (!Evoker.this.level().isEmptyBlock(blockpos)) {
                        BlockState blockstate1 = Evoker.this.level().getBlockState(blockpos);
                        VoxelShape voxelshape = blockstate1.getCollisionShape(Evoker.this.level(), blockpos);

                        if (!voxelshape.isEmpty()) {
                            d4 = voxelshape.max(Direction.Axis.Y);
                        }
                    }

                    flag = true;
                    break;
                }

                blockpos = blockpos.below();
            } while (blockpos.getY() >= Mth.floor(minY) - 1);

            if (flag) {
                Evoker.this.level().addFreshEntity(new EvokerFangs(Evoker.this.level(), x, (double) blockpos.getY() + d4, z, angle, delayTicks, Evoker.this));
                Evoker.this.level().gameEvent(GameEvent.ENTITY_PLACE, new Vec3(x, (double) blockpos.getY() + d4, z), GameEvent.Context.of((Entity) Evoker.this));
            }

        }

        @Override
        protected SoundEvent getSpellPrepareSound() {
            return SoundEvents.EVOKER_PREPARE_ATTACK;
        }

        @Override
        protected SpellcasterIllager.IllagerSpell getSpell() {
            return SpellcasterIllager.IllagerSpell.FANGS;
        }
    }

    private class EvokerSummonSpellGoal extends SpellcasterIllager.SpellcasterUseSpellGoal {

        private final TargetingConditions vexCountTargeting = TargetingConditions.forNonCombat().range(16.0D).ignoreLineOfSight().ignoreInvisibilityTesting();

        private EvokerSummonSpellGoal() {}

        @Override
        public boolean canUse() {
            if (!super.canUse()) {
                return false;
            } else {
                int i = getServerLevel(Evoker.this.level()).getNearbyEntities(Vex.class, this.vexCountTargeting, Evoker.this, Evoker.this.getBoundingBox().inflate(16.0D)).size();

                return Evoker.this.random.nextInt(8) + 1 > i;
            }
        }

        @Override
        protected int getCastingTime() {
            return 100;
        }

        @Override
        protected int getCastingInterval() {
            return 340;
        }

        @Override
        protected void performSpellCasting() {
            ServerLevel serverlevel = (ServerLevel) Evoker.this.level();
            PlayerTeam playerteam = Evoker.this.getTeam();

            for (int i = 0; i < 3; ++i) {
                BlockPos blockpos = Evoker.this.blockPosition().offset(-2 + Evoker.this.random.nextInt(5), 1, -2 + Evoker.this.random.nextInt(5));
                Vex vex = EntityType.VEX.create(Evoker.this.level(), EntitySpawnReason.MOB_SUMMONED);

                if (vex != null) {
                    vex.snapTo(blockpos, 0.0F, 0.0F);
                    vex.finalizeSpawn(serverlevel, serverlevel.getCurrentDifficultyAt(blockpos), EntitySpawnReason.MOB_SUMMONED, (SpawnGroupData) null);
                    vex.setOwner(Evoker.this);
                    vex.setBoundOrigin(blockpos);
                    vex.setLimitedLife(20 * (30 + Evoker.this.random.nextInt(90)));
                    if (playerteam != null) {
                        serverlevel.getScoreboard().addPlayerToTeam(vex.getScoreboardName(), playerteam);
                    }

                    serverlevel.addFreshEntityWithPassengers(vex);
                    serverlevel.gameEvent(GameEvent.ENTITY_PLACE, blockpos, GameEvent.Context.of((Entity) Evoker.this));
                }
            }

        }

        @Override
        protected SoundEvent getSpellPrepareSound() {
            return SoundEvents.EVOKER_PREPARE_SUMMON;
        }

        @Override
        protected SpellcasterIllager.IllagerSpell getSpell() {
            return SpellcasterIllager.IllagerSpell.SUMMON_VEX;
        }
    }

    public class EvokerWololoSpellGoal extends SpellcasterIllager.SpellcasterUseSpellGoal {

        private final TargetingConditions wololoTargeting = TargetingConditions.forNonCombat().range(16.0D).selector((livingentity, serverlevel) -> {
            return ((Sheep) livingentity).getColor() == DyeColor.BLUE;
        });

        public EvokerWololoSpellGoal() {}

        @Override
        public boolean canUse() {
            if (Evoker.this.getTarget() != null) {
                return false;
            } else if (Evoker.this.isCastingSpell()) {
                return false;
            } else if (Evoker.this.tickCount < this.nextAttackTickCount) {
                return false;
            } else {
                ServerLevel serverlevel = getServerLevel(Evoker.this.level());

                if (!(Boolean) serverlevel.getGameRules().get(GameRules.MOB_GRIEFING)) {
                    return false;
                } else {
                    List<Sheep> list = serverlevel.<Sheep>getNearbyEntities(Sheep.class, this.wololoTargeting, Evoker.this, Evoker.this.getBoundingBox().inflate(16.0D, 4.0D, 16.0D));

                    if (list.isEmpty()) {
                        return false;
                    } else {
                        Evoker.this.setWololoTarget((Sheep) list.get(Evoker.this.random.nextInt(list.size())));
                        return true;
                    }
                }
            }
        }

        @Override
        public boolean canContinueToUse() {
            return Evoker.this.getWololoTarget() != null && this.attackWarmupDelay > 0;
        }

        @Override
        public void stop() {
            super.stop();
            Evoker.this.setWololoTarget((Sheep) null);
        }

        @Override
        protected void performSpellCasting() {
            Sheep sheep = Evoker.this.getWololoTarget();

            if (sheep != null && sheep.isAlive()) {
                sheep.setColor(DyeColor.RED);
            }

        }

        @Override
        protected int getCastWarmupTime() {
            return 40;
        }

        @Override
        protected int getCastingTime() {
            return 60;
        }

        @Override
        protected int getCastingInterval() {
            return 140;
        }

        @Override
        protected SoundEvent getSpellPrepareSound() {
            return SoundEvents.EVOKER_PREPARE_WOLOLO;
        }

        @Override
        protected SpellcasterIllager.IllagerSpell getSpell() {
            return SpellcasterIllager.IllagerSpell.WOLOLO;
        }
    }
}
