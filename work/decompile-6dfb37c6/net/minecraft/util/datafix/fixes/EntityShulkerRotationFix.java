package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class EntityShulkerRotationFix extends NamedEntityFix {

    public EntityShulkerRotationFix(Schema outputSchema) {
        super(outputSchema, false, "EntityShulkerRotationFix", References.ENTITY, "minecraft:shulker");
    }

    public Dynamic<?> fixTag(Dynamic<?> input) {
        List<Double> list = input.get("Rotation").asList((dynamic1) -> {
            return dynamic1.asDouble(180.0D);
        });

        if (!list.isEmpty()) {
            list.set(0, (Double) list.get(0) - 180.0D);
            Stream stream = list.stream();

            Objects.requireNonNull(input);
            return input.set("Rotation", input.createList(stream.map(input::createDouble)));
        } else {
            return input;
        }
    }

    @Override
    protected Typed<?> fix(Typed<?> entity) {
        return entity.update(DSL.remainderFinder(), this::fixTag);
    }
}
