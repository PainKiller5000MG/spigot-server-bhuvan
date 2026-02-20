package net.minecraft.server.jsonrpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import org.jspecify.annotations.Nullable;

public class JsonRPCUtils {

    public static final String JSON_RPC_VERSION = "2.0";
    public static final String OPEN_RPC_VERSION = "1.3.2";

    public JsonRPCUtils() {}

    public static JsonObject createSuccessResult(JsonElement id, JsonElement result) {
        JsonObject jsonobject = new JsonObject();

        jsonobject.addProperty("jsonrpc", "2.0");
        jsonobject.add("id", id);
        jsonobject.add("result", result);
        return jsonobject;
    }

    public static JsonObject createRequest(@Nullable Integer id, Identifier method, List<JsonElement> params) {
        JsonObject jsonobject = new JsonObject();

        jsonobject.addProperty("jsonrpc", "2.0");
        if (id != null) {
            jsonobject.addProperty("id", id);
        }

        jsonobject.addProperty("method", method.toString());
        if (!params.isEmpty()) {
            JsonArray jsonarray = new JsonArray(params.size());

            for (JsonElement jsonelement : params) {
                jsonarray.add(jsonelement);
            }

            jsonobject.add("params", jsonarray);
        }

        return jsonobject;
    }

    public static JsonObject createError(JsonElement id, String message, int errorCode, @Nullable String data) {
        JsonObject jsonobject = new JsonObject();

        jsonobject.addProperty("jsonrpc", "2.0");
        jsonobject.add("id", id);
        JsonObject jsonobject1 = new JsonObject();

        jsonobject1.addProperty("code", errorCode);
        jsonobject1.addProperty("message", message);
        if (data != null && !data.isBlank()) {
            jsonobject1.addProperty("data", data);
        }

        jsonobject.add("error", jsonobject1);
        return jsonobject;
    }

    public static @Nullable JsonElement getRequestId(JsonObject jsonObject) {
        return jsonObject.get("id");
    }

    public static @Nullable String getMethodName(JsonObject jsonObject) {
        return GsonHelper.getAsString(jsonObject, "method", (String) null);
    }

    public static @Nullable JsonElement getParams(JsonObject jsonObject) {
        return jsonObject.get("params");
    }

    public static @Nullable JsonElement getResult(JsonObject jsonObject) {
        return jsonObject.get("result");
    }

    public static @Nullable JsonObject getError(JsonObject jsonObject) {
        return GsonHelper.getAsJsonObject(jsonObject, "error", (JsonObject) null);
    }
}
