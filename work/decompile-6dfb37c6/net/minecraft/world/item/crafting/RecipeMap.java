package net.minecraft.world.item.crafting;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class RecipeMap {

    public static final RecipeMap EMPTY = new RecipeMap(ImmutableMultimap.of(), Map.of());
    public final Multimap<RecipeType<?>, RecipeHolder<?>> byType;
    private final Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> byKey;

    private RecipeMap(Multimap<RecipeType<?>, RecipeHolder<?>> byType, Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> byKey) {
        this.byType = byType;
        this.byKey = byKey;
    }

    public static RecipeMap create(Iterable<RecipeHolder<?>> recipes) {
        ImmutableMultimap.Builder<RecipeType<?>, RecipeHolder<?>> immutablemultimap_builder = ImmutableMultimap.builder();
        ImmutableMap.Builder<ResourceKey<Recipe<?>>, RecipeHolder<?>> immutablemap_builder = ImmutableMap.builder();

        for (RecipeHolder<?> recipeholder : recipes) {
            immutablemultimap_builder.put(recipeholder.value().getType(), recipeholder);
            immutablemap_builder.put(recipeholder.id(), recipeholder);
        }

        return new RecipeMap(immutablemultimap_builder.build(), immutablemap_builder.build());
    }

    public <I extends RecipeInput, T extends Recipe<I>> Collection<RecipeHolder<T>> byType(RecipeType<T> type) {
        return this.byType.get(type);
    }

    public Collection<RecipeHolder<?>> values() {
        return this.byKey.values();
    }

    public @Nullable RecipeHolder<?> byKey(ResourceKey<Recipe<?>> recipeId) {
        return (RecipeHolder) this.byKey.get(recipeId);
    }

    public <I extends RecipeInput, T extends Recipe<I>> Stream<RecipeHolder<T>> getRecipesFor(RecipeType<T> type, I container, Level level) {
        return container.isEmpty() ? Stream.empty() : this.byType(type).stream().filter((recipeholder) -> {
            return recipeholder.value().matches(container, level);
        });
    }
}
