package net.minecraft.util.datafix.fixes;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ReorganizePoi extends DataFix {

    public ReorganizePoi(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    protected TypeRewriteRule makeRule() {
        Type<Pair<String, Dynamic<?>>> type = DSL.named(References.POI_CHUNK.typeName(), DSL.remainderType());

        if (!Objects.equals(type, this.getInputSchema().getType(References.POI_CHUNK))) {
            throw new IllegalStateException("Poi type is not what was expected.");
        } else {
            return this.fixTypeEverywhere("POI reorganization", type, (dynamicops) -> {
                return (pair) -> {
                    return pair.mapSecond(ReorganizePoi::cap);
                };
            });
        }
    }

    private static <T> Dynamic<T> cap(Dynamic<T> input) {
        Map<Dynamic<T>, Dynamic<T>> map = Maps.newHashMap();

        for (int i = 0; i < 16; ++i) {
            String s = String.valueOf(i);
            Optional<Dynamic<T>> optional = input.get(s).result();

            if (optional.isPresent()) {
                Dynamic<T> dynamic1 = (Dynamic) optional.get();
                Dynamic<T> dynamic2 = input.createMap(ImmutableMap.of(input.createString("Records"), dynamic1));

                map.put(input.createString(Integer.toString(i)), dynamic2);
                input = input.remove(s);
            }
        }

        return input.set("Sections", input.createMap(map));
    }
}
