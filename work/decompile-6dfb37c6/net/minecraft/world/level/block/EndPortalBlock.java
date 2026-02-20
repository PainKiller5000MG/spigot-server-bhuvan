package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.EndPlatformFeature;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class EndPortalBlock extends BaseEntityBlock implements Portal {

    public static final MapCodec<EndPortalBlock> CODEC = simpleCodec(EndPortalBlock::new);
    private static final VoxelShape SHAPE = Block.column(16.0D, 6.0D, 12.0D);

    @Override
    public MapCodec<EndPortalBlock> codec() {
        return EndPortalBlock.CODEC;
    }

    protected EndPortalBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new TheEndPortalBlockEntity(worldPosition, blockState);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return EndPortalBlock.SHAPE;
    }

    @Override
    protected VoxelShape getEntityInsideCollisionShape(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return state.getShape(level, pos);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (entity.canUsePortal(false)) {
            if (!level.isClientSide() && level.dimension() == Level.END && entity instanceof ServerPlayer) {
                ServerPlayer serverplayer = (ServerPlayer) entity;

                if (!serverplayer.seenCredits) {
                    serverplayer.showEndCredits();
                    return;
                }
            }

            entity.setAsInsidePortal(this, pos);
        }

    }

    @Override
    public @Nullable TeleportTransition getPortalDestination(ServerLevel currentLevel, Entity entity, BlockPos portalEntryPos) {
        LevelData.RespawnData leveldata_respawndata = currentLevel.getRespawnData();
        ResourceKey<Level> resourcekey = currentLevel.dimension();
        boolean flag = resourcekey == Level.END;
        ResourceKey<Level> resourcekey1 = flag ? leveldata_respawndata.dimension() : Level.END;
        BlockPos blockpos1 = flag ? leveldata_respawndata.pos() : ServerLevel.END_SPAWN_POINT;
        ServerLevel serverlevel1 = currentLevel.getServer().getLevel(resourcekey1);

        if (serverlevel1 == null) {
            return null;
        } else {
            Vec3 vec3 = blockpos1.getBottomCenter();
            float f;
            float f1;
            Set<Relative> set;

            if (!flag) {
                EndPlatformFeature.createEndPlatform(serverlevel1, BlockPos.containing(vec3).below(), true);
                f = Direction.WEST.toYRot();
                f1 = 0.0F;
                set = Relative.union(Relative.DELTA, Set.of(Relative.X_ROT));
                if (entity instanceof ServerPlayer) {
                    vec3 = vec3.subtract(0.0D, 1.0D, 0.0D);
                }
            } else {
                f = leveldata_respawndata.yaw();
                f1 = leveldata_respawndata.pitch();
                set = Relative.union(Relative.DELTA, Relative.ROTATION);
                if (entity instanceof ServerPlayer) {
                    ServerPlayer serverplayer = (ServerPlayer) entity;

                    return serverplayer.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
                }

                vec3 = entity.adjustSpawnLocation(serverlevel1, blockpos1).getBottomCenter();
            }

            return new TeleportTransition(serverlevel1, vec3, Vec3.ZERO, f, f1, set, TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET));
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double d0 = (double) pos.getX() + random.nextDouble();
        double d1 = (double) pos.getY() + 0.8D;
        double d2 = (double) pos.getZ() + random.nextDouble();

        level.addParticle(ParticleTypes.SMOKE, d0, d1, d2, 0.0D, 0.0D, 0.0D);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return ItemStack.EMPTY;
    }

    @Override
    protected boolean canBeReplaced(BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }
}
