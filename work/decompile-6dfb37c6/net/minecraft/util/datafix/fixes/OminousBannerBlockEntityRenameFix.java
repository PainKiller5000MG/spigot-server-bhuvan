package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.util.Pair;

public class OminousBannerBlockEntityRenameFix extends NamedEntityFix {

    public OminousBannerBlockEntityRenameFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "OminousBannerBlockEntityRenameFix", References.BLOCK_ENTITY, "minecraft:banner");
    }

    @Override
    protected Typed<?> fix(Typed<?> entity) {
        OpticFinder<?> opticfinder = entity.getType().findField("CustomName");
        OpticFinder<Pair<String, String>> opticfinder1 = DSL.typeFinder(this.getInputSchema().getType(References.TEXT_COMPONENT));

        return entity.updateTyped(opticfinder, (typed1) -> {
            return typed1.update(opticfinder1, (pair) -> {
                return pair.mapSecond((s) -> {
                    return s.replace("\"translate\":\"block.minecraft.illager_banner\"", "\"translate\":\"block.minecraft.ominous_banner\"");
                });
            });
        });
    }
}
