package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.List;
import com.mojang.datafixers.types.templates.List.ListType;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public class BeehiveFieldRenameFix extends DataFix {

    public BeehiveFieldRenameFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    private Dynamic<?> fixBeehive(Dynamic<?> beehive) {
        return beehive.remove("Bees");
    }

    private Dynamic<?> fixBee(Dynamic<?> bee) {
        bee = bee.remove("EntityData");
        bee = bee.renameField("TicksInHive", "ticks_in_hive");
        bee = bee.renameField("MinOccupationTicks", "min_ticks_in_hive");
        return bee;
    }

    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getChoiceType(References.BLOCK_ENTITY, "minecraft:beehive");
        OpticFinder<?> opticfinder = DSL.namedChoice("minecraft:beehive", type);
        List.ListType<?> list_listtype = (ListType) type.findFieldType("Bees");
        Type<?> type1 = list_listtype.getElement();
        OpticFinder<?> opticfinder1 = DSL.fieldFinder("Bees", list_listtype);
        OpticFinder<?> opticfinder2 = DSL.typeFinder(type1);
        Type<?> type2 = this.getInputSchema().getType(References.BLOCK_ENTITY);
        Type<?> type3 = this.getOutputSchema().getType(References.BLOCK_ENTITY);

        return this.fixTypeEverywhereTyped("BeehiveFieldRenameFix", type2, type3, (typed) -> {
            return ExtraDataFixUtils.cast(type3, typed.updateTyped(opticfinder, (typed1) -> {
                return typed1.update(DSL.remainderFinder(), this::fixBeehive).updateTyped(opticfinder1, (typed2) -> {
                    return typed2.updateTyped(opticfinder2, (typed3) -> {
                        return typed3.update(DSL.remainderFinder(), this::fixBee);
                    });
                });
            }));
        });
    }
}
