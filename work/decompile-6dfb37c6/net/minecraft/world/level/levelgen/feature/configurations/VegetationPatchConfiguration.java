package net.minecraft.world.level.levelgen.feature.configurations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class VegetationPatchConfiguration implements FeatureConfiguration {

    public static final Codec<VegetationPatchConfiguration> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(TagKey.hashedCodec(Registries.BLOCK).fieldOf("replaceable").forGetter((vegetationpatchconfiguration) -> {
            return vegetationpatchconfiguration.replaceable;
        }), BlockStateProvider.CODEC.fieldOf("ground_state").forGetter((vegetationpatchconfiguration) -> {
            return vegetationpatchconfiguration.groundState;
        }), PlacedFeature.CODEC.fieldOf("vegetation_feature").forGetter((vegetationpatchconfiguration) -> {
            return vegetationpatchconfiguration.vegetationFeature;
        }), CaveSurface.CODEC.fieldOf("surface").forGetter((vegetationpatchconfiguration) -> {
            return vegetationpatchconfiguration.surface;
        }), IntProvider.codec(1, 128).fieldOf("depth").forGetter((vegetationpatchconfiguration) -> {
            return vegetationpatchconfiguration.depth;
        }), Codec.floatRange(0.0F, 1.0F).fieldOf("extra_bottom_block_chance").forGetter((vegetationpatchconfiguration) -> {
            return vegetationpatchconfiguration.extraBottomBlockChance;
        }), Codec.intRange(1, 256).fieldOf("vertical_range").forGetter((vegetationpatchconfiguration) -> {
            return vegetationpatchconfiguration.verticalRange;
        }), Codec.floatRange(0.0F, 1.0F).fieldOf("vegetation_chance").forGetter((vegetationpatchconfiguration) -> {
            return vegetationpatchconfiguration.vegetationChance;
        }), IntProvider.CODEC.fieldOf("xz_radius").forGetter((vegetationpatchconfiguration) -> {
            return vegetationpatchconfiguration.xzRadius;
        }), Codec.floatRange(0.0F, 1.0F).fieldOf("extra_edge_column_chance").forGetter((vegetationpatchconfiguration) -> {
            return vegetationpatchconfiguration.extraEdgeColumnChance;
        })).apply(instance, VegetationPatchConfiguration::new);
    });
    public final TagKey<Block> replaceable;
    public final BlockStateProvider groundState;
    public final Holder<PlacedFeature> vegetationFeature;
    public final CaveSurface surface;
    public final IntProvider depth;
    public final float extraBottomBlockChance;
    public final int verticalRange;
    public final float vegetationChance;
    public final IntProvider xzRadius;
    public final float extraEdgeColumnChance;

    public VegetationPatchConfiguration(TagKey<Block> replaceable, BlockStateProvider groundState, Holder<PlacedFeature> vegetationFeature, CaveSurface surface, IntProvider depth, float extraBottomBlockChance, int verticalRange, float vegetationChance, IntProvider xzRadius, float extraEdgeColumnChance) {
        this.replaceable = replaceable;
        this.groundState = groundState;
        this.vegetationFeature = vegetationFeature;
        this.surface = surface;
        this.depth = depth;
        this.extraBottomBlockChance = extraBottomBlockChance;
        this.verticalRange = verticalRange;
        this.vegetationChance = vegetationChance;
        this.xzRadius = xzRadius;
        this.extraEdgeColumnChance = extraEdgeColumnChance;
    }
}
