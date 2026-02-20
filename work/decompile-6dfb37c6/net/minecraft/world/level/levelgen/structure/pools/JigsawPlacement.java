package net.minecraft.world.level.levelgen.structure.pools;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SequencedPriorityIterator;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.mutable.MutableObject;
import org.slf4j.Logger;

public class JigsawPlacement {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int UNSET_HEIGHT = Integer.MIN_VALUE;

    public JigsawPlacement() {}

    public static Optional<Structure.GenerationStub> addPieces(Structure.GenerationContext context, Holder<StructureTemplatePool> startPool, Optional<Identifier> startJigsaw, int maxDepth, BlockPos position, boolean doExpansionHack, Optional<Heightmap.Types> projectStartToHeightmap, JigsawStructure.MaxDistance maxDistanceFromCenter, PoolAliasLookup poolAliasLookup, DimensionPadding dimensionPadding, LiquidSettings liquidSettings) {
        RegistryAccess registryaccess = context.registryAccess();
        ChunkGenerator chunkgenerator = context.chunkGenerator();
        StructureTemplateManager structuretemplatemanager = context.structureTemplateManager();
        LevelHeightAccessor levelheightaccessor = context.heightAccessor();
        WorldgenRandom worldgenrandom = context.random();
        Registry<StructureTemplatePool> registry = registryaccess.lookupOrThrow(Registries.TEMPLATE_POOL);
        Rotation rotation = Rotation.getRandom(worldgenrandom);
        StructureTemplatePool structuretemplatepool = (StructureTemplatePool) startPool.unwrapKey().flatMap((resourcekey) -> {
            return registry.getOptional(poolAliasLookup.lookup(resourcekey));
        }).orElse(startPool.value());
        StructurePoolElement structurepoolelement = structuretemplatepool.getRandomTemplate(worldgenrandom);

        if (structurepoolelement == EmptyPoolElement.INSTANCE) {
            return Optional.empty();
        } else {
            BlockPos blockpos1;

            if (startJigsaw.isPresent()) {
                Identifier identifier = (Identifier) startJigsaw.get();
                Optional<BlockPos> optional2 = getRandomNamedJigsaw(structurepoolelement, identifier, position, rotation, structuretemplatemanager, worldgenrandom);

                if (optional2.isEmpty()) {
                    JigsawPlacement.LOGGER.error("No starting jigsaw {} found in start pool {}", identifier, startPool.unwrapKey().map((resourcekey) -> {
                        return resourcekey.identifier().toString();
                    }).orElse("<unregistered>"));
                    return Optional.empty();
                }

                blockpos1 = (BlockPos) optional2.get();
            } else {
                blockpos1 = position;
            }

            Vec3i vec3i = blockpos1.subtract(position);
            BlockPos blockpos2 = position.subtract(vec3i);
            PoolElementStructurePiece poolelementstructurepiece = new PoolElementStructurePiece(structuretemplatemanager, structurepoolelement, blockpos2, structurepoolelement.getGroundLevelDelta(), rotation, structurepoolelement.getBoundingBox(structuretemplatemanager, blockpos2, rotation), liquidSettings);
            BoundingBox boundingbox = poolelementstructurepiece.getBoundingBox();
            int j = (boundingbox.maxX() + boundingbox.minX()) / 2;
            int k = (boundingbox.maxZ() + boundingbox.minZ()) / 2;
            int l = projectStartToHeightmap.isEmpty() ? blockpos2.getY() : position.getY() + chunkgenerator.getFirstFreeHeight(j, k, (Heightmap.Types) projectStartToHeightmap.get(), levelheightaccessor, context.randomState());
            int i1 = boundingbox.minY() + poolelementstructurepiece.getGroundLevelDelta();

            poolelementstructurepiece.move(0, l - i1, 0);
            if (isStartTooCloseToWorldHeightLimits(levelheightaccessor, dimensionPadding, poolelementstructurepiece.getBoundingBox())) {
                JigsawPlacement.LOGGER.debug("Center piece {} with bounding box {} does not fit dimension padding {}", new Object[]{structurepoolelement, poolelementstructurepiece.getBoundingBox(), dimensionPadding});
                return Optional.empty();
            } else {
                int j1 = l + vec3i.getY();

                return Optional.of(new Structure.GenerationStub(new BlockPos(j, j1, k), (structurepiecesbuilder) -> {
                    List<PoolElementStructurePiece> list = Lists.newArrayList();

                    list.add(poolelementstructurepiece);
                    if (maxDepth > 0) {
                        AABB aabb = new AABB((double) (j - maxDistanceFromCenter.horizontal()), (double) Math.max(j1 - maxDistanceFromCenter.vertical(), levelheightaccessor.getMinY() + dimensionPadding.bottom()), (double) (k - maxDistanceFromCenter.horizontal()), (double) (j + maxDistanceFromCenter.horizontal() + 1), (double) Math.min(j1 + maxDistanceFromCenter.vertical() + 1, levelheightaccessor.getMaxY() + 1 - dimensionPadding.top()), (double) (k + maxDistanceFromCenter.horizontal() + 1));
                        VoxelShape voxelshape = Shapes.join(Shapes.create(aabb), Shapes.create(AABB.of(boundingbox)), BooleanOp.ONLY_FIRST);

                        addPieces(context.randomState(), maxDepth, doExpansionHack, chunkgenerator, structuretemplatemanager, levelheightaccessor, worldgenrandom, registry, poolelementstructurepiece, list, voxelshape, poolAliasLookup, liquidSettings);
                        Objects.requireNonNull(structurepiecesbuilder);
                        list.forEach(structurepiecesbuilder::addPiece);
                    }
                }));
            }
        }
    }

