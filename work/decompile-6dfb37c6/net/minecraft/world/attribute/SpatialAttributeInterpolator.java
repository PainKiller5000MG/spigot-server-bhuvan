package net.minecraft.world.attribute;

import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2DoubleMap;
import it.unimi.dsi.fastutil.objects.Reference2DoubleMap.Entry;
import it.unimi.dsi.fastutil.objects.Reference2DoubleMaps;
import java.util.Objects;

public class SpatialAttributeInterpolator {

    private final Reference2DoubleArrayMap<EnvironmentAttributeMap> weightsBySource = new Reference2DoubleArrayMap();

    public SpatialAttributeInterpolator() {}

    public void clear() {
        this.weightsBySource.clear();
    }

    public SpatialAttributeInterpolator accumulate(double weight, EnvironmentAttributeMap attributes) {
        this.weightsBySource.mergeDouble(attributes, weight, Double::sum);
        return this;
    }

    public <Value> Value applyAttributeLayer(EnvironmentAttribute<Value> attribute, Value baseValue) {
        if (this.weightsBySource.isEmpty()) {
            return baseValue;
        } else if (this.weightsBySource.size() == 1) {
            EnvironmentAttributeMap environmentattributemap = (EnvironmentAttributeMap) this.weightsBySource.keySet().iterator().next();

            return (Value) environmentattributemap.applyModifier(attribute, baseValue);
        } else {
            LerpFunction<Value> lerpfunction = attribute.type().spatialLerp();
            Value value1 = null;
            double d0 = 0.0D;
            ObjectIterator objectiterator = Reference2DoubleMaps.fastIterable(this.weightsBySource).iterator();

            while (objectiterator.hasNext()) {
                Reference2DoubleMap.Entry<EnvironmentAttributeMap> reference2doublemap_entry = (Entry) objectiterator.next();
                EnvironmentAttributeMap environmentattributemap1 = (EnvironmentAttributeMap) reference2doublemap_entry.getKey();
                double d1 = reference2doublemap_entry.getDoubleValue();
                Value value2 = (Value) environmentattributemap1.applyModifier(attribute, baseValue);

                d0 += d1;
                if (value1 == null) {
                    value1 = value2;
                } else {
                    float f = (float) (d1 / d0);

                    value1 = lerpfunction.apply(f, value1, value2);
                }
            }

            return (Value) Objects.requireNonNull(value1);
        }
    }
}
