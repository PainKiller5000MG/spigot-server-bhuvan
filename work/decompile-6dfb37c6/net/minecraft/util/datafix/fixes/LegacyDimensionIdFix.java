package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;

public class LegacyDimensionIdFix extends DataFix {

    public LegacyDimensionIdFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    public TypeRewriteRule makeRule() {
        TypeRewriteRule typerewriterule = this.fixTypeEverywhereTyped("PlayerLegacyDimensionFix", this.getInputSchema().getType(References.PLAYER), (typed) -> {
            return typed.update(DSL.remainderFinder(), this::fixPlayer);
        });
        Type<?> type = this.getInputSchema().getType(References.SAVED_DATA_MAP_DATA);
        OpticFinder<?> opticfinder = type.findField("data");
        TypeRewriteRule typerewriterule1 = this.fixTypeEverywhereTyped("MapLegacyDimensionFix", type, (typed) -> {
            return typed.updateTyped(opticfinder, (typed1) -> {
                return typed1.update(DSL.remainderFinder(), this::fixMap);
            });
        });

        return TypeRewriteRule.seq(typerewriterule, typerewriterule1);
    }

    private <T> Dynamic<T> fixMap(Dynamic<T> remainder) {
        return remainder.update("dimension", this::fixDimensionId);
    }

    private <T> Dynamic<T> fixPlayer(Dynamic<T> remainder) {
        return remainder.update("Dimension", this::fixDimensionId);
    }

    private <T> Dynamic<T> fixDimensionId(Dynamic<T> id) {
        return (Dynamic) DataFixUtils.orElse(id.asNumber().result().map((number) -> {
            Dynamic dynamic1;

            switch (number.intValue()) {
                case -1:
                    dynamic1 = id.createString("minecraft:the_nether");
                    break;
                case 1:
                    dynamic1 = id.createString("minecraft:the_end");
                    break;
                default:
                    dynamic1 = id.createString("minecraft:overworld");
            }

            return dynamic1;
        }), id);
    }
}
