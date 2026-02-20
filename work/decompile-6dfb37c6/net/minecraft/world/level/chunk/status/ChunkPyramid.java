package net.minecraft.world.level.chunk.status;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

public record ChunkPyramid(ImmutableList<ChunkStep> steps) {

    public static final ChunkPyramid GENERATION_PYRAMID = (new ChunkPyramid.Builder()).step(ChunkStatus.EMPTY, (chunkstep_builder) -> {
        return chunkstep_builder;
    }).step(ChunkStatus.STRUCTURE_STARTS, (chunkstep_builder) -> {
        return chunkstep_builder.setTask(ChunkStatusTasks::generateStructureStarts);
    }).step(ChunkStatus.STRUCTURE_REFERENCES, (chunkstep_builder) -> {
        return chunkstep_builder.addRequirement(ChunkStatus.STRUCTURE_STARTS, 8).setTask(ChunkStatusTasks::generateStructureReferences);
    }).step(ChunkStatus.BIOMES, (chunkstep_builder) -> {
        return chunkstep_builder.addRequirement(ChunkStatus.STRUCTURE_STARTS, 8).setTask(ChunkStatusTasks::generateBiomes);
    }).step(ChunkStatus.NOISE, (chunkstep_builder) -> {
        return chunkstep_builder.addRequirement(ChunkStatus.STRUCTURE_STARTS, 8).addRequirement(ChunkStatus.BIOMES, 1).blockStateWriteRadius(0).setTask(ChunkStatusTasks::generateNoise);
    }).step(ChunkStatus.SURFACE, (chunkstep_builder) -> {
        return chunkstep_builder.addRequirement(ChunkStatus.STRUCTURE_STARTS, 8).addRequirement(ChunkStatus.BIOMES, 1).blockStateWriteRadius(0).setTask(ChunkStatusTasks::generateSurface);
    }).step(ChunkStatus.CARVERS, (chunkstep_builder) -> {
        return chunkstep_builder.addRequirement(ChunkStatus.STRUCTURE_STARTS, 8).blockStateWriteRadius(0).setTask(ChunkStatusTasks::generateCarvers);
    }).step(ChunkStatus.FEATURES, (chunkstep_builder) -> {
        return chunkstep_builder.addRequirement(ChunkStatus.STRUCTURE_STARTS, 8).addRequirement(ChunkStatus.CARVERS, 1).blockStateWriteRadius(1).setTask(ChunkStatusTasks::generateFeatures);
    }).step(ChunkStatus.INITIALIZE_LIGHT, (chunkstep_builder) -> {
        return chunkstep_builder.setTask(ChunkStatusTasks::initializeLight);
    }).step(ChunkStatus.LIGHT, (chunkstep_builder) -> {
        return chunkstep_builder.addRequirement(ChunkStatus.INITIALIZE_LIGHT, 1).setTask(ChunkStatusTasks::light);
    }).step(ChunkStatus.SPAWN, (chunkstep_builder) -> {
        return chunkstep_builder.addRequirement(ChunkStatus.BIOMES, 1).setTask(ChunkStatusTasks::generateSpawn);
    }).step(ChunkStatus.FULL, (chunkstep_builder) -> {
        return chunkstep_builder.setTask(ChunkStatusTasks::full);
    }).build();
    public static final ChunkPyramid LOADING_PYRAMID = (new ChunkPyramid.Builder()).step(ChunkStatus.EMPTY, (chunkstep_builder) -> {
        return chunkstep_builder;
    }).step(ChunkStatus.STRUCTURE_STARTS, (chunkstep_builder) -> {
        return chunkstep_builder.setTask(ChunkStatusTasks::loadStructureStarts);
    }).step(ChunkStatus.STRUCTURE_REFERENCES, (chunkstep_builder) -> {
        return chunkstep_builder;
    }).step(ChunkStatus.BIOMES, (chunkstep_builder) -> {
        return chunkstep_builder;
    }).step(ChunkStatus.NOISE, (chunkstep_builder) -> {
        return chunkstep_builder;
    }).step(ChunkStatus.SURFACE, (chunkstep_builder) -> {
        return chunkstep_builder;
    }).step(ChunkStatus.CARVERS, (chunkstep_builder) -> {
        return chunkstep_builder;
    }).step(ChunkStatus.FEATURES, (chunkstep_builder) -> {
        return chunkstep_builder;
    }).step(ChunkStatus.INITIALIZE_LIGHT, (chunkstep_builder) -> {
        return chunkstep_builder.setTask(ChunkStatusTasks::initializeLight);
    }).step(ChunkStatus.LIGHT, (chunkstep_builder) -> {
        return chunkstep_builder.addRequirement(ChunkStatus.INITIALIZE_LIGHT, 1).setTask(ChunkStatusTasks::light);
    }).step(ChunkStatus.SPAWN, (chunkstep_builder) -> {
        return chunkstep_builder;
    }).step(ChunkStatus.FULL, (chunkstep_builder) -> {
        return chunkstep_builder.setTask(ChunkStatusTasks::full);
    }).build();

    public ChunkStep getStepTo(ChunkStatus status) {
        return (ChunkStep) this.steps.get(status.getIndex());
    }

    public static class Builder {

        private final List<ChunkStep> steps = new ArrayList();

        public Builder() {}

        public ChunkPyramid build() {
            return new ChunkPyramid(ImmutableList.copyOf(this.steps));
        }

        public ChunkPyramid.Builder step(ChunkStatus status, UnaryOperator<ChunkStep.Builder> operator) {
            ChunkStep.Builder chunkstep_builder;

            if (this.steps.isEmpty()) {
                chunkstep_builder = new ChunkStep.Builder(status);
            } else {
                chunkstep_builder = new ChunkStep.Builder(status, (ChunkStep) this.steps.getLast());
            }

            this.steps.add(((ChunkStep.Builder) operator.apply(chunkstep_builder)).build());
            return this;
        }
    }
}
