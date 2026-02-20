package net.minecraft.server.packs;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class FilePackResources extends AbstractPackResources {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final FilePackResources.SharedZipFileAccess zipFileAccess;
    private final String prefix;

    private FilePackResources(PackLocationInfo location, FilePackResources.SharedZipFileAccess zipFileAccess, String prefix) {
        super(location);
        this.zipFileAccess = zipFileAccess;
        this.prefix = prefix;
    }

    private static String getPathFromLocation(PackType type, Identifier location) {
        return String.format(Locale.ROOT, "%s/%s/%s", type.getDirectory(), location.getNamespace(), location.getPath());
    }

    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
        return this.getResource(String.join("/", path));
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        return this.getResource(getPathFromLocation(type, location));
    }

    private String addPrefix(String path) {
        return this.prefix.isEmpty() ? path : this.prefix + "/" + path;
    }

    private @Nullable IoSupplier<InputStream> getResource(String path) {
        ZipFile zipfile = this.zipFileAccess.getOrCreateZipFile();

        if (zipfile == null) {
            return null;
        } else {
            ZipEntry zipentry = zipfile.getEntry(this.addPrefix(path));

            return zipentry == null ? null : IoSupplier.create(zipfile, zipentry);
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        ZipFile zipfile = this.zipFileAccess.getOrCreateZipFile();

        if (zipfile == null) {
            return Set.of();
        } else {
            Enumeration<? extends ZipEntry> enumeration = zipfile.entries();
            Set<String> set = Sets.newHashSet();
            String s = this.addPrefix(type.getDirectory() + "/");

            while (enumeration.hasMoreElements()) {
                ZipEntry zipentry = (ZipEntry) enumeration.nextElement();
                String s1 = zipentry.getName();
                String s2 = extractNamespace(s, s1);

                if (!s2.isEmpty()) {
                    if (Identifier.isValidNamespace(s2)) {
                        set.add(s2);
                    } else {
                        FilePackResources.LOGGER.warn("Non [a-z0-9_.-] character in namespace {} in pack {}, ignoring", s2, this.zipFileAccess.file);
                    }
                }
            }

            return set;
        }
    }

    @VisibleForTesting
    public static String extractNamespace(String prefix, String name) {
        if (!name.startsWith(prefix)) {
            return "";
        } else {
            int i = prefix.length();
            int j = name.indexOf(47, i);

            return j == -1 ? name.substring(i) : name.substring(i, j);
        }
    }

    @Override
    public void close() {
        this.zipFileAccess.close();
    }

    @Override
    public void listResources(PackType type, String namespace, String directory, PackResources.ResourceOutput output) {
        ZipFile zipfile = this.zipFileAccess.getOrCreateZipFile();

        if (zipfile != null) {
            Enumeration<? extends ZipEntry> enumeration = zipfile.entries();
            String s2 = type.getDirectory();
            String s3 = this.addPrefix(s2 + "/" + namespace + "/");
            String s4 = s3 + directory + "/";

            while (enumeration.hasMoreElements()) {
                ZipEntry zipentry = (ZipEntry) enumeration.nextElement();

                if (!zipentry.isDirectory()) {
                    String s5 = zipentry.getName();

                    if (s5.startsWith(s4)) {
                        String s6 = s5.substring(s3.length());
                        Identifier identifier = Identifier.tryBuild(namespace, s6);

                        if (identifier != null) {
                            output.accept(identifier, IoSupplier.create(zipfile, zipentry));
                        } else {
                            FilePackResources.LOGGER.warn("Invalid path in datapack: {}:{}, ignoring", namespace, s6);
                        }
                    }
                }
            }

        }
    }

    private static class SharedZipFileAccess implements AutoCloseable {

        private final File file;
        private @Nullable ZipFile zipFile;
        private boolean failedToLoad;

        private SharedZipFileAccess(File file) {
            this.file = file;
        }

        private @Nullable ZipFile getOrCreateZipFile() {
            if (this.failedToLoad) {
                return null;
            } else {
                if (this.zipFile == null) {
                    try {
                        this.zipFile = new ZipFile(this.file);
                    } catch (IOException ioexception) {
                        FilePackResources.LOGGER.error("Failed to open pack {}", this.file, ioexception);
                        this.failedToLoad = true;
                        return null;
                    }
                }

                return this.zipFile;
            }
        }

        public void close() {
            if (this.zipFile != null) {
                IOUtils.closeQuietly(this.zipFile);
                this.zipFile = null;
            }

        }

        protected void finalize() throws Throwable {
            this.close();
            super.finalize();
        }
    }

    public static class FileResourcesSupplier implements Pack.ResourcesSupplier {

        private final File content;

        public FileResourcesSupplier(Path content) {
            this(content.toFile());
        }

        public FileResourcesSupplier(File content) {
            this.content = content;
        }

        @Override
        public PackResources openPrimary(PackLocationInfo location) {
            FilePackResources.SharedZipFileAccess filepackresources_sharedzipfileaccess = new FilePackResources.SharedZipFileAccess(this.content);

            return new FilePackResources(location, filepackresources_sharedzipfileaccess, "");
        }

        @Override
        public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
            FilePackResources.SharedZipFileAccess filepackresources_sharedzipfileaccess = new FilePackResources.SharedZipFileAccess(this.content);
            PackResources packresources = new FilePackResources(location, filepackresources_sharedzipfileaccess, "");
            List<String> list = metadata.overlays();

            if (list.isEmpty()) {
                return packresources;
            } else {
                List<PackResources> list1 = new ArrayList(list.size());

                for (String s : list) {
                    list1.add(new FilePackResources(location, filepackresources_sharedzipfileaccess, s));
                }

                return new CompositePackResources(packresources, list1);
            }
        }
    }
}
