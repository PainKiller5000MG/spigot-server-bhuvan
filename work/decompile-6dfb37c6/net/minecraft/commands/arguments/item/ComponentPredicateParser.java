package net.minecraft.commands.arguments.item;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import net.minecraft.util.parsing.packrat.Atom;
import net.minecraft.util.parsing.packrat.Dictionary;
import net.minecraft.util.parsing.packrat.NamedRule;
import net.minecraft.util.parsing.packrat.Scope;
import net.minecraft.util.parsing.packrat.Term;
import net.minecraft.util.parsing.packrat.commands.Grammar;
import net.minecraft.util.parsing.packrat.commands.IdentifierParseRule;
import net.minecraft.util.parsing.packrat.commands.ResourceLookupRule;
import net.minecraft.util.parsing.packrat.commands.StringReaderTerms;
import net.minecraft.util.parsing.packrat.commands.TagParseRule;

public class ComponentPredicateParser {

    public ComponentPredicateParser() {}

    public static <T, C, P> Grammar<List<T>> createGrammar(ComponentPredicateParser.Context<T, C, P> context) {
        Atom<List<T>> atom = Atom.<List<T>>of("top");
        Atom<Optional<T>> atom1 = Atom.<Optional<T>>of("type");
        Atom<Unit> atom2 = Atom.<Unit>of("any_type");
        Atom<T> atom3 = Atom.<T>of("element_type");
        Atom<T> atom4 = Atom.<T>of("tag_type");
        Atom<List<T>> atom5 = Atom.<List<T>>of("conditions");
        Atom<List<T>> atom6 = Atom.<List<T>>of("alternatives");
        Atom<T> atom7 = Atom.<T>of("term");
        Atom<T> atom8 = Atom.<T>of("negation");
        Atom<T> atom9 = Atom.<T>of("test");
        Atom<C> atom10 = Atom.<C>of("component_type");
        Atom<P> atom11 = Atom.<P>of("predicate_type");
        Atom<Identifier> atom12 = Atom.<Identifier>of("id");
        Atom<Dynamic<?>> atom13 = Atom.<Dynamic<?>>of("tag");
        Dictionary<StringReader> dictionary = new Dictionary<StringReader>();
        NamedRule<StringReader, Identifier> namedrule = dictionary.put(atom12, IdentifierParseRule.INSTANCE);
        NamedRule<StringReader, List<T>> namedrule1 = dictionary.put(atom, Term.alternative(Term.sequence(dictionary.named(atom1), StringReaderTerms.character('['), Term.cut(), Term.optional(dictionary.named(atom5)), StringReaderTerms.character(']')), dictionary.named(atom1)), (scope) -> {
            ImmutableList.Builder<T> immutablelist_builder = ImmutableList.builder();
            Optional optional = (Optional) scope.getOrThrow(atom1);

            Objects.requireNonNull(immutablelist_builder);
            optional.ifPresent(immutablelist_builder::add);
            List<T> list = (List) scope.get(atom5);

            if (list != null) {
                immutablelist_builder.addAll(list);
            }

            return immutablelist_builder.build();
        });

        dictionary.put(atom1, Term.alternative(dictionary.named(atom3), Term.sequence(StringReaderTerms.character('#'), Term.cut(), dictionary.named(atom4)), dictionary.named(atom2)), (scope) -> {
            return Optional.ofNullable(scope.getAny(atom3, atom4));
        });
        dictionary.put(atom2, StringReaderTerms.character('*'), (scope) -> {
            return Unit.INSTANCE;
        });
        dictionary.put(atom3, new ComponentPredicateParser.ElementLookupRule(namedrule, context));
        dictionary.put(atom4, new ComponentPredicateParser.TagLookupRule(namedrule, context));
        dictionary.put(atom5, Term.sequence(dictionary.named(atom6), Term.optional(Term.sequence(StringReaderTerms.character(','), dictionary.named(atom5)))), (scope) -> {
            T t0 = context.anyOf((List) scope.getOrThrow(atom6));

            return (List) Optional.ofNullable((List) scope.get(atom5)).map((list) -> {
                return Util.copyAndAdd(t0, list);
            }).orElse(List.of(t0));
        });
        dictionary.put(atom6, Term.sequence(dictionary.named(atom7), Term.optional(Term.sequence(StringReaderTerms.character('|'), dictionary.named(atom6)))), (scope) -> {
            T t0 = (T) scope.getOrThrow(atom7);

            return (List) Optional.ofNullable((List) scope.get(atom6)).map((list) -> {
                return Util.copyAndAdd(t0, list);
            }).orElse(List.of(t0));
        });
        dictionary.put(atom7, Term.alternative(dictionary.named(atom9), Term.sequence(StringReaderTerms.character('!'), dictionary.named(atom8))), (scope) -> {
            return scope.getAnyOrThrow(atom9, atom8);
        });
        dictionary.put(atom8, dictionary.named(atom9), (scope) -> {
            return context.negate(scope.getOrThrow(atom9));
        });
        dictionary.putComplex(atom9, Term.alternative(Term.sequence(dictionary.named(atom10), StringReaderTerms.character('='), Term.cut(), dictionary.named(atom13)), Term.sequence(dictionary.named(atom11), StringReaderTerms.character('~'), Term.cut(), dictionary.named(atom13)), dictionary.named(atom10)), (parsestate) -> {
            Scope scope = parsestate.scope();
            P p0 = (P) scope.get(atom11);

            try {
                if (p0 != null) {
                    Dynamic<?> dynamic = (Dynamic) scope.getOrThrow(atom13);

                    return context.createPredicateTest((ImmutableStringReader) parsestate.input(), p0, dynamic);
                } else {
                    C c0 = (C) scope.getOrThrow(atom10);
                    Dynamic<?> dynamic1 = (Dynamic) scope.get(atom13);

                    return dynamic1 != null ? context.createComponentTest((ImmutableStringReader) parsestate.input(), c0, dynamic1) : context.createComponentTest((ImmutableStringReader) parsestate.input(), c0);
                }
            } catch (CommandSyntaxException commandsyntaxexception) {
                parsestate.errorCollector().store(parsestate.mark(), commandsyntaxexception);
                return null;
            }
        });
        dictionary.put(atom10, new ComponentPredicateParser.ComponentLookupRule(namedrule, context));
        dictionary.put(atom11, new ComponentPredicateParser.PredicateLookupRule(namedrule, context));
        dictionary.put(atom13, new TagParseRule(NbtOps.INSTANCE));
        return new Grammar<List<T>>(dictionary, namedrule1);
    }

