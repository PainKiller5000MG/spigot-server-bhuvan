package net.minecraft.world.level.chunk;

import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class LevelChunkSection {

    public static final int SECTION_WIDTH = 16;
    public static final int SECTION_HEIGHT = 16;
    public static final int SECTION_SIZE = 4096;
    public static final int BIOME_CONTAINER_BITS = 2;
    private short nonEmptyBlockCount;
    private short tickingBlockCount;
    private short tickingFluidCount;
    private final PalettedContainer<BlockState> states;
    private PalettedContainerRO<Holder<Biome>> biomes;

    private LevelChunkSection(LevelChunkSection source) {
        this.nonEmptyBlockCount = source.nonEmptyBlockCount;
        this.tickingBlockCount = source.tickingBlockCount;
        this.tickingFluidCount = source.tickingFluidCount;
        this.states = source.states.copy();
        this.biomes = source.biomes.copy();
    }

    public LevelChunkSection(PalettedContainer<BlockState> states, PalettedContainerRO<Holder<Biome>> biomes) {
        this.states = states;
        this.biomes = biomes;
        this.recalcBlockCounts();
    }

    public LevelChunkSection(PalettedContainerFactory containerFactory) {
        this.states = containerFactory.createForBlockStates();
        this.biomes = containerFactory.createForBiomes();
    }

    public BlockState getBlockState(int sectionX, int sectionY, int sectionZ) {
        return (BlockState) this.states.get(sectionX, sectionY, sectionZ);
    }

    public FluidState getFluidState(int sectionX, int sectionY, int sectionZ) {
        return ((BlockState) this.states.get(sectionX, sectionY, sectionZ)).getFluidState();
    }

    public void acquire() {
        this.states.acquire();
    }

    public void release() {
        this.states.release();
    }

    public BlockState setBlockState(int sectionX, int sectionY, int sectionZ, BlockState state) {
        return this.setBlockState(sectionX, sectionY, sectionZ, state, true);
    }

    public BlockState setBlockState(int sectionX, int sectionY, int sectionZ, BlockState state, boolean checkThreading) {
        BlockState blockstate1;

        if (checkThreading) {
            blockstate1 = this.states.getAndSet(sectionX, sectionY, sectionZ, state);
        } else {
            blockstate1 = this.states.getAndSetUnchecked(sectionX, sectionY, sectionZ, state);
        }

        FluidState fluidstate = blockstate1.getFluidState();
        FluidState fluidstate1 = state.getFluidState();

        if (!blockstate1.isAir()) {
            --this.nonEmptyBlockCount;
            if (blockstate1.isRandomlyTicking()) {
                --this.tickingBlockCount;
            }
        }

        if (!fluidstate.isEmpty()) {
            --this.tickingFluidCount;
        }

        if (!state.isAir()) {
            ++this.nonEmptyBlockCount;
            if (state.isRandomlyTicking()) {
                ++this.tickingBlockCount;
            }
        }

        if (!fluidstate1.isEmpty()) {
            ++this.tickingFluidCount;
        }

        return blockstate1;
    }

    public boolean hasOnlyAir() {
        return this.nonEmptyBlockCount == 0;
    }

    public boolean isRandomlyTicking() {
        return this.isRandomlyTickingBlocks() || this.isRandomlyTickingFluids();
    }

    public boolean isRandomlyTickingBlocks() {
        return this.tickingBlockCount > 0;
    }

    public boolean isRandomlyTickingFluids() {
        return this.tickingFluidCount > 0;
    }

    public void recalcBlockCounts() {
        class 1BlockCounter implements PalettedContainer.CountConsumer<BlockState> {

            public int nonEmptyBlockCount;
            public int tickingBlockCount;
            public int tickingFluidCount;

            _BlockCounter/* $FF was: 1BlockCounter*/() {
}

            public void accept(BlockState state, int count) {
                FluidState fluidstate = state.getFluidState();

                if (!state.isAir()) {
                    this.nonEmptyBlockCount += count;
                    if (state.isRandomlyTicking()) {
                        this.tickingBlockCount += count;
                    }
                }

                if (!fluidstate.isEmpty()) {
                    this.nonEmptyBlockCount += count;
                    if (fluidstate.isRandomlyTicking()) {
                        this.tickingFluidCount += count;
                    }
                }

            }
        }

        1BlockCounter 1blockcounter = new 1BlockCounter();

        this.states.count(1blockcounter);
        this.nonEmptyBlockCount = (short)1blockcounter.nonEmptyBlockCount;
        this.tickingBlockCount = (short)1blockcounter.tickingBlockCount;
        this.tickingFluidCount = (short)1blockcounter.tickingFluidCount;
    }

    public PalettedContainer<BlockState> getStates() {
        return this.states;
    }

    public PalettedContainerRO<Holder<Biome>> getBiomes() {
        return this.biomes;
    }

    public void read(FriendlyByteBuf buffer) {
        this.nonEmptyBlockCount = buffer.readShort();
        this.states.read(buffer);
        PalettedContainer<Holder<Biome>> palettedcontainer = this.biomes.recreate();

        palettedcontainer.read(buffer);
        this.biomes = palettedcontainer;
    }

    public void readBiomes(FriendlyByteBuf buffer) {
        PalettedContainer<Holder<Biome>> palettedcontainer = this.biomes.recreate();

        palettedcontainer.read(buffer);
        this.biomes = palettedcontainer;
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeShort(this.nonEmptyBlockCount);
        this.states.write(buffer);
        this.biomes.write(buffer);
    }

    public int getSerializedSize() {
        return 2 + this.states.getSerializedSize() + this.biomes.getSerializedSize();
    }

    public boolean maybeHas(Predicate<BlockState> predicate) {
        return this.states.maybeHas(predicate);
    }

    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ) {
        return this.biomes.get(quartX, quartY, quartZ);
    }

    public void fillBiomesFromNoise(BiomeResolver biomeResolver, Climate.Sampler sampler, int quartMinX, int quartMinY, int quartMinZ) {
        PalettedContainer<Holder<Biome>> palettedcontainer = this.biomes.recreate();
        int l = 4;

        for (int i1 = 0; i1 < 4; ++i1) {
            for (int j1 = 0; j1 < 4; ++j1) {
                for (int k1 = 0; k1 < 4; ++k1) {
                    palettedcontainer.getAndSetUnchecked(i1, j1, k1, biomeResolver.getNoiseBiome(quartMinX + i1, quartMinY + j1, quartMinZ + k1, sampler));
                }
            }
        }

        this.biomes = palettedcontainer;
    }

    public LevelChunkSection copy() {
        return new LevelChunkSection(this);
    }
}
