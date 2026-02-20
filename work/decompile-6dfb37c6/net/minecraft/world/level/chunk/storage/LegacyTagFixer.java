package net.minecraft.world.level.chunk.storage;

import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

@FunctionalInterface
public interface LegacyTagFixer {

    Supplier<LegacyTagFixer> EMPTY = () -> {
        return (compoundtag) -> {
            return compoundtag;
        };
    };

    CompoundTag applyFix(CompoundTag tag);

    default void markChunkDone(ChunkPos pos) {}

    default int targetDataVersion() {
        return -1;
    }
}
