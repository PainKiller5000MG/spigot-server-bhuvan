package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class ItemStackUUIDFix extends AbstractUUIDFix {

    public ItemStackUUIDFix(Schema outputSchema) {
        super(outputSchema, References.ITEM_STACK);
    }

    public TypeRewriteRule makeRule() {
        OpticFinder<Pair<String, String>> opticfinder = DSL.fieldFinder("id", DSL.named(References.ITEM_NAME.typeName(), NamespacedSchema.namespacedString()));

        return this.fixTypeEverywhereTyped("ItemStackUUIDFix", this.getInputSchema().getType(this.typeReference), (typed) -> {
            OpticFinder<?> opticfinder1 = typed.getType().findField("tag");

            return typed.updateTyped(opticfinder1, (typed1) -> {
                return typed1.update(DSL.remainderFinder(), (dynamic) -> {
                    dynamic = this.updateAttributeModifiers(dynamic);
                    if ((Boolean) typed.getOptional(opticfinder).map((pair) -> {
                        return "minecraft:player_head".equals(pair.getSecond());
                    }).orElse(false)) {
                        dynamic = this.updateSkullOwner(dynamic);
                    }

                    return dynamic;
                });
            });
        });
    }

    private Dynamic<?> updateAttributeModifiers(Dynamic<?> tag) {
        return tag.update("AttributeModifiers", (dynamic1) -> {
            return tag.createList(dynamic1.asStream().map((dynamic2) -> {
                return (Dynamic) replaceUUIDLeastMost(dynamic2, "UUID", "UUID").orElse(dynamic2);
            }));
        });
    }

    private Dynamic<?> updateSkullOwner(Dynamic<?> tag) {
        return tag.update("SkullOwner", (dynamic1) -> {
            return (Dynamic) replaceUUIDString(dynamic1, "Id", "Id").orElse(dynamic1);
        });
    }
}
