package net.minecraft.server.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.ClientCredentialParameters;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.ConfidentialClientApplication.Builder;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.IClientCertificate;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import net.minecraft.util.GsonHelper;
import org.jspecify.annotations.Nullable;

public class PlayerSafetyServiceTextFilter extends ServerTextFilter {

    private final ConfidentialClientApplication client;
    private final ClientCredentialParameters clientParameters;
    private final Set<String> fullyFilteredEvents;
    private final int connectionReadTimeoutMs;

    private PlayerSafetyServiceTextFilter(URL chatEndpoint, ServerTextFilter.MessageEncoder chatEncoder, ServerTextFilter.IgnoreStrategy chatIgnoreStrategy, ExecutorService workerPool, ConfidentialClientApplication client, ClientCredentialParameters clientParameters, Set<String> fullyFilteredEvents, int connectionReadTimeoutMs) {
        super(chatEndpoint, chatEncoder, chatIgnoreStrategy, workerPool);
        this.client = client;
        this.clientParameters = clientParameters;
        this.fullyFilteredEvents = fullyFilteredEvents;
        this.connectionReadTimeoutMs = connectionReadTimeoutMs;
    }

    public static @Nullable ServerTextFilter createTextFilterFromConfig(String textFilteringConfig) {
        JsonObject jsonobject = GsonHelper.parse(textFilteringConfig);
        URI uri = URI.create(GsonHelper.getAsString(jsonobject, "apiServer"));
        String s1 = GsonHelper.getAsString(jsonobject, "apiPath");
        String s2 = GsonHelper.getAsString(jsonobject, "scope");
        String s3 = GsonHelper.getAsString(jsonobject, "serverId", "");
        String s4 = GsonHelper.getAsString(jsonobject, "applicationId");
        String s5 = GsonHelper.getAsString(jsonobject, "tenantId");
        String s6 = GsonHelper.getAsString(jsonobject, "roomId", "Java:Chat");
        String s7 = GsonHelper.getAsString(jsonobject, "certificatePath");
        String s8 = GsonHelper.getAsString(jsonobject, "certificatePassword", "");
        int i = GsonHelper.getAsInt(jsonobject, "hashesToDrop", -1);
        int j = GsonHelper.getAsInt(jsonobject, "maxConcurrentRequests", 7);
        JsonArray jsonarray = GsonHelper.getAsJsonArray(jsonobject, "fullyFilteredEvents");
        Set<String> set = new HashSet();

        jsonarray.forEach((jsonelement) -> {
            set.add(GsonHelper.convertToString(jsonelement, "filteredEvent"));
        });
        int k = GsonHelper.getAsInt(jsonobject, "connectionReadTimeoutMs", 2000);

        URL url;

        try {
            url = uri.resolve(s1).toURL();
        } catch (MalformedURLException malformedurlexception) {
            throw new RuntimeException(malformedurlexception);
        }

        ServerTextFilter.MessageEncoder servertextfilter_messageencoder = (gameprofile, s9) -> {
            JsonObject jsonobject1 = new JsonObject();

            jsonobject1.addProperty("userId", gameprofile.id().toString());
            jsonobject1.addProperty("userDisplayName", gameprofile.name());
            jsonobject1.addProperty("server", s3);
            jsonobject1.addProperty("room", s6);
            jsonobject1.addProperty("area", "JavaChatRealms");
            jsonobject1.addProperty("data", s9);
            jsonobject1.addProperty("language", "*");
            return jsonobject1;
        };
        ServerTextFilter.IgnoreStrategy servertextfilter_ignorestrategy = ServerTextFilter.IgnoreStrategy.select(i);
        ExecutorService executorservice = createWorkerPool(j);

        IClientCertificate iclientcertificate;

        try (InputStream inputstream = Files.newInputStream(Path.of(s7))) {
            iclientcertificate = ClientCredentialFactory.createFromCertificate(inputstream, s8);
        } catch (Exception exception) {
            PlayerSafetyServiceTextFilter.LOGGER.warn("Failed to open certificate file");
            return null;
        }

        ConfidentialClientApplication confidentialclientapplication;

        try {
            confidentialclientapplication = ((Builder) ((Builder) ConfidentialClientApplication.builder(s4, iclientcertificate).sendX5c(true).executorService(executorservice)).authority(String.format(Locale.ROOT, "https://login.microsoftonline.com/%s/", s5))).build();
        } catch (Exception exception1) {
            PlayerSafetyServiceTextFilter.LOGGER.warn("Failed to create confidential client application");
            return null;
        }

        ClientCredentialParameters clientcredentialparameters = ClientCredentialParameters.builder(Set.of(s2)).build();

        return new PlayerSafetyServiceTextFilter(url, servertextfilter_messageencoder, servertextfilter_ignorestrategy, executorservice, confidentialclientapplication, clientcredentialparameters, set, k);
    }

    private IAuthenticationResult aquireIAuthenticationResult() {
        return (IAuthenticationResult) this.client.acquireToken(this.clientParameters).join();
    }

    @Override
    protected void setAuthorizationProperty(HttpURLConnection connection) {
        IAuthenticationResult iauthenticationresult = this.aquireIAuthenticationResult();

        connection.setRequestProperty("Authorization", "Bearer " + iauthenticationresult.accessToken());
    }

    @Override
    protected FilteredText filterText(String message, ServerTextFilter.IgnoreStrategy ignoreStrategy, JsonObject response) {
        JsonObject jsonobject1 = GsonHelper.getAsJsonObject(response, "result", (JsonObject) null);

        if (jsonobject1 == null) {
            return FilteredText.fullyFiltered(message);
        } else {
            boolean flag = GsonHelper.getAsBoolean(jsonobject1, "filtered", true);

            if (!flag) {
                return FilteredText.passThrough(message);
            } else {
                for (JsonElement jsonelement : GsonHelper.getAsJsonArray(jsonobject1, "events", new JsonArray())) {
                    JsonObject jsonobject2 = jsonelement.getAsJsonObject();
                    String s1 = GsonHelper.getAsString(jsonobject2, "id", "");

                    if (this.fullyFilteredEvents.contains(s1)) {
                        return FilteredText.fullyFiltered(message);
                    }
                }

                JsonArray jsonarray = GsonHelper.getAsJsonArray(jsonobject1, "redactedTextIndex", new JsonArray());

                return new FilteredText(message, this.parseMask(message, jsonarray, ignoreStrategy));
            }
        }
    }

    @Override
    protected int connectionReadTimeout() {
        return this.connectionReadTimeoutMs;
    }
}
