package net.minecraft.server.network;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.FilterMask;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.LenientJsonParser;
import net.minecraft.util.StringUtil;
import net.minecraft.util.Util;
import net.minecraft.util.thread.ConsecutiveExecutor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class ServerTextFilter implements AutoCloseable {

    protected static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicInteger WORKER_COUNT = new AtomicInteger(1);
    private static final ThreadFactory THREAD_FACTORY = (runnable) -> {
        Thread thread = new Thread(runnable);

        thread.setName("Chat-Filter-Worker-" + ServerTextFilter.WORKER_COUNT.getAndIncrement());
        return thread;
    };
    private final URL chatEndpoint;
    private final ServerTextFilter.MessageEncoder chatEncoder;
    private final ServerTextFilter.IgnoreStrategy chatIgnoreStrategy;
    private final ExecutorService workerPool;

    protected static ExecutorService createWorkerPool(int maxConcurrentRequests) {
        return Executors.newFixedThreadPool(maxConcurrentRequests, ServerTextFilter.THREAD_FACTORY);
    }

    protected ServerTextFilter(URL chatEndpoint, ServerTextFilter.MessageEncoder chatEncoder, ServerTextFilter.IgnoreStrategy chatIgnoreStrategy, ExecutorService workerPool) {
        this.chatIgnoreStrategy = chatIgnoreStrategy;
        this.workerPool = workerPool;
        this.chatEndpoint = chatEndpoint;
        this.chatEncoder = chatEncoder;
    }

    protected static URL getEndpoint(URI host, @Nullable JsonObject source, String id, String def) throws MalformedURLException {
        String s2 = getEndpointFromConfig(source, id, def);

        return host.resolve("/" + s2).toURL();
    }

    protected static String getEndpointFromConfig(@Nullable JsonObject source, String id, String def) {
        return source != null ? GsonHelper.getAsString(source, id, def) : def;
    }

    public static @Nullable ServerTextFilter createFromConfig(DedicatedServerProperties config) {
        String s = config.textFilteringConfig;

        if (StringUtil.isBlank(s)) {
            return null;
        } else {
            ServerTextFilter servertextfilter;

            switch (config.textFilteringVersion) {
                case 0:
                    servertextfilter = LegacyTextFilter.createTextFilterFromConfig(s);
                    break;
                case 1:
                    servertextfilter = PlayerSafetyServiceTextFilter.createTextFilterFromConfig(s);
                    break;
                default:
                    ServerTextFilter.LOGGER.warn("Could not create text filter - unsupported text filtering version used");
                    servertextfilter = null;
            }

            return servertextfilter;
        }
    }

    protected CompletableFuture<FilteredText> requestMessageProcessing(GameProfile sender, String message, ServerTextFilter.IgnoreStrategy ignoreStrategy, Executor executor) {
        return message.isEmpty() ? CompletableFuture.completedFuture(FilteredText.EMPTY) : CompletableFuture.supplyAsync(() -> {
            JsonObject jsonobject = this.chatEncoder.encode(sender, message);

            try {
                JsonObject jsonobject1 = this.processRequestResponse(jsonobject, this.chatEndpoint);

                return this.filterText(message, ignoreStrategy, jsonobject1);
            } catch (Exception exception) {
                ServerTextFilter.LOGGER.warn("Failed to validate message '{}'", message, exception);
                return FilteredText.fullyFiltered(message);
            }
        }, executor);
    }

    protected abstract FilteredText filterText(String message, ServerTextFilter.IgnoreStrategy ignoreStrategy, JsonObject result);

    protected FilterMask parseMask(String message, JsonArray removedChars, ServerTextFilter.IgnoreStrategy ignoreStrategy) {
        if (removedChars.isEmpty()) {
            return FilterMask.PASS_THROUGH;
        } else if (ignoreStrategy.shouldIgnore(message, removedChars.size())) {
            return FilterMask.FULLY_FILTERED;
        } else {
            FilterMask filtermask = new FilterMask(message.length());

            for (int i = 0; i < removedChars.size(); ++i) {
                filtermask.setFiltered(removedChars.get(i).getAsInt());
            }

            return filtermask;
        }
    }

    public void close() {
        this.workerPool.shutdownNow();
    }

    protected void drainStream(InputStream input) throws IOException {
        byte[] abyte = new byte[1024];

        while (input.read(abyte) != -1) {
            ;
        }

    }

    private JsonObject processRequestResponse(JsonObject payload, URL url) throws IOException {
        HttpURLConnection httpurlconnection = this.makeRequest(payload, url);

        try (InputStream inputstream = httpurlconnection.getInputStream()) {
            if (httpurlconnection.getResponseCode() == 204) {
                return new JsonObject();
            } else {
                try {
                    return LenientJsonParser.parse((Reader) (new InputStreamReader(inputstream, StandardCharsets.UTF_8))).getAsJsonObject();
                } finally {
                    this.drainStream(inputstream);
                }
            }
        }
    }

    protected HttpURLConnection makeRequest(JsonObject payload, URL url) throws IOException {
        HttpURLConnection httpurlconnection = this.getURLConnection(url);

        this.setAuthorizationProperty(httpurlconnection);
        OutputStreamWriter outputstreamwriter = new OutputStreamWriter(httpurlconnection.getOutputStream(), StandardCharsets.UTF_8);

        try (JsonWriter jsonwriter = new JsonWriter(outputstreamwriter)) {
            Streams.write(payload, jsonwriter);
        } catch (Throwable throwable) {
            try {
                outputstreamwriter.close();
            } catch (Throwable throwable1) {
                throwable.addSuppressed(throwable1);
            }

            throw throwable;
        }

        outputstreamwriter.close();
        int i = httpurlconnection.getResponseCode();

        if (i >= 200 && i < 300) {
            return httpurlconnection;
        } else {
            throw new ServerTextFilter.RequestFailedException(i + " " + httpurlconnection.getResponseMessage());
        }
    }

    protected abstract void setAuthorizationProperty(HttpURLConnection connection);

    protected int connectionReadTimeout() {
        return 2000;
    }

    protected HttpURLConnection getURLConnection(URL url) throws IOException {
        HttpURLConnection httpurlconnection = (HttpURLConnection) url.openConnection();

        httpurlconnection.setConnectTimeout(15000);
        httpurlconnection.setReadTimeout(this.connectionReadTimeout());
        httpurlconnection.setUseCaches(false);
        httpurlconnection.setDoOutput(true);
        httpurlconnection.setDoInput(true);
        httpurlconnection.setRequestMethod("POST");
        httpurlconnection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        httpurlconnection.setRequestProperty("Accept", "application/json");
        httpurlconnection.setRequestProperty("User-Agent", "Minecraft server" + SharedConstants.getCurrentVersion().name());
        return httpurlconnection;
    }

    public TextFilter createContext(GameProfile gameProfile) {
        return new ServerTextFilter.PlayerContext(gameProfile);
    }

    protected static class RequestFailedException extends RuntimeException {

        protected RequestFailedException(String message) {
            super(message);
        }
    }

    protected class PlayerContext implements TextFilter {

        protected final GameProfile profile;
        protected final Executor streamExecutor;

        protected PlayerContext(GameProfile profile) {
            this.profile = profile;
            ConsecutiveExecutor consecutiveexecutor = new ConsecutiveExecutor(ServerTextFilter.this.workerPool, "chat stream for " + profile.name());

            Objects.requireNonNull(consecutiveexecutor);
            this.streamExecutor = consecutiveexecutor::schedule;
        }

        @Override
        public CompletableFuture<List<FilteredText>> processMessageBundle(List<String> messages) {
            List<CompletableFuture<FilteredText>> list1 = (List) messages.stream().map((s) -> {
                return ServerTextFilter.this.requestMessageProcessing(this.profile, s, ServerTextFilter.this.chatIgnoreStrategy, this.streamExecutor);
            }).collect(ImmutableList.toImmutableList());

            return Util.sequenceFailFast(list1).exceptionally((throwable) -> {
                return ImmutableList.of();
            });
        }

        @Override
        public CompletableFuture<FilteredText> processStreamMessage(String message) {
            return ServerTextFilter.this.requestMessageProcessing(this.profile, message, ServerTextFilter.this.chatIgnoreStrategy, this.streamExecutor);
        }
    }

    @FunctionalInterface
    public interface IgnoreStrategy {

        ServerTextFilter.IgnoreStrategy NEVER_IGNORE = (s, i) -> {
            return false;
        };
        ServerTextFilter.IgnoreStrategy IGNORE_FULLY_FILTERED = (s, i) -> {
            return s.length() == i;
        };

        static ServerTextFilter.IgnoreStrategy ignoreOverThreshold(int threshold) {
            return (s, j) -> {
                return j >= threshold;
            };
        }

        static ServerTextFilter.IgnoreStrategy select(int hashesToDrop) {
            ServerTextFilter.IgnoreStrategy servertextfilter_ignorestrategy;

            switch (hashesToDrop) {
                case -1:
                    servertextfilter_ignorestrategy = ServerTextFilter.IgnoreStrategy.NEVER_IGNORE;
                    break;
                case 0:
                    servertextfilter_ignorestrategy = ServerTextFilter.IgnoreStrategy.IGNORE_FULLY_FILTERED;
                    break;
                default:
                    servertextfilter_ignorestrategy = ignoreOverThreshold(hashesToDrop);
            }

            return servertextfilter_ignorestrategy;
        }

        boolean shouldIgnore(String message, int removedCharCount);
    }

    @FunctionalInterface
    protected interface MessageEncoder {

        JsonObject encode(GameProfile profile, String message);
    }
}
