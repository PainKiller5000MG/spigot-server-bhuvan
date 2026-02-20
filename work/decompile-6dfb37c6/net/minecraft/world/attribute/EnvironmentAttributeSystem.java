package net.minecraft.world.attribute;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.timeline.Timeline;
import org.jspecify.annotations.Nullable;

public class EnvironmentAttributeSystem implements EnvironmentAttributeReader {

    private final Map<EnvironmentAttribute<?>, EnvironmentAttributeSystem.ValueSampler<?>> attributeSamplers = new Reference2ObjectOpenHashMap();

    private EnvironmentAttributeSystem(Map<EnvironmentAttribute<?>, List<EnvironmentAttributeLayer<?>>> layersByAttribute) {
        layersByAttribute.forEach((environmentattribute, list) -> {
            this.attributeSamplers.put(environmentattribute, this.bakeLayerSampler(environmentattribute, list));
        });
    }

    private <Value> EnvironmentAttributeSystem.ValueSampler<Value> bakeLayerSampler(EnvironmentAttribute<Value> attribute, List<? extends EnvironmentAttributeLayer<?>> untypedLayers) {
        List<EnvironmentAttributeLayer<Value>> list1 = new ArrayList(untypedLayers);
        Value value = attribute.defaultValue();

        while (!((List) list1).isEmpty()) {
            Object object = list1.getFirst();

            if (!(object instanceof EnvironmentAttributeLayer.Constant)) {
                break;
            }

            EnvironmentAttributeLayer.Constant<Value> environmentattributelayer_constant = (EnvironmentAttributeLayer.Constant) object;

            value = environmentattributelayer_constant.applyConstant(value);
            list1.removeFirst();
        }

        boolean flag = list1.stream().anyMatch((environmentattributelayer) -> {
            return environmentattributelayer instanceof EnvironmentAttributeLayer.Positional;
        });

        return new EnvironmentAttributeSystem.ValueSampler<Value>(attribute, value, List.copyOf(list1), flag);
    }

    public static EnvironmentAttributeSystem.Builder builder() {
        return new EnvironmentAttributeSystem.Builder();
    }

    private static void addDefaultLayers(EnvironmentAttributeSystem.Builder builder, Level level) {
        RegistryAccess registryaccess = level.registryAccess();
        BiomeManager biomemanager = level.getBiomeManager();

        Objects.requireNonNull(level);
        LongSupplier longsupplier = level::getDayTime;

        addDimensionLayer(builder, level.dimensionType());
        addBiomeLayer(builder, registryaccess.lookupOrThrow(Registries.BIOME), biomemanager);
        level.dimensionType().timelines().forEach((holder) -> {
            builder.addTimelineLayer(holder, longsupplier);
        });
        if (level.canHaveWeather()) {
            WeatherAttributes.addBuiltinLayers(builder, WeatherAttributes.WeatherAccess.from(level));
        }

    }

    private static void addDimensionLayer(EnvironmentAttributeSystem.Builder builder, DimensionType dimensionType) {
        builder.addConstantLayer(dimensionType.attributes());
    }

    private static void addBiomeLayer(EnvironmentAttributeSystem.Builder builder, HolderLookup<Biome> biomes, BiomeManager biomeManager) {
        Stream<EnvironmentAttribute<?>> stream = biomes.listElements().flatMap((holder_reference) -> {
            return ((Biome) holder_reference.value()).getAttributes().keySet().stream();
        }).distinct();

        stream.forEach((environmentattribute) -> {
            addBiomeLayerForAttribute(builder, environmentattribute, biomeManager);
        });
    }

    private static <Value> void addBiomeLayerForAttribute(EnvironmentAttributeSystem.Builder builder, EnvironmentAttribute<Value> attribute, BiomeManager biomeManager) {
        builder.addPositionalLayer(attribute, (object, vec3, spatialattributeinterpolator) -> {
            if (spatialattributeinterpolator != null && attribute.isSpatiallyInterpolated()) {
                return spatialattributeinterpolator.applyAttributeLayer(attribute, object);
            } else {
                Holder<Biome> holder = biomeManager.getNoiseBiomeAtPosition(vec3.x, vec3.y, vec3.z);

                return ((Biome) holder.value()).getAttributes().applyModifier(attribute, object);
            }
        });
    }

    public void invalidateTickCache() {
        this.attributeSamplers.values().forEach(EnvironmentAttributeSystem.ValueSampler::invalidateTickCache);
    }

    private <Value> EnvironmentAttributeSystem.@Nullable ValueSampler<Value> getValueSampler(EnvironmentAttribute<Value> attribute) {
        return (EnvironmentAttributeSystem.ValueSampler) this.attributeSamplers.get(attribute);
    }

