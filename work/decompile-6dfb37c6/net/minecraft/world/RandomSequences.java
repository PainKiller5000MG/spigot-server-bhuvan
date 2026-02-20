package net.minecraft.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class RandomSequences extends SavedData {

    public static final Codec<RandomSequences> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(Codec.INT.fieldOf("salt").forGetter(RandomSequences::salt), Codec.BOOL.optionalFieldOf("include_world_seed", true).forGetter(RandomSequences::includeWorldSeed), Codec.BOOL.optionalFieldOf("include_sequence_id", true).forGetter(RandomSequences::includeSequenceId), Codec.unboundedMap(Identifier.CODEC, RandomSequence.CODEC).fieldOf("sequences").forGetter((randomsequences) -> {
            return randomsequences.sequences;
        })).apply(instance, RandomSequences::new);
    });
    public static final SavedDataType<RandomSequences> TYPE = new SavedDataType<RandomSequences>("random_sequences", RandomSequences::new, RandomSequences.CODEC, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);
    private int salt;
    private boolean includeWorldSeed = true;
    private boolean includeSequenceId = true;
    private final Map<Identifier, RandomSequence> sequences = new Object2ObjectOpenHashMap();

    public RandomSequences() {}

    private RandomSequences(int salt, boolean includeWorldSeed, boolean includeSequenceId, Map<Identifier, RandomSequence> sequences) {
        this.salt = salt;
        this.includeWorldSeed = includeWorldSeed;
        this.includeSequenceId = includeSequenceId;
        this.sequences.putAll(sequences);
    }

    public RandomSource get(Identifier key, long worldSeed) {
        RandomSource randomsource = ((RandomSequence) this.sequences.computeIfAbsent(key, (identifier1) -> {
            return this.createSequence(identifier1, worldSeed);
        })).random();

        return new RandomSequences.DirtyMarkingRandomSource(randomsource);
    }

    private RandomSequence createSequence(Identifier key, long worldSeed) {
        return this.createSequence(key, worldSeed, this.salt, this.includeWorldSeed, this.includeSequenceId);
    }

    private RandomSequence createSequence(Identifier key, long worldSeed, int salt, boolean includeWorldSeed, boolean includeSequenceId) {
        long k = (includeWorldSeed ? worldSeed : 0L) ^ (long) salt;

        return new RandomSequence(k, includeSequenceId ? Optional.of(key) : Optional.empty());
    }

    public void forAllSequences(BiConsumer<Identifier, RandomSequence> consumer) {
        this.sequences.forEach(consumer);
    }

    public void setSeedDefaults(int salt, boolean includeWorldSeed, boolean includeSequenceId) {
        this.salt = salt;
        this.includeWorldSeed = includeWorldSeed;
        this.includeSequenceId = includeSequenceId;
    }

    public int clear() {
        int i = this.sequences.size();

        this.sequences.clear();
        return i;
    }

    public void reset(Identifier id, long worldSeed) {
        this.sequences.put(id, this.createSequence(id, worldSeed));
    }

    public void reset(Identifier id, long worldSeed, int salt, boolean includeWorldSeed, boolean includeSequenceId) {
        this.sequences.put(id, this.createSequence(id, worldSeed, salt, includeWorldSeed, includeSequenceId));
    }

    private int salt() {
        return this.salt;
    }

    private boolean includeWorldSeed() {
        return this.includeWorldSeed;
    }

    private boolean includeSequenceId() {
        return this.includeSequenceId;
    }

    private class DirtyMarkingRandomSource implements RandomSource {

        private final RandomSource random;

        private DirtyMarkingRandomSource(RandomSource random) {
            this.random = random;
        }

        @Override
        public RandomSource fork() {
            RandomSequences.this.setDirty();
            return this.random.fork();
        }

        @Override
        public PositionalRandomFactory forkPositional() {
            RandomSequences.this.setDirty();
            return this.random.forkPositional();
        }

        @Override
        public void setSeed(long seed) {
            RandomSequences.this.setDirty();
            this.random.setSeed(seed);
        }

        @Override
        public int nextInt() {
            RandomSequences.this.setDirty();
            return this.random.nextInt();
        }

        @Override
        public int nextInt(int bound) {
            RandomSequences.this.setDirty();
            return this.random.nextInt(bound);
        }

        @Override
        public long nextLong() {
            RandomSequences.this.setDirty();
            return this.random.nextLong();
        }

        @Override
        public boolean nextBoolean() {
            RandomSequences.this.setDirty();
            return this.random.nextBoolean();
        }

        @Override
        public float nextFloat() {
            RandomSequences.this.setDirty();
            return this.random.nextFloat();
        }

        @Override
        public double nextDouble() {
            RandomSequences.this.setDirty();
            return this.random.nextDouble();
        }

        @Override
        public double nextGaussian() {
            RandomSequences.this.setDirty();
            return this.random.nextGaussian();
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj instanceof RandomSequences.DirtyMarkingRandomSource) {
                RandomSequences.DirtyMarkingRandomSource randomsequences_dirtymarkingrandomsource = (RandomSequences.DirtyMarkingRandomSource) obj;

                return this.random.equals(randomsequences_dirtymarkingrandomsource.random);
            } else {
                return false;
            }
        }
    }
}
