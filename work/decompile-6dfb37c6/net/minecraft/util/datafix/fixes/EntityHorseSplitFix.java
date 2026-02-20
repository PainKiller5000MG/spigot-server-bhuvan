package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Objects;
import net.minecraft.util.Util;

public class EntityHorseSplitFix extends EntityRenameFix {

    public EntityHorseSplitFix(Schema outputSchema, boolean changesType) {
        super("EntityHorseSplitFix", outputSchema, changesType);
    }

    @Override
    protected Pair<String, Typed<?>> fix(String name, Typed<?> entity) {
        if (Objects.equals("EntityHorse", name)) {
            Dynamic<?> dynamic = (Dynamic) entity.get(DSL.remainderFinder());
            int i = dynamic.get("Type").asInt(0);
            String s1;

            switch (i) {
                case 1:
                    s1 = "Donkey";
                    break;
                case 2:
                    s1 = "Mule";
                    break;
                case 3:
                    s1 = "ZombieHorse";
                    break;
                case 4:
                    s1 = "SkeletonHorse";
                    break;
                default:
                    s1 = "Horse";
            }

            String s2 = s1;
            Type<?> type = (Type) this.getOutputSchema().findChoiceType(References.ENTITY).types().get(s2);

            return Pair.of(s2, Util.writeAndReadTypedOrThrow(entity, type, (dynamic1) -> {
                return dynamic1.remove("Type");
            }));
        } else {
            return Pair.of(name, entity);
        }
    }
}