    @Override
    public <Value> Value getDimensionValue(EnvironmentAttribute<Value> attribute) {
        if (SharedConstants.IS_RUNNING_IN_IDE && attribute.isPositional()) {
            throw new IllegalStateException("Position must always be provided for positional attribute " + String.valueOf(attribute));
        } else {
            EnvironmentAttributeSystem.ValueSampler<Value> environmentattributesystem_valuesampler = this.<Value>getValueSampler(attribute);

            return (Value) (environmentattributesystem_valuesampler == null ? attribute.defaultValue() : environmentattributesystem_valuesampler.getDimensionValue());
        }
    }

    @Override
    public <Value> Value getValue(EnvironmentAttribute<Value> attribute, Vec3 pos, @Nullable SpatialAttributeInterpolator biomeInterpolator) {
        EnvironmentAttributeSystem.ValueSampler<Value> environmentattributesystem_valuesampler = this.<Value>getValueSampler(attribute);

        return (Value) (environmentattributesystem_valuesampler == null ? attribute.defaultValue() : environmentattributesystem_valuesampler.getValue(pos, biomeInterpolator));
    }

    @VisibleForTesting
    <Value> Value getConstantBaseValue(EnvironmentAttribute<Value> attribute) {
        EnvironmentAttributeSystem.ValueSampler<Value> environmentattributesystem_valuesampler = this.<Value>getValueSampler(attribute);

        return (Value) (environmentattributesystem_valuesampler != null ? environmentattributesystem_valuesampler.baseValue : attribute.defaultValue());
    }

    @VisibleForTesting
    boolean isAffectedByPosition(EnvironmentAttribute<?> attribute) {
        EnvironmentAttributeSystem.ValueSampler<?> environmentattributesystem_valuesampler = this.getValueSampler(attribute);

        return environmentattributesystem_valuesampler != null && environmentattributesystem_valuesampler.isAffectedByPosition;
    }

    public static class Builder {

        private final Map<EnvironmentAttribute<?>, List<EnvironmentAttributeLayer<?>>> layersByAttribute = new HashMap();

        private Builder() {}

        public EnvironmentAttributeSystem.Builder addDefaultLayers(Level level) {
            EnvironmentAttributeSystem.addDefaultLayers(this, level);
            return this;
        }

        public EnvironmentAttributeSystem.Builder addConstantLayer(EnvironmentAttributeMap attributeMap) {
            for (EnvironmentAttribute<?> environmentattribute : attributeMap.keySet()) {
                this.addConstantEntry(environmentattribute, attributeMap);
            }

            return this;
        }

        private <Value> EnvironmentAttributeSystem.Builder addConstantEntry(EnvironmentAttribute<Value> attribute, EnvironmentAttributeMap attributeMap) {
            EnvironmentAttributeMap.Entry<Value, ?> environmentattributemap_entry = attributeMap.get(attribute);

            if (environmentattributemap_entry == null) {
                throw new IllegalArgumentException("Missing attribute " + String.valueOf(attribute));
            } else {
                Objects.requireNonNull(environmentattributemap_entry);
                return this.addConstantLayer(attribute, environmentattributemap_entry::applyModifier);
            }
        }

        public <Value> EnvironmentAttributeSystem.Builder addConstantLayer(EnvironmentAttribute<Value> attribute, EnvironmentAttributeLayer.Constant<Value> layer) {
            return this.addLayer(attribute, layer);
        }

        public <Value> EnvironmentAttributeSystem.Builder addTimeBasedLayer(EnvironmentAttribute<Value> attribute, EnvironmentAttributeLayer.TimeBased<Value> layer) {
            return this.addLayer(attribute, layer);
        }

        public <Value> EnvironmentAttributeSystem.Builder addPositionalLayer(EnvironmentAttribute<Value> attribute, EnvironmentAttributeLayer.Positional<Value> layer) {
            return this.addLayer(attribute, layer);
        }

        private <Value> EnvironmentAttributeSystem.Builder addLayer(EnvironmentAttribute<Value> attribute, EnvironmentAttributeLayer<Value> layer) {
            ((List) this.layersByAttribute.computeIfAbsent(attribute, (environmentattribute1) -> {
                return new ArrayList();
            })).add(layer);
            return this;
        }

        public EnvironmentAttributeSystem.Builder addTimelineLayer(Holder<Timeline> timeline, LongSupplier dayTimeGetter) {
            for (EnvironmentAttribute<?> environmentattribute : (timeline.value()).attributes()) {
                this.addTimelineLayerForAttribute(timeline, environmentattribute, dayTimeGetter);
            }

            return this;
        }

        private <Value> void addTimelineLayerForAttribute(Holder<Timeline> timeline, EnvironmentAttribute<Value> attribute, LongSupplier dayTimeGetter) {
            this.addTimeBasedLayer(attribute, (timeline.value()).createTrackSampler(attribute, dayTimeGetter));
        }

