package net.minecraft.world.entity.ai.attributes;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public class AttributeInstance {

    private final Holder<Attribute> attribute;
    private final Map<AttributeModifier.Operation, Map<Identifier, AttributeModifier>> modifiersByOperation = Maps.newEnumMap(AttributeModifier.Operation.class);
    private final Map<Identifier, AttributeModifier> modifierById = new Object2ObjectArrayMap();
    private final Map<Identifier, AttributeModifier> permanentModifiers = new Object2ObjectArrayMap();
    private double baseValue;
    private boolean dirty = true;
    private double cachedValue;
    private final Consumer<AttributeInstance> onDirty;

    public AttributeInstance(Holder<Attribute> attribute, Consumer<AttributeInstance> onDirty) {
        this.attribute = attribute;
        this.onDirty = onDirty;
        this.baseValue = ((Attribute) attribute.value()).getDefaultValue();
    }

    public Holder<Attribute> getAttribute() {
        return this.attribute;
    }

    public double getBaseValue() {
        return this.baseValue;
    }

    public void setBaseValue(double baseValue) {
        if (baseValue != this.baseValue) {
            this.baseValue = baseValue;
            this.setDirty();
        }
    }

    @VisibleForTesting
    Map<Identifier, AttributeModifier> getModifiers(AttributeModifier.Operation operation) {
        return (Map) this.modifiersByOperation.computeIfAbsent(operation, (attributemodifier_operation1) -> {
            return new Object2ObjectOpenHashMap();
        });
    }

    public Set<AttributeModifier> getModifiers() {
        return ImmutableSet.copyOf(this.modifierById.values());
    }

    public Set<AttributeModifier> getPermanentModifiers() {
        return ImmutableSet.copyOf(this.permanentModifiers.values());
    }

    public @Nullable AttributeModifier getModifier(Identifier id) {
        return (AttributeModifier) this.modifierById.get(id);
    }

    public boolean hasModifier(Identifier modifier) {
        return this.modifierById.get(modifier) != null;
    }

    private void addModifier(AttributeModifier modifier) {
        AttributeModifier attributemodifier1 = (AttributeModifier) this.modifierById.putIfAbsent(modifier.id(), modifier);

        if (attributemodifier1 != null) {
            throw new IllegalArgumentException("Modifier is already applied on this attribute!");
        } else {
            this.getModifiers(modifier.operation()).put(modifier.id(), modifier);
            this.setDirty();
        }
    }

    public void addOrUpdateTransientModifier(AttributeModifier modifier) {
        AttributeModifier attributemodifier1 = (AttributeModifier) this.modifierById.put(modifier.id(), modifier);

        if (modifier != attributemodifier1) {
            this.getModifiers(modifier.operation()).put(modifier.id(), modifier);
            this.setDirty();
        }
    }

    public void addTransientModifier(AttributeModifier modifier) {
        this.addModifier(modifier);
    }

    public void addOrReplacePermanentModifier(AttributeModifier modifier) {
        this.removeModifier(modifier.id());
        this.addModifier(modifier);
        this.permanentModifiers.put(modifier.id(), modifier);
    }

    public void addPermanentModifier(AttributeModifier modifier) {
        this.addModifier(modifier);
        this.permanentModifiers.put(modifier.id(), modifier);
    }

    public void addPermanentModifiers(Collection<AttributeModifier> modifiers) {
        for (AttributeModifier attributemodifier : modifiers) {
            this.addPermanentModifier(attributemodifier);
        }

    }

    protected void setDirty() {
        this.dirty = true;
        this.onDirty.accept(this);
    }

    public void removeModifier(AttributeModifier modifier) {
        this.removeModifier(modifier.id());
    }

    public boolean removeModifier(Identifier id) {
        AttributeModifier attributemodifier = (AttributeModifier) this.modifierById.remove(id);

        if (attributemodifier == null) {
            return false;
        } else {
            this.getModifiers(attributemodifier.operation()).remove(id);
            this.permanentModifiers.remove(id);
            this.setDirty();
            return true;
        }
    }

    public void removeModifiers() {
        for (AttributeModifier attributemodifier : this.getModifiers()) {
            this.removeModifier(attributemodifier);
        }

    }

    public double getValue() {
        if (this.dirty) {
            this.cachedValue = this.calculateValue();
            this.dirty = false;
        }

        return this.cachedValue;
    }

    private double calculateValue() {
        double d0 = this.getBaseValue();

        for (AttributeModifier attributemodifier : this.getModifiersOrEmpty(AttributeModifier.Operation.ADD_VALUE)) {
            d0 += attributemodifier.amount();
        }

        double d1 = d0;

        for (AttributeModifier attributemodifier1 : this.getModifiersOrEmpty(AttributeModifier.Operation.ADD_MULTIPLIED_BASE)) {
            d1 += d0 * attributemodifier1.amount();
        }

        for (AttributeModifier attributemodifier2 : this.getModifiersOrEmpty(AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)) {
            d1 *= 1.0D + attributemodifier2.amount();
        }

        return ((Attribute) this.attribute.value()).sanitizeValue(d1);
    }

    private Collection<AttributeModifier> getModifiersOrEmpty(AttributeModifier.Operation operation) {
        return ((Map) this.modifiersByOperation.getOrDefault(operation, Map.of())).values();
    }

    public void replaceFrom(AttributeInstance other) {
        this.baseValue = other.baseValue;
        this.modifierById.clear();
        this.modifierById.putAll(other.modifierById);
        this.permanentModifiers.clear();
        this.permanentModifiers.putAll(other.permanentModifiers);
        this.modifiersByOperation.clear();
        other.modifiersByOperation.forEach((attributemodifier_operation, map) -> {
            this.getModifiers(attributemodifier_operation).putAll(map);
        });
        this.setDirty();
    }

    public AttributeInstance.Packed pack() {
        return new AttributeInstance.Packed(this.attribute, this.baseValue, List.copyOf(this.permanentModifiers.values()));
    }

    public void apply(AttributeInstance.Packed packed) {
        this.baseValue = packed.baseValue;

        for (AttributeModifier attributemodifier : packed.modifiers) {
            this.modifierById.put(attributemodifier.id(), attributemodifier);
            this.getModifiers(attributemodifier.operation()).put(attributemodifier.id(), attributemodifier);
            this.permanentModifiers.put(attributemodifier.id(), attributemodifier);
        }

        this.setDirty();
    }

    public static record Packed(Holder<Attribute> attribute, double baseValue, List<AttributeModifier> modifiers) {

        public static final Codec<AttributeInstance.Packed> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(BuiltInRegistries.ATTRIBUTE.holderByNameCodec().fieldOf("id").forGetter(AttributeInstance.Packed::attribute), Codec.DOUBLE.fieldOf("base").orElse(0.0D).forGetter(AttributeInstance.Packed::baseValue), AttributeModifier.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(AttributeInstance.Packed::modifiers)).apply(instance, AttributeInstance.Packed::new);
        });
        public static final Codec<List<AttributeInstance.Packed>> LIST_CODEC = AttributeInstance.Packed.CODEC.listOf();
    }
}
