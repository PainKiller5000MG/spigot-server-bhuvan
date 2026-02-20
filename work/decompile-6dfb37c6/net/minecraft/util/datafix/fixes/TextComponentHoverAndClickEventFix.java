package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.Dynamic;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.ExtraDataFixUtils;
import org.jspecify.annotations.Nullable;

public class TextComponentHoverAndClickEventFix extends DataFix {

    public TextComponentHoverAndClickEventFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    protected TypeRewriteRule makeRule() {
        Type<? extends Pair<String, ?>> type = this.getInputSchema().getType(References.TEXT_COMPONENT).findFieldType("hoverEvent");

        return this.createFixer(this.getInputSchema().getTypeRaw(References.TEXT_COMPONENT), this.getOutputSchema().getType(References.TEXT_COMPONENT), type);
    }

    private <C1, C2, H extends Pair<String, ?>> TypeRewriteRule createFixer(Type<C1> oldRawTextComponentType, Type<C2> newTextComponentType, Type<H> hoverEventType) {
        Type<Pair<String, Either<Either<String, List<C1>>, Pair<Either<List<C1>, Unit>, Pair<Either<C1, Unit>, Pair<Either<H, Unit>, Dynamic<?>>>>>>> type3 = DSL.named(References.TEXT_COMPONENT.typeName(), DSL.or(DSL.or(DSL.string(), DSL.list(oldRawTextComponentType)), DSL.and(DSL.optional(DSL.field("extra", DSL.list(oldRawTextComponentType))), DSL.optional(DSL.field("separator", oldRawTextComponentType)), DSL.optional(DSL.field("hoverEvent", hoverEventType)), DSL.remainderType())));

        if (!type3.equals(this.getInputSchema().getType(References.TEXT_COMPONENT))) {
            String s = String.valueOf(type3);

            throw new IllegalStateException("Text component type did not match, expected " + s + " but got " + String.valueOf(this.getInputSchema().getType(References.TEXT_COMPONENT)));
        } else {
            Type<?> type4 = ExtraDataFixUtils.patchSubType(type3, type3, newTextComponentType);

            return this.fixTypeEverywhere("TextComponentHoverAndClickEventFix", type3, newTextComponentType, (dynamicops) -> {
                return (pair) -> {
                    boolean flag = (Boolean) ((Either) pair.getSecond()).map((either) -> {
                        return false;
                    }, (pair1) -> {
                        Pair<Either<H, Unit>, Dynamic<?>> pair2 = (Pair) ((Pair) pair1.getSecond()).getSecond();
                        boolean flag1 = ((Either) pair2.getFirst()).left().isPresent();
                        boolean flag2 = ((Dynamic) pair2.getSecond()).get("clickEvent").result().isPresent();

                        return flag1 || flag2;
                    });

                    return !flag ? pair : Util.writeAndReadTypedOrThrow(ExtraDataFixUtils.cast(type4, pair, dynamicops), newTextComponentType, TextComponentHoverAndClickEventFix::fixTextComponent).getValue();
                };
            });
        }
    }

    private static Dynamic<?> fixTextComponent(Dynamic<?> dynamic) {
        return dynamic.renameAndFixField("hoverEvent", "hover_event", TextComponentHoverAndClickEventFix::fixHoverEvent).renameAndFixField("clickEvent", "click_event", TextComponentHoverAndClickEventFix::fixClickEvent);
    }

    private static Dynamic<?> copyFields(Dynamic<?> target, Dynamic<?> source, String... fields) {
        for (String s : fields) {
            target = Dynamic.copyField(source, s, target, s);
        }

        return target;
    }

    private static Dynamic<?> fixHoverEvent(Dynamic<?> dynamic) {
        Dynamic dynamic1;

        switch (dynamic.get("action").asString("")) {
            case "show_text":
                dynamic1 = dynamic.renameField("contents", "value");
                break;
            case "show_item":
                Dynamic<?> dynamic2 = dynamic.get("contents").orElseEmptyMap();
                Optional<String> optional = dynamic2.asString().result();

                dynamic1 = optional.isPresent() ? dynamic.renameField("contents", "id") : copyFields(dynamic.remove("contents"), dynamic2, "id", "count", "components");
                break;
            case "show_entity":
                Dynamic<?> dynamic3 = dynamic.get("contents").orElseEmptyMap();

                dynamic1 = copyFields(dynamic.remove("contents"), dynamic3, "id", "type", "name").renameField("id", "uuid").renameField("type", "id");
                break;
            default:
                dynamic1 = dynamic;
        }

        return dynamic1;
    }

    private static <T> @Nullable Dynamic<T> fixClickEvent(Dynamic<T> dynamic) {
        String s = dynamic.get("action").asString("");
        String s1 = dynamic.get("value").asString("");
        Dynamic dynamic1;

        switch (s) {
            case "open_url":
                dynamic1 = !validateUri(s1) ? null : dynamic.renameField("value", "url");
                break;
            case "open_file":
                dynamic1 = dynamic.renameField("value", "path");
                break;
            case "run_command":
            case "suggest_command":
                dynamic1 = !validateChat(s1) ? null : dynamic.renameField("value", "command");
                break;
            case "change_page":
                Integer integer = (Integer) dynamic.get("value").result().map(TextComponentHoverAndClickEventFix::parseOldPage).orElse((Object) null);

                if (integer == null) {
                    dynamic1 = null;
                } else {
                    int i = Math.max(integer, 1);

                    dynamic1 = dynamic.remove("value").set("page", dynamic.createInt(i));
                }
                break;
            default:
                dynamic1 = dynamic;
        }

        return dynamic1;
    }

    private static @Nullable Integer parseOldPage(Dynamic<?> value) {
        Optional<Number> optional = value.asNumber().result();

        if (optional.isPresent()) {
            return ((Number) optional.get()).intValue();
        } else {
            try {
                return Integer.parseInt(value.asString(""));
            } catch (Exception exception) {
                return null;
            }
        }
    }

    private static boolean validateUri(String uri) {
        try {
            URI uri1 = new URI(uri);
            String s1 = uri1.getScheme();

            if (s1 == null) {
                return false;
            } else {
                String s2 = s1.toLowerCase(Locale.ROOT);

                return "http".equals(s2) || "https".equals(s2);
            }
        } catch (URISyntaxException urisyntaxexception) {
            return false;
        }
    }

    private static boolean validateChat(String string) {
        for (int i = 0; i < string.length(); ++i) {
            char c0 = string.charAt(i);

            if (c0 == 167 || c0 < ' ' || c0 == 127) {
                return false;
            }
        }

        return true;
    }
}
