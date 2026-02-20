package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import java.util.stream.Stream;

public class MobSpawnerEntityIdentifiersFix extends DataFix {

    public MobSpawnerEntityIdentifiersFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    private Dynamic<?> fix(Dynamic<?> input) {
        if (!"MobSpawner".equals(input.get("id").asString(""))) {
            return input;
        } else {
            Optional<String> optional = input.get("EntityId").asString().result();

            if (optional.isPresent()) {
                Dynamic<?> dynamic1 = (Dynamic) DataFixUtils.orElse(input.get("SpawnData").result(), input.emptyMap());

                dynamic1 = dynamic1.set("id", dynamic1.createString(((String) optional.get()).isEmpty() ? "Pig" : (String) optional.get()));
                input = input.set("SpawnData", dynamic1);
                input = input.remove("EntityId");
            }

            Optional<? extends Stream<? extends Dynamic<?>>> optional1 = input.get("SpawnPotentials").asStreamOpt().result();

            if (optional1.isPresent()) {
                input = input.set("SpawnPotentials", input.createList(((Stream) optional1.get()).map((dynamic2) -> {
                    Optional<String> optional2 = dynamic2.get("Type").asString().result();

                    if (optional2.isPresent()) {
                        Dynamic<?> dynamic3 = ((Dynamic) DataFixUtils.orElse(dynamic2.get("Properties").result(), dynamic2.emptyMap())).set("id", dynamic2.createString((String) optional2.get()));

                        return dynamic2.set("Entity", dynamic3).remove("Type").remove("Properties");
                    } else {
                        return dynamic2;
                    }
                })));
            }

            return input;
        }
    }

    public TypeRewriteRule makeRule() {
        Type<?> type = this.getOutputSchema().getType(References.UNTAGGED_SPAWNER);

        return this.fixTypeEverywhereTyped("MobSpawnerEntityIdentifiersFix", this.getInputSchema().getType(References.UNTAGGED_SPAWNER), type, (typed) -> {
            Dynamic<?> dynamic = (Dynamic) typed.get(DSL.remainderFinder());

            dynamic = dynamic.set("id", dynamic.createString("MobSpawner"));
            DataResult<? extends Pair<? extends Typed<?>, ?>> dataresult = type.readTyped(this.fix(dynamic));

            return dataresult.result().isEmpty() ? typed : (Typed) ((Pair) dataresult.result().get()).getFirst();
        });
    }
}
