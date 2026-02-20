package net.minecraft.world.item.enchantment;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;
import org.jspecify.annotations.Nullable;

public class ItemEnchantments implements TooltipProvider {

    public static final ItemEnchantments EMPTY = new ItemEnchantments(new Object2IntOpenHashMap());
    private static final Codec<Integer> LEVEL_CODEC = Codec.intRange(1, 255);
    public static final Codec<ItemEnchantments> CODEC = Codec.unboundedMap(Enchantment.CODEC, ItemEnchantments.LEVEL_CODEC).xmap((map) -> {
        return new ItemEnchantments(new Object2IntOpenHashMap(map));
    }, (itemenchantments) -> {
        return itemenchantments.enchantments;
    });
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemEnchantments> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.map(Object2IntOpenHashMap::new, Enchantment.STREAM_CODEC, ByteBufCodecs.VAR_INT), (itemenchantments) -> {
        return itemenchantments.enchantments;
    }, ItemEnchantments::new);
    private final Object2IntOpenHashMap<Holder<Enchantment>> enchantments;

    private ItemEnchantments(Object2IntOpenHashMap<Holder<Enchantment>> enchantments) {
        this.enchantments = enchantments;
        ObjectIterator objectiterator = enchantments.object2IntEntrySet().iterator();

        while (objectiterator.hasNext()) {
            Object2IntMap.Entry<Holder<Enchantment>> object2intmap_entry = (Entry) objectiterator.next();
            int i = object2intmap_entry.getIntValue();

            if (i < 0 || i > 255) {
                String s = String.valueOf(object2intmap_entry.getKey());

                throw new IllegalArgumentException("Enchantment " + s + " has invalid level " + i);
            }
        }

    }

    public int getLevel(Holder<Enchantment> enchantment) {
        return this.enchantments.getInt(enchantment);
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
        HolderLookup.Provider holderlookup_provider = context.registries();
        HolderSet<Enchantment> holderset = getTagOrEmpty(holderlookup_provider, Registries.ENCHANTMENT, EnchantmentTags.TOOLTIP_ORDER);

        for (Holder<Enchantment> holder : holderset) {
            int i = this.enchantments.getInt(holder);

            if (i > 0) {
                consumer.accept(Enchantment.getFullname(holder, i));
            }
        }

        ObjectIterator objectiterator = this.enchantments.object2IntEntrySet().iterator();

        while (objectiterator.hasNext()) {
            Object2IntMap.Entry<Holder<Enchantment>> object2intmap_entry = (Entry) objectiterator.next();
            Holder<Enchantment> holder1 = (Holder) object2intmap_entry.getKey();

            if (!holderset.contains(holder1)) {
                consumer.accept(Enchantment.getFullname((Holder) object2intmap_entry.getKey(), object2intmap_entry.getIntValue()));
            }
        }

    }

    private static <T> HolderSet<T> getTagOrEmpty(HolderLookup.@Nullable Provider registries, ResourceKey<Registry<T>> registry, TagKey<T> tag) {
        if (registries != null) {
            Optional<HolderSet.Named<T>> optional = registries.lookupOrThrow(registry).get(tag);

            if (optional.isPresent()) {
                return (HolderSet) optional.get();
            }
        }

        return HolderSet.direct();
    }

    public Set<Holder<Enchantment>> keySet() {
        return Collections.unmodifiableSet(this.enchantments.keySet());
    }

    public Set<Object2IntMap.Entry<Holder<Enchantment>>> entrySet() {
        return Collections.unmodifiableSet(this.enchantments.object2IntEntrySet());
    }

    public int size() {
        return this.enchantments.size();
    }

    public boolean isEmpty() {
        return this.enchantments.isEmpty();
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof ItemEnchantments) {
            ItemEnchantments itemenchantments = (ItemEnchantments) obj;

            return this.enchantments.equals(itemenchantments.enchantments);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return this.enchantments.hashCode();
    }

    public String toString() {
        return "ItemEnchantments{enchantments=" + String.valueOf(this.enchantments) + "}";
    }

    public static class Mutable {

        private final Object2IntOpenHashMap<Holder<Enchantment>> enchantments = new Object2IntOpenHashMap();

        public Mutable(ItemEnchantments enchantments) {
            this.enchantments.putAll(enchantments.enchantments);
        }

        public void set(Holder<Enchantment> enchantment, int level) {
            if (level <= 0) {
                this.enchantments.removeInt(enchantment);
            } else {
                this.enchantments.put(enchantment, Math.min(level, 255));
            }

        }

        public void upgrade(Holder<Enchantment> enchantment, int level) {
            if (level > 0) {
                this.enchantments.merge(enchantment, Math.min(level, 255), Integer::max);
            }

        }

        public void removeIf(Predicate<Holder<Enchantment>> predicate) {
            this.enchantments.keySet().removeIf(predicate);
        }

        public int getLevel(Holder<Enchantment> enchantment) {
            return this.enchantments.getOrDefault(enchantment, 0);
        }

        public Set<Holder<Enchantment>> keySet() {
            return this.enchantments.keySet();
        }

        public ItemEnchantments toImmutable() {
            return new ItemEnchantments(this.enchantments);
        }
    }
}
