package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.function.Function;

public class AdvancementsRenameFix extends DataFix {

    private final String name;
    private final Function<String, String> renamer;

    public AdvancementsRenameFix(Schema outputSchema, boolean changesType, String name, Function<String, String> renamer) {
        super(outputSchema, changesType);
        this.name = name;
        this.renamer = renamer;
    }

    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(this.name, this.getInputSchema().getType(References.ADVANCEMENTS), (typed) -> {
            return typed.update(DSL.remainderFinder(), (dynamic) -> {
                return dynamic.updateMapValues((pair) -> {
                    String s = ((Dynamic) pair.getFirst()).asString("");

                    return pair.mapFirst((dynamic1) -> {
                        return dynamic.createString((String) this.renamer.apply(s));
                    });
                });
            });
        });
    }
}
