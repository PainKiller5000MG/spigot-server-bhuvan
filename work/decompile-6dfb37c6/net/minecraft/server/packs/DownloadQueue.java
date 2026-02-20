package net.minecraft.server.packs;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.FileUtil;
import net.minecraft.util.HttpUtil;
import net.minecraft.util.Util;
import net.minecraft.util.eventlog.JsonEventLog;
import net.minecraft.util.thread.ConsecutiveExecutor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class DownloadQueue implements AutoCloseable {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_KEPT_PACKS = 20;
    private final Path cacheDir;
    private final JsonEventLog<DownloadQueue.LogEntry> eventLog;
    private final ConsecutiveExecutor tasks = new ConsecutiveExecutor(Util.nonCriticalIoPool(), "download-queue");

    public DownloadQueue(Path cacheDir) throws IOException {
        this.cacheDir = cacheDir;
        FileUtil.createDirectoriesSafe(cacheDir);
        this.eventLog = JsonEventLog.<DownloadQueue.LogEntry>open(DownloadQueue.LogEntry.CODEC, cacheDir.resolve("log.json"));
        DownloadCacheCleaner.vacuumCacheDir(cacheDir, 20);
    }

    private DownloadQueue.BatchResult runDownload(DownloadQueue.BatchConfig config, Map<UUID, DownloadQueue.DownloadRequest> requests) {
        DownloadQueue.BatchResult downloadqueue_batchresult = new DownloadQueue.BatchResult();

        requests.forEach((uuid, downloadqueue_downloadrequest) -> {
            Path path = this.cacheDir.resolve(uuid.toString());
            Path path1 = null;

            try {
                path1 = HttpUtil.downloadFile(path, downloadqueue_downloadrequest.url, config.headers, config.hashFunction, downloadqueue_downloadrequest.hash, config.maxSize, config.proxy, config.listener);
                downloadqueue_batchresult.downloaded.put(uuid, path1);
            } catch (Exception exception) {
                DownloadQueue.LOGGER.error("Failed to download {}", downloadqueue_downloadrequest.url, exception);
                downloadqueue_batchresult.failed.add(uuid);
            }

            try {
                this.eventLog.write(new DownloadQueue.LogEntry(uuid, downloadqueue_downloadrequest.url.toString(), Instant.now(), Optional.ofNullable(downloadqueue_downloadrequest.hash).map(HashCode::toString), path1 != null ? this.getFileInfo(path1) : Either.left("download_failed")));
            } catch (Exception exception1) {
                DownloadQueue.LOGGER.error("Failed to log download of {}", downloadqueue_downloadrequest.url, exception1);
            }

        });
        return downloadqueue_batchresult;
    }

    private Either<String, DownloadQueue.FileInfoEntry> getFileInfo(Path downloadedFile) {
        try {
            long i = Files.size(downloadedFile);
            Path path1 = this.cacheDir.relativize(downloadedFile);

            return Either.right(new DownloadQueue.FileInfoEntry(path1.toString(), i));
        } catch (IOException ioexception) {
            DownloadQueue.LOGGER.error("Failed to get file size of {}", downloadedFile, ioexception);
            return Either.left("no_access");
        }
    }

    public CompletableFuture<DownloadQueue.BatchResult> downloadBatch(DownloadQueue.BatchConfig config, Map<UUID, DownloadQueue.DownloadRequest> requests) {
        Supplier supplier = () -> {
            return this.runDownload(config, requests);
        };
        ConsecutiveExecutor consecutiveexecutor = this.tasks;

        Objects.requireNonNull(this.tasks);
        return CompletableFuture.supplyAsync(supplier, consecutiveexecutor::schedule);
    }

    public void close() throws IOException {
        this.tasks.close();
        this.eventLog.close();
    }

    private static record FileInfoEntry(String name, long size) {

        public static final Codec<DownloadQueue.FileInfoEntry> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(Codec.STRING.fieldOf("name").forGetter(DownloadQueue.FileInfoEntry::name), Codec.LONG.fieldOf("size").forGetter(DownloadQueue.FileInfoEntry::size)).apply(instance, DownloadQueue.FileInfoEntry::new);
        });
    }

    private static record LogEntry(UUID id, String url, Instant time, Optional<String> hash, Either<String, DownloadQueue.FileInfoEntry> errorOrFileInfo) {

        public static final Codec<DownloadQueue.LogEntry> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(DownloadQueue.LogEntry::id), Codec.STRING.fieldOf("url").forGetter(DownloadQueue.LogEntry::url), ExtraCodecs.INSTANT_ISO8601.fieldOf("time").forGetter(DownloadQueue.LogEntry::time), Codec.STRING.optionalFieldOf("hash").forGetter(DownloadQueue.LogEntry::hash), Codec.mapEither(Codec.STRING.fieldOf("error"), DownloadQueue.FileInfoEntry.CODEC.fieldOf("file")).forGetter(DownloadQueue.LogEntry::errorOrFileInfo)).apply(instance, DownloadQueue.LogEntry::new);
        });
    }

    public static record BatchResult(Map<UUID, Path> downloaded, Set<UUID> failed) {

        public BatchResult() {
            this(new HashMap(), new HashSet());
        }
    }

    public static record DownloadRequest(URL url, @Nullable HashCode hash) {

    }

    public static record BatchConfig(HashFunction hashFunction, int maxSize, Map<String, String> headers, Proxy proxy, HttpUtil.DownloadProgressListener listener) {

    }
}
