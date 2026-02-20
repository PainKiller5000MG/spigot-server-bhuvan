package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class EntityHorseSaddleFix extends NamedEntityFix {

    public EntityHorseSaddleFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "EntityHorseSaddleFix", References.ENTITY, "EntityHorse");
    }

    @Override
    protected Typed<?> fix(Typed<?> entity) {
        OpticFinder<Pair<String, String>> opticfinder = DSL.fieldFinder("id", DSL.named(References.ITEM_NAME.typeName(), NamespacedSchema.namespacedString()));
        Type<?> type = this.getInputSchema().getTypeRaw(References.ITEM_STACK);
        OpticFinder<?> opticfinder1 = DSL.fieldFinder("SaddleItem", type);
        Optional<? extends Typed<?>> optional = entity.getOptionalTyped(opticfinder1);
        Dynamic<?> dynamic = (Dynamic) entity.get(DSL.remainderFinder());

        if (optional.isEmpty() && dynamic.get("Saddle").asBoolean(false)) {
            Typed<?> typed1 = (Typed) type.pointTyped(entity.getOps()).orElseThrow(IllegalStateException::new);

            typed1 = typed1.set(opticfinder, Pair.of(References.ITEM_NAME.typeName(), "minecraft:saddle"));
            Dynamic<?> dynamic1 = dynamic.emptyMap();

            dynamic1 = dynamic1.set("Count", dynamic1.createByte((byte) 1));
            dynamic1 = dynamic1.set("Damage", dynamic1.createShort((short) 0));
            typed1 = typed1.set(DSL.remainderFinder(), dynamic1);
            dynamic.remove("Saddle");
            return entity.set(opticfinder1, typed1).set(DSL.remainderFinder(), dynamic);
        } else {
            return entity;
        }
    }
}
