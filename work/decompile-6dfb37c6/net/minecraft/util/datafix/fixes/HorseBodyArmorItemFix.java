package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Streams;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class HorseBodyArmorItemFix extends NamedEntityWriteReadFix {

    private final String previousBodyArmorTag;
    private final boolean clearArmorItems;

    public HorseBodyArmorItemFix(Schema outputSchema, String entityName, String previousBodyArmorTag, boolean clearArmorItems) {
        super(outputSchema, true, "Horse armor fix for " + entityName, References.ENTITY, entityName);
        this.previousBodyArmorTag = previousBodyArmorTag;
        this.clearArmorItems = clearArmorItems;
    }

    @Override
    protected <T> Dynamic<T> fix(Dynamic<T> input) {
        Optional<? extends Dynamic<?>> optional = input.get(this.previousBodyArmorTag).result();

        if (optional.isPresent()) {
            Dynamic<?> dynamic1 = (Dynamic) optional.get();
            Dynamic<T> dynamic2 = input.remove(this.previousBodyArmorTag);

            if (this.clearArmorItems) {
                dynamic2 = dynamic2.update("ArmorItems", (dynamic3) -> {
                    return dynamic3.createList(Streams.mapWithIndex(dynamic3.asStream(), (dynamic4, i) -> {
                        return i == 2L ? dynamic4.emptyMap() : dynamic4;
                    }));
                });
                dynamic2 = dynamic2.update("ArmorDropChances", (dynamic3) -> {
                    return dynamic3.createList(Streams.mapWithIndex(dynamic3.asStream(), (dynamic4, i) -> {
                        return i == 2L ? dynamic4.createFloat(0.085F) : dynamic4;
                    }));
                });
            }

            dynamic2 = dynamic2.set("body_armor_item", dynamic1);
            dynamic2 = dynamic2.set("body_armor_drop_chance", input.createFloat(2.0F));
            return dynamic2;
        } else {
            return input;
        }
    }
}
