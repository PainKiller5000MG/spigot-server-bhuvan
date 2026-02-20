package net.minecraft.commands;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.permissions.PermissionSetSupplier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.Level;

public interface SharedSuggestionProvider extends PermissionSetSupplier {

    CharMatcher MATCH_SPLITTER = CharMatcher.anyOf("._/");

    Collection<String> getOnlinePlayerNames();

    default Collection<String> getCustomTabSugggestions() {
        return this.getOnlinePlayerNames();
    }

    default Collection<String> getSelectedEntities() {
        return Collections.emptyList();
    }

    Collection<String> getAllTeams();

    Stream<Identifier> getAvailableSounds();

    CompletableFuture<Suggestions> customSuggestion(CommandContext<?> context);

    default Collection<SharedSuggestionProvider.TextCoordinates> getRelevantCoordinates() {
        return Collections.singleton(SharedSuggestionProvider.TextCoordinates.DEFAULT_GLOBAL);
    }

    default Collection<SharedSuggestionProvider.TextCoordinates> getAbsoluteCoordinates() {
        return Collections.singleton(SharedSuggestionProvider.TextCoordinates.DEFAULT_GLOBAL);
    }

    Set<ResourceKey<Level>> levels();

    RegistryAccess registryAccess();

    FeatureFlagSet enabledFeatures();

    default void suggestRegistryElements(HolderLookup<?> registry, SharedSuggestionProvider.ElementSuggestionType elements, SuggestionsBuilder builder) {
        if (elements.shouldSuggestTags()) {
            suggestResource(registry.listTagIds().map(TagKey::location), builder, "#");
        }

        if (elements.shouldSuggestElements()) {
            suggestResource(registry.listElementIds().map(ResourceKey::identifier), builder);
        }

    }

