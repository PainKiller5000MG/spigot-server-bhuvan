package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.CreakingHeartBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.CreakingHeartState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.Nullable;

public class CreakingHeartBlock extends BaseEntityBlock {

    public static final MapCodec<CreakingHeartBlock> CODEC = simpleCodec(CreakingHeartBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final EnumProperty<CreakingHeartState> STATE = BlockStateProperties.CREAKING_HEART_STATE;
    public static final BooleanProperty NATURAL = BlockStateProperties.NATURAL;

    @Override
    public MapCodec<CreakingHeartBlock> codec() {
        return CreakingHeartBlock.CODEC;
    }

    protected CreakingHeartBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) this.defaultBlockState().setValue(CreakingHeartBlock.AXIS, Direction.Axis.Y)).setValue(CreakingHeartBlock.STATE, CreakingHeartState.UPROOTED)).setValue(CreakingHeartBlock.NATURAL, false));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new CreakingHeartBlockEntity(worldPosition, blockState);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> type) {
        return level.isClientSide() ? null : (blockState.getValue(CreakingHeartBlock.STATE) != CreakingHeartState.UPROOTED ? createTickerHelper(type, BlockEntityType.CREAKING_HEART, CreakingHeartBlockEntity::serverTick) : null);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if ((Boolean) level.environmentAttributes().getValue(EnvironmentAttributes.CREAKING_ACTIVE, pos)) {
            if (state.getValue(CreakingHeartBlock.STATE) != CreakingHeartState.UPROOTED) {
                if (random.nextInt(16) == 0 && isSurroundedByLogs(level, pos)) {
                    level.playLocalSound((double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), SoundEvents.CREAKING_HEART_IDLE, SoundSource.BLOCKS, 1.0F, 1.0F, false);
                }

            }
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        ticks.scheduleTick(pos, (Block) this, 1);
        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState blockstate1 = updateState(state, level, pos);

        if (blockstate1 != state) {
            level.setBlock(pos, blockstate1, 3);
        }

    }

    private static BlockState updateState(BlockState state, Level level, BlockPos pos) {
        boolean flag = hasRequiredLogs(state, level, pos);
        boolean flag1 = state.getValue(CreakingHeartBlock.STATE) == CreakingHeartState.UPROOTED;

        return flag && flag1 ? (BlockState) state.setValue(CreakingHeartBlock.STATE, (Boolean) level.environmentAttributes().getValue(EnvironmentAttributes.CREAKING_ACTIVE, pos) ? CreakingHeartState.AWAKE : CreakingHeartState.DORMANT) : state;
    }

    public static boolean hasRequiredLogs(BlockState state, LevelReader level, BlockPos pos) {
        Direction.Axis direction_axis = (Direction.Axis) state.getValue(CreakingHeartBlock.AXIS);

        for (Direction direction : direction_axis.getDirections()) {
            BlockState blockstate1 = level.getBlockState(pos.relative(direction));

            if (!blockstate1.is(BlockTags.PALE_OAK_LOGS) || blockstate1.getValue(CreakingHeartBlock.AXIS) != direction_axis) {
                return false;
            }
        }

        return true;
    }

    private static boolean isSurroundedByLogs(LevelAccessor level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos blockpos1 = pos.relative(direction);
            BlockState blockstate = level.getBlockState(blockpos1);

            if (!blockstate.is(BlockTags.PALE_OAK_LOGS)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return updateState((BlockState) this.defaultBlockState().setValue(CreakingHeartBlock.AXIS, context.getClickedFace().getAxis()), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return RotatedPillarBlock.rotatePillar(state, rotation);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CreakingHeartBlock.AXIS, CreakingHeartBlock.STATE, CreakingHeartBlock.NATURAL);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> onHit) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof CreakingHeartBlockEntity creakingheartblockentity) {
            if (explosion instanceof ServerExplosion serverexplosion) {
                if (explosion.getBlockInteraction().shouldAffectBlocklikeEntities()) {
                    creakingheartblockentity.removeProtector(serverexplosion.getDamageSource());
                    LivingEntity livingentity = explosion.getIndirectSourceEntity();

                    if (livingentity instanceof Player) {
                        Player player = (Player) livingentity;

                        if (explosion.getBlockInteraction().shouldAffectBlocklikeEntities()) {
                            this.tryAwardExperience(player, state, level, pos);
                        }
                    }
                }
            }
        }

        super.onExplosionHit(state, level, pos, explosion, onHit);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof CreakingHeartBlockEntity creakingheartblockentity) {
            creakingheartblockentity.removeProtector(player.damageSources().playerAttack(player));
            this.tryAwardExperience(player, state, level, pos);
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    private void tryAwardExperience(Player player, BlockState state, Level level, BlockPos pos) {
        if (!player.preventsBlockDrops() && !player.isSpectator() && (Boolean) state.getValue(CreakingHeartBlock.NATURAL) && level instanceof ServerLevel serverlevel) {
            this.popExperience(serverlevel, pos, level.random.nextIntBetweenInclusive(20, 24));
        }

    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        if (state.getValue(CreakingHeartBlock.STATE) == CreakingHeartState.UPROOTED) {
            return 0;
        } else {
            BlockEntity blockentity = level.getBlockEntity(pos);

            if (blockentity instanceof CreakingHeartBlockEntity) {
                CreakingHeartBlockEntity creakingheartblockentity = (CreakingHeartBlockEntity) blockentity;

                return creakingheartblockentity.getAnalogOutputSignal();
            } else {
                return 0;
            }
        }
    }
}
