package net.minecraft.world.entity.ai.attributes;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public class AttributeSupplier {

    private final Map<Holder<Attribute>, AttributeInstance> instances;

    private AttributeSupplier(Map<Holder<Attribute>, AttributeInstance> instances) {
        this.instances = instances;
    }

    private AttributeInstance getAttributeInstance(Holder<Attribute> attribute) {
        AttributeInstance attributeinstance = (AttributeInstance) this.instances.get(attribute);

        if (attributeinstance == null) {
            throw new IllegalArgumentException("Can't find attribute " + attribute.getRegisteredName());
        } else {
            return attributeinstance;
        }
    }

    public double getValue(Holder<Attribute> attribute) {
        return this.getAttributeInstance(attribute).getValue();
    }

    public double getBaseValue(Holder<Attribute> attribute) {
        return this.getAttributeInstance(attribute).getBaseValue();
    }

    public double getModifierValue(Holder<Attribute> attribute, Identifier id) {
        AttributeModifier attributemodifier = this.getAttributeInstance(attribute).getModifier(id);

        if (attributemodifier == null) {
            String s = String.valueOf(id);

            throw new IllegalArgumentException("Can't find modifier " + s + " on attribute " + attribute.getRegisteredName());
        } else {
            return attributemodifier.amount();
        }
    }

    public @Nullable AttributeInstance createInstance(Consumer<AttributeInstance> onDirty, Holder<Attribute> attribute) {
        AttributeInstance attributeinstance = (AttributeInstance) this.instances.get(attribute);

        if (attributeinstance == null) {
            return null;
        } else {
            AttributeInstance attributeinstance1 = new AttributeInstance(attribute, onDirty);

            attributeinstance1.replaceFrom(attributeinstance);
            return attributeinstance1;
        }
    }

    public static AttributeSupplier.Builder builder() {
        return new AttributeSupplier.Builder();
    }

    public boolean hasAttribute(Holder<Attribute> attribute) {
        return this.instances.containsKey(attribute);
    }

    public boolean hasModifier(Holder<Attribute> attribute, Identifier modifier) {
        AttributeInstance attributeinstance = (AttributeInstance) this.instances.get(attribute);

        return attributeinstance != null && attributeinstance.getModifier(modifier) != null;
    }

    public static class Builder {

        private final ImmutableMap.Builder<Holder<Attribute>, AttributeInstance> builder = ImmutableMap.builder();
        private boolean instanceFrozen;

        public Builder() {}

        private AttributeInstance create(Holder<Attribute> attribute) {
            AttributeInstance attributeinstance = new AttributeInstance(attribute, (attributeinstance1) -> {
                if (this.instanceFrozen) {
                    throw new UnsupportedOperationException("Tried to change value for default attribute instance: " + attribute.getRegisteredName());
                }
            });

            this.builder.put(attribute, attributeinstance);
            return attributeinstance;
        }

        public AttributeSupplier.Builder add(Holder<Attribute> attribute) {
            this.create(attribute);
            return this;
        }

        public AttributeSupplier.Builder add(Holder<Attribute> attribute, double baseValue) {
            AttributeInstance attributeinstance = this.create(attribute);

            attributeinstance.setBaseValue(baseValue);
            return this;
        }

        public AttributeSupplier build() {
            this.instanceFrozen = true;
            return new AttributeSupplier(this.builder.buildKeepingLast());
        }
    }
}
