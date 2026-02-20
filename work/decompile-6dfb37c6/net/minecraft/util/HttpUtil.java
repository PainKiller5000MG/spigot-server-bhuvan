package net.minecraft.util;

import com.google.common.hash.Funnels;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class HttpUtil {

    private static final Logger LOGGER = LogUtils.getLogger();

    private HttpUtil() {}

    public static Path downloadFile(Path targetDir, URL url, Map<String, String> headers, HashFunction hashFunction, @Nullable HashCode requestedHash, int maxSize, Proxy proxy, HttpUtil.DownloadProgressListener listener) {
        HttpURLConnection httpurlconnection = null;
        InputStream inputstream = null;

        listener.requestStart();
        Path path1;

        if (requestedHash != null) {
            path1 = cachedFilePath(targetDir, requestedHash);

            try {
                if (checkExistingFile(path1, hashFunction, requestedHash)) {
                    HttpUtil.LOGGER.info("Returning cached file since actual hash matches requested");
                    listener.requestFinished(true);
                    updateModificationTime(path1);
                    return path1;
                }
            } catch (IOException ioexception) {
                HttpUtil.LOGGER.warn("Failed to check cached file {}", path1, ioexception);
            }

            try {
                HttpUtil.LOGGER.warn("Existing file {} not found or had mismatched hash", path1);
                Files.deleteIfExists(path1);
            } catch (IOException ioexception1) {
                listener.requestFinished(false);
                throw new UncheckedIOException("Failed to remove existing file " + String.valueOf(path1), ioexception1);
            }
        } else {
            path1 = null;
        }

        Path path2;

        try {
            httpurlconnection = (HttpURLConnection) url.openConnection(proxy);
            httpurlconnection.setInstanceFollowRedirects(true);
            Objects.requireNonNull(httpurlconnection);
            headers.forEach(httpurlconnection::setRequestProperty);
            inputstream = httpurlconnection.getInputStream();
            long j = httpurlconnection.getContentLengthLong();
            OptionalLong optionallong = j != -1L ? OptionalLong.of(j) : OptionalLong.empty();

            FileUtil.createDirectoriesSafe(targetDir);
            listener.downloadStart(optionallong);
            if (optionallong.isPresent() && optionallong.getAsLong() > (long) maxSize) {
                String s = String.valueOf(optionallong);

                throw new IOException("Filesize is bigger than maximum allowed (file is " + s + ", limit is " + maxSize + ")");
            }

            if (path1 == null) {
                Path path3 = Files.createTempFile(targetDir, "download", ".tmp");

                try {
                    HashCode hashcode1 = downloadAndHash(hashFunction, maxSize, listener, inputstream, path3);
                    Path path4 = cachedFilePath(targetDir, hashcode1);

                    if (!checkExistingFile(path4, hashFunction, hashcode1)) {
                        Files.move(path3, path4, StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        updateModificationTime(path4);
                    }

                    listener.requestFinished(true);
                    Path path5 = path4;

                    return path5;
                } finally {
                    Files.deleteIfExists(path3);
                }
            }

            HashCode hashcode2 = downloadAndHash(hashFunction, maxSize, listener, inputstream, path1);

            if (!hashcode2.equals(requestedHash)) {
                String s1 = String.valueOf(hashcode2);

                throw new IOException("Hash of downloaded file (" + s1 + ") did not match requested (" + String.valueOf(requestedHash) + ")");
            }

            listener.requestFinished(true);
            path2 = path1;
        } catch (Throwable throwable) {
            if (httpurlconnection != null) {
                InputStream inputstream1 = httpurlconnection.getErrorStream();

                if (inputstream1 != null) {
                    try {
                        HttpUtil.LOGGER.error("HTTP response error: {}", IOUtils.toString(inputstream1, StandardCharsets.UTF_8));
                    } catch (Exception exception) {
                        HttpUtil.LOGGER.error("Failed to read response from server");
                    }
                }
            }

            listener.requestFinished(false);
            throw new IllegalStateException("Failed to download file " + String.valueOf(url), throwable);
        } finally {
            IOUtils.closeQuietly(inputstream);
        }

        return path2;
    }

    private static void updateModificationTime(Path targetFile) {
        try {
            Files.setLastModifiedTime(targetFile, FileTime.from(Instant.now()));
        } catch (IOException ioexception) {
            HttpUtil.LOGGER.warn("Failed to update modification time of {}", targetFile, ioexception);
        }

    }

    private static HashCode hashFile(Path file, HashFunction hashFunction) throws IOException {
        Hasher hasher = hashFunction.newHasher();

        try (OutputStream outputstream = Funnels.asOutputStream(hasher); InputStream inputstream = Files.newInputStream(file);) {
            inputstream.transferTo(outputstream);
        }

        return hasher.hash();
    }

    private static boolean checkExistingFile(Path file, HashFunction hashFunction, HashCode expectedHash) throws IOException {
        if (Files.exists(file, new LinkOption[0])) {
            HashCode hashcode1 = hashFile(file, hashFunction);

            if (hashcode1.equals(expectedHash)) {
                return true;
            }

            HttpUtil.LOGGER.warn("Mismatched hash of file {}, expected {} but found {}", new Object[]{file, expectedHash, hashcode1});
        }

        return false;
    }

    private static Path cachedFilePath(Path targetDir, HashCode requestedHash) {
        return targetDir.resolve(requestedHash.toString());
    }

    private static HashCode downloadAndHash(HashFunction hashFunction, int maxSize, HttpUtil.DownloadProgressListener listener, InputStream input, Path downloadFile) throws IOException {
        try (OutputStream outputstream = Files.newOutputStream(downloadFile, StandardOpenOption.CREATE)) {
            Hasher hasher = hashFunction.newHasher();
            byte[] abyte = new byte[8196];
            long j = 0L;

            int k;

            while ((k = input.read(abyte)) >= 0) {
                j += (long) k;
                listener.downloadedBytes(j);
                if (j > (long) maxSize) {
                    throw new IOException("Filesize was bigger than maximum allowed (got >= " + j + ", limit was " + maxSize + ")");
                }

                if (Thread.interrupted()) {
                    HttpUtil.LOGGER.error("INTERRUPTED");
                    throw new IOException("Download interrupted");
                }

                outputstream.write(abyte, 0, k);
                hasher.putBytes(abyte, 0, k);
            }

            return hasher.hash();
        }
    }

    public static int getAvailablePort() {
        try (ServerSocket serversocket = new ServerSocket(0)) {
            return serversocket.getLocalPort();
        } catch (IOException ioexception) {
            return 25564;
        }
    }

    public static boolean isPortAvailable(int port) {
        if (port >= 0 && port <= 65535) {
            try {
                boolean flag;

                try (ServerSocket serversocket = new ServerSocket(port)) {
                    flag = serversocket.getLocalPort() == port;
                }

                return flag;
            } catch (IOException ioexception) {
                return false;
            }
        } else {
            return false;
        }
    }

    public interface DownloadProgressListener {

        void requestStart();

        void downloadStart(OptionalLong sizeBytes);

        void downloadedBytes(long bytesSoFar);

        void requestFinished(boolean success);
    }
}
