package net.minecraft.world.level.block;

import com.google.common.collect.BiMap;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.block.state.pattern.BlockPatternBuilder;
import net.minecraft.world.level.block.state.predicate.BlockStatePredicate;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.Nullable;

public class CarvedPumpkinBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<CarvedPumpkinBlock> CODEC = simpleCodec(CarvedPumpkinBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    private @Nullable BlockPattern snowGolemBase;
    private @Nullable BlockPattern snowGolemFull;
    private @Nullable BlockPattern ironGolemBase;
    private @Nullable BlockPattern ironGolemFull;
    private @Nullable BlockPattern copperGolemBase;
    private @Nullable BlockPattern copperGolemFull;
    private static final Predicate<BlockState> PUMPKINS_PREDICATE = (blockstate) -> {
        return blockstate.is(Blocks.CARVED_PUMPKIN) || blockstate.is(Blocks.JACK_O_LANTERN);
    };

    @Override
    public MapCodec<? extends CarvedPumpkinBlock> codec() {
        return CarvedPumpkinBlock.CODEC;
    }

    protected CarvedPumpkinBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) (this.stateDefinition.any()).setValue(CarvedPumpkinBlock.FACING, Direction.NORTH));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock())) {
            this.trySpawnGolem(level, pos);
        }
    }

    public boolean canSpawnGolem(LevelReader level, BlockPos topPos) {
        return this.getOrCreateSnowGolemBase().find(level, topPos) != null || this.getOrCreateIronGolemBase().find(level, topPos) != null || this.getOrCreateCopperGolemBase().find(level, topPos) != null;
    }

    private void trySpawnGolem(Level level, BlockPos topPos) {
        BlockPattern.BlockPatternMatch blockpattern_blockpatternmatch = this.getOrCreateSnowGolemFull().find(level, topPos);

        if (blockpattern_blockpatternmatch != null) {
            SnowGolem snowgolem = EntityType.SNOW_GOLEM.create(level, EntitySpawnReason.TRIGGERED);

            if (snowgolem != null) {
                spawnGolemInWorld(level, blockpattern_blockpatternmatch, snowgolem, blockpattern_blockpatternmatch.getBlock(0, 2, 0).getPos());
                return;
            }
        }

        BlockPattern.BlockPatternMatch blockpattern_blockpatternmatch1 = this.getOrCreateIronGolemFull().find(level, topPos);

        if (blockpattern_blockpatternmatch1 != null) {
            IronGolem irongolem = EntityType.IRON_GOLEM.create(level, EntitySpawnReason.TRIGGERED);

            if (irongolem != null) {
                irongolem.setPlayerCreated(true);
                spawnGolemInWorld(level, blockpattern_blockpatternmatch1, irongolem, blockpattern_blockpatternmatch1.getBlock(1, 2, 0).getPos());
                return;
            }
        }

        BlockPattern.BlockPatternMatch blockpattern_blockpatternmatch2 = this.getOrCreateCopperGolemFull().find(level, topPos);

        if (blockpattern_blockpatternmatch2 != null) {
            CopperGolem coppergolem = EntityType.COPPER_GOLEM.create(level, EntitySpawnReason.TRIGGERED);

            if (coppergolem != null) {
                spawnGolemInWorld(level, blockpattern_blockpatternmatch2, coppergolem, blockpattern_blockpatternmatch2.getBlock(0, 0, 0).getPos());
                this.replaceCopperBlockWithChest(level, blockpattern_blockpatternmatch2);
                coppergolem.spawn(this.getWeatherStateFromPattern(blockpattern_blockpatternmatch2));
            }
        }

    }

    private WeatheringCopper.WeatherState getWeatherStateFromPattern(BlockPattern.BlockPatternMatch copperGolemMatch) {
        BlockState blockstate = copperGolemMatch.getBlock(0, 1, 0).getState();
        Block block = blockstate.getBlock();

        if (block instanceof WeatheringCopper weatheringcopper) {
            return (WeatheringCopper.WeatherState) weatheringcopper.getAge();
        } else {
            return (WeatheringCopper.WeatherState) ((WeatheringCopper) Optional.ofNullable((Block) ((BiMap) HoneycombItem.WAX_OFF_BY_BLOCK.get()).get(blockstate.getBlock())).filter((block1) -> {
                return block1 instanceof WeatheringCopper;
            }).map((block1) -> {
                return (WeatheringCopper) block1;
            }).orElse((WeatheringCopper) Blocks.COPPER_BLOCK)).getAge();
        }
    }

    private static void spawnGolemInWorld(Level level, BlockPattern.BlockPatternMatch match, Entity golem, BlockPos spawnPos) {
        clearPatternBlocks(level, match);
        golem.snapTo((double) spawnPos.getX() + 0.5D, (double) spawnPos.getY() + 0.05D, (double) spawnPos.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(golem);

        for (ServerPlayer serverplayer : level.getEntitiesOfClass(ServerPlayer.class, golem.getBoundingBox().inflate(5.0D))) {
            CriteriaTriggers.SUMMONED_ENTITY.trigger(serverplayer, golem);
        }

        updatePatternBlocks(level, match);
    }

    public static void clearPatternBlocks(Level level, BlockPattern.BlockPatternMatch match) {
        for (int i = 0; i < match.getWidth(); ++i) {
            for (int j = 0; j < match.getHeight(); ++j) {
                BlockInWorld blockinworld = match.getBlock(i, j, 0);

                level.setBlock(blockinworld.getPos(), Blocks.AIR.defaultBlockState(), 2);
                level.levelEvent(2001, blockinworld.getPos(), Block.getId(blockinworld.getState()));
            }
        }

    }

    public static void updatePatternBlocks(Level level, BlockPattern.BlockPatternMatch match) {
        for (int i = 0; i < match.getWidth(); ++i) {
            for (int j = 0; j < match.getHeight(); ++j) {
                BlockInWorld blockinworld = match.getBlock(i, j, 0);

                level.updateNeighborsAt(blockinworld.getPos(), Blocks.AIR);
            }
        }

    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState) this.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CarvedPumpkinBlock.FACING);
    }

    private BlockPattern getOrCreateSnowGolemBase() {
        if (this.snowGolemBase == null) {
            this.snowGolemBase = BlockPatternBuilder.start().aisle(" ", "#", "#").where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.SNOW_BLOCK))).build();
        }

        return this.snowGolemBase;
    }

    private BlockPattern getOrCreateSnowGolemFull() {
        if (this.snowGolemFull == null) {
            this.snowGolemFull = BlockPatternBuilder.start().aisle("^", "#", "#").where('^', BlockInWorld.hasState(CarvedPumpkinBlock.PUMPKINS_PREDICATE)).where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.SNOW_BLOCK))).build();
        }

        return this.snowGolemFull;
    }

    private BlockPattern getOrCreateIronGolemBase() {
        if (this.ironGolemBase == null) {
            this.ironGolemBase = BlockPatternBuilder.start().aisle("~ ~", "###", "~#~").where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.IRON_BLOCK))).where('~', BlockInWorld.hasState(BlockBehaviour.BlockStateBase::isAir)).build();
        }

        return this.ironGolemBase;
    }

    private BlockPattern getOrCreateIronGolemFull() {
        if (this.ironGolemFull == null) {
            this.ironGolemFull = BlockPatternBuilder.start().aisle("~^~", "###", "~#~").where('^', BlockInWorld.hasState(CarvedPumpkinBlock.PUMPKINS_PREDICATE)).where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.IRON_BLOCK))).where('~', BlockInWorld.hasState(BlockBehaviour.BlockStateBase::isAir)).build();
        }

        return this.ironGolemFull;
    }

    private BlockPattern getOrCreateCopperGolemBase() {
        if (this.copperGolemBase == null) {
            this.copperGolemBase = BlockPatternBuilder.start().aisle(" ", "#").where('#', BlockInWorld.hasState((blockstate) -> {
                return blockstate.is(BlockTags.COPPER);
            })).build();
        }

        return this.copperGolemBase;
    }

    private BlockPattern getOrCreateCopperGolemFull() {
        if (this.copperGolemFull == null) {
            this.copperGolemFull = BlockPatternBuilder.start().aisle("^", "#").where('^', BlockInWorld.hasState(CarvedPumpkinBlock.PUMPKINS_PREDICATE)).where('#', BlockInWorld.hasState((blockstate) -> {
                return blockstate.is(BlockTags.COPPER);
            })).build();
        }

        return this.copperGolemFull;
    }

    public void replaceCopperBlockWithChest(Level level, BlockPattern.BlockPatternMatch match) {
        BlockInWorld blockinworld = match.getBlock(0, 1, 0);
        BlockInWorld blockinworld1 = match.getBlock(0, 0, 0);
        Direction direction = (Direction) blockinworld1.getState().getValue(CarvedPumpkinBlock.FACING);
        BlockState blockstate = CopperChestBlock.getFromCopperBlock(blockinworld.getState().getBlock(), direction, level, blockinworld.getPos());

        level.setBlock(blockinworld.getPos(), blockstate, 2);
    }
}
