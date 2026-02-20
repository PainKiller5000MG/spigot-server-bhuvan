package net.minecraft.util.datafix.fixes;

import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.function.Function;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public class FixProjectileStoredItem extends DataFix {

    private static final String EMPTY_POTION = "minecraft:empty";

    public FixProjectileStoredItem(Schema outputSchema) {
        super(outputSchema, true);
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ENTITY);
        Type<?> type1 = this.getOutputSchema().getType(References.ENTITY);

        return this.fixTypeEverywhereTyped("Fix AbstractArrow item type", type, type1, ExtraDataFixUtils.chainAllFilters(this.fixChoice("minecraft:trident", FixProjectileStoredItem::castUnchecked), this.fixChoice("minecraft:arrow", FixProjectileStoredItem::fixArrow), this.fixChoice("minecraft:spectral_arrow", FixProjectileStoredItem::fixSpectralArrow)));
    }

    private Function<Typed<?>, Typed<?>> fixChoice(String entityName, FixProjectileStoredItem.SubFixer<?> fixer) {
        Type<?> type = this.getInputSchema().getChoiceType(References.ENTITY, entityName);
        Type<?> type1 = this.getOutputSchema().getChoiceType(References.ENTITY, entityName);

        return fixChoiceCap(entityName, fixer, type, type1);
    }

    private static <T> Function<Typed<?>, Typed<?>> fixChoiceCap(String entityName, FixProjectileStoredItem.SubFixer<?> fixer, Type<?> inputEntityChoiceType, Type<T> outputEntityChoiceType) {
        OpticFinder<?> opticfinder = DSL.namedChoice(entityName, inputEntityChoiceType);

        return (typed) -> {
            return typed.updateTyped(opticfinder, outputEntityChoiceType, (typed1) -> {
                return fixer.fix(typed1, outputEntityChoiceType);
            });
        };
    }

    private static <T> Typed<T> fixArrow(Typed<?> typed, Type<T> outputType) {
        return Util.writeAndReadTypedOrThrow(typed, outputType, (dynamic) -> {
            return dynamic.set("item", createItemStack(dynamic, getArrowType(dynamic)));
        });
    }

    private static String getArrowType(Dynamic<?> input) {
        return input.get("Potion").asString("minecraft:empty").equals("minecraft:empty") ? "minecraft:arrow" : "minecraft:tipped_arrow";
    }

    private static <T> Typed<T> fixSpectralArrow(Typed<?> typed, Type<T> outputType) {
        return Util.writeAndReadTypedOrThrow(typed, outputType, (dynamic) -> {
            return dynamic.set("item", createItemStack(dynamic, "minecraft:spectral_arrow"));
        });
    }

    private static Dynamic<?> createItemStack(Dynamic<?> input, String itemName) {
        return input.createMap(ImmutableMap.of(input.createString("id"), input.createString(itemName), input.createString("Count"), input.createInt(1)));
    }

    private static <T> Typed<T> castUnchecked(Typed<?> input, Type<T> outputType) {
        return new Typed(outputType, input.getOps(), input.getValue());
    }

    private interface SubFixer<F> {

        Typed<F> fix(Typed<?> input, Type<F> outputType);
    }
}
