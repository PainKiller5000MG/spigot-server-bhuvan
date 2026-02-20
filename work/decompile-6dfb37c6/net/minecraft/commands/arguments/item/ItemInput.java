package net.minecraft.commands.arguments.item;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.serialization.DynamicOps;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ItemInput {

    private static final Dynamic2CommandExceptionType ERROR_STACK_TOO_BIG = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("arguments.item.overstacked", object, object1);
    });
    private final Holder<Item> item;
    private final DataComponentPatch components;

    public ItemInput(Holder<Item> item, DataComponentPatch components) {
        this.item = item;
        this.components = components;
    }

    public Item getItem() {
        return this.item.value();
    }

    public ItemStack createItemStack(int count, boolean checkSize) throws CommandSyntaxException {
        ItemStack itemstack = new ItemStack(this.item, count);

        itemstack.applyComponents(this.components);
        if (checkSize && count > itemstack.getMaxStackSize()) {
            throw ItemInput.ERROR_STACK_TOO_BIG.create(this.getItemName(), itemstack.getMaxStackSize());
        } else {
            return itemstack;
        }
    }

    public String serialize(HolderLookup.Provider registries) {
        StringBuilder stringbuilder = new StringBuilder(this.getItemName());
        String s = this.serializeComponents(registries);

        if (!s.isEmpty()) {
            stringbuilder.append('[');
            stringbuilder.append(s);
            stringbuilder.append(']');
        }

        return stringbuilder.toString();
    }

    private String serializeComponents(HolderLookup.Provider registries) {
        DynamicOps<Tag> dynamicops = registries.<Tag>createSerializationContext(NbtOps.INSTANCE);

        return (String) this.components.entrySet().stream().flatMap((entry) -> {
            DataComponentType<?> datacomponenttype = (DataComponentType) entry.getKey();
            Identifier identifier = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(datacomponenttype);

            if (identifier == null) {
                return Stream.empty();
            } else {
                Optional<?> optional = (Optional) entry.getValue();

                if (optional.isPresent()) {
                    TypedDataComponent<?> typeddatacomponent = TypedDataComponent.createUnchecked(datacomponenttype, optional.get());

                    return typeddatacomponent.encodeValue(dynamicops).result().stream().map((tag) -> {
                        String s = identifier.toString();

                        return s + "=" + String.valueOf(tag);
                    });
                } else {
                    return Stream.of("!" + identifier.toString());
                }
            }
        }).collect(Collectors.joining(String.valueOf(',')));
    }

    private String getItemName() {
        return this.item.unwrapKey().map(ResourceKey::identifier).orElseGet(() -> {
            return "unknown[" + String.valueOf(this.item) + "]";
        }).toString();
    }
}
