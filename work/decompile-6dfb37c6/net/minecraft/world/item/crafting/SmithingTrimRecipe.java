package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import org.jspecify.annotations.Nullable;

public class SmithingTrimRecipe implements SmithingRecipe {

    private final Ingredient template;
    private final Ingredient base;
    private final Ingredient addition;
    private final Holder<TrimPattern> pattern;
    private @Nullable PlacementInfo placementInfo;

    public SmithingTrimRecipe(Ingredient template, Ingredient base, Ingredient addition, Holder<TrimPattern> pattern) {
        this.template = template;
        this.base = base;
        this.addition = addition;
        this.pattern = pattern;
    }

    public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider registries) {
        return applyTrim(registries, input.base(), input.addition(), this.pattern);
    }

    public static ItemStack applyTrim(HolderLookup.Provider registries, ItemStack baseItem, ItemStack materialItem, Holder<TrimPattern> pattern) {
        Optional<Holder<TrimMaterial>> optional = TrimMaterials.getFromIngredient(registries, materialItem);

        if (optional.isPresent()) {
            ArmorTrim armortrim = (ArmorTrim) baseItem.get(DataComponents.TRIM);
            ArmorTrim armortrim1 = new ArmorTrim((Holder) optional.get(), pattern);

            if (Objects.equals(armortrim, armortrim1)) {
                return ItemStack.EMPTY;
            } else {
                ItemStack itemstack2 = baseItem.copyWithCount(1);

                itemstack2.set(DataComponents.TRIM, armortrim1);
                return itemstack2;
            }
        } else {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public Optional<Ingredient> templateIngredient() {
        return Optional.of(this.template);
    }

    @Override
    public Ingredient baseIngredient() {
        return this.base;
    }

    @Override
    public Optional<Ingredient> additionIngredient() {
        return Optional.of(this.addition);
    }

    @Override
    public RecipeSerializer<SmithingTrimRecipe> getSerializer() {
        return RecipeSerializer.SMITHING_TRIM;
    }

    @Override
    public PlacementInfo placementInfo() {
        if (this.placementInfo == null) {
            this.placementInfo = PlacementInfo.create(List.of(this.template, this.base, this.addition));
        }

        return this.placementInfo;
    }

    @Override
    public List<RecipeDisplay> display() {
        SlotDisplay slotdisplay = this.base.display();
        SlotDisplay slotdisplay1 = this.addition.display();
        SlotDisplay slotdisplay2 = this.template.display();

        return List.of(new SmithingRecipeDisplay(slotdisplay2, slotdisplay, slotdisplay1, new SlotDisplay.SmithingTrimDemoSlotDisplay(slotdisplay, slotdisplay1, this.pattern), new SlotDisplay.ItemSlotDisplay(Items.SMITHING_TABLE)));
    }

    public static class Serializer implements RecipeSerializer<SmithingTrimRecipe> {

        private static final MapCodec<SmithingTrimRecipe> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Ingredient.CODEC.fieldOf("template").forGetter((smithingtrimrecipe) -> {
                return smithingtrimrecipe.template;
            }), Ingredient.CODEC.fieldOf("base").forGetter((smithingtrimrecipe) -> {
                return smithingtrimrecipe.base;
            }), Ingredient.CODEC.fieldOf("addition").forGetter((smithingtrimrecipe) -> {
                return smithingtrimrecipe.addition;
            }), TrimPattern.CODEC.fieldOf("pattern").forGetter((smithingtrimrecipe) -> {
                return smithingtrimrecipe.pattern;
            })).apply(instance, SmithingTrimRecipe::new);
        });
        public static final StreamCodec<RegistryFriendlyByteBuf, SmithingTrimRecipe> STREAM_CODEC = StreamCodec.composite(Ingredient.CONTENTS_STREAM_CODEC, (smithingtrimrecipe) -> {
            return smithingtrimrecipe.template;
        }, Ingredient.CONTENTS_STREAM_CODEC, (smithingtrimrecipe) -> {
            return smithingtrimrecipe.base;
        }, Ingredient.CONTENTS_STREAM_CODEC, (smithingtrimrecipe) -> {
            return smithingtrimrecipe.addition;
        }, TrimPattern.STREAM_CODEC, (smithingtrimrecipe) -> {
            return smithingtrimrecipe.pattern;
        }, SmithingTrimRecipe::new);

        public Serializer() {}

        @Override
        public MapCodec<SmithingTrimRecipe> codec() {
            return SmithingTrimRecipe.Serializer.CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, SmithingTrimRecipe> streamCodec() {
            return SmithingTrimRecipe.Serializer.STREAM_CODEC;
        }
    }
}
