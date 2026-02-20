package net.minecraft.world.entity.monster.illager;

import java.util.EnumSet;
import java.util.function.IntFunction;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public abstract class SpellcasterIllager extends AbstractIllager {

    private static final EntityDataAccessor<Byte> DATA_SPELL_CASTING_ID = SynchedEntityData.<Byte>defineId(SpellcasterIllager.class, EntityDataSerializers.BYTE);
    private static final int DEFAULT_SPELLCASTING_TICKS = 0;
    protected int spellCastingTickCount = 0;
    private SpellcasterIllager.IllagerSpell currentSpell;

    protected SpellcasterIllager(EntityType<? extends SpellcasterIllager> type, Level level) {
        super(type, level);
        this.currentSpell = SpellcasterIllager.IllagerSpell.NONE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(SpellcasterIllager.DATA_SPELL_CASTING_ID, (byte) 0);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.spellCastingTickCount = input.getIntOr("SpellTicks", 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("SpellTicks", this.spellCastingTickCount);
    }

    @Override
    public AbstractIllager.IllagerArmPose getArmPose() {
        return this.isCastingSpell() ? AbstractIllager.IllagerArmPose.SPELLCASTING : (this.isCelebrating() ? AbstractIllager.IllagerArmPose.CELEBRATING : AbstractIllager.IllagerArmPose.CROSSED);
    }

    public boolean isCastingSpell() {
        return this.level().isClientSide() ? (Byte) this.entityData.get(SpellcasterIllager.DATA_SPELL_CASTING_ID) > 0 : this.spellCastingTickCount > 0;
    }

    public void setIsCastingSpell(SpellcasterIllager.IllagerSpell spell) {
        this.currentSpell = spell;
        this.entityData.set(SpellcasterIllager.DATA_SPELL_CASTING_ID, (byte) spell.id);
    }

    public SpellcasterIllager.IllagerSpell getCurrentSpell() {
        return !this.level().isClientSide() ? this.currentSpell : SpellcasterIllager.IllagerSpell.byId((Byte) this.entityData.get(SpellcasterIllager.DATA_SPELL_CASTING_ID));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (this.spellCastingTickCount > 0) {
            --this.spellCastingTickCount;
        }

    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide() && this.isCastingSpell()) {
            SpellcasterIllager.IllagerSpell spellcasterillager_illagerspell = this.getCurrentSpell();
            float f = (float) spellcasterillager_illagerspell.spellColor[0];
            float f1 = (float) spellcasterillager_illagerspell.spellColor[1];
            float f2 = (float) spellcasterillager_illagerspell.spellColor[2];
            float f3 = this.yBodyRot * ((float) Math.PI / 180F) + Mth.cos((double) ((float) this.tickCount * 0.6662F)) * 0.25F;
            float f4 = Mth.cos((double) f3);
            float f5 = Mth.sin((double) f3);
            double d0 = 0.6D * (double) this.getScale();
            double d1 = 1.8D * (double) this.getScale();

            this.level().addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, f, f1, f2), this.getX() + (double) f4 * d0, this.getY() + d1, this.getZ() + (double) f5 * d0, 0.0D, 0.0D, 0.0D);
            this.level().addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, f, f1, f2), this.getX() - (double) f4 * d0, this.getY() + d1, this.getZ() - (double) f5 * d0, 0.0D, 0.0D, 0.0D);
        }

    }

    protected int getSpellCastingTime() {
        return this.spellCastingTickCount;
    }

    protected abstract SoundEvent getCastingSoundEvent();

    protected class SpellcasterCastingSpellGoal extends Goal {

        public SpellcasterCastingSpellGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return SpellcasterIllager.this.getSpellCastingTime() > 0;
        }

        @Override
        public void start() {
            super.start();
            SpellcasterIllager.this.navigation.stop();
        }

        @Override
        public void stop() {
            super.stop();
            SpellcasterIllager.this.setIsCastingSpell(SpellcasterIllager.IllagerSpell.NONE);
        }

        @Override
        public void tick() {
            if (SpellcasterIllager.this.getTarget() != null) {
                SpellcasterIllager.this.getLookControl().setLookAt(SpellcasterIllager.this.getTarget(), (float) SpellcasterIllager.this.getMaxHeadYRot(), (float) SpellcasterIllager.this.getMaxHeadXRot());
            }

        }
    }

    protected abstract class SpellcasterUseSpellGoal extends Goal {

        protected int attackWarmupDelay;
        protected int nextAttackTickCount;

        protected SpellcasterUseSpellGoal() {}

        @Override
        public boolean canUse() {
            LivingEntity livingentity = SpellcasterIllager.this.getTarget();

            return livingentity != null && livingentity.isAlive() ? (SpellcasterIllager.this.isCastingSpell() ? false : SpellcasterIllager.this.tickCount >= this.nextAttackTickCount) : false;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity livingentity = SpellcasterIllager.this.getTarget();

            return livingentity != null && livingentity.isAlive() && this.attackWarmupDelay > 0;
        }

        @Override
        public void start() {
            this.attackWarmupDelay = this.adjustedTickDelay(this.getCastWarmupTime());
            SpellcasterIllager.this.spellCastingTickCount = this.getCastingTime();
            this.nextAttackTickCount = SpellcasterIllager.this.tickCount + this.getCastingInterval();
            SoundEvent soundevent = this.getSpellPrepareSound();

            if (soundevent != null) {
                SpellcasterIllager.this.playSound(soundevent, 1.0F, 1.0F);
            }

            SpellcasterIllager.this.setIsCastingSpell(this.getSpell());
        }

        @Override
        public void tick() {
            --this.attackWarmupDelay;
            if (this.attackWarmupDelay == 0) {
                this.performSpellCasting();
                SpellcasterIllager.this.playSound(SpellcasterIllager.this.getCastingSoundEvent(), 1.0F, 1.0F);
            }

        }

        protected abstract void performSpellCasting();

        protected int getCastWarmupTime() {
            return 20;
        }

        protected abstract int getCastingTime();

        protected abstract int getCastingInterval();

        protected abstract @Nullable SoundEvent getSpellPrepareSound();

        protected abstract SpellcasterIllager.IllagerSpell getSpell();
    }

    public static enum IllagerSpell {

        NONE(0, 0.0D, 0.0D, 0.0D), SUMMON_VEX(1, 0.7D, 0.7D, 0.8D), FANGS(2, 0.4D, 0.3D, 0.35D), WOLOLO(3, 0.7D, 0.5D, 0.2D), DISAPPEAR(4, 0.3D, 0.3D, 0.8D), BLINDNESS(5, 0.1D, 0.1D, 0.2D);

        private static final IntFunction<SpellcasterIllager.IllagerSpell> BY_ID = ByIdMap.<SpellcasterIllager.IllagerSpell>continuous((spellcasterillager_illagerspell) -> {
            return spellcasterillager_illagerspell.id;
        }, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        private final int id;
        private final double[] spellColor;

        private IllagerSpell(int id, double red, double green, double blue) {
            this.id = id;
            this.spellColor = new double[]{red, green, blue};
        }

        public static SpellcasterIllager.IllagerSpell byId(int id) {
            return (SpellcasterIllager.IllagerSpell) SpellcasterIllager.IllagerSpell.BY_ID.apply(id);
        }
    }
}
