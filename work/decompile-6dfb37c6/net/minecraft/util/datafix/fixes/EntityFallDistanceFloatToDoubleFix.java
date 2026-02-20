package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;

public class EntityFallDistanceFloatToDoubleFix extends DataFix {

    private final TypeReference type;

    public EntityFallDistanceFloatToDoubleFix(Schema outputSchema, TypeReference type) {
        super(outputSchema, false);
        this.type = type;
    }

    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped("EntityFallDistanceFloatToDoubleFixFor" + this.type.typeName(), this.getOutputSchema().getType(this.type), EntityFallDistanceFloatToDoubleFix::fixEntity);
    }

    private static Typed<?> fixEntity(Typed<?> entity) {
        return entity.update(DSL.remainderFinder(), (dynamic) -> {
            return dynamic.renameAndFixField("FallDistance", "fall_distance", (dynamic1) -> {
                return dynamic1.createDouble((double) dynamic1.asFloat(0.0F));
            });
        });
    }
}
