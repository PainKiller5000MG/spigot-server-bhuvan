package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class EntityAttributeBaseValueFix extends NamedEntityFix {

    private final String attributeId;
    private final DoubleUnaryOperator valueFixer;

    public EntityAttributeBaseValueFix(Schema outputSchema, String name, String entityName, String attributeId, DoubleUnaryOperator valueFixer) {
        super(outputSchema, false, name, References.ENTITY, entityName);
        this.attributeId = attributeId;
        this.valueFixer = valueFixer;
    }

    @Override
    protected Typed<?> fix(Typed<?> entity) {
        return entity.update(DSL.remainderFinder(), this::fixValue);
    }

    private Dynamic<?> fixValue(Dynamic<?> tag) {
        return tag.update("attributes", (dynamic1) -> {
            return tag.createList(dynamic1.asStream().map((dynamic2) -> {
                String s = NamespacedSchema.ensureNamespaced(dynamic2.get("id").asString(""));

                if (!s.equals(this.attributeId)) {
                    return dynamic2;
                } else {
                    double d0 = dynamic2.get("base").asDouble(0.0D);

                    return dynamic2.set("base", dynamic2.createDouble(this.valueFixer.applyAsDouble(d0)));
                }
            }));
        });
    }
}
