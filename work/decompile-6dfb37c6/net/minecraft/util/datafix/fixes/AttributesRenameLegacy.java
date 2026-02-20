package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

public class AttributesRenameLegacy extends DataFix {

    private final String name;
    private final UnaryOperator<String> renames;

    public AttributesRenameLegacy(Schema outputSchema, String name, UnaryOperator<String> renames) {
        super(outputSchema, false);
        this.name = name;
        this.renames = renames;
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<?> opticfinder = type.findField("tag");

        return TypeRewriteRule.seq(this.fixTypeEverywhereTyped(this.name + " (ItemStack)", type, (typed) -> {
            return typed.updateTyped(opticfinder, this::fixItemStackTag);
        }), new TypeRewriteRule[]{this.fixTypeEverywhereTyped(this.name + " (Entity)", this.getInputSchema().getType(References.ENTITY), this::fixEntity), this.fixTypeEverywhereTyped(this.name + " (Player)", this.getInputSchema().getType(References.PLAYER), this::fixEntity)});
    }

    private Dynamic<?> fixName(Dynamic<?> name) {
        Optional optional = name.asString().result().map(this.renames);

        Objects.requireNonNull(name);
        return (Dynamic) DataFixUtils.orElse(optional.map(name::createString), name);
    }

    private Typed<?> fixItemStackTag(Typed<?> itemStack) {
        return itemStack.update(DSL.remainderFinder(), (dynamic) -> {
            return dynamic.update("AttributeModifiers", (dynamic1) -> {
                Optional optional = dynamic1.asStreamOpt().result().map((stream) -> {
                    return stream.map((dynamic2) -> {
                        return dynamic2.update("AttributeName", this::fixName);
                    });
                });

                Objects.requireNonNull(dynamic1);
                return (Dynamic) DataFixUtils.orElse(optional.map(dynamic1::createList), dynamic1);
            });
        });
    }

    private Typed<?> fixEntity(Typed<?> entity) {
        return entity.update(DSL.remainderFinder(), (dynamic) -> {
            return dynamic.update("Attributes", (dynamic1) -> {
                Optional optional = dynamic1.asStreamOpt().result().map((stream) -> {
                    return stream.map((dynamic2) -> {
                        return dynamic2.update("Name", this::fixName);
                    });
                });

                Objects.requireNonNull(dynamic1);
                return (Dynamic) DataFixUtils.orElse(optional.map(dynamic1::createList), dynamic1);
            });
        });
    }
}
