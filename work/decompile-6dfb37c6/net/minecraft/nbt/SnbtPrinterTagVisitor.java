package net.minecraft.nbt;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.util.Util;

public class SnbtPrinterTagVisitor implements TagVisitor {

    private static final Map<String, List<String>> KEY_ORDER = (Map) Util.make(Maps.newHashMap(), (hashmap) -> {
        hashmap.put("{}", Lists.newArrayList(new String[]{"DataVersion", "author", "size", "data", "entities", "palette", "palettes"}));
        hashmap.put("{}.data.[].{}", Lists.newArrayList(new String[]{"pos", "state", "nbt"}));
        hashmap.put("{}.entities.[].{}", Lists.newArrayList(new String[]{"blockPos", "pos"}));
    });
    private static final Set<String> NO_INDENTATION = Sets.newHashSet(new String[]{"{}.size.[]", "{}.data.[].{}", "{}.palette.[].{}", "{}.entities.[].{}"});
    private static final Pattern SIMPLE_VALUE = Pattern.compile("[A-Za-z0-9._+-]+");
    private static final String NAME_VALUE_SEPARATOR = String.valueOf(':');
    private static final String ELEMENT_SEPARATOR = String.valueOf(',');
    private static final String LIST_OPEN = "[";
    private static final String LIST_CLOSE = "]";
    private static final String LIST_TYPE_SEPARATOR = ";";
    private static final String ELEMENT_SPACING = " ";
    private static final String STRUCT_OPEN = "{";
    private static final String STRUCT_CLOSE = "}";
    private static final String NEWLINE = "\n";
    private final String indentation;
    private final int depth;
    private final List<String> path;
    private String result;

    public SnbtPrinterTagVisitor() {
        this("    ", 0, Lists.newArrayList());
    }

    public SnbtPrinterTagVisitor(String indentation, int depth, List<String> path) {
        this.result = "";
        this.indentation = indentation;
        this.depth = depth;
        this.path = path;
    }

    public String visit(Tag tag) {
        tag.accept((TagVisitor) this);
        return this.result;
    }

    @Override
    public void visitString(StringTag tag) {
        this.result = StringTag.quoteAndEscape(tag.value());
    }

    @Override
    public void visitByte(ByteTag tag) {
        this.result = tag.value() + "b";
    }

    @Override
    public void visitShort(ShortTag tag) {
        this.result = tag.value() + "s";
    }

    @Override
    public void visitInt(IntTag tag) {
        this.result = String.valueOf(tag.value());
    }

    @Override
    public void visitLong(LongTag tag) {
        this.result = tag.value() + "L";
    }

    @Override
    public void visitFloat(FloatTag tag) {
        this.result = tag.value() + "f";
    }

    @Override
    public void visitDouble(DoubleTag tag) {
        this.result = tag.value() + "d";
    }

    @Override
    public void visitByteArray(ByteArrayTag tag) {
        StringBuilder stringbuilder = (new StringBuilder("[")).append("B").append(";");
        byte[] abyte = tag.getAsByteArray();

        for (int i = 0; i < abyte.length; ++i) {
            stringbuilder.append(" ").append(abyte[i]).append("B");
            if (i != abyte.length - 1) {
                stringbuilder.append(SnbtPrinterTagVisitor.ELEMENT_SEPARATOR);
            }
        }

        stringbuilder.append("]");
        this.result = stringbuilder.toString();
    }

    @Override
    public void visitIntArray(IntArrayTag tag) {
        StringBuilder stringbuilder = (new StringBuilder("[")).append("I").append(";");
        int[] aint = tag.getAsIntArray();

        for (int i = 0; i < aint.length; ++i) {
            stringbuilder.append(" ").append(aint[i]);
            if (i != aint.length - 1) {
                stringbuilder.append(SnbtPrinterTagVisitor.ELEMENT_SEPARATOR);
            }
        }

        stringbuilder.append("]");
        this.result = stringbuilder.toString();
    }

