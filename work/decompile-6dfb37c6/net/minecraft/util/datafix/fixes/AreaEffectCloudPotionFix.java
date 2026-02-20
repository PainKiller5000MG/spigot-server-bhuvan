package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class AreaEffectCloudPotionFix extends NamedEntityFix {

    public AreaEffectCloudPotionFix(Schema outputSchema) {
        super(outputSchema, false, "AreaEffectCloudPotionFix", References.ENTITY, "minecraft:area_effect_cloud");
    }

    @Override
    protected Typed<?> fix(Typed<?> entity) {
        return entity.update(DSL.remainderFinder(), this::fix);
    }

    private <T> Dynamic<T> fix(Dynamic<T> entity) {
        Optional<Dynamic<T>> optional = entity.get("Color").result();
        Optional<Dynamic<T>> optional1 = entity.get("effects").result();
        Optional<Dynamic<T>> optional2 = entity.get("Potion").result();

        entity = entity.remove("Color").remove("effects").remove("Potion");
        if (optional.isEmpty() && optional1.isEmpty() && optional2.isEmpty()) {
            return entity;
        } else {
            Dynamic<T> dynamic1 = entity.emptyMap();

            if (optional.isPresent()) {
                dynamic1 = dynamic1.set("custom_color", (Dynamic) optional.get());
            }

            if (optional1.isPresent()) {
                dynamic1 = dynamic1.set("custom_effects", (Dynamic) optional1.get());
            }

            if (optional2.isPresent()) {
                dynamic1 = dynamic1.set("potion", (Dynamic) optional2.get());
            }

            return entity.set("potion_contents", dynamic1);
        }
    }
}