    private static class ElementLookupRule<T, C, P> extends ResourceLookupRule<ComponentPredicateParser.Context<T, C, P>, T> {

        private ElementLookupRule(NamedRule<StringReader, Identifier> idParser, ComponentPredicateParser.Context<T, C, P> context) {
            super(idParser, context);
        }

        @Override
        protected T validateElement(ImmutableStringReader reader, Identifier id) throws Exception {
            return (T) ((ComponentPredicateParser.Context) this.context).forElementType(reader, id);
        }

        @Override
        public Stream<Identifier> possibleResources() {
            return ((ComponentPredicateParser.Context) this.context).listElementTypes();
        }
    }

    private static class TagLookupRule<T, C, P> extends ResourceLookupRule<ComponentPredicateParser.Context<T, C, P>, T> {

        private TagLookupRule(NamedRule<StringReader, Identifier> idParser, ComponentPredicateParser.Context<T, C, P> context) {
            super(idParser, context);
        }

        @Override
        protected T validateElement(ImmutableStringReader reader, Identifier id) throws Exception {
            return (T) ((ComponentPredicateParser.Context) this.context).forTagType(reader, id);
        }

        @Override
        public Stream<Identifier> possibleResources() {
            return ((ComponentPredicateParser.Context) this.context).listTagTypes();
        }
    }

    private static class ComponentLookupRule<T, C, P> extends ResourceLookupRule<ComponentPredicateParser.Context<T, C, P>, C> {

        private ComponentLookupRule(NamedRule<StringReader, Identifier> idParser, ComponentPredicateParser.Context<T, C, P> context) {
            super(idParser, context);
        }

        @Override
        protected C validateElement(ImmutableStringReader reader, Identifier id) throws Exception {
            return (C) ((ComponentPredicateParser.Context) this.context).lookupComponentType(reader, id);
        }

        @Override
        public Stream<Identifier> possibleResources() {
            return ((ComponentPredicateParser.Context) this.context).listComponentTypes();
        }
    }

    private static class PredicateLookupRule<T, C, P> extends ResourceLookupRule<ComponentPredicateParser.Context<T, C, P>, P> {

        private PredicateLookupRule(NamedRule<StringReader, Identifier> idParser, ComponentPredicateParser.Context<T, C, P> context) {
            super(idParser, context);
        }

        @Override
        protected P validateElement(ImmutableStringReader reader, Identifier id) throws Exception {
            return (P) ((ComponentPredicateParser.Context) this.context).lookupPredicateType(reader, id);
        }

        @Override
        public Stream<Identifier> possibleResources() {
            return ((ComponentPredicateParser.Context) this.context).listPredicateTypes();
        }
    }

    public interface Context<T, C, P> {

        T forElementType(ImmutableStringReader reader, Identifier id) throws CommandSyntaxException;

        Stream<Identifier> listElementTypes();

        T forTagType(ImmutableStringReader reader, Identifier id) throws CommandSyntaxException;

        Stream<Identifier> listTagTypes();

        C lookupComponentType(ImmutableStringReader reader, Identifier id) throws CommandSyntaxException;

        Stream<Identifier> listComponentTypes();

        T createComponentTest(ImmutableStringReader reader, C componentType, Dynamic<?> value) throws CommandSyntaxException;

        T createComponentTest(ImmutableStringReader reader, C componentType);

        P lookupPredicateType(ImmutableStringReader reader, Identifier id) throws CommandSyntaxException;

        Stream<Identifier> listPredicateTypes();

        T createPredicateTest(ImmutableStringReader reader, P predicateType, Dynamic<?> value) throws CommandSyntaxException;

        T negate(T value);

        T anyOf(List<T> alternatives);
    }
}
