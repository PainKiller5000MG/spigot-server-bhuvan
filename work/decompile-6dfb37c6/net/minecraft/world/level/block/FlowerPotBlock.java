package net.minecraft.world.level.block;

import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FlowerPotBlock extends Block {

    public static final MapCodec<FlowerPotBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(BuiltInRegistries.BLOCK.byNameCodec().fieldOf("potted").forGetter((flowerpotblock) -> {
            return flowerpotblock.potted;
        }), propertiesCodec()).apply(instance, FlowerPotBlock::new);
    });
    private static final Map<Block, Block> POTTED_BY_CONTENT = Maps.newHashMap();
    private static final VoxelShape SHAPE = Block.column(6.0D, 0.0D, 6.0D);
    private final Block potted;

    @Override
    public MapCodec<FlowerPotBlock> codec() {
        return FlowerPotBlock.CODEC;
    }

    public FlowerPotBlock(Block potted, BlockBehaviour.Properties properties) {
        super(properties);
        this.potted = potted;
        FlowerPotBlock.POTTED_BY_CONTENT.put(potted, this);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FlowerPotBlock.SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        Item item = itemStack.getItem();
        Block block;

        if (item instanceof BlockItem blockitem) {
            block = (Block) FlowerPotBlock.POTTED_BY_CONTENT.getOrDefault(blockitem.getBlock(), Blocks.AIR);
        } else {
            block = Blocks.AIR;
        }

        BlockState blockstate1 = block.defaultBlockState();

        if (blockstate1.isAir()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        } else if (!this.isEmpty()) {
            return InteractionResult.CONSUME;
        } else {
            level.setBlock(pos, blockstate1, 3);
            level.gameEvent(player, (Holder) GameEvent.BLOCK_CHANGE, pos);
            player.awardStat(Stats.POT_FLOWER);
            itemStack.consume(1, player);
            return InteractionResult.SUCCESS;
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (this.isEmpty()) {
            return InteractionResult.CONSUME;
        } else {
            ItemStack itemstack = new ItemStack(this.potted);

            if (!player.addItem(itemstack)) {
                player.drop(itemstack, false);
            }

            level.setBlock(pos, Blocks.FLOWER_POT.defaultBlockState(), 3);
            level.gameEvent(player, (Holder) GameEvent.BLOCK_CHANGE, pos);
            return InteractionResult.SUCCESS;
        }
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return this.isEmpty() ? super.getCloneItemStack(level, pos, state, includeData) : new ItemStack(this.potted);
    }

    private boolean isEmpty() {
        return this.potted == Blocks.AIR;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return directionToNeighbour == Direction.DOWN && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    public Block getPotted() {
        return this.potted;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.is(Blocks.POTTED_OPEN_EYEBLOSSOM) || state.is(Blocks.POTTED_CLOSED_EYEBLOSSOM);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (this.isRandomlyTicking(state)) {
            boolean flag = this.potted == Blocks.OPEN_EYEBLOSSOM;
            boolean flag1 = ((TriState) level.environmentAttributes().getValue(EnvironmentAttributes.EYEBLOSSOM_OPEN, pos)).toBoolean(flag);

            if (flag != flag1) {
                level.setBlock(pos, this.opposite(state), 3);
                EyeblossomBlock.Type eyeblossomblock_type = EyeblossomBlock.Type.fromBoolean(flag).transform();

                eyeblossomblock_type.spawnTransformParticle(level, pos, random);
                level.playSound((Entity) null, pos, eyeblossomblock_type.longSwitchSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }

        super.randomTick(state, level, pos, random);
    }

    public BlockState opposite(BlockState state) {
        return state.is(Blocks.POTTED_OPEN_EYEBLOSSOM) ? Blocks.POTTED_CLOSED_EYEBLOSSOM.defaultBlockState() : (state.is(Blocks.POTTED_CLOSED_EYEBLOSSOM) ? Blocks.POTTED_OPEN_EYEBLOSSOM.defaultBlockState() : state);
    }
}
