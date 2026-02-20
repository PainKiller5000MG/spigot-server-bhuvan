package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.List.ListType;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.stream.LongStream;
import net.minecraft.util.Mth;

public class BitStorageAlignFix extends DataFix {

    private static final int BIT_TO_LONG_SHIFT = 6;
    private static final int SECTION_WIDTH = 16;
    private static final int SECTION_HEIGHT = 16;
    private static final int SECTION_SIZE = 4096;
    private static final int HEIGHTMAP_BITS = 9;
    private static final int HEIGHTMAP_SIZE = 256;

    public BitStorageAlignFix(Schema schema) {
        super(schema, false);
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        Type<?> type1 = type.findFieldType("Level");
        OpticFinder<?> opticfinder = DSL.fieldFinder("Level", type1);
        OpticFinder<?> opticfinder1 = opticfinder.type().findField("Sections");
        Type<?> type2 = ((ListType) opticfinder1.type()).getElement();
        OpticFinder<?> opticfinder2 = DSL.typeFinder(type2);
        Type<Pair<String, Dynamic<?>>> type3 = DSL.named(References.BLOCK_STATE.typeName(), DSL.remainderType());
        OpticFinder<List<Pair<String, Dynamic<?>>>> opticfinder3 = DSL.fieldFinder("Palette", DSL.list(type3));

        return this.fixTypeEverywhereTyped("BitStorageAlignFix", type, this.getOutputSchema().getType(References.CHUNK), (typed) -> {
            return typed.updateTyped(opticfinder, (typed1) -> {
                return this.updateHeightmaps(updateSections(opticfinder1, opticfinder2, opticfinder3, typed1));
            });
        });
    }

    private Typed<?> updateHeightmaps(Typed<?> level) {
        return level.update(DSL.remainderFinder(), (dynamic) -> {
            return dynamic.update("Heightmaps", (dynamic1) -> {
                return dynamic1.updateMapValues((pair) -> {
                    return pair.mapSecond((dynamic2) -> {
                        return updateBitStorage(dynamic, dynamic2, 256, 9);
                    });
                });
            });
        });
    }

    private static Typed<?> updateSections(OpticFinder<?> sectionsFinder, OpticFinder<?> sectionFinder, OpticFinder<List<Pair<String, Dynamic<?>>>> paletteFinder, Typed<?> level) {
        return level.updateTyped(sectionsFinder, (typed1) -> {
            return typed1.updateTyped(sectionFinder, (typed2) -> {
                int i = (Integer) typed2.getOptional(paletteFinder).map((list) -> {
                    return Math.max(4, DataFixUtils.ceillog2(list.size()));
                }).orElse(0);

                return i != 0 && !Mth.isPowerOfTwo(i) ? typed2.update(DSL.remainderFinder(), (dynamic) -> {
                    return dynamic.update("BlockStates", (dynamic1) -> {
                        return updateBitStorage(dynamic, dynamic1, 4096, i);
                    });
                }) : typed2;
            });
        });
    }

    private static Dynamic<?> updateBitStorage(Dynamic<?> tag, Dynamic<?> storage, int size, int bits) {
        long[] along = storage.asLongStream().toArray();
        long[] along1 = addPadding(size, bits, along);

        return tag.createLongList(LongStream.of(along1));
    }

    public static long[] addPadding(int size, int bits, long[] data) {
        int k = data.length;

        if (k == 0) {
            return data;
        } else {
            long l = (1L << bits) - 1L;
            int i1 = 64 / bits;
            int j1 = (size + i1 - 1) / i1;
            long[] along1 = new long[j1];
            int k1 = 0;
            int l1 = 0;
            long i2 = 0L;
            int j2 = 0;
            long k2 = data[0];
            long l2 = k > 1 ? data[1] : 0L;

            for (int i3 = 0; i3 < size; ++i3) {
                int j3 = i3 * bits;
                int k3 = j3 >> 6;
                int l3 = (i3 + 1) * bits - 1 >> 6;
                int i4 = j3 ^ k3 << 6;

                if (k3 != j2) {
                    k2 = l2;
                    l2 = k3 + 1 < k ? data[k3 + 1] : 0L;
                    j2 = k3;
                }

                long j4;

                if (k3 == l3) {
                    j4 = k2 >>> i4 & l;
                } else {
                    int k4 = 64 - i4;

                    j4 = (k2 >>> i4 | l2 << k4) & l;
                }

                int l4 = l1 + bits;

                if (l4 >= 64) {
                    along1[k1++] = i2;
                    i2 = j4;
                    l1 = bits;
                } else {
                    i2 |= j4 << l1;
                    l1 = l4;
                }
            }

            if (i2 != 0L) {
                along1[k1] = i2;
            }

            return along1;
        }
    }
}
