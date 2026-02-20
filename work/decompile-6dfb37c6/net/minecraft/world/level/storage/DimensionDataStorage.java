package net.minecraft.world.level.storage;

import com.google.common.collect.Iterables;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.FastBufferedInputStream;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class DimensionDataStorage implements AutoCloseable {

    private static final Logger LOGGER = LogUtils.getLogger();
    public final Map<SavedDataType<?>, Optional<SavedData>> cache = new HashMap();
    private final DataFixer fixerUpper;
    private final HolderLookup.Provider registries;
    private final Path dataFolder;
    private CompletableFuture<?> pendingWriteFuture = CompletableFuture.completedFuture((Object) null);

    public DimensionDataStorage(Path dataFolder, DataFixer fixerUpper, HolderLookup.Provider registries) {
        this.fixerUpper = fixerUpper;
        this.dataFolder = dataFolder;
        this.registries = registries;
    }

    private Path getDataFile(String id) {
        return this.dataFolder.resolve(id + ".dat");
    }

    public <T extends SavedData> T computeIfAbsent(SavedDataType<T> type) {
        T t0 = this.get(type);

        if (t0 != null) {
            return t0;
        } else {
            T t1 = (T) (type.constructor().get());

            this.set(type, t1);
            return t1;
        }
    }

    public <T extends SavedData> @Nullable T get(SavedDataType<T> type) {
        Optional<SavedData> optional = (Optional) this.cache.get(type);

        if (optional == null) {
            optional = Optional.ofNullable(this.readSavedData(type));
            this.cache.put(type, optional);
        }

        return (T) (optional.orElse((Object) null));
    }

    private <T extends SavedData> @Nullable T readSavedData(SavedDataType<T> type) {
        try {
            Path path = this.getDataFile(type.id());

            if (Files.exists(path, new LinkOption[0])) {
                CompoundTag compoundtag = this.readTagFromDisk(type.id(), type.dataFixType(), SharedConstants.getCurrentVersion().dataVersion().version());
                RegistryOps<Tag> registryops = this.registries.<Tag>createSerializationContext(NbtOps.INSTANCE);

                return (T) (type.codec().parse(registryops, compoundtag.get("data")).resultOrPartial((s) -> {
                    DimensionDataStorage.LOGGER.error("Failed to parse saved data for '{}': {}", type, s);
                }).orElse((Object) null));
            }
        } catch (Exception exception) {
            DimensionDataStorage.LOGGER.error("Error loading saved data: {}", type, exception);
        }

        return null;
    }

    public <T extends SavedData> void set(SavedDataType<T> type, T data) {
        this.cache.put(type, Optional.of(data));
        data.setDirty();
    }

    public CompoundTag readTagFromDisk(String id, DataFixTypes type, int newVersion) throws IOException {
        CompoundTag compoundtag;

        try (InputStream inputstream = Files.newInputStream(this.getDataFile(id)); PushbackInputStream pushbackinputstream = new PushbackInputStream(new FastBufferedInputStream(inputstream), 2);) {
            CompoundTag compoundtag1;

            if (this.isGzip(pushbackinputstream)) {
                compoundtag1 = NbtIo.readCompressed((InputStream) pushbackinputstream, NbtAccounter.unlimitedHeap());
            } else {
                try (DataInputStream datainputstream = new DataInputStream(pushbackinputstream)) {
                    compoundtag1 = NbtIo.read((DataInput) datainputstream);
                }
            }

            int j = NbtUtils.getDataVersion(compoundtag1, 1343);

            compoundtag = type.update(this.fixerUpper, compoundtag1, j, newVersion);
        }

        return compoundtag;
    }

    private boolean isGzip(PushbackInputStream inputStream) throws IOException {
        byte[] abyte = new byte[2];
        boolean flag = false;
        int i = inputStream.read(abyte, 0, 2);

        if (i == 2) {
            int j = (abyte[1] & 255) << 8 | abyte[0] & 255;

            if (j == 35615) {
                flag = true;
            }
        }

        if (i != 0) {
            inputStream.unread(abyte, 0, i);
        }

        return flag;
    }

    public CompletableFuture<?> scheduleSave() {
        Map<SavedDataType<?>, CompoundTag> map = this.collectDirtyTagsToSave();

        if (map.isEmpty()) {
            return CompletableFuture.completedFuture((Object) null);
        } else {
            int i = Util.maxAllowedExecutorThreads();
            int j = map.size();

            if (j > i) {
                this.pendingWriteFuture = this.pendingWriteFuture.thenCompose((object) -> {
                    List<CompletableFuture<?>> list = new ArrayList(i);
                    int k = Mth.positiveCeilDiv(j, i);

                    for (List<Map.Entry<SavedDataType<?>, CompoundTag>> list1 : Iterables.partition(map.entrySet(), k)) {
                        list.add(CompletableFuture.runAsync(() -> {
                            for (Map.Entry<SavedDataType<?>, CompoundTag> map_entry : list1) {
                                this.tryWrite((SavedDataType) map_entry.getKey(), (CompoundTag) map_entry.getValue());
                            }

                        }, Util.ioPool()));
                    }

                    return CompletableFuture.allOf((CompletableFuture[]) list.toArray((l) -> {
                        return new CompletableFuture[l];
                    }));
                });
            } else {
                this.pendingWriteFuture = this.pendingWriteFuture.thenCompose((object) -> {
                    return CompletableFuture.allOf((CompletableFuture[]) map.entrySet().stream().map((entry) -> {
                        return CompletableFuture.runAsync(() -> {
                            this.tryWrite((SavedDataType) entry.getKey(), (CompoundTag) entry.getValue());
                        }, Util.ioPool());
                    }).toArray((k) -> {
                        return new CompletableFuture[k];
                    }));
                });
            }

            return this.pendingWriteFuture;
        }
    }

    private Map<SavedDataType<?>, CompoundTag> collectDirtyTagsToSave() {
        Map<SavedDataType<?>, CompoundTag> map = new Object2ObjectArrayMap();
        RegistryOps<Tag> registryops = this.registries.<Tag>createSerializationContext(NbtOps.INSTANCE);

        this.cache.forEach((saveddatatype, optional) -> {
            optional.filter(SavedData::isDirty).ifPresent((saveddata) -> {
                map.put(saveddatatype, this.encodeUnchecked(saveddatatype, saveddata, registryops));
                saveddata.setDirty(false);
            });
        });
        return map;
    }

    private <T extends SavedData> CompoundTag encodeUnchecked(SavedDataType<T> type, SavedData data, RegistryOps<Tag> ops) {
        Codec<T> codec = type.codec();
        CompoundTag compoundtag = new CompoundTag();

        compoundtag.put("data", (Tag) codec.encodeStart(ops, data).getOrThrow());
        NbtUtils.addCurrentDataVersion(compoundtag);
        return compoundtag;
    }

    private void tryWrite(SavedDataType<?> type, CompoundTag tag) {
        Path path = this.getDataFile(type.id());

        try {
            NbtIo.writeCompressed(tag, path);
        } catch (IOException ioexception) {
            DimensionDataStorage.LOGGER.error("Could not save data to {}", path.getFileName(), ioexception);
        }

    }

    public void saveAndJoin() {
        this.scheduleSave().join();
    }

    public void close() {
        this.saveAndJoin();
    }
}
