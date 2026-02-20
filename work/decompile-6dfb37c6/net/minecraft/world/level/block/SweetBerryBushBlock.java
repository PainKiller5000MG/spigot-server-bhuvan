package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SweetBerryBushBlock extends VegetationBlock implements BonemealableBlock {

    public static final MapCodec<SweetBerryBushBlock> CODEC = simpleCodec(SweetBerryBushBlock::new);
    private static final float HURT_SPEED_THRESHOLD = 0.003F;
    public static final int MAX_AGE = 3;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    private static final VoxelShape SHAPE_SAPLING = Block.column(10.0D, 0.0D, 8.0D);
    private static final VoxelShape SHAPE_GROWING = Block.column(14.0D, 0.0D, 16.0D);

    @Override
    public MapCodec<SweetBerryBushBlock> codec() {
        return SweetBerryBushBlock.CODEC;
    }

    public SweetBerryBushBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) (this.stateDefinition.any()).setValue(SweetBerryBushBlock.AGE, 0));
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(Items.SWEET_BERRIES);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape voxelshape;

        switch ((Integer) state.getValue(SweetBerryBushBlock.AGE)) {
            case 0:
                voxelshape = SweetBerryBushBlock.SHAPE_SAPLING;
                break;
            case 3:
                voxelshape = Shapes.block();
                break;
            default:
                voxelshape = SweetBerryBushBlock.SHAPE_GROWING;
        }

        return voxelshape;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return (Integer) state.getValue(SweetBerryBushBlock.AGE) < 3;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int i = (Integer) state.getValue(SweetBerryBushBlock.AGE);

        if (i < 3 && random.nextInt(5) == 0 && level.getRawBrightness(pos.above(), 0) >= 9) {
            BlockState blockstate1 = (BlockState) state.setValue(SweetBerryBushBlock.AGE, i + 1);

            level.setBlock(pos, blockstate1, 2);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(blockstate1));
        }

    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (entity instanceof LivingEntity && entity.getType() != EntityType.FOX && entity.getType() != EntityType.BEE) {
            entity.makeStuckInBlock(state, new Vec3((double) 0.8F, 0.75D, (double) 0.8F));
            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                if ((Integer) state.getValue(SweetBerryBushBlock.AGE) != 0) {
                    Vec3 vec3 = entity.isClientAuthoritative() ? entity.getKnownMovement() : entity.oldPosition().subtract(entity.position());

                    if (vec3.horizontalDistanceSqr() > 0.0D) {
                        double d0 = Math.abs(vec3.x());
                        double d1 = Math.abs(vec3.z());

                        if (d0 >= (double) 0.003F || d1 >= (double) 0.003F) {
                            entity.hurtServer(serverlevel, level.damageSources().sweetBerryBush(), 1.0F);
                        }
                    }

                    return;
                }
            }

        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        int i = (Integer) state.getValue(SweetBerryBushBlock.AGE);
        boolean flag = i == 3;

        return (InteractionResult) (!flag && itemStack.is(Items.BONE_MEAL) ? InteractionResult.PASS : super.useItemOn(itemStack, state, level, pos, player, hand, hitResult));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if ((Integer) state.getValue(SweetBerryBushBlock.AGE) > 1) {
            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                Block.dropFromBlockInteractLootTable(serverlevel, BuiltInLootTables.HARVEST_SWEET_BERRY_BUSH, state, level.getBlockEntity(pos), (ItemStack) null, player, (serverlevel1, itemstack) -> {
                    Block.popResource(serverlevel1, pos, itemstack);
                });
                serverlevel.playSound((Entity) null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + serverlevel.random.nextFloat() * 0.4F);
                BlockState blockstate1 = (BlockState) state.setValue(SweetBerryBushBlock.AGE, 1);

                serverlevel.setBlock(pos, blockstate1, 2);
                serverlevel.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, blockstate1));
            }

            return InteractionResult.SUCCESS;
        } else {
            return super.useWithoutItem(state, level, pos, player, hitResult);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SweetBerryBushBlock.AGE);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return (Integer) state.getValue(SweetBerryBushBlock.AGE) < 3;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        int i = Math.min(3, (Integer) state.getValue(SweetBerryBushBlock.AGE) + 1);

        level.setBlock(pos, (BlockState) state.setValue(SweetBerryBushBlock.AGE, i), 2);
    }
}
