package net.minecraft.world.level.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TriState;
import net.minecraft.world.Difficulty;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public class EyeblossomBlock extends FlowerBlock {

    public static final MapCodec<EyeblossomBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.BOOL.fieldOf("open").forGetter((eyeblossomblock) -> {
            return eyeblossomblock.type.open;
        }), propertiesCodec()).apply(instance, EyeblossomBlock::new);
    });
    private static final int EYEBLOSSOM_XZ_RANGE = 3;
    private static final int EYEBLOSSOM_Y_RANGE = 2;
    private final EyeblossomBlock.Type type;

    @Override
    public MapCodec<? extends EyeblossomBlock> codec() {
        return EyeblossomBlock.CODEC;
    }

    public EyeblossomBlock(EyeblossomBlock.Type type, BlockBehaviour.Properties properties) {
        super(type.effect, type.effectDuration, properties);
        this.type = type;
    }

    public EyeblossomBlock(boolean open, BlockBehaviour.Properties properties) {
        super(EyeblossomBlock.Type.fromBoolean(open).effect, EyeblossomBlock.Type.fromBoolean(open).effectDuration, properties);
        this.type = EyeblossomBlock.Type.fromBoolean(open);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (this.type.emitSounds() && random.nextInt(700) == 0) {
            BlockState blockstate1 = level.getBlockState(pos.below());

            if (blockstate1.is(Blocks.PALE_MOSS_BLOCK)) {
                level.playLocalSound((double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), SoundEvents.EYEBLOSSOM_IDLE, SoundSource.AMBIENT, 1.0F, 1.0F, false);
            }
        }

    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (this.tryChangingState(state, level, pos, random)) {
            level.playSound((Entity) null, pos, this.type.transform().longSwitchSound, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        super.randomTick(state, level, pos, random);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (this.tryChangingState(state, level, pos, random)) {
            level.playSound((Entity) null, pos, this.type.transform().shortSwitchSound, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        super.tick(state, level, pos, random);
    }

    private boolean tryChangingState(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean flag = ((TriState) level.environmentAttributes().getValue(EnvironmentAttributes.EYEBLOSSOM_OPEN, pos)).toBoolean(this.type.open);

        if (flag == this.type.open) {
            return false;
        } else {
            EyeblossomBlock.Type eyeblossomblock_type = this.type.transform();

            level.setBlock(pos, eyeblossomblock_type.state(), 3);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(state));
            eyeblossomblock_type.spawnTransformParticle(level, pos, random);
            BlockPos.betweenClosed(pos.offset(-3, -2, -3), pos.offset(3, 2, 3)).forEach((blockpos1) -> {
                BlockState blockstate1 = level.getBlockState(blockpos1);

                if (blockstate1 == state) {
                    double d0 = Math.sqrt(pos.distSqr(blockpos1));
                    int i = random.nextIntBetweenInclusive((int) (d0 * 5.0D), (int) (d0 * 10.0D));

                    level.scheduleTick(blockpos1, state.getBlock(), i);
                }

            });
            return true;
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (!level.isClientSide() && level.getDifficulty() != Difficulty.PEACEFUL && entity instanceof Bee bee) {
            if (Bee.attractsBees(state) && !bee.hasEffect(MobEffects.POISON)) {
                bee.addEffect(this.getBeeInteractionEffect());
            }
        }

    }

    @Override
    public MobEffectInstance getBeeInteractionEffect() {
        return new MobEffectInstance(MobEffects.POISON, 25);
    }

    public static enum Type {

        OPEN(true, MobEffects.BLINDNESS, 11.0F, SoundEvents.EYEBLOSSOM_OPEN_LONG, SoundEvents.EYEBLOSSOM_OPEN, 16545810), CLOSED(false, MobEffects.NAUSEA, 7.0F, SoundEvents.EYEBLOSSOM_CLOSE_LONG, SoundEvents.EYEBLOSSOM_CLOSE, 6250335);

        private final boolean open;
        private final Holder<MobEffect> effect;
        private final float effectDuration;
        private final SoundEvent longSwitchSound;
        private final SoundEvent shortSwitchSound;
        private final int particleColor;

        private Type(boolean open, Holder<MobEffect> effect, float duration, SoundEvent longSwitchSound, SoundEvent shortSwitchSound, int particleColor) {
            this.open = open;
            this.effect = effect;
            this.effectDuration = duration;
            this.longSwitchSound = longSwitchSound;
            this.shortSwitchSound = shortSwitchSound;
            this.particleColor = particleColor;
        }

        public Block block() {
            return this.open ? Blocks.OPEN_EYEBLOSSOM : Blocks.CLOSED_EYEBLOSSOM;
        }

        public BlockState state() {
            return this.block().defaultBlockState();
        }

        public EyeblossomBlock.Type transform() {
            return fromBoolean(!this.open);
        }

        public boolean emitSounds() {
            return this.open;
        }

        public static EyeblossomBlock.Type fromBoolean(boolean open) {
            return open ? EyeblossomBlock.Type.OPEN : EyeblossomBlock.Type.CLOSED;
        }

        public void spawnTransformParticle(ServerLevel level, BlockPos pos, RandomSource random) {
            Vec3 vec3 = pos.getCenter();
            double d0 = 0.5D + random.nextDouble();
            Vec3 vec31 = new Vec3(random.nextDouble() - 0.5D, random.nextDouble() + 1.0D, random.nextDouble() - 0.5D);
            Vec3 vec32 = vec3.add(vec31.scale(d0));
            TrailParticleOption trailparticleoption = new TrailParticleOption(vec32, this.particleColor, (int) (20.0D * d0));

            level.sendParticles(trailparticleoption, vec3.x, vec3.y, vec3.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        public SoundEvent longSwitchSound() {
            return this.longSwitchSound;
        }
    }
}
