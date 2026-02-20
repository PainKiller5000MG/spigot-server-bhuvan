package net.minecraft.world.level.chunk.storage;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import net.minecraft.util.FastBufferedInputStream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class RegionFileVersion {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Int2ObjectMap<RegionFileVersion> VERSIONS = new Int2ObjectOpenHashMap();
    private static final Object2ObjectMap<String, RegionFileVersion> VERSIONS_BY_NAME = new Object2ObjectOpenHashMap();
    public static final RegionFileVersion VERSION_GZIP = register(new RegionFileVersion(1, (String) null, (inputstream) -> {
        return new FastBufferedInputStream(new GZIPInputStream(inputstream));
    }, (outputstream) -> {
        return new BufferedOutputStream(new GZIPOutputStream(outputstream));
    }));
    public static final RegionFileVersion VERSION_DEFLATE = register(new RegionFileVersion(2, "deflate", (inputstream) -> {
        return new FastBufferedInputStream(new InflaterInputStream(inputstream));
    }, (outputstream) -> {
        return new BufferedOutputStream(new DeflaterOutputStream(outputstream));
    }));
    public static final RegionFileVersion VERSION_NONE = register(new RegionFileVersion(3, "none", FastBufferedInputStream::new, BufferedOutputStream::new));
    public static final RegionFileVersion VERSION_LZ4 = register(new RegionFileVersion(4, "lz4", (inputstream) -> {
        return new FastBufferedInputStream(new LZ4BlockInputStream(inputstream));
    }, (outputstream) -> {
        return new BufferedOutputStream(new LZ4BlockOutputStream(outputstream));
    }));
    public static final RegionFileVersion VERSION_CUSTOM = register(new RegionFileVersion(127, (String) null, (inputstream) -> {
        throw new UnsupportedOperationException();
    }, (outputstream) -> {
        throw new UnsupportedOperationException();
    }));
    public static final RegionFileVersion DEFAULT = RegionFileVersion.VERSION_DEFLATE;
    private static volatile RegionFileVersion selected = RegionFileVersion.DEFAULT;
    private final int id;
    private final @Nullable String optionName;
    private final RegionFileVersion.StreamWrapper<InputStream> inputWrapper;
    private final RegionFileVersion.StreamWrapper<OutputStream> outputWrapper;

    private RegionFileVersion(int id, @Nullable String optionName, RegionFileVersion.StreamWrapper<InputStream> inputWrapper, RegionFileVersion.StreamWrapper<OutputStream> outputWrapper) {
        this.id = id;
        this.optionName = optionName;
        this.inputWrapper = inputWrapper;
        this.outputWrapper = outputWrapper;
    }

    private static RegionFileVersion register(RegionFileVersion version) {
        RegionFileVersion.VERSIONS.put(version.id, version);
        if (version.optionName != null) {
            RegionFileVersion.VERSIONS_BY_NAME.put(version.optionName, version);
        }

        return version;
    }

    public static @Nullable RegionFileVersion fromId(int id) {
        return (RegionFileVersion) RegionFileVersion.VERSIONS.get(id);
    }

    public static void configure(String optionName) {
        RegionFileVersion regionfileversion = (RegionFileVersion) RegionFileVersion.VERSIONS_BY_NAME.get(optionName);

        if (regionfileversion != null) {
            RegionFileVersion.selected = regionfileversion;
        } else {
            RegionFileVersion.LOGGER.error("Invalid `region-file-compression` value `{}` in server.properties. Please use one of: {}", optionName, String.join(", ", RegionFileVersion.VERSIONS_BY_NAME.keySet()));
        }

    }

    public static RegionFileVersion getSelected() {
        return RegionFileVersion.selected;
    }

    public static boolean isValidVersion(int version) {
        return RegionFileVersion.VERSIONS.containsKey(version);
    }

    public int getId() {
        return this.id;
    }

    public OutputStream wrap(OutputStream is) throws IOException {
        return this.outputWrapper.wrap(is);
    }

    public InputStream wrap(InputStream is) throws IOException {
        return this.inputWrapper.wrap(is);
    }

    @FunctionalInterface
    private interface StreamWrapper<O> {

        O wrap(O stream) throws IOException;
    }
}
