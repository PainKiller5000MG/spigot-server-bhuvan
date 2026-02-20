package net.minecraft.nbt;

import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.UnsignedBytes;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JavaOps;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.bytes.ByteList;
import it.unimi.dsi.fastutil.chars.CharList;
import java.nio.ByteBuffer;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.IntStream.Builder;
import java.util.stream.LongStream;
import net.minecraft.network.chat.Component;
import net.minecraft.util.parsing.packrat.Atom;
import net.minecraft.util.parsing.packrat.DelayedException;
import net.minecraft.util.parsing.packrat.Dictionary;
import net.minecraft.util.parsing.packrat.NamedRule;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Scope;
import net.minecraft.util.parsing.packrat.Term;
import net.minecraft.util.parsing.packrat.commands.Grammar;
import net.minecraft.util.parsing.packrat.commands.GreedyPatternParseRule;
import net.minecraft.util.parsing.packrat.commands.GreedyPredicateParseRule;
import net.minecraft.util.parsing.packrat.commands.NumberRunParseRule;
import net.minecraft.util.parsing.packrat.commands.StringReaderTerms;
import net.minecraft.util.parsing.packrat.commands.UnquotedStringParseRule;
import org.jspecify.annotations.Nullable;

public class SnbtGrammar {

    private static final DynamicCommandExceptionType ERROR_NUMBER_PARSE_FAILURE = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("snbt.parser.number_parse_failure", object);
    });
    private static final DynamicCommandExceptionType ERROR_EXPECTED_HEX_ESCAPE = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("snbt.parser.expected_hex_escape", object);
    });
    private static final DynamicCommandExceptionType ERROR_INVALID_CODEPOINT = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("snbt.parser.invalid_codepoint", object);
    });
    private static final DynamicCommandExceptionType ERROR_NO_SUCH_OPERATION = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("snbt.parser.no_such_operation", object);
    });
    private static final DelayedException<CommandSyntaxException> ERROR_EXPECTED_INTEGER_TYPE = DelayedException.create(new SimpleCommandExceptionType(Component.translatable("snbt.parser.expected_integer_type")));
    private static final DelayedException<CommandSyntaxException> ERROR_EXPECTED_FLOAT_TYPE = DelayedException.create(new SimpleCommandExceptionType(Component.translatable("snbt.parser.expected_float_type")));
    private static final DelayedException<CommandSyntaxException> ERROR_EXPECTED_NON_NEGATIVE_NUMBER = DelayedException.create(new SimpleCommandExceptionType(Component.translatable("snbt.parser.expected_non_negative_number")));
    private static final DelayedException<CommandSyntaxException> ERROR_INVALID_CHARACTER_NAME = DelayedException.create(new SimpleCommandExceptionType(Component.translatable("snbt.parser.invalid_character_name")));
    private static final DelayedException<CommandSyntaxException> ERROR_INVALID_ARRAY_ELEMENT_TYPE = DelayedException.create(new SimpleCommandExceptionType(Component.translatable("snbt.parser.invalid_array_element_type")));
    private static final DelayedException<CommandSyntaxException> ERROR_INVALID_UNQUOTED_START = DelayedException.create(new SimpleCommandExceptionType(Component.translatable("snbt.parser.invalid_unquoted_start")));
    private static final DelayedException<CommandSyntaxException> ERROR_EXPECTED_UNQUOTED_STRING = DelayedException.create(new SimpleCommandExceptionType(Component.translatable("snbt.parser.expected_unquoted_string")));
    private static final DelayedException<CommandSyntaxException> ERROR_INVALID_STRING_CONTENTS = DelayedException.create(new SimpleCommandExceptionType(Component.translatable("snbt.parser.invalid_string_contents")));
    private static final DelayedException<CommandSyntaxException> ERROR_EXPECTED_BINARY_NUMERAL = DelayedException.create(new SimpleCommandExceptionType(Component.translatable("snbt.parser.expected_binary_numeral")));
    private static final DelayedException<CommandSyntaxException> ERROR_UNDESCORE_NOT_ALLOWED = DelayedException.create(new SimpleCommandExceptionType(Component.translatable("snbt.parser.underscore_not_allowed")));
    private static final DelayedException<CommandSyntaxException> ERROR_EXPECTED_DECIMAL_NUMERAL = DelayedException.create(new SimpleCommandExceptionType(Component.translatable("snbt.parser.expected_decimal_numeral")));
    private static final DelayedException<CommandSyntaxException> ERROR_EXPECTED_HEX_NUMERAL = DelayedException.create(new SimpleCommandExceptionType(Component.translatable("snbt.parser.expected_hex_numeral")));
    private static final DelayedException<CommandSyntaxException> ERROR_EMPTY_KEY = DelayedException.create(new SimpleCommandExceptionType(Component.translatable("snbt.parser.empty_key")));
    private static final DelayedException<CommandSyntaxException> ERROR_LEADING_ZERO_NOT_ALLOWED = DelayedException.create(new SimpleCommandExceptionType(Component.translatable("snbt.parser.leading_zero_not_allowed")));
    private static final DelayedException<CommandSyntaxException> ERROR_INFINITY_NOT_ALLOWED = DelayedException.create(new SimpleCommandExceptionType(Component.translatable("snbt.parser.infinity_not_allowed")));
    private static final HexFormat HEX_ESCAPE = HexFormat.of().withUpperCase();
    private static final NumberRunParseRule BINARY_NUMERAL = new NumberRunParseRule(SnbtGrammar.ERROR_EXPECTED_BINARY_NUMERAL, SnbtGrammar.ERROR_UNDESCORE_NOT_ALLOWED) {
        @Override
        protected boolean isAccepted(char c) {
            boolean flag;

            switch (c) {
                case '0':
                case '1':
                case '_':
                    flag = true;
                    break;
                default:
                    flag = false;
            }

            return flag;
        }
    };
    private static final NumberRunParseRule DECIMAL_NUMERAL = new NumberRunParseRule(SnbtGrammar.ERROR_EXPECTED_DECIMAL_NUMERAL, SnbtGrammar.ERROR_UNDESCORE_NOT_ALLOWED) {
        @Override
        protected boolean isAccepted(char c) {
            boolean flag;

            switch (c) {
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                case '_':
                    flag = true;
                    break;
                default:
                    flag = false;
            }

            return flag;
        }
    };
    private static final NumberRunParseRule HEX_NUMERAL = new NumberRunParseRule(SnbtGrammar.ERROR_EXPECTED_HEX_NUMERAL, SnbtGrammar.ERROR_UNDESCORE_NOT_ALLOWED) {
        @Override
        protected boolean isAccepted(char c) {
            boolean flag;

            switch (c) {
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                case 'A':
                case 'B':
                case 'C':
                case 'D':
                case 'E':
                case 'F':
                case '_':
                case 'a':
                case 'b':
                case 'c':
                case 'd':
                case 'e':
                case 'f':
                    flag = true;
                    break;
                case ':':
                case ';':
                case '<':
                case '=':
                case '>':
                case '?':
                case '@':
                case 'G':
                case 'H':
                case 'I':
                case 'J':
                case 'K':
                case 'L':
                case 'M':
                case 'N':
                case 'O':
                case 'P':
                case 'Q':
                case 'R':
                case 'S':
                case 'T':
                case 'U':
                case 'V':
                case 'W':
                case 'X':
                case 'Y':
                case 'Z':
                case '[':
                case '\\':
                case ']':
                case '^':
                case '`':
                default:
                    flag = false;
            }

            return flag;
        }
    };
    private static final GreedyPredicateParseRule PLAIN_STRING_CHUNK = new GreedyPredicateParseRule(1, SnbtGrammar.ERROR_INVALID_STRING_CONTENTS) {
        @Override
        protected boolean isAccepted(char c) {
            boolean flag;

            switch (c) {
                case '"':
                case '\'':
                case '\\':
                    flag = false;
                    break;
                default:
                    flag = true;
            }

            return flag;
        }
    };
    private static final StringReaderTerms.TerminalCharacters NUMBER_LOOKEAHEAD = new StringReaderTerms.TerminalCharacters(CharList.of()) {
        @Override
        protected boolean isAccepted(char c) {
            return SnbtGrammar.canStartNumber(c);
        }
    };
    private static final Pattern UNICODE_NAME = Pattern.compile("[-a-zA-Z0-9 ]+");

    public SnbtGrammar() {}

    private static DelayedException<CommandSyntaxException> createNumberParseError(NumberFormatException ex) {
        return DelayedException.create(SnbtGrammar.ERROR_NUMBER_PARSE_FAILURE, ex.getMessage());
    }

    public static @Nullable String escapeControlCharacters(char c) {
        String s;

        switch (c) {
            case '\b':
                s = "b";
                break;
            case '\t':
                s = "t";
                break;
            case '\n':
                s = "n";
                break;
            case '\u000b':
            default:
                s = c < ' ' ? "x" + SnbtGrammar.HEX_ESCAPE.toHexDigits((byte) c) : null;
                break;
            case '\f':
                s = "f";
                break;
            case '\r':
                s = "r";
        }

        return s;
    }

    private static boolean isAllowedToStartUnquotedString(char c) {
        return !canStartNumber(c);
    }

    private static boolean canStartNumber(char c) {
        boolean flag;

        switch (c) {
            case '+':
            case '-':
            case '.':
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                flag = true;
                break;
            case ',':
            case '/':
            default:
                flag = false;
        }

        return flag;
    }

    private static boolean needsUnderscoreRemoval(String contents) {
        return contents.indexOf(95) != -1;
    }

    private static void cleanAndAppend(StringBuilder output, String contents) {
        cleanAndAppend(output, contents, needsUnderscoreRemoval(contents));
    }

    private static void cleanAndAppend(StringBuilder output, String contents, boolean needsUnderscoreRemoval) {
        if (needsUnderscoreRemoval) {
            for (char c0 : contents.toCharArray()) {
                if (c0 != '_') {
                    output.append(c0);
                }
            }
        } else {
            output.append(contents);
        }

    }

    private static short parseUnsignedShort(String string, int radix) {
        int j = Integer.parseInt(string, radix);

        if (j >> 16 == 0) {
            return (short) j;
        } else {
            throw new NumberFormatException("out of range: " + j);
        }
    }

    private static <T> @Nullable T createFloat(DynamicOps<T> ops, SnbtGrammar.Sign sign, @Nullable String whole, @Nullable String fraction, SnbtGrammar.@Nullable Signed<String> exponent, SnbtGrammar.@Nullable TypeSuffix typeSuffix, ParseState<?> state) {
        StringBuilder stringbuilder = new StringBuilder();

        sign.append(stringbuilder);
        if (whole != null) {
            cleanAndAppend(stringbuilder, whole);
        }

        if (fraction != null) {
            stringbuilder.append('.');
            cleanAndAppend(stringbuilder, fraction);
        }

        if (exponent != null) {
            stringbuilder.append('e');
            exponent.sign().append(stringbuilder);
            cleanAndAppend(stringbuilder, (String)exponent.value);
        }

        try {
            String s2 = stringbuilder.toString();
            byte b0 = 0;
            Object object;

            //$FF: b0->value
            //0->FLOAT
            //1->DOUBLE
            switch (typeSuffix.enumSwitch<invokedynamic>(typeSuffix, b0)) {
                case -1:
                    object = convertDouble(ops, state, s2);
                    break;
                case 0:
                    object = convertFloat(ops, state, s2);
                    break;
                case 1:
                    object = convertDouble(ops, state, s2);
                    break;
                default:
                    state.errorCollector().store(state.mark(), SnbtGrammar.ERROR_EXPECTED_FLOAT_TYPE);
                    object = null;
            }

            return (T)object;
        } catch (NumberFormatException numberformatexception) {
            state.errorCollector().store(state.mark(), createNumberParseError(numberformatexception));
            return null;
        }
    }

    private static <T> @Nullable T convertFloat(DynamicOps<T> ops, ParseState<?> state, String contents) {
        float f = Float.parseFloat(contents);

        if (!Float.isFinite(f)) {
            state.errorCollector().store(state.mark(), SnbtGrammar.ERROR_INFINITY_NOT_ALLOWED);
            return null;
        } else {
            return (T) ops.createFloat(f);
        }
    }

    private static <T> @Nullable T convertDouble(DynamicOps<T> ops, ParseState<?> state, String contents) {
        double d0 = Double.parseDouble(contents);

        if (!Double.isFinite(d0)) {
            state.errorCollector().store(state.mark(), SnbtGrammar.ERROR_INFINITY_NOT_ALLOWED);
            return null;
        } else {
            return (T) ops.createDouble(d0);
        }
    }

    private static String joinList(List<String> list) {
        String s;

        switch (list.size()) {
            case 0:
                s = "";
                break;
            case 1:
                s = (String) list.getFirst();
                break;
            default:
                s = String.join("", list);
        }

        return s;
    }

    public static <T> Grammar<T> createParser(DynamicOps<T> ops) {
        T t0 = (T) ops.createBoolean(true);
        T t1 = (T) ops.createBoolean(false);
        T t2 = (T) ops.emptyMap();
        T t3 = (T) ops.emptyList();
        Dictionary<StringReader> dictionary = new Dictionary<StringReader>();
        Atom<SnbtGrammar.Sign> atom = Atom.<SnbtGrammar.Sign>of("sign");

        dictionary.put(atom, Term.alternative(Term.sequence(StringReaderTerms.character('+'), Term.marker(atom, SnbtGrammar.Sign.PLUS)), Term.sequence(StringReaderTerms.character('-'), Term.marker(atom, SnbtGrammar.Sign.MINUS))), (scope) -> {
            return (SnbtGrammar.Sign) scope.getOrThrow(atom);
        });
        Atom<SnbtGrammar.IntegerSuffix> atom1 = Atom.<SnbtGrammar.IntegerSuffix>of("integer_suffix");

        dictionary.put(atom1, Term.alternative(Term.sequence(StringReaderTerms.characters('u', 'U'), Term.alternative(Term.sequence(StringReaderTerms.characters('b', 'B'), Term.marker(atom1, new SnbtGrammar.IntegerSuffix(SnbtGrammar.SignedPrefix.UNSIGNED, SnbtGrammar.TypeSuffix.BYTE))), Term.sequence(StringReaderTerms.characters('s', 'S'), Term.marker(atom1, new SnbtGrammar.IntegerSuffix(SnbtGrammar.SignedPrefix.UNSIGNED, SnbtGrammar.TypeSuffix.SHORT))), Term.sequence(StringReaderTerms.characters('i', 'I'), Term.marker(atom1, new SnbtGrammar.IntegerSuffix(SnbtGrammar.SignedPrefix.UNSIGNED, SnbtGrammar.TypeSuffix.INT))), Term.sequence(StringReaderTerms.characters('l', 'L'), Term.marker(atom1, new SnbtGrammar.IntegerSuffix(SnbtGrammar.SignedPrefix.UNSIGNED, SnbtGrammar.TypeSuffix.LONG))))), Term.sequence(StringReaderTerms.characters('s', 'S'), Term.alternative(Term.sequence(StringReaderTerms.characters('b', 'B'), Term.marker(atom1, new SnbtGrammar.IntegerSuffix(SnbtGrammar.SignedPrefix.SIGNED, SnbtGrammar.TypeSuffix.BYTE))), Term.sequence(StringReaderTerms.characters('s', 'S'), Term.marker(atom1, new SnbtGrammar.IntegerSuffix(SnbtGrammar.SignedPrefix.SIGNED, SnbtGrammar.TypeSuffix.SHORT))), Term.sequence(StringReaderTerms.characters('i', 'I'), Term.marker(atom1, new SnbtGrammar.IntegerSuffix(SnbtGrammar.SignedPrefix.SIGNED, SnbtGrammar.TypeSuffix.INT))), Term.sequence(StringReaderTerms.characters('l', 'L'), Term.marker(atom1, new SnbtGrammar.IntegerSuffix(SnbtGrammar.SignedPrefix.SIGNED, SnbtGrammar.TypeSuffix.LONG))))), Term.sequence(StringReaderTerms.characters('b', 'B'), Term.marker(atom1, new SnbtGrammar.IntegerSuffix((SnbtGrammar.SignedPrefix) null, SnbtGrammar.TypeSuffix.BYTE))), Term.sequence(StringReaderTerms.characters('s', 'S'), Term.marker(atom1, new SnbtGrammar.IntegerSuffix((SnbtGrammar.SignedPrefix) null, SnbtGrammar.TypeSuffix.SHORT))), Term.sequence(StringReaderTerms.characters('i', 'I'), Term.marker(atom1, new SnbtGrammar.IntegerSuffix((SnbtGrammar.SignedPrefix) null, SnbtGrammar.TypeSuffix.INT))), Term.sequence(StringReaderTerms.characters('l', 'L'), Term.marker(atom1, new SnbtGrammar.IntegerSuffix((SnbtGrammar.SignedPrefix) null, SnbtGrammar.TypeSuffix.LONG)))), (scope) -> {
            return (SnbtGrammar.IntegerSuffix) scope.getOrThrow(atom1);
        });
        Atom<String> atom2 = Atom.<String>of("binary_numeral");

        dictionary.put(atom2, SnbtGrammar.BINARY_NUMERAL);
        Atom<String> atom3 = Atom.<String>of("decimal_numeral");

        dictionary.put(atom3, SnbtGrammar.DECIMAL_NUMERAL);
        Atom<String> atom4 = Atom.<String>of("hex_numeral");

        dictionary.put(atom4, SnbtGrammar.HEX_NUMERAL);
        Atom<SnbtGrammar.IntegerLiteral> atom5 = Atom.<SnbtGrammar.IntegerLiteral>of("integer_literal");
        NamedRule<StringReader, SnbtGrammar.IntegerLiteral> namedrule = dictionary.put(atom5, Term.sequence(Term.optional(dictionary.named(atom)), Term.alternative(Term.sequence(StringReaderTerms.character('0'), Term.cut(), Term.alternative(Term.sequence(StringReaderTerms.characters('x', 'X'), Term.cut(), dictionary.named(atom4)), Term.sequence(StringReaderTerms.characters('b', 'B'), dictionary.named(atom2)), Term.sequence(dictionary.named(atom3), Term.cut(), Term.fail(SnbtGrammar.ERROR_LEADING_ZERO_NOT_ALLOWED)), Term.marker(atom3, "0"))), dictionary.named(atom3)), Term.optional(dictionary.named(atom1))), (scope) -> {
            SnbtGrammar.IntegerSuffix snbtgrammar_integersuffix = (SnbtGrammar.IntegerSuffix) scope.getOrDefault(atom1, SnbtGrammar.IntegerSuffix.EMPTY);
            SnbtGrammar.Sign snbtgrammar_sign = (SnbtGrammar.Sign) scope.getOrDefault(atom, SnbtGrammar.Sign.PLUS);
            String s = (String) scope.get(atom3);

            if (s != null) {
                return new SnbtGrammar.IntegerLiteral(snbtgrammar_sign, SnbtGrammar.Base.DECIMAL, s, snbtgrammar_integersuffix);
            } else {
                String s1 = (String) scope.get(atom4);

                if (s1 != null) {
                    return new SnbtGrammar.IntegerLiteral(snbtgrammar_sign, SnbtGrammar.Base.HEX, s1, snbtgrammar_integersuffix);
                } else {
                    String s2 = (String) scope.getOrThrow(atom2);

                    return new SnbtGrammar.IntegerLiteral(snbtgrammar_sign, SnbtGrammar.Base.BINARY, s2, snbtgrammar_integersuffix);
                }
            }
        });
        Atom<SnbtGrammar.TypeSuffix> atom6 = Atom.<SnbtGrammar.TypeSuffix>of("float_type_suffix");

        dictionary.put(atom6, Term.alternative(Term.sequence(StringReaderTerms.characters('f', 'F'), Term.marker(atom6, SnbtGrammar.TypeSuffix.FLOAT)), Term.sequence(StringReaderTerms.characters('d', 'D'), Term.marker(atom6, SnbtGrammar.TypeSuffix.DOUBLE))), (scope) -> {
            return (SnbtGrammar.TypeSuffix) scope.getOrThrow(atom6);
        });
        Atom<SnbtGrammar.Signed<String>> atom7 = Atom.<SnbtGrammar.Signed<String>>of("float_exponent_part");

        dictionary.put(atom7, Term.sequence(StringReaderTerms.characters('e', 'E'), Term.optional(dictionary.named(atom)), dictionary.named(atom3)), (scope) -> {
            return new SnbtGrammar.Signed((SnbtGrammar.Sign) scope.getOrDefault(atom, SnbtGrammar.Sign.PLUS), (String) scope.getOrThrow(atom3));
        });
        Atom<String> atom8 = Atom.<String>of("float_whole_part");
        Atom<String> atom9 = Atom.<String>of("float_fraction_part");
        Atom<T> atom10 = Atom.<T>of("float_literal");

        dictionary.putComplex(atom10, Term.sequence(Term.optional(dictionary.named(atom)), Term.alternative(Term.sequence(dictionary.namedWithAlias(atom3, atom8), StringReaderTerms.character('.'), Term.cut(), Term.optional(dictionary.namedWithAlias(atom3, atom9)), Term.optional(dictionary.named(atom7)), Term.optional(dictionary.named(atom6))), Term.sequence(StringReaderTerms.character('.'), Term.cut(), dictionary.namedWithAlias(atom3, atom9), Term.optional(dictionary.named(atom7)), Term.optional(dictionary.named(atom6))), Term.sequence(dictionary.namedWithAlias(atom3, atom8), dictionary.named(atom7), Term.cut(), Term.optional(dictionary.named(atom6))), Term.sequence(dictionary.namedWithAlias(atom3, atom8), Term.optional(dictionary.named(atom7)), dictionary.named(atom6)))), (parsestate) -> {
            Scope scope = parsestate.scope();
            SnbtGrammar.Sign snbtgrammar_sign = (SnbtGrammar.Sign) scope.getOrDefault(atom, SnbtGrammar.Sign.PLUS);
            String s = (String) scope.get(atom8);
            String s1 = (String) scope.get(atom9);
            SnbtGrammar.Signed<String> snbtgrammar_signed = (SnbtGrammar.Signed) scope.get(atom7);
            SnbtGrammar.TypeSuffix snbtgrammar_typesuffix = (SnbtGrammar.TypeSuffix) scope.get(atom6);

            return createFloat(ops, snbtgrammar_sign, s, s1, snbtgrammar_signed, snbtgrammar_typesuffix, parsestate);
        });
        Atom<String> atom11 = Atom.<String>of("string_hex_2");

        dictionary.put(atom11, new SnbtGrammar.SimpleHexLiteralParseRule(2));
        Atom<String> atom12 = Atom.<String>of("string_hex_4");

        dictionary.put(atom12, new SnbtGrammar.SimpleHexLiteralParseRule(4));
        Atom<String> atom13 = Atom.<String>of("string_hex_8");

        dictionary.put(atom13, new SnbtGrammar.SimpleHexLiteralParseRule(8));
        Atom<String> atom14 = Atom.<String>of("string_unicode_name");

        dictionary.put(atom14, new GreedyPatternParseRule(SnbtGrammar.UNICODE_NAME, SnbtGrammar.ERROR_INVALID_CHARACTER_NAME));
        Atom<String> atom15 = Atom.<String>of("string_escape_sequence");

        dictionary.putComplex(atom15, Term.alternative(Term.sequence(StringReaderTerms.character('b'), Term.marker(atom15, "\b")), Term.sequence(StringReaderTerms.character('s'), Term.marker(atom15, " ")), Term.sequence(StringReaderTerms.character('t'), Term.marker(atom15, "\t")), Term.sequence(StringReaderTerms.character('n'), Term.marker(atom15, "\n")), Term.sequence(StringReaderTerms.character('f'), Term.marker(atom15, "\f")), Term.sequence(StringReaderTerms.character('r'), Term.marker(atom15, "\r")), Term.sequence(StringReaderTerms.character('\\'), Term.marker(atom15, "\\")), Term.sequence(StringReaderTerms.character('\''), Term.marker(atom15, "'")), Term.sequence(StringReaderTerms.character('"'), Term.marker(atom15, "\"")), Term.sequence(StringReaderTerms.character('x'), dictionary.named(atom11)), Term.sequence(StringReaderTerms.character('u'), dictionary.named(atom12)), Term.sequence(StringReaderTerms.character('U'), dictionary.named(atom13)), Term.sequence(StringReaderTerms.character('N'), StringReaderTerms.character('{'), dictionary.named(atom14), StringReaderTerms.character('}'))), (parsestate) -> {
            Scope scope = parsestate.scope();
            String s = (String) scope.getAny(atom15);

            if (s != null) {
                return s;
            } else {
                String s1 = (String) scope.getAny(atom11, atom12, atom13);

                if (s1 != null) {
                    int i = HexFormat.fromHexDigits(s1);

                    if (!Character.isValidCodePoint(i)) {
                        parsestate.errorCollector().store(parsestate.mark(), DelayedException.create(SnbtGrammar.ERROR_INVALID_CODEPOINT, String.format(Locale.ROOT, "U+%08X", i)));
                        return null;
                    } else {
                        return Character.toString(i);
                    }
                } else {
                    String s2 = (String) scope.getOrThrow(atom14);

                    int j;

                    try {
                        j = Character.codePointOf(s2);
                    } catch (IllegalArgumentException illegalargumentexception) {
                        parsestate.errorCollector().store(parsestate.mark(), SnbtGrammar.ERROR_INVALID_CHARACTER_NAME);
                        return null;
                    }

                    return Character.toString(j);
                }
            }
        });
        Atom<String> atom16 = Atom.<String>of("string_plain_contents");

        dictionary.put(atom16, SnbtGrammar.PLAIN_STRING_CHUNK);
        Atom<List<String>> atom17 = Atom.<List<String>>of("string_chunks");
        Atom<String> atom18 = Atom.<String>of("string_contents");
        Atom<String> atom19 = Atom.<String>of("single_quoted_string_chunk");
        NamedRule<StringReader, String> namedrule1 = dictionary.put(atom19, Term.alternative(dictionary.namedWithAlias(atom16, atom18), Term.sequence(StringReaderTerms.character('\\'), dictionary.namedWithAlias(atom15, atom18)), Term.sequence(StringReaderTerms.character('"'), Term.marker(atom18, "\""))), (scope) -> {
            return (String) scope.getOrThrow(atom18);
        });
        Atom<String> atom20 = Atom.<String>of("single_quoted_string_contents");

        dictionary.put(atom20, Term.repeated(namedrule1, atom17), (scope) -> {
            return joinList((List) scope.getOrThrow(atom17));
        });
        Atom<String> atom21 = Atom.<String>of("double_quoted_string_chunk");
        NamedRule<StringReader, String> namedrule2 = dictionary.put(atom21, Term.alternative(dictionary.namedWithAlias(atom16, atom18), Term.sequence(StringReaderTerms.character('\\'), dictionary.namedWithAlias(atom15, atom18)), Term.sequence(StringReaderTerms.character('\''), Term.marker(atom18, "'"))), (scope) -> {
            return (String) scope.getOrThrow(atom18);
        });
        Atom<String> atom22 = Atom.<String>of("double_quoted_string_contents");

        dictionary.put(atom22, Term.repeated(namedrule2, atom17), (scope) -> {
            return joinList((List) scope.getOrThrow(atom17));
        });
        Atom<String> atom23 = Atom.<String>of("quoted_string_literal");

        dictionary.put(atom23, Term.alternative(Term.sequence(StringReaderTerms.character('"'), Term.cut(), Term.optional(dictionary.namedWithAlias(atom22, atom18)), StringReaderTerms.character('"')), Term.sequence(StringReaderTerms.character('\''), Term.optional(dictionary.namedWithAlias(atom20, atom18)), StringReaderTerms.character('\''))), (scope) -> {
            return (String) scope.getOrThrow(atom18);
        });
        Atom<String> atom24 = Atom.<String>of("unquoted_string");

        dictionary.put(atom24, new UnquotedStringParseRule(1, SnbtGrammar.ERROR_EXPECTED_UNQUOTED_STRING));
        Atom<T> atom25 = Atom.<T>of("literal");
        Atom<List<T>> atom26 = Atom.<List<T>>of("arguments");

        dictionary.put(atom26, Term.repeatedWithTrailingSeparator(dictionary.forward(atom25), atom26, StringReaderTerms.character(',')), (scope) -> {
            return (List) scope.getOrThrow(atom26);
        });
        Atom<T> atom27 = Atom.<T>of("unquoted_string_or_builtin");

        dictionary.putComplex(atom27, Term.sequence(dictionary.named(atom24), Term.optional(Term.sequence(StringReaderTerms.character('('), dictionary.named(atom26), StringReaderTerms.character(')')))), (parsestate) -> {
            Scope scope = parsestate.scope();
            String s = (String) scope.getOrThrow(atom24);

            if (!s.isEmpty() && isAllowedToStartUnquotedString(s.charAt(0))) {
                List<T> list = (List) scope.get(atom26);

                if (list != null) {
                    SnbtOperations.BuiltinKey snbtoperations_builtinkey = new SnbtOperations.BuiltinKey(s, list.size());
                    SnbtOperations.BuiltinOperation snbtoperations_builtinoperation = (SnbtOperations.BuiltinOperation) SnbtOperations.BUILTIN_OPERATIONS.get(snbtoperations_builtinkey);

                    if (snbtoperations_builtinoperation != null) {
                        return snbtoperations_builtinoperation.run(ops, list, parsestate);
                    } else {
                        parsestate.errorCollector().store(parsestate.mark(), DelayedException.create(SnbtGrammar.ERROR_NO_SUCH_OPERATION, snbtoperations_builtinkey.toString()));
                        return null;
                    }
                } else {
                    return s.equalsIgnoreCase("true") ? t0 : (s.equalsIgnoreCase("false") ? t1 : ops.createString(s));
                }
            } else {
                parsestate.errorCollector().store(parsestate.mark(), SnbtOperations.BUILTIN_IDS, SnbtGrammar.ERROR_INVALID_UNQUOTED_START);
                return null;
            }
        });
        Atom<String> atom28 = Atom.<String>of("map_key");

        dictionary.put(atom28, Term.alternative(dictionary.named(atom23), dictionary.named(atom24)), (scope) -> {
            return (String) scope.getAnyOrThrow(atom23, atom24);
        });
        Atom<Map.Entry<String, T>> atom29 = Atom.<Map.Entry<String, T>>of("map_entry");
        NamedRule<StringReader, Map.Entry<String, T>> namedrule3 = dictionary.putComplex(atom29, Term.sequence(dictionary.named(atom28), StringReaderTerms.character(':'), dictionary.named(atom25)), (parsestate) -> {
            Scope scope = parsestate.scope();
            String s = (String) scope.getOrThrow(atom28);

            if (s.isEmpty()) {
                parsestate.errorCollector().store(parsestate.mark(), SnbtGrammar.ERROR_EMPTY_KEY);
                return null;
            } else {
                T t4 = (T) scope.getOrThrow(atom25);

                return Map.entry(s, t4);
            }
        });
        Atom<List<Map.Entry<String, T>>> atom30 = Atom.<List<Map.Entry<String, T>>>of("map_entries");

        dictionary.put(atom30, Term.repeatedWithTrailingSeparator(namedrule3, atom30, StringReaderTerms.character(',')), (scope) -> {
            return (List) scope.getOrThrow(atom30);
        });
        Atom<T> atom31 = Atom.<T>of("map_literal");

        dictionary.put(atom31, Term.sequence(StringReaderTerms.character('{'), dictionary.named(atom30), StringReaderTerms.character('}')), (scope) -> {
            List<Map.Entry<String, T>> list = (List) scope.getOrThrow(atom30);

            if (list.isEmpty()) {
                return t2;
            } else {
                ImmutableMap.Builder<T, T> immutablemap_builder = ImmutableMap.builderWithExpectedSize(list.size());

                for (Map.Entry<String, T> map_entry : list) {
                    immutablemap_builder.put(ops.createString((String) map_entry.getKey()), map_entry.getValue());
                }

                return ops.createMap(immutablemap_builder.buildKeepingLast());
            }
        });
        Atom<List<T>> atom32 = Atom.<List<T>>of("list_entries");

        dictionary.put(atom32, Term.repeatedWithTrailingSeparator(dictionary.forward(atom25), atom32, StringReaderTerms.character(',')), (scope) -> {
            return (List) scope.getOrThrow(atom32);
        });
        Atom<SnbtGrammar.ArrayPrefix> atom33 = Atom.<SnbtGrammar.ArrayPrefix>of("array_prefix");

        dictionary.put(atom33, Term.alternative(Term.sequence(StringReaderTerms.character('B'), Term.marker(atom33, SnbtGrammar.ArrayPrefix.BYTE)), Term.sequence(StringReaderTerms.character('L'), Term.marker(atom33, SnbtGrammar.ArrayPrefix.LONG)), Term.sequence(StringReaderTerms.character('I'), Term.marker(atom33, SnbtGrammar.ArrayPrefix.INT))), (scope) -> {
            return (SnbtGrammar.ArrayPrefix) scope.getOrThrow(atom33);
        });
        Atom<List<SnbtGrammar.IntegerLiteral>> atom34 = Atom.<List<SnbtGrammar.IntegerLiteral>>of("int_array_entries");

        dictionary.put(atom34, Term.repeatedWithTrailingSeparator(namedrule, atom34, StringReaderTerms.character(',')), (scope) -> {
            return (List) scope.getOrThrow(atom34);
        });
        Atom<T> atom35 = Atom.<T>of("list_literal");

        dictionary.putComplex(atom35, Term.sequence(StringReaderTerms.character('['), Term.alternative(Term.sequence(dictionary.named(atom33), StringReaderTerms.character(';'), dictionary.named(atom34)), dictionary.named(atom32)), StringReaderTerms.character(']')), (parsestate) -> {
            Scope scope = parsestate.scope();
            SnbtGrammar.ArrayPrefix snbtgrammar_arrayprefix = (SnbtGrammar.ArrayPrefix) scope.get(atom33);

            if (snbtgrammar_arrayprefix != null) {
                List<SnbtGrammar.IntegerLiteral> list = (List) scope.getOrThrow(atom34);

                return list.isEmpty() ? snbtgrammar_arrayprefix.create(ops) : snbtgrammar_arrayprefix.create(ops, list, parsestate);
            } else {
                List<T> list1 = (List) scope.getOrThrow(atom32);

                return list1.isEmpty() ? t3 : ops.createList(list1.stream());
            }
        });
        NamedRule<StringReader, T> namedrule4 = dictionary.putComplex(atom25, Term.alternative(Term.sequence(Term.positiveLookahead(SnbtGrammar.NUMBER_LOOKEAHEAD), Term.alternative(dictionary.namedWithAlias(atom10, atom25), dictionary.named(atom5))), Term.sequence(Term.positiveLookahead(StringReaderTerms.characters('"', '\'')), Term.cut(), dictionary.named(atom23)), Term.sequence(Term.positiveLookahead(StringReaderTerms.character('{')), Term.cut(), dictionary.namedWithAlias(atom31, atom25)), Term.sequence(Term.positiveLookahead(StringReaderTerms.character('[')), Term.cut(), dictionary.namedWithAlias(atom35, atom25)), dictionary.namedWithAlias(atom27, atom25)), (parsestate) -> {
            Scope scope = parsestate.scope();
            String s = (String) scope.get(atom23);

            if (s != null) {
                return ops.createString(s);
            } else {
                SnbtGrammar.IntegerLiteral snbtgrammar_integerliteral = (SnbtGrammar.IntegerLiteral) scope.get(atom5);

                return snbtgrammar_integerliteral != null ? snbtgrammar_integerliteral.create(ops, parsestate) : scope.getOrThrow(atom25);
            }
        });

        return new Grammar<T>(dictionary, namedrule4);
    }

    private static enum Sign {

        PLUS, MINUS;

        private Sign() {}

        public void append(StringBuilder output) {
            if (this == SnbtGrammar.Sign.MINUS) {
                output.append("-");
            }

        }
    }

    private static enum Base {

        BINARY, DECIMAL, HEX;

        private Base() {}
    }

    private static enum TypeSuffix {

        FLOAT, DOUBLE, BYTE, SHORT, INT, LONG;

        private TypeSuffix() {}
    }

    private static enum SignedPrefix {

        SIGNED, UNSIGNED;

        private SignedPrefix() {}
    }

    private static record IntegerSuffix(SnbtGrammar.@Nullable SignedPrefix signed, SnbtGrammar.@Nullable TypeSuffix type) {

        public static final SnbtGrammar.IntegerSuffix EMPTY = new SnbtGrammar.IntegerSuffix((SnbtGrammar.SignedPrefix) null, (SnbtGrammar.TypeSuffix) null);
    }

    private static enum ArrayPrefix {

        BYTE(SnbtGrammar.TypeSuffix.BYTE, new SnbtGrammar.TypeSuffix[0]) {
            private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.wrap(new byte[0]);

            @Override
            public <T> T create(DynamicOps<T> ops) {
                return (T) ops.createByteList(null.EMPTY_BUFFER);
            }

            @Override
            public <T> @Nullable T create(DynamicOps<T> ops, List<SnbtGrammar.IntegerLiteral> entries, ParseState<?> state) {
                ByteList bytelist = new ByteArrayList();

                for (SnbtGrammar.IntegerLiteral snbtgrammar_integerliteral : entries) {
                    Number number = this.buildNumber(snbtgrammar_integerliteral, state);

                    if (number == null) {
                        return null;
                    }

                    bytelist.add(number.byteValue());
                }

                return (T) ops.createByteList(ByteBuffer.wrap(bytelist.toByteArray()));
            }
        },
        INT(SnbtGrammar.TypeSuffix.INT, new SnbtGrammar.TypeSuffix[]{SnbtGrammar.TypeSuffix.BYTE, SnbtGrammar.TypeSuffix.SHORT}) {
            @Override
            public <T> T create(DynamicOps<T> ops) {
                return (T) ops.createIntList(IntStream.empty());
            }

            @Override
            public <T> @Nullable T create(DynamicOps<T> ops, List<SnbtGrammar.IntegerLiteral> entries, ParseState<?> state) {
                Builder builder = IntStream.builder();

                for (SnbtGrammar.IntegerLiteral snbtgrammar_integerliteral : entries) {
                    Number number = this.buildNumber(snbtgrammar_integerliteral, state);

                    if (number == null) {
                        return null;
                    }

                    builder.add(number.intValue());
                }

                return (T) ops.createIntList(builder.build());
            }
        },
        LONG(SnbtGrammar.TypeSuffix.LONG, new SnbtGrammar.TypeSuffix[]{SnbtGrammar.TypeSuffix.BYTE, SnbtGrammar.TypeSuffix.SHORT, SnbtGrammar.TypeSuffix.INT}) {
            @Override
            public <T> T create(DynamicOps<T> ops) {
                return (T) ops.createLongList(LongStream.empty());
            }

            @Override
            public <T> @Nullable T create(DynamicOps<T> ops, List<SnbtGrammar.IntegerLiteral> entries, ParseState<?> state) {
                java.util.stream.LongStream.Builder java_util_stream_longstream_builder = LongStream.builder();

                for (SnbtGrammar.IntegerLiteral snbtgrammar_integerliteral : entries) {
                    Number number = this.buildNumber(snbtgrammar_integerliteral, state);

                    if (number == null) {
                        return null;
                    }

                    java_util_stream_longstream_builder.add(number.longValue());
                }

                return (T) ops.createLongList(java_util_stream_longstream_builder.build());
            }
        };

        private final SnbtGrammar.TypeSuffix defaultType;
        private final Set<SnbtGrammar.TypeSuffix> additionalTypes;

        private ArrayPrefix(SnbtGrammar.TypeSuffix defaultType, SnbtGrammar.TypeSuffix... additionalTypes) {
            this.additionalTypes = Set.of(additionalTypes);
            this.defaultType = defaultType;
        }

        public boolean isAllowed(SnbtGrammar.TypeSuffix type) {
            return type == this.defaultType || this.additionalTypes.contains(type);
        }

        public abstract <T> T create(DynamicOps<T> ops);

        public abstract <T> @Nullable T create(DynamicOps<T> ops, List<SnbtGrammar.IntegerLiteral> entries, ParseState<?> state);

        protected @Nullable Number buildNumber(SnbtGrammar.IntegerLiteral entry, ParseState<?> state) {
            SnbtGrammar.TypeSuffix snbtgrammar_typesuffix = this.computeType(entry.suffix);

            if (snbtgrammar_typesuffix == null) {
                state.errorCollector().store(state.mark(), SnbtGrammar.ERROR_INVALID_ARRAY_ELEMENT_TYPE);
                return null;
            } else {
                return (Number) entry.create(JavaOps.INSTANCE, snbtgrammar_typesuffix, state);
            }
        }

        private SnbtGrammar.@Nullable TypeSuffix computeType(SnbtGrammar.IntegerSuffix value) {
            SnbtGrammar.TypeSuffix snbtgrammar_typesuffix = value.type();

            return snbtgrammar_typesuffix == null ? this.defaultType : (!this.isAllowed(snbtgrammar_typesuffix) ? null : snbtgrammar_typesuffix);
        }
    }

    private static class SimpleHexLiteralParseRule extends GreedyPredicateParseRule {

        public SimpleHexLiteralParseRule(int size) {
            super(size, size, DelayedException.create(SnbtGrammar.ERROR_EXPECTED_HEX_ESCAPE, String.valueOf(size)));
        }

        @Override
        protected boolean isAccepted(char c) {
            boolean flag;

            switch (c) {
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                case 'A':
                case 'B':
                case 'C':
                case 'D':
                case 'E':
                case 'F':
                case 'a':
                case 'b':
                case 'c':
                case 'd':
                case 'e':
                case 'f':
                    flag = true;
                    break;
                case ':':
                case ';':
                case '<':
                case '=':
                case '>':
                case '?':
                case '@':
                case 'G':
                case 'H':
                case 'I':
                case 'J':
                case 'K':
                case 'L':
                case 'M':
                case 'N':
                case 'O':
                case 'P':
                case 'Q':
                case 'R':
                case 'S':
                case 'T':
                case 'U':
                case 'V':
                case 'W':
                case 'X':
                case 'Y':
                case 'Z':
                case '[':
                case '\\':
                case ']':
                case '^':
                case '_':
                case '`':
                default:
                    flag = false;
            }

            return flag;
        }
    }

    private static record IntegerLiteral(SnbtGrammar.Sign sign, SnbtGrammar.Base base, String digits, SnbtGrammar.IntegerSuffix suffix) {

        private SnbtGrammar.SignedPrefix signedOrDefault() {
            if (this.suffix.signed != null) {
                return this.suffix.signed;
            } else {
                SnbtGrammar.SignedPrefix snbtgrammar_signedprefix;

                switch (this.base.ordinal()) {
                    case 0:
                    case 2:
                        snbtgrammar_signedprefix = SnbtGrammar.SignedPrefix.UNSIGNED;
                        break;
                    case 1:
                        snbtgrammar_signedprefix = SnbtGrammar.SignedPrefix.SIGNED;
                        break;
                    default:
                        throw new MatchException((String) null, (Throwable) null);
                }

                return snbtgrammar_signedprefix;
            }
        }

        private String cleanupDigits(SnbtGrammar.Sign sign) {
            boolean flag = SnbtGrammar.needsUnderscoreRemoval(this.digits);

            if (sign != SnbtGrammar.Sign.MINUS && !flag) {
                return this.digits;
            } else {
                StringBuilder stringbuilder = new StringBuilder();

                sign.append(stringbuilder);
                SnbtGrammar.cleanAndAppend(stringbuilder, this.digits, flag);
                return stringbuilder.toString();
            }
        }

        public <T> @Nullable T create(DynamicOps<T> ops, ParseState<?> state) {
            return (T) this.create(ops, (SnbtGrammar.TypeSuffix) Objects.requireNonNullElse(this.suffix.type, SnbtGrammar.TypeSuffix.INT), state);
        }

        public <T> @Nullable T create(DynamicOps<T> ops, SnbtGrammar.TypeSuffix type, ParseState<?> state) {
            boolean flag = this.signedOrDefault() == SnbtGrammar.SignedPrefix.SIGNED;

            if (!flag && this.sign == SnbtGrammar.Sign.MINUS) {
                state.errorCollector().store(state.mark(), SnbtGrammar.ERROR_EXPECTED_NON_NEGATIVE_NUMBER);
                return null;
            } else {
                String s = this.cleanupDigits(this.sign);
                byte b0;

                switch (this.base.ordinal()) {
                    case 0:
                        b0 = 2;
                        break;
                    case 1:
                        b0 = 10;
                        break;
                    case 2:
                        b0 = 16;
                        break;
                    default:
                        throw new MatchException((String) null, (Throwable) null);
                }

                int i = b0;

                try {
                    if (flag) {
                        Object object;

                        switch (type.ordinal()) {
                            case 2:
                                object = ops.createByte(Byte.parseByte(s, i));
                                break;
                            case 3:
                                object = ops.createShort(Short.parseShort(s, i));
                                break;
                            case 4:
                                object = ops.createInt(Integer.parseInt(s, i));
                                break;
                            case 5:
                                object = ops.createLong(Long.parseLong(s, i));
                                break;
                            default:
                                state.errorCollector().store(state.mark(), SnbtGrammar.ERROR_EXPECTED_INTEGER_TYPE);
                                object = null;
                        }

                        return (T) object;
                    } else {
                        Object object1;

                        switch (type.ordinal()) {
                            case 2:
                                object1 = ops.createByte(UnsignedBytes.parseUnsignedByte(s, i));
                                break;
                            case 3:
                                object1 = ops.createShort(SnbtGrammar.parseUnsignedShort(s, i));
                                break;
                            case 4:
                                object1 = ops.createInt(Integer.parseUnsignedInt(s, i));
                                break;
                            case 5:
                                object1 = ops.createLong(Long.parseUnsignedLong(s, i));
                                break;
                            default:
                                state.errorCollector().store(state.mark(), SnbtGrammar.ERROR_EXPECTED_INTEGER_TYPE);
                                object1 = null;
                        }

                        return (T) object1;
                    }
                } catch (NumberFormatException numberformatexception) {
                    state.errorCollector().store(state.mark(), SnbtGrammar.createNumberParseError(numberformatexception));
                    return null;
                }
            }
        }
    }

    private static record Signed<T>(SnbtGrammar.Sign sign, T value) {

    }
}
