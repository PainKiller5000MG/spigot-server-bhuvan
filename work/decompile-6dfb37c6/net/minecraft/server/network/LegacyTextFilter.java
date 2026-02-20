package net.minecraft.server.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import net.minecraft.network.chat.FilterMask;
import net.minecraft.util.GsonHelper;
import org.jspecify.annotations.Nullable;

public class LegacyTextFilter extends ServerTextFilter {

    private static final String ENDPOINT = "v1/chat";
    private final URL joinEndpoint;
    private final LegacyTextFilter.JoinOrLeaveEncoder joinEncoder;
    private final URL leaveEndpoint;
    private final LegacyTextFilter.JoinOrLeaveEncoder leaveEncoder;
    private final String authKey;

    private LegacyTextFilter(URL chatEndpoint, ServerTextFilter.MessageEncoder chatEncoder, URL joinEndpoint, LegacyTextFilter.JoinOrLeaveEncoder joinEncoder, URL leaveEndpoint, LegacyTextFilter.JoinOrLeaveEncoder leaveEncoder, String authKey, ServerTextFilter.IgnoreStrategy chatIgnoreStrategy, ExecutorService workerPool) {
        super(chatEndpoint, chatEncoder, chatIgnoreStrategy, workerPool);
        this.joinEndpoint = joinEndpoint;
        this.joinEncoder = joinEncoder;
        this.leaveEndpoint = leaveEndpoint;
        this.leaveEncoder = leaveEncoder;
        this.authKey = authKey;
    }

    public static @Nullable ServerTextFilter createTextFilterFromConfig(String config) {
        try {
            JsonObject jsonobject = GsonHelper.parse(config);
            URI uri = new URI(GsonHelper.getAsString(jsonobject, "apiServer"));
            String s1 = GsonHelper.getAsString(jsonobject, "apiKey");

            if (s1.isEmpty()) {
                throw new IllegalArgumentException("Missing API key");
            } else {
                int i = GsonHelper.getAsInt(jsonobject, "ruleId", 1);
                String s2 = GsonHelper.getAsString(jsonobject, "serverId", "");
                String s3 = GsonHelper.getAsString(jsonobject, "roomId", "Java:Chat");
                int j = GsonHelper.getAsInt(jsonobject, "hashesToDrop", -1);
                int k = GsonHelper.getAsInt(jsonobject, "maxConcurrentRequests", 7);
                JsonObject jsonobject1 = GsonHelper.getAsJsonObject(jsonobject, "endpoints", (JsonObject) null);
                String s4 = getEndpointFromConfig(jsonobject1, "chat", "v1/chat");
                boolean flag = s4.equals("v1/chat");
                URL url = uri.resolve("/" + s4).toURL();
                URL url1 = getEndpoint(uri, jsonobject1, "join", "v1/join");
                URL url2 = getEndpoint(uri, jsonobject1, "leave", "v1/leave");
                LegacyTextFilter.JoinOrLeaveEncoder legacytextfilter_joinorleaveencoder = (gameprofile) -> {
                    JsonObject jsonobject2 = new JsonObject();

                    jsonobject2.addProperty("server", s2);
                    jsonobject2.addProperty("room", s3);
                    jsonobject2.addProperty("user_id", gameprofile.id().toString());
                    jsonobject2.addProperty("user_display_name", gameprofile.name());
                    return jsonobject2;
                };
                ServerTextFilter.MessageEncoder servertextfilter_messageencoder;

                if (flag) {
                    servertextfilter_messageencoder = (gameprofile, s5) -> {
                        JsonObject jsonobject2 = new JsonObject();

                        jsonobject2.addProperty("rule", i);
                        jsonobject2.addProperty("server", s2);
                        jsonobject2.addProperty("room", s3);
                        jsonobject2.addProperty("player", gameprofile.id().toString());
                        jsonobject2.addProperty("player_display_name", gameprofile.name());
                        jsonobject2.addProperty("text", s5);
                        jsonobject2.addProperty("language", "*");
                        return jsonobject2;
                    };
                } else {
                    String s5 = String.valueOf(i);

                    servertextfilter_messageencoder = (gameprofile, s6) -> {
                        JsonObject jsonobject2 = new JsonObject();

                        jsonobject2.addProperty("rule_id", s5);
                        jsonobject2.addProperty("category", s2);
                        jsonobject2.addProperty("subcategory", s3);
                        jsonobject2.addProperty("user_id", gameprofile.id().toString());
                        jsonobject2.addProperty("user_display_name", gameprofile.name());
                        jsonobject2.addProperty("text", s6);
                        jsonobject2.addProperty("language", "*");
                        return jsonobject2;
                    };
                }

                ServerTextFilter.IgnoreStrategy servertextfilter_ignorestrategy = ServerTextFilter.IgnoreStrategy.select(j);
                ExecutorService executorservice = createWorkerPool(k);
                String s6 = Base64.getEncoder().encodeToString(s1.getBytes(StandardCharsets.US_ASCII));

                return new LegacyTextFilter(url, servertextfilter_messageencoder, url1, legacytextfilter_joinorleaveencoder, url2, legacytextfilter_joinorleaveencoder, s6, servertextfilter_ignorestrategy, executorservice);
            }
        } catch (Exception exception) {
            LegacyTextFilter.LOGGER.warn("Failed to parse chat filter config {}", config, exception);
            return null;
        }
    }

