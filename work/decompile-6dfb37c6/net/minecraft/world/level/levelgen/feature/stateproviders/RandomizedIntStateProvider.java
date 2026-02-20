package net.minecraft.world.level.levelgen.feature.stateproviders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

public class RandomizedIntStateProvider extends BlockStateProvider {

    public static final MapCodec<RandomizedIntStateProvider> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(BlockStateProvider.CODEC.fieldOf("source").forGetter((randomizedintstateprovider) -> {
            return randomizedintstateprovider.source;
        }), Codec.STRING.fieldOf("property").forGetter((randomizedintstateprovider) -> {
            return randomizedintstateprovider.propertyName;
        }), IntProvider.CODEC.fieldOf("values").forGetter((randomizedintstateprovider) -> {
            return randomizedintstateprovider.values;
        })).apply(instance, RandomizedIntStateProvider::new);
    });
    private final BlockStateProvider source;
    private final String propertyName;
    private @Nullable IntegerProperty property;
    private final IntProvider values;

    public RandomizedIntStateProvider(BlockStateProvider source, IntegerProperty property, IntProvider values) {
        this.source = source;
        this.property = property;
        this.propertyName = property.getName();
        this.values = values;
        Collection<Integer> collection = property.getPossibleValues();

        for (int i = values.getMinValue(); i <= values.getMaxValue(); ++i) {
            if (!collection.contains(i)) {
                String s = property.getName();

                throw new IllegalArgumentException("Property value out of range: " + s + ": " + i);
            }
        }

    }

    public RandomizedIntStateProvider(BlockStateProvider source, String propertyName, IntProvider values) {
        this.source = source;
        this.propertyName = propertyName;
        this.values = values;
    }

    @Override
    protected BlockStateProviderType<?> type() {
        return BlockStateProviderType.RANDOMIZED_INT_STATE_PROVIDER;
    }

    @Override
    public BlockState getState(RandomSource random, BlockPos pos) {
        BlockState blockstate = this.source.getState(random, pos);

        if (this.property == null || !blockstate.hasProperty(this.property)) {
            IntegerProperty integerproperty = findProperty(blockstate, this.propertyName);

            if (integerproperty == null) {
                return blockstate;
            }

            this.property = integerproperty;
        }

        return (BlockState) blockstate.setValue(this.property, this.values.sample(random));
    }

    private static @Nullable IntegerProperty findProperty(BlockState source, String propertyName) {
        Collection<Property<?>> collection = source.getProperties();
        Optional<IntegerProperty> optional = collection.stream().filter((property) -> {
            return property.getName().equals(propertyName);
        }).filter((property) -> {
            return property instanceof IntegerProperty;
        }).map((property) -> {
            return (IntegerProperty) property;
        }).findAny();

        return (IntegerProperty) optional.orElse((Object) null);
    }
}
