package net.minecraft.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.DataResult;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;

public class ParticleArgument implements ArgumentType<ParticleOptions> {

    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo:bar", "particle{foo:bar}");
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_PARTICLE = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("particle.notFound", object);
    });
    public static final DynamicCommandExceptionType ERROR_INVALID_OPTIONS = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("particle.invalidOptions", object);
    });
    private final HolderLookup.Provider registries;
    private static final TagParser<?> VALUE_PARSER = TagParser.create(NbtOps.INSTANCE);

    public ParticleArgument(CommandBuildContext context) {
        this.registries = context;
    }

    public static ParticleArgument particle(CommandBuildContext context) {
        return new ParticleArgument(context);
    }

    public static ParticleOptions getParticle(CommandContext<CommandSourceStack> context, String name) {
        return (ParticleOptions) context.getArgument(name, ParticleOptions.class);
    }

    public ParticleOptions parse(StringReader reader) throws CommandSyntaxException {
        return readParticle(reader, this.registries);
    }

    public Collection<String> getExamples() {
        return ParticleArgument.EXAMPLES;
    }

    public static ParticleOptions readParticle(StringReader reader, HolderLookup.Provider registries) throws CommandSyntaxException {
        ParticleType<?> particletype = readParticleType(reader, registries.lookupOrThrow(Registries.PARTICLE_TYPE));

        return readParticle(ParticleArgument.VALUE_PARSER, reader, particletype, registries);
    }

    private static ParticleType<?> readParticleType(StringReader reader, HolderLookup<ParticleType<?>> particles) throws CommandSyntaxException {
        Identifier identifier = Identifier.read(reader);
        ResourceKey<ParticleType<?>> resourcekey = ResourceKey.create(Registries.PARTICLE_TYPE, identifier);

        return (ParticleType) ((Holder.Reference) particles.get(resourcekey).orElseThrow(() -> {
            return ParticleArgument.ERROR_UNKNOWN_PARTICLE.createWithContext(reader, identifier);
        })).value();
    }

    private static <T extends ParticleOptions, O> T readParticle(TagParser<O> parser, StringReader reader, ParticleType<T> type, HolderLookup.Provider registries) throws CommandSyntaxException {
        RegistryOps<O> registryops = registries.<O>createSerializationContext(parser.getOps());
        O o0;

        if (reader.canRead() && reader.peek() == '{') {
            o0 = parser.parseAsArgument(reader);
        } else {
            o0 = registryops.emptyMap();
        }

        DataResult dataresult = type.codec().codec().parse(registryops, o0);
        DynamicCommandExceptionType dynamiccommandexceptiontype = ParticleArgument.ERROR_INVALID_OPTIONS;

        Objects.requireNonNull(dynamiccommandexceptiontype);
        return (T) (dataresult.getOrThrow(dynamiccommandexceptiontype::create));
    }

    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        HolderLookup.RegistryLookup<ParticleType<?>> holderlookup_registrylookup = this.registries.lookupOrThrow(Registries.PARTICLE_TYPE);

        return SharedSuggestionProvider.suggestResource(holderlookup_registrylookup.listElementIds().map(ResourceKey::identifier), builder);
    }
}