    @Override
    public TextFilter createContext(GameProfile gameProfile) {
        return new ServerTextFilter.PlayerContext(gameProfile) {
            @Override
            public void join() {
                LegacyTextFilter.this.processJoinOrLeave(this.profile, LegacyTextFilter.this.joinEndpoint, LegacyTextFilter.this.joinEncoder, this.streamExecutor);
            }

            @Override
            public void leave() {
                LegacyTextFilter.this.processJoinOrLeave(this.profile, LegacyTextFilter.this.leaveEndpoint, LegacyTextFilter.this.leaveEncoder, this.streamExecutor);
            }
        };
    }

    private void processJoinOrLeave(GameProfile user, URL endpoint, LegacyTextFilter.JoinOrLeaveEncoder encoder, Executor executor) {
        executor.execute(() -> {
            JsonObject jsonobject = encoder.encode(user);

            try {
                this.processRequest(jsonobject, endpoint);
            } catch (Exception exception) {
                LegacyTextFilter.LOGGER.warn("Failed to send join/leave packet to {} for player {}", new Object[]{endpoint, user, exception});
            }

        });
    }

    private void processRequest(JsonObject payload, URL url) throws IOException {
        HttpURLConnection httpurlconnection = this.makeRequest(payload, url);

        try (InputStream inputstream = httpurlconnection.getInputStream()) {
            this.drainStream(inputstream);
        }

    }

    @Override
    protected void setAuthorizationProperty(HttpURLConnection connection) {
        connection.setRequestProperty("Authorization", "Basic " + this.authKey);
    }

    @Override
    protected FilteredText filterText(String message, ServerTextFilter.IgnoreStrategy ignoreStrategy, JsonObject result) {
        boolean flag = GsonHelper.getAsBoolean(result, "response", false);

        if (flag) {
            return FilteredText.passThrough(message);
        } else {
            String s1 = GsonHelper.getAsString(result, "hashed", (String) null);

            if (s1 == null) {
                return FilteredText.fullyFiltered(message);
            } else {
                JsonArray jsonarray = GsonHelper.getAsJsonArray(result, "hashes");
                FilterMask filtermask = this.parseMask(message, jsonarray, ignoreStrategy);

                return new FilteredText(message, filtermask);
            }
        }
    }

    @FunctionalInterface
    private interface JoinOrLeaveEncoder {

        JsonObject encode(GameProfile profile);
    }
}
