package net.minecraft.world.item.crafting;

import com.mojang.datafixers.Products.P1;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public abstract class CustomRecipe implements CraftingRecipe {

    private final CraftingBookCategory category;

    public CustomRecipe(CraftingBookCategory category) {
        this.category = category;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public CraftingBookCategory category() {
        return this.category;
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public abstract RecipeSerializer<? extends CustomRecipe> getSerializer();

    public static class Serializer<T extends CraftingRecipe> implements RecipeSerializer<T> {

        private final MapCodec<T> codec;
        private final StreamCodec<RegistryFriendlyByteBuf, T> streamCodec;

        public Serializer(CustomRecipe.Serializer.Factory<T> constructor) {
            this.codec = RecordCodecBuilder.mapCodec((instance) -> {
                P1 p1 = instance.group(CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(CraftingRecipe::category));

                Objects.requireNonNull(constructor);
                return p1.apply(instance, constructor::create);
            });
            StreamCodec streamcodec = CraftingBookCategory.STREAM_CODEC;
            Function function = CraftingRecipe::category;

            Objects.requireNonNull(constructor);
            this.streamCodec = StreamCodec.composite(streamcodec, function, constructor::create);
        }

        @Override
        public MapCodec<T> codec() {
            return this.codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, T> streamCodec() {
            return this.streamCodec;
        }

        @FunctionalInterface
        public interface Factory<T extends CraftingRecipe> {

            T create(CraftingBookCategory category);
        }
    }
}
