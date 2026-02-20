package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class ShulkerBoxBlock extends BaseEntityBlock {

    public static final MapCodec<ShulkerBoxBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(DyeColor.CODEC.optionalFieldOf("color").forGetter((shulkerboxblock) -> {
            return Optional.ofNullable(shulkerboxblock.color);
        }), propertiesCodec()).apply(instance, (optional, blockbehaviour_properties) -> {
            return new ShulkerBoxBlock((DyeColor) optional.orElse((Object) null), blockbehaviour_properties);
        });
    });
    public static final Map<Direction, VoxelShape> SHAPES_OPEN_SUPPORT = Shapes.rotateAll(Block.boxZ(16.0D, 0.0D, 1.0D));
    public static final EnumProperty<Direction> FACING = DirectionalBlock.FACING;
    public static final Identifier CONTENTS = Identifier.withDefaultNamespace("contents");
    public final @Nullable DyeColor color;

    @Override
    public MapCodec<ShulkerBoxBlock> codec() {
        return ShulkerBoxBlock.CODEC;
    }

    public ShulkerBoxBlock(@Nullable DyeColor color, BlockBehaviour.Properties properties) {
        super(properties);
        this.color = color;
        this.registerDefaultState((BlockState) (this.stateDefinition.any()).setValue(ShulkerBoxBlock.FACING, Direction.UP));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new ShulkerBoxBlockEntity(this.color, worldPosition, blockState);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> type) {
        return createTickerHelper(type, BlockEntityType.SHULKER_BOX, ShulkerBoxBlockEntity::tick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverlevel) {
            BlockEntity blockentity = level.getBlockEntity(pos);

            if (blockentity instanceof ShulkerBoxBlockEntity shulkerboxblockentity) {
                if (canOpen(state, level, pos, shulkerboxblockentity)) {
                    player.openMenu(shulkerboxblockentity);
                    player.awardStat(Stats.OPEN_SHULKER_BOX);
                    PiglinAi.angerNearbyPiglins(serverlevel, player, true);
                }
            }
        }

        return InteractionResult.SUCCESS;
    }

    private static boolean canOpen(BlockState state, Level level, BlockPos pos, ShulkerBoxBlockEntity blockEntity) {
        if (blockEntity.getAnimationStatus() != ShulkerBoxBlockEntity.AnimationStatus.CLOSED) {
            return true;
        } else {
            AABB aabb = Shulker.getProgressDeltaAabb(1.0F, (Direction) state.getValue(ShulkerBoxBlock.FACING), 0.0F, 0.5F, pos.getBottomCenter()).deflate(1.0E-6D);

            return level.noCollision(aabb);
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState) this.defaultBlockState().setValue(ShulkerBoxBlock.FACING, context.getClickedFace());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ShulkerBoxBlock.FACING);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof ShulkerBoxBlockEntity shulkerboxblockentity) {
            if (!level.isClientSide() && player.preventsBlockDrops() && !shulkerboxblockentity.isEmpty()) {
                ItemStack itemstack = getColoredItemStack(this.getColor());

                itemstack.applyComponents(blockentity.collectComponents());
                ItemEntity itementity = new ItemEntity(level, (double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, itemstack);

                itementity.setDefaultPickUpDelay();
                level.addFreshEntity(itementity);
            } else {
                shulkerboxblockentity.unpackLootTable(player);
            }
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity blockentity = (BlockEntity) params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);

        if (blockentity instanceof ShulkerBoxBlockEntity shulkerboxblockentity) {
            params = params.withDynamicDrop(ShulkerBoxBlock.CONTENTS, (consumer) -> {
                for (int i = 0; i < shulkerboxblockentity.getContainerSize(); ++i) {
                    consumer.accept(shulkerboxblockentity.getItem(i));
                }

            });
        }

        return super.getDrops(state, params);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof ShulkerBoxBlockEntity shulkerboxblockentity) {
            if (!shulkerboxblockentity.isClosed()) {
                return (VoxelShape) ShulkerBoxBlock.SHAPES_OPEN_SUPPORT.get(((Direction) state.getValue(ShulkerBoxBlock.FACING)).getOpposite());
            }
        }

        return Shapes.block();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof ShulkerBoxBlockEntity shulkerboxblockentity) {
            return Shapes.create(shulkerboxblockentity.getBoundingBox(state));
        } else {
            return Shapes.block();
        }
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return false;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    public static Block getBlockByColor(@Nullable DyeColor color) {
        if (color == null) {
            return Blocks.SHULKER_BOX;
        } else {
            Block block;

            switch (color) {
                case WHITE:
                    block = Blocks.WHITE_SHULKER_BOX;
                    break;
                case ORANGE:
                    block = Blocks.ORANGE_SHULKER_BOX;
                    break;
                case MAGENTA:
                    block = Blocks.MAGENTA_SHULKER_BOX;
                    break;
                case LIGHT_BLUE:
                    block = Blocks.LIGHT_BLUE_SHULKER_BOX;
                    break;
                case YELLOW:
                    block = Blocks.YELLOW_SHULKER_BOX;
                    break;
                case LIME:
                    block = Blocks.LIME_SHULKER_BOX;
                    break;
                case PINK:
                    block = Blocks.PINK_SHULKER_BOX;
                    break;
                case GRAY:
                    block = Blocks.GRAY_SHULKER_BOX;
                    break;
                case LIGHT_GRAY:
                    block = Blocks.LIGHT_GRAY_SHULKER_BOX;
                    break;
                case CYAN:
                    block = Blocks.CYAN_SHULKER_BOX;
                    break;
                case BLUE:
                    block = Blocks.BLUE_SHULKER_BOX;
                    break;
                case BROWN:
                    block = Blocks.BROWN_SHULKER_BOX;
                    break;
                case GREEN:
                    block = Blocks.GREEN_SHULKER_BOX;
                    break;
                case RED:
                    block = Blocks.RED_SHULKER_BOX;
                    break;
                case BLACK:
                    block = Blocks.BLACK_SHULKER_BOX;
                    break;
                case PURPLE:
                    block = Blocks.PURPLE_SHULKER_BOX;
                    break;
                default:
                    throw new MatchException((String) null, (Throwable) null);
            }

            return block;
        }
    }

    public @Nullable DyeColor getColor() {
        return this.color;
    }

    public static ItemStack getColoredItemStack(@Nullable DyeColor color) {
        return new ItemStack(getBlockByColor(color));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(ShulkerBoxBlock.FACING, rotation.rotate((Direction) state.getValue(ShulkerBoxBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(ShulkerBoxBlock.FACING)));
    }
}
