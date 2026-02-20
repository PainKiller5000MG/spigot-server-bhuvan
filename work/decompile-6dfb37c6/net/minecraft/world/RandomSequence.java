package net.minecraft.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

public class RandomSequence {

    public static final Codec<RandomSequence> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(XoroshiroRandomSource.CODEC.fieldOf("source").forGetter((randomsequence) -> {
            return randomsequence.source;
        })).apply(instance, RandomSequence::new);
    });
    private final XoroshiroRandomSource source;

    public RandomSequence(XoroshiroRandomSource source) {
        this.source = source;
    }

    public RandomSequence(long seed, Identifier key) {
        this(createSequence(seed, Optional.of(key)));
    }

    public RandomSequence(long seed, Optional<Identifier> key) {
        this(createSequence(seed, key));
    }

    private static XoroshiroRandomSource createSequence(long seed, Optional<Identifier> key) {
        RandomSupport.Seed128bit randomsupport_seed128bit = RandomSupport.upgradeSeedTo128bitUnmixed(seed);

        if (key.isPresent()) {
            randomsupport_seed128bit = randomsupport_seed128bit.xor(seedForKey((Identifier) key.get()));
        }

        return new XoroshiroRandomSource(randomsupport_seed128bit.mixed());
    }

    public static RandomSupport.Seed128bit seedForKey(Identifier key) {
        return RandomSupport.seedFromHashOf(key.toString());
    }

    public RandomSource random() {
        return this.source;
    }
}
