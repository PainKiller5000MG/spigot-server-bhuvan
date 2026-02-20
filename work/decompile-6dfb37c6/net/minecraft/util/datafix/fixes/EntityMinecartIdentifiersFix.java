package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.Util;

public class EntityMinecartIdentifiersFix extends EntityRenameFix {

    public EntityMinecartIdentifiersFix(Schema outputSchema) {
        super("EntityMinecartIdentifiersFix", outputSchema, true);
    }

    @Override
    protected Pair<String, Typed<?>> fix(String name, Typed<?> entity) {
        if (!name.equals("Minecart")) {
            return Pair.of(name, entity);
        } else {
            int i = ((Dynamic) entity.getOrCreate(DSL.remainderFinder())).get("Type").asInt(0);
            String s1;

            switch (i) {
                case 1:
                    s1 = "MinecartChest";
                    break;
                case 2:
                    s1 = "MinecartFurnace";
                    break;
                default:
                    s1 = "MinecartRideable";
            }

            String s2 = s1;
            Type<?> type = (Type) this.getOutputSchema().findChoiceType(References.ENTITY).types().get(s2);

            return Pair.of(s2, Util.writeAndReadTypedOrThrow(entity, type, (dynamic) -> {
                return dynamic.remove("Type");
            }));
        }
    }
}
