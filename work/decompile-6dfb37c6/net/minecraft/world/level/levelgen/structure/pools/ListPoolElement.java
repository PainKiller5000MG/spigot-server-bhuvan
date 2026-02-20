package net.minecraft.world.level.levelgen.structure.pools;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public class ListPoolElement extends StructurePoolElement {

    public static final MapCodec<ListPoolElement> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(StructurePoolElement.CODEC.listOf().fieldOf("elements").forGetter((listpoolelement) -> {
            return listpoolelement.elements;
        }), projectionCodec()).apply(instance, ListPoolElement::new);
    });
    private final List<StructurePoolElement> elements;

    public ListPoolElement(List<StructurePoolElement> elements, StructureTemplatePool.Projection projection) {
        super(projection);
        if (elements.isEmpty()) {
            throw new IllegalArgumentException("Elements are empty");
        } else {
            this.elements = elements;
            this.setProjectionOnEachElement(projection);
        }
    }

    @Override
    public Vec3i getSize(StructureTemplateManager structureTemplateManager, Rotation rotation) {
        int i = 0;
        int j = 0;
        int k = 0;

        for (StructurePoolElement structurepoolelement : this.elements) {
            Vec3i vec3i = structurepoolelement.getSize(structureTemplateManager, rotation);

            i = Math.max(i, vec3i.getX());
            j = Math.max(j, vec3i.getY());
            k = Math.max(k, vec3i.getZ());
        }

        return new Vec3i(i, j, k);
    }

    @Override
    public List<StructureTemplate.JigsawBlockInfo> getShuffledJigsawBlocks(StructureTemplateManager structureTemplateManager, BlockPos position, Rotation rotation, RandomSource random) {
        return ((StructurePoolElement) this.elements.get(0)).getShuffledJigsawBlocks(structureTemplateManager, position, rotation, random);
    }

    @Override
    public BoundingBox getBoundingBox(StructureTemplateManager structureTemplateManager, BlockPos position, Rotation rotation) {
        Stream<BoundingBox> stream = this.elements.stream().filter((structurepoolelement) -> {
            return structurepoolelement != EmptyPoolElement.INSTANCE;
        }).map((structurepoolelement) -> {
            return structurepoolelement.getBoundingBox(structureTemplateManager, position, rotation);
        });

        Objects.requireNonNull(stream);
        return (BoundingBox) BoundingBox.encapsulatingBoxes(stream::iterator).orElseThrow(() -> {
            return new IllegalStateException("Unable to calculate boundingbox for ListPoolElement");
        });
    }

    @Override
    public boolean place(StructureTemplateManager structureTemplateManager, WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, BlockPos position, BlockPos referencePos, Rotation rotation, BoundingBox chunkBB, RandomSource random, LiquidSettings liquidSettings, boolean keepJigsaws) {
        for (StructurePoolElement structurepoolelement : this.elements) {
            if (!structurepoolelement.place(structureTemplateManager, level, structureManager, generator, position, referencePos, rotation, chunkBB, random, liquidSettings, keepJigsaws)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public StructurePoolElementType<?> getType() {
        return StructurePoolElementType.LIST;
    }

    @Override
    public StructurePoolElement setProjection(StructureTemplatePool.Projection projection) {
        super.setProjection(projection);
        this.setProjectionOnEachElement(projection);
        return this;
    }

    public String toString() {
        Stream stream = this.elements.stream().map(Object::toString);

        return "List[" + (String) stream.collect(Collectors.joining(", ")) + "]";
    }

    private void setProjectionOnEachElement(StructureTemplatePool.Projection projection) {
        this.elements.forEach((structurepoolelement) -> {
            structurepoolelement.setProjection(projection);
        });
    }

    @VisibleForTesting
    public List<StructurePoolElement> getElements() {
        return this.elements;
    }
}
