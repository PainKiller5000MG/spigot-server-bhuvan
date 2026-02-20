package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.Optional;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.ExtraDataFixUtils;
import net.minecraft.util.datafix.LegacyComponentDataFixUtils;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class EntityCustomNameToComponentFix extends DataFix {

    public EntityCustomNameToComponentFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ENTITY);
        Type<?> type1 = this.getOutputSchema().getType(References.ENTITY);
        OpticFinder<String> opticfinder = DSL.fieldFinder("id", NamespacedSchema.namespacedString());
        OpticFinder<String> opticfinder1 = type.findField("CustomName");
        Type<?> type2 = type1.findFieldType("CustomName");

        return this.fixTypeEverywhereTyped("EntityCustomNameToComponentFix", type, type1, (typed) -> {
            return fixEntity(typed, type1, opticfinder, opticfinder1, type2);
        });
    }

    private static <T> Typed<?> fixEntity(Typed<?> entity, Type<?> newEntityType, OpticFinder<String> idF, OpticFinder<String> customNameF, Type<T> newCustomNameType) {
        Optional<String> optional = entity.getOptional(customNameF);

        if (optional.isEmpty()) {
            return ExtraDataFixUtils.cast(newEntityType, entity);
        } else if (((String) optional.get()).isEmpty()) {
            return Util.writeAndReadTypedOrThrow(entity, newEntityType, (dynamic) -> {
                return dynamic.remove("CustomName");
            });
        } else {
            String s = (String) entity.getOptional(idF).orElse("");
            Dynamic<?> dynamic = fixCustomName(entity.getOps(), (String) optional.get(), s);

            return entity.set(customNameF, Util.readTypedOrThrow(newCustomNameType, dynamic));
        }
    }

    private static <T> Dynamic<T> fixCustomName(DynamicOps<T> ops, String customName, String id) {
        return "minecraft:commandblock_minecart".equals(id) ? new Dynamic(ops, ops.createString(customName)) : LegacyComponentDataFixUtils.createPlainTextComponent(ops, customName);
    }
}
