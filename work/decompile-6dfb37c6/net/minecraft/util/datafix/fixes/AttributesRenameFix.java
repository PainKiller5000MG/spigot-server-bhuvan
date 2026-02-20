package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public class AttributesRenameFix extends DataFix {

    private final String name;
    private final UnaryOperator<String> renames;

    public AttributesRenameFix(Schema outputSchema, String name, UnaryOperator<String> renames) {
        super(outputSchema, false);
        this.name = name;
        this.renames = renames;
    }

    protected TypeRewriteRule makeRule() {
        return TypeRewriteRule.seq(this.fixTypeEverywhereTyped(this.name + " (Components)", this.getInputSchema().getType(References.DATA_COMPONENTS), this::fixDataComponents), new TypeRewriteRule[]{this.fixTypeEverywhereTyped(this.name + " (Entity)", this.getInputSchema().getType(References.ENTITY), this::fixEntity), this.fixTypeEverywhereTyped(this.name + " (Player)", this.getInputSchema().getType(References.PLAYER), this::fixEntity)});
    }

    private Typed<?> fixDataComponents(Typed<?> components) {
        return components.update(DSL.remainderFinder(), (dynamic) -> {
            return dynamic.update("minecraft:attribute_modifiers", (dynamic1) -> {
                return dynamic1.update("modifiers", (dynamic2) -> {
                    Optional optional = dynamic2.asStreamOpt().result().map((stream) -> {
                        return stream.map(this::fixTypeField);
                    });

                    Objects.requireNonNull(dynamic2);
                    return (Dynamic) DataFixUtils.orElse(optional.map(dynamic2::createList), dynamic2);
                });
            });
        });
    }

    private Typed<?> fixEntity(Typed<?> entity) {
        return entity.update(DSL.remainderFinder(), (dynamic) -> {
            return dynamic.update("attributes", (dynamic1) -> {
                Optional optional = dynamic1.asStreamOpt().result().map((stream) -> {
                    return stream.map(this::fixIdField);
                });

                Objects.requireNonNull(dynamic1);
                return (Dynamic) DataFixUtils.orElse(optional.map(dynamic1::createList), dynamic1);
            });
        });
    }

    private Dynamic<?> fixIdField(Dynamic<?> dynamic) {
        return ExtraDataFixUtils.fixStringField(dynamic, "id", this.renames);
    }

    private Dynamic<?> fixTypeField(Dynamic<?> dynamic) {
        return ExtraDataFixUtils.fixStringField(dynamic, "type", this.renames);
    }
}
