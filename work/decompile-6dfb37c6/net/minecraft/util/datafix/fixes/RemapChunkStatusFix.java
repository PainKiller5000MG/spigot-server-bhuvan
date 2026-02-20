package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class RemapChunkStatusFix extends DataFix {

    private final String name;
    private final UnaryOperator<String> mapper;

    public RemapChunkStatusFix(Schema schema, String name, UnaryOperator<String> mapper) {
        super(schema, false);
        this.name = name;
        this.mapper = mapper;
    }

    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(this.name, this.getInputSchema().getType(References.CHUNK), (typed) -> {
            return typed.update(DSL.remainderFinder(), (dynamic) -> {
                return dynamic.update("Status", this::fixStatus).update("below_zero_retrogen", (dynamic1) -> {
                    return dynamic1.update("target_status", this::fixStatus);
                });
            });
        });
    }

    private <T> Dynamic<T> fixStatus(Dynamic<T> dynamic) {
        Optional optional = dynamic.asString().result().map(NamespacedSchema::ensureNamespaced).map(this.mapper);

        Objects.requireNonNull(dynamic);
        Optional<Dynamic<T>> optional1 = optional.map(dynamic::createString);

        return (Dynamic) DataFixUtils.orElse(optional1, dynamic);
    }
}
