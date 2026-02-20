package net.minecraft.world.level.material;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public abstract class Fluid {

    public static final IdMapper<FluidState> FLUID_STATE_REGISTRY = new IdMapper<FluidState>();
    protected final StateDefinition<Fluid, FluidState> stateDefinition;
    private FluidState defaultFluidState;
    private final Holder.Reference<Fluid> builtInRegistryHolder;

    protected Fluid() {
        this.builtInRegistryHolder = BuiltInRegistries.FLUID.createIntrusiveHolder(this);
        StateDefinition.Builder<Fluid, FluidState> statedefinition_builder = new StateDefinition.Builder<Fluid, FluidState>(this);

        this.createFluidStateDefinition(statedefinition_builder);
        this.stateDefinition = statedefinition_builder.create(Fluid::defaultFluidState, FluidState::new);
        this.registerDefaultState(this.stateDefinition.any());
    }

    protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {}

    public StateDefinition<Fluid, FluidState> getStateDefinition() {
        return this.stateDefinition;
    }

    protected final void registerDefaultState(FluidState state) {
        this.defaultFluidState = state;
    }

    public final FluidState defaultFluidState() {
        return this.defaultFluidState;
    }

    public abstract Item getBucket();

    protected void animateTick(Level level, BlockPos pos, FluidState fluidState, RandomSource random) {}

    protected void tick(ServerLevel level, BlockPos pos, BlockState blockState, FluidState fluidState) {}

    protected void randomTick(ServerLevel level, BlockPos pos, FluidState fluidState, RandomSource random) {}

    protected void entityInside(Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier) {}

    protected @Nullable ParticleOptions getDripParticle() {
        return null;
    }

    protected abstract boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid other, Direction direction);

    protected abstract Vec3 getFlow(BlockGetter level, BlockPos pos, FluidState fluidState);

    public abstract int getTickDelay(LevelReader level);

    protected boolean isRandomlyTicking() {
        return false;
    }

    protected boolean isEmpty() {
        return false;
    }

    protected abstract float getExplosionResistance();

    public abstract float getHeight(FluidState fluidState, BlockGetter level, BlockPos pos);

    public abstract float getOwnHeight(FluidState fluidState);

    protected abstract BlockState createLegacyBlock(FluidState fluidState);

    public abstract boolean isSource(FluidState fluidState);

    public abstract int getAmount(FluidState fluidState);

    public boolean isSame(Fluid other) {
        return other == this;
    }

    /** @deprecated */
    @Deprecated
    public boolean is(TagKey<Fluid> tag) {
        return this.builtInRegistryHolder.is(tag);
    }

    public abstract VoxelShape getShape(FluidState state, BlockGetter level, BlockPos pos);

    public @Nullable AABB getAABB(FluidState state, BlockGetter level, BlockPos pos) {
        if (this.isEmpty()) {
            return null;
        } else {
            float f = state.getHeight(level, pos);

            return new AABB((double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), (double) pos.getX() + 1.0D, (double) ((float) pos.getY() + f), (double) pos.getZ() + 1.0D);
        }
    }

    public Optional<SoundEvent> getPickupSound() {
        return Optional.empty();
    }

    /** @deprecated */
    @Deprecated
    public Holder.Reference<Fluid> builtInRegistryHolder() {
        return this.builtInRegistryHolder;
    }
}
