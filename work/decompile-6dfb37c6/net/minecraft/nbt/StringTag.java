package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Optional;

public record StringTag(String value) implements PrimitiveTag {

    private static final int SELF_SIZE_IN_BYTES = 36;
    public static final TagType<StringTag> TYPE = new TagType.VariableSize<StringTag>() {
        @Override
        public StringTag load(DataInput input, NbtAccounter accounter) throws IOException {
            return StringTag.valueOf(readAccounted(input, accounter));
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor output, NbtAccounter accounter) throws IOException {
            return output.visit(readAccounted(input, accounter));
        }

        private static String readAccounted(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.accountBytes(36L);
            String s = input.readUTF();

            accounter.accountBytes(2L, (long) s.length());
            return s;
        }

        @Override
        public void skip(DataInput input, NbtAccounter accounter) throws IOException {
            StringTag.skipString(input);
        }

        @Override
        public String getName() {
            return "STRING";
        }

        @Override
        public String getPrettyName() {
            return "TAG_String";
        }
    };
    private static final StringTag EMPTY = new StringTag("");
    private static final char DOUBLE_QUOTE = '"';
    private static final char SINGLE_QUOTE = '\'';
    private static final char ESCAPE = '\\';
    private static final char NOT_SET = '\u0000';

    public static void skipString(DataInput input) throws IOException {
        input.skipBytes(input.readUnsignedShort());
    }

    public static StringTag valueOf(String data) {
        return data.isEmpty() ? StringTag.EMPTY : new StringTag(data);
    }

    @Override
    public void write(DataOutput output) throws IOException {
        output.writeUTF(this.value);
    }

    @Override
    public int sizeInBytes() {
        return 36 + 2 * this.value.length();
    }

    @Override
    public byte getId() {
        return 8;
    }

    @Override
    public TagType<StringTag> getType() {
        return StringTag.TYPE;
    }

    @Override
    public String toString() {
        StringTagVisitor stringtagvisitor = new StringTagVisitor();

        stringtagvisitor.visitString(this);
        return stringtagvisitor.build();
    }

    @Override
    public StringTag copy() {
        return this;
    }

    @Override
    public Optional<String> asString() {
        return Optional.of(this.value);
    }

    @Override
    public void accept(TagVisitor visitor) {
        visitor.visitString(this);
    }

    public static String quoteAndEscape(String input) {
        StringBuilder stringbuilder = new StringBuilder();

        quoteAndEscape(input, stringbuilder);
        return stringbuilder.toString();
    }

    public static void quoteAndEscape(String input, StringBuilder result) {
        int i = result.length();

        result.append(' ');
        char c0 = 0;

        for (int j = 0; j < input.length(); ++j) {
            char c1 = input.charAt(j);

            if (c1 == '\\') {
                result.append("\\\\");
            } else if (c1 != '"' && c1 != '\'') {
                String s1 = SnbtGrammar.escapeControlCharacters(c1);

                if (s1 != null) {
                    result.append('\\');
                    result.append(s1);
                } else {
                    result.append(c1);
                }
            } else {
                if (c0 == 0) {
                    c0 = (char) (c1 == '"' ? 39 : 34);
                }

                if (c0 == c1) {
                    result.append('\\');
                }

                result.append(c1);
            }
        }

        if (c0 == 0) {
            c0 = '"';
        }

        result.setCharAt(i, c0);
        result.append(c0);
    }

    public static String escapeWithoutQuotes(String input) {
        StringBuilder stringbuilder = new StringBuilder();

        escapeWithoutQuotes(input, stringbuilder);
        return stringbuilder.toString();
    }

    public static void escapeWithoutQuotes(String input, StringBuilder result) {
        for (int i = 0; i < input.length(); ++i) {
            char c0 = input.charAt(i);

            switch (c0) {
                case '"':
                case '\'':
                case '\\':
                    result.append('\\');
                    result.append(c0);
                    break;
                default:
                    String s1 = SnbtGrammar.escapeControlCharacters(c0);

                    if (s1 != null) {
                        result.append('\\');
                        result.append(s1);
                    } else {
                        result.append(c0);
                    }
            }
        }

    }

    @Override
    public StreamTagVisitor.ValueResult accept(StreamTagVisitor visitor) {
        return visitor.visit(this.value);
    }
}
