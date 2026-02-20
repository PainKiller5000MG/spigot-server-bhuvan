package net.minecraft.util.datafix.fixes;

import com.google.gson.JsonElement;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Util;

public class LegacyHoverEventFix extends DataFix {

    public LegacyHoverEventFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    protected TypeRewriteRule makeRule() {
        Type<? extends Pair<String, ?>> type = this.getInputSchema().getType(References.TEXT_COMPONENT).findFieldType("hoverEvent");

        return this.createFixer(this.getInputSchema().getTypeRaw(References.TEXT_COMPONENT), type);
    }

    private <C, H extends Pair<String, ?>> TypeRewriteRule createFixer(Type<C> rawTextComponentType, Type<H> hoverEventType) {
        Type<Pair<String, Either<Either<String, List<C>>, Pair<Either<List<C>, Unit>, Pair<Either<C, Unit>, Pair<Either<H, Unit>, Dynamic<?>>>>>>> type2 = DSL.named(References.TEXT_COMPONENT.typeName(), DSL.or(DSL.or(DSL.string(), DSL.list(rawTextComponentType)), DSL.and(DSL.optional(DSL.field("extra", DSL.list(rawTextComponentType))), DSL.optional(DSL.field("separator", rawTextComponentType)), DSL.optional(DSL.field("hoverEvent", hoverEventType)), DSL.remainderType())));

        if (!type2.equals(this.getInputSchema().getType(References.TEXT_COMPONENT))) {
            String s = String.valueOf(type2);

            throw new IllegalStateException("Text component type did not match, expected " + s + " but got " + String.valueOf(this.getInputSchema().getType(References.TEXT_COMPONENT)));
        } else {
            return this.fixTypeEverywhere("LegacyHoverEventFix", type2, (dynamicops) -> {
                return (pair) -> {
                    return pair.mapSecond((either) -> {
                        return either.mapRight((pair1) -> {
                            return pair1.mapSecond((pair2) -> {
                                return pair2.mapSecond((pair3) -> {
                                    Dynamic<?> dynamic = (Dynamic) pair3.getSecond();
                                    Optional<? extends Dynamic<?>> optional = dynamic.get("hoverEvent").result();

                                    if (optional.isEmpty()) {
                                        return pair3;
                                    } else {
                                        Optional<? extends Dynamic<?>> optional1 = ((Dynamic) optional.get()).get("value").result();

                                        if (optional1.isEmpty()) {
                                            return pair3;
                                        } else {
                                            String s1 = (String) ((Either) pair3.getFirst()).left().map(Pair::getFirst).orElse("");
                                            H h0 = (H) ((Pair) this.fixHoverEvent(hoverEventType, s1, (Dynamic) optional.get()));

                                            return pair3.mapFirst((either1) -> {
                                                return Either.left(h0);
                                            });
                                        }
                                    }
                                });
                            });
                        });
                    });
                };
            });
        }
    }

    private <H> H fixHoverEvent(Type<H> hoverEventType, String action, Dynamic<?> oldHoverEvent) {
        return (H) ("show_text".equals(action) ? fixShowTextHover(hoverEventType, oldHoverEvent) : createPlaceholderHover(hoverEventType, oldHoverEvent));
    }

    private static <H> H fixShowTextHover(Type<H> hoverEventType, Dynamic<?> oldHoverEvent) {
        Dynamic<?> dynamic1 = oldHoverEvent.renameField("value", "contents");

        return (H) Util.readTypedOrThrow(hoverEventType, dynamic1).getValue();
    }

    private static <H> H createPlaceholderHover(Type<H> hoverEventType, Dynamic<?> oldHoverEvent) {
        JsonElement jsonelement = (JsonElement) oldHoverEvent.convert(JsonOps.INSTANCE).getValue();
        Dynamic<?> dynamic1 = new Dynamic(JavaOps.INSTANCE, Map.of("action", "show_text", "contents", Map.of("text", "Legacy hoverEvent: " + GsonHelper.toStableString(jsonelement))));

        return (H) Util.readTypedOrThrow(hoverEventType, dynamic1).getValue();
    }
}