    static <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder, ResourceKey<? extends Registry<?>> registryKey, SharedSuggestionProvider.ElementSuggestionType type) {
        Object object = context.getSource();

        if (object instanceof SharedSuggestionProvider sharedsuggestionprovider) {
            return sharedsuggestionprovider.suggestRegistryElements(registryKey, type, builder, context);
        } else {
            return builder.buildFuture();
        }
    }

    CompletableFuture<Suggestions> suggestRegistryElements(ResourceKey<? extends Registry<?>> key, SharedSuggestionProvider.ElementSuggestionType elements, SuggestionsBuilder builder, CommandContext<?> context);

    static <T> void filterResources(Iterable<T> values, String contents, Function<T, Identifier> converter, Consumer<T> consumer) {
        boolean flag = contents.indexOf(58) > -1;

        for (T t0 : values) {
            Identifier identifier = (Identifier) converter.apply(t0);

            if (flag) {
                String s1 = identifier.toString();

                if (matchesSubStr(contents, s1)) {
                    consumer.accept(t0);
                }
            } else if (matchesSubStr(contents, identifier.getNamespace()) || matchesSubStr(contents, identifier.getPath())) {
                consumer.accept(t0);
            }
        }

    }

    static <T> void filterResources(Iterable<T> values, String contents, String prefix, Function<T, Identifier> converter, Consumer<T> consumer) {
        if (contents.isEmpty()) {
            values.forEach(consumer);
        } else {
            String s2 = Strings.commonPrefix(contents, prefix);

            if (!s2.isEmpty()) {
                String s3 = contents.substring(s2.length());

                filterResources(values, s3, converter, consumer);
            }
        }

    }

    static CompletableFuture<Suggestions> suggestResource(Iterable<Identifier> values, SuggestionsBuilder builder, String prefix) {
        String s1 = builder.getRemaining().toLowerCase(Locale.ROOT);

        filterResources(values, s1, prefix, (identifier) -> {
            return identifier;
        }, (identifier) -> {
            builder.suggest(prefix + String.valueOf(identifier));
        });
        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggestResource(Stream<Identifier> values, SuggestionsBuilder builder, String prefix) {
        Objects.requireNonNull(values);
        return suggestResource(values::iterator, builder, prefix);
    }

    static CompletableFuture<Suggestions> suggestResource(Iterable<Identifier> values, SuggestionsBuilder builder) {
        String s = builder.getRemaining().toLowerCase(Locale.ROOT);

        filterResources(values, s, (identifier) -> {
            return identifier;
        }, (identifier) -> {
            builder.suggest(identifier.toString());
        });
        return builder.buildFuture();
    }

    static <T> CompletableFuture<Suggestions> suggestResource(Iterable<T> values, SuggestionsBuilder builder, Function<T, Identifier> id, Function<T, Message> tooltip) {
        String s = builder.getRemaining().toLowerCase(Locale.ROOT);

        filterResources(values, s, id, (object) -> {
            builder.suggest(((Identifier) id.apply(object)).toString(), (Message) tooltip.apply(object));
        });
        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggestResource(Stream<Identifier> values, SuggestionsBuilder builder) {
        Objects.requireNonNull(values);
        return suggestResource(values::iterator, builder);
    }

    static <T> CompletableFuture<Suggestions> suggestResource(Stream<T> values, SuggestionsBuilder builder, Function<T, Identifier> id, Function<T, Message> tooltip) {
        Objects.requireNonNull(values);
        return suggestResource(values::iterator, builder, id, tooltip);
    }

    static CompletableFuture<Suggestions> suggestCoordinates(String currentInput, Collection<SharedSuggestionProvider.TextCoordinates> allSuggestions, SuggestionsBuilder builder, Predicate<String> validator) {
        List<String> list = Lists.newArrayList();

        if (Strings.isNullOrEmpty(currentInput)) {
            for (SharedSuggestionProvider.TextCoordinates sharedsuggestionprovider_textcoordinates : allSuggestions) {
                String s1 = sharedsuggestionprovider_textcoordinates.x + " " + sharedsuggestionprovider_textcoordinates.y + " " + sharedsuggestionprovider_textcoordinates.z;

                if (validator.test(s1)) {
                    list.add(sharedsuggestionprovider_textcoordinates.x);
                    list.add(sharedsuggestionprovider_textcoordinates.x + " " + sharedsuggestionprovider_textcoordinates.y);
                    list.add(s1);
                }
            }
        } else {
            String[] astring = currentInput.split(" ");

            if (astring.length == 1) {
                for (SharedSuggestionProvider.TextCoordinates sharedsuggestionprovider_textcoordinates1 : allSuggestions) {
                    String s2 = astring[0] + " " + sharedsuggestionprovider_textcoordinates1.y + " " + sharedsuggestionprovider_textcoordinates1.z;

                    if (validator.test(s2)) {
                        list.add(astring[0] + " " + sharedsuggestionprovider_textcoordinates1.y);
                        list.add(s2);
                    }
                }
            } else if (astring.length == 2) {
                for (SharedSuggestionProvider.TextCoordinates sharedsuggestionprovider_textcoordinates2 : allSuggestions) {
                    String s3 = astring[0] + " " + astring[1] + " " + sharedsuggestionprovider_textcoordinates2.z;

                    if (validator.test(s3)) {
                        list.add(s3);
                    }
                }
            }
        }

        return suggest(list, builder);
    }

    static CompletableFuture<Suggestions> suggest2DCoordinates(String currentInput, Collection<SharedSuggestionProvider.TextCoordinates> allSuggestions, SuggestionsBuilder builder, Predicate<String> validator) {
        List<String> list = Lists.newArrayList();

        if (Strings.isNullOrEmpty(currentInput)) {
            for (SharedSuggestionProvider.TextCoordinates sharedsuggestionprovider_textcoordinates : allSuggestions) {
                String s1 = sharedsuggestionprovider_textcoordinates.x + " " + sharedsuggestionprovider_textcoordinates.z;

                if (validator.test(s1)) {
                    list.add(sharedsuggestionprovider_textcoordinates.x);
                    list.add(s1);
                }
            }
        } else {
            String[] astring = currentInput.split(" ");

            if (astring.length == 1) {
                for (SharedSuggestionProvider.TextCoordinates sharedsuggestionprovider_textcoordinates1 : allSuggestions) {
                    String s2 = astring[0] + " " + sharedsuggestionprovider_textcoordinates1.z;

                    if (validator.test(s2)) {
                        list.add(s2);
                    }
                }
            }
        }

        return suggest(list, builder);
    }

    static CompletableFuture<Suggestions> suggest(Iterable<String> values, SuggestionsBuilder builder) {
        String s = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (String s1 : values) {
            if (matchesSubStr(s, s1.toLowerCase(Locale.ROOT))) {
                builder.suggest(s1);
            }
        }

        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggest(Stream<String> values, SuggestionsBuilder builder) {
        String s = builder.getRemaining().toLowerCase(Locale.ROOT);
        Stream stream1 = values.filter((s1) -> {
            return matchesSubStr(s, s1.toLowerCase(Locale.ROOT));
        });

        Objects.requireNonNull(builder);
        stream1.forEach(builder::suggest);
        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggest(String[] values, SuggestionsBuilder builder) {
        String s = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (String s1 : values) {
            if (matchesSubStr(s, s1.toLowerCase(Locale.ROOT))) {
                builder.suggest(s1);
            }
        }

        return builder.buildFuture();
    }

    static <T> CompletableFuture<Suggestions> suggest(Iterable<T> values, SuggestionsBuilder builder, Function<T, String> toString, Function<T, Message> tooltip) {
        String s = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (T t0 : values) {
            String s1 = (String) toString.apply(t0);

            if (matchesSubStr(s, s1.toLowerCase(Locale.ROOT))) {
                builder.suggest(s1, (Message) tooltip.apply(t0));
            }
        }

        return builder.buildFuture();
    }

    static boolean matchesSubStr(String pattern, String input) {
        int i;

        for (int j = 0; !input.startsWith(pattern, j); j = i + 1) {
            i = SharedSuggestionProvider.MATCH_SPLITTER.indexIn(input, j);
            if (i < 0) {
                return false;
            }
        }

        return true;
    }

    public static class TextCoordinates {

        public static final SharedSuggestionProvider.TextCoordinates DEFAULT_LOCAL = new SharedSuggestionProvider.TextCoordinates("^", "^", "^");
        public static final SharedSuggestionProvider.TextCoordinates DEFAULT_GLOBAL = new SharedSuggestionProvider.TextCoordinates("~", "~", "~");
        public final String x;
        public final String y;
        public final String z;

        public TextCoordinates(String x, String y, String z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static enum ElementSuggestionType {

        TAGS, ELEMENTS, ALL;

        private ElementSuggestionType() {}

        public boolean shouldSuggestTags() {
            return this == SharedSuggestionProvider.ElementSuggestionType.TAGS || this == SharedSuggestionProvider.ElementSuggestionType.ALL;
        }

        public boolean shouldSuggestElements() {
            return this == SharedSuggestionProvider.ElementSuggestionType.ELEMENTS || this == SharedSuggestionProvider.ElementSuggestionType.ALL;
        }
    }
}
