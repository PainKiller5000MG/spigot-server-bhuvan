package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Map;

public class VariantRenameFix extends NamedEntityFix {

    private final Map<String, String> renames;

    public VariantRenameFix(Schema outputSchema, String name, TypeReference type, String entityName, Map<String, String> renames) {
        super(outputSchema, false, name, type, entityName);
        this.renames = renames;
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), (dynamic) -> {
            return dynamic.update("variant", (dynamic1) -> {
                return (Dynamic) DataFixUtils.orElse(dynamic1.asString().map((s) -> {
                    return dynamic1.createString((String) this.renames.getOrDefault(s, s));
                }).result(), dynamic1);
            });
        });
    }
}
