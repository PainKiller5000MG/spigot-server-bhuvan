package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.EndFeatures;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.EndGatewayConfiguration;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class TheEndGatewayBlockEntity extends TheEndPortalBlockEntity {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SPAWN_TIME = 200;
    private static final int COOLDOWN_TIME = 40;
    private static final int ATTENTION_INTERVAL = 2400;
    private static final int EVENT_COOLDOWN = 1;
    private static final int GATEWAY_HEIGHT_ABOVE_SURFACE = 10;
    private static final long DEFAULT_AGE = 0L;
    private static final boolean DEFAULT_EXACT_TELEPORT = false;
    public long age = 0L;
    private int teleportCooldown;
    public @Nullable BlockPos exitPortal;
    public boolean exactTeleport = false;

    public TheEndGatewayBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(BlockEntityType.END_GATEWAY, worldPosition, blockState);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("Age", this.age);
        output.storeNullable("exit_portal", BlockPos.CODEC, this.exitPortal);
        if (this.exactTeleport) {
            output.putBoolean("ExactTeleport", true);
        }

    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.age = input.getLongOr("Age", 0L);
        this.exitPortal = (BlockPos) input.read("exit_portal", BlockPos.CODEC).filter(Level::isInSpawnableBounds).orElse((Object) null);
        this.exactTeleport = input.getBooleanOr("ExactTeleport", false);
    }

    public static void beamAnimationTick(Level level, BlockPos pos, BlockState state, TheEndGatewayBlockEntity entity) {
        ++entity.age;
        if (entity.isCoolingDown()) {
            --entity.teleportCooldown;
        }

    }

    public static void portalTick(Level level, BlockPos pos, BlockState state, TheEndGatewayBlockEntity entity) {
        boolean flag = entity.isSpawning();
        boolean flag1 = entity.isCoolingDown();

        ++entity.age;
        if (flag1) {
            --entity.teleportCooldown;
        } else if (entity.age % 2400L == 0L) {
            triggerCooldown(level, pos, state, entity);
        }

        if (flag != entity.isSpawning() || flag1 != entity.isCoolingDown()) {
            setChanged(level, pos, state);
        }

    }

    public boolean isSpawning() {
        return this.age < 200L;
    }

    public boolean isCoolingDown() {
        return this.teleportCooldown > 0;
    }

    public float getSpawnPercent(float a) {
        return Mth.clamp(((float) this.age + a) / 200.0F, 0.0F, 1.0F);
    }

    public float getCooldownPercent(float a) {
        return 1.0F - Mth.clamp(((float) this.teleportCooldown - a) / 40.0F, 0.0F, 1.0F);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    public static void triggerCooldown(Level level, BlockPos pos, BlockState blockState, TheEndGatewayBlockEntity entity) {
        if (!level.isClientSide()) {
            entity.teleportCooldown = 40;
            level.blockEvent(pos, blockState.getBlock(), 1, 0);
            setChanged(level, pos, blockState);
        }

    }

    @Override
    public boolean triggerEvent(int b0, int b1) {
        if (b0 == 1) {
            this.teleportCooldown = 40;
            return true;
        } else {
            return super.triggerEvent(b0, b1);
        }
    }

    public @Nullable Vec3 getPortalPosition(ServerLevel currentLevel, BlockPos portalEntryPos) {
        if (this.exitPortal == null && currentLevel.dimension() == Level.END) {
            BlockPos blockpos1 = findOrCreateValidTeleportPos(currentLevel, portalEntryPos);

            blockpos1 = blockpos1.above(10);
            TheEndGatewayBlockEntity.LOGGER.debug("Creating portal at {}", blockpos1);
            spawnGatewayPortal(currentLevel, blockpos1, EndGatewayConfiguration.knownExit(portalEntryPos, false));
            this.setExitPosition(blockpos1, this.exactTeleport);
        }

        if (this.exitPortal != null) {
            BlockPos blockpos2 = this.exactTeleport ? this.exitPortal : findExitPosition(currentLevel, this.exitPortal);

            return blockpos2.getBottomCenter();
        } else {
            return null;
        }
    }

    private static BlockPos findExitPosition(Level level, BlockPos exitPortal) {
        BlockPos blockpos1 = findTallestBlock(level, exitPortal.offset(0, 2, 0), 5, false);

        TheEndGatewayBlockEntity.LOGGER.debug("Best exit position for portal at {} is {}", exitPortal, blockpos1);
        return blockpos1.above();
    }

    private static BlockPos findOrCreateValidTeleportPos(ServerLevel level, BlockPos endGatewayPos) {
        Vec3 vec3 = findExitPortalXZPosTentative(level, endGatewayPos);
        LevelChunk levelchunk = getChunk(level, vec3);
        BlockPos blockpos1 = findValidSpawnInChunk(levelchunk);

        if (blockpos1 == null) {
            BlockPos blockpos2 = BlockPos.containing(vec3.x + 0.5D, 75.0D, vec3.z + 0.5D);

            TheEndGatewayBlockEntity.LOGGER.debug("Failed to find a suitable block to teleport to, spawning an island on {}", blockpos2);
            level.registryAccess().lookup(Registries.CONFIGURED_FEATURE).flatMap((registry) -> {
                return registry.get(EndFeatures.END_ISLAND);
            }).ifPresent((holder_reference) -> {
                ((ConfiguredFeature) holder_reference.value()).place(level, level.getChunkSource().getGenerator(), RandomSource.create(blockpos2.asLong()), blockpos2);
            });
            blockpos1 = blockpos2;
        } else {
            TheEndGatewayBlockEntity.LOGGER.debug("Found suitable block to teleport to: {}", blockpos1);
        }

        return findTallestBlock(level, blockpos1, 16, true);
    }

    private static Vec3 findExitPortalXZPosTentative(ServerLevel level, BlockPos endGatewayPos) {
        Vec3 vec3 = (new Vec3((double) endGatewayPos.getX(), 0.0D, (double) endGatewayPos.getZ())).normalize();
        int i = 1024;
        Vec3 vec31 = vec3.scale(1024.0D);

        for (int j = 16; !isChunkEmpty(level, vec31) && j-- > 0; vec31 = vec31.add(vec3.scale(-16.0D))) {
            TheEndGatewayBlockEntity.LOGGER.debug("Skipping backwards past nonempty chunk at {}", vec31);
        }

        for (int k = 16; isChunkEmpty(level, vec31) && k-- > 0; vec31 = vec31.add(vec3.scale(16.0D))) {
            TheEndGatewayBlockEntity.LOGGER.debug("Skipping forward past empty chunk at {}", vec31);
        }

        TheEndGatewayBlockEntity.LOGGER.debug("Found chunk at {}", vec31);
        return vec31;
    }

    private static boolean isChunkEmpty(ServerLevel level, Vec3 xzPos) {
        return getChunk(level, xzPos).getHighestFilledSectionIndex() == -1;
    }

    private static BlockPos findTallestBlock(BlockGetter level, BlockPos around, int dist, boolean allowBedrock) {
        BlockPos blockpos1 = null;

        for (int j = -dist; j <= dist; ++j) {
            for (int k = -dist; k <= dist; ++k) {
                if (j != 0 || k != 0 || allowBedrock) {
                    for (int l = level.getMaxY(); l > (blockpos1 == null ? level.getMinY() : blockpos1.getY()); --l) {
                        BlockPos blockpos2 = new BlockPos(around.getX() + j, l, around.getZ() + k);
                        BlockState blockstate = level.getBlockState(blockpos2);

                        if (blockstate.isCollisionShapeFullBlock(level, blockpos2) && (allowBedrock || !blockstate.is(Blocks.BEDROCK))) {
                            blockpos1 = blockpos2;
                            break;
                        }
                    }
                }
            }
        }

        return blockpos1 == null ? around : blockpos1;
    }

    private static LevelChunk getChunk(Level level, Vec3 pos) {
        return level.getChunk(Mth.floor(pos.x / 16.0D), Mth.floor(pos.z / 16.0D));
    }

    private static @Nullable BlockPos findValidSpawnInChunk(LevelChunk chunk) {
        ChunkPos chunkpos = chunk.getPos();
        BlockPos blockpos = new BlockPos(chunkpos.getMinBlockX(), 30, chunkpos.getMinBlockZ());
        int i = chunk.getHighestSectionPosition() + 16 - 1;
        BlockPos blockpos1 = new BlockPos(chunkpos.getMaxBlockX(), i, chunkpos.getMaxBlockZ());
        BlockPos blockpos2 = null;
        double d0 = 0.0D;

        for (BlockPos blockpos3 : BlockPos.betweenClosed(blockpos, blockpos1)) {
            BlockState blockstate = chunk.getBlockState(blockpos3);
            BlockPos blockpos4 = blockpos3.above();
            BlockPos blockpos5 = blockpos3.above(2);

            if (blockstate.is(Blocks.END_STONE) && !chunk.getBlockState(blockpos4).isCollisionShapeFullBlock(chunk, blockpos4) && !chunk.getBlockState(blockpos5).isCollisionShapeFullBlock(chunk, blockpos5)) {
                double d1 = blockpos3.distToCenterSqr(0.0D, 0.0D, 0.0D);

                if (blockpos2 == null || d1 < d0) {
                    blockpos2 = blockpos3;
                    d0 = d1;
                }
            }
        }

        return blockpos2;
    }

    private static void spawnGatewayPortal(ServerLevel level, BlockPos portalPos, EndGatewayConfiguration config) {
        Feature.END_GATEWAY.place(config, level, level.getChunkSource().getGenerator(), RandomSource.create(), portalPos);
    }

    @Override
    public boolean shouldRenderFace(Direction direction) {
        return Block.shouldRenderFace(this.getBlockState(), this.level.getBlockState(this.getBlockPos().relative(direction)), direction);
    }

    public int getParticleAmount() {
        int i = 0;

        for (Direction direction : Direction.values()) {
            i += this.shouldRenderFace(direction) ? 1 : 0;
        }

        return i;
    }

    public void setExitPosition(BlockPos exactPosition, boolean exact) {
        this.exactTeleport = exact;
        this.exitPortal = exactPosition;
        this.setChanged();
    }
}
