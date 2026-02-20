package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.ServerLevelAccessor;

public class CappedProcessor extends StructureProcessor {

    public static final MapCodec<CappedProcessor> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(StructureProcessorType.SINGLE_CODEC.fieldOf("delegate").forGetter((cappedprocessor) -> {
            return cappedprocessor.delegate;
        }), IntProvider.POSITIVE_CODEC.fieldOf("limit").forGetter((cappedprocessor) -> {
            return cappedprocessor.limit;
        })).apply(instance, CappedProcessor::new);
    });
    private final StructureProcessor delegate;
    private final IntProvider limit;

    public CappedProcessor(StructureProcessor delegate, IntProvider limit) {
        this.delegate = delegate;
        this.limit = limit;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return StructureProcessorType.CAPPED;
    }

    @Override
    public final List<StructureTemplate.StructureBlockInfo> finalizeProcessing(ServerLevelAccessor level, BlockPos position, BlockPos referencePos, List<StructureTemplate.StructureBlockInfo> originalBlockInfoList, List<StructureTemplate.StructureBlockInfo> processedBlockInfoList, StructurePlaceSettings settings) {
        if (this.limit.getMaxValue() != 0 && !processedBlockInfoList.isEmpty()) {
            if (originalBlockInfoList.size() != processedBlockInfoList.size()) {
                int i = originalBlockInfoList.size();

                Util.logAndPauseIfInIde("Original block info list not in sync with processed list, skipping processing. Original size: " + i + ", Processed size: " + processedBlockInfoList.size());
                return processedBlockInfoList;
            } else {
                RandomSource randomsource = RandomSource.create(level.getLevel().getSeed()).forkPositional().at(position);
                int j = Math.min(this.limit.sample(randomsource), processedBlockInfoList.size());

                if (j < 1) {
                    return processedBlockInfoList;
                } else {
                    IntArrayList intarraylist = Util.toShuffledList(IntStream.range(0, processedBlockInfoList.size()), randomsource);
                    IntIterator intiterator = intarraylist.intIterator();
                    int k = 0;

                    while (intiterator.hasNext() && k < j) {
                        int l = intiterator.nextInt();
                        StructureTemplate.StructureBlockInfo structuretemplate_structureblockinfo = (StructureTemplate.StructureBlockInfo) originalBlockInfoList.get(l);
                        StructureTemplate.StructureBlockInfo structuretemplate_structureblockinfo1 = (StructureTemplate.StructureBlockInfo) processedBlockInfoList.get(l);
                        StructureTemplate.StructureBlockInfo structuretemplate_structureblockinfo2 = this.delegate.processBlock(level, position, referencePos, structuretemplate_structureblockinfo, structuretemplate_structureblockinfo1, settings);

                        if (structuretemplate_structureblockinfo2 != null && !structuretemplate_structureblockinfo1.equals(structuretemplate_structureblockinfo2)) {
                            ++k;
                            processedBlockInfoList.set(l, structuretemplate_structureblockinfo2);
                        }
                    }

                    return processedBlockInfoList;
                }
            }
        } else {
            return processedBlockInfoList;
        }
    }
}