    private static boolean isStartTooCloseToWorldHeightLimits(LevelHeightAccessor heightAccessor, DimensionPadding dimensionPadding, BoundingBox centerPieceBb) {
        if (dimensionPadding == DimensionPadding.ZERO) {
            return false;
        } else {
            int i = heightAccessor.getMinY() + dimensionPadding.bottom();
            int j = heightAccessor.getMaxY() - dimensionPadding.top();

            return centerPieceBb.minY() < i || centerPieceBb.maxY() > j;
        }
    }

    private static Optional<BlockPos> getRandomNamedJigsaw(StructurePoolElement element, Identifier targetJigsawId, BlockPos position, Rotation rotation, StructureTemplateManager structureTemplateManager, WorldgenRandom random) {
        for (StructureTemplate.JigsawBlockInfo structuretemplate_jigsawblockinfo : element.getShuffledJigsawBlocks(structureTemplateManager, position, rotation, random)) {
            if (targetJigsawId.equals(structuretemplate_jigsawblockinfo.name())) {
                return Optional.of(structuretemplate_jigsawblockinfo.info().pos());
            }
        }

        return Optional.empty();
    }

    private static void addPieces(RandomState randomState, int maxDepth, boolean doExpansionHack, ChunkGenerator chunkGenerator, StructureTemplateManager structureTemplateManager, LevelHeightAccessor heightAccessor, RandomSource random, Registry<StructureTemplatePool> pools, PoolElementStructurePiece centerPiece, List<PoolElementStructurePiece> pieces, VoxelShape shape, PoolAliasLookup poolAliasLookup, LiquidSettings liquidSettings) {
        JigsawPlacement.Placer jigsawplacement_placer = new JigsawPlacement.Placer(pools, maxDepth, chunkGenerator, structureTemplateManager, pieces, random);

        jigsawplacement_placer.tryPlacingChildren(centerPiece, new MutableObject(shape), 0, doExpansionHack, heightAccessor, randomState, poolAliasLookup, liquidSettings);

        while (jigsawplacement_placer.placing.hasNext()) {
            JigsawPlacement.PieceState jigsawplacement_piecestate = (JigsawPlacement.PieceState) jigsawplacement_placer.placing.next();

            jigsawplacement_placer.tryPlacingChildren(jigsawplacement_piecestate.piece, jigsawplacement_piecestate.free, jigsawplacement_piecestate.depth, doExpansionHack, heightAccessor, randomState, poolAliasLookup, liquidSettings);
        }

    }

