package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

public class StructurePlaceSettings {

    private Mirror mirror;
    private Rotation rotation;
    private BlockPos rotationPivot;
    private boolean ignoreEntities;
    private @Nullable BoundingBox boundingBox;
    private LiquidSettings liquidSettings;
    private @Nullable RandomSource random;
    private int palette;
    private final List<StructureProcessor> processors;
    private boolean knownShape;
    private boolean finalizeEntities;

    public StructurePlaceSettings() {
        this.mirror = Mirror.NONE;
        this.rotation = Rotation.NONE;
        this.rotationPivot = BlockPos.ZERO;
        this.liquidSettings = LiquidSettings.APPLY_WATERLOGGING;
        this.processors = Lists.newArrayList();
    }

    public StructurePlaceSettings copy() {
        StructurePlaceSettings structureplacesettings = new StructurePlaceSettings();

        structureplacesettings.mirror = this.mirror;
        structureplacesettings.rotation = this.rotation;
        structureplacesettings.rotationPivot = this.rotationPivot;
        structureplacesettings.ignoreEntities = this.ignoreEntities;
        structureplacesettings.boundingBox = this.boundingBox;
        structureplacesettings.liquidSettings = this.liquidSettings;
        structureplacesettings.random = this.random;
        structureplacesettings.palette = this.palette;
        structureplacesettings.processors.addAll(this.processors);
        structureplacesettings.knownShape = this.knownShape;
        structureplacesettings.finalizeEntities = this.finalizeEntities;
        return structureplacesettings;
    }

    public StructurePlaceSettings setMirror(Mirror mirror) {
        this.mirror = mirror;
        return this;
    }

    public StructurePlaceSettings setRotation(Rotation rotation) {
        this.rotation = rotation;
        return this;
    }

    public StructurePlaceSettings setRotationPivot(BlockPos rotationPivot) {
        this.rotationPivot = rotationPivot;
        return this;
    }

    public StructurePlaceSettings setIgnoreEntities(boolean ignoreEntities) {
        this.ignoreEntities = ignoreEntities;
        return this;
    }

    public StructurePlaceSettings setBoundingBox(BoundingBox boundingBox) {
        this.boundingBox = boundingBox;
        return this;
    }

    public StructurePlaceSettings setRandom(@Nullable RandomSource random) {
        this.random = random;
        return this;
    }

    public StructurePlaceSettings setLiquidSettings(LiquidSettings liquidSettings) {
        this.liquidSettings = liquidSettings;
        return this;
    }

    public StructurePlaceSettings setKnownShape(boolean knownShape) {
        this.knownShape = knownShape;
        return this;
    }

    public StructurePlaceSettings clearProcessors() {
        this.processors.clear();
        return this;
    }

    public StructurePlaceSettings addProcessor(StructureProcessor processor) {
        this.processors.add(processor);
        return this;
    }

    public StructurePlaceSettings popProcessor(StructureProcessor processor) {
        this.processors.remove(processor);
        return this;
    }

    public Mirror getMirror() {
        return this.mirror;
    }

    public Rotation getRotation() {
        return this.rotation;
    }

    public BlockPos getRotationPivot() {
        return this.rotationPivot;
    }

    public RandomSource getRandom(@Nullable BlockPos pos) {
        return this.random != null ? this.random : (pos == null ? RandomSource.create(Util.getMillis()) : RandomSource.create(Mth.getSeed(pos)));
    }

    public boolean isIgnoreEntities() {
        return this.ignoreEntities;
    }

    public @Nullable BoundingBox getBoundingBox() {
        return this.boundingBox;
    }

    public boolean getKnownShape() {
        return this.knownShape;
    }

    public List<StructureProcessor> getProcessors() {
        return this.processors;
    }

    public boolean shouldApplyWaterlogging() {
        return this.liquidSettings == LiquidSettings.APPLY_WATERLOGGING;
    }

    public StructureTemplate.Palette getRandomPalette(List<StructureTemplate.Palette> palettes, @Nullable BlockPos pos) {
        int i = palettes.size();

        if (i == 0) {
            throw new IllegalStateException("No palettes");
        } else {
            return (StructureTemplate.Palette) palettes.get(this.getRandom(pos).nextInt(i));
        }
    }

    public StructurePlaceSettings setFinalizeEntities(boolean finalizeEntities) {
        this.finalizeEntities = finalizeEntities;
        return this;
    }

    public boolean shouldFinalizeEntities() {
        return this.finalizeEntities;
    }
}
