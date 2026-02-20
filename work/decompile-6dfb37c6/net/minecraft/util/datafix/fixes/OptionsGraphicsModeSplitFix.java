package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class OptionsGraphicsModeSplitFix extends DataFix {

    private final String newFieldName;
    private final String valueIfFast;
    private final String valueIfFancy;
    private final String valueIfFabulous;

    public OptionsGraphicsModeSplitFix(Schema outputSchema, String newFieldName, String valueIfFast, String valueIfFancy, String valueIfFabulous) {
        super(outputSchema, true);
        this.newFieldName = newFieldName;
        this.valueIfFast = valueIfFast;
        this.valueIfFancy = valueIfFancy;
        this.valueIfFabulous = valueIfFabulous;
    }

    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped("graphicsMode split to " + this.newFieldName, this.getInputSchema().getType(References.OPTIONS), (typed) -> {
            return typed.update(DSL.remainderFinder(), (dynamic) -> {
                return (Dynamic) DataFixUtils.orElseGet(dynamic.get("graphicsMode").asString().map((s) -> {
                    return dynamic.set(this.newFieldName, dynamic.createString(this.getValue(s)));
                }).result(), () -> {
                    return dynamic.set(this.newFieldName, dynamic.createString(this.valueIfFancy));
                });
            });
        });
    }

    private String getValue(String mode) {
        String s1;

        switch (mode) {
            case "2":
                s1 = this.valueIfFabulous;
                break;
            case "0":
                s1 = this.valueIfFast;
                break;
            default:
                s1 = this.valueIfFancy;
        }

        return s1;
    }
}
