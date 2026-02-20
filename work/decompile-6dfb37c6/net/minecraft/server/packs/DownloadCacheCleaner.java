package net.minecraft.server.packs;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;

public class DownloadCacheCleaner {

    private static final Logger LOGGER = LogUtils.getLogger();

    public DownloadCacheCleaner() {}

    public static void vacuumCacheDir(Path cacheDir, int maxFiles) {
        try {
            List<DownloadCacheCleaner.PathAndTime> list = listFilesWithModificationTimes(cacheDir);
            int j = list.size() - maxFiles;

            if (j <= 0) {
                return;
            }

            list.sort(DownloadCacheCleaner.PathAndTime.NEWEST_FIRST);
            List<DownloadCacheCleaner.PathAndPriority> list1 = prioritizeFilesInDirs(list);

            Collections.reverse(list1);
            list1.sort(DownloadCacheCleaner.PathAndPriority.HIGHEST_PRIORITY_FIRST);
            Set<Path> set = new HashSet();

            for (int k = 0; k < j; ++k) {
                DownloadCacheCleaner.PathAndPriority downloadcachecleaner_pathandpriority = (DownloadCacheCleaner.PathAndPriority) list1.get(k);
                Path path1 = downloadcachecleaner_pathandpriority.path;

                try {
                    Files.delete(path1);
                    if (downloadcachecleaner_pathandpriority.removalPriority == 0) {
                        set.add(path1.getParent());
                    }
                } catch (IOException ioexception) {
                    DownloadCacheCleaner.LOGGER.warn("Failed to delete cache file {}", path1, ioexception);
                }
            }

            set.remove(cacheDir);

            for (Path path2 : set) {
                try {
                    Files.delete(path2);
                } catch (DirectoryNotEmptyException directorynotemptyexception) {
                    ;
                } catch (IOException ioexception1) {
                    DownloadCacheCleaner.LOGGER.warn("Failed to delete empty(?) cache directory {}", path2, ioexception1);
                }
            }
        } catch (UncheckedIOException | IOException ioexception2) {
            DownloadCacheCleaner.LOGGER.error("Failed to vacuum cache dir {}", cacheDir, ioexception2);
        }

    }

    private static List<DownloadCacheCleaner.PathAndTime> listFilesWithModificationTimes(final Path cacheDir) throws IOException {
        try {
            final List<DownloadCacheCleaner.PathAndTime> list = new ArrayList();

            Files.walkFileTree(cacheDir, new SimpleFileVisitor<Path>() {
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile() && !file.getParent().equals(cacheDir)) {
                        FileTime filetime = attrs.lastModifiedTime();

                        list.add(new DownloadCacheCleaner.PathAndTime(file, filetime));
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
            return list;
        } catch (NoSuchFileException nosuchfileexception) {
            return List.of();
        }
    }

    private static List<DownloadCacheCleaner.PathAndPriority> prioritizeFilesInDirs(List<DownloadCacheCleaner.PathAndTime> filesAndDates) {
        List<DownloadCacheCleaner.PathAndPriority> list1 = new ArrayList();
        Object2IntOpenHashMap<Path> object2intopenhashmap = new Object2IntOpenHashMap();

        for (DownloadCacheCleaner.PathAndTime downloadcachecleaner_pathandtime : filesAndDates) {
            int i = object2intopenhashmap.addTo(downloadcachecleaner_pathandtime.path.getParent(), 1);

            list1.add(new DownloadCacheCleaner.PathAndPriority(downloadcachecleaner_pathandtime.path, i));
        }

        return list1;
    }

    private static record PathAndTime(Path path, FileTime modifiedTime) {

        public static final Comparator<DownloadCacheCleaner.PathAndTime> NEWEST_FIRST = Comparator.comparing(DownloadCacheCleaner.PathAndTime::modifiedTime).reversed();
    }

    private static record PathAndPriority(Path path, int removalPriority) {

        public static final Comparator<DownloadCacheCleaner.PathAndPriority> HIGHEST_PRIORITY_FIRST = Comparator.comparing(DownloadCacheCleaner.PathAndPriority::removalPriority).reversed();
    }
}
