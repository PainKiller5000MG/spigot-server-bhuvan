package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.List;

public class SpawnerDataFix extends DataFix {

    public SpawnerDataFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.UNTAGGED_SPAWNER);
        Type<?> type1 = this.getOutputSchema().getType(References.UNTAGGED_SPAWNER);
        OpticFinder<?> opticfinder = type.findField("SpawnData");
        Type<?> type2 = type1.findField("SpawnData").type();
        OpticFinder<?> opticfinder1 = type.findField("SpawnPotentials");
        Type<?> type3 = type1.findField("SpawnPotentials").type();

        return this.fixTypeEverywhereTyped("Fix mob spawner data structure", type, type1, (typed) -> {
            return typed.updateTyped(opticfinder, type2, (typed1) -> {
                return this.wrapEntityToSpawnData(type2, typed1);
            }).updateTyped(opticfinder1, type3, (typed1) -> {
                return this.wrapSpawnPotentialsToWeightedEntries(type3, typed1);
            });
        });
    }

    private <T> Typed<T> wrapEntityToSpawnData(Type<T> newType, Typed<?> spawnData) {
        DynamicOps<?> dynamicops = spawnData.getOps();

        return new Typed(newType, dynamicops, Pair.of(spawnData.getValue(), new Dynamic(dynamicops)));
    }

    private <T> Typed<T> wrapSpawnPotentialsToWeightedEntries(Type<T> newType, Typed<?> spawnPotentials) {
        DynamicOps<?> dynamicops = spawnPotentials.getOps();
        List<?> list = (List) spawnPotentials.getValue();
        List<?> list1 = list.stream().map((object) -> {
            Pair<Object, Dynamic<?>> pair = (Pair) object;
            int i = ((Number) ((Dynamic) pair.getSecond()).get("Weight").asNumber().result().orElse(1)).intValue();
            Dynamic<?> dynamic = new Dynamic(dynamicops);

            dynamic = dynamic.set("weight", dynamic.createInt(i));
            Dynamic<?> dynamic1 = ((Dynamic) pair.getSecond()).remove("Weight").remove("Entity");

            return Pair.of(Pair.of(pair.getFirst(), dynamic1), dynamic);
        }).toList();

        return new Typed(newType, dynamicops, list1);
    }
}
