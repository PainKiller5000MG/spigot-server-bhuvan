package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class CauldronRenameFix extends DataFix {

    public CauldronRenameFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    private static Dynamic<?> fix(Dynamic<?> tag) {
        Optional<String> optional = tag.get("Name").asString().result();

        if (optional.equals(Optional.of("minecraft:cauldron"))) {
            Dynamic<?> dynamic1 = tag.get("Properties").orElseEmptyMap();

            return dynamic1.get("level").asString("0").equals("0") ? tag.remove("Properties") : tag.set("Name", tag.createString("minecraft:water_cauldron"));
        } else {
            return tag;
        }
    }

    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped("cauldron_rename_fix", this.getInputSchema().getType(References.BLOCK_STATE), (typed) -> {
            return typed.update(DSL.remainderFinder(), CauldronRenameFix::fix);
        });
    }
}