    public static boolean generateJigsaw(ServerLevel level, Holder<StructureTemplatePool> pool, Identifier target, int maxDepth, BlockPos position, boolean keepJigsaws) {
        ChunkGenerator chunkgenerator = level.getChunkSource().getGenerator();
        StructureTemplateManager structuretemplatemanager = level.getStructureManager();
        StructureManager structuremanager = level.structureManager();
        RandomSource randomsource = level.getRandom();
        Structure.GenerationContext structure_generationcontext = new Structure.GenerationContext(level.registryAccess(), chunkgenerator, chunkgenerator.getBiomeSource(), level.getChunkSource().randomState(), structuretemplatemanager, level.getSeed(), new ChunkPos(position), level, (holder1) -> {
            return true;
        });
        Optional<Structure.GenerationStub> optional = addPieces(structure_generationcontext, pool, Optional.of(target), maxDepth, position, false, Optional.empty(), new JigsawStructure.MaxDistance(128), PoolAliasLookup.EMPTY, JigsawStructure.DEFAULT_DIMENSION_PADDING, JigsawStructure.DEFAULT_LIQUID_SETTINGS);

        if (optional.isPresent()) {
            StructurePiecesBuilder structurepiecesbuilder = ((Structure.GenerationStub) optional.get()).getPiecesBuilder();

            for (StructurePiece structurepiece : structurepiecesbuilder.build().pieces()) {
                if (structurepiece instanceof PoolElementStructurePiece) {
                    PoolElementStructurePiece poolelementstructurepiece = (PoolElementStructurePiece) structurepiece;

                    poolelementstructurepiece.place(level, structuremanager, chunkgenerator, randomsource, BoundingBox.infinite(), position, keepJigsaws);
                }
            }

            return true;
        } else {
            return false;
        }
    }

    private static record PieceState(PoolElementStructurePiece piece, MutableObject<VoxelShape> free, int depth) {

    }

    private static final class Placer {

        private final Registry<StructureTemplatePool> pools;
        private final int maxDepth;
        private final ChunkGenerator chunkGenerator;
        private final StructureTemplateManager structureTemplateManager;
        private final List<? super PoolElementStructurePiece> pieces;
        private final RandomSource random;
        private final SequencedPriorityIterator<JigsawPlacement.PieceState> placing = new SequencedPriorityIterator<JigsawPlacement.PieceState>();

        private Placer(Registry<StructureTemplatePool> pools, int maxDepth, ChunkGenerator chunkGenerator, StructureTemplateManager structureTemplateManager, List<? super PoolElementStructurePiece> pieces, RandomSource random) {
            this.pools = pools;
            this.maxDepth = maxDepth;
            this.chunkGenerator = chunkGenerator;
            this.structureTemplateManager = structureTemplateManager;
            this.pieces = pieces;
            this.random = random;
        }

