package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.util.Objects;

public class BlockEntityBannerColorFix extends NamedEntityFix {

    public BlockEntityBannerColorFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "BlockEntityBannerColorFix", References.BLOCK_ENTITY, "minecraft:banner");
    }

    public Dynamic<?> fixTag(Dynamic<?> input) {
        input = input.update("Base", (dynamic1) -> {
            return dynamic1.createInt(15 - dynamic1.asInt(0));
        });
        input = input.update("Patterns", (dynamic1) -> {
            DataResult dataresult = dynamic1.asStreamOpt().map((stream) -> {
                return stream.map((dynamic2) -> {
                    return dynamic2.update("Color", (dynamic3) -> {
                        return dynamic3.createInt(15 - dynamic3.asInt(0));
                    });
                });
            });

            Objects.requireNonNull(dynamic1);
            return (Dynamic) DataFixUtils.orElse(dataresult.map(dynamic1::createList).result(), dynamic1);
        });
        return input;
    }

    @Override
    protected Typed<?> fix(Typed<?> entity) {
        return entity.update(DSL.remainderFinder(), this::fixTag);
    }
}
