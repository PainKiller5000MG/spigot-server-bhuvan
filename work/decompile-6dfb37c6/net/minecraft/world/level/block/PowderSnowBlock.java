package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.InsideBlockEffectType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class PowderSnowBlock extends Block implements BucketPickup {

    public static final MapCodec<PowderSnowBlock> CODEC = simpleCodec(PowderSnowBlock::new);
    private static final float HORIZONTAL_PARTICLE_MOMENTUM_FACTOR = 0.083333336F;
    private static final float IN_BLOCK_HORIZONTAL_SPEED_MULTIPLIER = 0.9F;
    private static final float IN_BLOCK_VERTICAL_SPEED_MULTIPLIER = 1.5F;
    private static final float NUM_BLOCKS_TO_FALL_INTO_BLOCK = 2.5F;
    private static final VoxelShape FALLING_COLLISION_SHAPE = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, (double) 0.9F, 1.0D);
    private static final double MINIMUM_FALL_DISTANCE_FOR_SOUND = 4.0D;
    private static final double MINIMUM_FALL_DISTANCE_FOR_BIG_SOUND = 7.0D;

    @Override
    public MapCodec<PowderSnowBlock> codec() {
        return PowderSnowBlock.CODEC;
    }

    public PowderSnowBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState neighborState, Direction direction) {
        return neighborState.is(this) ? true : super.skipRendering(state, neighborState, direction);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (!(entity instanceof LivingEntity) || entity.getInBlockState().is(this)) {
            entity.makeStuckInBlock(state, new Vec3((double) 0.9F, 1.5D, (double) 0.9F));
            if (level.isClientSide()) {
                RandomSource randomsource = level.getRandom();
                boolean flag1 = entity.xOld != entity.getX() || entity.zOld != entity.getZ();

                if (flag1 && randomsource.nextBoolean()) {
                    level.addParticle(ParticleTypes.SNOWFLAKE, entity.getX(), (double) (pos.getY() + 1), entity.getZ(), (double) (Mth.randomBetween(randomsource, -1.0F, 1.0F) * 0.083333336F), (double) 0.05F, (double) (Mth.randomBetween(randomsource, -1.0F, 1.0F) * 0.083333336F));
                }
            }
        }

        BlockPos blockpos1 = pos.immutable();

        effectApplier.runBefore(InsideBlockEffectType.EXTINGUISH, (entity1) -> {
            if (level instanceof ServerLevel serverlevel) {
                if (entity1.isOnFire() && ((Boolean) serverlevel.getGameRules().get(GameRules.MOB_GRIEFING) || entity1 instanceof Player) && entity1.mayInteract(serverlevel, blockpos1)) {
                    level.destroyBlock(blockpos1, false);
                }
            }

        });
        effectApplier.apply(InsideBlockEffectType.FREEZE);
        effectApplier.apply(InsideBlockEffectType.EXTINGUISH);
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
        if (fallDistance >= 4.0D && entity instanceof LivingEntity livingentity) {
            LivingEntity.Fallsounds livingentity_fallsounds = livingentity.getFallSounds();
            SoundEvent soundevent = fallDistance < 7.0D ? livingentity_fallsounds.small() : livingentity_fallsounds.big();

            entity.playSound(soundevent, 1.0F, 1.0F);
        }
    }

    @Override
    protected VoxelShape getEntityInsideCollisionShape(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        VoxelShape voxelshape = this.getCollisionShape(state, level, pos, CollisionContext.of(entity));

        return voxelshape.isEmpty() ? Shapes.block() : voxelshape;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!context.isPlacement() && context instanceof EntityCollisionContext entitycollisioncontext) {
            Entity entity = entitycollisioncontext.getEntity();

            if (entity != null) {
                if (entity.fallDistance > 2.5D) {
                    return PowderSnowBlock.FALLING_COLLISION_SHAPE;
                }

                boolean flag = entity instanceof FallingBlockEntity;

                if (flag || canEntityWalkOnPowderSnow(entity) && context.isAbove(Shapes.block(), pos, false) && !context.isDescending()) {
                    return super.getCollisionShape(state, level, pos, context);
                }
            }
        }

        return Shapes.empty();
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    public static boolean canEntityWalkOnPowderSnow(Entity entity) {
        return entity.getType().is(EntityTypeTags.POWDER_SNOW_WALKABLE_MOBS) ? true : (entity instanceof LivingEntity ? ((LivingEntity) entity).getItemBySlot(EquipmentSlot.FEET).is(Items.LEATHER_BOOTS) : false);
    }

    @Override
    public ItemStack pickupBlock(@Nullable LivingEntity user, LevelAccessor level, BlockPos pos, BlockState state) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
        if (!level.isClientSide()) {
            level.levelEvent(2001, pos, Block.getId(state));
        }

        return new ItemStack(Items.POWDER_SNOW_BUCKET);
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Optional.of(SoundEvents.BUCKET_FILL_POWDER_SNOW);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return true;
    }
}