        private void tryPlacingChildren(PoolElementStructurePiece sourcePiece, MutableObject<VoxelShape> contextFree, int depth, boolean doExpansionHack, LevelHeightAccessor heightAccessor, RandomState randomState, PoolAliasLookup poolAliasLookup, LiquidSettings liquidSettings) {
            StructurePoolElement structurepoolelement = sourcePiece.getElement();
            BlockPos blockpos = sourcePiece.getPosition();
            Rotation rotation = sourcePiece.getRotation();
            StructureTemplatePool.Projection structuretemplatepool_projection = structurepoolelement.getProjection();
            boolean flag1 = structuretemplatepool_projection == StructureTemplatePool.Projection.RIGID;
            MutableObject<VoxelShape> mutableobject1 = new MutableObject();
            BoundingBox boundingbox = sourcePiece.getBoundingBox();
            int j = boundingbox.minY();

            label129:
            for (StructureTemplate.JigsawBlockInfo structuretemplate_jigsawblockinfo : structurepoolelement.getShuffledJigsawBlocks(this.structureTemplateManager, blockpos, rotation, this.random)) {
                StructureTemplate.StructureBlockInfo structuretemplate_structureblockinfo = structuretemplate_jigsawblockinfo.info();
                Direction direction = JigsawBlock.getFrontFacing(structuretemplate_structureblockinfo.state());
                BlockPos blockpos1 = structuretemplate_structureblockinfo.pos();
                BlockPos blockpos2 = blockpos1.relative(direction);
                int k = blockpos1.getY() - j;
                int l = Integer.MIN_VALUE;
                ResourceKey<StructureTemplatePool> resourcekey = poolAliasLookup.lookup(structuretemplate_jigsawblockinfo.pool());
                Optional<? extends Holder<StructureTemplatePool>> optional = this.pools.get(resourcekey);

                if (optional.isEmpty()) {
                    JigsawPlacement.LOGGER.warn("Empty or non-existent pool: {}", resourcekey.identifier());
                } else {
                    Holder<StructureTemplatePool> holder = (Holder) optional.get();

                    if (((StructureTemplatePool) holder.value()).size() == 0 && !holder.is(Pools.EMPTY)) {
                        JigsawPlacement.LOGGER.warn("Empty or non-existent pool: {}", resourcekey.identifier());
                    } else {
                        Holder<StructureTemplatePool> holder1 = ((StructureTemplatePool) holder.value()).getFallback();

                        if (((StructureTemplatePool) holder1.value()).size() == 0 && !holder1.is(Pools.EMPTY)) {
                            JigsawPlacement.LOGGER.warn("Empty or non-existent fallback pool: {}", holder1.unwrapKey().map((resourcekey1) -> {
                                return resourcekey1.identifier().toString();
                            }).orElse("<unregistered>"));
                        } else {
                            boolean flag2 = boundingbox.isInside(blockpos2);
                            MutableObject<VoxelShape> mutableobject2;

                            if (flag2) {
                                mutableobject2 = mutableobject1;
                                if (mutableobject1.get() == null) {
                                    mutableobject1.setValue(Shapes.create(AABB.of(boundingbox)));
                                }
                            } else {
                                mutableobject2 = contextFree;
                            }

                            List<StructurePoolElement> list = Lists.newArrayList();

                            if (depth != this.maxDepth) {
                                list.addAll((holder.value()).getShuffledTemplates(this.random));
                            }

                            list.addAll((holder1.value()).getShuffledTemplates(this.random));
                            int i1 = structuretemplate_jigsawblockinfo.placementPriority();

                            for (StructurePoolElement structurepoolelement1 : list) {
                                if (structurepoolelement1 == EmptyPoolElement.INSTANCE) {
                                    break;
                                }

                                for (Rotation rotation1 : Rotation.getShuffled(this.random)) {
                                    List<StructureTemplate.JigsawBlockInfo> list1 = structurepoolelement1.getShuffledJigsawBlocks(this.structureTemplateManager, BlockPos.ZERO, rotation1, this.random);
                                    BoundingBox boundingbox1 = structurepoolelement1.getBoundingBox(this.structureTemplateManager, BlockPos.ZERO, rotation1);
                                    int j1;

                                    if (doExpansionHack && boundingbox1.getYSpan() <= 16) {
                                        j1 = list1.stream().mapToInt((structuretemplate_jigsawblockinfo1) -> {
                                            StructureTemplate.StructureBlockInfo structuretemplate_structureblockinfo1 = structuretemplate_jigsawblockinfo1.info();

                                            if (!boundingbox1.isInside(structuretemplate_structureblockinfo1.pos().relative(JigsawBlock.getFrontFacing(structuretemplate_structureblockinfo1.state())))) {
                                                return 0;
                                            } else {
                                                ResourceKey<StructureTemplatePool> resourcekey1 = poolAliasLookup.lookup(structuretemplate_jigsawblockinfo1.pool());
                                                Optional<? extends Holder<StructureTemplatePool>> optional1 = this.pools.get(resourcekey1);
                                                Optional<Holder<StructureTemplatePool>> optional2 = optional1.map((holder2) -> {
                                                    return ((StructureTemplatePool) holder2.value()).getFallback();
                                                });
                                                int k1 = (Integer) optional1.map((holder2) -> {
                                                    return ((StructureTemplatePool) holder2.value()).getMaxSize(this.structureTemplateManager);
                                                }).orElse(0);
                                                int l1 = (Integer) optional2.map((holder2) -> {
                                                    return ((StructureTemplatePool) holder2.value()).getMaxSize(this.structureTemplateManager);
                                                }).orElse(0);

                                                return Math.max(k1, l1);
                                            }
                                        }).max().orElse(0);
                                    } else {
                                        j1 = 0;
                                    }

                                    for (StructureTemplate.JigsawBlockInfo structuretemplate_jigsawblockinfo1 : list1) {
                                        if (JigsawBlock.canAttach(structuretemplate_jigsawblockinfo, structuretemplate_jigsawblockinfo1)) {
                                            BlockPos blockpos3 = structuretemplate_jigsawblockinfo1.info().pos();
                                            BlockPos blockpos4 = blockpos2.subtract(blockpos3);
                                            BoundingBox boundingbox2 = structurepoolelement1.getBoundingBox(this.structureTemplateManager, blockpos4, rotation1);
                                            int k1 = boundingbox2.minY();
                                            StructureTemplatePool.Projection structuretemplatepool_projection1 = structurepoolelement1.getProjection();
                                            boolean flag3 = structuretemplatepool_projection1 == StructureTemplatePool.Projection.RIGID;
                                            int l1 = blockpos3.getY();
                                            int i2 = k - l1 + JigsawBlock.getFrontFacing(structuretemplate_structureblockinfo.state()).getStepY();
                                            int j2;

                                            if (flag1 && flag3) {
                                                j2 = j + i2;
                                            } else {
                                                if (l == Integer.MIN_VALUE) {
                                                    l = this.chunkGenerator.getFirstFreeHeight(blockpos1.getX(), blockpos1.getZ(), Heightmap.Types.WORLD_SURFACE_WG, heightAccessor, randomState);
                                                }

                                                j2 = l - l1;
                                            }

                                            int k2 = j2 - k1;
                                            BoundingBox boundingbox3 = boundingbox2.moved(0, k2, 0);
                                            BlockPos blockpos5 = blockpos4.offset(0, k2, 0);

                                            if (j1 > 0) {
                                                int l2 = Math.max(j1 + 1, boundingbox3.maxY() - boundingbox3.minY());

                                                boundingbox3.encapsulate(new BlockPos(boundingbox3.minX(), boundingbox3.minY() + l2, boundingbox3.minZ()));
                                            }

                                            if (!Shapes.joinIsNotEmpty((VoxelShape) mutableobject2.get(), Shapes.create(AABB.of(boundingbox3).deflate(0.25D)), BooleanOp.ONLY_SECOND)) {
                                                mutableobject2.setValue(Shapes.joinUnoptimized((VoxelShape) mutableobject2.get(), Shapes.create(AABB.of(boundingbox3)), BooleanOp.ONLY_FIRST));
                                                int i3 = sourcePiece.getGroundLevelDelta();
                                                int j3;

                                                if (flag3) {
                                                    j3 = i3 - i2;
                                                } else {
                                                    j3 = structurepoolelement1.getGroundLevelDelta();
                                                }

                                                PoolElementStructurePiece poolelementstructurepiece1 = new PoolElementStructurePiece(this.structureTemplateManager, structurepoolelement1, blockpos5, j3, rotation1, boundingbox3, liquidSettings);
                                                int k3;

                                                if (flag1) {
                                                    k3 = j + k;
                                                } else if (flag3) {
                                                    k3 = j2 + l1;
                                                } else {
                                                    if (l == Integer.MIN_VALUE) {
                                                        l = this.chunkGenerator.getFirstFreeHeight(blockpos1.getX(), blockpos1.getZ(), Heightmap.Types.WORLD_SURFACE_WG, heightAccessor, randomState);
                                                    }

                                                    k3 = l + i2 / 2;
                                                }

                                                sourcePiece.addJunction(new JigsawJunction(blockpos2.getX(), k3 - k + i3, blockpos2.getZ(), i2, structuretemplatepool_projection1));
                                                poolelementstructurepiece1.addJunction(new JigsawJunction(blockpos1.getX(), k3 - l1 + j3, blockpos1.getZ(), -i2, structuretemplatepool_projection));
                                                this.pieces.add(poolelementstructurepiece1);
                                                if (depth + 1 <= this.maxDepth) {
                                                    JigsawPlacement.PieceState jigsawplacement_piecestate = new JigsawPlacement.PieceState(poolelementstructurepiece1, mutableobject2, depth + 1);

                                                    this.placing.add(jigsawplacement_piecestate, i1);
                                                }
                                                continue label129;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }
    }
}
