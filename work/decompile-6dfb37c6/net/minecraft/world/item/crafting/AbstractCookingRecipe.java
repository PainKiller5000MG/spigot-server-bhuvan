package net.minecraft.world.item.crafting;

import com.mojang.datafixers.Products.P6;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;

public abstract class AbstractCookingRecipe extends SingleItemRecipe {

    private final CookingBookCategory category;
    private final float experience;
    private final int cookingTime;

    public AbstractCookingRecipe(String group, CookingBookCategory category, Ingredient ingredient, ItemStack result, float experience, int cookingTime) {
        super(group, ingredient, result);
        this.category = category;
        this.experience = experience;
        this.cookingTime = cookingTime;
    }

    @Override
    public abstract RecipeSerializer<? extends AbstractCookingRecipe> getSerializer();

    @Override
    public abstract RecipeType<? extends AbstractCookingRecipe> getType();

    public float experience() {
        return this.experience;
    }

    public int cookingTime() {
        return this.cookingTime;
    }

    public CookingBookCategory category() {
        return this.category;
    }

    protected abstract Item furnaceIcon();

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new FurnaceRecipeDisplay(this.input().display(), SlotDisplay.AnyFuel.INSTANCE, new SlotDisplay.ItemStackSlotDisplay(this.result()), new SlotDisplay.ItemSlotDisplay(this.furnaceIcon()), this.cookingTime, this.experience));
    }

    public static class Serializer<T extends AbstractCookingRecipe> implements RecipeSerializer<T> {

        private final MapCodec<T> codec;
        private final StreamCodec<RegistryFriendlyByteBuf, T> streamCodec;

        public Serializer(AbstractCookingRecipe.Factory<T> factory, int defaultCookingTime) {
            this.codec = RecordCodecBuilder.mapCodec((instance) -> {
                P6 p6 = instance.group(Codec.STRING.optionalFieldOf("group", "").forGetter(SingleItemRecipe::group), CookingBookCategory.CODEC.fieldOf("category").orElse(CookingBookCategory.MISC).forGetter(AbstractCookingRecipe::category), Ingredient.CODEC.fieldOf("ingredient").forGetter(SingleItemRecipe::input), ItemStack.STRICT_SINGLE_ITEM_CODEC.fieldOf("result").forGetter(SingleItemRecipe::result), Codec.FLOAT.fieldOf("experience").orElse(0.0F).forGetter(AbstractCookingRecipe::experience), Codec.INT.fieldOf("cookingtime").orElse(defaultCookingTime).forGetter(AbstractCookingRecipe::cookingTime));

                Objects.requireNonNull(factory);
                return p6.apply(instance, factory::create);
            });
            StreamCodec streamcodec = ByteBufCodecs.STRING_UTF8;
            Function function = SingleItemRecipe::group;
            StreamCodec streamcodec1 = CookingBookCategory.STREAM_CODEC;
            Function function1 = AbstractCookingRecipe::category;
            StreamCodec streamcodec2 = Ingredient.CONTENTS_STREAM_CODEC;
            Function function2 = SingleItemRecipe::input;
            StreamCodec streamcodec3 = ItemStack.STREAM_CODEC;
            Function function3 = SingleItemRecipe::result;
            StreamCodec streamcodec4 = ByteBufCodecs.FLOAT;
            Function function4 = AbstractCookingRecipe::experience;
            StreamCodec streamcodec5 = ByteBufCodecs.INT;
            Function function5 = AbstractCookingRecipe::cookingTime;

            Objects.requireNonNull(factory);
            this.streamCodec = StreamCodec.composite(streamcodec, function, streamcodec1, function1, streamcodec2, function2, streamcodec3, function3, streamcodec4, function4, streamcodec5, function5, factory::create);
        }

        @Override
        public MapCodec<T> codec() {
            return this.codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, T> streamCodec() {
            return this.streamCodec;
        }
    }

    @FunctionalInterface
    public interface Factory<T extends AbstractCookingRecipe> {

        T create(String group, CookingBookCategory category, Ingredient ingredient, ItemStack result, float experience, int cookingTime);
    }
}
