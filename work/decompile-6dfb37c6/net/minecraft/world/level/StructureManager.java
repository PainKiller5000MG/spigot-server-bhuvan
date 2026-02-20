package net.minecraft.world.level;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.chunk.StructureAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.jspecify.annotations.Nullable;

public class StructureManager {

    public final LevelAccessor level;
    private final WorldOptions worldOptions;
    private final StructureCheck structureCheck;

    public StructureManager(LevelAccessor level, WorldOptions worldOptions, StructureCheck structureCheck) {
        this.level = level;
        this.worldOptions = worldOptions;
        this.structureCheck = structureCheck;
    }

    public StructureManager forWorldGenRegion(WorldGenRegion region) {
        if (region.getLevel() != this.level) {
            String s = String.valueOf(region.getLevel());

            throw new IllegalStateException("Using invalid structure manager (source level: " + s + ", region: " + String.valueOf(region));
        } else {
            return new StructureManager(region, this.worldOptions, this.structureCheck);
        }
    }

    public List<StructureStart> startsForStructure(ChunkPos pos, Predicate<Structure> matcher) {
        Map<Structure, LongSet> map = this.level.getChunk(pos.x, pos.z, ChunkStatus.STRUCTURE_REFERENCES).getAllReferences();
        ImmutableList.Builder<StructureStart> immutablelist_builder = ImmutableList.builder();

        for (Map.Entry<Structure, LongSet> map_entry : map.entrySet()) {
            Structure structure = (Structure) map_entry.getKey();

            if (matcher.test(structure)) {
                LongSet longset = (LongSet) map_entry.getValue();

                Objects.requireNonNull(immutablelist_builder);
                this.fillStartsForStructure(structure, longset, immutablelist_builder::add);
            }
        }

        return immutablelist_builder.build();
    }

    public List<StructureStart> startsForStructure(SectionPos pos, Structure structure) {
        LongSet longset = this.level.getChunk(pos.x(), pos.z(), ChunkStatus.STRUCTURE_REFERENCES).getReferencesForStructure(structure);
        ImmutableList.Builder<StructureStart> immutablelist_builder = ImmutableList.builder();

        Objects.requireNonNull(immutablelist_builder);
        this.fillStartsForStructure(structure, longset, immutablelist_builder::add);
        return immutablelist_builder.build();
    }

    public void fillStartsForStructure(Structure structure, LongSet referencesForStructure, Consumer<StructureStart> consumer) {
        LongIterator longiterator = referencesForStructure.iterator();

        while (longiterator.hasNext()) {
            long i = (Long) longiterator.next();
            SectionPos sectionpos = SectionPos.of(new ChunkPos(i), this.level.getMinSectionY());
            StructureStart structurestart = this.getStartForStructure(sectionpos, structure, this.level.getChunk(sectionpos.x(), sectionpos.z(), ChunkStatus.STRUCTURE_STARTS));

            if (structurestart != null && structurestart.isValid()) {
                consumer.accept(structurestart);
            }
        }

    }

    public @Nullable StructureStart getStartForStructure(SectionPos pos, Structure structure, StructureAccess chunk) {
        return chunk.getStartForStructure(structure);
    }

    public void setStartForStructure(SectionPos pos, Structure structure, StructureStart start, StructureAccess chunk) {
        chunk.setStartForStructure(structure, start);
    }

    public void addReferenceForStructure(SectionPos pos, Structure structure, long reference, StructureAccess chunk) {
        chunk.addReferenceForStructure(structure, reference);
    }

    public boolean shouldGenerateStructures() {
        return this.worldOptions.generateStructures();
    }

    public StructureStart getStructureAt(BlockPos blockPos, Structure structure) {
        for (StructureStart structurestart : this.startsForStructure(SectionPos.of(blockPos), structure)) {
            if (structurestart.getBoundingBox().isInside(blockPos)) {
                return structurestart;
            }
        }

        return StructureStart.INVALID_START;
    }

    public StructureStart getStructureWithPieceAt(BlockPos blockPos, TagKey<Structure> structureTag) {
        return this.getStructureWithPieceAt(blockPos, (holder) -> {
            return holder.is(structureTag);
        });
    }

    public StructureStart getStructureWithPieceAt(BlockPos blockPos, HolderSet<Structure> structures) {
        Objects.requireNonNull(structures);
        return this.getStructureWithPieceAt(blockPos, structures::contains);
    }

    public StructureStart getStructureWithPieceAt(BlockPos blockPos, Predicate<Holder<Structure>> predicate) {
        Registry<Structure> registry = this.registryAccess().lookupOrThrow(Registries.STRUCTURE);

        for (StructureStart structurestart : this.startsForStructure(new ChunkPos(blockPos), (structure) -> {
            Optional optional = registry.get(registry.getId(structure));

            Objects.requireNonNull(predicate);
            return (Boolean) optional.map(predicate::test).orElse(false);
        })) {
            if (this.structureHasPieceAt(blockPos, structurestart)) {
                return structurestart;
            }
        }

        return StructureStart.INVALID_START;
    }

    public StructureStart getStructureWithPieceAt(BlockPos blockPos, Structure structure) {
        for (StructureStart structurestart : this.startsForStructure(SectionPos.of(blockPos), structure)) {
            if (this.structureHasPieceAt(blockPos, structurestart)) {
                return structurestart;
            }
        }

        return StructureStart.INVALID_START;
    }

    public boolean structureHasPieceAt(BlockPos blockPos, StructureStart structureStart) {
        for (StructurePiece structurepiece : structureStart.getPieces()) {
            if (structurepiece.getBoundingBox().isInside(blockPos)) {
                return true;
            }
        }

        return false;
    }

    public boolean hasAnyStructureAt(BlockPos pos) {
        SectionPos sectionpos = SectionPos.of(pos);

        return this.level.getChunk(sectionpos.x(), sectionpos.z(), ChunkStatus.STRUCTURE_REFERENCES).hasAnyStructureReferences();
    }

    public Map<Structure, LongSet> getAllStructuresAt(BlockPos pos) {
        SectionPos sectionpos = SectionPos.of(pos);

        return this.level.getChunk(sectionpos.x(), sectionpos.z(), ChunkStatus.STRUCTURE_REFERENCES).getAllReferences();
    }

    public StructureCheckResult checkStructurePresence(ChunkPos pos, Structure structure, StructurePlacement placement, boolean createReference) {
        return this.structureCheck.checkStart(pos, structure, placement, createReference);
    }

    public void addReference(StructureStart start) {
        start.addReference();
        this.structureCheck.incrementReference(start.getChunkPos(), start.getStructure());
    }

    public RegistryAccess registryAccess() {
        return this.level.registryAccess();
    }
}
