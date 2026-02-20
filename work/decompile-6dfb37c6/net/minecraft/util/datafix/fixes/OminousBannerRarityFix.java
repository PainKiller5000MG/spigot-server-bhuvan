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
import net.minecraft.util.datafix.LegacyComponentDataFixUtils;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class OminousBannerRarityFix extends DataFix {

    public OminousBannerRarityFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.BLOCK_ENTITY);
        Type<?> type1 = this.getInputSchema().getType(References.ITEM_STACK);
        TaggedChoice.TaggedChoiceType<?> taggedchoice_taggedchoicetype = this.getInputSchema().findChoiceType(References.BLOCK_ENTITY);
        OpticFinder<Pair<String, String>> opticfinder = DSL.fieldFinder("id", DSL.named(References.ITEM_NAME.typeName(), NamespacedSchema.namespacedString()));
        OpticFinder<?> opticfinder1 = type.findField("components");
        OpticFinder<?> opticfinder2 = type1.findField("components");
        OpticFinder<?> opticfinder3 = opticfinder1.type().findField("minecraft:item_name");
        OpticFinder<Pair<String, String>> opticfinder4 = DSL.typeFinder(this.getInputSchema().getType(References.TEXT_COMPONENT));

        return TypeRewriteRule.seq(this.fixTypeEverywhereTyped("Ominous Banner block entity common rarity to uncommon rarity fix", type, (typed) -> {
            Object object = ((Pair) typed.get(taggedchoice_taggedchoicetype.finder())).getFirst();

            return object.equals("minecraft:banner") ? this.fix(typed, opticfinder1, opticfinder3, opticfinder4) : typed;
        }), this.fixTypeEverywhereTyped("Ominous Banner item stack common rarity to uncommon rarity fix", type1, (typed) -> {
            String s = (String) typed.getOptional(opticfinder).map(Pair::getSecond).orElse("");

            return s.equals("minecraft:white_banner") ? this.fix(typed, opticfinder2, opticfinder3, opticfinder4) : typed;
        }));
    }

    private Typed<?> fix(Typed<?> input, OpticFinder<?> componentsFieldFinder, OpticFinder<?> itemNameFinder, OpticFinder<Pair<String, String>> textComponentFinder) {
        return input.updateTyped(componentsFieldFinder, (typed1) -> {
            boolean flag = typed1.getOptionalTyped(itemNameFinder).flatMap((typed2) -> {
                return typed2.getOptional(textComponentFinder);
            }).map(Pair::getSecond).flatMap(LegacyComponentDataFixUtils::extractTranslationString).filter((s) -> {
                return s.equals("block.minecraft.ominous_banner");
            }).isPresent();

            return flag ? typed1.updateTyped(itemNameFinder, (typed2) -> {
                return typed2.set(textComponentFinder, Pair.of(References.TEXT_COMPONENT.typeName(), LegacyComponentDataFixUtils.createTranslatableComponentJson("block.minecraft.ominous_banner")));
            }).update(DSL.remainderFinder(), (dynamic) -> {
                return dynamic.set("minecraft:rarity", dynamic.createString("uncommon"));
            }) : typed1;
        });
    }
}
