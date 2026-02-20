package net.minecraft.world.level.chunk;

import net.minecraft.core.IdMap;
import net.minecraft.util.Mth;

public abstract class Strategy<T> {

    private static final Palette.Factory SINGLE_VALUE_PALETTE_FACTORY = SingleValuePalette::create;
    private static final Palette.Factory LINEAR_PALETTE_FACTORY = LinearPalette::create;
    private static final Palette.Factory HASHMAP_PALETTE_FACTORY = HashMapPalette::create;
    private static final Configuration ZERO_BITS = new Configuration.Simple(Strategy.SINGLE_VALUE_PALETTE_FACTORY, 0);
    private static final Configuration ONE_BIT_LINEAR = new Configuration.Simple(Strategy.LINEAR_PALETTE_FACTORY, 1);
    private static final Configuration TWO_BITS_LINEAR = new Configuration.Simple(Strategy.LINEAR_PALETTE_FACTORY, 2);
    private static final Configuration THREE_BITS_LINEAR = new Configuration.Simple(Strategy.LINEAR_PALETTE_FACTORY, 3);
    private static final Configuration FOUR_BITS_LINEAR = new Configuration.Simple(Strategy.LINEAR_PALETTE_FACTORY, 4);
    private static final Configuration FIVE_BITS_HASHMAP = new Configuration.Simple(Strategy.HASHMAP_PALETTE_FACTORY, 5);
    private static final Configuration SIX_BITS_HASHMAP = new Configuration.Simple(Strategy.HASHMAP_PALETTE_FACTORY, 6);
    private static final Configuration SEVEN_BITS_HASHMAP = new Configuration.Simple(Strategy.HASHMAP_PALETTE_FACTORY, 7);
    private static final Configuration EIGHT_BITS_HASHMAP = new Configuration.Simple(Strategy.HASHMAP_PALETTE_FACTORY, 8);
    private final IdMap<T> globalMap;
    private final GlobalPalette<T> globalPalette;
    protected final int globalPaletteBitsInMemory;
    private final int bitsPerAxis;
    private final int entryCount;

    private Strategy(IdMap<T> globalMap, int bitsPerAxis) {
        this.globalMap = globalMap;
        this.globalPalette = new GlobalPalette<T>(globalMap);
        this.globalPaletteBitsInMemory = minimumBitsRequiredForDistinctValues(globalMap.size());
        this.bitsPerAxis = bitsPerAxis;
        this.entryCount = 1 << bitsPerAxis * 3;
    }

    public static <T> Strategy<T> createForBlockStates(IdMap<T> registry) {
        return new Strategy<T>(registry, 4) {
            @Override
            public Configuration getConfigurationForBitCount(int entryBits) {
                Object object;

                switch (entryBits) {
                    case 0:
                        object = Strategy.ZERO_BITS;
                        break;
                    case 1:
                    case 2:
                    case 3:
                    case 4:
                        object = Strategy.FOUR_BITS_LINEAR;
                        break;
                    case 5:
                        object = Strategy.FIVE_BITS_HASHMAP;
                        break;
                    case 6:
                        object = Strategy.SIX_BITS_HASHMAP;
                        break;
                    case 7:
                        object = Strategy.SEVEN_BITS_HASHMAP;
                        break;
                    case 8:
                        object = Strategy.EIGHT_BITS_HASHMAP;
                        break;
                    default:
                        object = new Configuration.Global(this.globalPaletteBitsInMemory, entryBits);
                }

                return (Configuration) object;
            }
        };
    }

    public static <T> Strategy<T> createForBiomes(IdMap<T> registry) {
        return new Strategy<T>(registry, 2) {
            @Override
            public Configuration getConfigurationForBitCount(int entryBits) {
                Object object;

                switch (entryBits) {
                    case 0:
                        object = Strategy.ZERO_BITS;
                        break;
                    case 1:
                        object = Strategy.ONE_BIT_LINEAR;
                        break;
                    case 2:
                        object = Strategy.TWO_BITS_LINEAR;
                        break;
                    case 3:
                        object = Strategy.THREE_BITS_LINEAR;
                        break;
                    default:
                        object = new Configuration.Global(this.globalPaletteBitsInMemory, entryBits);
                }

                return (Configuration) object;
            }
        };
    }

    public int entryCount() {
        return this.entryCount;
    }

    public int getIndex(int x, int y, int z) {
        return (y << this.bitsPerAxis | z) << this.bitsPerAxis | x;
    }

    public IdMap<T> globalMap() {
        return this.globalMap;
    }

    public GlobalPalette<T> globalPalette() {
        return this.globalPalette;
    }

    protected abstract Configuration getConfigurationForBitCount(int entryBits);

    protected Configuration getConfigurationForPaletteSize(int paletteSize) {
        int j = minimumBitsRequiredForDistinctValues(paletteSize);

        return this.getConfigurationForBitCount(j);
    }

    private static int minimumBitsRequiredForDistinctValues(int count) {
        return Mth.ceillog2(count);
    }
}
