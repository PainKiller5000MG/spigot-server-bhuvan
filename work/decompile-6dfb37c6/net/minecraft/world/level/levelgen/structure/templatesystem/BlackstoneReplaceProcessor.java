package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

public class BlackstoneReplaceProcessor extends StructureProcessor {

    public static final MapCodec<BlackstoneReplaceProcessor> CODEC = MapCodec.unit(() -> {
        return BlackstoneReplaceProcessor.INSTANCE;
    });
    public static final BlackstoneReplaceProcessor INSTANCE = new BlackstoneReplaceProcessor();
    private final Map<Block, Block> replacements = (Map) Util.make(Maps.newHashMap(), (hashmap) -> {
        hashmap.put(Blocks.COBBLESTONE, Blocks.BLACKSTONE);
        hashmap.put(Blocks.MOSSY_COBBLESTONE, Blocks.BLACKSTONE);
        hashmap.put(Blocks.STONE, Blocks.POLISHED_BLACKSTONE);
        hashmap.put(Blocks.STONE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICKS);
        hashmap.put(Blocks.MOSSY_STONE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICKS);
        hashmap.put(Blocks.COBBLESTONE_STAIRS, Blocks.BLACKSTONE_STAIRS);
        hashmap.put(Blocks.MOSSY_COBBLESTONE_STAIRS, Blocks.BLACKSTONE_STAIRS);
        hashmap.put(Blocks.STONE_STAIRS, Blocks.POLISHED_BLACKSTONE_STAIRS);
        hashmap.put(Blocks.STONE_BRICK_STAIRS, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS);
        hashmap.put(Blocks.MOSSY_STONE_BRICK_STAIRS, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS);
        hashmap.put(Blocks.COBBLESTONE_SLAB, Blocks.BLACKSTONE_SLAB);
        hashmap.put(Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.BLACKSTONE_SLAB);
        hashmap.put(Blocks.SMOOTH_STONE_SLAB, Blocks.POLISHED_BLACKSTONE_SLAB);
        hashmap.put(Blocks.STONE_SLAB, Blocks.POLISHED_BLACKSTONE_SLAB);
        hashmap.put(Blocks.STONE_BRICK_SLAB, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB);
        hashmap.put(Blocks.MOSSY_STONE_BRICK_SLAB, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB);
        hashmap.put(Blocks.STONE_BRICK_WALL, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
        hashmap.put(Blocks.MOSSY_STONE_BRICK_WALL, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
        hashmap.put(Blocks.COBBLESTONE_WALL, Blocks.BLACKSTONE_WALL);
        hashmap.put(Blocks.MOSSY_COBBLESTONE_WALL, Blocks.BLACKSTONE_WALL);
        hashmap.put(Blocks.CHISELED_STONE_BRICKS, Blocks.CHISELED_POLISHED_BLACKSTONE);
        hashmap.put(Blocks.CRACKED_STONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS);
        hashmap.put(Blocks.IRON_BARS, Blocks.IRON_CHAIN);
    });

    private BlackstoneReplaceProcessor() {}

    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos targetPosition, BlockPos referencePos, StructureTemplate.StructureBlockInfo originalBlockInfo, StructureTemplate.StructureBlockInfo processedBlockInfo, StructurePlaceSettings settings) {
        Block block = (Block) this.replacements.get(processedBlockInfo.state().getBlock());

        if (block == null) {
            return processedBlockInfo;
        } else {
            BlockState blockstate = processedBlockInfo.state();
            BlockState blockstate1 = block.defaultBlockState();

            if (blockstate.hasProperty(StairBlock.FACING)) {
                blockstate1 = (BlockState) blockstate1.setValue(StairBlock.FACING, (Direction) blockstate.getValue(StairBlock.FACING));
            }

            if (blockstate.hasProperty(StairBlock.HALF)) {
                blockstate1 = (BlockState) blockstate1.setValue(StairBlock.HALF, (Half) blockstate.getValue(StairBlock.HALF));
            }

            if (blockstate.hasProperty(SlabBlock.TYPE)) {
                blockstate1 = (BlockState) blockstate1.setValue(SlabBlock.TYPE, (SlabType) blockstate.getValue(SlabBlock.TYPE));
            }

            return new StructureTemplate.StructureBlockInfo(processedBlockInfo.pos(), blockstate1, processedBlockInfo.nbt());
        }
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return StructureProcessorType.BLACKSTONE_REPLACE;
    }
}
