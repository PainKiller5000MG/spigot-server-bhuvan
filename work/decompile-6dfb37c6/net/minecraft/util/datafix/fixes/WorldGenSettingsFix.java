package net.minecraft.util.datafix.fixes;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicLike;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.OptionalDynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;

public class WorldGenSettingsFix extends DataFix {

    private static final String VILLAGE = "minecraft:village";
    private static final String DESERT_PYRAMID = "minecraft:desert_pyramid";
    private static final String IGLOO = "minecraft:igloo";
    private static final String JUNGLE_TEMPLE = "minecraft:jungle_pyramid";
    private static final String SWAMP_HUT = "minecraft:swamp_hut";
    private static final String PILLAGER_OUTPOST = "minecraft:pillager_outpost";
    private static final String END_CITY = "minecraft:endcity";
    private static final String WOODLAND_MANSION = "minecraft:mansion";
    private static final String OCEAN_MONUMENT = "minecraft:monument";
    private static final ImmutableMap<String, WorldGenSettingsFix.StructureFeatureConfiguration> DEFAULTS = ImmutableMap.builder().put("minecraft:village", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 8, 10387312)).put("minecraft:desert_pyramid", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 8, 14357617)).put("minecraft:igloo", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 8, 14357618)).put("minecraft:jungle_pyramid", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 8, 14357619)).put("minecraft:swamp_hut", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 8, 14357620)).put("minecraft:pillager_outpost", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 8, 165745296)).put("minecraft:monument", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 5, 10387313)).put("minecraft:endcity", new WorldGenSettingsFix.StructureFeatureConfiguration(20, 11, 10387313)).put("minecraft:mansion", new WorldGenSettingsFix.StructureFeatureConfiguration(80, 20, 10387319)).build();

    public WorldGenSettingsFix(Schema parent) {
        super(parent, true);
    }

    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped("WorldGenSettings building", this.getInputSchema().getType(References.WORLD_GEN_SETTINGS), (typed) -> {
            return typed.update(DSL.remainderFinder(), WorldGenSettingsFix::fix);
        });
    }

    private static <T> Dynamic<T> noise(long seed, DynamicLike<T> input, Dynamic<T> noiseGeneratorSettings, Dynamic<T> biomeSource) {
        return input.createMap(ImmutableMap.of(input.createString("type"), input.createString("minecraft:noise"), input.createString("biome_source"), biomeSource, input.createString("seed"), input.createLong(seed), input.createString("settings"), noiseGeneratorSettings));
    }

    private static <T> Dynamic<T> vanillaBiomeSource(Dynamic<T> input, long seed, boolean legacyBiomeInitLayer, boolean largeBiomes) {
        ImmutableMap.Builder<Dynamic<T>, Dynamic<T>> immutablemap_builder = ImmutableMap.builder().put(input.createString("type"), input.createString("minecraft:vanilla_layered")).put(input.createString("seed"), input.createLong(seed)).put(input.createString("large_biomes"), input.createBoolean(largeBiomes));

        if (legacyBiomeInitLayer) {
            immutablemap_builder.put(input.createString("legacy_biome_init_layer"), input.createBoolean(legacyBiomeInitLayer));
        }

        return input.createMap(immutablemap_builder.build());
    }

    private static <T> Dynamic<T> fix(Dynamic<T> input) {
        DynamicOps<T> dynamicops = input.getOps();
        long i = input.get("RandomSeed").asLong(0L);
        Optional<String> optional = input.get("generatorName").asString().map((s) -> {
            return s.toLowerCase(Locale.ROOT);
        }).result();
        Optional<String> optional1 = (Optional) input.get("legacy_custom_options").asString().result().map(Optional::of).orElseGet(() -> {
            return optional.equals(Optional.of("customized")) ? input.get("generatorOptions").asString().result() : Optional.empty();
        });
        boolean flag = false;
        Dynamic<T> dynamic1;

        if (optional.equals(Optional.of("customized"))) {
            dynamic1 = defaultOverworld(input, i);
        } else if (optional.isEmpty()) {
            dynamic1 = defaultOverworld(input, i);
        } else {
            switch ((String) optional.get()) {
                case "flat":
                    OptionalDynamic<T> optionaldynamic = input.get("generatorOptions");
                    Map<Dynamic<T>, Dynamic<T>> map = fixFlatStructures(dynamicops, optionaldynamic);

                    dynamic1 = input.createMap(ImmutableMap.of(input.createString("type"), input.createString("minecraft:flat"), input.createString("settings"), input.createMap(ImmutableMap.of(input.createString("structures"), input.createMap(map), input.createString("layers"), (Dynamic) optionaldynamic.get("layers").result().orElseGet(() -> {
                        return input.createList(Stream.of(input.createMap(ImmutableMap.of(input.createString("height"), input.createInt(1), input.createString("block"), input.createString("minecraft:bedrock"))), input.createMap(ImmutableMap.of(input.createString("height"), input.createInt(2), input.createString("block"), input.createString("minecraft:dirt"))), input.createMap(ImmutableMap.of(input.createString("height"), input.createInt(1), input.createString("block"), input.createString("minecraft:grass_block")))));
                    }), input.createString("biome"), input.createString(optionaldynamic.get("biome").asString("minecraft:plains"))))));
                    break;
                case "debug_all_block_states":
                    dynamic1 = input.createMap(ImmutableMap.of(input.createString("type"), input.createString("minecraft:debug")));
                    break;
                case "buffet":
                    OptionalDynamic<T> optionaldynamic1 = input.get("generatorOptions");
                    OptionalDynamic<?> optionaldynamic2 = optionaldynamic1.get("chunk_generator");
                    Optional<String> optional2 = optionaldynamic2.get("type").asString().result();
                    Dynamic<T> dynamic2;

                    if (Objects.equals(optional2, Optional.of("minecraft:caves"))) {
                        dynamic2 = input.createString("minecraft:caves");
                        flag = true;
                    } else if (Objects.equals(optional2, Optional.of("minecraft:floating_islands"))) {
                        dynamic2 = input.createString("minecraft:floating_islands");
                    } else {
                        dynamic2 = input.createString("minecraft:overworld");
                    }

                    Dynamic<T> dynamic3 = (Dynamic) optionaldynamic1.get("biome_source").result().orElseGet(() -> {
                        return input.createMap(ImmutableMap.of(input.createString("type"), input.createString("minecraft:fixed")));
                    });
                    Dynamic<T> dynamic4;

                    if (dynamic3.get("type").asString().result().equals(Optional.of("minecraft:fixed"))) {
                        String s = (String) dynamic3.get("options").get("biomes").asStream().findFirst().flatMap((dynamic5) -> {
                            return dynamic5.asString().result();
                        }).orElse("minecraft:ocean");

                        dynamic4 = dynamic3.remove("options").set("biome", input.createString(s));
                    } else {
                        dynamic4 = dynamic3;
                    }

                    dynamic1 = noise(i, input, dynamic2, dynamic4);
                    break;
                default:
                    boolean flag1 = ((String) optional.get()).equals("default");
                    boolean flag2 = ((String) optional.get()).equals("default_1_1") || flag1 && input.get("generatorVersion").asInt(0) == 0;
                    boolean flag3 = ((String) optional.get()).equals("amplified");
                    boolean flag4 = ((String) optional.get()).equals("largebiomes");

                    dynamic1 = noise(i, input, input.createString(flag3 ? "minecraft:amplified" : "minecraft:overworld"), vanillaBiomeSource(input, i, flag2, flag4));
            }
        }

        boolean flag5 = input.get("MapFeatures").asBoolean(true);
        boolean flag6 = input.get("BonusChest").asBoolean(false);
        ImmutableMap.Builder<T, T> immutablemap_builder = ImmutableMap.builder();

        immutablemap_builder.put(dynamicops.createString("seed"), dynamicops.createLong(i));
        immutablemap_builder.put(dynamicops.createString("generate_features"), dynamicops.createBoolean(flag5));
        immutablemap_builder.put(dynamicops.createString("bonus_chest"), dynamicops.createBoolean(flag6));
        immutablemap_builder.put(dynamicops.createString("dimensions"), vanillaLevels(input, i, dynamic1, flag));
        optional1.ifPresent((s1) -> {
            immutablemap_builder.put(dynamicops.createString("legacy_custom_options"), dynamicops.createString(s1));
        });
        return new Dynamic(dynamicops, dynamicops.createMap(immutablemap_builder.build()));
    }

    protected static <T> Dynamic<T> defaultOverworld(Dynamic<T> input, long seed) {
        return noise(seed, input, input.createString("minecraft:overworld"), vanillaBiomeSource(input, seed, false, false));
    }

    protected static <T> T vanillaLevels(Dynamic<T> input, long seed, Dynamic<T> overworldGenerator, boolean caves) {
        DynamicOps<T> dynamicops = input.getOps();

        return (T) dynamicops.createMap(ImmutableMap.of(dynamicops.createString("minecraft:overworld"), dynamicops.createMap(ImmutableMap.of(dynamicops.createString("type"), dynamicops.createString("minecraft:overworld" + (caves ? "_caves" : "")), dynamicops.createString("generator"), overworldGenerator.getValue())), dynamicops.createString("minecraft:the_nether"), dynamicops.createMap(ImmutableMap.of(dynamicops.createString("type"), dynamicops.createString("minecraft:the_nether"), dynamicops.createString("generator"), noise(seed, input, input.createString("minecraft:nether"), input.createMap(ImmutableMap.of(input.createString("type"), input.createString("minecraft:multi_noise"), input.createString("seed"), input.createLong(seed), input.createString("preset"), input.createString("minecraft:nether")))).getValue())), dynamicops.createString("minecraft:the_end"), dynamicops.createMap(ImmutableMap.of(dynamicops.createString("type"), dynamicops.createString("minecraft:the_end"), dynamicops.createString("generator"), noise(seed, input, input.createString("minecraft:end"), input.createMap(ImmutableMap.of(input.createString("type"), input.createString("minecraft:the_end"), input.createString("seed"), input.createLong(seed)))).getValue()))));
    }

    private static <T> Map<Dynamic<T>, Dynamic<T>> fixFlatStructures(DynamicOps<T> ops, OptionalDynamic<T> settings) {
        MutableInt mutableint = new MutableInt(32);
        MutableInt mutableint1 = new MutableInt(3);
        MutableInt mutableint2 = new MutableInt(128);
        MutableBoolean mutableboolean = new MutableBoolean(false);
        Map<String, WorldGenSettingsFix.StructureFeatureConfiguration> map = Maps.newHashMap();

        if (settings.result().isEmpty()) {
            mutableboolean.setTrue();
            map.put("minecraft:village", (WorldGenSettingsFix.StructureFeatureConfiguration) WorldGenSettingsFix.DEFAULTS.get("minecraft:village"));
        }

        settings.get("structures").flatMap(Dynamic::getMapValues).ifSuccess((map1) -> {
            map1.forEach((dynamic, dynamic1) -> {
                dynamic1.getMapValues().result().ifPresent((map2) -> {
                    map2.forEach((dynamic2, dynamic3) -> {
                        String s = dynamic.asString("");
                        String s1 = dynamic2.asString("");
                        String s2 = dynamic3.asString("");

                        if ("stronghold".equals(s)) {
                            mutableboolean.setTrue();
                            switch (s1) {
                                case "distance":
                                    mutableint.setValue(getInt(s2, mutableint.intValue(), 1));
                                    return;
                                case "spread":
                                    mutableint1.setValue(getInt(s2, mutableint1.intValue(), 1));
                                    return;
                                case "count":
                                    mutableint2.setValue(getInt(s2, mutableint2.intValue(), 1));
                                    return;
                                default:
                            }
                        } else {
                            switch (s1) {
                                case "distance":
                                    switch (s) {
                                        case "village":
                                            setSpacing(map, "minecraft:village", s2, 9);
                                            return;
                                        case "biome_1":
                                            setSpacing(map, "minecraft:desert_pyramid", s2, 9);
                                            setSpacing(map, "minecraft:igloo", s2, 9);
                                            setSpacing(map, "minecraft:jungle_pyramid", s2, 9);
                                            setSpacing(map, "minecraft:swamp_hut", s2, 9);
                                            setSpacing(map, "minecraft:pillager_outpost", s2, 9);
                                            return;
                                        case "endcity":
                                            setSpacing(map, "minecraft:endcity", s2, 1);
                                            return;
                                        case "mansion":
                                            setSpacing(map, "minecraft:mansion", s2, 1);
                                            return;
                                        default:
                                            return;
                                    }
                                case "separation":
                                    if ("oceanmonument".equals(s)) {
                                        WorldGenSettingsFix.StructureFeatureConfiguration worldgensettingsfix_structurefeatureconfiguration = (WorldGenSettingsFix.StructureFeatureConfiguration) map.getOrDefault("minecraft:monument", (WorldGenSettingsFix.StructureFeatureConfiguration) WorldGenSettingsFix.DEFAULTS.get("minecraft:monument"));
                                        int i = getInt(s2, worldgensettingsfix_structurefeatureconfiguration.separation, 1);

                                        map.put("minecraft:monument", new WorldGenSettingsFix.StructureFeatureConfiguration(i, worldgensettingsfix_structurefeatureconfiguration.separation, worldgensettingsfix_structurefeatureconfiguration.salt));
                                    }

                                    return;
                                case "spacing":
                                    if ("oceanmonument".equals(s)) {
                                        setSpacing(map, "minecraft:monument", s2, 1);
                                    }

                                    return;
                                default:
                            }
                        }
                    });
                });
            });
        });
        ImmutableMap.Builder<Dynamic<T>, Dynamic<T>> immutablemap_builder = ImmutableMap.builder();

        immutablemap_builder.put(settings.createString("structures"), settings.createMap((Map) map.entrySet().stream().collect(Collectors.toMap((entry) -> {
            return settings.createString((String) entry.getKey());
        }, (entry) -> {
            return ((WorldGenSettingsFix.StructureFeatureConfiguration) entry.getValue()).serialize(ops);
        }))));
        if (mutableboolean.isTrue()) {
            immutablemap_builder.put(settings.createString("stronghold"), settings.createMap(ImmutableMap.of(settings.createString("distance"), settings.createInt(mutableint.intValue()), settings.createString("spread"), settings.createInt(mutableint1.intValue()), settings.createString("count"), settings.createInt(mutableint2.intValue()))));
        }

        return immutablemap_builder.build();
    }

    private static int getInt(String input, int def) {
        return NumberUtils.toInt(input, def);
    }

    private static int getInt(String input, int def, int min) {
        return Math.max(min, getInt(input, def));
    }

    private static void setSpacing(Map<String, WorldGenSettingsFix.StructureFeatureConfiguration> structureConfig, String structure, String optionValue, int min) {
        WorldGenSettingsFix.StructureFeatureConfiguration worldgensettingsfix_structurefeatureconfiguration = (WorldGenSettingsFix.StructureFeatureConfiguration) structureConfig.getOrDefault(structure, (WorldGenSettingsFix.StructureFeatureConfiguration) WorldGenSettingsFix.DEFAULTS.get(structure));
        int j = getInt(optionValue, worldgensettingsfix_structurefeatureconfiguration.spacing, min);

        structureConfig.put(structure, new WorldGenSettingsFix.StructureFeatureConfiguration(j, worldgensettingsfix_structurefeatureconfiguration.separation, worldgensettingsfix_structurefeatureconfiguration.salt));
    }

    private static final class StructureFeatureConfiguration {

        public static final Codec<WorldGenSettingsFix.StructureFeatureConfiguration> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(Codec.INT.fieldOf("spacing").forGetter((worldgensettingsfix_structurefeatureconfiguration) -> {
                return worldgensettingsfix_structurefeatureconfiguration.spacing;
            }), Codec.INT.fieldOf("separation").forGetter((worldgensettingsfix_structurefeatureconfiguration) -> {
                return worldgensettingsfix_structurefeatureconfiguration.separation;
            }), Codec.INT.fieldOf("salt").forGetter((worldgensettingsfix_structurefeatureconfiguration) -> {
                return worldgensettingsfix_structurefeatureconfiguration.salt;
            })).apply(instance, WorldGenSettingsFix.StructureFeatureConfiguration::new);
        });
        private final int spacing;
        private final int separation;
        private final int salt;

        public StructureFeatureConfiguration(int spacing, int separation, int salt) {
            this.spacing = spacing;
            this.separation = separation;
            this.salt = salt;
        }

        public <T> Dynamic<T> serialize(DynamicOps<T> ops) {
            return new Dynamic(ops, WorldGenSettingsFix.StructureFeatureConfiguration.CODEC.encodeStart(ops, this).result().orElse(ops.emptyMap()));
        }
    }
}
