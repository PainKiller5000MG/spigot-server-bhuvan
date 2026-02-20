package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Maps;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class EntityPaintingMotiveFix extends NamedEntityFix {

    private static final Map<String, String> MAP = (Map) DataFixUtils.make(Maps.newHashMap(), (hashmap) -> {
        hashmap.put("donkeykong", "donkey_kong");
        hashmap.put("burningskull", "burning_skull");
        hashmap.put("skullandroses", "skull_and_roses");
    });

    public EntityPaintingMotiveFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "EntityPaintingMotiveFix", References.ENTITY, "minecraft:painting");
    }

    public Dynamic<?> fixTag(Dynamic<?> input) {
        Optional<String> optional = input.get("Motive").asString().result();

        if (optional.isPresent()) {
            String s = ((String) optional.get()).toLowerCase(Locale.ROOT);

            return input.set("Motive", input.createString(NamespacedSchema.ensureNamespaced((String) EntityPaintingMotiveFix.MAP.getOrDefault(s, s))));
        } else {
            return input;
        }
    }

    @Override
    protected Typed<?> fix(Typed<?> entity) {
        return entity.update(DSL.remainderFinder(), this::fixTag);
    }
}
