package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class JigsawReplacementProcessor extends StructureProcessor {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<JigsawReplacementProcessor> CODEC = MapCodec.unit(() -> {
        return JigsawReplacementProcessor.INSTANCE;
    });
    public static final JigsawReplacementProcessor INSTANCE = new JigsawReplacementProcessor();

    private JigsawReplacementProcessor() {}

    @Override
    public StructureTemplate.@Nullable StructureBlockInfo processBlock(LevelReader level, BlockPos targetPosition, BlockPos referencePos, StructureTemplate.StructureBlockInfo originalBlockInfo, StructureTemplate.StructureBlockInfo processedBlockInfo, StructurePlaceSettings settings) {
        BlockState blockstate = processedBlockInfo.state();

        if (blockstate.is(Blocks.JIGSAW) && !SharedConstants.DEBUG_KEEP_JIGSAW_BLOCKS_DURING_STRUCTURE_GEN) {
            if (processedBlockInfo.nbt() == null) {
                JigsawReplacementProcessor.LOGGER.warn("Jigsaw block at {} is missing nbt, will not replace", targetPosition);
                return processedBlockInfo;
            } else {
                String s = processedBlockInfo.nbt().getStringOr("final_state", "minecraft:air");

                BlockState blockstate1;

                try {
                    BlockStateParser.BlockResult blockstateparser_blockresult = BlockStateParser.parseForBlock(level.holderLookup(Registries.BLOCK), s, true);

                    blockstate1 = blockstateparser_blockresult.blockState();
                } catch (CommandSyntaxException commandsyntaxexception) {
                    JigsawReplacementProcessor.LOGGER.error("Failed to parse jigsaw replacement state '{}' at {}: {}", new Object[]{s, targetPosition, commandsyntaxexception.getMessage()});
                    return null;
                }

                return blockstate1.is(Blocks.STRUCTURE_VOID) ? null : new StructureTemplate.StructureBlockInfo(processedBlockInfo.pos(), blockstate1, (CompoundTag) null);
            }
        } else {
            return processedBlockInfo;
        }
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return StructureProcessorType.JIGSAW_REPLACEMENT;
    }
}
