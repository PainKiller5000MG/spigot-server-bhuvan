package net.minecraft.world.level.material;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.InsideBlockEffectType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

public abstract class LavaFluid extends FlowingFluid {

    public static final float MIN_LEVEL_CUTOFF = 0.44444445F;

    public LavaFluid() {}

    @Override
    public Fluid getFlowing() {
        return Fluids.FLOWING_LAVA;
    }

    @Override
    public Fluid getSource() {
        return Fluids.LAVA;
    }

    @Override
    public Item getBucket() {
        return Items.LAVA_BUCKET;
    }

    @Override
    public void animateTick(Level level, BlockPos pos, FluidState fluidState, RandomSource random) {
        BlockPos blockpos1 = pos.above();

        if (level.getBlockState(blockpos1).isAir() && !level.getBlockState(blockpos1).isSolidRender()) {
            if (random.nextInt(100) == 0) {
                double d0 = (double) pos.getX() + random.nextDouble();
                double d1 = (double) pos.getY() + 1.0D;
                double d2 = (double) pos.getZ() + random.nextDouble();

                level.addParticle(ParticleTypes.LAVA, d0, d1, d2, 0.0D, 0.0D, 0.0D);
                level.playLocalSound(d0, d1, d2, SoundEvents.LAVA_POP, SoundSource.AMBIENT, 0.2F + random.nextFloat() * 0.2F, 0.9F + random.nextFloat() * 0.15F, false);
            }

            if (random.nextInt(200) == 0) {
                level.playLocalSound((double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), SoundEvents.LAVA_AMBIENT, SoundSource.AMBIENT, 0.2F + random.nextFloat() * 0.2F, 0.9F + random.nextFloat() * 0.15F, false);
            }
        }

    }

    @Override
    public void randomTick(ServerLevel level, BlockPos pos, FluidState fluidState, RandomSource random) {
        if (level.canSpreadFireAround(pos)) {
            int i = random.nextInt(3);

            if (i > 0) {
                BlockPos blockpos1 = pos;

                for (int j = 0; j < i; ++j) {
                    blockpos1 = blockpos1.offset(random.nextInt(3) - 1, 1, random.nextInt(3) - 1);
                    if (!level.isLoaded(blockpos1)) {
                        return;
                    }

                    BlockState blockstate = level.getBlockState(blockpos1);

                    if (blockstate.isAir()) {
                        if (this.hasFlammableNeighbours(level, blockpos1)) {
                            level.setBlockAndUpdate(blockpos1, BaseFireBlock.getState(level, blockpos1));
                            return;
                        }
                    } else if (blockstate.blocksMotion()) {
                        return;
                    }
                }
            } else {
                for (int k = 0; k < 3; ++k) {
                    BlockPos blockpos2 = pos.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);

                    if (!level.isLoaded(blockpos2)) {
                        return;
                    }

                    if (level.isEmptyBlock(blockpos2.above()) && this.isFlammable(level, blockpos2)) {
                        level.setBlockAndUpdate(blockpos2.above(), BaseFireBlock.getState(level, blockpos2));
                    }
                }
            }

        }
    }

    @Override
    protected void entityInside(Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier) {
        effectApplier.apply(InsideBlockEffectType.CLEAR_FREEZE);
        effectApplier.apply(InsideBlockEffectType.LAVA_IGNITE);
        effectApplier.runAfter(InsideBlockEffectType.LAVA_IGNITE, Entity::lavaHurt);
    }

    private boolean hasFlammableNeighbours(LevelReader level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (this.isFlammable(level, pos.relative(direction))) {
                return true;
            }
        }

        return false;
    }

    private boolean isFlammable(LevelReader level, BlockPos pos) {
        return level.isInsideBuildHeight(pos.getY()) && !level.hasChunkAt(pos) ? false : level.getBlockState(pos).ignitedByLava();
    }

    @Override
    public @Nullable ParticleOptions getDripParticle() {
        return ParticleTypes.DRIPPING_LAVA;
    }

    @Override
    protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) {
        this.fizz(level, pos);
    }

    @Override
    public int getSlopeFindDistance(LevelReader level) {
        return isFastLava(level) ? 4 : 2;
    }

    @Override
    public BlockState createLegacyBlock(FluidState fluidState) {
        return (BlockState) Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(fluidState));
    }

    @Override
    public boolean isSame(Fluid other) {
        return other == Fluids.LAVA || other == Fluids.FLOWING_LAVA;
    }

    @Override
    public int getDropOff(LevelReader level) {
        return isFastLava(level) ? 1 : 2;
    }

    @Override
    public boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid other, Direction direction) {
        return state.getHeight(level, pos) >= 0.44444445F && other.is(FluidTags.WATER);
    }

    @Override
    public int getTickDelay(LevelReader level) {
        return isFastLava(level) ? 10 : 30;
    }

    @Override
    public int getSpreadDelay(Level level, BlockPos pos, FluidState oldFluidState, FluidState newFluidState) {
        int i = this.getTickDelay(level);

        if (!oldFluidState.isEmpty() && !newFluidState.isEmpty() && !(Boolean) oldFluidState.getValue(LavaFluid.FALLING) && !(Boolean) newFluidState.getValue(LavaFluid.FALLING) && newFluidState.getHeight(level, pos) > oldFluidState.getHeight(level, pos) && level.getRandom().nextInt(4) != 0) {
            i *= 4;
        }

        return i;
    }

    private void fizz(LevelAccessor level, BlockPos pos) {
        level.levelEvent(1501, pos, 0);
    }

    @Override
    protected boolean canConvertToSource(ServerLevel level) {
        return (Boolean) level.getGameRules().get(GameRules.LAVA_SOURCE_CONVERSION);
    }

    @Override
    protected void spreadTo(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState target) {
        if (direction == Direction.DOWN) {
            FluidState fluidstate1 = level.getFluidState(pos);

            if (this.is(FluidTags.LAVA) && fluidstate1.is(FluidTags.WATER)) {
                if (state.getBlock() instanceof LiquidBlock) {
                    level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
                }

                this.fizz(level, pos);
                return;
            }
        }

        super.spreadTo(level, pos, state, direction, target);
    }

    @Override
    protected boolean isRandomlyTicking() {
        return true;
    }

    @Override
    protected float getExplosionResistance() {
        return 100.0F;
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Optional.of(SoundEvents.BUCKET_FILL_LAVA);
    }

    private static boolean isFastLava(LevelReader level) {
        return (Boolean) level.environmentAttributes().getDimensionValue(EnvironmentAttributes.FAST_LAVA);
    }

    public static class Source extends LavaFluid {

        public Source() {}

        @Override
        public int getAmount(FluidState fluidState) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState fluidState) {
            return true;
        }
    }

    public static class Flowing extends LavaFluid {

        public Flowing() {}

        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LavaFluid.Flowing.LEVEL);
        }

        @Override
        public int getAmount(FluidState fluidState) {
            return (Integer) fluidState.getValue(LavaFluid.Flowing.LEVEL);
        }

        @Override
        public boolean isSource(FluidState fluidState) {
            return false;
        }
    }
}
