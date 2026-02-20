package net.minecraft.world.level.block;

import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gamerules.GameRules;

public class InfestedBlock extends Block {

    public static final MapCodec<InfestedBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(BuiltInRegistries.BLOCK.byNameCodec().fieldOf("host").forGetter(InfestedBlock::getHostBlock), propertiesCodec()).apply(instance, InfestedBlock::new);
    });
    private final Block hostBlock;
    private static final Map<Block, Block> BLOCK_BY_HOST_BLOCK = Maps.newIdentityHashMap();
    private static final Map<BlockState, BlockState> HOST_TO_INFESTED_STATES = Maps.newIdentityHashMap();
    private static final Map<BlockState, BlockState> INFESTED_TO_HOST_STATES = Maps.newIdentityHashMap();

    @Override
    public MapCodec<? extends InfestedBlock> codec() {
        return InfestedBlock.CODEC;
    }

    public InfestedBlock(Block hostBlock, BlockBehaviour.Properties properties) {
        super(properties.destroyTime(hostBlock.defaultDestroyTime() / 2.0F).explosionResistance(0.75F));
        this.hostBlock = hostBlock;
        InfestedBlock.BLOCK_BY_HOST_BLOCK.put(hostBlock, this);
    }

    public Block getHostBlock() {
        return this.hostBlock;
    }

    public static boolean isCompatibleHostBlock(BlockState blockState) {
        return InfestedBlock.BLOCK_BY_HOST_BLOCK.containsKey(blockState.getBlock());
    }

    private void spawnInfestation(ServerLevel level, BlockPos pos) {
        Silverfish silverfish = EntityType.SILVERFISH.create(level, EntitySpawnReason.TRIGGERED);

        if (silverfish != null) {
            silverfish.snapTo((double) pos.getX() + 0.5D, (double) pos.getY(), (double) pos.getZ() + 0.5D, 0.0F, 0.0F);
            level.addFreshEntity(silverfish);
            silverfish.spawnAnim();
        }

    }

    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, tool, dropExperience);
        if ((Boolean) level.getGameRules().get(GameRules.BLOCK_DROPS) && !EnchantmentHelper.hasTag(tool, EnchantmentTags.PREVENTS_INFESTED_SPAWNS)) {
            this.spawnInfestation(level, pos);
        }

    }

    public static BlockState infestedStateByHost(BlockState hostState) {
        return getNewStateWithProperties(InfestedBlock.HOST_TO_INFESTED_STATES, hostState, () -> {
            return ((Block) InfestedBlock.BLOCK_BY_HOST_BLOCK.get(hostState.getBlock())).defaultBlockState();
        });
    }

    public BlockState hostStateByInfested(BlockState infestedState) {
        return getNewStateWithProperties(InfestedBlock.INFESTED_TO_HOST_STATES, infestedState, () -> {
            return this.getHostBlock().defaultBlockState();
        });
    }

    private static BlockState getNewStateWithProperties(Map<BlockState, BlockState> map, BlockState oldState, Supplier<BlockState> newStateSupplier) {
        return (BlockState) map.computeIfAbsent(oldState, (blockstate1) -> {
            BlockState blockstate2 = (BlockState) newStateSupplier.get();

            for (Property property : blockstate1.getProperties()) {
                blockstate2 = blockstate2.hasProperty(property) ? (BlockState) blockstate2.setValue(property, blockstate1.getValue(property)) : blockstate2;
            }

            return blockstate2;
        });
    }
}
