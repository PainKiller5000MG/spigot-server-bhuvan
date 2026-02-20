package net.minecraft.commands.arguments.item;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.mutable.MutableObject;

public class ItemParser {

    private static final DynamicCommandExceptionType ERROR_UNKNOWN_ITEM = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("argument.item.id.invalid", object);
    });
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_COMPONENT = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("arguments.item.component.unknown", object);
    });
    private static final Dynamic2CommandExceptionType ERROR_MALFORMED_COMPONENT = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("arguments.item.component.malformed", object, object1);
    });
    private static final SimpleCommandExceptionType ERROR_EXPECTED_COMPONENT = new SimpleCommandExceptionType(Component.translatable("arguments.item.component.expected"));
    private static final DynamicCommandExceptionType ERROR_REPEATED_COMPONENT = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("arguments.item.component.repeated", object);
    });
    private static final DynamicCommandExceptionType ERROR_MALFORMED_ITEM = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("arguments.item.malformed", object);
    });
    public static final char SYNTAX_START_COMPONENTS = '[';
    public static final char SYNTAX_END_COMPONENTS = ']';
    public static final char SYNTAX_COMPONENT_SEPARATOR = ',';
    public static final char SYNTAX_COMPONENT_ASSIGNMENT = '=';
    public static final char SYNTAX_REMOVED_COMPONENT = '!';
    private static final Function<SuggestionsBuilder, CompletableFuture<Suggestions>> SUGGEST_NOTHING = SuggestionsBuilder::buildFuture;
    private final HolderLookup.RegistryLookup<Item> items;
    private final RegistryOps<Tag> registryOps;
    private final TagParser<Tag> tagParser;

    public ItemParser(HolderLookup.Provider registries) {
        this.items = registries.lookupOrThrow(Registries.ITEM);
        this.registryOps = registries.<Tag>createSerializationContext(NbtOps.INSTANCE);
        this.tagParser = TagParser.<Tag>create(this.registryOps);
    }

    public ItemParser.ItemResult parse(StringReader reader) throws CommandSyntaxException {
        final MutableObject<Holder<Item>> mutableobject = new MutableObject();
        final DataComponentPatch.Builder datacomponentpatch_builder = DataComponentPatch.builder();

        this.parse(reader, new ItemParser.Visitor() {
            @Override
            public void visitItem(Holder<Item> item) {
                mutableobject.setValue(item);
            }

            @Override
            public <T> void visitComponent(DataComponentType<T> type, T value) {
                datacomponentpatch_builder.set(type, value);
            }

            @Override
            public <T> void visitRemovedComponent(DataComponentType<T> type) {
                datacomponentpatch_builder.remove(type);
            }
        });
        Holder<Item> holder = (Holder) Objects.requireNonNull((Holder) mutableobject.get(), "Parser gave no item");
        DataComponentPatch datacomponentpatch = datacomponentpatch_builder.build();

        validateComponents(reader, holder, datacomponentpatch);
        return new ItemParser.ItemResult(holder, datacomponentpatch);
    }

    private static void validateComponents(StringReader reader, Holder<Item> item, DataComponentPatch components) throws CommandSyntaxException {
        DataComponentMap datacomponentmap = PatchedDataComponentMap.fromPatch(((Item) item.value()).components(), components);
        DataResult<Unit> dataresult = ItemStack.validateComponents(datacomponentmap);

        dataresult.getOrThrow((s) -> {
            return ItemParser.ERROR_MALFORMED_ITEM.createWithContext(reader, s);
        });
    }

    public void parse(StringReader reader, ItemParser.Visitor visitor) throws CommandSyntaxException {
        int i = reader.getCursor();

        try {
            (new ItemParser.State(reader, visitor)).parse();
        } catch (CommandSyntaxException commandsyntaxexception) {
            reader.setCursor(i);
            throw commandsyntaxexception;
        }
    }

    public CompletableFuture<Suggestions> fillSuggestions(SuggestionsBuilder builder) {
        StringReader stringreader = new StringReader(builder.getInput());

        stringreader.setCursor(builder.getStart());
        ItemParser.SuggestionsVisitor itemparser_suggestionsvisitor = new ItemParser.SuggestionsVisitor();
        ItemParser.State itemparser_state = new ItemParser.State(stringreader, itemparser_suggestionsvisitor);

        try {
            itemparser_state.parse();
        } catch (CommandSyntaxException commandsyntaxexception) {
            ;
        }

        return itemparser_suggestionsvisitor.resolveSuggestions(builder, stringreader);
    }

    private class State {

        private final StringReader reader;
        private final ItemParser.Visitor visitor;

        private State(StringReader reader, ItemParser.Visitor visitor) {
            this.reader = reader;
            this.visitor = visitor;
        }

        public void parse() throws CommandSyntaxException {
            this.visitor.visitSuggestions(this::suggestItem);
            this.readItem();
            this.visitor.visitSuggestions(this::suggestStartComponents);
            if (this.reader.canRead() && this.reader.peek() == '[') {
                this.visitor.visitSuggestions(ItemParser.SUGGEST_NOTHING);
                this.readComponents();
            }

        }

        private void readItem() throws CommandSyntaxException {
            int i = this.reader.getCursor();
            Identifier identifier = Identifier.read(this.reader);

            this.visitor.visitItem((Holder) ItemParser.this.items.get(ResourceKey.create(Registries.ITEM, identifier)).orElseThrow(() -> {
                this.reader.setCursor(i);
                return ItemParser.ERROR_UNKNOWN_ITEM.createWithContext(this.reader, identifier);
            }));
        }

        private void readComponents() throws CommandSyntaxException {
            this.reader.expect('[');
            this.visitor.visitSuggestions(this::suggestComponentAssignmentOrRemoval);
            Set<DataComponentType<?>> set = new ReferenceArraySet();

            while (this.reader.canRead() && this.reader.peek() != ']') {
                this.reader.skipWhitespace();
                if (this.reader.canRead() && this.reader.peek() == '!') {
                    this.reader.skip();
                    this.visitor.visitSuggestions(this::suggestComponent);
                    DataComponentType<?> datacomponenttype = readComponentType(this.reader);

                    if (!set.add(datacomponenttype)) {
                        throw ItemParser.ERROR_REPEATED_COMPONENT.create(datacomponenttype);
                    }

                    this.visitor.visitRemovedComponent(datacomponenttype);
                    this.visitor.visitSuggestions(ItemParser.SUGGEST_NOTHING);
                    this.reader.skipWhitespace();
                } else {
                    DataComponentType<?> datacomponenttype1 = readComponentType(this.reader);

                    if (!set.add(datacomponenttype1)) {
                        throw ItemParser.ERROR_REPEATED_COMPONENT.create(datacomponenttype1);
                    }

                    this.visitor.visitSuggestions(this::suggestAssignment);
                    this.reader.skipWhitespace();
                    this.reader.expect('=');
                    this.visitor.visitSuggestions(ItemParser.SUGGEST_NOTHING);
                    this.reader.skipWhitespace();
                    this.readComponent(ItemParser.this.tagParser, ItemParser.this.registryOps, datacomponenttype1);
                    this.reader.skipWhitespace();
                }

                this.visitor.visitSuggestions(this::suggestNextOrEndComponents);
                if (!this.reader.canRead() || this.reader.peek() != ',') {
                    break;
                }

                this.reader.skip();
                this.reader.skipWhitespace();
                this.visitor.visitSuggestions(this::suggestComponentAssignmentOrRemoval);
                if (!this.reader.canRead()) {
                    throw ItemParser.ERROR_EXPECTED_COMPONENT.createWithContext(this.reader);
                }
            }

            this.reader.expect(']');
            this.visitor.visitSuggestions(ItemParser.SUGGEST_NOTHING);
        }

        public static DataComponentType<?> readComponentType(StringReader reader) throws CommandSyntaxException {
            if (!reader.canRead()) {
                throw ItemParser.ERROR_EXPECTED_COMPONENT.createWithContext(reader);
            } else {
                int i = reader.getCursor();
                Identifier identifier = Identifier.read(reader);
                DataComponentType<?> datacomponenttype = (DataComponentType) BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(identifier);

                if (datacomponenttype != null && !datacomponenttype.isTransient()) {
                    return datacomponenttype;
                } else {
                    reader.setCursor(i);
                    throw ItemParser.ERROR_UNKNOWN_COMPONENT.createWithContext(reader, identifier);
                }
            }
        }

        private <T, O> void readComponent(TagParser<O> tagParser, RegistryOps<O> registryOps, DataComponentType<T> componentType) throws CommandSyntaxException {
            int i = this.reader.getCursor();
            O o0 = tagParser.parseAsArgument(this.reader);
            DataResult<T> dataresult = componentType.codecOrThrow().parse(registryOps, o0);

            this.visitor.visitComponent(componentType, dataresult.getOrThrow((s) -> {
                this.reader.setCursor(i);
                return ItemParser.ERROR_MALFORMED_COMPONENT.createWithContext(this.reader, componentType.toString(), s);
            }));
        }

        private CompletableFuture<Suggestions> suggestStartComponents(SuggestionsBuilder builder) {
            if (builder.getRemaining().isEmpty()) {
                builder.suggest(String.valueOf('['));
            }

            return builder.buildFuture();
        }

        private CompletableFuture<Suggestions> suggestNextOrEndComponents(SuggestionsBuilder builder) {
            if (builder.getRemaining().isEmpty()) {
                builder.suggest(String.valueOf(','));
                builder.suggest(String.valueOf(']'));
            }

            return builder.buildFuture();
        }

        private CompletableFuture<Suggestions> suggestAssignment(SuggestionsBuilder builder) {
            if (builder.getRemaining().isEmpty()) {
                builder.suggest(String.valueOf('='));
            }

            return builder.buildFuture();
        }

        private CompletableFuture<Suggestions> suggestItem(SuggestionsBuilder builder) {
            return SharedSuggestionProvider.suggestResource(ItemParser.this.items.listElementIds().map(ResourceKey::identifier), builder);
        }

        private CompletableFuture<Suggestions> suggestComponentAssignmentOrRemoval(SuggestionsBuilder builder) {
            builder.suggest(String.valueOf('!'));
            return this.suggestComponent(builder, String.valueOf('='));
        }

        private CompletableFuture<Suggestions> suggestComponent(SuggestionsBuilder builder) {
            return this.suggestComponent(builder, "");
        }

        private CompletableFuture<Suggestions> suggestComponent(SuggestionsBuilder builder, String suffix) {
            String s1 = builder.getRemaining().toLowerCase(Locale.ROOT);

            SharedSuggestionProvider.filterResources(BuiltInRegistries.DATA_COMPONENT_TYPE.entrySet(), s1, (entry) -> {
                return ((ResourceKey) entry.getKey()).identifier();
            }, (entry) -> {
                DataComponentType<?> datacomponenttype = (DataComponentType) entry.getValue();

                if (datacomponenttype.codec() != null) {
                    Identifier identifier = ((ResourceKey) entry.getKey()).identifier();
                    String s2 = String.valueOf(identifier);

                    builder.suggest(s2 + suffix);
                }

            });
            return builder.buildFuture();
        }
    }

    public static record ItemResult(Holder<Item> item, DataComponentPatch components) {

    }

    private static class SuggestionsVisitor implements ItemParser.Visitor {

        private Function<SuggestionsBuilder, CompletableFuture<Suggestions>> suggestions;

        private SuggestionsVisitor() {
            this.suggestions = ItemParser.SUGGEST_NOTHING;
        }

        @Override
        public void visitSuggestions(Function<SuggestionsBuilder, CompletableFuture<Suggestions>> suggestions) {
            this.suggestions = suggestions;
        }

        public CompletableFuture<Suggestions> resolveSuggestions(SuggestionsBuilder builder, StringReader reader) {
            return (CompletableFuture) this.suggestions.apply(builder.createOffset(reader.getCursor()));
        }
    }

    public interface Visitor {

        default void visitItem(Holder<Item> item) {}

        default <T> void visitComponent(DataComponentType<T> type, T value) {}

        default <T> void visitRemovedComponent(DataComponentType<T> type) {}

        default void visitSuggestions(Function<SuggestionsBuilder, CompletableFuture<Suggestions>> suggestions) {}
    }
}
