package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;

public class EntitySalmonSizeFix extends NamedEntityFix {

    public EntitySalmonSizeFix(Schema outputSchema) {
        super(outputSchema, false, "EntitySalmonSizeFix", References.ENTITY, "minecraft:salmon");
    }

    @Override
    protected Typed<?> fix(Typed<?> entity) {
        return entity.update(DSL.remainderFinder(), (dynamic) -> {
            String s = dynamic.get("type").asString("medium");

            return s.equals("large") ? dynamic : dynamic.set("type", dynamic.createString("medium"));
        });
    }
}
