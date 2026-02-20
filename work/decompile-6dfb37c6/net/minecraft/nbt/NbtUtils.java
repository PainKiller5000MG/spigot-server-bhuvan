package net.minecraft.nbt;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Comparators;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class NbtUtils {

    private static final Comparator<ListTag> YXZ_LISTTAG_INT_COMPARATOR = Comparator.comparingInt((listtag) -> {
        return listtag.getIntOr(1, 0);
    }).thenComparingInt((listtag) -> {
        return listtag.getIntOr(0, 0);
    }).thenComparingInt((listtag) -> {
        return listtag.getIntOr(2, 0);
    });
    private static final Comparator<ListTag> YXZ_LISTTAG_DOUBLE_COMPARATOR = Comparator.comparingDouble((listtag) -> {
        return listtag.getDoubleOr(1, 0.0D);
    }).thenComparingDouble((listtag) -> {
        return listtag.getDoubleOr(0, 0.0D);
    }).thenComparingDouble((listtag) -> {
        return listtag.getDoubleOr(2, 0.0D);
    });
    private static final Codec<ResourceKey<Block>> BLOCK_NAME_CODEC = ResourceKey.codec(Registries.BLOCK);
    public static final String SNBT_DATA_TAG = "data";
    private static final char PROPERTIES_START = '{';
    private static final char PROPERTIES_END = '}';
    private static final String ELEMENT_SEPARATOR = ",";
    private static final char KEY_VALUE_SEPARATOR = ':';
    private static final Splitter COMMA_SPLITTER = Splitter.on(",");
    private static final Splitter COLON_SPLITTER = Splitter.on(':').limit(2);
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int INDENT = 2;
    private static final int NOT_FOUND = -1;

    private NbtUtils() {}

    @VisibleForTesting
    public static boolean compareNbt(@Nullable Tag expected, @Nullable Tag actual, boolean partialListMatches) {
        if (expected == actual) {
            return true;
        } else if (expected == null) {
            return true;
        } else if (actual == null) {
            return false;
        } else if (!expected.getClass().equals(actual.getClass())) {
            return false;
        } else if (expected instanceof CompoundTag) {
            CompoundTag compoundtag = (CompoundTag) expected;
            CompoundTag compoundtag1 = (CompoundTag) actual;

            if (compoundtag1.size() < compoundtag.size()) {
                return false;
            } else {
                for (Map.Entry<String, Tag> map_entry : compoundtag.entrySet()) {
                    Tag tag2 = (Tag) map_entry.getValue();

                    if (!compareNbt(tag2, compoundtag1.get((String) map_entry.getKey()), partialListMatches)) {
                        return false;
                    }
                }

                return true;
            }
        } else {
            if (expected instanceof ListTag) {
                ListTag listtag = (ListTag) expected;

                if (partialListMatches) {
                    ListTag listtag1 = (ListTag) actual;

                    if (listtag.isEmpty()) {
                        return listtag1.isEmpty();
                    }

                    if (listtag1.size() < listtag.size()) {
                        return false;
                    }

                    for (Tag tag3 : listtag) {
                        boolean flag1 = false;

                        for (Tag tag4 : listtag1) {
                            if (compareNbt(tag3, tag4, partialListMatches)) {
                                flag1 = true;
                                break;
                            }
                        }

                        if (!flag1) {
                            return false;
                        }
                    }

                    return true;
                }
            }

            return expected.equals(actual);
        }
    }

    public static BlockState readBlockState(HolderGetter<Block> blocks, CompoundTag tag) {
        Optional optional = tag.read("Name", NbtUtils.BLOCK_NAME_CODEC);

        Objects.requireNonNull(blocks);
        Optional<? extends Holder<Block>> optional1 = optional.flatMap(blocks::get);

        if (optional1.isEmpty()) {
            return Blocks.AIR.defaultBlockState();
        } else {
            Block block = (Block) ((Holder) optional1.get()).value();
            BlockState blockstate = block.defaultBlockState();
            Optional<CompoundTag> optional2 = tag.getCompound("Properties");

            if (optional2.isPresent()) {
                StateDefinition<Block, BlockState> statedefinition = block.getStateDefinition();

                for (String s : ((CompoundTag) optional2.get()).keySet()) {
                    Property<?> property = statedefinition.getProperty(s);

                    if (property != null) {
                        blockstate = (BlockState) setValueHelper(blockstate, property, s, (CompoundTag) optional2.get(), tag);
                    }
                }
            }

            return blockstate;
        }
    }

    private static <S extends StateHolder<?, S>, T extends Comparable<T>> S setValueHelper(S result, Property<T> property, String key, CompoundTag properties, CompoundTag tag) {
        Optional optional = properties.getString(key);

        Objects.requireNonNull(property);
        Optional<T> optional1 = optional.flatMap(property::getValue);

        if (optional1.isPresent()) {
            return (S) (((StateHolder) result).setValue(property, (Comparable) optional1.get()));
        } else {
            NbtUtils.LOGGER.warn("Unable to read property: {} with value: {} for blockstate: {}", new Object[]{key, properties.get(key), tag});
            return result;
        }
    }

    public static CompoundTag writeBlockState(BlockState state) {
        CompoundTag compoundtag = new CompoundTag();

        compoundtag.putString("Name", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        Map<Property<?>, Comparable<?>> map = state.getValues();

        if (!map.isEmpty()) {
            CompoundTag compoundtag1 = new CompoundTag();

            for (Map.Entry<Property<?>, Comparable<?>> map_entry : map.entrySet()) {
                Property<?> property = (Property) map_entry.getKey();

                compoundtag1.putString(property.getName(), getName(property, (Comparable) map_entry.getValue()));
            }

            compoundtag.put("Properties", compoundtag1);
        }

        return compoundtag;
    }

    public static CompoundTag writeFluidState(FluidState state) {
        CompoundTag compoundtag = new CompoundTag();

        compoundtag.putString("Name", BuiltInRegistries.FLUID.getKey(state.getType()).toString());
        Map<Property<?>, Comparable<?>> map = state.getValues();

        if (!map.isEmpty()) {
            CompoundTag compoundtag1 = new CompoundTag();

            for (Map.Entry<Property<?>, Comparable<?>> map_entry : map.entrySet()) {
                Property<?> property = (Property) map_entry.getKey();

                compoundtag1.putString(property.getName(), getName(property, (Comparable) map_entry.getValue()));
            }

            compoundtag.put("Properties", compoundtag1);
        }

        return compoundtag;
    }

    private static <T extends Comparable<T>> String getName(Property<T> key, Comparable<?> value) {
        return key.getName(value);
    }

    public static String prettyPrint(Tag tag) {
        return prettyPrint(tag, false);
    }

    public static String prettyPrint(Tag tag, boolean withBinaryBlobs) {
        return prettyPrint(new StringBuilder(), tag, 0, withBinaryBlobs).toString();
    }

    public static StringBuilder prettyPrint(StringBuilder builder, Tag input, int indent, boolean withBinaryBlobs) {
        Objects.requireNonNull(input);
        byte b0 = 0;
        StringBuilder stringbuilder1;

        //$FF: b0->value
        //0->net/minecraft/nbt/PrimitiveTag
        //1->net/minecraft/nbt/EndTag
        //2->net/minecraft/nbt/ByteArrayTag
        //3->net/minecraft/nbt/ListTag
        //4->net/minecraft/nbt/IntArrayTag
        //5->net/minecraft/nbt/CompoundTag
        //6->net/minecraft/nbt/LongArrayTag
        switch (input.typeSwitch<invokedynamic>(input, b0)) {
            case 0:
                PrimitiveTag primitivetag = (PrimitiveTag)input;

                stringbuilder1 = builder.append(primitivetag);
                break;
            case 1:
                EndTag endtag = (EndTag)input;

                stringbuilder1 = builder;
                break;
            case 2:
                ByteArrayTag bytearraytag = (ByteArrayTag)input;
                byte[] abyte = bytearraytag.getAsByteArray();
                int j = abyte.length;

                indent(indent, builder).append("byte[").append(j).append("] {\n");
                if (!withBinaryBlobs) {
                    indent(indent + 1, builder).append(" // Skipped, supply withBinaryBlobs true");
                } else {
                    indent(indent + 1, builder);

                    for(int k = 0; k < abyte.length; ++k) {
                        if (k != 0) {
                            builder.append(',');
                        }

                        if (k % 16 == 0 && k / 16 > 0) {
                            builder.append('\n');
                            if (k < abyte.length) {
                                indent(indent + 1, builder);
                            }
                        } else if (k != 0) {
                            builder.append(' ');
                        }

                        builder.append(String.format(Locale.ROOT, "0x%02X", abyte[k] & 255));
                    }
                }

                builder.append('\n');
                indent(indent, builder).append('}');
                stringbuilder1 = builder;
                break;
            case 3:
                ListTag listtag = (ListTag)input;
                int l = listtag.size();

                indent(indent, builder).append("list").append("[").append(l).append("] [");
                if (l != 0) {
                    builder.append('\n');
                }

                for(int i1 = 0; i1 < l; ++i1) {
                    if (i1 != 0) {
                        builder.append(",\n");
                    }

                    indent(indent + 1, builder);
                    prettyPrint(builder, listtag.get(i1), indent + 1, withBinaryBlobs);
                }

                if (l != 0) {
                    builder.append('\n');
                }

                indent(indent, builder).append(']');
                stringbuilder1 = builder;
                break;
            case 4:
                IntArrayTag intarraytag = (IntArrayTag)input;
                int[] aint = intarraytag.getAsIntArray();
                int j1 = 0;

                for(int k1 : aint) {
                    j1 = Math.max(j1, String.format(Locale.ROOT, "%X", k1).length());
                }

                int l1 = aint.length;

                indent(indent, builder).append("int[").append(l1).append("] {\n");
                if (!withBinaryBlobs) {
                    indent(indent + 1, builder).append(" // Skipped, supply withBinaryBlobs true");
                } else {
                    indent(indent + 1, builder);

                    for(int i2 = 0; i2 < aint.length; ++i2) {
                        if (i2 != 0) {
                            builder.append(',');
                        }

                        if (i2 % 16 == 0 && i2 / 16 > 0) {
                            builder.append('\n');
                            if (i2 < aint.length) {
                                indent(indent + 1, builder);
                            }
                        } else if (i2 != 0) {
                            builder.append(' ');
                        }

                        builder.append(String.format(Locale.ROOT, "0x%0" + j1 + "X", aint[i2]));
                    }
                }

                builder.append('\n');
                indent(indent, builder).append('}');
                stringbuilder1 = builder;
                break;
            case 5:
                CompoundTag compoundtag = (CompoundTag)input;
                List<String> list = Lists.newArrayList(compoundtag.keySet());

                Collections.sort(list);
                indent(indent, builder).append('{');
                if (builder.length() - builder.lastIndexOf("\n") > 2 * (indent + 1)) {
                    builder.append('\n');
                    indent(indent + 1, builder);
                }

                int j2 = list.stream().mapToInt(String::length).max().orElse(0);
                String s = Strings.repeat(" ", j2);

                for(int k2 = 0; k2 < ((List)list).size(); ++k2) {
                    if (k2 != 0) {
                        builder.append(",\n");
                    }

                    String s1 = (String)list.get(k2);

                    indent(indent + 1, builder).append('"').append(s1).append('"').append(s, 0, s.length() - s1.length()).append(": ");
                    prettyPrint(builder, compoundtag.get(s1), indent + 1, withBinaryBlobs);
                }

                if (!list.isEmpty()) {
                    builder.append('\n');
                }

                indent(indent, builder).append('}');
                stringbuilder1 = builder;
                break;
            case 6:
                LongArrayTag longarraytag = (LongArrayTag)input;
                long[] along = longarraytag.getAsLongArray();
                long l2 = 0L;

                for(long i3 : along) {
                    l2 = Math.max(l2, (long)String.format(Locale.ROOT, "%X", i3).length());
                }

                long j3 = (long)along.length;

                indent(indent, builder).append("long[").append(j3).append("] {\n");
                if (!withBinaryBlobs) {
                    indent(indent + 1, builder).append(" // Skipped, supply withBinaryBlobs true");
                } else {
                    indent(indent + 1, builder);

                    for(int k3 = 0; k3 < along.length; ++k3) {
                        if (k3 != 0) {
                            builder.append(',');
                        }

                        if (k3 % 16 == 0 && k3 / 16 > 0) {
                            builder.append('\n');
                            if (k3 < along.length) {
                                indent(indent + 1, builder);
                            }
                        } else if (k3 != 0) {
                            builder.append(' ');
                        }

                        builder.append(String.format(Locale.ROOT, "0x%0" + l2 + "X", along[k3]));
                    }
                }

                builder.append('\n');
                indent(indent, builder).append('}');
                stringbuilder1 = builder;
                break;
            default:
                throw new MatchException((String)null, (Throwable)null);
        }

        return stringbuilder1;
    }

    private static StringBuilder indent(int indent, StringBuilder builder) {
        int j = builder.lastIndexOf("\n") + 1;
        int k = builder.length() - j;

        for (int l = 0; l < 2 * indent - k; ++l) {
            builder.append(' ');
        }

        return builder;
    }

    public static Component toPrettyComponent(Tag tag) {
        return (new TextComponentTagVisitor("")).visit(tag);
    }

    public static String structureToSnbt(CompoundTag structure) {
        return (new SnbtPrinterTagVisitor()).visit(packStructureTemplate(structure));
    }

    public static CompoundTag snbtToStructure(String snbt) throws CommandSyntaxException {
        return unpackStructureTemplate(TagParser.parseCompoundFully(snbt));
    }

    @VisibleForTesting
    static CompoundTag packStructureTemplate(CompoundTag snbt) {
        Optional<ListTag> optional = snbt.getList("palettes");
        ListTag listtag;

        if (optional.isPresent()) {
            listtag = ((ListTag) optional.get()).getListOrEmpty(0);
        } else {
            listtag = snbt.getListOrEmpty("palette");
        }

        ListTag listtag1 = (ListTag) listtag.compoundStream().map(NbtUtils::packBlockState).map(StringTag::valueOf).collect(Collectors.toCollection(ListTag::new));

        snbt.put("palette", listtag1);
        if (optional.isPresent()) {
            ListTag listtag2 = new ListTag();

            ((ListTag) optional.get()).stream().flatMap((tag) -> {
                return tag.asList().stream();
            }).forEach((listtag3) -> {
                CompoundTag compoundtag1 = new CompoundTag();

                for (int i = 0; i < listtag3.size(); ++i) {
                    compoundtag1.putString((String) listtag1.getString(i).orElseThrow(), packBlockState((CompoundTag) listtag3.getCompound(i).orElseThrow()));
                }

                listtag2.add(compoundtag1);
            });
            snbt.put("palettes", listtag2);
        }

        Optional<ListTag> optional1 = snbt.getList("entities");

        if (optional1.isPresent()) {
            ListTag listtag3 = (ListTag) ((ListTag) optional1.get()).compoundStream().sorted(Comparator.comparing((compoundtag1) -> {
                return compoundtag1.getList("pos");
            }, Comparators.emptiesLast(NbtUtils.YXZ_LISTTAG_DOUBLE_COMPARATOR))).collect(Collectors.toCollection(ListTag::new));

            snbt.put("entities", listtag3);
        }

        ListTag listtag4 = (ListTag) snbt.getList("blocks").stream().flatMap(ListTag::compoundStream).sorted(Comparator.comparing((compoundtag1) -> {
            return compoundtag1.getList("pos");
        }, Comparators.emptiesLast(NbtUtils.YXZ_LISTTAG_INT_COMPARATOR))).peek((compoundtag1) -> {
            compoundtag1.putString("state", (String) listtag1.getString(compoundtag1.getIntOr("state", 0)).orElseThrow());
        }).collect(Collectors.toCollection(ListTag::new));

        snbt.put("data", listtag4);
        snbt.remove("blocks");
        return snbt;
    }

    @VisibleForTesting
    static CompoundTag unpackStructureTemplate(CompoundTag template) {
        ListTag listtag = template.getListOrEmpty("palette");
        Map<String, Tag> map = (Map) listtag.stream().flatMap((tag) -> {
            return tag.asString().stream();
        }).collect(ImmutableMap.toImmutableMap(Function.identity(), NbtUtils::unpackBlockState));
        Optional<ListTag> optional = template.getList("palettes");

        if (optional.isPresent()) {
            template.put("palettes", (Tag) ((ListTag) optional.get()).compoundStream().map((compoundtag1) -> {
                return (ListTag) map.keySet().stream().map((s) -> {
                    return (String) compoundtag1.getString(s).orElseThrow();
                }).map(NbtUtils::unpackBlockState).collect(Collectors.toCollection(ListTag::new));
            }).collect(Collectors.toCollection(ListTag::new)));
            template.remove("palette");
        } else {
            template.put("palette", (Tag) map.values().stream().collect(Collectors.toCollection(ListTag::new)));
        }

        Optional<ListTag> optional1 = template.getList("data");

        if (optional1.isPresent()) {
            Object2IntMap<String> object2intmap = new Object2IntOpenHashMap();

            object2intmap.defaultReturnValue(-1);

            for (int i = 0; i < listtag.size(); ++i) {
                object2intmap.put((String) listtag.getString(i).orElseThrow(), i);
            }

            ListTag listtag1 = (ListTag) optional1.get();

            for (int j = 0; j < listtag1.size(); ++j) {
                CompoundTag compoundtag1 = (CompoundTag) listtag1.getCompound(j).orElseThrow();
                String s = (String) compoundtag1.getString("state").orElseThrow();
                int k = object2intmap.getInt(s);

                if (k == -1) {
                    throw new IllegalStateException("Entry " + s + " missing from palette");
                }

                compoundtag1.putInt("state", k);
            }

            template.put("blocks", listtag1);
            template.remove("data");
        }

        return template;
    }

    @VisibleForTesting
    static String packBlockState(CompoundTag compound) {
        StringBuilder stringbuilder = new StringBuilder((String) compound.getString("Name").orElseThrow());

        compound.getCompound("Properties").ifPresent((compoundtag1) -> {
            String s = (String) compoundtag1.entrySet().stream().sorted(Entry.comparingByKey()).map((entry) -> {
                String s1 = (String) entry.getKey();

                return s1 + ":" + (String) ((Tag) entry.getValue()).asString().orElseThrow();
            }).collect(Collectors.joining(","));

            stringbuilder.append('{').append(s).append('}');
        });
        return stringbuilder.toString();
    }

    @VisibleForTesting
    static CompoundTag unpackBlockState(String compound) {
        CompoundTag compoundtag = new CompoundTag();
        int i = compound.indexOf(123);
        String s1;

        if (i >= 0) {
            s1 = compound.substring(0, i);
            CompoundTag compoundtag1 = new CompoundTag();

            if (i + 2 <= compound.length()) {
                String s2 = compound.substring(i + 1, compound.indexOf(125, i));

                NbtUtils.COMMA_SPLITTER.split(s2).forEach((s3) -> {
                    List<String> list = NbtUtils.COLON_SPLITTER.splitToList(s3);

                    if (list.size() == 2) {
                        compoundtag1.putString((String) list.get(0), (String) list.get(1));
                    } else {
                        NbtUtils.LOGGER.error("Something went wrong parsing: '{}' -- incorrect gamedata!", compound);
                    }

                });
                compoundtag.put("Properties", compoundtag1);
            }
        } else {
            s1 = compound;
        }

        compoundtag.putString("Name", s1);
        return compoundtag;
    }

    public static CompoundTag addCurrentDataVersion(CompoundTag tag) {
        int i = SharedConstants.getCurrentVersion().dataVersion().version();

        return addDataVersion(tag, i);
    }

    public static CompoundTag addDataVersion(CompoundTag tag, int version) {
        tag.putInt("DataVersion", version);
        return tag;
    }

    public static Dynamic<Tag> addCurrentDataVersion(Dynamic<Tag> tag) {
        int i = SharedConstants.getCurrentVersion().dataVersion().version();

        return addDataVersion(tag, i);
    }

    public static Dynamic<Tag> addDataVersion(Dynamic<Tag> tag, int version) {
        return tag.set("DataVersion", tag.createInt(version));
    }

    public static void addCurrentDataVersion(ValueOutput output) {
        int i = SharedConstants.getCurrentVersion().dataVersion().version();

        addDataVersion(output, i);
    }

    public static void addDataVersion(ValueOutput output, int version) {
        output.putInt("DataVersion", version);
    }

    public static int getDataVersion(CompoundTag tag) {
        return getDataVersion(tag, -1);
    }

    public static int getDataVersion(CompoundTag tag, int _default) {
        return tag.getIntOr("DataVersion", _default);
    }

    public static int getDataVersion(Dynamic<?> dynamic, int _default) {
        return dynamic.get("DataVersion").asInt(_default);
    }
}
