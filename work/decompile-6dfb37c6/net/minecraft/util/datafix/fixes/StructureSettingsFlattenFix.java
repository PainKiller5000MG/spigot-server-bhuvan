package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.Util;

public class StructureSettingsFlattenFix extends DataFix {

    public StructureSettingsFlattenFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.WORLD_GEN_SETTINGS);
        OpticFinder<?> opticfinder = type.findField("dimensions");

        return this.fixTypeEverywhereTyped("StructureSettingsFlatten", type, (typed) -> {
            return typed.updateTyped(opticfinder, (typed1) -> {
                return Util.writeAndReadTypedOrThrow(typed1, opticfinder.type(), (dynamic) -> {
                    return dynamic.updateMapValues(StructureSettingsFlattenFix::fixDimension);
                });
            });
        });
    }

    private static Pair<Dynamic<?>, Dynamic<?>> fixDimension(Pair<Dynamic<?>, Dynamic<?>> entry) {
        Dynamic<?> dynamic = (Dynamic) entry.getSecond();

        return Pair.of((Dynamic) entry.getFirst(), dynamic.update("generator", (dynamic1) -> {
            return dynamic1.update("settings", (dynamic2) -> {
                return dynamic2.update("structures", StructureSettingsFlattenFix::fixStructures);
            });
        }));
    }

    private static Dynamic<?> fixStructures(Dynamic<?> input) {
        Dynamic<?> dynamic1 = input.get("structures").orElseEmptyMap().updateMapValues((pair) -> {
            return pair.mapSecond((dynamic2) -> {
                return dynamic2.set("type", input.createString("minecraft:random_spread"));
            });
        });

        return (Dynamic) DataFixUtils.orElse(input.get("stronghold").result().map((dynamic2) -> {
            return dynamic1.set("minecraft:stronghold", dynamic2.set("type", input.createString("minecraft:concentric_rings")));
        }), dynamic1);
    }
}
