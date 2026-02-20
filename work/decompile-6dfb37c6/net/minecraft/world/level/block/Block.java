package net.minecraft.world.level.block;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Object2ByteLinkedOpenHashMap;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class Block extends BlockBehaviour implements ItemLike {

    public static final MapCodec<Block> CODEC = simpleCodec(Block::new);
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Holder.Reference<Block> builtInRegistryHolder;
    public static final IdMapper<BlockState> BLOCK_STATE_REGISTRY = new IdMapper<BlockState>();
    private static final LoadingCache<VoxelShape, Boolean> SHAPE_FULL_BLOCK_CACHE = CacheBuilder.newBuilder().maximumSize(512L).weakKeys().build(new CacheLoader<VoxelShape, Boolean>() {
        public Boolean load(VoxelShape shape) {
            return !Shapes.joinIsNotEmpty(Shapes.block(), shape, BooleanOp.NOT_SAME);
        }
    });
    public static final int UPDATE_NEIGHBORS = 1;
    public static final int UPDATE_CLIENTS = 2;
    public static final int UPDATE_INVISIBLE = 4;
    public static final int UPDATE_IMMEDIATE = 8;
    public static final int UPDATE_KNOWN_SHAPE = 16;
    public static final int UPDATE_SUPPRESS_DROPS = 32;
    public static final int UPDATE_MOVE_BY_PISTON = 64;
    public static final int UPDATE_SKIP_SHAPE_UPDATE_ON_WIRE = 128;
    public static final int UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS = 256;
    public static final int UPDATE_SKIP_ON_PLACE = 512;
    public static final @Block.UpdateFlags int UPDATE_NONE = 260;
    public static final @Block.UpdateFlags int UPDATE_ALL = 3;
    public static final @Block.UpdateFlags int UPDATE_ALL_IMMEDIATE = 11;
    public static final @Block.UpdateFlags int UPDATE_SKIP_ALL_SIDEEFFECTS = 816;
    public static final float INDESTRUCTIBLE = -1.0F;
    public static final float INSTANT = 0.0F;
    public static final int UPDATE_LIMIT = 512;
    protected final StateDefinition<Block, BlockState> stateDefinition;
    private BlockState defaultBlockState;
    private @Nullable Item item;
    private static final int CACHE_SIZE = 256;
    private static final ThreadLocal<Object2ByteLinkedOpenHashMap<Block.ShapePairKey>> OCCLUSION_CACHE = ThreadLocal.withInitial(() -> {
        Object2ByteLinkedOpenHashMap<Block.ShapePairKey> object2bytelinkedopenhashmap = new Object2ByteLinkedOpenHashMap<Block.ShapePairKey>(256, 0.25F) {
            protected void rehash(int newN) {}
        };

        object2bytelinkedopenhashmap.defaultReturnValue((byte) 127);
        return object2bytelinkedopenhashmap;
    });

    @Override
    protected MapCodec<? extends Block> codec() {
        return Block.CODEC;
    }

    public static int getId(@Nullable BlockState blockState) {
        if (blockState == null) {
            return 0;
        } else {
            int i = Block.BLOCK_STATE_REGISTRY.getId(blockState);

            return i == -1 ? 0 : i;
        }
    }

    public static BlockState stateById(int idWithData) {
        BlockState blockstate = (BlockState) Block.BLOCK_STATE_REGISTRY.byId(idWithData);

        return blockstate == null ? Blocks.AIR.defaultBlockState() : blockstate;
    }

    public static Block byItem(@Nullable Item item) {
        return item instanceof BlockItem ? ((BlockItem) item).getBlock() : Blocks.AIR;
    }

    public static BlockState pushEntitiesUp(BlockState state, BlockState newState, LevelAccessor level, BlockPos pos) {
        VoxelShape voxelshape = Shapes.joinUnoptimized(state.getCollisionShape(level, pos), newState.getCollisionShape(level, pos), BooleanOp.ONLY_SECOND).move((Vec3i) pos);

        if (voxelshape.isEmpty()) {
            return newState;
        } else {
            for (Entity entity : level.getEntities((Entity) null, voxelshape.bounds())) {
                double d0 = Shapes.collide(Direction.Axis.Y, entity.getBoundingBox().move(0.0D, 1.0D, 0.0D), List.of(voxelshape), -1.0D);

                entity.teleportRelative(0.0D, 1.0D + d0, 0.0D);
            }

            return newState;
        }
    }

    public static VoxelShape box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return Shapes.box(minX / 16.0D, minY / 16.0D, minZ / 16.0D, maxX / 16.0D, maxY / 16.0D, maxZ / 16.0D);
    }

    public static VoxelShape[] boxes(int endInclusive, IntFunction<VoxelShape> voxelShapeFactory) {
        return (VoxelShape[]) IntStream.rangeClosed(0, endInclusive).mapToObj(voxelShapeFactory).toArray((j) -> {
            return new VoxelShape[j];
        });
    }

    public static VoxelShape cube(double size) {
        return cube(size, size, size);
    }

    public static VoxelShape cube(double sizeX, double sizeY, double sizeZ) {
        double d3 = sizeY / 2.0D;

        return column(sizeX, sizeZ, 8.0D - d3, 8.0D + d3);
    }

    public static VoxelShape column(double sizeXZ, double minY, double maxY) {
        return column(sizeXZ, sizeXZ, minY, maxY);
    }

    public static VoxelShape column(double sizeX, double sizeZ, double minY, double maxY) {
        double d4 = sizeX / 2.0D;
        double d5 = sizeZ / 2.0D;

        return box(8.0D - d4, minY, 8.0D - d5, 8.0D + d4, maxY, 8.0D + d5);
    }

    public static VoxelShape boxZ(double sizeXY, double minZ, double maxZ) {
        return boxZ(sizeXY, sizeXY, minZ, maxZ);
    }

    public static VoxelShape boxZ(double sizeX, double sizeY, double minZ, double maxZ) {
        double d4 = sizeY / 2.0D;

        return boxZ(sizeX, 8.0D - d4, 8.0D + d4, minZ, maxZ);
    }

    public static VoxelShape boxZ(double sizeX, double minY, double maxY, double minZ, double maxZ) {
        double d5 = sizeX / 2.0D;

        return box(8.0D - d5, minY, minZ, 8.0D + d5, maxY, maxZ);
    }

    public static BlockState updateFromNeighbourShapes(BlockState state, LevelAccessor level, BlockPos pos) {
        BlockState blockstate1 = state;
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

        for (Direction direction : Block.UPDATE_SHAPE_ORDER) {
            blockpos_mutableblockpos.setWithOffset(pos, direction);
            blockstate1 = blockstate1.updateShape(level, level, pos, direction, blockpos_mutableblockpos, level.getBlockState(blockpos_mutableblockpos), level.getRandom());
        }

        return blockstate1;
    }

    public static void updateOrDestroy(BlockState blockState, BlockState newState, LevelAccessor level, BlockPos blockPos, @Block.UpdateFlags int updateFlags) {
        updateOrDestroy(blockState, newState, level, blockPos, updateFlags, 512);
    }

    public static void updateOrDestroy(BlockState blockState, BlockState newState, LevelAccessor level, BlockPos blockPos, @Block.UpdateFlags int updateFlags, int updateLimit) {
        if (newState != blockState) {
            if (newState.isAir()) {
                if (!level.isClientSide()) {
                    level.destroyBlock(blockPos, (updateFlags & 32) == 0, (Entity) null, updateLimit);
                }
            } else {
                level.setBlock(blockPos, newState, updateFlags & -33, updateLimit);
            }
        }

    }

    public Block(BlockBehaviour.Properties properties) {
        super(properties);
        this.builtInRegistryHolder = BuiltInRegistries.BLOCK.createIntrusiveHolder(this);
        StateDefinition.Builder<Block, BlockState> statedefinition_builder = new StateDefinition.Builder<Block, BlockState>(this);

        this.createBlockStateDefinition(statedefinition_builder);
        this.stateDefinition = statedefinition_builder.create(Block::defaultBlockState, BlockState::new);
        this.registerDefaultState(this.stateDefinition.any());
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            String s = this.getClass().getSimpleName();

            if (!s.endsWith("Block")) {
                Block.LOGGER.error("Block classes should end with Block and {} doesn't.", s);
            }
        }

    }

    public static boolean isExceptionForConnection(BlockState state) {
        return state.getBlock() instanceof LeavesBlock || state.is(Blocks.BARRIER) || state.is(Blocks.CARVED_PUMPKIN) || state.is(Blocks.JACK_O_LANTERN) || state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN) || state.is(BlockTags.SHULKER_BOXES);
    }

    protected static boolean dropFromBlockInteractLootTable(ServerLevel level, ResourceKey<LootTable> key, BlockState interactedBlockState, @Nullable BlockEntity interactedBlockEntity, @Nullable ItemStack tool, @Nullable Entity interactingEntity, BiConsumer<ServerLevel, ItemStack> consumer) {
        return dropFromLootTable(level, key, (lootparams_builder) -> {
            return lootparams_builder.withParameter(LootContextParams.BLOCK_STATE, interactedBlockState).withOptionalParameter(LootContextParams.BLOCK_ENTITY, interactedBlockEntity).withOptionalParameter(LootContextParams.INTERACTING_ENTITY, interactingEntity).withOptionalParameter(LootContextParams.TOOL, tool).create(LootContextParamSets.BLOCK_INTERACT);
        }, consumer);
    }

    protected static boolean dropFromLootTable(ServerLevel level, ResourceKey<LootTable> key, Function<LootParams.Builder, LootParams> paramsBuilder, BiConsumer<ServerLevel, ItemStack> consumer) {
        LootTable loottable = level.getServer().reloadableRegistries().getLootTable(key);
        LootParams lootparams = (LootParams) paramsBuilder.apply(new LootParams.Builder(level));
        List<ItemStack> list = loottable.getRandomItems(lootparams);

        if (!list.isEmpty()) {
            list.forEach((itemstack) -> {
                consumer.accept(level, itemstack);
            });
            return true;
        } else {
            return false;
        }
    }

    public static boolean shouldRenderFace(BlockState state, BlockState neighborState, Direction direction) {
        VoxelShape voxelshape = neighborState.getFaceOcclusionShape(direction.getOpposite());

        if (voxelshape == Shapes.block()) {
            return false;
        } else if (state.skipRendering(neighborState, direction)) {
            return false;
        } else if (voxelshape == Shapes.empty()) {
            return true;
        } else {
            VoxelShape voxelshape1 = state.getFaceOcclusionShape(direction);

            if (voxelshape1 == Shapes.empty()) {
                return true;
            } else {
                Block.ShapePairKey block_shapepairkey = new Block.ShapePairKey(voxelshape1, voxelshape);
                Object2ByteLinkedOpenHashMap<Block.ShapePairKey> object2bytelinkedopenhashmap = (Object2ByteLinkedOpenHashMap) Block.OCCLUSION_CACHE.get();
                byte b0 = object2bytelinkedopenhashmap.getAndMoveToFirst(block_shapepairkey);

                if (b0 != 127) {
                    return b0 != 0;
                } else {
                    boolean flag = Shapes.joinIsNotEmpty(voxelshape1, voxelshape, BooleanOp.ONLY_FIRST);

                    if (object2bytelinkedopenhashmap.size() == 256) {
                        object2bytelinkedopenhashmap.removeLastByte();
                    }

                    object2bytelinkedopenhashmap.putAndMoveToFirst(block_shapepairkey, (byte) (flag ? 1 : 0));
                    return flag;
                }
            }
        }
    }

    public static boolean canSupportRigidBlock(BlockGetter level, BlockPos below) {
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP, SupportType.RIGID);
    }

    public static boolean canSupportCenter(LevelReader level, BlockPos belowPos, Direction direction) {
        BlockState blockstate = level.getBlockState(belowPos);

        return direction == Direction.DOWN && blockstate.is(BlockTags.UNSTABLE_BOTTOM_CENTER) ? false : blockstate.isFaceSturdy(level, belowPos, direction, SupportType.CENTER);
    }

    public static boolean isFaceFull(VoxelShape shape, Direction direction) {
        VoxelShape voxelshape1 = shape.getFaceShape(direction);

        return isShapeFullBlock(voxelshape1);
    }

    public static boolean isShapeFullBlock(VoxelShape shape) {
        return (Boolean) Block.SHAPE_FULL_BLOCK_CACHE.getUnchecked(shape);
    }

    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {}

    public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {}

    public static List<ItemStack> getDrops(BlockState state, ServerLevel level, BlockPos pos, @Nullable BlockEntity blockEntity) {
        LootParams.Builder lootparams_builder = (new LootParams.Builder(level)).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos)).withParameter(LootContextParams.TOOL, ItemStack.EMPTY).withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockEntity);

        return state.getDrops(lootparams_builder);
    }

    public static List<ItemStack> getDrops(BlockState state, ServerLevel level, BlockPos pos, @Nullable BlockEntity blockEntity, @Nullable Entity breaker, ItemStack tool) {
        LootParams.Builder lootparams_builder = (new LootParams.Builder(level)).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos)).withParameter(LootContextParams.TOOL, tool).withOptionalParameter(LootContextParams.THIS_ENTITY, breaker).withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockEntity);

        return state.getDrops(lootparams_builder);
    }

    public static void dropResources(BlockState state, Level level, BlockPos pos) {
        if (level instanceof ServerLevel) {
            getDrops(state, (ServerLevel) level, pos, (BlockEntity) null).forEach((itemstack) -> {
                popResource(level, pos, itemstack);
            });
            state.spawnAfterBreak((ServerLevel) level, pos, ItemStack.EMPTY, true);
        }

    }

    public static void dropResources(BlockState state, LevelAccessor level, BlockPos pos, @Nullable BlockEntity blockEntity) {
        if (level instanceof ServerLevel) {
            getDrops(state, (ServerLevel) level, pos, blockEntity).forEach((itemstack) -> {
                popResource((ServerLevel) level, pos, itemstack);
            });
            state.spawnAfterBreak((ServerLevel) level, pos, ItemStack.EMPTY, true);
        }

    }

    public static void dropResources(BlockState state, Level level, BlockPos pos, @Nullable BlockEntity blockEntity, @Nullable Entity breaker, ItemStack tool) {
        if (level instanceof ServerLevel) {
            getDrops(state, (ServerLevel) level, pos, blockEntity, breaker, tool).forEach((itemstack1) -> {
                popResource(level, pos, itemstack1);
            });
            state.spawnAfterBreak((ServerLevel) level, pos, tool, true);
        }

    }

    public static void popResource(Level level, BlockPos pos, ItemStack itemStack) {
        double d0 = (double) EntityType.ITEM.getHeight() / 2.0D;
        double d1 = (double) pos.getX() + 0.5D + Mth.nextDouble(level.random, -0.25D, 0.25D);
        double d2 = (double) pos.getY() + 0.5D + Mth.nextDouble(level.random, -0.25D, 0.25D) - d0;
        double d3 = (double) pos.getZ() + 0.5D + Mth.nextDouble(level.random, -0.25D, 0.25D);

        popResource(level, () -> {
            return new ItemEntity(level, d1, d2, d3, itemStack);
        }, itemStack);
    }

    public static void popResourceFromFace(Level level, BlockPos pos, Direction face, ItemStack itemStack) {
        int i = face.getStepX();
        int j = face.getStepY();
        int k = face.getStepZ();
        double d0 = (double) EntityType.ITEM.getWidth() / 2.0D;
        double d1 = (double) EntityType.ITEM.getHeight() / 2.0D;
        double d2 = (double) pos.getX() + 0.5D + (i == 0 ? Mth.nextDouble(level.random, -0.25D, 0.25D) : (double) i * (0.5D + d0));
        double d3 = (double) pos.getY() + 0.5D + (j == 0 ? Mth.nextDouble(level.random, -0.25D, 0.25D) : (double) j * (0.5D + d1)) - d1;
        double d4 = (double) pos.getZ() + 0.5D + (k == 0 ? Mth.nextDouble(level.random, -0.25D, 0.25D) : (double) k * (0.5D + d0));
        double d5 = i == 0 ? Mth.nextDouble(level.random, -0.1D, 0.1D) : (double) i * 0.1D;
        double d6 = j == 0 ? Mth.nextDouble(level.random, 0.0D, 0.1D) : (double) j * 0.1D + 0.1D;
        double d7 = k == 0 ? Mth.nextDouble(level.random, -0.1D, 0.1D) : (double) k * 0.1D;

        popResource(level, () -> {
            return new ItemEntity(level, d2, d3, d4, itemStack, d5, d6, d7);
        }, itemStack);
    }

    private static void popResource(Level level, Supplier<ItemEntity> entityFactory, ItemStack itemStack) {
        if (level instanceof ServerLevel serverlevel) {
            if (!itemStack.isEmpty() && (Boolean) serverlevel.getGameRules().get(GameRules.BLOCK_DROPS)) {
                ItemEntity itementity = (ItemEntity) entityFactory.get();

                itementity.setDefaultPickUpDelay();
                level.addFreshEntity(itementity);
                return;
            }
        }

    }

    public void popExperience(ServerLevel level, BlockPos pos, int amount) {
        if ((Boolean) level.getGameRules().get(GameRules.BLOCK_DROPS)) {
            ExperienceOrb.award(level, Vec3.atCenterOf(pos), amount);
        }

    }

    public float getExplosionResistance() {
        return this.explosionResistance;
    }

    public void wasExploded(ServerLevel level, BlockPos pos, Explosion explosion) {}

    public void stepOn(Level level, BlockPos pos, BlockState onState, Entity entity) {}

    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState();
    }

    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack destroyedWith) {
        player.awardStat(Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005F);
        dropResources(state, level, pos, blockEntity, player, destroyedWith);
    }

    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {}

    public boolean isPossibleToRespawnInThis(BlockState state) {
        return !state.isSolid() && !state.liquid();
    }

    public MutableComponent getName() {
        return Component.translatable(this.getDescriptionId());
    }

    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
        entity.causeFallDamage(fallDistance, 1.0F, entity.damageSources().fall());
    }

    public void updateEntityMovementAfterFallOn(BlockGetter level, Entity entity) {
        entity.setDeltaMovement(entity.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
    }

    public float getFriction() {
        return this.friction;
    }

    public float getSpeedFactor() {
        return this.speedFactor;
    }

    public float getJumpFactor() {
        return this.jumpFactor;
    }

    protected void spawnDestroyParticles(Level level, Player player, BlockPos pos, BlockState state) {
        level.levelEvent(player, 2001, pos, getId(state));
    }

    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        this.spawnDestroyParticles(level, player, pos, state);
        if (state.is(BlockTags.GUARDED_BY_PIGLINS) && level instanceof ServerLevel serverlevel) {
            PiglinAi.angerNearbyPiglins(serverlevel, player, false);
        }

        level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(player, state));
        return state;
    }

    public void handlePrecipitation(BlockState state, Level level, BlockPos pos, Biome.Precipitation precipitation) {}

    public boolean dropFromExplosion(Explosion explosion) {
        return true;
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {}

    public StateDefinition<Block, BlockState> getStateDefinition() {
        return this.stateDefinition;
    }

    protected final void registerDefaultState(BlockState state) {
        this.defaultBlockState = state;
    }

    public final BlockState defaultBlockState() {
        return this.defaultBlockState;
    }

    public final BlockState withPropertiesOf(BlockState source) {
        BlockState blockstate1 = this.defaultBlockState();

        for (Property<?> property : source.getBlock().getStateDefinition().getProperties()) {
            if (blockstate1.hasProperty(property)) {
                blockstate1 = copyProperty(source, blockstate1, property);
            }
        }

        return blockstate1;
    }

    private static <T extends Comparable<T>> BlockState copyProperty(BlockState from, BlockState to, Property<T> property) {
        return (BlockState) to.setValue(property, from.getValue(property));
    }

    @Override
    public Item asItem() {
        if (this.item == null) {
            this.item = Item.byBlock(this);
        }

        return this.item;
    }

    public boolean hasDynamicShape() {
        return this.dynamicShape;
    }

    public String toString() {
        return "Block{" + BuiltInRegistries.BLOCK.wrapAsHolder(this).getRegisteredName() + "}";
    }

    @Override
    protected Block asBlock() {
        return this;
    }

    protected Function<BlockState, VoxelShape> getShapeForEachState(Function<BlockState, VoxelShape> shapeCalculator) {
        ImmutableMap immutablemap = (ImmutableMap) this.stateDefinition.getPossibleStates().stream().collect(ImmutableMap.toImmutableMap(Function.identity(), shapeCalculator));

        Objects.requireNonNull(immutablemap);
        return immutablemap::get;
    }

    protected Function<BlockState, VoxelShape> getShapeForEachState(Function<BlockState, VoxelShape> shapeCalculator, Property<?>... ignoredProperties) {
        Map<? extends Property<?>, Object> map = (Map) Arrays.stream(ignoredProperties).collect(Collectors.toMap((property) -> {
            return property;
        }, (property) -> {
            return property.getPossibleValues().getFirst();
        }));
        ImmutableMap<BlockState, VoxelShape> immutablemap = (ImmutableMap) this.stateDefinition.getPossibleStates().stream().filter((blockstate) -> {
            return map.entrySet().stream().allMatch((entry) -> {
                return blockstate.getValue((Property) entry.getKey()) == entry.getValue();
            });
        }).collect(ImmutableMap.toImmutableMap(Function.identity(), shapeCalculator));

        return (blockstate) -> {
            for (Map.Entry<? extends Property<?>, Object> map_entry : map.entrySet()) {
                blockstate = (BlockState) setValueHelper(blockstate, (Property) map_entry.getKey(), map_entry.getValue());
            }

            return (VoxelShape) immutablemap.get(blockstate);
        };
    }

    private static <S extends StateHolder<?, S>, T extends Comparable<T>> S setValueHelper(S state, Property<T> property, Object value) {
        return (S) (((StateHolder) state).setValue(property, (Comparable) value));
    }

    /** @deprecated */
    @Deprecated
    public Holder.Reference<Block> builtInRegistryHolder() {
        return this.builtInRegistryHolder;
    }

    protected void tryDropExperience(ServerLevel level, BlockPos pos, ItemStack tool, IntProvider xpRange) {
        int i = EnchantmentHelper.processBlockExperience(level, tool, xpRange.sample(level.getRandom()));

        if (i > 0) {
            this.popExperience(level, pos, i);
        }

    }

    private static record ShapePairKey(VoxelShape first, VoxelShape second) {

        public boolean equals(Object o) {
            boolean flag;

            if (o instanceof Block.ShapePairKey block_shapepairkey) {
                if (this.first == block_shapepairkey.first && this.second == block_shapepairkey.second) {
                    flag = true;
                    return flag;
                }
            }

            flag = false;
            return flag;
        }

        public int hashCode() {
            return System.identityHashCode(this.first) * 31 + System.identityHashCode(this.second);
        }
    }

    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.METHOD, ElementType.TYPE_USE})
    public @interface UpdateFlags {
    }
}
