package net.minecraft.world.entity.ai.attributes;

import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public class AttributeMap {

    private final Map<Holder<Attribute>, AttributeInstance> attributes = new Object2ObjectOpenHashMap();
    private final Set<AttributeInstance> attributesToSync = new ObjectOpenHashSet();
    private final Set<AttributeInstance> attributesToUpdate = new ObjectOpenHashSet();
    private final AttributeSupplier supplier;

    public AttributeMap(AttributeSupplier supplier) {
        this.supplier = supplier;
    }

    private void onAttributeModified(AttributeInstance attributeInstance) {
        this.attributesToUpdate.add(attributeInstance);
        if (((Attribute) attributeInstance.getAttribute().value()).isClientSyncable()) {
            this.attributesToSync.add(attributeInstance);
        }

    }

    public Set<AttributeInstance> getAttributesToSync() {
        return this.attributesToSync;
    }

    public Set<AttributeInstance> getAttributesToUpdate() {
        return this.attributesToUpdate;
    }

    public Collection<AttributeInstance> getSyncableAttributes() {
        return (Collection) this.attributes.values().stream().filter((attributeinstance) -> {
            return ((Attribute) attributeinstance.getAttribute().value()).isClientSyncable();
        }).collect(Collectors.toList());
    }

    public @Nullable AttributeInstance getInstance(Holder<Attribute> attribute) {
        return (AttributeInstance) this.attributes.computeIfAbsent(attribute, (holder1) -> {
            return this.supplier.createInstance(this::onAttributeModified, holder1);
        });
    }

    public boolean hasAttribute(Holder<Attribute> attribute) {
        return this.attributes.get(attribute) != null || this.supplier.hasAttribute(attribute);
    }

    public boolean hasModifier(Holder<Attribute> attribute, Identifier id) {
        AttributeInstance attributeinstance = (AttributeInstance) this.attributes.get(attribute);

        return attributeinstance != null ? attributeinstance.getModifier(id) != null : this.supplier.hasModifier(attribute, id);
    }

    public double getValue(Holder<Attribute> attribute) {
        AttributeInstance attributeinstance = (AttributeInstance) this.attributes.get(attribute);

        return attributeinstance != null ? attributeinstance.getValue() : this.supplier.getValue(attribute);
    }

    public double getBaseValue(Holder<Attribute> attribute) {
        AttributeInstance attributeinstance = (AttributeInstance) this.attributes.get(attribute);

        return attributeinstance != null ? attributeinstance.getBaseValue() : this.supplier.getBaseValue(attribute);
    }

    public double getModifierValue(Holder<Attribute> attribute, Identifier id) {
        AttributeInstance attributeinstance = (AttributeInstance) this.attributes.get(attribute);

        return attributeinstance != null ? attributeinstance.getModifier(id).amount() : this.supplier.getModifierValue(attribute, id);
    }

    public void addTransientAttributeModifiers(Multimap<Holder<Attribute>, AttributeModifier> modifiers) {
        modifiers.forEach((holder, attributemodifier) -> {
            AttributeInstance attributeinstance = this.getInstance(holder);

            if (attributeinstance != null) {
                attributeinstance.removeModifier(attributemodifier.id());
                attributeinstance.addTransientModifier(attributemodifier);
            }

        });
    }

    public void removeAttributeModifiers(Multimap<Holder<Attribute>, AttributeModifier> modifiers) {
        modifiers.asMap().forEach((holder, collection) -> {
            AttributeInstance attributeinstance = (AttributeInstance) this.attributes.get(holder);

            if (attributeinstance != null) {
                collection.forEach((attributemodifier) -> {
                    attributeinstance.removeModifier(attributemodifier.id());
                });
            }

        });
    }

    public void assignAllValues(AttributeMap other) {
        other.attributes.values().forEach((attributeinstance) -> {
            AttributeInstance attributeinstance1 = this.getInstance(attributeinstance.getAttribute());

            if (attributeinstance1 != null) {
                attributeinstance1.replaceFrom(attributeinstance);
            }

        });
    }

    public void assignBaseValues(AttributeMap other) {
        other.attributes.values().forEach((attributeinstance) -> {
            AttributeInstance attributeinstance1 = this.getInstance(attributeinstance.getAttribute());

            if (attributeinstance1 != null) {
                attributeinstance1.setBaseValue(attributeinstance.getBaseValue());
            }

        });
    }

    public void assignPermanentModifiers(AttributeMap other) {
        other.attributes.values().forEach((attributeinstance) -> {
            AttributeInstance attributeinstance1 = this.getInstance(attributeinstance.getAttribute());

            if (attributeinstance1 != null) {
                attributeinstance1.addPermanentModifiers(attributeinstance.getPermanentModifiers());
            }

        });
    }

    public boolean resetBaseValue(Holder<Attribute> attribute) {
        if (!this.supplier.hasAttribute(attribute)) {
            return false;
        } else {
            AttributeInstance attributeinstance = (AttributeInstance) this.attributes.get(attribute);

            if (attributeinstance != null) {
                attributeinstance.setBaseValue(this.supplier.getBaseValue(attribute));
            }

            return true;
        }
    }

    public List<AttributeInstance.Packed> pack() {
        List<AttributeInstance.Packed> list = new ArrayList(this.attributes.values().size());

        for (AttributeInstance attributeinstance : this.attributes.values()) {
            list.add(attributeinstance.pack());
        }

        return list;
    }

    public void apply(List<AttributeInstance.Packed> packedAttributes) {
        for (AttributeInstance.Packed attributeinstance_packed : packedAttributes) {
            AttributeInstance attributeinstance = this.getInstance(attributeinstance_packed.attribute());

            if (attributeinstance != null) {
                attributeinstance.apply(attributeinstance_packed);
            }
        }

    }
}
