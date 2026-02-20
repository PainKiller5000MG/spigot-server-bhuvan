package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;

public class FossilFeatureConfiguration implements FeatureConfiguration {

    public static final Codec<FossilFeatureConfiguration> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(Identifier.CODEC.listOf().fieldOf("fossil_structures").forGetter((fossilfeatureconfiguration) -> {
            return fossilfeatureconfiguration.fossilStructures;
        }), Identifier.CODEC.listOf().fieldOf("overlay_structures").forGetter((fossilfeatureconfiguration) -> {
            return fossilfeatureconfiguration.overlayStructures;
        }), StructureProcessorType.LIST_CODEC.fieldOf("fossil_processors").forGetter((fossilfeatureconfiguration) -> {
            return fossilfeatureconfiguration.fossilProcessors;
        }), StructureProcessorType.LIST_CODEC.fieldOf("overlay_processors").forGetter((fossilfeatureconfiguration) -> {
            return fossilfeatureconfiguration.overlayProcessors;
        }), Codec.intRange(0, 7).fieldOf("max_empty_corners_allowed").forGetter((fossilfeatureconfiguration) -> {
            return fossilfeatureconfiguration.maxEmptyCornersAllowed;
        })).apply(instance, FossilFeatureConfiguration::new);
    });
    public final List<Identifier> fossilStructures;
    public final List<Identifier> overlayStructures;
    public final Holder<StructureProcessorList> fossilProcessors;
    public final Holder<StructureProcessorList> overlayProcessors;
    public final int maxEmptyCornersAllowed;

    public FossilFeatureConfiguration(List<Identifier> fossilStructures, List<Identifier> overlayStructures, Holder<StructureProcessorList> fossilProcessors, Holder<StructureProcessorList> overlayProcessors, int maxEmptyCornersAllowed) {
        if (fossilStructures.isEmpty()) {
            throw new IllegalArgumentException("Fossil structure lists need at least one entry");
        } else if (fossilStructures.size() != overlayStructures.size()) {
            throw new IllegalArgumentException("Fossil structure lists must be equal lengths");
        } else {
            this.fossilStructures = fossilStructures;
            this.overlayStructures = overlayStructures;
            this.fossilProcessors = fossilProcessors;
            this.overlayProcessors = overlayProcessors;
            this.maxEmptyCornersAllowed = maxEmptyCornersAllowed;
        }
    }
}