    @Override
    public void visitLongArray(LongArrayTag tag) {
        String s = "L";
        StringBuilder stringbuilder = (new StringBuilder("[")).append("L").append(";");
        long[] along = tag.getAsLongArray();

        for (int i = 0; i < along.length; ++i) {
            stringbuilder.append(" ").append(along[i]).append("L");
            if (i != along.length - 1) {
                stringbuilder.append(SnbtPrinterTagVisitor.ELEMENT_SEPARATOR);
            }
        }

        stringbuilder.append("]");
        this.result = stringbuilder.toString();
    }

    @Override
    public void visitList(ListTag tag) {
        if (tag.isEmpty()) {
            this.result = "[]";
        } else {
            StringBuilder stringbuilder = new StringBuilder("[");

            this.pushPath("[]");
            String s = SnbtPrinterTagVisitor.NO_INDENTATION.contains(this.pathString()) ? "" : this.indentation;

            if (!s.isEmpty()) {
                stringbuilder.append("\n");
            }

            for (int i = 0; i < tag.size(); ++i) {
                stringbuilder.append(Strings.repeat(s, this.depth + 1));
                stringbuilder.append((new SnbtPrinterTagVisitor(s, this.depth + 1, this.path)).visit(tag.get(i)));
                if (i != tag.size() - 1) {
                    stringbuilder.append(SnbtPrinterTagVisitor.ELEMENT_SEPARATOR).append(s.isEmpty() ? " " : "\n");
                }
            }

            if (!s.isEmpty()) {
                stringbuilder.append("\n").append(Strings.repeat(s, this.depth));
            }

            stringbuilder.append("]");
            this.result = stringbuilder.toString();
            this.popPath();
        }
    }

    @Override
    public void visitCompound(CompoundTag tag) {
        if (tag.isEmpty()) {
            this.result = "{}";
        } else {
            StringBuilder stringbuilder = new StringBuilder("{");

            this.pushPath("{}");
            String s = SnbtPrinterTagVisitor.NO_INDENTATION.contains(this.pathString()) ? "" : this.indentation;

            if (!s.isEmpty()) {
                stringbuilder.append("\n");
            }

            Collection<String> collection = this.getKeys(tag);
            Iterator<String> iterator = collection.iterator();

            while (iterator.hasNext()) {
                String s1 = (String) iterator.next();
                Tag tag1 = tag.get(s1);

                this.pushPath(s1);
                stringbuilder.append(Strings.repeat(s, this.depth + 1)).append(handleEscapePretty(s1)).append(SnbtPrinterTagVisitor.NAME_VALUE_SEPARATOR).append(" ").append((new SnbtPrinterTagVisitor(s, this.depth + 1, this.path)).visit(tag1));
                this.popPath();
                if (iterator.hasNext()) {
                    stringbuilder.append(SnbtPrinterTagVisitor.ELEMENT_SEPARATOR).append(s.isEmpty() ? " " : "\n");
                }
            }

            if (!s.isEmpty()) {
                stringbuilder.append("\n").append(Strings.repeat(s, this.depth));
            }

            stringbuilder.append("}");
            this.result = stringbuilder.toString();
            this.popPath();
        }
    }

    private void popPath() {
        this.path.remove(this.path.size() - 1);
    }

    private void pushPath(String e) {
        this.path.add(e);
    }

    protected List<String> getKeys(CompoundTag tag) {
        Set<String> set = Sets.newHashSet(tag.keySet());
        List<String> list = Lists.newArrayList();
        List<String> list1 = (List) SnbtPrinterTagVisitor.KEY_ORDER.get(this.pathString());

        if (list1 != null) {
            for (String s : list1) {
                if (set.remove(s)) {
                    list.add(s);
                }
            }

            if (!set.isEmpty()) {
                Stream stream = set.stream().sorted();

                Objects.requireNonNull(list);
                stream.forEach(list::add);
            }
        } else {
            list.addAll(set);
            Collections.sort(list);
        }

        return list;
    }

    public String pathString() {
        return String.join(".", this.path);
    }

    protected static String handleEscapePretty(String input) {
        return SnbtPrinterTagVisitor.SIMPLE_VALUE.matcher(input).matches() ? input : StringTag.quoteAndEscape(input);
    }

    @Override
    public void visitEnd(EndTag tag) {}
}
