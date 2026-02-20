package net.minecraft.world.level.block;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class SculkSpreader {

    public static final int MAX_GROWTH_RATE_RADIUS = 24;
    public static final int MAX_CHARGE = 1000;
    public static final float MAX_DECAY_FACTOR = 0.5F;
    private static final int MAX_CURSORS = 32;
    public static final int SHRIEKER_PLACEMENT_RATE = 11;
    public static final int MAX_CURSOR_DISTANCE = 1024;
    private final boolean isWorldGeneration;
    private final TagKey<Block> replaceableBlocks;
    private final int growthSpawnCost;
    private final int noGrowthRadius;
    private final int chargeDecayRate;
    private final int additionalDecayRate;
    private List<SculkSpreader.ChargeCursor> cursors = new ArrayList();

    public SculkSpreader(boolean isWorldGeneration, TagKey<Block> replaceableBlocks, int growthSpawnCost, int noGrowthRadius, int chargeDecayRate, int additionalDecayRate) {
        this.isWorldGeneration = isWorldGeneration;
        this.replaceableBlocks = replaceableBlocks;
        this.growthSpawnCost = growthSpawnCost;
        this.noGrowthRadius = noGrowthRadius;
        this.chargeDecayRate = chargeDecayRate;
        this.additionalDecayRate = additionalDecayRate;
    }

    public static SculkSpreader createLevelSpreader() {
        return new SculkSpreader(false, BlockTags.SCULK_REPLACEABLE, 10, 4, 10, 5);
    }

    public static SculkSpreader createWorldGenSpreader() {
        return new SculkSpreader(true, BlockTags.SCULK_REPLACEABLE_WORLD_GEN, 50, 1, 5, 10);
    }

    public TagKey<Block> replaceableBlocks() {
        return this.replaceableBlocks;
    }

    public int growthSpawnCost() {
        return this.growthSpawnCost;
    }

    public int noGrowthRadius() {
        return this.noGrowthRadius;
    }

    public int chargeDecayRate() {
        return this.chargeDecayRate;
    }

    public int additionalDecayRate() {
        return this.additionalDecayRate;
    }

    public boolean isWorldGeneration() {
        return this.isWorldGeneration;
    }

    @VisibleForTesting
    public List<SculkSpreader.ChargeCursor> getCursors() {
        return this.cursors;
    }

    public void clear() {
        this.cursors.clear();
    }

    public void load(ValueInput input) {
        this.cursors.clear();
        ((List) input.read("cursors", SculkSpreader.ChargeCursor.CODEC.sizeLimitedListOf(32)).orElse(List.of())).forEach(this::addCursor);
    }

    public void save(ValueOutput output) {
        output.store("cursors", SculkSpreader.ChargeCursor.CODEC.listOf(), this.cursors);
        if (SharedConstants.DEBUG_SCULK_CATALYST) {
            int i = (Integer) this.getCursors().stream().map(SculkSpreader.ChargeCursor::getCharge).reduce(0, Integer::sum);
            int j = (Integer) this.getCursors().stream().map((sculkspreader_chargecursor) -> {
                return 1;
            }).reduce(0, Integer::sum);
            int k = (Integer) this.getCursors().stream().map(SculkSpreader.ChargeCursor::getCharge).reduce(0, Math::max);

            output.putInt("stats.total", i);
            output.putInt("stats.count", j);
            output.putInt("stats.max", k);
            output.putInt("stats.avg", i / (j + 1));
        }

    }

    public void addCursors(BlockPos startPos, int charge) {
        while (charge > 0) {
            int j = Math.min(charge, 1000);

            this.addCursor(new SculkSpreader.ChargeCursor(startPos, j));
            charge -= j;
        }

    }

    private void addCursor(SculkSpreader.ChargeCursor cursor) {
        if (this.cursors.size() < 32) {
            this.cursors.add(cursor);
        }
    }

    public void updateCursors(LevelAccessor level, BlockPos originPos, RandomSource random, boolean spreadVeins) {
        if (!this.cursors.isEmpty()) {
            List<SculkSpreader.ChargeCursor> list = new ArrayList();
            Map<BlockPos, SculkSpreader.ChargeCursor> map = new HashMap();
            Object2IntMap<BlockPos> object2intmap = new Object2IntOpenHashMap();

            for (SculkSpreader.ChargeCursor sculkspreader_chargecursor : this.cursors) {
                if (!sculkspreader_chargecursor.isPosUnreasonable(originPos)) {
                    sculkspreader_chargecursor.update(level, originPos, random, this, spreadVeins);
                    if (sculkspreader_chargecursor.charge <= 0) {
                        level.levelEvent(3006, sculkspreader_chargecursor.getPos(), 0);
                    } else {
                        BlockPos blockpos1 = sculkspreader_chargecursor.getPos();

                        object2intmap.computeInt(blockpos1, (blockpos2, integer) -> {
                            return (integer == null ? 0 : integer) + sculkspreader_chargecursor.charge;
                        });
                        SculkSpreader.ChargeCursor sculkspreader_chargecursor1 = (SculkSpreader.ChargeCursor) map.get(blockpos1);

                        if (sculkspreader_chargecursor1 == null) {
                            map.put(blockpos1, sculkspreader_chargecursor);
                            list.add(sculkspreader_chargecursor);
                        } else if (!this.isWorldGeneration() && sculkspreader_chargecursor.charge + sculkspreader_chargecursor1.charge <= 1000) {
                            sculkspreader_chargecursor1.mergeWith(sculkspreader_chargecursor);
                        } else {
                            list.add(sculkspreader_chargecursor);
                            if (sculkspreader_chargecursor.charge < sculkspreader_chargecursor1.charge) {
                                map.put(blockpos1, sculkspreader_chargecursor);
                            }
                        }
                    }
                }
            }

            ObjectIterator objectiterator = object2intmap.object2IntEntrySet().iterator();

            while (objectiterator.hasNext()) {
                Object2IntMap.Entry<BlockPos> object2intmap_entry = (Entry) objectiterator.next();
                BlockPos blockpos2 = (BlockPos) object2intmap_entry.getKey();
                int i = object2intmap_entry.getIntValue();
                SculkSpreader.ChargeCursor sculkspreader_chargecursor2 = (SculkSpreader.ChargeCursor) map.get(blockpos2);
                Collection<Direction> collection = sculkspreader_chargecursor2 == null ? null : sculkspreader_chargecursor2.getFacingData();

                if (i > 0 && collection != null) {
                    int j = (int) (Math.log1p((double) i) / (double) 2.3F) + 1;
                    int k = (j << 6) + MultifaceBlock.pack(collection);

                    level.levelEvent(3006, blockpos2, k);
                }
            }

            this.cursors = list;
        }
    }

    public static class ChargeCursor {

        private static final ObjectArrayList<Vec3i> NON_CORNER_NEIGHBOURS = (ObjectArrayList) Util.make(new ObjectArrayList(18), (objectarraylist) -> {
            Stream stream = BlockPos.betweenClosedStream(new BlockPos(-1, -1, -1), new BlockPos(1, 1, 1)).filter((blockpos) -> {
                return (blockpos.getX() == 0 || blockpos.getY() == 0 || blockpos.getZ() == 0) && !blockpos.equals(BlockPos.ZERO);
            }).map(BlockPos::immutable);

            Objects.requireNonNull(objectarraylist);
            stream.forEach(objectarraylist::add);
        });
        public static final int MAX_CURSOR_DECAY_DELAY = 1;
        private BlockPos pos;
        private int charge;
        private int updateDelay;
        private int decayDelay;
        private @Nullable Set<Direction> facings;
        private static final Codec<Set<Direction>> DIRECTION_SET = Direction.CODEC.listOf().xmap((list) -> {
            return Sets.newEnumSet(list, Direction.class);
        }, Lists::newArrayList);
        public static final Codec<SculkSpreader.ChargeCursor> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(BlockPos.CODEC.fieldOf("pos").forGetter(SculkSpreader.ChargeCursor::getPos), Codec.intRange(0, 1000).fieldOf("charge").orElse(0).forGetter(SculkSpreader.ChargeCursor::getCharge), Codec.intRange(0, 1).fieldOf("decay_delay").orElse(1).forGetter(SculkSpreader.ChargeCursor::getDecayDelay), Codec.intRange(0, Integer.MAX_VALUE).fieldOf("update_delay").orElse(0).forGetter((sculkspreader_chargecursor) -> {
                return sculkspreader_chargecursor.updateDelay;
            }), SculkSpreader.ChargeCursor.DIRECTION_SET.lenientOptionalFieldOf("facings").forGetter((sculkspreader_chargecursor) -> {
                return Optional.ofNullable(sculkspreader_chargecursor.getFacingData());
            })).apply(instance, SculkSpreader.ChargeCursor::new);
        });

        private ChargeCursor(BlockPos pos, int charge, int decayDelay, int updateDelay, Optional<Set<Direction>> facings) {
            this.pos = pos;
            this.charge = charge;
            this.decayDelay = decayDelay;
            this.updateDelay = updateDelay;
            this.facings = (Set) facings.orElse((Object) null);
        }

        public ChargeCursor(BlockPos pos, int charge) {
            this(pos, charge, 1, 0, Optional.empty());
        }

        public BlockPos getPos() {
            return this.pos;
        }

        private boolean isPosUnreasonable(BlockPos originPos) {
            return this.pos.distChessboard(originPos) > 1024;
        }

        public int getCharge() {
            return this.charge;
        }

        public int getDecayDelay() {
            return this.decayDelay;
        }

        public @Nullable Set<Direction> getFacingData() {
            return this.facings;
        }

        private boolean shouldUpdate(LevelAccessor level, BlockPos pos, boolean isWorldGen) {
            if (this.charge <= 0) {
                return false;
            } else if (isWorldGen) {
                return true;
            } else if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                return serverlevel.shouldTickBlocksAt(pos);
            } else {
                return false;
            }
        }

        public void update(LevelAccessor level, BlockPos originPos, RandomSource random, SculkSpreader spreader, boolean spreadVeins) {
            if (this.shouldUpdate(level, originPos, spreader.isWorldGeneration)) {
                if (this.updateDelay > 0) {
                    --this.updateDelay;
                } else {
                    BlockState blockstate = level.getBlockState(this.pos);
                    SculkBehaviour sculkbehaviour = getBlockBehaviour(blockstate);

                    if (spreadVeins && sculkbehaviour.attemptSpreadVein(level, this.pos, blockstate, this.facings, spreader.isWorldGeneration())) {
                        if (sculkbehaviour.canChangeBlockStateOnSpread()) {
                            blockstate = level.getBlockState(this.pos);
                            sculkbehaviour = getBlockBehaviour(blockstate);
                        }

                        level.playSound((Entity) null, this.pos, SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.BLOCKS, 1.0F, 1.0F);
                    }

                    this.charge = sculkbehaviour.attemptUseCharge(this, level, originPos, random, spreader, spreadVeins);
                    if (this.charge <= 0) {
                        sculkbehaviour.onDischarged(level, blockstate, this.pos, random);
                    } else {
                        BlockPos blockpos1 = getValidMovementPos(level, this.pos, random);

                        if (blockpos1 != null) {
                            sculkbehaviour.onDischarged(level, blockstate, this.pos, random);
                            this.pos = blockpos1.immutable();
                            if (spreader.isWorldGeneration() && !this.pos.closerThan(new Vec3i(originPos.getX(), this.pos.getY(), originPos.getZ()), 15.0D)) {
                                this.charge = 0;
                                return;
                            }

                            blockstate = level.getBlockState(blockpos1);
                        }

                        if (blockstate.getBlock() instanceof SculkBehaviour) {
                            this.facings = MultifaceBlock.availableFaces(blockstate);
                        }

                        this.decayDelay = sculkbehaviour.updateDecayDelay(this.decayDelay);
                        this.updateDelay = sculkbehaviour.getSculkSpreadDelay();
                    }
                }
            }
        }

        private void mergeWith(SculkSpreader.ChargeCursor other) {
            this.charge += other.charge;
            other.charge = 0;
            this.updateDelay = Math.min(this.updateDelay, other.updateDelay);
        }

        private static SculkBehaviour getBlockBehaviour(BlockState state) {
            Block block = state.getBlock();
            SculkBehaviour sculkbehaviour;

            if (block instanceof SculkBehaviour sculkbehaviour1) {
                sculkbehaviour = sculkbehaviour1;
            } else {
                sculkbehaviour = SculkBehaviour.DEFAULT;
            }

            return sculkbehaviour;
        }

        private static List<Vec3i> getRandomizedNonCornerNeighbourOffsets(RandomSource random) {
            return Util.shuffledCopy(SculkSpreader.ChargeCursor.NON_CORNER_NEIGHBOURS, random);
        }

        private static @Nullable BlockPos getValidMovementPos(LevelAccessor level, BlockPos pos, RandomSource random) {
            BlockPos.MutableBlockPos blockpos_mutableblockpos = pos.mutable();
            BlockPos.MutableBlockPos blockpos_mutableblockpos1 = pos.mutable();

            for (Vec3i vec3i : getRandomizedNonCornerNeighbourOffsets(random)) {
                blockpos_mutableblockpos1.setWithOffset(pos, vec3i);
                BlockState blockstate = level.getBlockState(blockpos_mutableblockpos1);

                if (blockstate.getBlock() instanceof SculkBehaviour && isMovementUnobstructed(level, pos, blockpos_mutableblockpos1)) {
                    blockpos_mutableblockpos.set(blockpos_mutableblockpos1);
                    if (SculkVeinBlock.hasSubstrateAccess(level, blockstate, blockpos_mutableblockpos1)) {
                        break;
                    }
                }
            }

            return blockpos_mutableblockpos.equals(pos) ? null : blockpos_mutableblockpos;
        }

        private static boolean isMovementUnobstructed(LevelAccessor level, BlockPos from, BlockPos to) {
            if (from.distManhattan(to) == 1) {
                return true;
            } else {
                BlockPos blockpos2 = to.subtract(from);
                Direction direction = Direction.fromAxisAndDirection(Direction.Axis.X, blockpos2.getX() < 0 ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE);
                Direction direction1 = Direction.fromAxisAndDirection(Direction.Axis.Y, blockpos2.getY() < 0 ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE);
                Direction direction2 = Direction.fromAxisAndDirection(Direction.Axis.Z, blockpos2.getZ() < 0 ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE);

                return blockpos2.getX() == 0 ? isUnobstructed(level, from, direction1) || isUnobstructed(level, from, direction2) : (blockpos2.getY() == 0 ? isUnobstructed(level, from, direction) || isUnobstructed(level, from, direction2) : isUnobstructed(level, from, direction) || isUnobstructed(level, from, direction1));
            }
        }

        private static boolean isUnobstructed(LevelAccessor level, BlockPos from, Direction direction) {
            BlockPos blockpos1 = from.relative(direction);

            return !level.getBlockState(blockpos1).isFaceSturdy(level, blockpos1, direction.getOpposite());
        }
    }
}
