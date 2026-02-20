package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public class ProjectileStoredWeaponFix extends DataFix {

    public ProjectileStoredWeaponFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ENTITY);
        Type<?> type1 = this.getOutputSchema().getType(References.ENTITY);

        return this.fixTypeEverywhereTyped("Fix Arrow stored weapon", type, type1, ExtraDataFixUtils.chainAllFilters(this.fixChoice("minecraft:arrow"), this.fixChoice("minecraft:spectral_arrow")));
    }

    private Function<Typed<?>, Typed<?>> fixChoice(String entityName) {
        Type<?> type = this.getInputSchema().getChoiceType(References.ENTITY, entityName);
        Type<?> type1 = this.getOutputSchema().getChoiceType(References.ENTITY, entityName);

        return fixChoiceCap(entityName, type, type1);
    }

    private static <T> Function<Typed<?>, Typed<?>> fixChoiceCap(String entityName, Type<?> inputEntityChoiceType, Type<T> outputEntityChoiceType) {
        OpticFinder<?> opticfinder = DSL.namedChoice(entityName, inputEntityChoiceType);

        return (typed) -> {
            return typed.updateTyped(opticfinder, outputEntityChoiceType, (typed1) -> {
                return Util.writeAndReadTypedOrThrow(typed1, outputEntityChoiceType, UnaryOperator.identity());
            });
        };
    }
}
