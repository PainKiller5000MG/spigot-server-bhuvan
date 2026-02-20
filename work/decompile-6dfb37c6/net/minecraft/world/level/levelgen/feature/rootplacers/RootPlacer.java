package net.minecraft.world.level.levelgen.feature.rootplacers;

import com.mojang.datafixers.Products;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public abstract class RootPlacer {

    public static final Codec<RootPlacer> CODEC = BuiltInRegistries.ROOT_PLACER_TYPE.byNameCodec().dispatch(RootPlacer::type, RootPlacerType::codec);
    protected final IntProvider trunkOffsetY;
    protected final BlockStateProvider rootProvider;
    protected final Optional<AboveRootPlacement> aboveRootPlacement;

    protected static <P extends RootPlacer> Products.P3<RecordCodecBuilder.Mu<P>, IntProvider, BlockStateProvider, Optional<AboveRootPlacement>> rootPlacerParts(RecordCodecBuilder.Instance<P> instance) {
        return instance.group(IntProvider.CODEC.fieldOf("trunk_offset_y").forGetter((rootplacer) -> {
            return rootplacer.trunkOffsetY;
        }), BlockStateProvider.CODEC.fieldOf("root_provider").forGetter((rootplacer) -> {
            return rootplacer.rootProvider;
        }), AboveRootPlacement.CODEC.optionalFieldOf("above_root_placement").forGetter((rootplacer) -> {
            return rootplacer.aboveRootPlacement;
        }));
    }

    public RootPlacer(IntProvider trunkOffsetY, BlockStateProvider rootProvider, Optional<AboveRootPlacement> aboveRootPlacement) {
        this.trunkOffsetY = trunkOffsetY;
        this.rootProvider = rootProvider;
        this.aboveRootPlacement = aboveRootPlacement;
    }

    protected abstract RootPlacerType<?> type();

    public abstract boolean placeRoots(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> rootSetter, RandomSource random, BlockPos origin, BlockPos trunkOrigin, TreeConfiguration config);

    protected boolean canPlaceRoot(LevelSimulatedReader level, BlockPos pos) {
        return TreeFeature.validTreePos(level, pos);
    }

    protected void placeRoot(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> rootSetter, RandomSource random, BlockPos pos, TreeConfiguration config) {
        if (this.canPlaceRoot(level, pos)) {
            rootSetter.accept(pos, this.getPotentiallyWaterloggedState(level, pos, this.rootProvider.getState(random, pos)));
            if (this.aboveRootPlacement.isPresent()) {
                AboveRootPlacement aboverootplacement = (AboveRootPlacement) this.aboveRootPlacement.get();
                BlockPos blockpos1 = pos.above();

                if (random.nextFloat() < aboverootplacement.aboveRootPlacementChance() && level.isStateAtPosition(blockpos1, BlockBehaviour.BlockStateBase::isAir)) {
                    rootSetter.accept(blockpos1, this.getPotentiallyWaterloggedState(level, blockpos1, aboverootplacement.aboveRootProvider().getState(random, blockpos1)));
                }
            }

        }
    }

    protected BlockState getPotentiallyWaterloggedState(LevelSimulatedReader level, BlockPos pos, BlockState state) {
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            boolean flag = level.isFluidAtPosition(pos, (fluidstate) -> {
                return fluidstate.is(FluidTags.WATER);
            });

            return (BlockState) state.setValue(BlockStateProperties.WATERLOGGED, flag);
        } else {
            return state;
        }
    }

    public BlockPos getTrunkOrigin(BlockPos origin, RandomSource random) {
        return origin.above(this.trunkOffsetY.sample(random));
    }
}
