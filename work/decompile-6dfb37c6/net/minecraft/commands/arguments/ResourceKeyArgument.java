package net.minecraft.commands.arguments;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

public class ResourceKeyArgument<T> implements ArgumentType<ResourceKey<T>> {

    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo:bar", "012");
    private static final DynamicCommandExceptionType ERROR_INVALID_FEATURE = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.place.feature.invalid", object);
    });
    private static final DynamicCommandExceptionType ERROR_INVALID_STRUCTURE = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.place.structure.invalid", object);
    });
    private static final DynamicCommandExceptionType ERROR_INVALID_TEMPLATE_POOL = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.place.jigsaw.invalid", object);
    });
    private static final DynamicCommandExceptionType ERROR_INVALID_RECIPE = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("recipe.notFound", object);
    });
    private static final DynamicCommandExceptionType ERROR_INVALID_ADVANCEMENT = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("advancement.advancementNotFound", object);
    });
    private final ResourceKey<? extends Registry<T>> registryKey;

    public ResourceKeyArgument(ResourceKey<? extends Registry<T>> registryKey) {
        this.registryKey = registryKey;
    }

    public static <T> ResourceKeyArgument<T> key(ResourceKey<? extends Registry<T>> key) {
        return new ResourceKeyArgument<T>(key);
    }

    public static <T> ResourceKey<T> getRegistryKey(CommandContext<CommandSourceStack> context, String name, ResourceKey<Registry<T>> registryKey, DynamicCommandExceptionType exceptionType) throws CommandSyntaxException {
        ResourceKey<?> resourcekey1 = (ResourceKey) context.getArgument(name, ResourceKey.class);
        Optional<ResourceKey<T>> optional = resourcekey1.cast(registryKey);

        return (ResourceKey) optional.orElseThrow(() -> {
            return exceptionType.create(resourcekey1.identifier());
        });
    }

    private static <T> Registry<T> getRegistry(CommandContext<CommandSourceStack> context, ResourceKey<? extends Registry<T>> registryKey) {
        return ((CommandSourceStack) context.getSource()).getServer().registryAccess().lookupOrThrow(registryKey);
    }

    private static <T> Holder.Reference<T> resolveKey(CommandContext<CommandSourceStack> context, String name, ResourceKey<Registry<T>> registryKey, DynamicCommandExceptionType exception) throws CommandSyntaxException {
        ResourceKey<T> resourcekey1 = getRegistryKey(context, name, registryKey, exception);

        return (Holder.Reference) getRegistry(context, registryKey).get(resourcekey1).orElseThrow(() -> {
            return exception.create(resourcekey1.identifier());
        });
    }

    public static Holder.Reference<ConfiguredFeature<?, ?>> getConfiguredFeature(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return resolveKey(context, name, Registries.CONFIGURED_FEATURE, ResourceKeyArgument.ERROR_INVALID_FEATURE);
    }

    public static Holder.Reference<Structure> getStructure(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return resolveKey(context, name, Registries.STRUCTURE, ResourceKeyArgument.ERROR_INVALID_STRUCTURE);
    }

    public static Holder.Reference<StructureTemplatePool> getStructureTemplatePool(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return resolveKey(context, name, Registries.TEMPLATE_POOL, ResourceKeyArgument.ERROR_INVALID_TEMPLATE_POOL);
    }

    public static RecipeHolder<?> getRecipe(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        RecipeManager recipemanager = ((CommandSourceStack) context.getSource()).getServer().getRecipeManager();
        ResourceKey<Recipe<?>> resourcekey = getRegistryKey(context, name, Registries.RECIPE, ResourceKeyArgument.ERROR_INVALID_RECIPE);

        return (RecipeHolder) recipemanager.byKey(resourcekey).orElseThrow(() -> {
            return ResourceKeyArgument.ERROR_INVALID_RECIPE.create(resourcekey.identifier());
        });
    }

    public static AdvancementHolder getAdvancement(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ResourceKey<Advancement> resourcekey = getRegistryKey(context, name, Registries.ADVANCEMENT, ResourceKeyArgument.ERROR_INVALID_ADVANCEMENT);
        AdvancementHolder advancementholder = ((CommandSourceStack) context.getSource()).getServer().getAdvancements().get(resourcekey.identifier());

        if (advancementholder == null) {
            throw ResourceKeyArgument.ERROR_INVALID_ADVANCEMENT.create(resourcekey.identifier());
        } else {
            return advancementholder;
        }
    }

    public ResourceKey<T> parse(StringReader reader) throws CommandSyntaxException {
        Identifier identifier = Identifier.read(reader);

        return ResourceKey.create(this.registryKey, identifier);
    }

    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.listSuggestions(context, builder, this.registryKey, SharedSuggestionProvider.ElementSuggestionType.ELEMENTS);
    }

    public Collection<String> getExamples() {
        return ResourceKeyArgument.EXAMPLES;
    }

    public static class Info<T> implements ArgumentTypeInfo<ResourceKeyArgument<T>, ResourceKeyArgument.Info<T>.Template> {

        public Info() {}

        public void serializeToNetwork(ResourceKeyArgument.Info<T>.Template template, FriendlyByteBuf out) {
            out.writeResourceKey(template.registryKey);
        }

        @Override
        public ResourceKeyArgument.Info<T>.Template deserializeFromNetwork(FriendlyByteBuf in) {
            return new ResourceKeyArgument.Info.Template(in.readRegistryKey());
        }

        public void serializeToJson(ResourceKeyArgument.Info<T>.Template template, JsonObject out) {
            out.addProperty("registry", template.registryKey.identifier().toString());
        }

        public ResourceKeyArgument.Info<T>.Template unpack(ResourceKeyArgument<T> argument) {
            return new ResourceKeyArgument.Info.Template(argument.registryKey);
        }

        public final class Template implements ArgumentTypeInfo.Template<ResourceKeyArgument<T>> {

            private final ResourceKey<? extends Registry<T>> registryKey;

            private Template(ResourceKey<? extends Registry<T>> registryKey) {
                this.registryKey = registryKey;
            }

            @Override
            public ResourceKeyArgument<T> instantiate(CommandBuildContext context) {
                return new ResourceKeyArgument<T>(this.registryKey);
            }

            @Override
            public ArgumentTypeInfo<ResourceKeyArgument<T>, ?> type() {
                return Info.this;
            }
        }
    }
}
