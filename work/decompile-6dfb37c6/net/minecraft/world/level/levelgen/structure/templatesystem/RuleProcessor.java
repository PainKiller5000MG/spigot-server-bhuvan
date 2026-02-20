package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class RuleProcessor extends StructureProcessor {

    public static final MapCodec<RuleProcessor> CODEC = ProcessorRule.CODEC.listOf().fieldOf("rules").xmap(RuleProcessor::new, (ruleprocessor) -> {
        return ruleprocessor.rules;
    });
    private final ImmutableList<ProcessorRule> rules;

    public RuleProcessor(List<? extends ProcessorRule> rules) {
        this.rules = ImmutableList.copyOf(rules);
    }

    @Override
    public StructureTemplate.@Nullable StructureBlockInfo processBlock(LevelReader level, BlockPos targetPosition, BlockPos referencePos, StructureTemplate.StructureBlockInfo originalBlockInfo, StructureTemplate.StructureBlockInfo processedBlockInfo, StructurePlaceSettings settings) {
        RandomSource randomsource = RandomSource.create(Mth.getSeed(processedBlockInfo.pos()));
        BlockState blockstate = level.getBlockState(processedBlockInfo.pos());
        UnmodifiableIterator unmodifiableiterator = this.rules.iterator();

        while (unmodifiableiterator.hasNext()) {
            ProcessorRule processorrule = (ProcessorRule) unmodifiableiterator.next();

            if (processorrule.test(processedBlockInfo.state(), blockstate, originalBlockInfo.pos(), processedBlockInfo.pos(), referencePos, randomsource)) {
                return new StructureTemplate.StructureBlockInfo(processedBlockInfo.pos(), processorrule.getOutputState(), processorrule.getOutputTag(randomsource, processedBlockInfo.nbt()));
            }
        }

        return processedBlockInfo;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return StructureProcessorType.RULE;
    }
}
