package net.minecraft.util.datafix;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.Optional;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.LenientJsonParser;
import net.minecraft.util.StrictJsonParser;

public class LegacyComponentDataFixUtils {

    private static final String EMPTY_CONTENTS = createTextComponentJson("");

    public LegacyComponentDataFixUtils() {}

    public static <T> Dynamic<T> createPlainTextComponent(DynamicOps<T> ops, String text) {
        String s1 = createTextComponentJson(text);

        return new Dynamic(ops, ops.createString(s1));
    }

    public static <T> Dynamic<T> createEmptyComponent(DynamicOps<T> ops) {
        return new Dynamic(ops, ops.createString(LegacyComponentDataFixUtils.EMPTY_CONTENTS));
    }

    public static String createTextComponentJson(String text) {
        JsonObject jsonobject = new JsonObject();

        jsonobject.addProperty("text", text);
        return GsonHelper.toStableString(jsonobject);
    }

    public static String createTranslatableComponentJson(String key) {
        JsonObject jsonobject = new JsonObject();

        jsonobject.addProperty("translate", key);
        return GsonHelper.toStableString(jsonobject);
    }

    public static <T> Dynamic<T> createTranslatableComponent(DynamicOps<T> ops, String key) {
        String s1 = createTranslatableComponentJson(key);

        return new Dynamic(ops, ops.createString(s1));
    }

    public static String rewriteFromLenient(String string) {
        if (!string.isEmpty() && !string.equals("null")) {
            char c0 = string.charAt(0);
            char c1 = string.charAt(string.length() - 1);

            if (c0 == '"' && c1 == '"' || c0 == '{' && c1 == '}' || c0 == '[' && c1 == ']') {
                try {
                    JsonElement jsonelement = LenientJsonParser.parse(string);

                    if (jsonelement.isJsonPrimitive()) {
                        return createTextComponentJson(jsonelement.getAsString());
                    }

                    return GsonHelper.toStableString(jsonelement);
                } catch (JsonParseException jsonparseexception) {
                    ;
                }
            }

            return createTextComponentJson(string);
        } else {
            return LegacyComponentDataFixUtils.EMPTY_CONTENTS;
        }
    }

    public static boolean isStrictlyValidJson(Dynamic<?> component) {
        return component.asString().result().filter((s) -> {
            try {
                StrictJsonParser.parse(s);
                return true;
            } catch (JsonParseException jsonparseexception) {
                return false;
            }
        }).isPresent();
    }

    public static Optional<String> extractTranslationString(String component) {
        try {
            JsonElement jsonelement = LenientJsonParser.parse(component);

            if (jsonelement.isJsonObject()) {
                JsonObject jsonobject = jsonelement.getAsJsonObject();
                JsonElement jsonelement1 = jsonobject.get("translate");

                if (jsonelement1 != null && jsonelement1.isJsonPrimitive()) {
                    return Optional.of(jsonelement1.getAsString());
                }
            }
        } catch (JsonParseException jsonparseexception) {
            ;
        }

        return Optional.empty();
    }
}
