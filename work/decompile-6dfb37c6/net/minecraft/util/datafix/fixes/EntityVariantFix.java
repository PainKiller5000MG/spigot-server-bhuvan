package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.function.Function;
import java.util.function.IntFunction;

public class EntityVariantFix extends NamedEntityFix {

    private final String fieldName;
    private final IntFunction<String> idConversions;

    public EntityVariantFix(Schema outputSchema, String name, TypeReference type, String entityName, String fieldName, IntFunction<String> idConversions) {
        super(outputSchema, false, name, type, entityName);
        this.fieldName = fieldName;
        this.idConversions = idConversions;
    }

    private static <T> Dynamic<T> updateAndRename(Dynamic<T> input, String oldKey, String newKey, Function<Dynamic<T>, Dynamic<T>> function) {
        return input.map((object) -> {
            DynamicOps<T> dynamicops = input.getOps();
            Function<T, T> function1 = (object1) -> {
                return ((Dynamic) function.apply(new Dynamic(dynamicops, object1))).getValue();
            };

            return dynamicops.get(object, oldKey).map((object1) -> {
                return dynamicops.set(object, newKey, function1.apply(object1));
            }).result().orElse(object);
        });
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), (dynamic) -> {
            return updateAndRename(dynamic, this.fieldName, "variant", (dynamic1) -> {
                return (Dynamic) DataFixUtils.orElse(dynamic1.asNumber().map((number) -> {
                    return dynamic1.createString((String) this.idConversions.apply(number.intValue()));
                }).result(), dynamic1);
            });
        });
    }
}
