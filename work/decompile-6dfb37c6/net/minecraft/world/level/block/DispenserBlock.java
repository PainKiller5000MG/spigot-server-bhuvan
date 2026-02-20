package net.minecraft.world.level.block;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.dispenser.EquipmentDispenseItemBehavior;
import net.minecraft.core.dispenser.ProjectileDispenseBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.DropperBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class DispenserBlock extends BaseEntityBlock {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<DispenserBlock> CODEC = simpleCodec(DispenserBlock::new);
    public static final EnumProperty<Direction> FACING = DirectionalBlock.FACING;
    public static final BooleanProperty TRIGGERED = BlockStateProperties.TRIGGERED;
    private static final DefaultDispenseItemBehavior DEFAULT_BEHAVIOR = new DefaultDispenseItemBehavior();
    public static final Map<Item, DispenseItemBehavior> DISPENSER_REGISTRY = new IdentityHashMap();
    private static final int TRIGGER_DURATION = 4;

    @Override
    public MapCodec<? extends DispenserBlock> codec() {
        return DispenserBlock.CODEC;
    }

    public static void registerBehavior(ItemLike item, DispenseItemBehavior behavior) {
        DispenserBlock.DISPENSER_REGISTRY.put(item.asItem(), behavior);
    }

    public static void registerProjectileBehavior(ItemLike item) {
        DispenserBlock.DISPENSER_REGISTRY.put(item.asItem(), new ProjectileDispenseBehavior(item.asItem()));
    }

    protected DispenserBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(DispenserBlock.FACING, Direction.NORTH)).setValue(DispenserBlock.TRIGGERED, false));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            BlockEntity blockentity = level.getBlockEntity(pos);

            if (blockentity instanceof DispenserBlockEntity) {
                DispenserBlockEntity dispenserblockentity = (DispenserBlockEntity) blockentity;

                player.openMenu(dispenserblockentity);
                player.awardStat(dispenserblockentity instanceof DropperBlockEntity ? Stats.INSPECT_DROPPER : Stats.INSPECT_DISPENSER);
            }
        }

        return InteractionResult.SUCCESS;
    }

    public void dispenseFrom(ServerLevel level, BlockState state, BlockPos pos) {
        DispenserBlockEntity dispenserblockentity = (DispenserBlockEntity) level.getBlockEntity(pos, BlockEntityType.DISPENSER).orElse((Object) null);

        if (dispenserblockentity == null) {
            DispenserBlock.LOGGER.warn("Ignoring dispensing attempt for Dispenser without matching block entity at {}", pos);
        } else {
            BlockSource blocksource = new BlockSource(level, pos, state, dispenserblockentity);
            int i = dispenserblockentity.getRandomSlot(level.random);

            if (i < 0) {
                level.levelEvent(1001, pos, 0);
                level.gameEvent(GameEvent.BLOCK_ACTIVATE, pos, GameEvent.Context.of(dispenserblockentity.getBlockState()));
            } else {
                ItemStack itemstack = dispenserblockentity.getItem(i);
                DispenseItemBehavior dispenseitembehavior = this.getDispenseMethod(level, itemstack);

                if (dispenseitembehavior != DispenseItemBehavior.NOOP) {
                    dispenserblockentity.setItem(i, dispenseitembehavior.dispense(blocksource, itemstack));
                }

            }
        }
    }

    protected DispenseItemBehavior getDispenseMethod(Level level, ItemStack itemStack) {
        if (!itemStack.isItemEnabled(level.enabledFeatures())) {
            return DispenserBlock.DEFAULT_BEHAVIOR;
        } else {
            DispenseItemBehavior dispenseitembehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(itemStack.getItem());

            return dispenseitembehavior != null ? dispenseitembehavior : getDefaultDispenseMethod(itemStack);
        }
    }

    private static DispenseItemBehavior getDefaultDispenseMethod(ItemStack itemStack) {
        return (DispenseItemBehavior) (itemStack.has(DataComponents.EQUIPPABLE) ? EquipmentDispenseItemBehavior.INSTANCE : DispenserBlock.DEFAULT_BEHAVIOR);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        boolean flag1 = level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.above());
        boolean flag2 = (Boolean) state.getValue(DispenserBlock.TRIGGERED);

        if (flag1 && !flag2) {
            level.scheduleTick(pos, (Block) this, 4);
            level.setBlock(pos, (BlockState) state.setValue(DispenserBlock.TRIGGERED, true), 2);
        } else if (!flag1 && flag2) {
            level.setBlock(pos, (BlockState) state.setValue(DispenserBlock.TRIGGERED, false), 2);
        }

    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        this.dispenseFrom(level, state, pos);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new DispenserBlockEntity(worldPosition, blockState);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState) this.defaultBlockState().setValue(DispenserBlock.FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    public static Position getDispensePosition(BlockSource source) {
        return getDispensePosition(source, 0.7D, Vec3.ZERO);
    }

    public static Position getDispensePosition(BlockSource source, double scale, Vec3 offset) {
        Direction direction = (Direction) source.state().getValue(DispenserBlock.FACING);

        return source.center().add(scale * (double) direction.getStepX() + offset.x(), scale * (double) direction.getStepY() + offset.y(), scale * (double) direction.getStepZ() + offset.z());
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(DispenserBlock.FACING, rotation.rotate((Direction) state.getValue(DispenserBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(DispenserBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DispenserBlock.FACING, DispenserBlock.TRIGGERED);
    }
}
