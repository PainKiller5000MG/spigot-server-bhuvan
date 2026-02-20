package net.minecraft.world.level.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class CampfireBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {

    public static final MapCodec<CampfireBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.BOOL.fieldOf("spawn_particles").forGetter((campfireblock) -> {
            return campfireblock.spawnParticles;
        }), Codec.intRange(0, 1000).fieldOf("fire_damage").forGetter((campfireblock) -> {
            return campfireblock.fireDamage;
        }), propertiesCodec()).apply(instance, CampfireBlock::new);
    });
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty SIGNAL_FIRE = BlockStateProperties.SIGNAL_FIRE;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.column(16.0D, 0.0D, 7.0D);
    private static final VoxelShape SHAPE_VIRTUAL_POST = Block.column(4.0D, 0.0D, 16.0D);
    private static final int SMOKE_DISTANCE = 5;
    private final boolean spawnParticles;
    private final int fireDamage;

    @Override
    public MapCodec<CampfireBlock> codec() {
        return CampfireBlock.CODEC;
    }

    public CampfireBlock(boolean spawnParticles, int fireDamage, BlockBehaviour.Properties properties) {
        super(properties);
        this.spawnParticles = spawnParticles;
        this.fireDamage = fireDamage;
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(CampfireBlock.LIT, true)).setValue(CampfireBlock.SIGNAL_FIRE, false)).setValue(CampfireBlock.WATERLOGGED, false)).setValue(CampfireBlock.FACING, Direction.NORTH));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof CampfireBlockEntity campfireblockentity) {
            ItemStack itemstack1 = player.getItemInHand(hand);

            if (level.recipeAccess().propertySet(RecipePropertySet.CAMPFIRE_INPUT).test(itemstack1)) {
                if (level instanceof ServerLevel) {
                    ServerLevel serverlevel = (ServerLevel) level;

                    if (campfireblockentity.placeFood(serverlevel, player, itemstack1)) {
                        player.awardStat(Stats.INTERACT_WITH_CAMPFIRE);
                        return InteractionResult.SUCCESS_SERVER;
                    }
                }

                return InteractionResult.CONSUME;
            }
        }

        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if ((Boolean) state.getValue(CampfireBlock.LIT) && entity instanceof LivingEntity) {
            entity.hurt(level.damageSources().campfire(), (float) this.fireDamage);
        }

        super.entityInside(state, level, pos, entity, effectApplier, isPrecise);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelAccessor levelaccessor = context.getLevel();
        BlockPos blockpos = context.getClickedPos();
        boolean flag = levelaccessor.getFluidState(blockpos).getType() == Fluids.WATER;

        return (BlockState) ((BlockState) ((BlockState) ((BlockState) this.defaultBlockState().setValue(CampfireBlock.WATERLOGGED, flag)).setValue(CampfireBlock.SIGNAL_FIRE, this.isSmokeSource(levelaccessor.getBlockState(blockpos.below())))).setValue(CampfireBlock.LIT, !flag)).setValue(CampfireBlock.FACING, context.getHorizontalDirection());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if ((Boolean) state.getValue(CampfireBlock.WATERLOGGED)) {
            ticks.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return directionToNeighbour == Direction.DOWN ? (BlockState) state.setValue(CampfireBlock.SIGNAL_FIRE, this.isSmokeSource(neighbourState)) : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    private boolean isSmokeSource(BlockState blockState) {
        return blockState.is(Blocks.HAY_BLOCK);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CampfireBlock.SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if ((Boolean) state.getValue(CampfireBlock.LIT)) {
            if (random.nextInt(10) == 0) {
                level.playLocalSound((double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.5F + random.nextFloat(), random.nextFloat() * 0.7F + 0.6F, false);
            }

            if (this.spawnParticles && random.nextInt(5) == 0) {
                for (int i = 0; i < random.nextInt(1) + 1; ++i) {
                    level.addParticle(ParticleTypes.LAVA, (double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, (double) (random.nextFloat() / 2.0F), 5.0E-5D, (double) (random.nextFloat() / 2.0F));
                }
            }

        }
    }

    public static void dowse(@Nullable Entity source, LevelAccessor level, BlockPos pos, BlockState state) {
        if (level.isClientSide()) {
            for (int i = 0; i < 20; ++i) {
                makeParticles((Level) level, pos, (Boolean) state.getValue(CampfireBlock.SIGNAL_FIRE), true);
            }
        }

        level.gameEvent(source, (Holder) GameEvent.BLOCK_CHANGE, pos);
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        if (!(Boolean) state.getValue(BlockStateProperties.WATERLOGGED) && fluidState.getType() == Fluids.WATER) {
            boolean flag = (Boolean) state.getValue(CampfireBlock.LIT);

            if (flag) {
                if (!level.isClientSide()) {
                    level.playSound((Entity) null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1.0F, 1.0F);
                }

                dowse((Entity) null, level, pos, state);
            }

            level.setBlock(pos, (BlockState) ((BlockState) state.setValue(CampfireBlock.WATERLOGGED, true)).setValue(CampfireBlock.LIT, false), 3);
            level.scheduleTick(pos, fluidState.getType(), fluidState.getType().getTickDelay(level));
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult blockHit, Projectile projectile) {
        BlockPos blockpos = blockHit.getBlockPos();

        if (level instanceof ServerLevel serverlevel) {
            if (projectile.isOnFire() && projectile.mayInteract(serverlevel, blockpos) && !(Boolean) state.getValue(CampfireBlock.LIT) && !(Boolean) state.getValue(CampfireBlock.WATERLOGGED)) {
                level.setBlock(blockpos, (BlockState) state.setValue(BlockStateProperties.LIT, true), 11);
            }
        }

    }

    public static void makeParticles(Level level, BlockPos pos, boolean isSignalFire, boolean smoking) {
        RandomSource randomsource = level.getRandom();
        SimpleParticleType simpleparticletype = isSignalFire ? ParticleTypes.CAMPFIRE_SIGNAL_SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE;

        level.addAlwaysVisibleParticle(simpleparticletype, true, (double) pos.getX() + 0.5D + randomsource.nextDouble() / 3.0D * (double) (randomsource.nextBoolean() ? 1 : -1), (double) pos.getY() + randomsource.nextDouble() + randomsource.nextDouble(), (double) pos.getZ() + 0.5D + randomsource.nextDouble() / 3.0D * (double) (randomsource.nextBoolean() ? 1 : -1), 0.0D, 0.07D, 0.0D);
        if (smoking) {
            level.addParticle(ParticleTypes.SMOKE, (double) pos.getX() + 0.5D + randomsource.nextDouble() / 4.0D * (double) (randomsource.nextBoolean() ? 1 : -1), (double) pos.getY() + 0.4D, (double) pos.getZ() + 0.5D + randomsource.nextDouble() / 4.0D * (double) (randomsource.nextBoolean() ? 1 : -1), 0.0D, 0.005D, 0.0D);
        }

    }

    public static boolean isSmokeyPos(Level level, BlockPos pos) {
        for (int i = 1; i <= 5; ++i) {
            BlockPos blockpos1 = pos.below(i);
            BlockState blockstate = level.getBlockState(blockpos1);

            if (isLitCampfire(blockstate)) {
                return true;
            }

            boolean flag = Shapes.joinIsNotEmpty(CampfireBlock.SHAPE_VIRTUAL_POST, blockstate.getCollisionShape(level, pos, CollisionContext.empty()), BooleanOp.AND);

            if (flag) {
                BlockState blockstate1 = level.getBlockState(blockpos1.below());

                return isLitCampfire(blockstate1);
            }
        }

        return false;
    }

    public static boolean isLitCampfire(BlockState blockState) {
        return blockState.hasProperty(CampfireBlock.LIT) && blockState.is(BlockTags.CAMPFIRES) && (Boolean) blockState.getValue(CampfireBlock.LIT);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(CampfireBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(CampfireBlock.FACING, rotation.rotate((Direction) state.getValue(CampfireBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(CampfireBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CampfireBlock.LIT, CampfireBlock.SIGNAL_FIRE, CampfireBlock.WATERLOGGED, CampfireBlock.FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new CampfireBlockEntity(worldPosition, blockState);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> type) {
        if (level instanceof ServerLevel serverlevel) {
            if ((Boolean) blockState.getValue(CampfireBlock.LIT)) {
                RecipeManager.CachedCheck<SingleRecipeInput, CampfireCookingRecipe> recipemanager_cachedcheck = RecipeManager.<SingleRecipeInput, CampfireCookingRecipe>createCheck(RecipeType.CAMPFIRE_COOKING);

                return createTickerHelper(type, BlockEntityType.CAMPFIRE, (level1, blockpos, blockstate1, campfireblockentity) -> {
                    CampfireBlockEntity.cookTick(serverlevel, blockpos, blockstate1, campfireblockentity, recipemanager_cachedcheck);
                });
            } else {
                return createTickerHelper(type, BlockEntityType.CAMPFIRE, CampfireBlockEntity::cooldownTick);
            }
        } else {
            return (Boolean) blockState.getValue(CampfireBlock.LIT) ? createTickerHelper(type, BlockEntityType.CAMPFIRE, CampfireBlockEntity::particleTick) : null;
        }
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    public static boolean canLight(BlockState state) {
        return state.is(BlockTags.CAMPFIRES, (blockbehaviour_blockstatebase) -> {
            return blockbehaviour_blockstatebase.hasProperty(CampfireBlock.WATERLOGGED) && blockbehaviour_blockstatebase.hasProperty(CampfireBlock.LIT);
        }) && !(Boolean) state.getValue(CampfireBlock.WATERLOGGED) && !(Boolean) state.getValue(CampfireBlock.LIT);
    }
}
