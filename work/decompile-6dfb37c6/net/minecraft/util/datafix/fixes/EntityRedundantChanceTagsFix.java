package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Codec;
import com.mojang.serialization.OptionalDynamic;
import java.util.List;
import java.util.Objects;

public class EntityRedundantChanceTagsFix extends DataFix {

    private static final Codec<List<Float>> FLOAT_LIST_CODEC = Codec.FLOAT.listOf();

    public EntityRedundantChanceTagsFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped("EntityRedundantChanceTagsFix", this.getInputSchema().getType(References.ENTITY), (typed) -> {
            return typed.update(DSL.remainderFinder(), (dynamic) -> {
                if (isZeroList(dynamic.get("HandDropChances"), 2)) {
                    dynamic = dynamic.remove("HandDropChances");
                }

                if (isZeroList(dynamic.get("ArmorDropChances"), 4)) {
                    dynamic = dynamic.remove("ArmorDropChances");
                }

                return dynamic;
            });
        });
    }

    private static boolean isZeroList(OptionalDynamic<?> element, int size) {
        Codec codec = EntityRedundantChanceTagsFix.FLOAT_LIST_CODEC;

        Objects.requireNonNull(codec);
        return (Boolean) element.flatMap(codec::parse).map((list) -> {
            return list.size() == size && list.stream().allMatch((ofloat) -> {
                return ofloat == 0.0F;
            });
        }).result().orElse(false);
    }
}
