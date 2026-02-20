package net.minecraft.world.level.chunk.storage;

import java.util.Objects;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.world.level.ChunkPos;

public interface ChunkIOErrorReporter {

    void reportChunkLoadFailure(Throwable throwable, RegionStorageInfo storageInfo, ChunkPos pos);

    void reportChunkSaveFailure(Throwable throwable, RegionStorageInfo storageInfo, ChunkPos pos);

    static ReportedException createMisplacedChunkReport(ChunkPos storedPos, ChunkPos requestedPos) {
        String s = String.valueOf(storedPos);
        CrashReport crashreport = CrashReport.forThrowable(new IllegalStateException("Retrieved chunk position " + s + " does not match requested " + String.valueOf(requestedPos)), "Chunk found in invalid location");
        CrashReportCategory crashreportcategory = crashreport.addCategory("Misplaced Chunk");

        Objects.requireNonNull(storedPos);
        crashreportcategory.setDetail("Stored Position", storedPos::toString);
        return new ReportedException(crashreport);
    }

    default void reportMisplacedChunk(ChunkPos storedPos, ChunkPos requestedPos, RegionStorageInfo storageInfo) {
        this.reportChunkLoadFailure(createMisplacedChunkReport(storedPos, requestedPos), storageInfo, requestedPos);
    }
}
