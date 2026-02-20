package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.Dynamic;

public class ChestedHorsesInventoryZeroIndexingFix extends DataFix {

    public ChestedHorsesInventoryZeroIndexingFix(Schema v3807) {
        super(v3807, false);
    }

    protected TypeRewriteRule makeRule() {
        OpticFinder<Pair<String, Pair<Either<Pair<String, String>, Unit>, Pair<Either<?, Unit>, Dynamic<?>>>>> opticfinder = DSL.typeFinder(this.getInputSchema().getType(References.ITEM_STACK));
        Type<?> type = this.getInputSchema().getType(References.ENTITY);

        return TypeRewriteRule.seq(this.horseLikeInventoryIndexingFixer(opticfinder, type, "minecraft:llama"), new TypeRewriteRule[]{this.horseLikeInventoryIndexingFixer(opticfinder, type, "minecraft:trader_llama"), this.horseLikeInventoryIndexingFixer(opticfinder, type, "minecraft:mule"), this.horseLikeInventoryIndexingFixer(opticfinder, type, "minecraft:donkey")});
    }

    private TypeRewriteRule horseLikeInventoryIndexingFixer(OpticFinder<Pair<String, Pair<Either<Pair<String, String>, Unit>, Pair<Either<?, Unit>, Dynamic<?>>>>> itemStackFinder, Type<?> schema, String horseId) {
        Type<?> type1 = this.getInputSchema().getChoiceType(References.ENTITY, horseId);
        OpticFinder<?> opticfinder1 = DSL.namedChoice(horseId, type1);
        OpticFinder<?> opticfinder2 = type1.findField("Items");

        return this.fixTypeEverywhereTyped("Fix non-zero indexing in chest horse type " + horseId, schema, (typed) -> {
            return typed.updateTyped(opticfinder1, (typed1) -> {
                return typed1.updateTyped(opticfinder2, (typed2) -> {
                    return typed2.update(itemStackFinder, (pair) -> {
                        return pair.mapSecond((pair1) -> {
                            return pair1.mapSecond((pair2) -> {
                                return pair2.mapSecond((dynamic) -> {
                                    return dynamic.update("Slot", (dynamic1) -> {
                                        return dynamic1.createByte((byte) (dynamic1.asInt(2) - 2));
                                    });
                                });
                            });
                        });
                    });
                });
            });
        });
    }
}
