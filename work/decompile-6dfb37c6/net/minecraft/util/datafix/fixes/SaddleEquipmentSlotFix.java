package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.TaggedChoice;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Set;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.ExtraDataFixUtils;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class SaddleEquipmentSlotFix extends DataFix {

    private static final Set<String> ENTITIES_WITH_SADDLE_ITEM = Set.of("minecraft:horse", "minecraft:skeleton_horse", "minecraft:zombie_horse", "minecraft:donkey", "minecraft:mule", "minecraft:camel", "minecraft:llama", "minecraft:trader_llama");
    private static final Set<String> ENTITIES_WITH_SADDLE_FLAG = Set.of("minecraft:pig", "minecraft:strider");
    private static final String SADDLE_FLAG = "Saddle";
    private static final String NEW_SADDLE = "saddle";

    public SaddleEquipmentSlotFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    protected TypeRewriteRule makeRule() {
        TaggedChoice.TaggedChoiceType<String> taggedchoice_taggedchoicetype = this.getInputSchema().findChoiceType(References.ENTITY);
        OpticFinder<Pair<String, ?>> opticfinder = DSL.typeFinder(taggedchoice_taggedchoicetype);
        Type<?> type = this.getInputSchema().getType(References.ENTITY);
        Type<?> type1 = this.getOutputSchema().getType(References.ENTITY);
        Type<?> type2 = ExtraDataFixUtils.patchSubType(type, type, type1);

        return this.fixTypeEverywhereTyped("SaddleEquipmentSlotFix", type, type1, (typed) -> {
            String s = (String) typed.getOptional(opticfinder).map(Pair::getFirst).map(NamespacedSchema::ensureNamespaced).orElse("");
            Typed<?> typed1 = ExtraDataFixUtils.cast(type2, typed);

            return SaddleEquipmentSlotFix.ENTITIES_WITH_SADDLE_ITEM.contains(s) ? Util.writeAndReadTypedOrThrow(typed1, type1, SaddleEquipmentSlotFix::fixEntityWithSaddleItem) : (SaddleEquipmentSlotFix.ENTITIES_WITH_SADDLE_FLAG.contains(s) ? Util.writeAndReadTypedOrThrow(typed1, type1, SaddleEquipmentSlotFix::fixEntityWithSaddleFlag) : ExtraDataFixUtils.cast(type1, typed));
        });
    }

    private static Dynamic<?> fixEntityWithSaddleItem(Dynamic<?> input) {
        return input.get("SaddleItem").result().isEmpty() ? input : fixDropChances(input.renameField("SaddleItem", "saddle"));
    }

    private static Dynamic<?> fixEntityWithSaddleFlag(Dynamic<?> tag) {
        boolean flag = tag.get("Saddle").asBoolean(false);

        tag = tag.remove("Saddle");
        if (!flag) {
            return tag;
        } else {
            Dynamic<?> dynamic1 = tag.emptyMap().set("id", tag.createString("minecraft:saddle")).set("count", tag.createInt(1));

            return fixDropChances(tag.set("saddle", dynamic1));
        }
    }

    private static Dynamic<?> fixDropChances(Dynamic<?> tag) {
        Dynamic<?> dynamic1 = tag.get("drop_chances").orElseEmptyMap().set("saddle", tag.createFloat(2.0F));

        return tag.set("drop_chances", dynamic1);
    }
}
