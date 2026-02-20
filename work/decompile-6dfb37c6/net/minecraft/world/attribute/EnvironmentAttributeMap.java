package net.minecraft.world.attribute;

import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.Util;
import net.minecraft.world.attribute.modifier.AttributeModifier;
import org.jspecify.annotations.Nullable;

public final class EnvironmentAttributeMap {

    public static final EnvironmentAttributeMap EMPTY = new EnvironmentAttributeMap(Map.of());
    public static final Codec<EnvironmentAttributeMap> CODEC = Codec.lazyInitialized(() -> {
        return Codec.dispatchedMap(EnvironmentAttributes.CODEC, Util.memoize(EnvironmentAttributeMap.Entry::createCodec)).xmap(EnvironmentAttributeMap::new, (environmentattributemap) -> {
            return environmentattributemap.entries;
        });
    });
    public static final Codec<EnvironmentAttributeMap> NETWORK_CODEC = EnvironmentAttributeMap.CODEC.xmap(EnvironmentAttributeMap::filterSyncable, EnvironmentAttributeMap::filterSyncable);
    public static final Codec<EnvironmentAttributeMap> CODEC_ONLY_POSITIONAL = EnvironmentAttributeMap.CODEC.validate((environmentattributemap) -> {
        List<EnvironmentAttribute<?>> list = environmentattributemap.keySet().stream().filter((environmentattribute) -> {
            return !environmentattribute.isPositional();
        }).toList();

        return !list.isEmpty() ? DataResult.error(() -> {
            return "The following attributes cannot be positional: " + String.valueOf(list);
        }) : DataResult.success(environmentattributemap);
    });
    private final Map<EnvironmentAttribute<?>, EnvironmentAttributeMap.Entry<?, ?>> entries;

    private static EnvironmentAttributeMap filterSyncable(EnvironmentAttributeMap attributes) {
        return new EnvironmentAttributeMap(Map.copyOf(Maps.filterKeys(attributes.entries, EnvironmentAttribute::isSyncable)));
    }

    private EnvironmentAttributeMap(Map<EnvironmentAttribute<?>, EnvironmentAttributeMap.Entry<?, ?>> entries) {
        this.entries = entries;
    }

    public static EnvironmentAttributeMap.Builder builder() {
        return new EnvironmentAttributeMap.Builder();
    }

    public <Value> EnvironmentAttributeMap.@Nullable Entry<Value, ?> get(EnvironmentAttribute<Value> attribute) {
        return (EnvironmentAttributeMap.Entry) this.entries.get(attribute);
    }

    public <Value> Value applyModifier(EnvironmentAttribute<Value> attribute, Value baseValue) {
        EnvironmentAttributeMap.Entry<Value, ?> environmentattributemap_entry = this.get(attribute);

        return environmentattributemap_entry != null ? environmentattributemap_entry.applyModifier(baseValue) : baseValue;
    }

    public boolean contains(EnvironmentAttribute<?> attribute) {
        return this.entries.containsKey(attribute);
    }

    public Set<EnvironmentAttribute<?>> keySet() {
        return this.entries.keySet();
    }

    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else {
            boolean flag;

            if (obj instanceof EnvironmentAttributeMap) {
                EnvironmentAttributeMap environmentattributemap = (EnvironmentAttributeMap) obj;

                if (this.entries.equals(environmentattributemap.entries)) {
                    flag = true;
                    return flag;
                }
            }

            flag = false;
            return flag;
        }
    }

    public int hashCode() {
        return this.entries.hashCode();
    }

    public String toString() {
        return this.entries.toString();
    }

    public static record Entry<Value, Argument>(Argument argument, AttributeModifier<Value, Argument> modifier) {

        private static <Value> Codec<EnvironmentAttributeMap.Entry<Value, ?>> createCodec(EnvironmentAttribute<Value> attribute) {
            Codec<EnvironmentAttributeMap.Entry<Value, ?>> codec = attribute.type().modifierCodec().dispatch("modifier", EnvironmentAttributeMap.Entry::modifier, Util.memoize((attributemodifier) -> {
                return createFullCodec(attribute, attributemodifier);
            }));

            return Codec.either(attribute.valueCodec(), codec).xmap((either) -> {
                return (EnvironmentAttributeMap.Entry) either.map((object) -> {
                    return new EnvironmentAttributeMap.Entry(object, AttributeModifier.override());
                }, (environmentattributemap_entry) -> {
                    return environmentattributemap_entry;
                });
            }, (environmentattributemap_entry) -> {
                return environmentattributemap_entry.modifier == AttributeModifier.override() ? Either.left(environmentattributemap_entry.argument()) : Either.right(environmentattributemap_entry);
            });
        }

        private static <Value, Argument> MapCodec<EnvironmentAttributeMap.Entry<Value, Argument>> createFullCodec(EnvironmentAttribute<Value> attribute, AttributeModifier<Value, Argument> modifier) {
            return RecordCodecBuilder.mapCodec((instance) -> {
                return instance.group(modifier.argumentCodec(attribute).fieldOf("argument").forGetter(EnvironmentAttributeMap.Entry::argument)).apply(instance, (object) -> {
                    return new EnvironmentAttributeMap.Entry(object, modifier);
                });
            });
        }

        public Value applyModifier(Value subject) {
            return this.modifier.apply(subject, this.argument);
        }
    }

    public static class Builder {

        private final Map<EnvironmentAttribute<?>, EnvironmentAttributeMap.Entry<?, ?>> entries = new HashMap();

        private Builder() {}

        public EnvironmentAttributeMap.Builder putAll(EnvironmentAttributeMap map) {
            this.entries.putAll(map.entries);
            return this;
        }

        public <Value, Parameter> EnvironmentAttributeMap.Builder modify(EnvironmentAttribute<Value> attribute, AttributeModifier<Value, Parameter> modifier, Parameter value) {
            attribute.type().checkAllowedModifier(modifier);
            this.entries.put(attribute, new EnvironmentAttributeMap.Entry(value, modifier));
            return this;
        }

        public <Value> EnvironmentAttributeMap.Builder set(EnvironmentAttribute<Value> attribute, Value value) {
            return this.modify(attribute, AttributeModifier.override(), value);
        }

        public EnvironmentAttributeMap build() {
            return this.entries.isEmpty() ? EnvironmentAttributeMap.EMPTY : new EnvironmentAttributeMap(Map.copyOf(this.entries));
        }
    }
}
