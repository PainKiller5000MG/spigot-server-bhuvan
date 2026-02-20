package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class VillagerFollowRangeFix extends NamedEntityFix {

    private static final double ORIGINAL_VALUE = 16.0D;
    private static final double NEW_BASE_VALUE = 48.0D;

    public VillagerFollowRangeFix(Schema outputSchema) {
        super(outputSchema, false, "Villager Follow Range Fix", References.ENTITY, "minecraft:villager");
    }

    @Override
    protected Typed<?> fix(Typed<?> entity) {
        return entity.update(DSL.remainderFinder(), VillagerFollowRangeFix::fixValue);
    }

    private static Dynamic<?> fixValue(Dynamic<?> tag) {
        return tag.update("Attributes", (dynamic1) -> {
            return tag.createList(dynamic1.asStream().map((dynamic2) -> {
                return dynamic2.get("Name").asString("").equals("generic.follow_range") && dynamic2.get("Base").asDouble(0.0D) == 16.0D ? dynamic2.set("Base", dynamic2.createDouble(48.0D)) : dynamic2;
            }));
        });
    }
}
