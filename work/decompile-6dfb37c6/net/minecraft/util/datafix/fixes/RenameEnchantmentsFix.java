package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class RenameEnchantmentsFix extends DataFix {

    final String name;
    final Map<String, String> renames;

    public RenameEnchantmentsFix(Schema outputSchema, String name, Map<String, String> renames) {
        super(outputSchema, false);
        this.name = name;
        this.renames = renames;
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<?> opticfinder = type.findField("tag");

        return this.fixTypeEverywhereTyped(this.name, type, (typed) -> {
            return typed.updateTyped(opticfinder, (typed1) -> {
                return typed1.update(DSL.remainderFinder(), this::fixTag);
            });
        });
    }

    private Dynamic<?> fixTag(Dynamic<?> tag) {
        tag = this.fixEnchantmentList(tag, "Enchantments");
        tag = this.fixEnchantmentList(tag, "StoredEnchantments");
        return tag;
    }

    private Dynamic<?> fixEnchantmentList(Dynamic<?> itemStack, String field) {
        return itemStack.update(field, (dynamic1) -> {
            DataResult dataresult = dynamic1.asStreamOpt().map((stream) -> {
                return stream.map((dynamic2) -> {
                    return dynamic2.update("id", (dynamic3) -> {
                        return (Dynamic) dynamic3.asString().map((s1) -> {
                            return dynamic2.createString((String) this.renames.getOrDefault(NamespacedSchema.ensureNamespaced(s1), s1));
                        }).mapOrElse(Function.identity(), (error) -> {
                            return dynamic3;
                        });
                    });
                });
            });

            Objects.requireNonNull(dynamic1);
            return (Dynamic) dataresult.map(dynamic1::createList).mapOrElse(Function.identity(), (error) -> {
                return dynamic1;
            });
        });
    }
}
