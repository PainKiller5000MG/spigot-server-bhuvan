package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class BeehiveBlock extends BaseEntityBlock {

    public static final MapCodec<BeehiveBlock> CODEC = simpleCodec(BeehiveBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty HONEY_LEVEL = BlockStateProperties.LEVEL_HONEY;
    public static final int MAX_HONEY_LEVELS = 5;

    @Override
    public MapCodec<BeehiveBlock> codec() {
        return BeehiveBlock.CODEC;
    }

    public BeehiveBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(BeehiveBlock.HONEY_LEVEL, 0)).setValue(BeehiveBlock.FACING, Direction.NORTH));
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return (Integer) state.getValue(BeehiveBlock.HONEY_LEVEL);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack destroyedWith) {
        super.playerDestroy(level, player, pos, state, blockEntity, destroyedWith);
        if (!level.isClientSide() && blockEntity instanceof BeehiveBlockEntity beehiveblockentity) {
            if (!EnchantmentHelper.hasTag(destroyedWith, EnchantmentTags.PREVENTS_BEE_SPAWNS_WHEN_MINING)) {
                beehiveblockentity.emptyAllLivingFromHive(player, state, BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
                Containers.updateNeighboursAfterDestroy(state, level, pos);
                this.angerNearbyBees(level, pos);
            }

            CriteriaTriggers.BEE_NEST_DESTROYED.trigger((ServerPlayer) player, state, destroyedWith, beehiveblockentity.getOccupantCount());
        }

    }

    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> onHit) {
        super.onExplosionHit(state, level, pos, explosion, onHit);
        this.angerNearbyBees(level, pos);
    }

    private void angerNearbyBees(Level level, BlockPos pos) {
        AABB aabb = (new AABB(pos)).inflate(8.0D, 6.0D, 8.0D);
        List<Bee> list = level.<Bee>getEntitiesOfClass(Bee.class, aabb);

        if (!list.isEmpty()) {
            List<Player> list1 = level.<Player>getEntitiesOfClass(Player.class, aabb);

            if (list1.isEmpty()) {
                return;
            }

            for (Bee bee : list) {
                if (bee.getTarget() == null) {
                    Player player = (Player) Util.getRandom(list1, level.random);

                    bee.setTarget(player);
                }
            }
        }

    }

    public static void dropHoneycomb(ServerLevel level, ItemStack tool, BlockState blockState, @Nullable BlockEntity blockEntity, @Nullable Entity entity, BlockPos pos) {
        dropFromBlockInteractLootTable(level, BuiltInLootTables.HARVEST_BEEHIVE, blockState, blockEntity, tool, entity, (serverlevel1, itemstack1) -> {
            popResource(serverlevel1, pos, itemstack1);
        });
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        int i = (Integer) state.getValue(BeehiveBlock.HONEY_LEVEL);
        boolean flag = false;

        if (i >= 5) {
            Item item;
            label40:
            {
                item = itemStack.getItem();
                if (level instanceof ServerLevel) {
                    ServerLevel serverlevel = (ServerLevel) level;

                    if (itemStack.is(Items.SHEARS)) {
                        dropHoneycomb(serverlevel, itemStack, state, level.getBlockEntity(pos), player, pos);
                        level.playSound((Entity) null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
                        itemStack.hurtAndBreak(1, player, hand.asEquipmentSlot());
                        flag = true;
                        level.gameEvent(player, (Holder) GameEvent.SHEAR, pos);
                        break label40;
                    }
                }

                if (itemStack.is(Items.GLASS_BOTTLE)) {
                    itemStack.shrink(1);
                    level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                    if (itemStack.isEmpty()) {
                        player.setItemInHand(hand, new ItemStack(Items.HONEY_BOTTLE));
                    } else if (!player.getInventory().add(new ItemStack(Items.HONEY_BOTTLE))) {
                        player.drop(new ItemStack(Items.HONEY_BOTTLE), false);
                    }

                    flag = true;
                    level.gameEvent(player, (Holder) GameEvent.FLUID_PICKUP, pos);
                }
            }

            if (!level.isClientSide() && flag) {
                player.awardStat(Stats.ITEM_USED.get(item));
            }
        }

        if (flag) {
            if (!CampfireBlock.isSmokeyPos(level, pos)) {
                if (this.hiveContainsBees(level, pos)) {
                    this.angerNearbyBees(level, pos);
                }

                this.releaseBeesAndResetHoneyLevel(level, state, pos, player, BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
            } else {
                this.resetHoneyLevel(level, state, pos);
            }

            return InteractionResult.SUCCESS;
        } else {
            return super.useItemOn(itemStack, state, level, pos, player, hand, hitResult);
        }
    }

    private boolean hiveContainsBees(Level level, BlockPos pos) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof BeehiveBlockEntity beehiveblockentity) {
            return !beehiveblockentity.isEmpty();
        } else {
            return false;
        }
    }

    public void releaseBeesAndResetHoneyLevel(Level level, BlockState state, BlockPos pos, @Nullable Player player, BeehiveBlockEntity.BeeReleaseStatus beeReleaseStatus) {
        this.resetHoneyLevel(level, state, pos);
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof BeehiveBlockEntity beehiveblockentity) {
            beehiveblockentity.emptyAllLivingFromHive(player, state, beeReleaseStatus);
        }

    }

    public void resetHoneyLevel(Level level, BlockState state, BlockPos pos) {
        level.setBlock(pos, (BlockState) state.setValue(BeehiveBlock.HONEY_LEVEL, 0), 3);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if ((Integer) state.getValue(BeehiveBlock.HONEY_LEVEL) >= 5) {
            for (int i = 0; i < random.nextInt(1) + 1; ++i) {
                this.trySpawnDripParticles(level, pos, state);
            }
        }

    }

    private void trySpawnDripParticles(Level level, BlockPos pos, BlockState state) {
        if (state.getFluidState().isEmpty() && level.random.nextFloat() >= 0.3F) {
            VoxelShape voxelshape = state.getCollisionShape(level, pos);
            double d0 = voxelshape.max(Direction.Axis.Y);

            if (d0 >= 1.0D && !state.is(BlockTags.IMPERMEABLE)) {
                double d1 = voxelshape.min(Direction.Axis.Y);

                if (d1 > 0.0D) {
                    this.spawnParticle(level, pos, voxelshape, (double) pos.getY() + d1 - 0.05D);
                } else {
                    BlockPos blockpos1 = pos.below();
                    BlockState blockstate1 = level.getBlockState(blockpos1);
                    VoxelShape voxelshape1 = blockstate1.getCollisionShape(level, blockpos1);
                    double d2 = voxelshape1.max(Direction.Axis.Y);

                    if ((d2 < 1.0D || !blockstate1.isCollisionShapeFullBlock(level, blockpos1)) && blockstate1.getFluidState().isEmpty()) {
                        this.spawnParticle(level, pos, voxelshape, (double) pos.getY() - 0.05D);
                    }
                }
            }

        }
    }

    private void spawnParticle(Level level, BlockPos pos, VoxelShape dripShape, double height) {
        this.spawnFluidParticle(level, (double) pos.getX() + dripShape.min(Direction.Axis.X), (double) pos.getX() + dripShape.max(Direction.Axis.X), (double) pos.getZ() + dripShape.min(Direction.Axis.Z), (double) pos.getZ() + dripShape.max(Direction.Axis.Z), height);
    }

    private void spawnFluidParticle(Level level, double x1, double x2, double z1, double z2, double y) {
        level.addParticle(ParticleTypes.DRIPPING_HONEY, Mth.lerp(level.random.nextDouble(), x1, x2), y, Mth.lerp(level.random.nextDouble(), z1, z2), 0.0D, 0.0D, 0.0D);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState) this.defaultBlockState().setValue(BeehiveBlock.FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BeehiveBlock.HONEY_LEVEL, BeehiveBlock.FACING);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new BeehiveBlockEntity(worldPosition, blockState);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, BlockEntityType.BEEHIVE, BeehiveBlockEntity::serverTick);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel serverlevel) {
            if (player.preventsBlockDrops() && (Boolean) serverlevel.getGameRules().get(GameRules.BLOCK_DROPS)) {
                BlockEntity blockentity = level.getBlockEntity(pos);

                if (blockentity instanceof BeehiveBlockEntity) {
                    BeehiveBlockEntity beehiveblockentity = (BeehiveBlockEntity) blockentity;
                    int i = (Integer) state.getValue(BeehiveBlock.HONEY_LEVEL);
                    boolean flag = !beehiveblockentity.isEmpty();

                    if (flag || i > 0) {
                        ItemStack itemstack = new ItemStack(this);

                        itemstack.applyComponents(beehiveblockentity.collectComponents());
                        itemstack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(BeehiveBlock.HONEY_LEVEL, i));
                        ItemEntity itementity = new ItemEntity(level, (double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), itemstack);

                        itementity.setDefaultPickUpDelay();
                        level.addFreshEntity(itementity);
                    }
                }
            }
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        Entity entity = (Entity) params.getOptionalParameter(LootContextParams.THIS_ENTITY);

        if (entity instanceof PrimedTnt || entity instanceof Creeper || entity instanceof WitherSkull || entity instanceof WitherBoss || entity instanceof MinecartTNT) {
            BlockEntity blockentity = (BlockEntity) params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);

            if (blockentity instanceof BeehiveBlockEntity) {
                BeehiveBlockEntity beehiveblockentity = (BeehiveBlockEntity) blockentity;

                beehiveblockentity.emptyAllLivingFromHive((Player) null, state, BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
            }
        }

        return super.getDrops(state, params);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        ItemStack itemstack = super.getCloneItemStack(level, pos, state, includeData);

        if (includeData) {
            itemstack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(BeehiveBlock.HONEY_LEVEL, (Integer) state.getValue(BeehiveBlock.HONEY_LEVEL)));
        }

        return itemstack;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (level.getBlockState(neighbourPos).getBlock() instanceof FireBlock) {
            BlockEntity blockentity = level.getBlockEntity(pos);

            if (blockentity instanceof BeehiveBlockEntity) {
                BeehiveBlockEntity beehiveblockentity = (BeehiveBlockEntity) blockentity;

                beehiveblockentity.emptyAllLivingFromHive((Player) null, state, BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
            }
        }

        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(BeehiveBlock.FACING, rotation.rotate((Direction) state.getValue(BeehiveBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(BeehiveBlock.FACING)));
    }
}
