package net.minecraft.world.item.crafting;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class RecipeManager extends SimplePreparableReloadListener<RecipeMap> implements RecipeAccess {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceKey<RecipePropertySet>, RecipeManager.IngredientExtractor> RECIPE_PROPERTY_SETS = Map.of(RecipePropertySet.SMITHING_ADDITION, (RecipeManager.IngredientExtractor) (recipe) -> {
        Optional optional;

        if (recipe instanceof SmithingRecipe smithingrecipe) {
            optional = smithingrecipe.additionIngredient();
        } else {
            optional = Optional.empty();
        }

        return optional;
    }, RecipePropertySet.SMITHING_BASE, (RecipeManager.IngredientExtractor) (recipe) -> {
        Optional optional;

        if (recipe instanceof SmithingRecipe smithingrecipe) {
            optional = Optional.of(smithingrecipe.baseIngredient());
        } else {
            optional = Optional.empty();
        }

        return optional;
    }, RecipePropertySet.SMITHING_TEMPLATE, (RecipeManager.IngredientExtractor) (recipe) -> {
        Optional optional;

        if (recipe instanceof SmithingRecipe smithingrecipe) {
            optional = smithingrecipe.templateIngredient();
        } else {
            optional = Optional.empty();
        }

        return optional;
    }, RecipePropertySet.FURNACE_INPUT, forSingleInput(RecipeType.SMELTING), RecipePropertySet.BLAST_FURNACE_INPUT, forSingleInput(RecipeType.BLASTING), RecipePropertySet.SMOKER_INPUT, forSingleInput(RecipeType.SMOKING), RecipePropertySet.CAMPFIRE_INPUT, forSingleInput(RecipeType.CAMPFIRE_COOKING));
    private static final FileToIdConverter RECIPE_LISTER = FileToIdConverter.registry(Registries.RECIPE);
    private final HolderLookup.Provider registries;
    public RecipeMap recipes;
    private Map<ResourceKey<RecipePropertySet>, RecipePropertySet> propertySets;
    private SelectableRecipe.SingleInputSet<StonecutterRecipe> stonecutterRecipes;
    private List<RecipeManager.ServerDisplayInfo> allDisplays;
    private Map<ResourceKey<Recipe<?>>, List<RecipeManager.ServerDisplayInfo>> recipeToDisplay;

    public RecipeManager(HolderLookup.Provider registries) {
        this.recipes = RecipeMap.EMPTY;
        this.propertySets = Map.of();
        this.stonecutterRecipes = SelectableRecipe.SingleInputSet.<StonecutterRecipe>empty();
        this.allDisplays = List.of();
        this.recipeToDisplay = Map.of();
        this.registries = registries;
    }

    @Override
    protected RecipeMap prepare(ResourceManager manager, ProfilerFiller profiler) {
        SortedMap<Identifier, Recipe<?>> sortedmap = new TreeMap();

        SimpleJsonResourceReloadListener.scanDirectory(manager, RecipeManager.RECIPE_LISTER, this.registries.createSerializationContext(JsonOps.INSTANCE), Recipe.CODEC, sortedmap);
        List<RecipeHolder<?>> list = new ArrayList(sortedmap.size());

        sortedmap.forEach((identifier, recipe) -> {
            ResourceKey<Recipe<?>> resourcekey = ResourceKey.create(Registries.RECIPE, identifier);
            RecipeHolder<?> recipeholder = new RecipeHolder(resourcekey, recipe);

            list.add(recipeholder);
        });
        return RecipeMap.create(list);
    }

    protected void apply(RecipeMap recipes, ResourceManager manager, ProfilerFiller profiler) {
        this.recipes = recipes;
        RecipeManager.LOGGER.info("Loaded {} recipes", recipes.values().size());
    }

    public void finalizeRecipeLoading(FeatureFlagSet enabledFlags) {
        List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> list = new ArrayList();
        List<RecipeManager.IngredientCollector> list1 = RecipeManager.RECIPE_PROPERTY_SETS.entrySet().stream().map((entry) -> {
            return new RecipeManager.IngredientCollector((ResourceKey) entry.getKey(), (RecipeManager.IngredientExtractor) entry.getValue());
        }).toList();

        this.recipes.values().forEach((recipeholder) -> {
            Recipe<?> recipe = recipeholder.value();

            if (!recipe.isSpecial() && recipe.placementInfo().isImpossibleToPlace()) {
                RecipeManager.LOGGER.warn("Recipe {} can't be placed due to empty ingredients and will be ignored", recipeholder.id().identifier());
            } else {
                list1.forEach((recipemanager_ingredientcollector) -> {
                    recipemanager_ingredientcollector.accept(recipe);
                });
                if (recipe instanceof StonecutterRecipe) {
                    StonecutterRecipe stonecutterrecipe = (StonecutterRecipe) recipe;

                    if (isIngredientEnabled(enabledFlags, stonecutterrecipe.input()) && stonecutterrecipe.resultDisplay().isEnabled(enabledFlags)) {
                        list.add(new SelectableRecipe.SingleInputEntry(stonecutterrecipe.input(), new SelectableRecipe(stonecutterrecipe.resultDisplay(), Optional.of(recipeholder))));
                    }
                }

            }
        });
        this.propertySets = (Map) list1.stream().collect(Collectors.toUnmodifiableMap((recipemanager_ingredientcollector) -> {
            return recipemanager_ingredientcollector.key;
        }, (recipemanager_ingredientcollector) -> {
            return recipemanager_ingredientcollector.asPropertySet(enabledFlags);
        }));
        this.stonecutterRecipes = new SelectableRecipe.SingleInputSet<StonecutterRecipe>(list);
        this.allDisplays = unpackRecipeInfo(this.recipes.values(), enabledFlags);
        this.recipeToDisplay = (Map) this.allDisplays.stream().collect(Collectors.groupingBy((recipemanager_serverdisplayinfo) -> {
            return recipemanager_serverdisplayinfo.parent.id();
        }, IdentityHashMap::new, Collectors.toList()));
    }

    private static List<Ingredient> filterDisabled(FeatureFlagSet enabledFlags, List<Ingredient> ingredients) {
        ingredients.removeIf((ingredient) -> {
            return !isIngredientEnabled(enabledFlags, ingredient);
        });
        return ingredients;
    }

    private static boolean isIngredientEnabled(FeatureFlagSet enabledFlags, Ingredient ingredient) {
        return ingredient.items().allMatch((holder) -> {
            return ((Item) holder.value()).isEnabled(enabledFlags);
        });
    }

    public <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getRecipeFor(RecipeType<T> type, I input, Level level, @Nullable ResourceKey<Recipe<?>> recipeHint) {
        RecipeHolder<T> recipeholder = recipeHint != null ? this.byKeyTyped(type, recipeHint) : null;

        return this.getRecipeFor(type, input, level, recipeholder);
    }

    public <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getRecipeFor(RecipeType<T> type, I input, Level level, @Nullable RecipeHolder<T> recipeHint) {
        return recipeHint != null && recipeHint.value().matches(input, level) ? Optional.of(recipeHint) : this.getRecipeFor(type, input, level);
    }

    public <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getRecipeFor(RecipeType<T> type, I input, Level level) {
        return this.recipes.getRecipesFor(type, input, level).findFirst();
    }

    public Optional<RecipeHolder<?>> byKey(ResourceKey<Recipe<?>> recipeId) {
        return Optional.ofNullable(this.recipes.byKey(recipeId));
    }

    private <T extends Recipe<?>> @Nullable RecipeHolder<T> byKeyTyped(RecipeType<T> type, ResourceKey<Recipe<?>> recipeId) {
        RecipeHolder<?> recipeholder = this.recipes.byKey(recipeId);

        return recipeholder != null && recipeholder.value().getType().equals(type) ? recipeholder : null;
    }

    public Map<ResourceKey<RecipePropertySet>, RecipePropertySet> getSynchronizedItemProperties() {
        return this.propertySets;
    }

    public SelectableRecipe.SingleInputSet<StonecutterRecipe> getSynchronizedStonecutterRecipes() {
        return this.stonecutterRecipes;
    }

    @Override
    public RecipePropertySet propertySet(ResourceKey<RecipePropertySet> id) {
        return (RecipePropertySet) this.propertySets.getOrDefault(id, RecipePropertySet.EMPTY);
    }

    @Override
    public SelectableRecipe.SingleInputSet<StonecutterRecipe> stonecutterRecipes() {
        return this.stonecutterRecipes;
    }

    public Collection<RecipeHolder<?>> getRecipes() {
        return this.recipes.values();
    }

    public RecipeManager.@Nullable ServerDisplayInfo getRecipeFromDisplay(RecipeDisplayId id) {
        int i = id.index();

        return i >= 0 && i < this.allDisplays.size() ? (RecipeManager.ServerDisplayInfo) this.allDisplays.get(i) : null;
    }

    public void listDisplaysForRecipe(ResourceKey<Recipe<?>> id, Consumer<RecipeDisplayEntry> output) {
        List<RecipeManager.ServerDisplayInfo> list = (List) this.recipeToDisplay.get(id);

        if (list != null) {
            list.forEach((recipemanager_serverdisplayinfo) -> {
                output.accept(recipemanager_serverdisplayinfo.display);
            });
        }

    }

    @VisibleForTesting
    protected static RecipeHolder<?> fromJson(ResourceKey<Recipe<?>> id, JsonObject object, HolderLookup.Provider registries) {
        Recipe<?> recipe = (Recipe) Recipe.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), object).getOrThrow(JsonParseException::new);

        return new RecipeHolder(id, recipe);
    }

    public static <I extends RecipeInput, T extends Recipe<I>> RecipeManager.CachedCheck<I, T> createCheck(final RecipeType<T> type) {
        return new RecipeManager.CachedCheck<I, T>() {
            private @Nullable ResourceKey<Recipe<?>> lastRecipe;

            @Override
            public Optional<RecipeHolder<T>> getRecipeFor(I input, ServerLevel level) {
                RecipeManager recipemanager = level.recipeAccess();
                Optional<RecipeHolder<T>> optional = recipemanager.getRecipeFor(type, input, level, this.lastRecipe);

                if (optional.isPresent()) {
                    RecipeHolder<T> recipeholder = (RecipeHolder) optional.get();

                    this.lastRecipe = recipeholder.id();
                    return Optional.of(recipeholder);
                } else {
                    return Optional.empty();
                }
            }
        };
    }

    private static List<RecipeManager.ServerDisplayInfo> unpackRecipeInfo(Iterable<RecipeHolder<?>> recipes, FeatureFlagSet enabledFeatures) {
        List<RecipeManager.ServerDisplayInfo> list = new ArrayList();
        Object2IntMap<String> object2intmap = new Object2IntOpenHashMap();

        for (RecipeHolder<?> recipeholder : recipes) {
            Recipe<?> recipe = recipeholder.value();
            OptionalInt optionalint;

            if (recipe.group().isEmpty()) {
                optionalint = OptionalInt.empty();
            } else {
                optionalint = OptionalInt.of(object2intmap.computeIfAbsent(recipe.group(), (object) -> {
                    return object2intmap.size();
                }));
            }

            Optional<List<Ingredient>> optional;

            if (recipe.isSpecial()) {
                optional = Optional.empty();
            } else {
                optional = Optional.of(recipe.placementInfo().ingredients());
            }

            for (RecipeDisplay recipedisplay : recipe.display()) {
                if (recipedisplay.isEnabled(enabledFeatures)) {
                    int i = list.size();
                    RecipeDisplayId recipedisplayid = new RecipeDisplayId(i);
                    RecipeDisplayEntry recipedisplayentry = new RecipeDisplayEntry(recipedisplayid, recipedisplay, optionalint, recipe.recipeBookCategory(), optional);

                    list.add(new RecipeManager.ServerDisplayInfo(recipedisplayentry, recipeholder));
                }
            }
        }

        return list;
    }

    private static RecipeManager.IngredientExtractor forSingleInput(RecipeType<? extends SingleItemRecipe> type) {
        return (recipe) -> {
            Optional optional;

            if (recipe.getType() == type && recipe instanceof SingleItemRecipe singleitemrecipe) {
                optional = Optional.of(singleitemrecipe.input());
            } else {
                optional = Optional.empty();
            }

            return optional;
        };
    }

    public static record ServerDisplayInfo(RecipeDisplayEntry display, RecipeHolder<?> parent) {

    }

    public static class IngredientCollector implements Consumer<Recipe<?>> {

        private final ResourceKey<RecipePropertySet> key;
        private final RecipeManager.IngredientExtractor extractor;
        private final List<Ingredient> ingredients = new ArrayList();

        protected IngredientCollector(ResourceKey<RecipePropertySet> key, RecipeManager.IngredientExtractor extractor) {
            this.key = key;
            this.extractor = extractor;
        }

        public void accept(Recipe<?> recipe) {
            Optional optional = this.extractor.apply(recipe);
            List list = this.ingredients;

            Objects.requireNonNull(this.ingredients);
            optional.ifPresent(list::add);
        }

        public RecipePropertySet asPropertySet(FeatureFlagSet enabledFeatures) {
            return RecipePropertySet.create(RecipeManager.filterDisabled(enabledFeatures, this.ingredients));
        }
    }

    public interface CachedCheck<I extends RecipeInput, T extends Recipe<I>> {

        Optional<RecipeHolder<T>> getRecipeFor(I input, ServerLevel level);
    }

    @FunctionalInterface
    public interface IngredientExtractor {

        Optional<Ingredient> apply(Recipe<?> recipe);
    }
}
