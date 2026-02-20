package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public abstract class NamedEntityWriteReadFix extends DataFix {

    private final String name;
    private final String entityName;
    private final TypeReference type;

    public NamedEntityWriteReadFix(Schema outputSchema, boolean changesType, String name, TypeReference type, String entityName) {
        super(outputSchema, changesType);
        this.name = name;
        this.type = type;
        this.entityName = entityName;
    }

    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(this.type);
        Type<?> type1 = this.getInputSchema().getChoiceType(this.type, this.entityName);
        Type<?> type2 = this.getOutputSchema().getType(this.type);
        OpticFinder<?> opticfinder = DSL.namedChoice(this.entityName, type1);
        Type<?> type3 = ExtraDataFixUtils.patchSubType(type, type, type2);

        return this.fix(type, type2, type3, opticfinder);
    }

    private <S, T, A> TypeRewriteRule fix(Type<S> inputEntityType, Type<T> outputEntityType, Type<?> patchedEntityType, OpticFinder<A> choiceFinder) {
        return this.fixTypeEverywhereTyped(this.name, inputEntityType, outputEntityType, (typed) -> {
            if (typed.getOptional(choiceFinder).isEmpty()) {
                return ExtraDataFixUtils.cast(outputEntityType, typed);
            } else {
                Typed<?> typed1 = ExtraDataFixUtils.cast(patchedEntityType, typed);

                return Util.writeAndReadTypedOrThrow(typed1, outputEntityType, this::fix);
            }
        });
    }

    protected abstract <T> Dynamic<T> fix(Dynamic<T> input);
}
