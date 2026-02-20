package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.IdMapper;
import net.minecraft.core.Vec3i;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class StructureTemplate {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String PALETTE_TAG = "palette";
    public static final String PALETTE_LIST_TAG = "palettes";
    public static final String ENTITIES_TAG = "entities";
    public static final String BLOCKS_TAG = "blocks";
    public static final String BLOCK_TAG_POS = "pos";
    public static final String BLOCK_TAG_STATE = "state";
    public static final String BLOCK_TAG_NBT = "nbt";
    public static final String ENTITY_TAG_POS = "pos";
    public static final String ENTITY_TAG_BLOCKPOS = "blockPos";
    public static final String ENTITY_TAG_NBT = "nbt";
    public static final String SIZE_TAG = "size";
    public final List<StructureTemplate.Palette> palettes = Lists.newArrayList();
    public final List<StructureTemplate.StructureEntityInfo> entityInfoList = Lists.newArrayList();
    private Vec3i size;
    private String author;

    public StructureTemplate() {
        this.size = Vec3i.ZERO;
        this.author = "?";
    }

    public Vec3i getSize() {
        return this.size;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getAuthor() {
        return this.author;
    }

    public void fillFromWorld(Level level, BlockPos position, Vec3i size, boolean inludeEntities, List<Block> ignoreBlocks) {
        if (size.getX() >= 1 && size.getY() >= 1 && size.getZ() >= 1) {
            BlockPos blockpos1 = position.offset(size).offset(-1, -1, -1);
            List<StructureTemplate.StructureBlockInfo> list1 = Lists.newArrayList();
            List<StructureTemplate.StructureBlockInfo> list2 = Lists.newArrayList();
            List<StructureTemplate.StructureBlockInfo> list3 = Lists.newArrayList();
            BlockPos blockpos2 = new BlockPos(Math.min(position.getX(), blockpos1.getX()), Math.min(position.getY(), blockpos1.getY()), Math.min(position.getZ(), blockpos1.getZ()));
            BlockPos blockpos3 = new BlockPos(Math.max(position.getX(), blockpos1.getX()), Math.max(position.getY(), blockpos1.getY()), Math.max(position.getZ(), blockpos1.getZ()));

            this.size = size;

            try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(StructureTemplate.LOGGER)) {
                for (BlockPos blockpos4 : BlockPos.betweenClosed(blockpos2, blockpos3)) {
                    BlockPos blockpos5 = blockpos4.subtract(blockpos2);
                    BlockState blockstate = level.getBlockState(blockpos4);
                    Stream stream = ignoreBlocks.stream();

                    Objects.requireNonNull(blockstate);
                    if (!stream.anyMatch(blockstate::is)) {
                        BlockEntity blockentity = level.getBlockEntity(blockpos4);
                        StructureTemplate.StructureBlockInfo structuretemplate_structureblockinfo;

                        if (blockentity != null) {
                            TagValueOutput tagvalueoutput = TagValueOutput.createWithContext(problemreporter_scopedcollector, level.registryAccess());

                            blockentity.saveWithId(tagvalueoutput);
                            structuretemplate_structureblockinfo = new StructureTemplate.StructureBlockInfo(blockpos5, blockstate, tagvalueoutput.buildResult());
                        } else {
                            structuretemplate_structureblockinfo = new StructureTemplate.StructureBlockInfo(blockpos5, blockstate, (CompoundTag) null);
                        }

                        addToLists(structuretemplate_structureblockinfo, list1, list2, list3);
                    }
                }

                List<StructureTemplate.StructureBlockInfo> list4 = buildInfoList(list1, list2, list3);

                this.palettes.clear();
                this.palettes.add(new StructureTemplate.Palette(list4));
                if (inludeEntities) {
                    this.fillEntityList(level, blockpos2, blockpos3, problemreporter_scopedcollector);
                } else {
                    this.entityInfoList.clear();
                }
            }

        }
    }

    private static void addToLists(StructureTemplate.StructureBlockInfo info, List<StructureTemplate.StructureBlockInfo> fullBlockList, List<StructureTemplate.StructureBlockInfo> blockEntitiesList, List<StructureTemplate.StructureBlockInfo> otherBlocksList) {
        if (info.nbt != null) {
            blockEntitiesList.add(info);
        } else if (!info.state.getBlock().hasDynamicShape() && info.state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
            fullBlockList.add(info);
        } else {
            otherBlocksList.add(info);
        }

    }

    private static List<StructureTemplate.StructureBlockInfo> buildInfoList(List<StructureTemplate.StructureBlockInfo> fullBlockList, List<StructureTemplate.StructureBlockInfo> blockEntitiesList, List<StructureTemplate.StructureBlockInfo> otherBlocksList) {
        Comparator<StructureTemplate.StructureBlockInfo> comparator = Comparator.comparingInt((structuretemplate_structureblockinfo) -> {
            return structuretemplate_structureblockinfo.pos.getY();
        }).thenComparingInt((structuretemplate_structureblockinfo) -> {
            return structuretemplate_structureblockinfo.pos.getX();
        }).thenComparingInt((structuretemplate_structureblockinfo) -> {
            return structuretemplate_structureblockinfo.pos.getZ();
        });

        fullBlockList.sort(comparator);
        otherBlocksList.sort(comparator);
        blockEntitiesList.sort(comparator);
        List<StructureTemplate.StructureBlockInfo> list3 = Lists.newArrayList();

        list3.addAll(fullBlockList);
        list3.addAll(otherBlocksList);
        list3.addAll(blockEntitiesList);
        return list3;
    }

    private void fillEntityList(Level level, BlockPos minCorner, BlockPos maxCorner, ProblemReporter reporter) {
        List<Entity> list = level.<Entity>getEntitiesOfClass(Entity.class, AABB.encapsulatingFullBlocks(minCorner, maxCorner), (entity) -> {
            return !(entity instanceof Player);
        });

        this.entityInfoList.clear();

        for (Entity entity : list) {
            Vec3 vec3 = new Vec3(entity.getX() - (double) minCorner.getX(), entity.getY() - (double) minCorner.getY(), entity.getZ() - (double) minCorner.getZ());
            TagValueOutput tagvalueoutput = TagValueOutput.createWithContext(reporter.forChild(entity.problemPath()), entity.registryAccess());

            entity.save(tagvalueoutput);
            BlockPos blockpos2;

            if (entity instanceof Painting painting) {
                blockpos2 = painting.getPos().subtract(minCorner);
            } else {
                blockpos2 = BlockPos.containing(vec3);
            }

            this.entityInfoList.add(new StructureTemplate.StructureEntityInfo(vec3, blockpos2, tagvalueoutput.buildResult().copy()));
        }

    }

    public List<StructureTemplate.StructureBlockInfo> filterBlocks(BlockPos position, StructurePlaceSettings settings, Block block) {
        return this.filterBlocks(position, settings, block, true);
    }

    public List<StructureTemplate.JigsawBlockInfo> getJigsaws(BlockPos position, Rotation rotation) {
        if (this.palettes.isEmpty()) {
            return new ArrayList();
        } else {
            StructurePlaceSettings structureplacesettings = (new StructurePlaceSettings()).setRotation(rotation);
            List<StructureTemplate.JigsawBlockInfo> list = structureplacesettings.getRandomPalette(this.palettes, position).jigsaws();
            List<StructureTemplate.JigsawBlockInfo> list1 = new ArrayList(list.size());

            for (StructureTemplate.JigsawBlockInfo structuretemplate_jigsawblockinfo : list) {
                StructureTemplate.StructureBlockInfo structuretemplate_structureblockinfo = structuretemplate_jigsawblockinfo.info;

                list1.add(structuretemplate_jigsawblockinfo.withInfo(new StructureTemplate.StructureBlockInfo(calculateRelativePosition(structureplacesettings, structuretemplate_structureblockinfo.pos()).offset(position), structuretemplate_structureblockinfo.state.rotate(structureplacesettings.getRotation()), structuretemplate_structureblockinfo.nbt)));
            }

            return list1;
        }
    }

    public ObjectArrayList<StructureTemplate.StructureBlockInfo> filterBlocks(BlockPos position, StructurePlaceSettings settings, Block block, boolean absolute) {
        ObjectArrayList<StructureTemplate.StructureBlockInfo> objectarraylist = new ObjectArrayList();
        BoundingBox boundingbox = settings.getBoundingBox();

        if (this.palettes.isEmpty()) {
            return objectarraylist;
        } else {
            for (StructureTemplate.StructureBlockInfo structuretemplate_structureblockinfo : settings.getRandomPalette(this.palettes, position).blocks(block)) {
                BlockPos blockpos1 = absolute ? calculateRelativePosition(settings, structuretemplate_structureblockinfo.pos).offset(position) : structuretemplate_structureblockinfo.pos;

                if (boundingbox == null || boundingbox.isInside(blockpos1)) {
                    objectarraylist.add(new StructureTemplate.StructureBlockInfo(blockpos1, structuretemplate_structureblockinfo.state.rotate(settings.getRotation()), structuretemplate_structureblockinfo.nbt));
                }
            }

            return objectarraylist;
        }
    }

    public BlockPos calculateConnectedPosition(StructurePlaceSettings settings1, BlockPos connection1, StructurePlaceSettings settings2, BlockPos connection2) {
        BlockPos blockpos2 = calculateRelativePosition(settings1, connection1);
        BlockPos blockpos3 = calculateRelativePosition(settings2, connection2);

        return blockpos2.subtract(blockpos3);
    }

    public static BlockPos calculateRelativePosition(StructurePlaceSettings settings, BlockPos pos) {
        return transform(pos, settings.getMirror(), settings.getRotation(), settings.getRotationPivot());
    }

    public boolean placeInWorld(ServerLevelAccessor level, BlockPos position, BlockPos referencePos, StructurePlaceSettings settings, RandomSource random, @Block.UpdateFlags int updateMode) {
        if (this.palettes.isEmpty()) {
            return false;
        } else {
            List<StructureTemplate.StructureBlockInfo> list = settings.getRandomPalette(this.palettes, position).blocks();

            if ((!list.isEmpty() || !settings.isIgnoreEntities() && !this.entityInfoList.isEmpty()) && this.size.getX() >= 1 && this.size.getY() >= 1 && this.size.getZ() >= 1) {
                BoundingBox boundingbox = settings.getBoundingBox();
                List<BlockPos> list1 = Lists.newArrayListWithCapacity(settings.shouldApplyWaterlogging() ? list.size() : 0);
                List<BlockPos> list2 = Lists.newArrayListWithCapacity(settings.shouldApplyWaterlogging() ? list.size() : 0);
                List<Pair<BlockPos, CompoundTag>> list3 = Lists.newArrayListWithCapacity(list.size());
                int j = Integer.MAX_VALUE;
                int k = Integer.MAX_VALUE;
                int l = Integer.MAX_VALUE;
                int i1 = Integer.MIN_VALUE;
                int j1 = Integer.MIN_VALUE;
                int k1 = Integer.MIN_VALUE;
                List<StructureTemplate.StructureBlockInfo> list4 = processBlockInfos(level, position, referencePos, settings, list);

                try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(StructureTemplate.LOGGER)) {
                    for (StructureTemplate.StructureBlockInfo structuretemplate_structureblockinfo : list4) {
                        BlockPos blockpos2 = structuretemplate_structureblockinfo.pos;

                        if (boundingbox == null || boundingbox.isInside(blockpos2)) {
                            FluidState fluidstate = settings.shouldApplyWaterlogging() ? level.getFluidState(blockpos2) : null;
                            BlockState blockstate = structuretemplate_structureblockinfo.state.mirror(settings.getMirror()).rotate(settings.getRotation());

                            if (structuretemplate_structureblockinfo.nbt != null) {
                                level.setBlock(blockpos2, Blocks.BARRIER.defaultBlockState(), 820);
                            }

                            if (level.setBlock(blockpos2, blockstate, updateMode)) {
                                j = Math.min(j, blockpos2.getX());
                                k = Math.min(k, blockpos2.getY());
                                l = Math.min(l, blockpos2.getZ());
                                i1 = Math.max(i1, blockpos2.getX());
                                j1 = Math.max(j1, blockpos2.getY());
                                k1 = Math.max(k1, blockpos2.getZ());
                                list3.add(Pair.of(blockpos2, structuretemplate_structureblockinfo.nbt));
                                if (structuretemplate_structureblockinfo.nbt != null) {
                                    BlockEntity blockentity = level.getBlockEntity(blockpos2);

                                    if (blockentity != null) {
                                        if (!SharedConstants.DEBUG_STRUCTURE_EDIT_MODE && blockentity instanceof RandomizableContainer) {
                                            structuretemplate_structureblockinfo.nbt.putLong("LootTableSeed", random.nextLong());
                                        }

                                        blockentity.loadWithComponents(TagValueInput.create(problemreporter_scopedcollector.forChild(blockentity.problemPath()), level.registryAccess(), structuretemplate_structureblockinfo.nbt));
                                    }
                                }

                                if (fluidstate != null) {
                                    if (blockstate.getFluidState().isSource()) {
                                        list2.add(blockpos2);
                                    } else if (blockstate.getBlock() instanceof LiquidBlockContainer) {
                                        ((LiquidBlockContainer) blockstate.getBlock()).placeLiquid(level, blockpos2, blockstate, fluidstate);
                                        if (!fluidstate.isSource()) {
                                            list1.add(blockpos2);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    boolean flag = true;
                    Direction[] adirection = new Direction[]{Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

                    while (flag && !((List) list1).isEmpty()) {
                        flag = false;
                        Iterator<BlockPos> iterator = list1.iterator();

                        while (iterator.hasNext()) {
                            BlockPos blockpos3 = (BlockPos) iterator.next();
                            FluidState fluidstate1 = level.getFluidState(blockpos3);

                            for (int l1 = 0; l1 < adirection.length && !fluidstate1.isSource(); ++l1) {
                                BlockPos blockpos4 = blockpos3.relative(adirection[l1]);
                                FluidState fluidstate2 = level.getFluidState(blockpos4);

                                if (fluidstate2.isSource() && !list2.contains(blockpos4)) {
                                    fluidstate1 = fluidstate2;
                                }
                            }

                            if (fluidstate1.isSource()) {
                                BlockState blockstate1 = level.getBlockState(blockpos3);
                                Block block = blockstate1.getBlock();

                                if (block instanceof LiquidBlockContainer) {
                                    ((LiquidBlockContainer) block).placeLiquid(level, blockpos3, blockstate1, fluidstate1);
                                    flag = true;
                                    iterator.remove();
                                }
                            }
                        }
                    }

                    if (j <= i1) {
                        if (!settings.getKnownShape()) {
                            DiscreteVoxelShape discretevoxelshape = new BitSetDiscreteVoxelShape(i1 - j + 1, j1 - k + 1, k1 - l + 1);
                            int i2 = j;
                            int j2 = k;
                            int k2 = l;

                            for (Pair<BlockPos, CompoundTag> pair : list3) {
                                BlockPos blockpos5 = (BlockPos) pair.getFirst();

                                discretevoxelshape.fill(blockpos5.getX() - i2, blockpos5.getY() - j2, blockpos5.getZ() - k2);
                            }

                            updateShapeAtEdge(level, updateMode, discretevoxelshape, i2, j2, k2);
                        }

                        for (Pair<BlockPos, CompoundTag> pair1 : list3) {
                            BlockPos blockpos6 = (BlockPos) pair1.getFirst();

                            if (!settings.getKnownShape()) {
                                BlockState blockstate2 = level.getBlockState(blockpos6);
                                BlockState blockstate3 = Block.updateFromNeighbourShapes(blockstate2, level, blockpos6);

                                if (blockstate2 != blockstate3) {
                                    level.setBlock(blockpos6, blockstate3, updateMode & -2 | 16);
                                }

                                level.updateNeighborsAt(blockpos6, blockstate3.getBlock());
                            }

                            if (pair1.getSecond() != null) {
                                BlockEntity blockentity1 = level.getBlockEntity(blockpos6);

                                if (blockentity1 != null) {
                                    blockentity1.setChanged();
                                }
                            }
                        }
                    }

                    if (!settings.isIgnoreEntities()) {
                        this.placeEntities(level, position, settings.getMirror(), settings.getRotation(), settings.getRotationPivot(), boundingbox, settings.shouldFinalizeEntities(), problemreporter_scopedcollector);
                    }
                }

                return true;
            } else {
                return false;
            }
        }
    }

    public static void updateShapeAtEdge(LevelAccessor level, @Block.UpdateFlags int updateMode, DiscreteVoxelShape shape, BlockPos pos) {
        updateShapeAtEdge(level, updateMode, shape, pos.getX(), pos.getY(), pos.getZ());
    }

    public static void updateShapeAtEdge(LevelAccessor level, @Block.UpdateFlags int updateMode, DiscreteVoxelShape shape, int startX, int startY, int startZ) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos blockpos_mutableblockpos1 = new BlockPos.MutableBlockPos();

        shape.forAllFaces((direction, i1, j1, k1) -> {
            blockpos_mutableblockpos.set(startX + i1, startY + j1, startZ + k1);
            blockpos_mutableblockpos1.setWithOffset(blockpos_mutableblockpos, direction);
            BlockState blockstate = level.getBlockState(blockpos_mutableblockpos);
            BlockState blockstate1 = level.getBlockState(blockpos_mutableblockpos1);
            BlockState blockstate2 = blockstate.updateShape(level, level, blockpos_mutableblockpos, direction, blockpos_mutableblockpos1, blockstate1, level.getRandom());

            if (blockstate != blockstate2) {
                level.setBlock(blockpos_mutableblockpos, blockstate2, updateMode & -2);
            }

            BlockState blockstate3 = blockstate1.updateShape(level, level, blockpos_mutableblockpos1, direction.getOpposite(), blockpos_mutableblockpos, blockstate2, level.getRandom());

            if (blockstate1 != blockstate3) {
                level.setBlock(blockpos_mutableblockpos1, blockstate3, updateMode & -2);
            }

        });
    }

    public static List<StructureTemplate.StructureBlockInfo> processBlockInfos(ServerLevelAccessor level, BlockPos position, BlockPos referencePos, StructurePlaceSettings settings, List<StructureTemplate.StructureBlockInfo> blockInfoList) {
        List<StructureTemplate.StructureBlockInfo> list1 = new ArrayList();
        List<StructureTemplate.StructureBlockInfo> list2 = new ArrayList();

        for (StructureTemplate.StructureBlockInfo structuretemplate_structureblockinfo : blockInfoList) {
            BlockPos blockpos2 = calculateRelativePosition(settings, structuretemplate_structureblockinfo.pos).offset(position);
            StructureTemplate.StructureBlockInfo structuretemplate_structureblockinfo1 = new StructureTemplate.StructureBlockInfo(blockpos2, structuretemplate_structureblockinfo.state, structuretemplate_structureblockinfo.nbt != null ? structuretemplate_structureblockinfo.nbt.copy() : null);

            for (Iterator<StructureProcessor> iterator = settings.getProcessors().iterator(); structuretemplate_structureblockinfo1 != null && iterator.hasNext(); structuretemplate_structureblockinfo1 = ((StructureProcessor) iterator.next()).processBlock(level, position, referencePos, structuretemplate_structureblockinfo, structuretemplate_structureblockinfo1, settings)) {
                ;
            }

            if (structuretemplate_structureblockinfo1 != null) {
                list2.add(structuretemplate_structureblockinfo1);
                list1.add(structuretemplate_structureblockinfo);
            }
        }

        for (StructureProcessor structureprocessor : settings.getProcessors()) {
            list2 = structureprocessor.finalizeProcessing(level, position, referencePos, list1, list2, settings);
        }

        return list2;
    }

    private void placeEntities(ServerLevelAccessor level, BlockPos position, Mirror mirror, Rotation rotation, BlockPos pivot, @Nullable BoundingBox boundingBox, boolean finalizeEntities, ProblemReporter problemReporter) {
        for (StructureTemplate.StructureEntityInfo structuretemplate_structureentityinfo : this.entityInfoList) {
            BlockPos blockpos2 = transform(structuretemplate_structureentityinfo.blockPos, mirror, rotation, pivot).offset(position);

            if (boundingBox == null || boundingBox.isInside(blockpos2)) {
                CompoundTag compoundtag = structuretemplate_structureentityinfo.nbt.copy();
                Vec3 vec3 = transform(structuretemplate_structureentityinfo.pos, mirror, rotation, pivot);
                Vec3 vec31 = vec3.add((double) position.getX(), (double) position.getY(), (double) position.getZ());
                ListTag listtag = new ListTag();

                listtag.add(DoubleTag.valueOf(vec31.x));
                listtag.add(DoubleTag.valueOf(vec31.y));
                listtag.add(DoubleTag.valueOf(vec31.z));
                compoundtag.put("Pos", listtag);
                compoundtag.remove("UUID");
                createEntityIgnoreException(problemReporter, level, compoundtag).ifPresent((entity) -> {
                    float f = entity.rotate(rotation);

                    f += entity.mirror(mirror) - entity.getYRot();
                    entity.snapTo(vec31.x, vec31.y, vec31.z, f, entity.getXRot());
                    entity.setYBodyRot(f);
                    entity.setYHeadRot(f);
                    if (finalizeEntities && entity instanceof Mob mob) {
                        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(vec31)), EntitySpawnReason.STRUCTURE, (SpawnGroupData) null);
                    }

                    level.addFreshEntityWithPassengers(entity);
                });
            }
        }

    }

    private static Optional<Entity> createEntityIgnoreException(ProblemReporter reporter, ServerLevelAccessor level, CompoundTag tag) {
        try {
            return EntityType.create(TagValueInput.create(reporter, level.registryAccess(), tag), level.getLevel(), EntitySpawnReason.STRUCTURE);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    public Vec3i getSize(Rotation rotation) {
        switch (rotation) {
            case COUNTERCLOCKWISE_90:
            case CLOCKWISE_90:
                return new Vec3i(this.size.getZ(), this.size.getY(), this.size.getX());
            default:
                return this.size;
        }
    }

    public static BlockPos transform(BlockPos pos, Mirror mirror, Rotation rotation, BlockPos pivot) {
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();
        boolean flag = true;

        switch (mirror) {
            case LEFT_RIGHT:
                k = -k;
                break;
            case FRONT_BACK:
                i = -i;
                break;
            default:
                flag = false;
        }

        int l = pivot.getX();
        int i1 = pivot.getZ();

        switch (rotation) {
            case COUNTERCLOCKWISE_90:
                return new BlockPos(l - i1 + k, j, l + i1 - i);
            case CLOCKWISE_90:
                return new BlockPos(l + i1 - k, j, i1 - l + i);
            case CLOCKWISE_180:
                return new BlockPos(l + l - i, j, i1 + i1 - k);
            default:
                return flag ? new BlockPos(i, j, k) : pos;
        }
    }

    public static Vec3 transform(Vec3 pos, Mirror mirror, Rotation rotation, BlockPos pivot) {
        double d0 = pos.x;
        double d1 = pos.y;
        double d2 = pos.z;
        boolean flag = true;

        switch (mirror) {
            case LEFT_RIGHT:
                d2 = 1.0D - d2;
                break;
            case FRONT_BACK:
                d0 = 1.0D - d0;
                break;
            default:
                flag = false;
        }

        int i = pivot.getX();
        int j = pivot.getZ();

        switch (rotation) {
            case COUNTERCLOCKWISE_90:
                return new Vec3((double) (i - j) + d2, d1, (double) (i + j + 1) - d0);
            case CLOCKWISE_90:
                return new Vec3((double) (i + j + 1) - d2, d1, (double) (j - i) + d0);
            case CLOCKWISE_180:
                return new Vec3((double) (i + i + 1) - d0, d1, (double) (j + j + 1) - d2);
            default:
                return flag ? new Vec3(d0, d1, d2) : pos;
        }
    }

    public BlockPos getZeroPositionWithTransform(BlockPos zeroPos, Mirror mirror, Rotation rotation) {
        return getZeroPositionWithTransform(zeroPos, mirror, rotation, this.getSize().getX(), this.getSize().getZ());
    }

    public static BlockPos getZeroPositionWithTransform(BlockPos zeroPos, Mirror mirror, Rotation rotation, int sizeX, int sizeZ) {
        --sizeX;
        --sizeZ;
        int k = mirror == Mirror.FRONT_BACK ? sizeX : 0;
        int l = mirror == Mirror.LEFT_RIGHT ? sizeZ : 0;
        BlockPos blockpos1 = zeroPos;

        switch (rotation) {
            case COUNTERCLOCKWISE_90:
                blockpos1 = zeroPos.offset(l, 0, sizeX - k);
                break;
            case CLOCKWISE_90:
                blockpos1 = zeroPos.offset(sizeZ - l, 0, k);
                break;
            case CLOCKWISE_180:
                blockpos1 = zeroPos.offset(sizeX - k, 0, sizeZ - l);
                break;
            case NONE:
                blockpos1 = zeroPos.offset(k, 0, l);
        }

        return blockpos1;
    }

    public BoundingBox getBoundingBox(StructurePlaceSettings settings, BlockPos position) {
        return this.getBoundingBox(position, settings.getRotation(), settings.getRotationPivot(), settings.getMirror());
    }

    public BoundingBox getBoundingBox(BlockPos position, Rotation rotation, BlockPos pivot, Mirror mirror) {
        return getBoundingBox(position, rotation, pivot, mirror, this.size);
    }

    @VisibleForTesting
    protected static BoundingBox getBoundingBox(BlockPos position, Rotation rotation, BlockPos pivot, Mirror mirror, Vec3i size) {
        Vec3i vec3i1 = size.offset(-1, -1, -1);
        BlockPos blockpos2 = transform(BlockPos.ZERO, mirror, rotation, pivot);
        BlockPos blockpos3 = transform(BlockPos.ZERO.offset(vec3i1), mirror, rotation, pivot);

        return BoundingBox.fromCorners(blockpos2, blockpos3).move(position);
    }

    public CompoundTag save(CompoundTag tag) {
        if (this.palettes.isEmpty()) {
            tag.put("blocks", new ListTag());
            tag.put("palette", new ListTag());
        } else {
            List<StructureTemplate.SimplePalette> list = Lists.newArrayList();
            StructureTemplate.SimplePalette structuretemplate_simplepalette = new StructureTemplate.SimplePalette();

            list.add(structuretemplate_simplepalette);

            for (int i = 1; i < this.palettes.size(); ++i) {
                list.add(new StructureTemplate.SimplePalette());
            }

            ListTag listtag = new ListTag();
            List<StructureTemplate.StructureBlockInfo> list1 = ((StructureTemplate.Palette) this.palettes.get(0)).blocks();

            for (int j = 0; j < list1.size(); ++j) {
                StructureTemplate.StructureBlockInfo structuretemplate_structureblockinfo = (StructureTemplate.StructureBlockInfo) list1.get(j);
                CompoundTag compoundtag1 = new CompoundTag();

                compoundtag1.put("pos", this.newIntegerList(structuretemplate_structureblockinfo.pos.getX(), structuretemplate_structureblockinfo.pos.getY(), structuretemplate_structureblockinfo.pos.getZ()));
                int k = structuretemplate_simplepalette.idFor(structuretemplate_structureblockinfo.state);

                compoundtag1.putInt("state", k);
                if (structuretemplate_structureblockinfo.nbt != null) {
                    compoundtag1.put("nbt", structuretemplate_structureblockinfo.nbt);
                }

                listtag.add(compoundtag1);

                for (int l = 1; l < this.palettes.size(); ++l) {
                    StructureTemplate.SimplePalette structuretemplate_simplepalette1 = (StructureTemplate.SimplePalette) list.get(l);

                    structuretemplate_simplepalette1.addMapping(((StructureTemplate.StructureBlockInfo) ((StructureTemplate.Palette) this.palettes.get(l)).blocks().get(j)).state, k);
                }
            }

            tag.put("blocks", listtag);
            if (list.size() == 1) {
                ListTag listtag1 = new ListTag();

                for (BlockState blockstate : structuretemplate_simplepalette) {
                    listtag1.add(NbtUtils.writeBlockState(blockstate));
                }

                tag.put("palette", listtag1);
            } else {
                ListTag listtag2 = new ListTag();

                for (StructureTemplate.SimplePalette structuretemplate_simplepalette2 : list) {
                    ListTag listtag3 = new ListTag();

                    for (BlockState blockstate1 : structuretemplate_simplepalette2) {
                        listtag3.add(NbtUtils.writeBlockState(blockstate1));
                    }

                    listtag2.add(listtag3);
                }

                tag.put("palettes", listtag2);
            }
        }

        ListTag listtag4 = new ListTag();

        for (StructureTemplate.StructureEntityInfo structuretemplate_structureentityinfo : this.entityInfoList) {
            CompoundTag compoundtag2 = new CompoundTag();

            compoundtag2.put("pos", this.newDoubleList(structuretemplate_structureentityinfo.pos.x, structuretemplate_structureentityinfo.pos.y, structuretemplate_structureentityinfo.pos.z));
            compoundtag2.put("blockPos", this.newIntegerList(structuretemplate_structureentityinfo.blockPos.getX(), structuretemplate_structureentityinfo.blockPos.getY(), structuretemplate_structureentityinfo.blockPos.getZ()));
            if (structuretemplate_structureentityinfo.nbt != null) {
                compoundtag2.put("nbt", structuretemplate_structureentityinfo.nbt);
            }

            listtag4.add(compoundtag2);
        }

        tag.put("entities", listtag4);
        tag.put("size", this.newIntegerList(this.size.getX(), this.size.getY(), this.size.getZ()));
        return NbtUtils.addCurrentDataVersion(tag);
    }

    public void load(HolderGetter<Block> blockLookup, CompoundTag tag) {
        this.palettes.clear();
        this.entityInfoList.clear();
        ListTag listtag = tag.getListOrEmpty("size");

        this.size = new Vec3i(listtag.getIntOr(0, 0), listtag.getIntOr(1, 0), listtag.getIntOr(2, 0));
        ListTag listtag1 = tag.getListOrEmpty("blocks");
        Optional<ListTag> optional = tag.getList("palettes");

        if (optional.isPresent()) {
            for (int i = 0; i < ((ListTag) optional.get()).size(); ++i) {
                this.loadPalette(blockLookup, ((ListTag) optional.get()).getListOrEmpty(i), listtag1);
            }
        } else {
            this.loadPalette(blockLookup, tag.getListOrEmpty("palette"), listtag1);
        }

        tag.getListOrEmpty("entities").compoundStream().forEach((compoundtag1) -> {
            ListTag listtag2 = compoundtag1.getListOrEmpty("pos");
            Vec3 vec3 = new Vec3(listtag2.getDoubleOr(0, 0.0D), listtag2.getDoubleOr(1, 0.0D), listtag2.getDoubleOr(2, 0.0D));
            ListTag listtag3 = compoundtag1.getListOrEmpty("blockPos");
            BlockPos blockpos = new BlockPos(listtag3.getIntOr(0, 0), listtag3.getIntOr(1, 0), listtag3.getIntOr(2, 0));

            compoundtag1.getCompound("nbt").ifPresent((compoundtag2) -> {
                this.entityInfoList.add(new StructureTemplate.StructureEntityInfo(vec3, blockpos, compoundtag2));
            });
        });
    }

    private void loadPalette(HolderGetter<Block> blockLookup, ListTag paletteList, ListTag blockList) {
        StructureTemplate.SimplePalette structuretemplate_simplepalette = new StructureTemplate.SimplePalette();

        for (int i = 0; i < paletteList.size(); ++i) {
            structuretemplate_simplepalette.addMapping(NbtUtils.readBlockState(blockLookup, paletteList.getCompoundOrEmpty(i)), i);
        }

        List<StructureTemplate.StructureBlockInfo> list = Lists.newArrayList();
        List<StructureTemplate.StructureBlockInfo> list1 = Lists.newArrayList();
        List<StructureTemplate.StructureBlockInfo> list2 = Lists.newArrayList();

        blockList.compoundStream().forEach((compoundtag) -> {
            ListTag listtag2 = compoundtag.getListOrEmpty("pos");
            BlockPos blockpos = new BlockPos(listtag2.getIntOr(0, 0), listtag2.getIntOr(1, 0), listtag2.getIntOr(2, 0));
            BlockState blockstate = structuretemplate_simplepalette.stateFor(compoundtag.getIntOr("state", 0));
            CompoundTag compoundtag1 = (CompoundTag) compoundtag.getCompound("nbt").orElse((Object) null);
            StructureTemplate.StructureBlockInfo structuretemplate_structureblockinfo = new StructureTemplate.StructureBlockInfo(blockpos, blockstate, compoundtag1);

            addToLists(structuretemplate_structureblockinfo, list, list1, list2);
        });
        List<StructureTemplate.StructureBlockInfo> list3 = buildInfoList(list, list1, list2);

        this.palettes.add(new StructureTemplate.Palette(list3));
    }

    private ListTag newIntegerList(int... values) {
        ListTag listtag = new ListTag();

        for (int i : values) {
            listtag.add(IntTag.valueOf(i));
        }

        return listtag;
    }

    private ListTag newDoubleList(double... values) {
        ListTag listtag = new ListTag();

        for (double d0 : values) {
            listtag.add(DoubleTag.valueOf(d0));
        }

        return listtag;
    }

    public static JigsawBlockEntity.JointType getJointType(CompoundTag nbt, BlockState state) {
        return (JigsawBlockEntity.JointType) nbt.read("joint", JigsawBlockEntity.JointType.CODEC).orElseGet(() -> {
            return getDefaultJointType(state);
        });
    }

    public static JigsawBlockEntity.JointType getDefaultJointType(BlockState state) {
        return JigsawBlock.getFrontFacing(state).getAxis().isHorizontal() ? JigsawBlockEntity.JointType.ALIGNED : JigsawBlockEntity.JointType.ROLLABLE;
    }

    private static class SimplePalette implements Iterable<BlockState> {

        public static final BlockState DEFAULT_BLOCK_STATE = Blocks.AIR.defaultBlockState();
        private final IdMapper<BlockState> ids = new IdMapper<BlockState>(16);
        private int lastId;

        private SimplePalette() {}

        public int idFor(BlockState state) {
            int i = this.ids.getId(state);

            if (i == -1) {
                i = this.lastId++;
                this.ids.addMapping(state, i);
            }

            return i;
        }

        public @Nullable BlockState stateFor(int index) {
            BlockState blockstate = (BlockState) this.ids.byId(index);

            return blockstate == null ? StructureTemplate.SimplePalette.DEFAULT_BLOCK_STATE : blockstate;
        }

        public Iterator<BlockState> iterator() {
            return this.ids.iterator();
        }

        public void addMapping(BlockState state, int id) {
            this.ids.addMapping(state, id);
        }
    }

    public static record StructureBlockInfo(BlockPos pos, BlockState state, @Nullable CompoundTag nbt) {

        public String toString() {
            return String.format(Locale.ROOT, "<StructureBlockInfo | %s | %s | %s>", this.pos, this.state, this.nbt);
        }
    }

    public static record JigsawBlockInfo(StructureTemplate.StructureBlockInfo info, JigsawBlockEntity.JointType jointType, Identifier name, ResourceKey<StructureTemplatePool> pool, Identifier target, int placementPriority, int selectionPriority) {

        public static StructureTemplate.JigsawBlockInfo of(StructureTemplate.StructureBlockInfo info) {
            CompoundTag compoundtag = (CompoundTag) Objects.requireNonNull(info.nbt(), () -> {
                return String.valueOf(info) + " nbt was null";
            });

            return new StructureTemplate.JigsawBlockInfo(info, StructureTemplate.getJointType(compoundtag, info.state()), (Identifier) compoundtag.read("name", Identifier.CODEC).orElse(JigsawBlockEntity.EMPTY_ID), (ResourceKey) compoundtag.read("pool", JigsawBlockEntity.POOL_CODEC).orElse(Pools.EMPTY), (Identifier) compoundtag.read("target", Identifier.CODEC).orElse(JigsawBlockEntity.EMPTY_ID), compoundtag.getIntOr("placement_priority", 0), compoundtag.getIntOr("selection_priority", 0));
        }

        public String toString() {
            return String.format(Locale.ROOT, "<JigsawBlockInfo | %s | %s | name: %s | pool: %s | target: %s | placement: %d | selection: %d | %s>", this.info.pos, this.info.state, this.name, this.pool.identifier(), this.target, this.placementPriority, this.selectionPriority, this.info.nbt);
        }

        public StructureTemplate.JigsawBlockInfo withInfo(StructureTemplate.StructureBlockInfo info) {
            return new StructureTemplate.JigsawBlockInfo(info, this.jointType, this.name, this.pool, this.target, this.placementPriority, this.selectionPriority);
        }
    }

    public static class StructureEntityInfo {

        public final Vec3 pos;
        public final BlockPos blockPos;
        public final CompoundTag nbt;

        public StructureEntityInfo(Vec3 pos, BlockPos blockPos, CompoundTag nbt) {
            this.pos = pos;
            this.blockPos = blockPos;
            this.nbt = nbt;
        }
    }

    public static final class Palette {

        private final List<StructureTemplate.StructureBlockInfo> blocks;
        private final Map<Block, List<StructureTemplate.StructureBlockInfo>> cache = Maps.newHashMap();
        private @Nullable List<StructureTemplate.JigsawBlockInfo> cachedJigsaws;

        private Palette(List<StructureTemplate.StructureBlockInfo> blocks) {
            this.blocks = blocks;
        }

        public List<StructureTemplate.JigsawBlockInfo> jigsaws() {
            if (this.cachedJigsaws == null) {
                this.cachedJigsaws = this.blocks(Blocks.JIGSAW).stream().map(StructureTemplate.JigsawBlockInfo::of).toList();
            }

            return this.cachedJigsaws;
        }

        public List<StructureTemplate.StructureBlockInfo> blocks() {
            return this.blocks;
        }

        public List<StructureTemplate.StructureBlockInfo> blocks(Block filter) {
            return (List) this.cache.computeIfAbsent(filter, (block1) -> {
                return (List) this.blocks.stream().filter((structuretemplate_structureblockinfo) -> {
                    return structuretemplate_structureblockinfo.state.is(block1);
                }).collect(Collectors.toList());
            });
        }
    }
}
