package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.apache.commons.lang3.function.TriFunction;

public record WeatheringCopperBlocks(Block unaffected, Block exposed, Block weathered, Block oxidized, Block waxed, Block waxedExposed, Block waxedWeathered, Block waxedOxidized) {

    public static <WaxedBlock extends Block, WeatheringBlock extends Block & WeatheringCopper> WeatheringCopperBlocks create(String id, TriFunction<String, Function<BlockBehaviour.Properties, Block>, BlockBehaviour.Properties, Block> register, Function<BlockBehaviour.Properties, WaxedBlock> waxedBlockFactory, BiFunction<WeatheringCopper.WeatherState, BlockBehaviour.Properties, WeatheringBlock> weatheringFactory, Function<WeatheringCopper.WeatherState, BlockBehaviour.Properties> propertiesSupplier) {
        Block block = (Block) register.apply(id, (Function) (blockbehaviour_properties) -> {
            return (Block) weatheringFactory.apply(WeatheringCopper.WeatherState.UNAFFECTED, blockbehaviour_properties);
        }, (BlockBehaviour.Properties) propertiesSupplier.apply(WeatheringCopper.WeatherState.UNAFFECTED));
        Block block1 = (Block) register.apply("exposed_" + id, (Function) (blockbehaviour_properties) -> {
            return (Block) weatheringFactory.apply(WeatheringCopper.WeatherState.EXPOSED, blockbehaviour_properties);
        }, (BlockBehaviour.Properties) propertiesSupplier.apply(WeatheringCopper.WeatherState.EXPOSED));
        Block block2 = (Block) register.apply("weathered_" + id, (Function) (blockbehaviour_properties) -> {
            return (Block) weatheringFactory.apply(WeatheringCopper.WeatherState.WEATHERED, blockbehaviour_properties);
        }, (BlockBehaviour.Properties) propertiesSupplier.apply(WeatheringCopper.WeatherState.WEATHERED));
        Block block3 = (Block) register.apply("oxidized_" + id, (Function) (blockbehaviour_properties) -> {
            return (Block) weatheringFactory.apply(WeatheringCopper.WeatherState.OXIDIZED, blockbehaviour_properties);
        }, (BlockBehaviour.Properties) propertiesSupplier.apply(WeatheringCopper.WeatherState.OXIDIZED));
        String s1 = "waxed_" + id;

        Objects.requireNonNull(waxedBlockFactory);
        Block block4 = (Block) register.apply(s1, waxedBlockFactory::apply, (BlockBehaviour.Properties) propertiesSupplier.apply(WeatheringCopper.WeatherState.UNAFFECTED));
        String s2 = "waxed_exposed_" + id;

        Objects.requireNonNull(waxedBlockFactory);
        Block block5 = (Block) register.apply(s2, waxedBlockFactory::apply, (BlockBehaviour.Properties) propertiesSupplier.apply(WeatheringCopper.WeatherState.EXPOSED));
        String s3 = "waxed_weathered_" + id;

        Objects.requireNonNull(waxedBlockFactory);
        Block block6 = (Block) register.apply(s3, waxedBlockFactory::apply, (BlockBehaviour.Properties) propertiesSupplier.apply(WeatheringCopper.WeatherState.WEATHERED));
        String s4 = "waxed_oxidized_" + id;

        Objects.requireNonNull(waxedBlockFactory);
        return new WeatheringCopperBlocks(block, block1, block2, block3, block4, block5, block6, (Block) register.apply(s4, waxedBlockFactory::apply, (BlockBehaviour.Properties) propertiesSupplier.apply(WeatheringCopper.WeatherState.OXIDIZED)));
    }

    public ImmutableBiMap<Block, Block> weatheringMapping() {
        return ImmutableBiMap.of(this.unaffected, this.exposed, this.exposed, this.weathered, this.weathered, this.oxidized);
    }

    public ImmutableBiMap<Block, Block> waxedMapping() {
        return ImmutableBiMap.of(this.unaffected, this.waxed, this.exposed, this.waxedExposed, this.weathered, this.waxedWeathered, this.oxidized, this.waxedOxidized);
    }

    public ImmutableList<Block> asList() {
        return ImmutableList.of(this.unaffected, this.waxed, this.exposed, this.waxedExposed, this.weathered, this.waxedWeathered, this.oxidized, this.waxedOxidized);
    }

    public void forEach(Consumer<Block> consumer) {
        consumer.accept(this.unaffected);
        consumer.accept(this.exposed);
        consumer.accept(this.weathered);
        consumer.accept(this.oxidized);
        consumer.accept(this.waxed);
        consumer.accept(this.waxedExposed);
        consumer.accept(this.waxedWeathered);
        consumer.accept(this.waxedOxidized);
    }
}
