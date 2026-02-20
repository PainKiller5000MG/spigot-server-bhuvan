package net.minecraft.util.datafix.fixes;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.LongStream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class StructuresBecomeConfiguredFix extends DataFix {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, StructuresBecomeConfiguredFix.Conversion> CONVERSION_MAP = ImmutableMap.builder().put("mineshaft", StructuresBecomeConfiguredFix.Conversion.biomeMapped(Map.of(List.of("minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands"), "minecraft:mineshaft_mesa"), "minecraft:mineshaft")).put("shipwreck", StructuresBecomeConfiguredFix.Conversion.biomeMapped(Map.of(List.of("minecraft:beach", "minecraft:snowy_beach"), "minecraft:shipwreck_beached"), "minecraft:shipwreck")).put("ocean_ruin", StructuresBecomeConfiguredFix.Conversion.biomeMapped(Map.of(List.of("minecraft:warm_ocean", "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean"), "minecraft:ocean_ruin_warm"), "minecraft:ocean_ruin_cold")).put("village", StructuresBecomeConfiguredFix.Conversion.biomeMapped(Map.of(List.of("minecraft:desert"), "minecraft:village_desert", List.of("minecraft:savanna"), "minecraft:village_savanna", List.of("minecraft:snowy_plains"), "minecraft:village_snowy", List.of("minecraft:taiga"), "minecraft:village_taiga"), "minecraft:village_plains")).put("ruined_portal", StructuresBecomeConfiguredFix.Conversion.biomeMapped(Map.of(List.of("minecraft:desert"), "minecraft:ruined_portal_desert", List.of("minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands", "minecraft:windswept_hills", "minecraft:windswept_forest", "minecraft:windswept_gravelly_hills", "minecraft:savanna_plateau", "minecraft:windswept_savanna", "minecraft:stony_shore", "minecraft:meadow", "minecraft:frozen_peaks", "minecraft:jagged_peaks", "minecraft:stony_peaks", "minecraft:snowy_slopes"), "minecraft:ruined_portal_mountain", List.of("minecraft:bamboo_jungle", "minecraft:jungle", "minecraft:sparse_jungle"), "minecraft:ruined_portal_jungle", List.of("minecraft:deep_frozen_ocean", "minecraft:deep_cold_ocean", "minecraft:deep_ocean", "minecraft:deep_lukewarm_ocean", "minecraft:frozen_ocean", "minecraft:ocean", "minecraft:cold_ocean", "minecraft:lukewarm_ocean", "minecraft:warm_ocean"), "minecraft:ruined_portal_ocean"), "minecraft:ruined_portal")).put("pillager_outpost", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:pillager_outpost")).put("mansion", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:mansion")).put("jungle_pyramid", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:jungle_pyramid")).put("desert_pyramid", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:desert_pyramid")).put("igloo", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:igloo")).put("swamp_hut", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:swamp_hut")).put("stronghold", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:stronghold")).put("monument", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:monument")).put("fortress", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:fortress")).put("endcity", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:end_city")).put("buried_treasure", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:buried_treasure")).put("nether_fossil", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:nether_fossil")).put("bastion_remnant", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:bastion_remnant")).build();

    public StructuresBecomeConfiguredFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        Type<?> type1 = this.getInputSchema().getType(References.CHUNK);

        return this.writeFixAndRead("StucturesToConfiguredStructures", type, type1, this::fix);
    }

    private Dynamic<?> fix(Dynamic<?> chunk) {
        return chunk.update("structures", (dynamic1) -> {
            return dynamic1.update("starts", (dynamic2) -> {
                return this.updateStarts(dynamic2, chunk);
            }).update("References", (dynamic2) -> {
                return this.updateReferences(dynamic2, chunk);
            });
        });
    }

    private Dynamic<?> updateStarts(Dynamic<?> starts, Dynamic<?> chunk) {
        Map<? extends Dynamic<?>, ? extends Dynamic<?>> map = (Map) starts.getMapValues().result().orElse(Map.of());
        HashMap<Dynamic<?>, Dynamic<?>> hashmap = Maps.newHashMap();

        map.forEach((dynamic2, dynamic3) -> {
            if (!dynamic3.get("id").asString("INVALID").equals("INVALID")) {
                Dynamic<?> dynamic4 = this.findUpdatedStructureType(dynamic2, chunk);

                if (dynamic4 == null) {
                    StructuresBecomeConfiguredFix.LOGGER.warn("Encountered unknown structure in datafixer: {}", dynamic2.asString("<missing key>"));
                } else {
                    hashmap.computeIfAbsent(dynamic4, (dynamic5) -> {
                        return dynamic3.set("id", dynamic4);
                    });
                }
            }
        });
        return chunk.createMap(hashmap);
    }

    private Dynamic<?> updateReferences(Dynamic<?> references, Dynamic<?> chunk) {
        Map<? extends Dynamic<?>, ? extends Dynamic<?>> map = (Map) references.getMapValues().result().orElse(Map.of());
        HashMap<Dynamic<?>, Dynamic<?>> hashmap = Maps.newHashMap();

        map.forEach((dynamic2, dynamic3) -> {
            if (dynamic3.asLongStream().count() != 0L) {
                Dynamic<?> dynamic4 = this.findUpdatedStructureType(dynamic2, chunk);

                if (dynamic4 == null) {
                    StructuresBecomeConfiguredFix.LOGGER.warn("Encountered unknown structure in datafixer: {}", dynamic2.asString("<missing key>"));
                } else {
                    hashmap.compute(dynamic4, (dynamic5, dynamic6) -> {
                        return dynamic6 == null ? dynamic3 : dynamic3.createLongList(LongStream.concat(dynamic6.asLongStream(), dynamic3.asLongStream()));
                    });
                }
            }
        });
        return chunk.createMap(hashmap);
    }

    private @Nullable Dynamic<?> findUpdatedStructureType(Dynamic<?> dynamicKey, Dynamic<?> chunk) {
        String s = dynamicKey.asString("UNKNOWN").toLowerCase(Locale.ROOT);
        StructuresBecomeConfiguredFix.Conversion structuresbecomeconfiguredfix_conversion = (StructuresBecomeConfiguredFix.Conversion) StructuresBecomeConfiguredFix.CONVERSION_MAP.get(s);

        if (structuresbecomeconfiguredfix_conversion == null) {
            return null;
        } else {
            String s1 = structuresbecomeconfiguredfix_conversion.fallback;

            if (!structuresbecomeconfiguredfix_conversion.biomeMapping().isEmpty()) {
                Optional<String> optional = this.guessConfiguration(chunk, structuresbecomeconfiguredfix_conversion);

                if (optional.isPresent()) {
                    s1 = (String) optional.get();
                }
            }

            return chunk.createString(s1);
        }
    }

    private Optional<String> guessConfiguration(Dynamic<?> chunk, StructuresBecomeConfiguredFix.Conversion conversion) {
        Object2IntArrayMap<String> object2intarraymap = new Object2IntArrayMap();

        chunk.get("sections").asList(Function.identity()).forEach((dynamic1) -> {
            dynamic1.get("biomes").get("palette").asList(Function.identity()).forEach((dynamic2) -> {
                String s = (String) conversion.biomeMapping().get(dynamic2.asString(""));

                if (s != null) {
                    object2intarraymap.mergeInt(s, 1, Integer::sum);
                }

            });
        });
        return object2intarraymap.object2IntEntrySet().stream().max(Comparator.comparingInt(Entry::getIntValue)).map(java.util.Map.Entry::getKey);
    }

    private static record Conversion(Map<String, String> biomeMapping, String fallback) {

        public static StructuresBecomeConfiguredFix.Conversion trivial(String result) {
            return new StructuresBecomeConfiguredFix.Conversion(Map.of(), result);
        }

        public static StructuresBecomeConfiguredFix.Conversion biomeMapped(Map<List<String>, String> mapping, String fallback) {
            return new StructuresBecomeConfiguredFix.Conversion(unpack(mapping), fallback);
        }

        private static Map<String, String> unpack(Map<List<String>, String> packed) {
            ImmutableMap.Builder<String, String> immutablemap_builder = ImmutableMap.builder();

            for (Map.Entry<List<String>, String> map_entry : packed.entrySet()) {
                ((List) map_entry.getKey()).forEach((s) -> {
                    immutablemap_builder.put(s, (String) map_entry.getValue());
                });
            }

            return immutablemap_builder.build();
        }
    }
}
