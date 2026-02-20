package net.minecraft.commands.arguments;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
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
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

public class ResourceOrTagKeyArgument<T> implements ArgumentType<ResourceOrTagKeyArgument.Result<T>> {

    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo:bar", "012", "#skeletons", "#minecraft:skeletons");
    private final ResourceKey<? extends Registry<T>> registryKey;

    public ResourceOrTagKeyArgument(ResourceKey<? extends Registry<T>> registryKey) {
        this.registryKey = registryKey;
    }

    public static <T> ResourceOrTagKeyArgument<T> resourceOrTagKey(ResourceKey<? extends Registry<T>> key) {
        return new ResourceOrTagKeyArgument<T>(key);
    }

    public static <T> ResourceOrTagKeyArgument.Result<T> getResourceOrTagKey(CommandContext<CommandSourceStack> context, String name, ResourceKey<Registry<T>> registryKey, DynamicCommandExceptionType exceptionType) throws CommandSyntaxException {
        ResourceOrTagKeyArgument.Result<?> resourceortagkeyargument_result = (ResourceOrTagKeyArgument.Result) context.getArgument(name, ResourceOrTagKeyArgument.Result.class);
        Optional<ResourceOrTagKeyArgument.Result<T>> optional = resourceortagkeyargument_result.cast(registryKey);

        return (ResourceOrTagKeyArgument.Result) optional.orElseThrow(() -> {
            return exceptionType.create(resourceortagkeyargument_result);
        });
    }

    public ResourceOrTagKeyArgument.Result<T> parse(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '#') {
            int i = reader.getCursor();

            try {
                reader.skip();
                Identifier identifier = Identifier.read(reader);

                return new ResourceOrTagKeyArgument.TagResult<T>(TagKey.create(this.registryKey, identifier));
            } catch (CommandSyntaxException commandsyntaxexception) {
                reader.setCursor(i);
                throw commandsyntaxexception;
            }
        } else {
            Identifier identifier1 = Identifier.read(reader);

            return new ResourceOrTagKeyArgument.ResourceResult<T>(ResourceKey.create(this.registryKey, identifier1));
        }
    }

    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.listSuggestions(context, builder, this.registryKey, SharedSuggestionProvider.ElementSuggestionType.ALL);
    }

    public Collection<String> getExamples() {
        return ResourceOrTagKeyArgument.EXAMPLES;
    }

    private static record ResourceResult<T>(ResourceKey<T> key) implements ResourceOrTagKeyArgument.Result<T> {

        @Override
        public Either<ResourceKey<T>, TagKey<T>> unwrap() {
            return Either.left(this.key);
        }

        @Override
        public <E> Optional<ResourceOrTagKeyArgument.Result<E>> cast(ResourceKey<? extends Registry<E>> registryKey) {
            return this.key.cast(registryKey).map(ResourceOrTagKeyArgument.ResourceResult::new);
        }

        public boolean test(Holder<T> holder) {
            return holder.is(this.key);
        }

        @Override
        public String asPrintable() {
            return this.key.identifier().toString();
        }
    }

    private static record TagResult<T>(TagKey<T> key) implements ResourceOrTagKeyArgument.Result<T> {

        @Override
        public Either<ResourceKey<T>, TagKey<T>> unwrap() {
            return Either.right(this.key);
        }

        @Override
        public <E> Optional<ResourceOrTagKeyArgument.Result<E>> cast(ResourceKey<? extends Registry<E>> registryKey) {
            return this.key.cast(registryKey).map(ResourceOrTagKeyArgument.TagResult::new);
        }

        public boolean test(Holder<T> holder) {
            return holder.is(this.key);
        }

        @Override
        public String asPrintable() {
            return "#" + String.valueOf(this.key.location());
        }
    }

    public static class Info<T> implements ArgumentTypeInfo<ResourceOrTagKeyArgument<T>, ResourceOrTagKeyArgument.Info<T>.Template> {

        public Info() {}

        public void serializeToNetwork(ResourceOrTagKeyArgument.Info<T>.Template template, FriendlyByteBuf out) {
            out.writeResourceKey(template.registryKey);
        }

        @Override
        public ResourceOrTagKeyArgument.Info<T>.Template deserializeFromNetwork(FriendlyByteBuf in) {
            return new ResourceOrTagKeyArgument.Info.Template(in.readRegistryKey());
        }

        public void serializeToJson(ResourceOrTagKeyArgument.Info<T>.Template template, JsonObject out) {
            out.addProperty("registry", template.registryKey.identifier().toString());
        }

        public ResourceOrTagKeyArgument.Info<T>.Template unpack(ResourceOrTagKeyArgument<T> argument) {
            return new ResourceOrTagKeyArgument.Info.Template(argument.registryKey);
        }

        public final class Template implements ArgumentTypeInfo.Template<ResourceOrTagKeyArgument<T>> {

            private final ResourceKey<? extends Registry<T>> registryKey;

            private Template(ResourceKey<? extends Registry<T>> registryKey) {
                this.registryKey = registryKey;
            }

            @Override
            public ResourceOrTagKeyArgument<T> instantiate(CommandBuildContext context) {
                return new ResourceOrTagKeyArgument<T>(this.registryKey);
            }

            @Override
            public ArgumentTypeInfo<ResourceOrTagKeyArgument<T>, ?> type() {
                return Info.this;
            }
        }
    }

    public interface Result<T> extends Predicate<Holder<T>> {

        Either<ResourceKey<T>, TagKey<T>> unwrap();

        <E> Optional<ResourceOrTagKeyArgument.Result<E>> cast(ResourceKey<? extends Registry<E>> registryKey);

        String asPrintable();
    }
}
