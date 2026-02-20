package net.minecraft.commands.arguments;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.datafixers.util.Either;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

public class ResourceOrTagArgument<T> implements ArgumentType<ResourceOrTagArgument.Result<T>> {

    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo:bar", "012", "#skeletons", "#minecraft:skeletons");
    private static final Dynamic2CommandExceptionType ERROR_UNKNOWN_TAG = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("argument.resource_tag.not_found", object, object1);
    });
    private static final Dynamic3CommandExceptionType ERROR_INVALID_TAG_TYPE = new Dynamic3CommandExceptionType((object, object1, object2) -> {
        return Component.translatableEscape("argument.resource_tag.invalid_type", object, object1, object2);
    });
    private final HolderLookup<T> registryLookup;
    private final ResourceKey<? extends Registry<T>> registryKey;

    public ResourceOrTagArgument(CommandBuildContext context, ResourceKey<? extends Registry<T>> registryKey) {
        this.registryKey = registryKey;
        this.registryLookup = context.lookupOrThrow(registryKey);
    }

    public static <T> ResourceOrTagArgument<T> resourceOrTag(CommandBuildContext context, ResourceKey<? extends Registry<T>> key) {
        return new ResourceOrTagArgument<T>(context, key);
    }

    public static <T> ResourceOrTagArgument.Result<T> getResourceOrTag(CommandContext<CommandSourceStack> context, String name, ResourceKey<Registry<T>> registryKey) throws CommandSyntaxException {
        ResourceOrTagArgument.Result<?> resourceortagargument_result = (ResourceOrTagArgument.Result) context.getArgument(name, ResourceOrTagArgument.Result.class);
        Optional<ResourceOrTagArgument.Result<T>> optional = resourceortagargument_result.cast(registryKey);

        return (ResourceOrTagArgument.Result) optional.orElseThrow(() -> {
            return (CommandSyntaxException) resourceortagargument_result.unwrap().map((holder_reference) -> {
                ResourceKey<?> resourcekey1 = holder_reference.key();

                return ResourceArgument.ERROR_INVALID_RESOURCE_TYPE.create(resourcekey1.identifier(), resourcekey1.registry(), registryKey.identifier());
            }, (holderset_named) -> {
                TagKey<?> tagkey = holderset_named.key();

                return ResourceOrTagArgument.ERROR_INVALID_TAG_TYPE.create(tagkey.location(), tagkey.registry(), registryKey.identifier());
            });
        });
    }

    public ResourceOrTagArgument.Result<T> parse(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '#') {
            int i = reader.getCursor();

            try {
                reader.skip();
                Identifier identifier = Identifier.read(reader);
                TagKey<T> tagkey = TagKey.<T>create(this.registryKey, identifier);
                HolderSet.Named<T> holderset_named = (HolderSet.Named) this.registryLookup.get(tagkey).orElseThrow(() -> {
                    return ResourceOrTagArgument.ERROR_UNKNOWN_TAG.createWithContext(reader, identifier, this.registryKey.identifier());
                });

                return new ResourceOrTagArgument.TagResult<T>(holderset_named);
            } catch (CommandSyntaxException commandsyntaxexception) {
                reader.setCursor(i);
                throw commandsyntaxexception;
            }
        } else {
            Identifier identifier1 = Identifier.read(reader);
            ResourceKey<T> resourcekey = ResourceKey.create(this.registryKey, identifier1);
            Holder.Reference<T> holder_reference = (Holder.Reference) this.registryLookup.get(resourcekey).orElseThrow(() -> {
                return ResourceArgument.ERROR_UNKNOWN_RESOURCE.createWithContext(reader, identifier1, this.registryKey.identifier());
            });

            return new ResourceOrTagArgument.ResourceResult<T>(holder_reference);
        }
    }

    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.listSuggestions(context, builder, this.registryKey, SharedSuggestionProvider.ElementSuggestionType.ALL);
    }

    public Collection<String> getExamples() {
        return ResourceOrTagArgument.EXAMPLES;
    }

    private static record ResourceResult<T>(Holder.Reference<T> value) implements ResourceOrTagArgument.Result<T> {

        @Override
        public Either<Holder.Reference<T>, HolderSet.Named<T>> unwrap() {
            return Either.left(this.value);
        }

        @Override
        public <E> Optional<ResourceOrTagArgument.Result<E>> cast(ResourceKey<? extends Registry<E>> registryKey) {
            return this.value.key().isFor(registryKey) ? Optional.of(this) : Optional.empty();
        }

        public boolean test(Holder<T> holder) {
            return holder.equals(this.value);
        }

        @Override
        public String asPrintable() {
            return this.value.key().identifier().toString();
        }
    }

    private static record TagResult<T>(HolderSet.Named<T> tag) implements ResourceOrTagArgument.Result<T> {

        @Override
        public Either<Holder.Reference<T>, HolderSet.Named<T>> unwrap() {
            return Either.right(this.tag);
        }

        @Override
        public <E> Optional<ResourceOrTagArgument.Result<E>> cast(ResourceKey<? extends Registry<E>> registryKey) {
            return this.tag.key().isFor(registryKey) ? Optional.of(this) : Optional.empty();
        }

        public boolean test(Holder<T> holder) {
            return this.tag.contains(holder);
        }

        @Override
        public String asPrintable() {
            return "#" + String.valueOf(this.tag.key().location());
        }
    }

    public static class Info<T> implements ArgumentTypeInfo<ResourceOrTagArgument<T>, ResourceOrTagArgument.Info<T>.Template> {

        public Info() {}

        public void serializeToNetwork(ResourceOrTagArgument.Info<T>.Template template, FriendlyByteBuf out) {
            out.writeResourceKey(template.registryKey);
        }

        @Override
        public ResourceOrTagArgument.Info<T>.Template deserializeFromNetwork(FriendlyByteBuf in) {
            return new ResourceOrTagArgument.Info.Template(in.readRegistryKey());
        }

        public void serializeToJson(ResourceOrTagArgument.Info<T>.Template template, JsonObject out) {
            out.addProperty("registry", template.registryKey.identifier().toString());
        }

        public ResourceOrTagArgument.Info<T>.Template unpack(ResourceOrTagArgument<T> argument) {
            return new ResourceOrTagArgument.Info.Template(argument.registryKey);
        }

        public final class Template implements ArgumentTypeInfo.Template<ResourceOrTagArgument<T>> {

            private final ResourceKey<? extends Registry<T>> registryKey;

            private Template(ResourceKey<? extends Registry<T>> registryKey) {
                this.registryKey = registryKey;
            }

            @Override
            public ResourceOrTagArgument<T> instantiate(CommandBuildContext context) {
                return new ResourceOrTagArgument<T>(context, this.registryKey);
            }

            @Override
            public ArgumentTypeInfo<ResourceOrTagArgument<T>, ?> type() {
                return Info.this;
            }
        }
    }

    public interface Result<T> extends Predicate<Holder<T>> {

        Either<Holder.Reference<T>, HolderSet.Named<T>> unwrap();

        <E> Optional<ResourceOrTagArgument.Result<E>> cast(ResourceKey<? extends Registry<E>> registryKey);

        String asPrintable();
    }
}
