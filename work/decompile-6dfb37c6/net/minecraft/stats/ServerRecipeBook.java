package net.minecraft.stats;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookRemovePacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookSettingsPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.slf4j.Logger;

public class ServerRecipeBook extends RecipeBook {

    public static final String RECIPE_BOOK_TAG = "recipeBook";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ServerRecipeBook.DisplayResolver displayResolver;
    @VisibleForTesting
    public final Set<ResourceKey<Recipe<?>>> known = Sets.newIdentityHashSet();
    @VisibleForTesting
    protected final Set<ResourceKey<Recipe<?>>> highlight = Sets.newIdentityHashSet();

    public ServerRecipeBook(ServerRecipeBook.DisplayResolver displayResolver) {
        this.displayResolver = displayResolver;
    }

    public void add(ResourceKey<Recipe<?>> id) {
        this.known.add(id);
    }

    public boolean contains(ResourceKey<Recipe<?>> id) {
        return this.known.contains(id);
    }

    public void remove(ResourceKey<Recipe<?>> id) {
        this.known.remove(id);
        this.highlight.remove(id);
    }

    public void removeHighlight(ResourceKey<Recipe<?>> id) {
        this.highlight.remove(id);
    }

    private void addHighlight(ResourceKey<Recipe<?>> id) {
        this.highlight.add(id);
    }

    public int addRecipes(Collection<RecipeHolder<?>> recipes, ServerPlayer player) {
        List<ClientboundRecipeBookAddPacket.Entry> list = new ArrayList();

        for (RecipeHolder<?> recipeholder : recipes) {
            ResourceKey<Recipe<?>> resourcekey = recipeholder.id();

            if (!this.known.contains(resourcekey) && !recipeholder.value().isSpecial()) {
                this.add(resourcekey);
                this.addHighlight(resourcekey);
                this.displayResolver.displaysForRecipe(resourcekey, (recipedisplayentry) -> {
                    list.add(new ClientboundRecipeBookAddPacket.Entry(recipedisplayentry, recipeholder.value().showNotification(), true));
                });
                CriteriaTriggers.RECIPE_UNLOCKED.trigger(player, recipeholder);
            }
        }

        if (!list.isEmpty()) {
            player.connection.send(new ClientboundRecipeBookAddPacket(list, false));
        }

        return list.size();
    }

    public int removeRecipes(Collection<RecipeHolder<?>> recipes, ServerPlayer player) {
        List<RecipeDisplayId> list = Lists.newArrayList();

        for (RecipeHolder<?> recipeholder : recipes) {
            ResourceKey<Recipe<?>> resourcekey = recipeholder.id();

            if (this.known.contains(resourcekey)) {
                this.remove(resourcekey);
                this.displayResolver.displaysForRecipe(resourcekey, (recipedisplayentry) -> {
                    list.add(recipedisplayentry.id());
                });
            }
        }

        if (!list.isEmpty()) {
            player.connection.send(new ClientboundRecipeBookRemovePacket(list));
        }

        return list.size();
    }

    private void loadRecipes(List<ResourceKey<Recipe<?>>> recipes, Consumer<ResourceKey<Recipe<?>>> recipeAddingMethod, Predicate<ResourceKey<Recipe<?>>> validator) {
        for (ResourceKey<Recipe<?>> resourcekey : recipes) {
            if (!validator.test(resourcekey)) {
                ServerRecipeBook.LOGGER.error("Tried to load unrecognized recipe: {} removed now.", resourcekey);
            } else {
                recipeAddingMethod.accept(resourcekey);
            }
        }

    }

    public void sendInitialRecipeBook(ServerPlayer player) {
        player.connection.send(new ClientboundRecipeBookSettingsPacket(this.getBookSettings().copy()));
        List<ClientboundRecipeBookAddPacket.Entry> list = new ArrayList(this.known.size());

        for (ResourceKey<Recipe<?>> resourcekey : this.known) {
            this.displayResolver.displaysForRecipe(resourcekey, (recipedisplayentry) -> {
                list.add(new ClientboundRecipeBookAddPacket.Entry(recipedisplayentry, false, this.highlight.contains(resourcekey)));
            });
        }

        player.connection.send(new ClientboundRecipeBookAddPacket(list, true));
    }

    public void copyOverData(ServerRecipeBook bookToCopy) {
        this.apply(bookToCopy.pack());
    }

    public ServerRecipeBook.Packed pack() {
        return new ServerRecipeBook.Packed(this.bookSettings.copy(), List.copyOf(this.known), List.copyOf(this.highlight));
    }

    private void apply(ServerRecipeBook.Packed packed) {
        this.known.clear();
        this.highlight.clear();
        this.bookSettings.replaceFrom(packed.settings);
        this.known.addAll(packed.known);
        this.highlight.addAll(packed.highlight);
    }

    public void loadUntrusted(ServerRecipeBook.Packed packed, Predicate<ResourceKey<Recipe<?>>> validator) {
        this.bookSettings.replaceFrom(packed.settings);
        List list = packed.known;
        Set set = this.known;

        Objects.requireNonNull(this.known);
        this.loadRecipes(list, set::add, validator);
        list = packed.highlight;
        set = this.highlight;
        Objects.requireNonNull(this.highlight);
        this.loadRecipes(list, set::add, validator);
    }

    public static record Packed(RecipeBookSettings settings, List<ResourceKey<Recipe<?>>> known, List<ResourceKey<Recipe<?>>> highlight) {

        public static final Codec<ServerRecipeBook.Packed> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(RecipeBookSettings.MAP_CODEC.forGetter(ServerRecipeBook.Packed::settings), Recipe.KEY_CODEC.listOf().fieldOf("recipes").forGetter(ServerRecipeBook.Packed::known), Recipe.KEY_CODEC.listOf().fieldOf("toBeDisplayed").forGetter(ServerRecipeBook.Packed::highlight)).apply(instance, ServerRecipeBook.Packed::new);
        });
    }

    @FunctionalInterface
    public interface DisplayResolver {

        void displaysForRecipe(ResourceKey<Recipe<?>> id, Consumer<RecipeDisplayEntry> output);
    }
}
