package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import org.jspecify.annotations.Nullable;

public class TridentAnimationFix extends DataComponentRemainderFix {

    public TridentAnimationFix(Schema outputSchema) {
        super(outputSchema, "TridentAnimationFix", "minecraft:consumable");
    }

    @Override
    protected <T> @Nullable Dynamic<T> fixComponent(Dynamic<T> input) {
        return input.update("animation", (dynamic1) -> {
            String s = (String) dynamic1.asString().result().orElse("");

            return "spear".equals(s) ? dynamic1.createString("trident") : dynamic1;
        });
    }
}
