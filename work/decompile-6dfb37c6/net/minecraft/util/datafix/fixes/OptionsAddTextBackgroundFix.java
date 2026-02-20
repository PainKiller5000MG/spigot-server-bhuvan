package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class OptionsAddTextBackgroundFix extends DataFix {

    public OptionsAddTextBackgroundFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped("OptionsAddTextBackgroundFix", this.getInputSchema().getType(References.OPTIONS), (typed) -> {
            return typed.update(DSL.remainderFinder(), (dynamic) -> {
                return (Dynamic) DataFixUtils.orElse(dynamic.get("chatOpacity").asString().map((s) -> {
                    double d0 = this.calculateBackground(s);

                    return dynamic.set("textBackgroundOpacity", dynamic.createString(String.valueOf(d0)));
                }).result(), dynamic);
            });
        });
    }

    private double calculateBackground(String textOpacity) {
        try {
            double d0 = 0.9D * Double.parseDouble(textOpacity) + 0.1D;

            return d0 / 2.0D;
        } catch (NumberFormatException numberformatexception) {
            return 0.5D;
        }
    }
}
