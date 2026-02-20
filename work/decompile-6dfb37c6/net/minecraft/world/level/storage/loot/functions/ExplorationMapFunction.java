package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;

public class ExplorationMapFunction extends LootItemConditionalFunction {

    public static final TagKey<Structure> DEFAULT_DESTINATION = StructureTags.ON_TREASURE_MAPS;
    public static final Holder<MapDecorationType> DEFAULT_DECORATION = MapDecorationTypes.WOODLAND_MANSION;
    public static final byte DEFAULT_ZOOM = 2;
    public static final int DEFAULT_SEARCH_RADIUS = 50;
    public static final boolean DEFAULT_SKIP_EXISTING = true;
    public static final MapCodec<ExplorationMapFunction> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).and(instance.group(TagKey.codec(Registries.STRUCTURE).optionalFieldOf("destination", ExplorationMapFunction.DEFAULT_DESTINATION).forGetter((explorationmapfunction) -> {
            return explorationmapfunction.destination;
        }), MapDecorationType.CODEC.optionalFieldOf("decoration", ExplorationMapFunction.DEFAULT_DECORATION).forGetter((explorationmapfunction) -> {
            return explorationmapfunction.mapDecoration;
        }), Codec.BYTE.optionalFieldOf("zoom", (byte) 2).forGetter((explorationmapfunction) -> {
            return explorationmapfunction.zoom;
        }), Codec.INT.optionalFieldOf("search_radius", 50).forGetter((explorationmapfunction) -> {
            return explorationmapfunction.searchRadius;
        }), Codec.BOOL.optionalFieldOf("skip_existing_chunks", true).forGetter((explorationmapfunction) -> {
            return explorationmapfunction.skipKnownStructures;
        }))).apply(instance, ExplorationMapFunction::new);
    });
    private final TagKey<Structure> destination;
    private final Holder<MapDecorationType> mapDecoration;
    private final byte zoom;
    private final int searchRadius;
    private final boolean skipKnownStructures;

    private ExplorationMapFunction(List<LootItemCondition> predicates, TagKey<Structure> destination, Holder<MapDecorationType> mapDecoration, byte zoom, int searchRadius, boolean skipKnownStructures) {
        super(predicates);
        this.destination = destination;
        this.mapDecoration = mapDecoration;
        this.zoom = zoom;
        this.searchRadius = searchRadius;
        this.skipKnownStructures = skipKnownStructures;
    }

    @Override
    public LootItemFunctionType<ExplorationMapFunction> getType() {
        return LootItemFunctions.EXPLORATION_MAP;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.ORIGIN);
    }

    @Override
    public ItemStack run(ItemStack itemStack, LootContext context) {
        if (!itemStack.is(Items.MAP)) {
            return itemStack;
        } else {
            Vec3 vec3 = (Vec3) context.getOptionalParameter(LootContextParams.ORIGIN);

            if (vec3 != null) {
                ServerLevel serverlevel = context.getLevel();
                BlockPos blockpos = serverlevel.findNearestMapStructure(this.destination, BlockPos.containing(vec3), this.searchRadius, this.skipKnownStructures);

                if (blockpos != null) {
                    ItemStack itemstack1 = MapItem.create(serverlevel, blockpos.getX(), blockpos.getZ(), this.zoom, true, true);

                    MapItem.renderBiomePreviewMap(serverlevel, itemstack1);
                    MapItemSavedData.addTargetDecoration(itemstack1, blockpos, "+", this.mapDecoration);
                    return itemstack1;
                }
            }

            return itemStack;
        }
    }

    public static ExplorationMapFunction.Builder makeExplorationMap() {
        return new ExplorationMapFunction.Builder();
    }

    public static class Builder extends LootItemConditionalFunction.Builder<ExplorationMapFunction.Builder> {

        private TagKey<Structure> destination;
        private Holder<MapDecorationType> mapDecoration;
        private byte zoom;
        private int searchRadius;
        private boolean skipKnownStructures;

        public Builder() {
            this.destination = ExplorationMapFunction.DEFAULT_DESTINATION;
            this.mapDecoration = ExplorationMapFunction.DEFAULT_DECORATION;
            this.zoom = 2;
            this.searchRadius = 50;
            this.skipKnownStructures = true;
        }

        @Override
        protected ExplorationMapFunction.Builder getThis() {
            return this;
        }

        public ExplorationMapFunction.Builder setDestination(TagKey<Structure> destination) {
            this.destination = destination;
            return this;
        }

        public ExplorationMapFunction.Builder setMapDecoration(Holder<MapDecorationType> mapDecoration) {
            this.mapDecoration = mapDecoration;
            return this;
        }

        public ExplorationMapFunction.Builder setZoom(byte zoom) {
            this.zoom = zoom;
            return this;
        }

        public ExplorationMapFunction.Builder setSearchRadius(int searchRadius) {
            this.searchRadius = searchRadius;
            return this;
        }

        public ExplorationMapFunction.Builder setSkipKnownStructures(boolean skipKnownStructures) {
            this.skipKnownStructures = skipKnownStructures;
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new ExplorationMapFunction(this.getConditions(), this.destination, this.mapDecoration, this.zoom, this.searchRadius, this.skipKnownStructures);
        }
    }
}
