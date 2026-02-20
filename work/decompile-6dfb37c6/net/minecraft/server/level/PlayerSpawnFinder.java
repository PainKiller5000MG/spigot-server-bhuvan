package net.minecraft.server.level;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class PlayerSpawnFinder {

    private static final EntityDimensions PLAYER_DIMENSIONS = EntityType.PLAYER.getDimensions();
    private static final int ABSOLUTE_MAX_ATTEMPTS = 1024;
    private final ServerLevel level;
    private final BlockPos spawnSuggestion;
    private final int radius;
    private final int candidateCount;
    private final int coprime;
    private final int offset;
    private int nextCandidateIndex;
    private final CompletableFuture<Vec3> finishedFuture = new CompletableFuture();

    private PlayerSpawnFinder(ServerLevel level, BlockPos spawnSuggestion, int radius) {
        this.level = level;
        this.spawnSuggestion = spawnSuggestion;
        this.radius = radius;
        long j = (long) radius * 2L + 1L;

        this.candidateCount = (int) Math.min(1024L, j * j);
        this.coprime = getCoprime(this.candidateCount);
        this.offset = RandomSource.create().nextInt(this.candidateCount);
    }

    public static CompletableFuture<Vec3> findSpawn(ServerLevel level, BlockPos spawnSuggestion) {
        if (level.dimensionType().hasSkyLight() && level.getServer().getWorldData().getGameType() != GameType.ADVENTURE) {
            int i = Math.max(0, (Integer) level.getGameRules().get(GameRules.RESPAWN_RADIUS));
            int j = Mth.floor(level.getWorldBorder().getDistanceToBorder((double) spawnSuggestion.getX(), (double) spawnSuggestion.getZ()));

            if (j < i) {
                i = j;
            }

            if (j <= 1) {
                i = 1;
            }

            PlayerSpawnFinder playerspawnfinder = new PlayerSpawnFinder(level, spawnSuggestion, i);

            playerspawnfinder.scheduleNext();
            return playerspawnfinder.finishedFuture;
        } else {
            return CompletableFuture.completedFuture(fixupSpawnHeight(level, spawnSuggestion));
        }
    }

    private void scheduleNext() {
        int i = this.nextCandidateIndex++;

        if (i < this.candidateCount) {
            int j = (this.offset + this.coprime * i) % this.candidateCount;
            int k = j % (this.radius * 2 + 1);
            int l = j / (this.radius * 2 + 1);
            int i1 = this.spawnSuggestion.getX() + k - this.radius;
            int j1 = this.spawnSuggestion.getZ() + l - this.radius;

            this.scheduleCandidate(i1, j1, i, () -> {
                BlockPos blockpos = getOverworldRespawnPos(this.level, i1, j1);

                return blockpos != null && noCollisionNoLiquid(this.level, blockpos) ? Optional.of(Vec3.atBottomCenterOf(blockpos)) : Optional.empty();
            });
        } else {
            this.scheduleCandidate(this.spawnSuggestion.getX(), this.spawnSuggestion.getZ(), i, () -> {
                return Optional.of(fixupSpawnHeight(this.level, this.spawnSuggestion));
            });
        }

    }

    private static Vec3 fixupSpawnHeight(CollisionGetter level, BlockPos spawnPos) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = spawnPos.mutable();

        while (!noCollisionNoLiquid(level, blockpos_mutableblockpos) && blockpos_mutableblockpos.getY() < level.getMaxY()) {
            blockpos_mutableblockpos.move(Direction.UP);
        }

        blockpos_mutableblockpos.move(Direction.DOWN);

        while (noCollisionNoLiquid(level, blockpos_mutableblockpos) && blockpos_mutableblockpos.getY() > level.getMinY()) {
            blockpos_mutableblockpos.move(Direction.DOWN);
        }

        blockpos_mutableblockpos.move(Direction.UP);
        return Vec3.atBottomCenterOf(blockpos_mutableblockpos);
    }

    private static boolean noCollisionNoLiquid(CollisionGetter level, BlockPos pos) {
        return level.noCollision((Entity) null, PlayerSpawnFinder.PLAYER_DIMENSIONS.makeBoundingBox(pos.getBottomCenter()), true);
    }

    private static int getCoprime(int possibleOrigins) {
        return possibleOrigins <= 16 ? possibleOrigins - 1 : 17;
    }

    private void scheduleCandidate(int candidateX, int candidateZ, int candidateIndex, Supplier<Optional<Vec3>> candidateChecker) {
        if (!this.finishedFuture.isDone()) {
            int l = SectionPos.blockToSectionCoord(candidateX);
            int i1 = SectionPos.blockToSectionCoord(candidateZ);

            this.level.getChunkSource().addTicketAndLoadWithRadius(TicketType.SPAWN_SEARCH, new ChunkPos(l, i1), 0).whenCompleteAsync((object, throwable) -> {
                if (throwable == null) {
                    try {
                        Optional<Vec3> optional = (Optional) candidateChecker.get();

                        if (optional.isPresent()) {
                            this.finishedFuture.complete((Vec3) optional.get());
                        } else {
                            this.scheduleNext();
                        }
                    } catch (Throwable throwable1) {
                        throwable = throwable1;
                    }
                }

                if (throwable != null) {
                    CrashReport crashreport = CrashReport.forThrowable(throwable, "Searching for spawn");
                    CrashReportCategory crashreportcategory = crashreport.addCategory("Spawn Lookup");
                    BlockPos blockpos = this.spawnSuggestion;

                    Objects.requireNonNull(this.spawnSuggestion);
                    crashreportcategory.setDetail("Origin", blockpos::toString);
                    crashreportcategory.setDetail("Radius", () -> {
                        return Integer.toString(this.radius);
                    });
                    crashreportcategory.setDetail("Candidate", () -> {
                        return "[" + candidateX + "," + candidateZ + "]";
                    });
                    crashreportcategory.setDetail("Progress", () -> {
                        return candidateIndex + " out of " + this.candidateCount;
                    });
                    this.finishedFuture.completeExceptionally(new ReportedException(crashreport));
                }

            }, this.level.getServer());
        }
    }

    protected static @Nullable BlockPos getOverworldRespawnPos(ServerLevel level, int x, int z) {
        boolean flag = level.dimensionType().hasCeiling();
        LevelChunk levelchunk = level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        int k = flag ? level.getChunkSource().getGenerator().getSpawnHeight(level) : levelchunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15);

        if (k < level.getMinY()) {
            return null;
        } else {
            int l = levelchunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);

            if (l <= k && l > levelchunk.getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15)) {
                return null;
            } else {
                BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

                for (int i1 = k + 1; i1 >= level.getMinY(); --i1) {
                    blockpos_mutableblockpos.set(x, i1, z);
                    BlockState blockstate = level.getBlockState(blockpos_mutableblockpos);

                    if (!blockstate.getFluidState().isEmpty()) {
                        break;
                    }

                    if (Block.isFaceFull(blockstate.getCollisionShape(level, blockpos_mutableblockpos), Direction.UP)) {
                        return blockpos_mutableblockpos.above().immutable();
                    }
                }

                return null;
            }
        }
    }

    public static @Nullable BlockPos getSpawnPosInChunk(ServerLevel level, ChunkPos chunkPos) {
        if (SharedConstants.debugVoidTerrain(chunkPos)) {
            return null;
        } else {
            for (int i = chunkPos.getMinBlockX(); i <= chunkPos.getMaxBlockX(); ++i) {
                for (int j = chunkPos.getMinBlockZ(); j <= chunkPos.getMaxBlockZ(); ++j) {
                    BlockPos blockpos = getOverworldRespawnPos(level, i, j);

                    if (blockpos != null) {
                        return blockpos;
                    }
                }
            }

            return null;
        }
    }
}
