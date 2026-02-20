package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class PlayerHeadBlockProfileFix extends NamedEntityFix {

    public PlayerHeadBlockProfileFix(Schema outputSchema) {
        super(outputSchema, false, "PlayerHeadBlockProfileFix", References.BLOCK_ENTITY, "minecraft:skull");
    }

    @Override
    protected Typed<?> fix(Typed<?> entity) {
        return entity.update(DSL.remainderFinder(), this::fix);
    }

    private <T> Dynamic<T> fix(Dynamic<T> entity) {
        Optional<Dynamic<T>> optional = entity.get("SkullOwner").result();
        Optional<Dynamic<T>> optional1 = entity.get("ExtraType").result();
        Optional<Dynamic<T>> optional2 = optional.or(() -> {
            return optional1;
        });

        if (optional2.isEmpty()) {
            return entity;
        } else {
            entity = entity.remove("SkullOwner").remove("ExtraType");
            entity = entity.set("profile", ItemStackComponentizationFix.fixProfile((Dynamic) optional2.get()));
            return entity;
        }
    }
}
