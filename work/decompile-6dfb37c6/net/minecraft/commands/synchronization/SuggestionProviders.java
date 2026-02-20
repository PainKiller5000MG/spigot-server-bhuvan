package net.minecraft.commands.synchronization;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

public class SuggestionProviders {

    private static final Map<Identifier, SuggestionProvider<SharedSuggestionProvider>> PROVIDERS_BY_NAME = new HashMap();
    private static final Identifier ID_ASK_SERVER = Identifier.withDefaultNamespace("ask_server");
    public static final SuggestionProvider<SharedSuggestionProvider> ASK_SERVER = register(SuggestionProviders.ID_ASK_SERVER, (commandcontext, suggestionsbuilder) -> {
        return ((SharedSuggestionProvider) commandcontext.getSource()).customSuggestion(commandcontext);
    });
    public static final SuggestionProvider<SharedSuggestionProvider> AVAILABLE_SOUNDS = register(Identifier.withDefaultNamespace("available_sounds"), (commandcontext, suggestionsbuilder) -> {
        return SharedSuggestionProvider.suggestResource(((SharedSuggestionProvider) commandcontext.getSource()).getAvailableSounds(), suggestionsbuilder);
    });
    public static final SuggestionProvider<SharedSuggestionProvider> SUMMONABLE_ENTITIES = register(Identifier.withDefaultNamespace("summonable_entities"), (commandcontext, suggestionsbuilder) -> {
        return SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.stream().filter((entitytype) -> {
            return entitytype.isEnabled(((SharedSuggestionProvider) commandcontext.getSource()).enabledFeatures()) && entitytype.canSummon();
        }), suggestionsbuilder, EntityType::getKey, EntityType::getDescription);
    });

    public SuggestionProviders() {}

    public static <S extends SharedSuggestionProvider> SuggestionProvider<S> register(Identifier name, SuggestionProvider<SharedSuggestionProvider> provider) {
        SuggestionProvider<SharedSuggestionProvider> suggestionprovider1 = (SuggestionProvider) SuggestionProviders.PROVIDERS_BY_NAME.putIfAbsent(name, provider);

        if (suggestionprovider1 != null) {
            throw new IllegalArgumentException("A command suggestion provider is already registered with the name '" + String.valueOf(name) + "'");
        } else {
            return new SuggestionProviders.RegisteredSuggestion(name, provider);
        }
    }

    public static <S extends SharedSuggestionProvider> SuggestionProvider<S> cast(SuggestionProvider<SharedSuggestionProvider> provider) {
        return provider;
    }

    public static <S extends SharedSuggestionProvider> SuggestionProvider<S> getProvider(Identifier name) {
        return cast((SuggestionProvider) SuggestionProviders.PROVIDERS_BY_NAME.getOrDefault(name, SuggestionProviders.ASK_SERVER));
    }

    public static Identifier getName(SuggestionProvider<?> provider) {
        Identifier identifier;

        if (provider instanceof SuggestionProviders.RegisteredSuggestion suggestionproviders_registeredsuggestion) {
            identifier = suggestionproviders_registeredsuggestion.name;
        } else {
            identifier = SuggestionProviders.ID_ASK_SERVER;
        }

        return identifier;
    }

    private static record RegisteredSuggestion(Identifier name, SuggestionProvider<SharedSuggestionProvider> delegate) implements SuggestionProvider<SharedSuggestionProvider> {

        public CompletableFuture<Suggestions> getSuggestions(CommandContext<SharedSuggestionProvider> context, SuggestionsBuilder builder) throws CommandSyntaxException {
            return this.delegate.getSuggestions(context, builder);
        }
    }
}
