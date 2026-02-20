package net.minecraft.world.attribute;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class EnvironmentAttributeProbe {

    private final Map<EnvironmentAttribute<?>, EnvironmentAttributeProbe.ValueProbe<?>> valueProbes = new Reference2ObjectOpenHashMap();
    private final Function<EnvironmentAttribute<?>, EnvironmentAttributeProbe.ValueProbe<?>> valueProbeFactory = (environmentattribute) -> {
        return new EnvironmentAttributeProbe.ValueProbe(environmentattribute);
    };
    private @Nullable Level level;
    private @Nullable Vec3 position;
    private final SpatialAttributeInterpolator biomeInterpolator = new SpatialAttributeInterpolator();

    public EnvironmentAttributeProbe() {}

    public void reset() {
        this.level = null;
        this.position = null;
        this.biomeInterpolator.clear();
        this.valueProbes.clear();
    }

    public void tick(Level level, Vec3 position) {
        this.level = level;
        this.position = position;
        this.valueProbes.values().removeIf(EnvironmentAttributeProbe.ValueProbe::tick);
        this.biomeInterpolator.clear();
        Vec3 vec31 = position.scale(0.25D);
        BiomeManager biomemanager = level.getBiomeManager();

        Objects.requireNonNull(biomemanager);
        GaussianSampler.sample(vec31, biomemanager::getNoiseBiomeAtQuart, (d0, holder) -> {
            this.biomeInterpolator.accumulate(d0, ((Biome) holder.value()).getAttributes());
        });
    }

    public <Value> Value getValue(EnvironmentAttribute<Value> attribute, float partialTicks) {
        EnvironmentAttributeProbe.ValueProbe<Value> environmentattributeprobe_valueprobe = (EnvironmentAttributeProbe.ValueProbe) this.valueProbes.computeIfAbsent(attribute, this.valueProbeFactory);

        return environmentattributeprobe_valueprobe.get(attribute, partialTicks);
    }

    private class ValueProbe<Value> {

        private Value lastValue;
        private @Nullable Value newValue;

        public ValueProbe(EnvironmentAttribute<Value> attribute) {
            Value value = (Value) this.getValueFromLevel(attribute);

            this.lastValue = value;
            this.newValue = value;
        }

        private Value getValueFromLevel(EnvironmentAttribute<Value> attribute) {
            return (Value) (EnvironmentAttributeProbe.this.level != null && EnvironmentAttributeProbe.this.position != null ? EnvironmentAttributeProbe.this.level.environmentAttributes().getValue(attribute, EnvironmentAttributeProbe.this.position, EnvironmentAttributeProbe.this.biomeInterpolator) : attribute.defaultValue());
        }

        public boolean tick() {
            if (this.newValue == null) {
                return true;
            } else {
                this.lastValue = this.newValue;
                this.newValue = null;
                return false;
            }
        }

        public Value get(EnvironmentAttribute<Value> attribute, float partialTicks) {
            if (this.newValue == null) {
                this.newValue = (Value) this.getValueFromLevel(attribute);
            }

            return attribute.type().partialTickLerp().apply(partialTicks, this.lastValue, this.newValue);
        }
    }
}
