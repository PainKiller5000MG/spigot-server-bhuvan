package net.minecraft.world.level.material;

import com.google.common.collect.UnmodifiableIterator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public class Fluids {

    public static final Fluid EMPTY = register("empty", new EmptyFluid());
    public static final FlowingFluid FLOWING_WATER = (FlowingFluid) register("flowing_water", new WaterFluid.Flowing());
    public static final FlowingFluid WATER = (FlowingFluid) register("water", new WaterFluid.Source());
    public static final FlowingFluid FLOWING_LAVA = (FlowingFluid) register("flowing_lava", new LavaFluid.Flowing());
    public static final FlowingFluid LAVA = (FlowingFluid) register("lava", new LavaFluid.Source());

    public Fluids() {}

    private static <T extends Fluid> T register(String name, T fluid) {
        return (T) (Registry.register(BuiltInRegistries.FLUID, name, fluid));
    }

    static {
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            UnmodifiableIterator unmodifiableiterator = fluid.getStateDefinition().getPossibleStates().iterator();

            while (unmodifiableiterator.hasNext()) {
                FluidState fluidstate = (FluidState) unmodifiableiterator.next();

                Fluid.FLUID_STATE_REGISTRY.add(fluidstate);
            }
        }

    }
}
