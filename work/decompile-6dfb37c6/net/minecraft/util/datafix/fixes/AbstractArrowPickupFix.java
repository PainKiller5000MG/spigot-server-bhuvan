package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.function.Function;

public class AbstractArrowPickupFix extends DataFix {

    public AbstractArrowPickupFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    protected TypeRewriteRule makeRule() {
        Schema schema = this.getInputSchema();

        return this.fixTypeEverywhereTyped("AbstractArrowPickupFix", schema.getType(References.ENTITY), this::updateProjectiles);
    }

    private Typed<?> updateProjectiles(Typed<?> input) {
        input = this.updateEntity(input, "minecraft:arrow", AbstractArrowPickupFix::updatePickup);
        input = this.updateEntity(input, "minecraft:spectral_arrow", AbstractArrowPickupFix::updatePickup);
        input = this.updateEntity(input, "minecraft:trident", AbstractArrowPickupFix::updatePickup);
        return input;
    }

    private static Dynamic<?> updatePickup(Dynamic<?> tag) {
        if (tag.get("pickup").result().isPresent()) {
            return tag;
        } else {
            boolean flag = tag.get("player").asBoolean(true);

            return tag.set("pickup", tag.createByte((byte) (flag ? 1 : 0))).remove("player");
        }
    }

    private Typed<?> updateEntity(Typed<?> input, String name, Function<Dynamic<?>, Dynamic<?>> function) {
        Type<?> type = this.getInputSchema().getChoiceType(References.ENTITY, name);
        Type<?> type1 = this.getOutputSchema().getChoiceType(References.ENTITY, name);

        return input.updateTyped(DSL.namedChoice(name, type), type1, (typed1) -> {
            return typed1.update(DSL.remainderFinder(), function);
        });
    }
}
