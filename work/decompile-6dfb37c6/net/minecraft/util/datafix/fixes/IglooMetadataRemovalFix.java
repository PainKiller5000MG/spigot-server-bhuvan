package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.util.Objects;

public class IglooMetadataRemovalFix extends DataFix {

    public IglooMetadataRemovalFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.STRUCTURE_FEATURE);

        return this.fixTypeEverywhereTyped("IglooMetadataRemovalFix", type, (typed) -> {
            return typed.update(DSL.remainderFinder(), IglooMetadataRemovalFix::fixTag);
        });
    }

    private static <T> Dynamic<T> fixTag(Dynamic<T> input) {
        boolean flag = (Boolean) input.get("Children").asStreamOpt().map((stream) -> {
            return stream.allMatch(IglooMetadataRemovalFix::isIglooPiece);
        }).result().orElse(false);

        return flag ? input.set("id", input.createString("Igloo")).remove("Children") : input.update("Children", IglooMetadataRemovalFix::removeIglooPieces);
    }

    private static <T> Dynamic<T> removeIglooPieces(Dynamic<T> children) {
        DataResult dataresult = children.asStreamOpt().map((stream) -> {
            return stream.filter((dynamic1) -> {
                return !isIglooPiece(dynamic1);
            });
        });

        Objects.requireNonNull(children);
        return (Dynamic) dataresult.map(children::createList).result().orElse(children);
    }

    private static boolean isIglooPiece(Dynamic<?> tag) {
        return tag.get("id").asString("").equals("Iglu");
    }
}
