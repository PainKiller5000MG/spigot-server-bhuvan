package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import java.util.Set;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class EffectDurationFix extends DataFix {

    private static final Set<String> POTION_ITEMS = Set.of("minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:tipped_arrow");

    public EffectDurationFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    protected TypeRewriteRule makeRule() {
        Schema schema = this.getInputSchema();
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<Pair<String, String>> opticfinder = DSL.fieldFinder("id", DSL.named(References.ITEM_NAME.typeName(), NamespacedSchema.namespacedString()));
        OpticFinder<?> opticfinder1 = type.findField("tag");

        return TypeRewriteRule.seq(this.fixTypeEverywhereTyped("EffectDurationEntity", schema.getType(References.ENTITY), (typed) -> {
            return typed.update(DSL.remainderFinder(), this::updateEntity);
        }), new TypeRewriteRule[]{this.fixTypeEverywhereTyped("EffectDurationPlayer", schema.getType(References.PLAYER), (typed) -> {
                    return typed.update(DSL.remainderFinder(), this::updateEntity);
                }), this.fixTypeEverywhereTyped("EffectDurationItem", type, (typed) -> {
                    if (typed.getOptional(opticfinder).filter((pair) -> {
                        return EffectDurationFix.POTION_ITEMS.contains(pair.getSecond());
                    }).isPresent()) {
                        Optional<? extends Typed<?>> optional = typed.getOptionalTyped(opticfinder1);

                        if (optional.isPresent()) {
                            Dynamic<?> dynamic = (Dynamic) ((Typed) optional.get()).get(DSL.remainderFinder());
                            Typed<?> typed1 = ((Typed) optional.get()).set(DSL.remainderFinder(), dynamic.update("CustomPotionEffects", this::fix));

                            return typed.set(opticfinder1, typed1);
                        }
                    }

                    return typed;
                })});
    }

    private Dynamic<?> fixEffect(Dynamic<?> effect) {
        return effect.update("FactorCalculationData", (dynamic1) -> {
            int i = dynamic1.get("effect_changed_timestamp").asInt(-1);

            dynamic1 = dynamic1.remove("effect_changed_timestamp");
            int j = effect.get("Duration").asInt(-1);
            int k = i - j;

            return dynamic1.set("ticks_active", dynamic1.createInt(k));
        });
    }

    private Dynamic<?> fix(Dynamic<?> input) {
        return input.createList(input.asStream().map(this::fixEffect));
    }

    private Dynamic<?> updateEntity(Dynamic<?> data) {
        data = data.update("Effects", this::fix);
        data = data.update("ActiveEffects", this::fix);
        data = data.update("CustomPotionEffects", this::fix);
        return data;
    }
}
