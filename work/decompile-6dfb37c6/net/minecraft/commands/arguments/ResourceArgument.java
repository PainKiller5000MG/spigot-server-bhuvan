package net.minecraft.commands.arguments;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.Structure;

public class ResourceArgument<T> implements ArgumentType<Holder.Reference<T>> {

    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo:bar", "012");
    private static final DynamicCommandExceptionType ERROR_NOT_SUMMONABLE_ENTITY = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("entity.not_summonable", object);
    });
    public static final Dynamic2CommandExceptionType ERROR_UNKNOWN_RESOURCE = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("argument.resource.not_found", object, object1);
    });
    public static final Dynamic3CommandExceptionType ERROR_INVALID_RESOURCE_TYPE = new Dynamic3CommandExceptionType((object, object1, object2) -> {
        return Component.translatableEscape("argument.resource.invalid_type", object, object1, object2);
    });
    private final ResourceKey<? extends Registry<T>> registryKey;
    private final HolderLookup<T> registryLookup;

    public ResourceArgument(CommandBuildContext context, ResourceKey<? extends Registry<T>> registryKey) {
        this.registryKey = registryKey;
        this.registryLookup = context.lookupOrThrow(registryKey);
    }

    public static <T> ResourceArgument<T> resource(CommandBuildContext context, ResourceKey<? extends Registry<T>> key) {
        return new ResourceArgument<T>(context, key);
    }

    public static <T> Holder.Reference<T> getResource(CommandContext<CommandSourceStack> context, String name, ResourceKey<Registry<T>> registryKey) throws CommandSyntaxException {
        Holder.Reference<T> holder_reference = (Holder.Reference) context.getArgument(name, Holder.Reference.class);
        ResourceKey<?> resourcekey1 = holder_reference.key();

        if (resourcekey1.isFor(registryKey)) {
            return holder_reference;
        } else {
            throw ResourceArgument.ERROR_INVALID_RESOURCE_TYPE.create(resourcekey1.identifier(), resourcekey1.registry(), registryKey.identifier());
        }
    }

    public static Holder.Reference<Attribute> getAttribute(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return getResource(context, name, Registries.ATTRIBUTE);
    }

    public static Holder.Reference<ConfiguredFeature<?, ?>> getConfiguredFeature(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return getResource(context, name, Registries.CONFIGURED_FEATURE);
    }

    public static Holder.Reference<Structure> getStructure(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return getResource(context, name, Registries.STRUCTURE);
    }

    public static Holder.Reference<EntityType<?>> getEntityType(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return getResource(context, name, Registries.ENTITY_TYPE);
    }

    public static Holder.Reference<EntityType<?>> getSummonableEntityType(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        Holder.Reference<EntityType<?>> holder_reference = getResource(context, name, Registries.ENTITY_TYPE);

        if (!((EntityType) holder_reference.value()).canSummon()) {
            throw ResourceArgument.ERROR_NOT_SUMMONABLE_ENTITY.create(holder_reference.key().identifier().toString());
        } else {
            return holder_reference;
        }
    }

    public static Holder.Reference<MobEffect> getMobEffect(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return getResource(context, name, Registries.MOB_EFFECT);
    }

    public static Holder.Reference<Enchantment> getEnchantment(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return getResource(context, name, Registries.ENCHANTMENT);
    }

    public Holder.Reference<T> parse(StringReader reader) throws CommandSyntaxException {
        Identifier identifier = Identifier.read(reader);
        ResourceKey<T> resourcekey = ResourceKey.create(this.registryKey, identifier);

        return (Holder.Reference) this.registryLookup.get(resourcekey).orElseThrow(() -> {
            return ResourceArgument.ERROR_UNKNOWN_RESOURCE.createWithContext(reader, identifier, this.registryKey.identifier());
        });
    }

    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.listSuggestions(context, builder, this.registryKey, SharedSuggestionProvider.ElementSuggestionType.ELEMENTS);
    }

    public Collection<String> getExamples() {
        return ResourceArgument.EXAMPLES;
    }

    public static class Info<T> implements ArgumentTypeInfo<ResourceArgument<T>, ResourceArgument.Info<T>.Template> {

        public Info() {}

        public void serializeToNetwork(ResourceArgument.Info<T>.Template template, FriendlyByteBuf out) {
            out.writeResourceKey(template.registryKey);
        }

        @Override
        public ResourceArgument.Info<T>.Template deserializeFromNetwork(FriendlyByteBuf in) {
            return new ResourceArgument.Info.Template(in.readRegistryKey());
        }

        public void serializeToJson(ResourceArgument.Info<T>.Template template, JsonObject out) {
            out.addProperty("registry", template.registryKey.identifier().toString());
        }

        public ResourceArgument.Info<T>.Template unpack(ResourceArgument<T> argument) {
            return new ResourceArgument.Info.Template(argument.registryKey);
        }

        public final class Template implements ArgumentTypeInfo.Template<ResourceArgument<T>> {

            private final ResourceKey<? extends Registry<T>> registryKey;

            private Template(ResourceKey<? extends Registry<T>> registryKey) {
                this.registryKey = registryKey;
            }

            @Override
            public ResourceArgument<T> instantiate(CommandBuildContext context) {
                return new ResourceArgument<T>(context, this.registryKey);
            }

            @Override
            public ArgumentTypeInfo<ResourceArgument<T>, ?> type() {
                return Info.this;
            }
        }
    }
}
