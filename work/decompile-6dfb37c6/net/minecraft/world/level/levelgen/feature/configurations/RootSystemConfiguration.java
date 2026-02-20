package net.minecraft.world.level.levelgen.feature.configurations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class RootSystemConfiguration implements FeatureConfiguration {

    public static final Codec<RootSystemConfiguration> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(PlacedFeature.CODEC.fieldOf("feature").forGetter((rootsystemconfiguration) -> {
            return rootsystemconfiguration.treeFeature;
        }), Codec.intRange(1, 64).fieldOf("required_vertical_space_for_tree").forGetter((rootsystemconfiguration) -> {
            return rootsystemconfiguration.requiredVerticalSpaceForTree;
        }), Codec.intRange(1, 64).fieldOf("root_radius").forGetter((rootsystemconfiguration) -> {
            return rootsystemconfiguration.rootRadius;
        }), TagKey.hashedCodec(Registries.BLOCK).fieldOf("root_replaceable").forGetter((rootsystemconfiguration) -> {
            return rootsystemconfiguration.rootReplaceable;
        }), BlockStateProvider.CODEC.fieldOf("root_state_provider").forGetter((rootsystemconfiguration) -> {
            return rootsystemconfiguration.rootStateProvider;
        }), Codec.intRange(1, 256).fieldOf("root_placement_attempts").forGetter((rootsystemconfiguration) -> {
            return rootsystemconfiguration.rootPlacementAttempts;
        }), Codec.intRange(1, 4096).fieldOf("root_column_max_height").forGetter((rootsystemconfiguration) -> {
            return rootsystemconfiguration.rootColumnMaxHeight;
        }), Codec.intRange(1, 64).fieldOf("hanging_root_radius").forGetter((rootsystemconfiguration) -> {
            return rootsystemconfiguration.hangingRootRadius;
        }), Codec.intRange(1, 16).fieldOf("hanging_roots_vertical_span").forGetter((rootsystemconfiguration) -> {
            return rootsystemconfiguration.hangingRootsVerticalSpan;
        }), BlockStateProvider.CODEC.fieldOf("hanging_root_state_provider").forGetter((rootsystemconfiguration) -> {
            return rootsystemconfiguration.hangingRootStateProvider;
        }), Codec.intRange(1, 256).fieldOf("hanging_root_placement_attempts").forGetter((rootsystemconfiguration) -> {
            return rootsystemconfiguration.hangingRootPlacementAttempts;
        }), Codec.intRange(1, 64).fieldOf("allowed_vertical_water_for_tree").forGetter((rootsystemconfiguration) -> {
            return rootsystemconfiguration.allowedVerticalWaterForTree;
        }), BlockPredicate.CODEC.fieldOf("allowed_tree_position").forGetter((rootsystemconfiguration) -> {
            return rootsystemconfiguration.allowedTreePosition;
        })).apply(instance, RootSystemConfiguration::new);
    });
    public final Holder<PlacedFeature> treeFeature;
    public final int requiredVerticalSpaceForTree;
    public final int rootRadius;
    public final TagKey<Block> rootReplaceable;
    public final BlockStateProvider rootStateProvider;
    public final int rootPlacementAttempts;
    public final int rootColumnMaxHeight;
    public final int hangingRootRadius;
    public final int hangingRootsVerticalSpan;
    public final BlockStateProvider hangingRootStateProvider;
    public final int hangingRootPlacementAttempts;
    public final int allowedVerticalWaterForTree;
    public final BlockPredicate allowedTreePosition;

    public RootSystemConfiguration(Holder<PlacedFeature> treeFeature, int requiredVerticalSpaceForTree, int rootRadius, TagKey<Block> rootReplaceable, BlockStateProvider rootStateProvider, int rootPlacementAttempts, int rootColumnMaxHeight, int hangingRootRadius, int hangingRootsVerticalSpan, BlockStateProvider hangingRootStateProvider, int hangingRootPlacementAttempts, int allowedVerticalWaterForTree, BlockPredicate allowedTreePosition) {
        this.treeFeature = treeFeature;
        this.requiredVerticalSpaceForTree = requiredVerticalSpaceForTree;
        this.rootRadius = rootRadius;
        this.rootReplaceable = rootReplaceable;
        this.rootStateProvider = rootStateProvider;
        this.rootPlacementAttempts = rootPlacementAttempts;
        this.rootColumnMaxHeight = rootColumnMaxHeight;
        this.hangingRootRadius = hangingRootRadius;
        this.hangingRootsVerticalSpan = hangingRootsVerticalSpan;
        this.hangingRootStateProvider = hangingRootStateProvider;
        this.hangingRootPlacementAttempts = hangingRootPlacementAttempts;
        this.allowedVerticalWaterForTree = allowedVerticalWaterForTree;
        this.allowedTreePosition = allowedTreePosition;
    }
}
