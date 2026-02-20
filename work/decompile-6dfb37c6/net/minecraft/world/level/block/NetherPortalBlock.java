package net.minecraft.world.level.block;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.BlockUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class NetherPortalBlock extends Block implements Portal {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<NetherPortalBlock> CODEC = simpleCodec(NetherPortalBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    private static final Map<Direction.Axis, VoxelShape> SHAPES = Shapes.rotateHorizontalAxis(Block.column(4.0D, 16.0D, 0.0D, 16.0D));

    @Override
    public MapCodec<NetherPortalBlock> codec() {
        return NetherPortalBlock.CODEC;
    }

    public NetherPortalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) (this.stateDefinition.any()).setValue(NetherPortalBlock.AXIS, Direction.Axis.X));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (VoxelShape) NetherPortalBlock.SHAPES.get(state.getValue(NetherPortalBlock.AXIS));
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.isSpawningMonsters() && (Boolean) level.environmentAttributes().getValue(EnvironmentAttributes.NETHER_PORTAL_SPAWNS_PIGLINS, pos) && random.nextInt(2000) < level.getDifficulty().getId() && level.anyPlayerCloseEnoughForSpawning(pos)) {
            while (level.getBlockState(pos).is(this)) {
                pos = pos.below();
            }

            if (level.getBlockState(pos).isValidSpawn(level, pos, EntityType.ZOMBIFIED_PIGLIN)) {
                Entity entity = EntityType.ZOMBIFIED_PIGLIN.spawn(level, pos.above(), EntitySpawnReason.STRUCTURE);

                if (entity != null) {
                    entity.setPortalCooldown();
                    Entity entity1 = entity.getVehicle();

                    if (entity1 != null) {
                        entity1.setPortalCooldown();
                    }
                }
            }
        }

    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        Direction.Axis direction_axis = directionToNeighbour.getAxis();
        Direction.Axis direction_axis1 = (Direction.Axis) state.getValue(NetherPortalBlock.AXIS);
        boolean flag = direction_axis1 != direction_axis && direction_axis.isHorizontal();

        return !flag && !neighbourState.is(this) && !PortalShape.findAnyShape(level, pos, direction_axis1).isComplete() ? Blocks.AIR.defaultBlockState() : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (entity.canUsePortal(false)) {
            entity.setAsInsidePortal(this, pos);
        }

    }

    @Override
    public int getPortalTransitionTime(ServerLevel level, Entity entity) {
        if (entity instanceof Player player) {
            return Math.max(0, (Integer) level.getGameRules().get(player.getAbilities().invulnerable ? GameRules.PLAYERS_NETHER_PORTAL_CREATIVE_DELAY : GameRules.PLAYERS_NETHER_PORTAL_DEFAULT_DELAY));
        } else {
            return 0;
        }
    }

    @Override
    public @Nullable TeleportTransition getPortalDestination(ServerLevel currentLevel, Entity entity, BlockPos portalEntryPos) {
        ResourceKey<Level> resourcekey = currentLevel.dimension() == Level.NETHER ? Level.OVERWORLD : Level.NETHER;
        ServerLevel serverlevel1 = currentLevel.getServer().getLevel(resourcekey);

        if (serverlevel1 == null) {
            return null;
        } else {
            boolean flag = serverlevel1.dimension() == Level.NETHER;
            WorldBorder worldborder = serverlevel1.getWorldBorder();
            double d0 = DimensionType.getTeleportationScale(currentLevel.dimensionType(), serverlevel1.dimensionType());
            BlockPos blockpos1 = worldborder.clampToBounds(entity.getX() * d0, entity.getY(), entity.getZ() * d0);

            return this.getExitPortal(serverlevel1, entity, portalEntryPos, blockpos1, flag, worldborder);
        }
    }

    private @Nullable TeleportTransition getExitPortal(ServerLevel newLevel, Entity entity, BlockPos portalEntryPos, BlockPos approximateExitPos, boolean toNether, WorldBorder worldBorder) {
        Optional<BlockPos> optional = newLevel.getPortalForcer().findClosestPortalPosition(approximateExitPos, toNether, worldBorder);
        BlockUtil.FoundRectangle blockutil_foundrectangle;
        TeleportTransition.PostTeleportTransition teleporttransition_postteleporttransition;

        if (optional.isPresent()) {
            BlockPos blockpos2 = (BlockPos) optional.get();
            BlockState blockstate = newLevel.getBlockState(blockpos2);

            blockutil_foundrectangle = BlockUtil.getLargestRectangleAround(blockpos2, (Direction.Axis) blockstate.getValue(BlockStateProperties.HORIZONTAL_AXIS), 21, Direction.Axis.Y, 21, (blockpos3) -> {
                return newLevel.getBlockState(blockpos3) == blockstate;
            });
            teleporttransition_postteleporttransition = TeleportTransition.PLAY_PORTAL_SOUND.then((entity1) -> {
                entity1.placePortalTicket(blockpos2);
            });
        } else {
            Direction.Axis direction_axis = (Direction.Axis) entity.level().getBlockState(portalEntryPos).getOptionalValue(NetherPortalBlock.AXIS).orElse(Direction.Axis.X);
            Optional<BlockUtil.FoundRectangle> optional1 = newLevel.getPortalForcer().createPortal(approximateExitPos, direction_axis);

            if (optional1.isEmpty()) {
                NetherPortalBlock.LOGGER.error("Unable to create a portal, likely target out of worldborder");
                return null;
            }

            blockutil_foundrectangle = (BlockUtil.FoundRectangle) optional1.get();
            teleporttransition_postteleporttransition = TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET);
        }

        return getDimensionTransitionFromExit(entity, portalEntryPos, blockutil_foundrectangle, newLevel, teleporttransition_postteleporttransition);
    }

    private static TeleportTransition getDimensionTransitionFromExit(Entity entity, BlockPos portalEntryPos, BlockUtil.FoundRectangle exitPortal, ServerLevel newLevel, TeleportTransition.PostTeleportTransition postTeleportTransition) {
        BlockState blockstate = entity.level().getBlockState(portalEntryPos);
        Direction.Axis direction_axis;
        Vec3 vec3;

        if (blockstate.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            direction_axis = (Direction.Axis) blockstate.getValue(BlockStateProperties.HORIZONTAL_AXIS);
            BlockUtil.FoundRectangle blockutil_foundrectangle1 = BlockUtil.getLargestRectangleAround(portalEntryPos, direction_axis, 21, Direction.Axis.Y, 21, (blockpos1) -> {
                return entity.level().getBlockState(blockpos1) == blockstate;
            });

            vec3 = entity.getRelativePortalPosition(direction_axis, blockutil_foundrectangle1);
        } else {
            direction_axis = Direction.Axis.X;
            vec3 = new Vec3(0.5D, 0.0D, 0.0D);
        }

        return createDimensionTransition(newLevel, exitPortal, direction_axis, vec3, entity, postTeleportTransition);
    }

    private static TeleportTransition createDimensionTransition(ServerLevel newLevel, BlockUtil.FoundRectangle foundRectangle, Direction.Axis portalAxis, Vec3 offset, Entity entity, TeleportTransition.PostTeleportTransition postTeleportTransition) {
        BlockPos blockpos = foundRectangle.minCorner;
        BlockState blockstate = newLevel.getBlockState(blockpos);
        Direction.Axis direction_axis1 = (Direction.Axis) blockstate.getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS).orElse(Direction.Axis.X);
        double d0 = (double) foundRectangle.axis1Size;
        double d1 = (double) foundRectangle.axis2Size;
        EntityDimensions entitydimensions = entity.getDimensions(entity.getPose());
        int i = portalAxis == direction_axis1 ? 0 : 90;
        double d2 = (double) entitydimensions.width() / 2.0D + (d0 - (double) entitydimensions.width()) * offset.x();
        double d3 = (d1 - (double) entitydimensions.height()) * offset.y();
        double d4 = 0.5D + offset.z();
        boolean flag = direction_axis1 == Direction.Axis.X;
        Vec3 vec31 = new Vec3((double) blockpos.getX() + (flag ? d2 : d4), (double) blockpos.getY() + d3, (double) blockpos.getZ() + (flag ? d4 : d2));
        Vec3 vec32 = PortalShape.findCollisionFreePosition(vec31, newLevel, entity, entitydimensions);

        return new TeleportTransition(newLevel, vec32, Vec3.ZERO, (float) i, 0.0F, Relative.union(Relative.DELTA, Relative.ROTATION), postTeleportTransition);
    }

    @Override
    public Portal.Transition getLocalTransition() {
        return Portal.Transition.CONFUSION;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(100) == 0) {
            level.playLocalSound((double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.5F, random.nextFloat() * 0.4F + 0.8F, false);
        }

        for (int i = 0; i < 4; ++i) {
            double d0 = (double) pos.getX() + random.nextDouble();
            double d1 = (double) pos.getY() + random.nextDouble();
            double d2 = (double) pos.getZ() + random.nextDouble();
            double d3 = ((double) random.nextFloat() - 0.5D) * 0.5D;
            double d4 = ((double) random.nextFloat() - 0.5D) * 0.5D;
            double d5 = ((double) random.nextFloat() - 0.5D) * 0.5D;
            int j = random.nextInt(2) * 2 - 1;

            if (!level.getBlockState(pos.west()).is(this) && !level.getBlockState(pos.east()).is(this)) {
                d0 = (double) pos.getX() + 0.5D + 0.25D * (double) j;
                d3 = (double) (random.nextFloat() * 2.0F * (float) j);
            } else {
                d2 = (double) pos.getZ() + 0.5D + 0.25D * (double) j;
                d5 = (double) (random.nextFloat() * 2.0F * (float) j);
            }

            level.addParticle(ParticleTypes.PORTAL, d0, d1, d2, d3, d4, d5);
        }

    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return ItemStack.EMPTY;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        switch (rotation) {
            case COUNTERCLOCKWISE_90:
            case CLOCKWISE_90:
                switch ((Direction.Axis) state.getValue(NetherPortalBlock.AXIS)) {
                    case X:
                        return (BlockState) state.setValue(NetherPortalBlock.AXIS, Direction.Axis.Z);
                    case Z:
                        return (BlockState) state.setValue(NetherPortalBlock.AXIS, Direction.Axis.X);
                    default:
                        return state;
                }
            default:
                return state;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NetherPortalBlock.AXIS);
    }
}
