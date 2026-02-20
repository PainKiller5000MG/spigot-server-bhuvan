package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.serialization.MapCodec;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class RespawnAnchorBlock extends Block {

    public static final MapCodec<RespawnAnchorBlock> CODEC = simpleCodec(RespawnAnchorBlock::new);
    public static final int MIN_CHARGES = 0;
    public static final int MAX_CHARGES = 4;
    public static final IntegerProperty CHARGE = BlockStateProperties.RESPAWN_ANCHOR_CHARGES;
    private static final ImmutableList<Vec3i> RESPAWN_HORIZONTAL_OFFSETS = ImmutableList.of(new Vec3i(0, 0, -1), new Vec3i(-1, 0, 0), new Vec3i(0, 0, 1), new Vec3i(1, 0, 0), new Vec3i(-1, 0, -1), new Vec3i(1, 0, -1), new Vec3i(-1, 0, 1), new Vec3i(1, 0, 1));
    private static final ImmutableList<Vec3i> RESPAWN_OFFSETS = (new Builder()).addAll(RespawnAnchorBlock.RESPAWN_HORIZONTAL_OFFSETS).addAll(RespawnAnchorBlock.RESPAWN_HORIZONTAL_OFFSETS.stream().map(Vec3i::below).iterator()).addAll(RespawnAnchorBlock.RESPAWN_HORIZONTAL_OFFSETS.stream().map(Vec3i::above).iterator()).add(new Vec3i(0, 1, 0)).build();

    @Override
    public MapCodec<RespawnAnchorBlock> codec() {
        return RespawnAnchorBlock.CODEC;
    }

    public RespawnAnchorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) (this.stateDefinition.any()).setValue(RespawnAnchorBlock.CHARGE, 0));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (isRespawnFuel(itemStack) && canBeCharged(state)) {
            charge(player, level, pos, state);
            itemStack.consume(1, player);
            return InteractionResult.SUCCESS;
        } else {
            return (InteractionResult) (hand == InteractionHand.MAIN_HAND && isRespawnFuel(player.getItemInHand(InteractionHand.OFF_HAND)) && canBeCharged(state) ? InteractionResult.PASS : InteractionResult.TRY_WITH_EMPTY_HAND);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if ((Integer) state.getValue(RespawnAnchorBlock.CHARGE) == 0) {
            return InteractionResult.PASS;
        } else if (level instanceof ServerLevel) {
            ServerLevel serverlevel = (ServerLevel) level;

            if (!canSetSpawn(serverlevel, pos)) {
                this.explode(state, serverlevel, pos);
                return InteractionResult.SUCCESS_SERVER;
            } else {
                if (player instanceof ServerPlayer) {
                    ServerPlayer serverplayer = (ServerPlayer) player;
                    ServerPlayer.RespawnConfig serverplayer_respawnconfig = serverplayer.getRespawnConfig();
                    ServerPlayer.RespawnConfig serverplayer_respawnconfig1 = new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(serverlevel.dimension(), pos, 0.0F, 0.0F), false);

                    if (serverplayer_respawnconfig == null || !serverplayer_respawnconfig.isSamePosition(serverplayer_respawnconfig1)) {
                        serverplayer.setRespawnPosition(serverplayer_respawnconfig1, true);
                        serverlevel.playSound((Entity) null, (double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 1.0F, 1.0F);
                        return InteractionResult.SUCCESS_SERVER;
                    }
                }

                return InteractionResult.CONSUME;
            }
        } else {
            return InteractionResult.CONSUME;
        }
    }

    private static boolean isRespawnFuel(ItemStack itemInHand) {
        return itemInHand.is(Items.GLOWSTONE);
    }

    private static boolean canBeCharged(BlockState state) {
        return (Integer) state.getValue(RespawnAnchorBlock.CHARGE) < 4;
    }

    private static boolean isWaterThatWouldFlow(BlockPos pos, Level level) {
        FluidState fluidstate = level.getFluidState(pos);

        if (!fluidstate.is(FluidTags.WATER)) {
            return false;
        } else if (fluidstate.isSource()) {
            return true;
        } else {
            float f = (float) fluidstate.getAmount();

            if (f < 2.0F) {
                return false;
            } else {
                FluidState fluidstate1 = level.getFluidState(pos.below());

                return !fluidstate1.is(FluidTags.WATER);
            }
        }
    }

    private void explode(BlockState state, ServerLevel level, final BlockPos pos) {
        level.removeBlock(pos, false);
        Stream stream = Direction.Plane.HORIZONTAL.stream();

        Objects.requireNonNull(pos);
        boolean flag = stream.map(pos::relative).anyMatch((blockpos1) -> {
            return isWaterThatWouldFlow(blockpos1, level);
        });
        final boolean flag1 = flag || level.getFluidState(pos.above()).is(FluidTags.WATER);
        ExplosionDamageCalculator explosiondamagecalculator = new ExplosionDamageCalculator() {
            @Override
            public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter level, BlockPos testPos, BlockState block, FluidState fluid) {
                return testPos.equals(pos) && flag1 ? Optional.of(Blocks.WATER.getExplosionResistance()) : super.getBlockExplosionResistance(explosion, level, testPos, block, fluid);
            }
        };
        Vec3 vec3 = pos.getCenter();

        level.explode((Entity) null, level.damageSources().badRespawnPointExplosion(vec3), explosiondamagecalculator, vec3, 5.0F, true, Level.ExplosionInteraction.BLOCK);
    }

    public static boolean canSetSpawn(ServerLevel level, BlockPos pos) {
        return (Boolean) level.environmentAttributes().getValue(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, pos);
    }

    public static void charge(@Nullable Entity sourceEntity, Level level, BlockPos pos, BlockState state) {
        BlockState blockstate1 = (BlockState) state.setValue(RespawnAnchorBlock.CHARGE, (Integer) state.getValue(RespawnAnchorBlock.CHARGE) + 1);

        level.setBlock(pos, blockstate1, 3);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(sourceEntity, blockstate1));
        level.playSound((Entity) null, (double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if ((Integer) state.getValue(RespawnAnchorBlock.CHARGE) != 0) {
            if (random.nextInt(100) == 0) {
                level.playLocalSound(pos, SoundEvents.RESPAWN_ANCHOR_AMBIENT, SoundSource.BLOCKS, 1.0F, 1.0F, false);
            }

            double d0 = (double) pos.getX() + 0.5D + (0.5D - random.nextDouble());
            double d1 = (double) pos.getY() + 1.0D;
            double d2 = (double) pos.getZ() + 0.5D + (0.5D - random.nextDouble());
            double d3 = (double) random.nextFloat() * 0.04D;

            level.addParticle(ParticleTypes.REVERSE_PORTAL, d0, d1, d2, 0.0D, d3, 0.0D);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RespawnAnchorBlock.CHARGE);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    public static int getScaledChargeLevel(BlockState state, int maximum) {
        return Mth.floor((float) ((Integer) state.getValue(RespawnAnchorBlock.CHARGE) - 0) / 4.0F * (float) maximum);
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return getScaledChargeLevel(state, 15);
    }

    public static Optional<Vec3> findStandUpPosition(EntityType<?> type, CollisionGetter level, BlockPos pos) {
        Optional<Vec3> optional = findStandUpPosition(type, level, pos, true);

        return optional.isPresent() ? optional : findStandUpPosition(type, level, pos, false);
    }

    private static Optional<Vec3> findStandUpPosition(EntityType<?> type, CollisionGetter level, BlockPos pos, boolean checkDangerous) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
        UnmodifiableIterator unmodifiableiterator = RespawnAnchorBlock.RESPAWN_OFFSETS.iterator();

        while (unmodifiableiterator.hasNext()) {
            Vec3i vec3i = (Vec3i) unmodifiableiterator.next();

            blockpos_mutableblockpos.set(pos).move(vec3i);
            Vec3 vec3 = DismountHelper.findSafeDismountLocation(type, level, blockpos_mutableblockpos, checkDangerous);

            if (vec3 != null) {
                return Optional.of(vec3);
            }
        }

        return Optional.empty();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
