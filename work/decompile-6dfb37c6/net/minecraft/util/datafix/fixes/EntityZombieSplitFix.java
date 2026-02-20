package net.minecraft.util.datafix.fixes;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.function.Supplier;
import net.minecraft.util.Util;

public class EntityZombieSplitFix extends EntityRenameFix {

    private final Supplier<Type<?>> zombieVillagerType = Suppliers.memoize(() -> {
        return this.getOutputSchema().getChoiceType(References.ENTITY, "ZombieVillager");
    });

    public EntityZombieSplitFix(Schema outputSchema) {
        super("EntityZombieSplitFix", outputSchema, true);
    }

    @Override
    protected Pair<String, Typed<?>> fix(String name, Typed<?> entity) {
        if (!name.equals("Zombie")) {
            return Pair.of(name, entity);
        } else {
            Dynamic<?> dynamic = (Dynamic) entity.getOptional(DSL.remainderFinder()).orElseThrow();
            int i = dynamic.get("ZombieType").asInt(0);
            String s1;
            Typed<?> typed1;

            switch (i) {
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                    s1 = "ZombieVillager";
                    typed1 = this.changeSchemaToZombieVillager(entity, i - 1);
                    break;
                case 6:
                    s1 = "Husk";
                    typed1 = entity;
                    break;
                default:
                    s1 = "Zombie";
                    typed1 = entity;
            }

            return Pair.of(s1, typed1.update(DSL.remainderFinder(), (dynamic1) -> {
                return dynamic1.remove("ZombieType");
            }));
        }
    }

    private Typed<?> changeSchemaToZombieVillager(Typed<?> entity, int profession) {
        return Util.writeAndReadTypedOrThrow(entity, (Type) this.zombieVillagerType.get(), (dynamic) -> {
            return dynamic.set("Profession", dynamic.createInt(profession));
        });
    }
}
