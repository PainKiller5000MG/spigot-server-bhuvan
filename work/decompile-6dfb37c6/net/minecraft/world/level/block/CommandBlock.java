package net.minecraft.world.level.block;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class CommandBlock extends BaseEntityBlock implements GameMasterBlock {

    public static final MapCodec<CommandBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.BOOL.fieldOf("automatic").forGetter((commandblock) -> {
            return commandblock.automatic;
        }), propertiesCodec()).apply(instance, CommandBlock::new);
    });
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final EnumProperty<Direction> FACING = DirectionalBlock.FACING;
    public static final BooleanProperty CONDITIONAL = BlockStateProperties.CONDITIONAL;
    private final boolean automatic;

    @Override
    public MapCodec<CommandBlock> codec() {
        return CommandBlock.CODEC;
    }

    public CommandBlock(boolean automatic, BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(CommandBlock.FACING, Direction.NORTH)).setValue(CommandBlock.CONDITIONAL, false));
        this.automatic = automatic;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        CommandBlockEntity commandblockentity = new CommandBlockEntity(worldPosition, blockState);

        commandblockentity.setAutomatic(this.automatic);
        return commandblockentity;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide()) {
            BlockEntity blockentity = level.getBlockEntity(pos);

            if (blockentity instanceof CommandBlockEntity) {
                CommandBlockEntity commandblockentity = (CommandBlockEntity) blockentity;

                this.setPoweredAndUpdate(level, pos, commandblockentity, level.hasNeighborSignal(pos));
            }

        }
    }

    private void setPoweredAndUpdate(Level level, BlockPos pos, CommandBlockEntity commandBlock, boolean isPowered) {
        boolean flag1 = commandBlock.isPowered();

        if (isPowered != flag1) {
            commandBlock.setPowered(isPowered);
            if (isPowered) {
                if (commandBlock.isAutomatic() || commandBlock.getMode() == CommandBlockEntity.Mode.SEQUENCE) {
                    return;
                }

                commandBlock.markConditionMet();
                level.scheduleTick(pos, (Block) this, 1);
            }

        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof CommandBlockEntity commandblockentity) {
            BaseCommandBlock basecommandblock = commandblockentity.getCommandBlock();
            boolean flag = !StringUtil.isNullOrEmpty(basecommandblock.getCommand());
            CommandBlockEntity.Mode commandblockentity_mode = commandblockentity.getMode();
            boolean flag1 = commandblockentity.wasConditionMet();

            if (commandblockentity_mode == CommandBlockEntity.Mode.AUTO) {
                commandblockentity.markConditionMet();
                if (flag1) {
                    this.execute(state, level, pos, basecommandblock, flag);
                } else if (commandblockentity.isConditional()) {
                    basecommandblock.setSuccessCount(0);
                }

                if (commandblockentity.isPowered() || commandblockentity.isAutomatic()) {
                    level.scheduleTick(pos, (Block) this, 1);
                }
            } else if (commandblockentity_mode == CommandBlockEntity.Mode.REDSTONE) {
                if (flag1) {
                    this.execute(state, level, pos, basecommandblock, flag);
                } else if (commandblockentity.isConditional()) {
                    basecommandblock.setSuccessCount(0);
                }
            }

            level.updateNeighbourForOutputSignal(pos, this);
        }

    }

    private void execute(BlockState state, ServerLevel level, BlockPos pos, BaseCommandBlock baseCommandBlock, boolean commandSet) {
        if (commandSet) {
            baseCommandBlock.performCommand(level);
        } else {
            baseCommandBlock.setSuccessCount(0);
        }

        executeChain(level, pos, (Direction) state.getValue(CommandBlock.FACING));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof CommandBlockEntity && player.canUseGameMasterBlocks()) {
            player.openCommandBlock((CommandBlockEntity) blockentity);
            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        return blockentity instanceof CommandBlockEntity ? ((CommandBlockEntity) blockentity).getCommandBlock().getSuccessCount() : 0;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof CommandBlockEntity commandblockentity) {
            BaseCommandBlock basecommandblock = commandblockentity.getCommandBlock();

            if (level instanceof ServerLevel serverlevel) {
                if (!itemStack.has(DataComponents.BLOCK_ENTITY_DATA)) {
                    basecommandblock.setTrackOutput((Boolean) serverlevel.getGameRules().get(GameRules.SEND_COMMAND_FEEDBACK));
                    commandblockentity.setAutomatic(this.automatic);
                }

                boolean flag = level.hasNeighborSignal(pos);

                this.setPoweredAndUpdate(level, pos, commandblockentity, flag);
            }

        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(CommandBlock.FACING, rotation.rotate((Direction) state.getValue(CommandBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(CommandBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CommandBlock.FACING, CommandBlock.CONDITIONAL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState) this.defaultBlockState().setValue(CommandBlock.FACING, context.getNearestLookingDirection().getOpposite());
    }

    private static void executeChain(ServerLevel level, BlockPos blockPos, Direction direction) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = blockPos.mutable();
        GameRules gamerules = level.getGameRules();

        int i;
        BlockState blockstate;

        for (i = (Integer) gamerules.get(GameRules.MAX_COMMAND_SEQUENCE_LENGTH); i-- > 0; direction = (Direction) blockstate.getValue(CommandBlock.FACING)) {
            blockpos_mutableblockpos.move(direction);
            blockstate = level.getBlockState(blockpos_mutableblockpos);
            Block block = blockstate.getBlock();

            if (!blockstate.is(Blocks.CHAIN_COMMAND_BLOCK)) {
                break;
            }

            BlockEntity blockentity = level.getBlockEntity(blockpos_mutableblockpos);

            if (!(blockentity instanceof CommandBlockEntity)) {
                break;
            }

            CommandBlockEntity commandblockentity = (CommandBlockEntity) blockentity;

            if (commandblockentity.getMode() != CommandBlockEntity.Mode.SEQUENCE) {
                break;
            }

            if (commandblockentity.isPowered() || commandblockentity.isAutomatic()) {
                BaseCommandBlock basecommandblock = commandblockentity.getCommandBlock();

                if (commandblockentity.markConditionMet()) {
                    if (!basecommandblock.performCommand(level)) {
                        break;
                    }

                    level.updateNeighbourForOutputSignal(blockpos_mutableblockpos, block);
                } else if (commandblockentity.isConditional()) {
                    basecommandblock.setSuccessCount(0);
                }
            }
        }

        if (i <= 0) {
            int j = Math.max((Integer) gamerules.get(GameRules.MAX_COMMAND_SEQUENCE_LENGTH), 0);

            CommandBlock.LOGGER.warn("Command Block chain tried to execute more than {} steps!", j);
        }

    }
}
