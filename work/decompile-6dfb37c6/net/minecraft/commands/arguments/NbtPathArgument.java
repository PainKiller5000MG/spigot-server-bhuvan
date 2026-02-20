package net.minecraft.commands.arguments;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.mutable.MutableBoolean;

public class NbtPathArgument implements ArgumentType<NbtPathArgument.NbtPath> {

    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo.bar", "foo[0]", "[0]", "[]", "{foo=bar}");
    public static final SimpleCommandExceptionType ERROR_INVALID_NODE = new SimpleCommandExceptionType(Component.translatable("arguments.nbtpath.node.invalid"));
    public static final SimpleCommandExceptionType ERROR_DATA_TOO_DEEP = new SimpleCommandExceptionType(Component.translatable("arguments.nbtpath.too_deep"));
    public static final DynamicCommandExceptionType ERROR_NOTHING_FOUND = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("arguments.nbtpath.nothing_found", object);
    });
    private static final DynamicCommandExceptionType ERROR_EXPECTED_LIST = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.data.modify.expected_list", object);
    });
    private static final DynamicCommandExceptionType ERROR_INVALID_INDEX = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.data.modify.invalid_index", object);
    });
    private static final char INDEX_MATCH_START = '[';
    private static final char INDEX_MATCH_END = ']';
    private static final char KEY_MATCH_START = '{';
    private static final char KEY_MATCH_END = '}';
    private static final char QUOTED_KEY_START = '"';
    private static final char SINGLE_QUOTED_KEY_START = '\'';

    public NbtPathArgument() {}

    public static NbtPathArgument nbtPath() {
        return new NbtPathArgument();
    }

    public static NbtPathArgument.NbtPath getPath(CommandContext<CommandSourceStack> context, String name) {
        return (NbtPathArgument.NbtPath) context.getArgument(name, NbtPathArgument.NbtPath.class);
    }

    public NbtPathArgument.NbtPath parse(StringReader reader) throws CommandSyntaxException {
        List<NbtPathArgument.Node> list = Lists.newArrayList();
        int i = reader.getCursor();
        Object2IntMap<NbtPathArgument.Node> object2intmap = new Object2IntOpenHashMap();
        boolean flag = true;

        while (reader.canRead() && reader.peek() != ' ') {
            NbtPathArgument.Node nbtpathargument_node = parseNode(reader, flag);

            list.add(nbtpathargument_node);
            object2intmap.put(nbtpathargument_node, reader.getCursor() - i);
            flag = false;
            if (reader.canRead()) {
                char c0 = reader.peek();

                if (c0 != ' ' && c0 != '[' && c0 != '{') {
                    reader.expect('.');
                }
            }
        }

        return new NbtPathArgument.NbtPath(reader.getString().substring(i, reader.getCursor()), (NbtPathArgument.Node[]) list.toArray(new NbtPathArgument.Node[0]), object2intmap);
    }

    private static NbtPathArgument.Node parseNode(StringReader reader, boolean firstNode) throws CommandSyntaxException {
        Object object;

        switch (reader.peek()) {
            case '"':
            case '\'':
                object = readObjectNode(reader, reader.readString());
                break;
            case '[':
                reader.skip();
                int i = reader.peek();

                if (i == 123) {
                    CompoundTag compoundtag = TagParser.parseCompoundAsArgument(reader);

                    reader.expect(']');
                    object = new NbtPathArgument.MatchElementNode(compoundtag);
                } else if (i == 93) {
                    reader.skip();
                    object = NbtPathArgument.AllElementsNode.INSTANCE;
                } else {
                    int j = reader.readInt();

                    reader.expect(']');
                    object = new NbtPathArgument.IndexedElementNode(j);
                }
                break;
            case '{':
                if (!firstNode) {
                    throw NbtPathArgument.ERROR_INVALID_NODE.createWithContext(reader);
                }

                CompoundTag compoundtag1 = TagParser.parseCompoundAsArgument(reader);

                object = new NbtPathArgument.MatchRootObjectNode(compoundtag1);
                break;
            default:
                object = readObjectNode(reader, readUnquotedName(reader));
        }

        return (NbtPathArgument.Node) object;
    }

    private static NbtPathArgument.Node readObjectNode(StringReader reader, String name) throws CommandSyntaxException {
        if (name.isEmpty()) {
            throw NbtPathArgument.ERROR_INVALID_NODE.createWithContext(reader);
        } else if (reader.canRead() && reader.peek() == '{') {
            CompoundTag compoundtag = TagParser.parseCompoundAsArgument(reader);

            return new NbtPathArgument.MatchObjectNode(name, compoundtag);
        } else {
            return new NbtPathArgument.CompoundChildNode(name);
        }
    }

    private static String readUnquotedName(StringReader reader) throws CommandSyntaxException {
        int i = reader.getCursor();

        while (reader.canRead() && isAllowedInUnquotedName(reader.peek())) {
            reader.skip();
        }

        if (reader.getCursor() == i) {
            throw NbtPathArgument.ERROR_INVALID_NODE.createWithContext(reader);
        } else {
            return reader.getString().substring(i, reader.getCursor());
        }
    }

    public Collection<String> getExamples() {
        return NbtPathArgument.EXAMPLES;
    }

    private static boolean isAllowedInUnquotedName(char c) {
        return c != ' ' && c != '"' && c != '\'' && c != '[' && c != ']' && c != '.' && c != '{' && c != '}';
    }

    private static Predicate<Tag> createTagPredicate(CompoundTag pattern) {
        return (tag) -> {
            return NbtUtils.compareNbt(pattern, tag, true);
        };
    }

    public static class NbtPath {

        private final String original;
        private final Object2IntMap<NbtPathArgument.Node> nodeToOriginalPosition;
        private final NbtPathArgument.Node[] nodes;
        public static final Codec<NbtPathArgument.NbtPath> CODEC = Codec.STRING.comapFlatMap((s) -> {
            try {
                NbtPathArgument.NbtPath nbtpathargument_nbtpath = (new NbtPathArgument()).parse(new StringReader(s));

                return DataResult.success(nbtpathargument_nbtpath);
            } catch (CommandSyntaxException commandsyntaxexception) {
                return DataResult.error(() -> {
                    return "Failed to parse path " + s + ": " + commandsyntaxexception.getMessage();
                });
            }
        }, NbtPathArgument.NbtPath::asString);

        public static NbtPathArgument.NbtPath of(String string) throws CommandSyntaxException {
            return (new NbtPathArgument()).parse(new StringReader(string));
        }

        public NbtPath(String original, NbtPathArgument.Node[] nodes, Object2IntMap<NbtPathArgument.Node> nodeToOriginalPosition) {
            this.original = original;
            this.nodes = nodes;
            this.nodeToOriginalPosition = nodeToOriginalPosition;
        }

        public List<Tag> get(Tag tag) throws CommandSyntaxException {
            List<Tag> list = Collections.singletonList(tag);

            for (NbtPathArgument.Node nbtpathargument_node : this.nodes) {
                list = nbtpathargument_node.get(list);
                if (list.isEmpty()) {
                    throw this.createNotFoundException(nbtpathargument_node);
                }
            }

            return list;
        }

        public int countMatching(Tag tag) {
            List<Tag> list = Collections.singletonList(tag);

            for (NbtPathArgument.Node nbtpathargument_node : this.nodes) {
                list = nbtpathargument_node.get(list);
                if (list.isEmpty()) {
                    return 0;
                }
            }

            return list.size();
        }

        private List<Tag> getOrCreateParents(Tag tag) throws CommandSyntaxException {
            List<Tag> list = Collections.singletonList(tag);

            for (int i = 0; i < this.nodes.length - 1; ++i) {
                NbtPathArgument.Node nbtpathargument_node = this.nodes[i];
                int j = i + 1;
                NbtPathArgument.Node nbtpathargument_node1 = this.nodes[j];

                Objects.requireNonNull(this.nodes[j]);
                list = nbtpathargument_node.getOrCreate(list, nbtpathargument_node1::createPreferredParentTag);
                if (list.isEmpty()) {
                    throw this.createNotFoundException(nbtpathargument_node);
                }
            }

            return list;
        }

        public List<Tag> getOrCreate(Tag tag, Supplier<Tag> newTagValue) throws CommandSyntaxException {
            List<Tag> list = this.getOrCreateParents(tag);
            NbtPathArgument.Node nbtpathargument_node = this.nodes[this.nodes.length - 1];

            return nbtpathargument_node.getOrCreate(list, newTagValue);
        }

        private static int apply(List<Tag> targets, Function<Tag, Integer> operation) {
            return (Integer) targets.stream().map(operation).reduce(0, (integer, integer1) -> {
                return integer + integer1;
            });
        }

        public static boolean isTooDeep(Tag tag, int depth) {
            if (depth >= 512) {
                return true;
            } else {
                if (tag instanceof CompoundTag) {
                    CompoundTag compoundtag = (CompoundTag) tag;

                    for (Tag tag1 : compoundtag.values()) {
                        if (isTooDeep(tag1, depth + 1)) {
                            return true;
                        }
                    }
                } else if (tag instanceof ListTag) {
                    for (Tag tag2 : (ListTag) tag) {
                        if (isTooDeep(tag2, depth + 1)) {
                            return true;
                        }
                    }
                }

                return false;
            }
        }

        public int set(Tag tag, Tag toAdd) throws CommandSyntaxException {
            if (isTooDeep(toAdd, this.estimatePathDepth())) {
                throw NbtPathArgument.ERROR_DATA_TOO_DEEP.create();
            } else {
                Tag tag2 = toAdd.copy();
                List<Tag> list = this.getOrCreateParents(tag);

                if (list.isEmpty()) {
                    return 0;
                } else {
                    NbtPathArgument.Node nbtpathargument_node = this.nodes[this.nodes.length - 1];
                    MutableBoolean mutableboolean = new MutableBoolean(false);

                    return apply(list, (tag3) -> {
                        return nbtpathargument_node.setTag(tag3, () -> {
                            if (mutableboolean.isFalse()) {
                                mutableboolean.setTrue();
                                return tag2;
                            } else {
                                return tag2.copy();
                            }
                        });
                    });
                }
            }
        }

        private int estimatePathDepth() {
            return this.nodes.length;
        }

        public int insert(int index, CompoundTag target, List<Tag> toInsert) throws CommandSyntaxException {
            List<Tag> list1 = new ArrayList(toInsert.size());

            for (Tag tag : toInsert) {
                Tag tag1 = tag.copy();

                list1.add(tag1);
                if (isTooDeep(tag1, this.estimatePathDepth())) {
                    throw NbtPathArgument.ERROR_DATA_TOO_DEEP.create();
                }
            }

            Collection<Tag> collection = this.getOrCreate(target, ListTag::new);
            int j = 0;
            boolean flag = false;

            for (Tag tag2 : collection) {
                if (!(tag2 instanceof CollectionTag)) {
                    throw NbtPathArgument.ERROR_EXPECTED_LIST.create(tag2);
                }

                CollectionTag collectiontag = (CollectionTag) tag2;
                boolean flag1 = false;
                int k = index < 0 ? collectiontag.size() + index + 1 : index;

                for (Tag tag3 : list1) {
                    try {
                        if (collectiontag.addTag(k, flag ? tag3.copy() : tag3)) {
                            ++k;
                            flag1 = true;
                        }
                    } catch (IndexOutOfBoundsException indexoutofboundsexception) {
                        throw NbtPathArgument.ERROR_INVALID_INDEX.create(k);
                    }
                }

                flag = true;
                j += flag1 ? 1 : 0;
            }

            return j;
        }

        public int remove(Tag tag) {
            List<Tag> list = Collections.singletonList(tag);

            for (int i = 0; i < this.nodes.length - 1; ++i) {
                list = this.nodes[i].get(list);
            }

            NbtPathArgument.Node nbtpathargument_node = this.nodes[this.nodes.length - 1];

            Objects.requireNonNull(nbtpathargument_node);
            return apply(list, nbtpathargument_node::removeTag);
        }

        private CommandSyntaxException createNotFoundException(NbtPathArgument.Node node) {
            int i = this.nodeToOriginalPosition.getInt(node);

            return NbtPathArgument.ERROR_NOTHING_FOUND.create(this.original.substring(0, i));
        }

        public String toString() {
            return this.original;
        }

        public String asString() {
            return this.original;
        }
    }

    private interface Node {

        void getTag(Tag parent, List<Tag> output);

        void getOrCreateTag(Tag parent, Supplier<Tag> child, List<Tag> output);

        Tag createPreferredParentTag();

        int setTag(Tag parent, Supplier<Tag> toAdd);

        int removeTag(Tag parent);

        default List<Tag> get(List<Tag> tags) {
            return this.collect(tags, this::getTag);
        }

        default List<Tag> getOrCreate(List<Tag> tags, Supplier<Tag> child) {
            return this.collect(tags, (tag, list1) -> {
                this.getOrCreateTag(tag, child, list1);
            });
        }

        default List<Tag> collect(List<Tag> tags, BiConsumer<Tag, List<Tag>> collector) {
            List<Tag> list1 = Lists.newArrayList();

            for (Tag tag : tags) {
                collector.accept(tag, list1);
            }

            return list1;
        }
    }

    private static class CompoundChildNode implements NbtPathArgument.Node {

        private final String name;

        public CompoundChildNode(String name) {
            this.name = name;
        }

        @Override
        public void getTag(Tag parent, List<Tag> output) {
            if (parent instanceof CompoundTag) {
                Tag tag1 = ((CompoundTag) parent).get(this.name);

                if (tag1 != null) {
                    output.add(tag1);
                }
            }

        }

        @Override
        public void getOrCreateTag(Tag parent, Supplier<Tag> child, List<Tag> output) {
            if (parent instanceof CompoundTag compoundtag) {
                Tag tag1;

                if (compoundtag.contains(this.name)) {
                    tag1 = compoundtag.get(this.name);
                } else {
                    tag1 = (Tag) child.get();
                    compoundtag.put(this.name, tag1);
                }

                output.add(tag1);
            }

        }

        @Override
        public Tag createPreferredParentTag() {
            return new CompoundTag();
        }

        @Override
        public int setTag(Tag parent, Supplier<Tag> toAdd) {
            if (parent instanceof CompoundTag compoundtag) {
                Tag tag1 = (Tag) toAdd.get();
                Tag tag2 = compoundtag.put(this.name, tag1);

                if (!tag1.equals(tag2)) {
                    return 1;
                }
            }

            return 0;
        }

        @Override
        public int removeTag(Tag parent) {
            if (parent instanceof CompoundTag compoundtag) {
                if (compoundtag.contains(this.name)) {
                    compoundtag.remove(this.name);
                    return 1;
                }
            }

            return 0;
        }
    }

    private static class IndexedElementNode implements NbtPathArgument.Node {

        private final int index;

        public IndexedElementNode(int index) {
            this.index = index;
        }

        @Override
        public void getTag(Tag parent, List<Tag> output) {
            if (parent instanceof CollectionTag collectiontag) {
                int i = collectiontag.size();
                int j = this.index < 0 ? i + this.index : this.index;

                if (0 <= j && j < i) {
                    output.add(collectiontag.get(j));
                }
            }

        }

        @Override
        public void getOrCreateTag(Tag parent, Supplier<Tag> child, List<Tag> output) {
            this.getTag(parent, output);
        }

        @Override
        public Tag createPreferredParentTag() {
            return new ListTag();
        }

        @Override
        public int setTag(Tag parent, Supplier<Tag> toAdd) {
            if (parent instanceof CollectionTag collectiontag) {
                int i = collectiontag.size();
                int j = this.index < 0 ? i + this.index : this.index;

                if (0 <= j && j < i) {
                    Tag tag1 = collectiontag.get(j);
                    Tag tag2 = (Tag) toAdd.get();

                    if (!tag2.equals(tag1) && collectiontag.setTag(j, tag2)) {
                        return 1;
                    }
                }
            }

            return 0;
        }

        @Override
        public int removeTag(Tag parent) {
            if (parent instanceof CollectionTag collectiontag) {
                int i = collectiontag.size();
                int j = this.index < 0 ? i + this.index : this.index;

                if (0 <= j && j < i) {
                    collectiontag.remove(j);
                    return 1;
                }
            }

            return 0;
        }
    }

    private static class MatchElementNode implements NbtPathArgument.Node {

        private final CompoundTag pattern;
        private final Predicate<Tag> predicate;

        public MatchElementNode(CompoundTag pattern) {
            this.pattern = pattern;
            this.predicate = NbtPathArgument.createTagPredicate(pattern);
        }

        @Override
        public void getTag(Tag parent, List<Tag> output) {
            if (parent instanceof ListTag listtag) {
                Stream stream = listtag.stream().filter(this.predicate);

                Objects.requireNonNull(output);
                stream.forEach(output::add);
            }

        }

        @Override
        public void getOrCreateTag(Tag parent, Supplier<Tag> child, List<Tag> output) {
            MutableBoolean mutableboolean = new MutableBoolean();

            if (parent instanceof ListTag listtag) {
                listtag.stream().filter(this.predicate).forEach((tag1) -> {
                    output.add(tag1);
                    mutableboolean.setTrue();
                });
                if (mutableboolean.isFalse()) {
                    CompoundTag compoundtag = this.pattern.copy();

                    listtag.add(compoundtag);
                    output.add(compoundtag);
                }
            }

        }

        @Override
        public Tag createPreferredParentTag() {
            return new ListTag();
        }

        @Override
        public int setTag(Tag parent, Supplier<Tag> toAdd) {
            int i = 0;

            if (parent instanceof ListTag listtag) {
                int j = listtag.size();

                if (j == 0) {
                    listtag.add((Tag) toAdd.get());
                    ++i;
                } else {
                    for (int k = 0; k < j; ++k) {
                        Tag tag1 = listtag.get(k);

                        if (this.predicate.test(tag1)) {
                            Tag tag2 = (Tag) toAdd.get();

                            if (!tag2.equals(tag1) && listtag.setTag(k, tag2)) {
                                ++i;
                            }
                        }
                    }
                }
            }

            return i;
        }

        @Override
        public int removeTag(Tag parent) {
            int i = 0;

            if (parent instanceof ListTag listtag) {
                for (int j = listtag.size() - 1; j >= 0; --j) {
                    if (this.predicate.test(listtag.get(j))) {
                        listtag.remove(j);
                        ++i;
                    }
                }
            }

            return i;
        }
    }

    private static class AllElementsNode implements NbtPathArgument.Node {

        public static final NbtPathArgument.AllElementsNode INSTANCE = new NbtPathArgument.AllElementsNode();

        private AllElementsNode() {}

        @Override
        public void getTag(Tag parent, List<Tag> output) {
            if (parent instanceof CollectionTag collectiontag) {
                Iterables.addAll(output, collectiontag);
            }

        }

        @Override
        public void getOrCreateTag(Tag parent, Supplier<Tag> child, List<Tag> output) {
            if (parent instanceof CollectionTag collectiontag) {
                if (collectiontag.isEmpty()) {
                    Tag tag1 = (Tag) child.get();

                    if (collectiontag.addTag(0, tag1)) {
                        output.add(tag1);
                    }
                } else {
                    Iterables.addAll(output, collectiontag);
                }
            }

        }

        @Override
        public Tag createPreferredParentTag() {
            return new ListTag();
        }

        @Override
        public int setTag(Tag parent, Supplier<Tag> toAdd) {
            if (!(parent instanceof CollectionTag collectiontag)) {
                return 0;
            } else {
                int i = collectiontag.size();

                if (i == 0) {
                    collectiontag.addTag(0, (Tag) toAdd.get());
                    return 1;
                } else {
                    Tag tag1 = (Tag) toAdd.get();
                    Stream stream = collectiontag.stream();

                    Objects.requireNonNull(tag1);
                    int j = i - (int) stream.filter(tag1::equals).count();

                    if (j == 0) {
                        return 0;
                    } else {
                        collectiontag.clear();
                        if (!collectiontag.addTag(0, tag1)) {
                            return 0;
                        } else {
                            for (int k = 1; k < i; ++k) {
                                collectiontag.addTag(k, (Tag) toAdd.get());
                            }

                            return j;
                        }
                    }
                }
            }
        }

        @Override
        public int removeTag(Tag parent) {
            if (parent instanceof CollectionTag collectiontag) {
                int i = collectiontag.size();

                if (i > 0) {
                    collectiontag.clear();
                    return i;
                }
            }

            return 0;
        }
    }

    private static class MatchObjectNode implements NbtPathArgument.Node {

        private final String name;
        private final CompoundTag pattern;
        private final Predicate<Tag> predicate;

        public MatchObjectNode(String name, CompoundTag pattern) {
            this.name = name;
            this.pattern = pattern;
            this.predicate = NbtPathArgument.createTagPredicate(pattern);
        }

        @Override
        public void getTag(Tag parent, List<Tag> output) {
            if (parent instanceof CompoundTag) {
                Tag tag1 = ((CompoundTag) parent).get(this.name);

                if (this.predicate.test(tag1)) {
                    output.add(tag1);
                }
            }

        }

        @Override
        public void getOrCreateTag(Tag parent, Supplier<Tag> child, List<Tag> output) {
            if (parent instanceof CompoundTag compoundtag) {
                Tag tag1 = compoundtag.get(this.name);

                if (tag1 == null) {
                    CompoundTag compoundtag1 = this.pattern.copy();

                    compoundtag.put(this.name, compoundtag1);
                    output.add(compoundtag1);
                } else if (this.predicate.test(tag1)) {
                    output.add(tag1);
                }
            }

        }

        @Override
        public Tag createPreferredParentTag() {
            return new CompoundTag();
        }

        @Override
        public int setTag(Tag parent, Supplier<Tag> toAdd) {
            if (parent instanceof CompoundTag compoundtag) {
                Tag tag1 = compoundtag.get(this.name);

                if (this.predicate.test(tag1)) {
                    Tag tag2 = (Tag) toAdd.get();

                    if (!tag2.equals(tag1)) {
                        compoundtag.put(this.name, tag2);
                        return 1;
                    }
                }
            }

            return 0;
        }

        @Override
        public int removeTag(Tag parent) {
            if (parent instanceof CompoundTag compoundtag) {
                Tag tag1 = compoundtag.get(this.name);

                if (this.predicate.test(tag1)) {
                    compoundtag.remove(this.name);
                    return 1;
                }
            }

            return 0;
        }
    }

    private static class MatchRootObjectNode implements NbtPathArgument.Node {

        private final Predicate<Tag> predicate;

        public MatchRootObjectNode(CompoundTag pattern) {
            this.predicate = NbtPathArgument.createTagPredicate(pattern);
        }

        @Override
        public void getTag(Tag self, List<Tag> output) {
            if (self instanceof CompoundTag && this.predicate.test(self)) {
                output.add(self);
            }

        }

        @Override
        public void getOrCreateTag(Tag self, Supplier<Tag> child, List<Tag> output) {
            this.getTag(self, output);
        }

        @Override
        public Tag createPreferredParentTag() {
            return new CompoundTag();
        }

        @Override
        public int setTag(Tag parent, Supplier<Tag> toAdd) {
            return 0;
        }

        @Override
        public int removeTag(Tag parent) {
            return 0;
        }
    }
}
