package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.OptionalDynamic;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.SectionPos;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class BlendingDataFix extends DataFix {

    private final String name;
    private static final Set<String> STATUSES_TO_SKIP_BLENDING = Set.of("minecraft:empty", "minecraft:structure_starts", "minecraft:structure_references", "minecraft:biomes");

    public BlendingDataFix(Schema outputSchema) {
        super(outputSchema, false);
        this.name = "Blending Data Fix v" + outputSchema.getVersionKey();
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getOutputSchema().getType(References.CHUNK);

        return this.fixTypeEverywhereTyped(this.name, type, (typed) -> {
            return typed.update(DSL.remainderFinder(), (dynamic) -> {
                return updateChunkTag(dynamic, dynamic.get("__context"));
            });
        });
    }

    private static Dynamic<?> updateChunkTag(Dynamic<?> chunkTag, OptionalDynamic<?> contextTag) {
        chunkTag = chunkTag.remove("blending_data");
        boolean flag = "minecraft:overworld".equals(contextTag.get("dimension").asString().result().orElse(""));
        Optional<? extends Dynamic<?>> optional = chunkTag.get("Status").result();

        if (flag && optional.isPresent()) {
            String s = NamespacedSchema.ensureNamespaced(((Dynamic) optional.get()).asString("empty"));
            Optional<? extends Dynamic<?>> optional1 = chunkTag.get("below_zero_retrogen").result();

            if (!BlendingDataFix.STATUSES_TO_SKIP_BLENDING.contains(s)) {
                chunkTag = updateBlendingData(chunkTag, 384, -64);
            } else if (optional1.isPresent()) {
                Dynamic<?> dynamic1 = (Dynamic) optional1.get();
                String s1 = NamespacedSchema.ensureNamespaced(dynamic1.get("target_status").asString("empty"));

                if (!BlendingDataFix.STATUSES_TO_SKIP_BLENDING.contains(s1)) {
                    chunkTag = updateBlendingData(chunkTag, 256, 0);
                }
            }
        }

        return chunkTag;
    }

    private static Dynamic<?> updateBlendingData(Dynamic<?> chunkTag, int height, int minY) {
        return chunkTag.set("blending_data", chunkTag.createMap(Map.of(chunkTag.createString("min_section"), chunkTag.createInt(SectionPos.blockToSectionCoord(minY)), chunkTag.createString("max_section"), chunkTag.createInt(SectionPos.blockToSectionCoord(minY + height)))));
    }
}
