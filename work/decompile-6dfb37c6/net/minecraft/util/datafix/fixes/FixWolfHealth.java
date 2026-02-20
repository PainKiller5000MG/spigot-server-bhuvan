package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import net.minecraft.util.datafix.schemas.NamespacedSchema;
import org.apache.commons.lang3.mutable.MutableBoolean;

public class FixWolfHealth extends NamedEntityFix {

    private static final String WOLF_ID = "minecraft:wolf";
    private static final String WOLF_HEALTH = "minecraft:generic.max_health";

    public FixWolfHealth(Schema outputSchema) {
        super(outputSchema, false, "FixWolfHealth", References.ENTITY, "minecraft:wolf");
    }

    @Override
    protected Typed<?> fix(Typed<?> entity) {
        return entity.update(DSL.remainderFinder(), (dynamic) -> {
            MutableBoolean mutableboolean = new MutableBoolean(false);

            dynamic = dynamic.update("Attributes", (dynamic1) -> {
                return dynamic1.createList(dynamic1.asStream().map((dynamic2) -> {
                    return "minecraft:generic.max_health".equals(NamespacedSchema.ensureNamespaced(dynamic2.get("Name").asString(""))) ? dynamic2.update("Base", (dynamic3) -> {
                        if (dynamic3.asDouble(0.0D) == 20.0D) {
                            mutableboolean.setTrue();
                            return dynamic3.createDouble(40.0D);
                        } else {
                            return dynamic3;
                        }
                    }) : dynamic2;
                }));
            });
            if (mutableboolean.isTrue()) {
                dynamic = dynamic.update("Health", (dynamic1) -> {
                    return dynamic1.createFloat(dynamic1.asFloat(0.0F) * 2.0F);
                });
            }

            return dynamic;
        });
    }
}