        public EnvironmentAttributeSystem build() {
            return new EnvironmentAttributeSystem(this.layersByAttribute);
        }
    }

    private static class ValueSampler<Value> {

        private final EnvironmentAttribute<Value> attribute;
        private final Value baseValue;
        private final List<EnvironmentAttributeLayer<Value>> layers;
        private final boolean isAffectedByPosition;
        private @Nullable Value cachedTickValue;
        private int cacheTickId;

        private ValueSampler(EnvironmentAttribute<Value> attribute, Value baseValue, List<EnvironmentAttributeLayer<Value>> layers, boolean isAffectedByPosition) {
            this.attribute = attribute;
            this.baseValue = baseValue;
            this.layers = layers;
            this.isAffectedByPosition = isAffectedByPosition;
        }

        public void invalidateTickCache() {
            this.cachedTickValue = null;
            ++this.cacheTickId;
        }

        public Value getDimensionValue() {
            if (this.cachedTickValue != null) {
                return this.cachedTickValue;
            } else {
                Value value = (Value) this.computeValueNotPositional();

                this.cachedTickValue = value;
                return value;
            }
        }

        public Value getValue(Vec3 pos, @Nullable SpatialAttributeInterpolator biomeInterpolator) {
            return (Value) (!this.isAffectedByPosition ? this.getDimensionValue() : this.computeValuePositional(pos, biomeInterpolator));
        }

        private Value computeValuePositional(Vec3 pos, @Nullable SpatialAttributeInterpolator biomeInterpolator) {
            Value value = this.baseValue;

            for(EnvironmentAttributeLayer<Value> environmentattributelayer : this.layers) {
                Objects.requireNonNull(environmentattributelayer);
                byte b0 = 0;
                Object object;

                //$FF: b0->value
                //0->net/minecraft/world/attribute/EnvironmentAttributeLayer$Constant
                //1->net/minecraft/world/attribute/EnvironmentAttributeLayer$TimeBased
                //2->net/minecraft/world/attribute/EnvironmentAttributeLayer$Positional
                switch (environmentattributelayer.typeSwitch<invokedynamic>(environmentattributelayer, b0)) {
                    case 0:
                        EnvironmentAttributeLayer.Constant<Value> environmentattributelayer_constant = (EnvironmentAttributeLayer.Constant)environmentattributelayer;

                        object = environmentattributelayer_constant.applyConstant(value);
                        break;
                    case 1:
                        EnvironmentAttributeLayer.TimeBased<Value> environmentattributelayer_timebased = (EnvironmentAttributeLayer.TimeBased)environmentattributelayer;

                        object = environmentattributelayer_timebased.applyTimeBased(value, this.cacheTickId);
                        break;
                    case 2:
                        EnvironmentAttributeLayer.Positional<Value> environmentattributelayer_positional = (EnvironmentAttributeLayer.Positional)environmentattributelayer;

                        object = environmentattributelayer_positional.applyPositional(value, (Vec3)Objects.requireNonNull(pos), biomeInterpolator);
                        break;
                    default:
                        throw new MatchException((String)null, (Throwable)null);
                }

                value = (Value)object;
            }

            return this.attribute.sanitizeValue(value);
        }

        private Value computeValueNotPositional() {
            Value value = this.baseValue;

            for(EnvironmentAttributeLayer<Value> environmentattributelayer : this.layers) {
                Objects.requireNonNull(environmentattributelayer);
                byte b0 = 0;
                Object object;

                //$FF: b0->value
                //0->net/minecraft/world/attribute/EnvironmentAttributeLayer$Constant
                //1->net/minecraft/world/attribute/EnvironmentAttributeLayer$TimeBased
                //2->net/minecraft/world/attribute/EnvironmentAttributeLayer$Positional
                switch (environmentattributelayer.typeSwitch<invokedynamic>(environmentattributelayer, b0)) {
                    case 0:
                        EnvironmentAttributeLayer.Constant<Value> environmentattributelayer_constant = (EnvironmentAttributeLayer.Constant)environmentattributelayer;

                        object = environmentattributelayer_constant.applyConstant(value);
                        break;
                    case 1:
                        EnvironmentAttributeLayer.TimeBased<Value> environmentattributelayer_timebased = (EnvironmentAttributeLayer.TimeBased)environmentattributelayer;

                        object = environmentattributelayer_timebased.applyTimeBased(value, this.cacheTickId);
                        break;
                    case 2:
                        EnvironmentAttributeLayer.Positional<Value> environmentattributelayer_positional = (EnvironmentAttributeLayer.Positional)environmentattributelayer;

                        object = value;
                        break;
                    default:
                        throw new MatchException((String)null, (Throwable)null);
                }

                value = (Value)object;
            }

            return this.attribute.sanitizeValue(value);
        }
    }
}
