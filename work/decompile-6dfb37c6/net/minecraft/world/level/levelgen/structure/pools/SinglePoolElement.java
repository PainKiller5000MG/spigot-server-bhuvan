package net.minecraft.world.level.levelgen.structure.pools;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public class SinglePoolElement extends StructurePoolElement {

    private static final Comparator<StructureTemplate.JigsawBlockInfo> HIGHEST_SELECTION_PRIORITY_FIRST = Comparator.comparingInt(StructureTemplate.JigsawBlockInfo::selectionPriority).reversed();
    private static final Codec<Either<Identifier, StructureTemplate>> TEMPLATE_CODEC = Codec.of(SinglePoolElement::encodeTemplate, Identifier.CODEC.map(Either::left));
    public static final MapCodec<SinglePoolElement> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(templateCodec(), processorsCodec(), projectionCodec(), overrideLiquidSettingsCodec()).apply(instance, SinglePoolElement::new);
    });
    protected final Either<Identifier, StructureTemplate> template;
    protected final Holder<StructureProcessorList> processors;
    protected final Optional<LiquidSettings> overrideLiquidSettings;

    private static <T> DataResult<T> encodeTemplate(Either<Identifier, StructureTemplate> template, DynamicOps<T> ops, T prefix) {
        Optional<Identifier> optional = template.left();

        return optional.isEmpty() ? DataResult.error(() -> {
            return "Can not serialize a runtime pool element";
        }) : Identifier.CODEC.encode((Identifier) optional.get(), ops, prefix);
    }

    protected static <E extends SinglePoolElement> RecordCodecBuilder<E, Holder<StructureProcessorList>> processorsCodec() {
        return StructureProcessorType.LIST_CODEC.fieldOf("processors").forGetter((singlepoolelement) -> {
            return singlepoolelement.processors;
        });
    }

    protected static <E extends SinglePoolElement> RecordCodecBuilder<E, Optional<LiquidSettings>> overrideLiquidSettingsCodec() {
        return LiquidSettings.CODEC.optionalFieldOf("override_liquid_settings").forGetter((singlepoolelement) -> {
            return singlepoolelement.overrideLiquidSettings;
        });
    }

    protected static <E extends SinglePoolElement> RecordCodecBuilder<E, Either<Identifier, StructureTemplate>> templateCodec() {
        return SinglePoolElement.TEMPLATE_CODEC.fieldOf("location").forGetter((singlepoolelement) -> {
            return singlepoolelement.template;
        });
    }

    protected SinglePoolElement(Either<Identifier, StructureTemplate> template, Holder<StructureProcessorList> processors, StructureTemplatePool.Projection projection, Optional<LiquidSettings> overrideLiquidSettings) {
        super(projection);
        this.template = template;
        this.processors = processors;
        this.overrideLiquidSettings = overrideLiquidSettings;
    }

    @Override
    public Vec3i getSize(StructureTemplateManager structureTemplateManager, Rotation rotation) {
        StructureTemplate structuretemplate = this.getTemplate(structureTemplateManager);

        return structuretemplate.getSize(rotation);
    }

    private StructureTemplate getTemplate(StructureTemplateManager structureTemplateManager) {
        Either either = this.template;

        Objects.requireNonNull(structureTemplateManager);
        return (StructureTemplate) either.map(structureTemplateManager::getOrCreate, Function.identity());
    }

    public List<StructureTemplate.StructureBlockInfo> getDataMarkers(StructureTemplateManager structureTemplateManager, BlockPos position, Rotation rotation, boolean absolute) {
        StructureTemplate structuretemplate = this.getTemplate(structureTemplateManager);
        List<StructureTemplate.StructureBlockInfo> list = structuretemplate.filterBlocks(position, (new StructurePlaceSettings()).setRotation(rotation), Blocks.STRUCTURE_BLOCK, absolute);
        List<StructureTemplate.StructureBlockInfo> list1 = Lists.newArrayList();

        for (StructureTemplate.StructureBlockInfo structuretemplate_structureblockinfo : list) {
            CompoundTag compoundtag = structuretemplate_structureblockinfo.nbt();

            if (compoundtag != null) {
                StructureMode structuremode = (StructureMode) compoundtag.read("mode", StructureMode.LEGACY_CODEC).orElseThrow();

                if (structuremode == StructureMode.DATA) {
                    list1.add(structuretemplate_structureblockinfo);
                }
            }
        }

        return list1;
    }

    @Override
    public List<StructureTemplate.JigsawBlockInfo> getShuffledJigsawBlocks(StructureTemplateManager structureTemplateManager, BlockPos position, Rotation rotation, RandomSource random) {
        List<StructureTemplate.JigsawBlockInfo> list = this.getTemplate(structureTemplateManager).getJigsaws(position, rotation);

        Util.shuffle(list, random);
        sortBySelectionPriority(list);
        return list;
    }

    @VisibleForTesting
    static void sortBySelectionPriority(List<StructureTemplate.JigsawBlockInfo> blocks) {
        blocks.sort(SinglePoolElement.HIGHEST_SELECTION_PRIORITY_FIRST);
    }

    @Override
    public BoundingBox getBoundingBox(StructureTemplateManager structureTemplateManager, BlockPos position, Rotation rotation) {
        StructureTemplate structuretemplate = this.getTemplate(structureTemplateManager);

        return structuretemplate.getBoundingBox((new StructurePlaceSettings()).setRotation(rotation), position);
    }

    @Override
    public boolean place(StructureTemplateManager structureTemplateManager, WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, BlockPos position, BlockPos referencePos, Rotation rotation, BoundingBox chunkBB, RandomSource random, LiquidSettings liquidSettings, boolean keepJigsaws) {
        StructureTemplate structuretemplate = this.getTemplate(structureTemplateManager);
        StructurePlaceSettings structureplacesettings = this.getSettings(rotation, chunkBB, liquidSettings, keepJigsaws);

        if (!structuretemplate.placeInWorld(level, position, referencePos, structureplacesettings, random, 18)) {
            return false;
        } else {
            for (StructureTemplate.StructureBlockInfo structuretemplate_structureblockinfo : StructureTemplate.processBlockInfos(level, position, referencePos, structureplacesettings, this.getDataMarkers(structureTemplateManager, position, rotation, false))) {
                this.handleDataMarker(level, structuretemplate_structureblockinfo, position, rotation, random, chunkBB);
            }

            return true;
        }
    }

    protected StructurePlaceSettings getSettings(Rotation rotation, BoundingBox chunkBB, LiquidSettings liquidSettings, boolean keepJigsaws) {
        StructurePlaceSettings structureplacesettings = new StructurePlaceSettings();

        structureplacesettings.setBoundingBox(chunkBB);
        structureplacesettings.setRotation(rotation);
        structureplacesettings.setKnownShape(true);
        structureplacesettings.setIgnoreEntities(false);
        structureplacesettings.addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        structureplacesettings.setFinalizeEntities(true);
        structureplacesettings.setLiquidSettings((LiquidSettings) this.overrideLiquidSettings.orElse(liquidSettings));
        if (!keepJigsaws) {
            structureplacesettings.addProcessor(JigsawReplacementProcessor.INSTANCE);
        }

        List list = (this.processors.value()).list();

        Objects.requireNonNull(structureplacesettings);
        list.forEach(structureplacesettings::addProcessor);
        ImmutableList immutablelist = this.getProjection().getProcessors();

        Objects.requireNonNull(structureplacesettings);
        immutablelist.forEach(structureplacesettings::addProcessor);
        return structureplacesettings;
    }

    @Override
    public StructurePoolElementType<?> getType() {
        return StructurePoolElementType.SINGLE;
    }

    public String toString() {
        return "Single[" + String.valueOf(this.template) + "]";
    }

    @VisibleForTesting
    public Identifier getTemplateLocation() {
        return (Identifier) this.template.orThrow();
    }
}
