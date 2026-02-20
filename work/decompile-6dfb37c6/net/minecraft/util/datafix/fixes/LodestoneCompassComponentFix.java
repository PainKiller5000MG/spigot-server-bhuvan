package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class LodestoneCompassComponentFix extends DataComponentRemainderFix {

    public LodestoneCompassComponentFix(Schema outputSchema) {
        super(outputSchema, "LodestoneCompassComponentFix", "minecraft:lodestone_target", "minecraft:lodestone_tracker");
    }

    @Override
    protected <T> Dynamic<T> fixComponent(Dynamic<T> input) {
        Optional<Dynamic<T>> optional = input.get("pos").result();
        Optional<Dynamic<T>> optional1 = input.get("dimension").result();

        input = input.remove("pos").remove("dimension");
        if (optional.isPresent() && optional1.isPresent()) {
            input = input.set("target", input.emptyMap().set("pos", (Dynamic) optional.get()).set("dimension", (Dynamic) optional1.get()));
        }

        return input;
    }
}
