package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class TippedArrowPotionToItemFix extends NamedEntityWriteReadFix {

    public TippedArrowPotionToItemFix(Schema outputSchema) {
        super(outputSchema, false, "TippedArrowPotionToItemFix", References.ENTITY, "minecraft:arrow");
    }

    @Override
    protected <T> Dynamic<T> fix(Dynamic<T> input) {
        Optional<Dynamic<T>> optional = input.get("Potion").result();
        Optional<Dynamic<T>> optional1 = input.get("custom_potion_effects").result();
        Optional<Dynamic<T>> optional2 = input.get("Color").result();

        return optional.isEmpty() && optional1.isEmpty() && optional2.isEmpty() ? input : input.remove("Potion").remove("custom_potion_effects").remove("Color").update("item", (dynamic1) -> {
            Dynamic<?> dynamic2 = dynamic1.get("tag").orElseEmptyMap();

            if (optional.isPresent()) {
                dynamic2 = dynamic2.set("Potion", (Dynamic) optional.get());
            }

            if (optional1.isPresent()) {
                dynamic2 = dynamic2.set("custom_potion_effects", (Dynamic) optional1.get());
            }

            if (optional2.isPresent()) {
                dynamic2 = dynamic2.set("CustomPotionColor", (Dynamic) optional2.get());
            }

            return dynamic1.set("tag", dynamic2);
        });
    }
}
