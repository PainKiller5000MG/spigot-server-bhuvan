package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ChunkToProtochunkFix extends DataFix {

    private static final int NUM_SECTIONS = 16;

    public ChunkToProtochunkFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    public TypeRewriteRule makeRule() {
        return this.writeFixAndRead("ChunkToProtoChunkFix", this.getInputSchema().getType(References.CHUNK), this.getOutputSchema().getType(References.CHUNK), (dynamic) -> {
            return dynamic.update("Level", ChunkToProtochunkFix::fixChunkData);
        });
    }

    private static <T> Dynamic<T> fixChunkData(Dynamic<T> tag) {
        boolean flag = tag.get("TerrainPopulated").asBoolean(false);
        boolean flag1 = tag.get("LightPopulated").asNumber().result().isEmpty() || tag.get("LightPopulated").asBoolean(false);
        String s;

        if (flag) {
            if (flag1) {
                s = "mobs_spawned";
            } else {
                s = "decorated";
            }
        } else {
            s = "carved";
        }

        return repackTicks(repackBiomes(tag)).set("Status", tag.createString(s)).set("hasLegacyStructureData", tag.createBoolean(true));
    }

    private static <T> Dynamic<T> repackBiomes(Dynamic<T> tag) {
        return tag.update("Biomes", (dynamic1) -> {
            return (Dynamic) DataFixUtils.orElse(dynamic1.asByteBufferOpt().result().map((bytebuffer) -> {
                int[] aint = new int[256];

                for (int i = 0; i < aint.length; ++i) {
                    if (i < bytebuffer.capacity()) {
                        aint[i] = bytebuffer.get(i) & 255;
                    }
                }

                return tag.createIntList(Arrays.stream(aint));
            }), dynamic1);
        });
    }

    private static <T> Dynamic<T> repackTicks(Dynamic<T> tag) {
        return (Dynamic) DataFixUtils.orElse(tag.get("TileTicks").asStreamOpt().result().map((stream) -> {
            List<ShortList> list = (List) IntStream.range(0, 16).mapToObj((i) -> {
                return new ShortArrayList();
            }).collect(Collectors.toList());

            stream.forEach((dynamic1) -> {
                int i = dynamic1.get("x").asInt(0);
                int j = dynamic1.get("y").asInt(0);
                int k = dynamic1.get("z").asInt(0);
                short short0 = packOffsetCoordinates(i, j, k);

                ((ShortList) list.get(j >> 4)).add(short0);
            });
            return tag.remove("TileTicks").set("ToBeTicked", tag.createList(list.stream().map((shortlist) -> {
                return tag.createList(shortlist.intStream().mapToObj((i) -> {
                    return tag.createShort((short) i);
                }));
            })));
        }), tag);
    }

    private static short packOffsetCoordinates(int x, int y, int z) {
        return (short) (x & 15 | (y & 15) << 4 | (z & 15) << 8);
    }
}
