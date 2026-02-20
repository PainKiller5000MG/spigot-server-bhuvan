package net.minecraft.world.level.chunk.storage;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

public class SimpleRegionStorage implements AutoCloseable {

    private final IOWorker worker;
    private final DataFixer fixerUpper;
    private final DataFixTypes dataFixType;
    private final Supplier<LegacyTagFixer> legacyFixer;

    public SimpleRegionStorage(RegionStorageInfo info, Path folder, DataFixer fixerUpper, boolean syncWrites, DataFixTypes dataFixType) {
        this(info, folder, fixerUpper, syncWrites, dataFixType, LegacyTagFixer.EMPTY);
    }

    public SimpleRegionStorage(RegionStorageInfo info, Path folder, DataFixer fixerUpper, boolean syncWrites, DataFixTypes dataFixType, Supplier<LegacyTagFixer> legacyFixer) {
        this.fixerUpper = fixerUpper;
        this.dataFixType = dataFixType;
        this.worker = new IOWorker(info, folder, syncWrites);
        Objects.requireNonNull(legacyFixer);
        this.legacyFixer = Suppliers.memoize(legacyFixer::get);
    }

    public boolean isOldChunkAround(ChunkPos pos, int range) {
        return this.worker.isOldChunkAround(pos, range);
    }

    public CompletableFuture<Optional<CompoundTag>> read(ChunkPos pos) {
        return this.worker.loadAsync(pos);
    }

    public CompletableFuture<Void> write(ChunkPos pos, CompoundTag value) {
        return this.write(pos, () -> {
            return value;
        });
    }

    public CompletableFuture<Void> write(ChunkPos pos, Supplier<CompoundTag> supplier) {
        this.markChunkDone(pos);
        return this.worker.store(pos, supplier);
    }

    public CompoundTag upgradeChunkTag(CompoundTag chunkTag, int defaultVersion, @Nullable CompoundTag dataFixContextTag) {
        int j = NbtUtils.getDataVersion(chunkTag, defaultVersion);

        if (j == SharedConstants.getCurrentVersion().dataVersion().version()) {
            return chunkTag;
        } else {
            try {
                chunkTag = ((LegacyTagFixer) this.legacyFixer.get()).applyFix(chunkTag);
                injectDatafixingContext(chunkTag, dataFixContextTag);
                chunkTag = this.dataFixType.updateToCurrentVersion(this.fixerUpper, chunkTag, Math.max(((LegacyTagFixer) this.legacyFixer.get()).targetDataVersion(), j));
                removeDatafixingContext(chunkTag);
                NbtUtils.addCurrentDataVersion(chunkTag);
                return chunkTag;
            } catch (Exception exception) {
                CrashReport crashreport = CrashReport.forThrowable(exception, "Updated chunk");
                CrashReportCategory crashreportcategory = crashreport.addCategory("Updated chunk details");

                crashreportcategory.setDetail("Data version", j);
                throw new ReportedException(crashreport);
            }
        }
    }

    public CompoundTag upgradeChunkTag(CompoundTag chunkTag, int defaultVersion) {
        return this.upgradeChunkTag(chunkTag, defaultVersion, (CompoundTag) null);
    }

    public Dynamic<Tag> upgradeChunkTag(Dynamic<Tag> chunkTag, int defaultVersion) {
        return new Dynamic(chunkTag.getOps(), this.upgradeChunkTag((CompoundTag) chunkTag.getValue(), defaultVersion, (CompoundTag) null));
    }

    public static void injectDatafixingContext(CompoundTag chunkTag, @Nullable CompoundTag contextTag) {
        if (contextTag != null) {
            chunkTag.put("__context", contextTag);
        }

    }

    private static void removeDatafixingContext(CompoundTag chunkTag) {
        chunkTag.remove("__context");
    }

    protected void markChunkDone(ChunkPos pos) {
        ((LegacyTagFixer) this.legacyFixer.get()).markChunkDone(pos);
    }

    public CompletableFuture<Void> synchronize(boolean flush) {
        return this.worker.synchronize(flush);
    }

    public void close() throws IOException {
        this.worker.close();
    }

    public ChunkScanAccess chunkScanner() {
        return this.worker;
    }

    public RegionStorageInfo storageInfo() {
        return this.worker.storageInfo();
    }
}
