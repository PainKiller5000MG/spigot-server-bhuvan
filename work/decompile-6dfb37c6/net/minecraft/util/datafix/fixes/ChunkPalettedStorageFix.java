package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;
import net.minecraft.util.datafix.ExtraDataFixUtils;
import net.minecraft.util.datafix.PackedBitStorage;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ChunkPalettedStorageFix extends DataFix {

    private static final int NORTH_WEST_MASK = 128;
    private static final int WEST_MASK = 64;
    private static final int SOUTH_WEST_MASK = 32;
    private static final int SOUTH_MASK = 16;
    private static final int SOUTH_EAST_MASK = 8;
    private static final int EAST_MASK = 4;
    private static final int NORTH_EAST_MASK = 2;
    private static final int NORTH_MASK = 1;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SIZE = 4096;

    public ChunkPalettedStorageFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    public static String getName(Dynamic<?> state) {
        return state.get("Name").asString("");
    }

    public static String getProperty(Dynamic<?> state, String property) {
        return state.get("Properties").get(property).asString("");
    }

    public static int idFor(CrudeIncrementalIntIdentityHashBiMap<Dynamic<?>> states, Dynamic<?> state) {
        int i = states.getId(state);

        if (i == -1) {
            i = states.add(state);
        }

        return i;
    }

    private Dynamic<?> fix(Dynamic<?> input) {
        Optional<? extends Dynamic<?>> optional = input.get("Level").result();

        return optional.isPresent() && ((Dynamic) optional.get()).get("Sections").asStreamOpt().result().isPresent() ? input.set("Level", (new ChunkPalettedStorageFix.UpgradeChunk((Dynamic) optional.get())).write()) : input;
    }

    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        Type<?> type1 = this.getOutputSchema().getType(References.CHUNK);

        return this.writeFixAndRead("ChunkPalettedStorageFix", type, type1, this::fix);
    }

    public static int getSideMask(boolean west, boolean east, boolean north, boolean south) {
        int i = 0;

        if (north) {
            if (east) {
                i |= 2;
            } else if (west) {
                i |= 128;
            } else {
                i |= 1;
            }
        } else if (south) {
            if (west) {
                i |= 32;
            } else if (east) {
                i |= 8;
            } else {
                i |= 16;
            }
        } else if (east) {
            i |= 4;
        } else if (west) {
            i |= 64;
        }

        return i;
    }

    private static class MappingConstants {

        private static final BitSet VIRTUAL = new BitSet(256);
        private static final BitSet FIX = new BitSet(256);
        private static final Dynamic<?> PUMPKIN = ExtraDataFixUtils.blockState("minecraft:pumpkin");
        private static final Dynamic<?> SNOWY_PODZOL = ExtraDataFixUtils.blockState("minecraft:podzol", Map.of("snowy", "true"));
        private static final Dynamic<?> SNOWY_GRASS = ExtraDataFixUtils.blockState("minecraft:grass_block", Map.of("snowy", "true"));
        private static final Dynamic<?> SNOWY_MYCELIUM = ExtraDataFixUtils.blockState("minecraft:mycelium", Map.of("snowy", "true"));
        private static final Dynamic<?> UPPER_SUNFLOWER = ExtraDataFixUtils.blockState("minecraft:sunflower", Map.of("half", "upper"));
        private static final Dynamic<?> UPPER_LILAC = ExtraDataFixUtils.blockState("minecraft:lilac", Map.of("half", "upper"));
        private static final Dynamic<?> UPPER_TALL_GRASS = ExtraDataFixUtils.blockState("minecraft:tall_grass", Map.of("half", "upper"));
        private static final Dynamic<?> UPPER_LARGE_FERN = ExtraDataFixUtils.blockState("minecraft:large_fern", Map.of("half", "upper"));
        private static final Dynamic<?> UPPER_ROSE_BUSH = ExtraDataFixUtils.blockState("minecraft:rose_bush", Map.of("half", "upper"));
        private static final Dynamic<?> UPPER_PEONY = ExtraDataFixUtils.blockState("minecraft:peony", Map.of("half", "upper"));
        private static final Map<String, Dynamic<?>> FLOWER_POT_MAP = (Map) DataFixUtils.make(Maps.newHashMap(), (hashmap) -> {
            hashmap.put("minecraft:air0", ExtraDataFixUtils.blockState("minecraft:flower_pot"));
            hashmap.put("minecraft:red_flower0", ExtraDataFixUtils.blockState("minecraft:potted_poppy"));
            hashmap.put("minecraft:red_flower1", ExtraDataFixUtils.blockState("minecraft:potted_blue_orchid"));
            hashmap.put("minecraft:red_flower2", ExtraDataFixUtils.blockState("minecraft:potted_allium"));
            hashmap.put("minecraft:red_flower3", ExtraDataFixUtils.blockState("minecraft:potted_azure_bluet"));
            hashmap.put("minecraft:red_flower4", ExtraDataFixUtils.blockState("minecraft:potted_red_tulip"));
            hashmap.put("minecraft:red_flower5", ExtraDataFixUtils.blockState("minecraft:potted_orange_tulip"));
            hashmap.put("minecraft:red_flower6", ExtraDataFixUtils.blockState("minecraft:potted_white_tulip"));
            hashmap.put("minecraft:red_flower7", ExtraDataFixUtils.blockState("minecraft:potted_pink_tulip"));
            hashmap.put("minecraft:red_flower8", ExtraDataFixUtils.blockState("minecraft:potted_oxeye_daisy"));
            hashmap.put("minecraft:yellow_flower0", ExtraDataFixUtils.blockState("minecraft:potted_dandelion"));
            hashmap.put("minecraft:sapling0", ExtraDataFixUtils.blockState("minecraft:potted_oak_sapling"));
            hashmap.put("minecraft:sapling1", ExtraDataFixUtils.blockState("minecraft:potted_spruce_sapling"));
            hashmap.put("minecraft:sapling2", ExtraDataFixUtils.blockState("minecraft:potted_birch_sapling"));
            hashmap.put("minecraft:sapling3", ExtraDataFixUtils.blockState("minecraft:potted_jungle_sapling"));
            hashmap.put("minecraft:sapling4", ExtraDataFixUtils.blockState("minecraft:potted_acacia_sapling"));
            hashmap.put("minecraft:sapling5", ExtraDataFixUtils.blockState("minecraft:potted_dark_oak_sapling"));
            hashmap.put("minecraft:red_mushroom0", ExtraDataFixUtils.blockState("minecraft:potted_red_mushroom"));
            hashmap.put("minecraft:brown_mushroom0", ExtraDataFixUtils.blockState("minecraft:potted_brown_mushroom"));
            hashmap.put("minecraft:deadbush0", ExtraDataFixUtils.blockState("minecraft:potted_dead_bush"));
            hashmap.put("minecraft:tallgrass2", ExtraDataFixUtils.blockState("minecraft:potted_fern"));
            hashmap.put("minecraft:cactus0", ExtraDataFixUtils.blockState("minecraft:potted_cactus"));
        });
        private static final Map<String, Dynamic<?>> SKULL_MAP = (Map) DataFixUtils.make(Maps.newHashMap(), (hashmap) -> {
            mapSkull(hashmap, 0, "skeleton", "skull");
            mapSkull(hashmap, 1, "wither_skeleton", "skull");
            mapSkull(hashmap, 2, "zombie", "head");
            mapSkull(hashmap, 3, "player", "head");
            mapSkull(hashmap, 4, "creeper", "head");
            mapSkull(hashmap, 5, "dragon", "head");
        });
        private static final Map<String, Dynamic<?>> DOOR_MAP = (Map) DataFixUtils.make(Maps.newHashMap(), (hashmap) -> {
            mapDoor(hashmap, "oak_door");
            mapDoor(hashmap, "iron_door");
            mapDoor(hashmap, "spruce_door");
            mapDoor(hashmap, "birch_door");
            mapDoor(hashmap, "jungle_door");
            mapDoor(hashmap, "acacia_door");
            mapDoor(hashmap, "dark_oak_door");
        });
        private static final Map<String, Dynamic<?>> NOTE_BLOCK_MAP = (Map) DataFixUtils.make(Maps.newHashMap(), (hashmap) -> {
            for (int i = 0; i < 26; ++i) {
                hashmap.put("true" + i, ExtraDataFixUtils.blockState("minecraft:note_block", Map.of("powered", "true", "note", String.valueOf(i))));
                hashmap.put("false" + i, ExtraDataFixUtils.blockState("minecraft:note_block", Map.of("powered", "false", "note", String.valueOf(i))));
            }

        });
        private static final Int2ObjectMap<String> DYE_COLOR_MAP = (Int2ObjectMap) DataFixUtils.make(new Int2ObjectOpenHashMap(), (int2objectopenhashmap) -> {
            int2objectopenhashmap.put(0, "white");
            int2objectopenhashmap.put(1, "orange");
            int2objectopenhashmap.put(2, "magenta");
            int2objectopenhashmap.put(3, "light_blue");
            int2objectopenhashmap.put(4, "yellow");
            int2objectopenhashmap.put(5, "lime");
            int2objectopenhashmap.put(6, "pink");
            int2objectopenhashmap.put(7, "gray");
            int2objectopenhashmap.put(8, "light_gray");
            int2objectopenhashmap.put(9, "cyan");
            int2objectopenhashmap.put(10, "purple");
            int2objectopenhashmap.put(11, "blue");
            int2objectopenhashmap.put(12, "brown");
            int2objectopenhashmap.put(13, "green");
            int2objectopenhashmap.put(14, "red");
            int2objectopenhashmap.put(15, "black");
        });
        private static final Map<String, Dynamic<?>> BED_BLOCK_MAP = (Map) DataFixUtils.make(Maps.newHashMap(), (hashmap) -> {
            ObjectIterator objectiterator = ChunkPalettedStorageFix.MappingConstants.DYE_COLOR_MAP.int2ObjectEntrySet().iterator();

            while (objectiterator.hasNext()) {
                Int2ObjectMap.Entry<String> int2objectmap_entry = (Entry) objectiterator.next();

                if (!Objects.equals(int2objectmap_entry.getValue(), "red")) {
                    addBeds(hashmap, int2objectmap_entry.getIntKey(), (String) int2objectmap_entry.getValue());
                }
            }

        });
        private static final Map<String, Dynamic<?>> BANNER_BLOCK_MAP = (Map) DataFixUtils.make(Maps.newHashMap(), (hashmap) -> {
            ObjectIterator objectiterator = ChunkPalettedStorageFix.MappingConstants.DYE_COLOR_MAP.int2ObjectEntrySet().iterator();

            while (objectiterator.hasNext()) {
                Int2ObjectMap.Entry<String> int2objectmap_entry = (Entry) objectiterator.next();

                if (!Objects.equals(int2objectmap_entry.getValue(), "white")) {
                    addBanners(hashmap, 15 - int2objectmap_entry.getIntKey(), (String) int2objectmap_entry.getValue());
                }
            }

        });
        private static final Dynamic<?> AIR;

        private MappingConstants() {}

        private static void mapSkull(Map<String, Dynamic<?>> map, int i, String name, String type) {
            map.put(i + "north", ExtraDataFixUtils.blockState("minecraft:" + name + "_wall_" + type, Map.of("facing", "north")));
            map.put(i + "east", ExtraDataFixUtils.blockState("minecraft:" + name + "_wall_" + type, Map.of("facing", "east")));
            map.put(i + "south", ExtraDataFixUtils.blockState("minecraft:" + name + "_wall_" + type, Map.of("facing", "south")));
            map.put(i + "west", ExtraDataFixUtils.blockState("minecraft:" + name + "_wall_" + type, Map.of("facing", "west")));

            for (int j = 0; j < 16; ++j) {
                map.put("" + i + j, ExtraDataFixUtils.blockState("minecraft:" + name + "_" + type, Map.of("rotation", String.valueOf(j))));
            }

        }

        private static void mapDoor(Map<String, Dynamic<?>> map, String type) {
            String s1 = "minecraft:" + type;

            map.put("minecraft:" + type + "eastlowerleftfalsefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "east", "half", "lower", "hinge", "left", "open", "false", "powered", "false")));
            map.put("minecraft:" + type + "eastlowerleftfalsetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "east", "half", "lower", "hinge", "left", "open", "false", "powered", "true")));
            map.put("minecraft:" + type + "eastlowerlefttruefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "east", "half", "lower", "hinge", "left", "open", "true", "powered", "false")));
            map.put("minecraft:" + type + "eastlowerlefttruetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "east", "half", "lower", "hinge", "left", "open", "true", "powered", "true")));
            map.put("minecraft:" + type + "eastlowerrightfalsefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "east", "half", "lower", "hinge", "right", "open", "false", "powered", "false")));
            map.put("minecraft:" + type + "eastlowerrightfalsetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "east", "half", "lower", "hinge", "right", "open", "false", "powered", "true")));
            map.put("minecraft:" + type + "eastlowerrighttruefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "east", "half", "lower", "hinge", "right", "open", "true", "powered", "false")));
            map.put("minecraft:" + type + "eastlowerrighttruetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "east", "half", "lower", "hinge", "right", "open", "true", "powered", "true")));
            map.put("minecraft:" + type + "eastupperleftfalsefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "east", "half", "upper", "hinge", "left", "open", "false", "powered", "false")));
            map.put("minecraft:" + type + "eastupperleftfalsetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "east", "half", "upper", "hinge", "left", "open", "false", "powered", "true")));
            map.put("minecraft:" + type + "eastupperlefttruefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "east", "half", "upper", "hinge", "left", "open", "true", "powered", "false")));
            map.put("minecraft:" + type + "eastupperlefttruetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "east", "half", "upper", "hinge", "left", "open", "true", "powered", "true")));
            map.put("minecraft:" + type + "eastupperrightfalsefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "east", "half", "upper", "hinge", "right", "open", "false", "powered", "false")));
            map.put("minecraft:" + type + "eastupperrightfalsetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "east", "half", "upper", "hinge", "right", "open", "false", "powered", "true")));
            map.put("minecraft:" + type + "eastupperrighttruefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "east", "half", "upper", "hinge", "right", "open", "true", "powered", "false")));
            map.put("minecraft:" + type + "eastupperrighttruetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "east", "half", "upper", "hinge", "right", "open", "true", "powered", "true")));
            map.put("minecraft:" + type + "northlowerleftfalsefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "north", "half", "lower", "hinge", "left", "open", "false", "powered", "false")));
            map.put("minecraft:" + type + "northlowerleftfalsetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "north", "half", "lower", "hinge", "left", "open", "false", "powered", "true")));
            map.put("minecraft:" + type + "northlowerlefttruefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "north", "half", "lower", "hinge", "left", "open", "true", "powered", "false")));
            map.put("minecraft:" + type + "northlowerlefttruetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "north", "half", "lower", "hinge", "left", "open", "true", "powered", "true")));
            map.put("minecraft:" + type + "northlowerrightfalsefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "north", "half", "lower", "hinge", "right", "open", "false", "powered", "false")));
            map.put("minecraft:" + type + "northlowerrightfalsetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "north", "half", "lower", "hinge", "right", "open", "false", "powered", "true")));
            map.put("minecraft:" + type + "northlowerrighttruefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "north", "half", "lower", "hinge", "right", "open", "true", "powered", "false")));
            map.put("minecraft:" + type + "northlowerrighttruetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "north", "half", "lower", "hinge", "right", "open", "true", "powered", "true")));
            map.put("minecraft:" + type + "northupperleftfalsefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "north", "half", "upper", "hinge", "left", "open", "false", "powered", "false")));
            map.put("minecraft:" + type + "northupperleftfalsetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "north", "half", "upper", "hinge", "left", "open", "false", "powered", "true")));
            map.put("minecraft:" + type + "northupperlefttruefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "north", "half", "upper", "hinge", "left", "open", "true", "powered", "false")));
            map.put("minecraft:" + type + "northupperlefttruetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "north", "half", "upper", "hinge", "left", "open", "true", "powered", "true")));
            map.put("minecraft:" + type + "northupperrightfalsefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "north", "half", "upper", "hinge", "right", "open", "false", "powered", "false")));
            map.put("minecraft:" + type + "northupperrightfalsetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "north", "half", "upper", "hinge", "right", "open", "false", "powered", "true")));
            map.put("minecraft:" + type + "northupperrighttruefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "north", "half", "upper", "hinge", "right", "open", "true", "powered", "false")));
            map.put("minecraft:" + type + "northupperrighttruetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "north", "half", "upper", "hinge", "right", "open", "true", "powered", "true")));
            map.put("minecraft:" + type + "southlowerleftfalsefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "south", "half", "lower", "hinge", "left", "open", "false", "powered", "false")));
            map.put("minecraft:" + type + "southlowerleftfalsetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "south", "half", "lower", "hinge", "left", "open", "false", "powered", "true")));
            map.put("minecraft:" + type + "southlowerlefttruefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "south", "half", "lower", "hinge", "left", "open", "true", "powered", "false")));
            map.put("minecraft:" + type + "southlowerlefttruetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "south", "half", "lower", "hinge", "left", "open", "true", "powered", "true")));
            map.put("minecraft:" + type + "southlowerrightfalsefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "south", "half", "lower", "hinge", "right", "open", "false", "powered", "false")));
            map.put("minecraft:" + type + "southlowerrightfalsetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "south", "half", "lower", "hinge", "right", "open", "false", "powered", "true")));
            map.put("minecraft:" + type + "southlowerrighttruefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "south", "half", "lower", "hinge", "right", "open", "true", "powered", "false")));
            map.put("minecraft:" + type + "southlowerrighttruetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "south", "half", "lower", "hinge", "right", "open", "true", "powered", "true")));
            map.put("minecraft:" + type + "southupperleftfalsefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "south", "half", "upper", "hinge", "left", "open", "false", "powered", "false")));
            map.put("minecraft:" + type + "southupperleftfalsetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "south", "half", "upper", "hinge", "left", "open", "false", "powered", "true")));
            map.put("minecraft:" + type + "southupperlefttruefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "south", "half", "upper", "hinge", "left", "open", "true", "powered", "false")));
            map.put("minecraft:" + type + "southupperlefttruetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "south", "half", "upper", "hinge", "left", "open", "true", "powered", "true")));
            map.put("minecraft:" + type + "southupperrightfalsefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "south", "half", "upper", "hinge", "right", "open", "false", "powered", "false")));
            map.put("minecraft:" + type + "southupperrightfalsetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "south", "half", "upper", "hinge", "right", "open", "false", "powered", "true")));
            map.put("minecraft:" + type + "southupperrighttruefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "south", "half", "upper", "hinge", "right", "open", "true", "powered", "false")));
            map.put("minecraft:" + type + "southupperrighttruetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "south", "half", "upper", "hinge", "right", "open", "true", "powered", "true")));
            map.put("minecraft:" + type + "westlowerleftfalsefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "west", "half", "lower", "hinge", "left", "open", "false", "powered", "false")));
            map.put("minecraft:" + type + "westlowerleftfalsetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "west", "half", "lower", "hinge", "left", "open", "false", "powered", "true")));
            map.put("minecraft:" + type + "westlowerlefttruefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "west", "half", "lower", "hinge", "left", "open", "true", "powered", "false")));
            map.put("minecraft:" + type + "westlowerlefttruetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "west", "half", "lower", "hinge", "left", "open", "true", "powered", "true")));
            map.put("minecraft:" + type + "westlowerrightfalsefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "west", "half", "lower", "hinge", "right", "open", "false", "powered", "false")));
            map.put("minecraft:" + type + "westlowerrightfalsetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "west", "half", "lower", "hinge", "right", "open", "false", "powered", "true")));
            map.put("minecraft:" + type + "westlowerrighttruefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "west", "half", "lower", "hinge", "right", "open", "true", "powered", "false")));
            map.put("minecraft:" + type + "westlowerrighttruetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "west", "half", "lower", "hinge", "right", "open", "true", "powered", "true")));
            map.put("minecraft:" + type + "westupperleftfalsefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "west", "half", "upper", "hinge", "left", "open", "false", "powered", "false")));
            map.put("minecraft:" + type + "westupperleftfalsetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "west", "half", "upper", "hinge", "left", "open", "false", "powered", "true")));
            map.put("minecraft:" + type + "westupperlefttruefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "west", "half", "upper", "hinge", "left", "open", "true", "powered", "false")));
            map.put("minecraft:" + type + "westupperlefttruetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "west", "half", "upper", "hinge", "left", "open", "true", "powered", "true")));
            map.put("minecraft:" + type + "westupperrightfalsefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "west", "half", "upper", "hinge", "right", "open", "false", "powered", "false")));
            map.put("minecraft:" + type + "westupperrightfalsetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "west", "half", "upper", "hinge", "right", "open", "false", "powered", "true")));
            map.put("minecraft:" + type + "westupperrighttruefalse", ExtraDataFixUtils.blockState(s1, Map.of("facing", "west", "half", "upper", "hinge", "right", "open", "true", "powered", "false")));
            map.put("minecraft:" + type + "westupperrighttruetrue", ExtraDataFixUtils.blockState(s1, Map.of("facing", "west", "half", "upper", "hinge", "right", "open", "true", "powered", "true")));
        }

        private static void addBeds(Map<String, Dynamic<?>> map, int colorId, String color) {
            map.put("southfalsefoot" + colorId, ExtraDataFixUtils.blockState("minecraft:" + color + "_bed", Map.of("facing", "south", "occupied", "false", "part", "foot")));
            map.put("westfalsefoot" + colorId, ExtraDataFixUtils.blockState("minecraft:" + color + "_bed", Map.of("facing", "west", "occupied", "false", "part", "foot")));
            map.put("northfalsefoot" + colorId, ExtraDataFixUtils.blockState("minecraft:" + color + "_bed", Map.of("facing", "north", "occupied", "false", "part", "foot")));
            map.put("eastfalsefoot" + colorId, ExtraDataFixUtils.blockState("minecraft:" + color + "_bed", Map.of("facing", "east", "occupied", "false", "part", "foot")));
            map.put("southfalsehead" + colorId, ExtraDataFixUtils.blockState("minecraft:" + color + "_bed", Map.of("facing", "south", "occupied", "false", "part", "head")));
            map.put("westfalsehead" + colorId, ExtraDataFixUtils.blockState("minecraft:" + color + "_bed", Map.of("facing", "west", "occupied", "false", "part", "head")));
            map.put("northfalsehead" + colorId, ExtraDataFixUtils.blockState("minecraft:" + color + "_bed", Map.of("facing", "north", "occupied", "false", "part", "head")));
            map.put("eastfalsehead" + colorId, ExtraDataFixUtils.blockState("minecraft:" + color + "_bed", Map.of("facing", "east", "occupied", "false", "part", "head")));
            map.put("southtruehead" + colorId, ExtraDataFixUtils.blockState("minecraft:" + color + "_bed", Map.of("facing", "south", "occupied", "true", "part", "head")));
            map.put("westtruehead" + colorId, ExtraDataFixUtils.blockState("minecraft:" + color + "_bed", Map.of("facing", "west", "occupied", "true", "part", "head")));
            map.put("northtruehead" + colorId, ExtraDataFixUtils.blockState("minecraft:" + color + "_bed", Map.of("facing", "north", "occupied", "true", "part", "head")));
            map.put("easttruehead" + colorId, ExtraDataFixUtils.blockState("minecraft:" + color + "_bed", Map.of("facing", "east", "occupied", "true", "part", "head")));
        }

        private static void addBanners(Map<String, Dynamic<?>> map, int colorId, String color) {
            for (int j = 0; j < 16; ++j) {
                map.put(j + "_" + colorId, ExtraDataFixUtils.blockState("minecraft:" + color + "_banner", Map.of("rotation", String.valueOf(j))));
            }

            map.put("north_" + colorId, ExtraDataFixUtils.blockState("minecraft:" + color + "_wall_banner", Map.of("facing", "north")));
            map.put("south_" + colorId, ExtraDataFixUtils.blockState("minecraft:" + color + "_wall_banner", Map.of("facing", "south")));
            map.put("west_" + colorId, ExtraDataFixUtils.blockState("minecraft:" + color + "_wall_banner", Map.of("facing", "west")));
            map.put("east_" + colorId, ExtraDataFixUtils.blockState("minecraft:" + color + "_wall_banner", Map.of("facing", "east")));
        }

        static {
            ChunkPalettedStorageFix.MappingConstants.FIX.set(2);
            ChunkPalettedStorageFix.MappingConstants.FIX.set(3);
            ChunkPalettedStorageFix.MappingConstants.FIX.set(110);
            ChunkPalettedStorageFix.MappingConstants.FIX.set(140);
            ChunkPalettedStorageFix.MappingConstants.FIX.set(144);
            ChunkPalettedStorageFix.MappingConstants.FIX.set(25);
            ChunkPalettedStorageFix.MappingConstants.FIX.set(86);
            ChunkPalettedStorageFix.MappingConstants.FIX.set(26);
            ChunkPalettedStorageFix.MappingConstants.FIX.set(176);
            ChunkPalettedStorageFix.MappingConstants.FIX.set(177);
            ChunkPalettedStorageFix.MappingConstants.FIX.set(175);
            ChunkPalettedStorageFix.MappingConstants.FIX.set(64);
            ChunkPalettedStorageFix.MappingConstants.FIX.set(71);
            ChunkPalettedStorageFix.MappingConstants.FIX.set(193);
            ChunkPalettedStorageFix.MappingConstants.FIX.set(194);
            ChunkPalettedStorageFix.MappingConstants.FIX.set(195);
            ChunkPalettedStorageFix.MappingConstants.FIX.set(196);
            ChunkPalettedStorageFix.MappingConstants.FIX.set(197);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(54);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(146);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(25);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(26);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(51);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(53);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(67);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(108);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(109);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(114);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(128);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(134);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(135);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(136);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(156);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(163);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(164);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(180);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(203);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(55);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(85);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(113);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(188);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(189);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(190);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(191);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(192);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(93);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(94);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(101);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(102);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(160);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(106);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(107);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(183);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(184);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(185);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(186);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(187);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(132);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(139);
            ChunkPalettedStorageFix.MappingConstants.VIRTUAL.set(199);
            AIR = ExtraDataFixUtils.blockState("minecraft:air");
        }
    }

    private static class Section {

        private final CrudeIncrementalIntIdentityHashBiMap<Dynamic<?>> palette = CrudeIncrementalIntIdentityHashBiMap.<Dynamic<?>>create(32);
        private final List<Dynamic<?>> listTag = Lists.newArrayList();
        private final Dynamic<?> section;
        private final boolean hasData;
        private final Int2ObjectMap<IntList> toFix = new Int2ObjectLinkedOpenHashMap();
        private final IntList update = new IntArrayList();
        public final int y;
        private final Set<Dynamic<?>> seen = Sets.newIdentityHashSet();
        private final int[] buffer = new int[4096];

        public Section(Dynamic<?> section) {
            this.section = section;
            this.y = section.get("Y").asInt(0);
            this.hasData = section.get("Blocks").result().isPresent();
        }

        public Dynamic<?> getBlock(int pos) {
            if (pos >= 0 && pos <= 4095) {
                Dynamic<?> dynamic = (Dynamic) this.palette.byId(this.buffer[pos]);

                return dynamic == null ? ChunkPalettedStorageFix.MappingConstants.AIR : dynamic;
            } else {
                return ChunkPalettedStorageFix.MappingConstants.AIR;
            }
        }

        public void setBlock(int idx, Dynamic<?> blockState) {
            if (this.seen.add(blockState)) {
                this.listTag.add("%%FILTER_ME%%".equals(ChunkPalettedStorageFix.getName(blockState)) ? ChunkPalettedStorageFix.MappingConstants.AIR : blockState);
            }

            this.buffer[idx] = ChunkPalettedStorageFix.idFor(this.palette, blockState);
        }

        public int upgrade(int sides) {
            if (!this.hasData) {
                return sides;
            } else {
                ByteBuffer bytebuffer = (ByteBuffer) this.section.get("Blocks").asByteBufferOpt().result().get();
                ChunkPalettedStorageFix.DataLayer chunkpalettedstoragefix_datalayer = (ChunkPalettedStorageFix.DataLayer) this.section.get("Data").asByteBufferOpt().map((bytebuffer1) -> {
                    return new ChunkPalettedStorageFix.DataLayer(DataFixUtils.toArray(bytebuffer1));
                }).result().orElseGet(ChunkPalettedStorageFix.DataLayer::new);
                ChunkPalettedStorageFix.DataLayer chunkpalettedstoragefix_datalayer1 = (ChunkPalettedStorageFix.DataLayer) this.section.get("Add").asByteBufferOpt().map((bytebuffer1) -> {
                    return new ChunkPalettedStorageFix.DataLayer(DataFixUtils.toArray(bytebuffer1));
                }).result().orElseGet(ChunkPalettedStorageFix.DataLayer::new);

                this.seen.add(ChunkPalettedStorageFix.MappingConstants.AIR);
                ChunkPalettedStorageFix.idFor(this.palette, ChunkPalettedStorageFix.MappingConstants.AIR);
                this.listTag.add(ChunkPalettedStorageFix.MappingConstants.AIR);

                for (int j = 0; j < 4096; ++j) {
                    int k = j & 15;
                    int l = j >> 8 & 15;
                    int i1 = j >> 4 & 15;
                    int j1 = chunkpalettedstoragefix_datalayer1.get(k, l, i1) << 12 | (bytebuffer.get(j) & 255) << 4 | chunkpalettedstoragefix_datalayer.get(k, l, i1);

                    if (ChunkPalettedStorageFix.MappingConstants.FIX.get(j1 >> 4)) {
                        this.addFix(j1 >> 4, j);
                    }

                    if (ChunkPalettedStorageFix.MappingConstants.VIRTUAL.get(j1 >> 4)) {
                        int k1 = ChunkPalettedStorageFix.getSideMask(k == 0, k == 15, i1 == 0, i1 == 15);

                        if (k1 == 0) {
                            this.update.add(j);
                        } else {
                            sides |= k1;
                        }
                    }

                    this.setBlock(j, BlockStateData.getTag(j1));
                }

                return sides;
            }
        }

        private void addFix(int id, int position) {
            IntList intlist = (IntList) this.toFix.get(id);

            if (intlist == null) {
                intlist = new IntArrayList();
                this.toFix.put(id, intlist);
            }

            intlist.add(position);
        }

        public Dynamic<?> write() {
            Dynamic<?> dynamic = this.section;

            if (!this.hasData) {
                return dynamic;
            } else {
                dynamic = dynamic.set("Palette", dynamic.createList(this.listTag.stream()));
                int i = Math.max(4, DataFixUtils.ceillog2(this.seen.size()));
                PackedBitStorage packedbitstorage = new PackedBitStorage(i, 4096);

                for (int j = 0; j < this.buffer.length; ++j) {
                    packedbitstorage.set(j, this.buffer[j]);
                }

                dynamic = dynamic.set("BlockStates", dynamic.createLongList(Arrays.stream(packedbitstorage.getRaw())));
                dynamic = dynamic.remove("Blocks");
                dynamic = dynamic.remove("Data");
                dynamic = dynamic.remove("Add");
                return dynamic;
            }
        }
    }

    private static final class UpgradeChunk {

        private int sides;
        private final @Nullable ChunkPalettedStorageFix.Section[] sections = new ChunkPalettedStorageFix.Section[16];
        private final Dynamic<?> level;
        private final int x;
        private final int z;
        private final Int2ObjectMap<Dynamic<?>> blockEntities = new Int2ObjectLinkedOpenHashMap(16);

        public UpgradeChunk(Dynamic<?> level) {
            this.level = level;
            this.x = level.get("xPos").asInt(0) << 4;
            this.z = level.get("zPos").asInt(0) << 4;
            level.get("TileEntities").asStreamOpt().ifSuccess((stream) -> {
                stream.forEach((dynamic1) -> {
                    int i = dynamic1.get("x").asInt(0) - this.x & 15;
                    int j = dynamic1.get("y").asInt(0);
                    int k = dynamic1.get("z").asInt(0) - this.z & 15;
                    int l = j << 8 | k << 4 | i;

                    if (this.blockEntities.put(l, dynamic1) != null) {
                        ChunkPalettedStorageFix.LOGGER.warn("In chunk: {}x{} found a duplicate block entity at position: [{}, {}, {}]", new Object[]{this.x, this.z, i, j, k});
                    }

                });
            });
            boolean flag = level.get("convertedFromAlphaFormat").asBoolean(false);

            level.get("Sections").asStreamOpt().ifSuccess((stream) -> {
                stream.forEach((dynamic1) -> {
                    ChunkPalettedStorageFix.Section chunkpalettedstoragefix_section = new ChunkPalettedStorageFix.Section(dynamic1);

                    this.sides = chunkpalettedstoragefix_section.upgrade(this.sides);
                    this.sections[chunkpalettedstoragefix_section.y] = chunkpalettedstoragefix_section;
                });
            });

            for (ChunkPalettedStorageFix.Section chunkpalettedstoragefix_section : this.sections) {
                if (chunkpalettedstoragefix_section != null) {
                    ObjectIterator objectiterator = chunkpalettedstoragefix_section.toFix.int2ObjectEntrySet().iterator();

                    while (objectiterator.hasNext()) {
                        Int2ObjectMap.Entry<IntList> int2objectmap_entry = (Entry) objectiterator.next();
                        int i = chunkpalettedstoragefix_section.y << 12;

                        switch (int2objectmap_entry.getIntKey()) {
                            case 2:
                                IntListIterator intlistiterator = ((IntList) int2objectmap_entry.getValue()).iterator();

                                while (intlistiterator.hasNext()) {
                                    int j = (Integer) intlistiterator.next();

                                    j |= i;
                                    Dynamic<?> dynamic1 = this.getBlock(j);

                                    if ("minecraft:grass_block".equals(ChunkPalettedStorageFix.getName(dynamic1))) {
                                        String s = ChunkPalettedStorageFix.getName(this.getBlock(relative(j, ChunkPalettedStorageFix.Direction.UP)));

                                        if ("minecraft:snow".equals(s) || "minecraft:snow_layer".equals(s)) {
                                            this.setBlock(j, ChunkPalettedStorageFix.MappingConstants.SNOWY_GRASS);
                                        }
                                    }
                                }
                                break;
                            case 3:
                                IntListIterator intlistiterator1 = ((IntList) int2objectmap_entry.getValue()).iterator();

                                while (intlistiterator1.hasNext()) {
                                    int k = (Integer) intlistiterator1.next();

                                    k |= i;
                                    Dynamic<?> dynamic2 = this.getBlock(k);

                                    if ("minecraft:podzol".equals(ChunkPalettedStorageFix.getName(dynamic2))) {
                                        String s1 = ChunkPalettedStorageFix.getName(this.getBlock(relative(k, ChunkPalettedStorageFix.Direction.UP)));

                                        if ("minecraft:snow".equals(s1) || "minecraft:snow_layer".equals(s1)) {
                                            this.setBlock(k, ChunkPalettedStorageFix.MappingConstants.SNOWY_PODZOL);
                                        }
                                    }
                                }
                                break;
                            case 25:
                                IntListIterator intlistiterator2 = ((IntList) int2objectmap_entry.getValue()).iterator();

                                while (intlistiterator2.hasNext()) {
                                    int l = (Integer) intlistiterator2.next();

                                    l |= i;
                                    Dynamic<?> dynamic3 = this.removeBlockEntity(l);

                                    if (dynamic3 != null) {
                                        String s2 = Boolean.toString(dynamic3.get("powered").asBoolean(false));
                                        String s3 = s2 + (byte) Math.min(Math.max(dynamic3.get("note").asInt(0), 0), 24);

                                        this.setBlock(l, (Dynamic) ChunkPalettedStorageFix.MappingConstants.NOTE_BLOCK_MAP.getOrDefault(s3, (Dynamic) ChunkPalettedStorageFix.MappingConstants.NOTE_BLOCK_MAP.get("false0")));
                                    }
                                }
                                break;
                            case 26:
                                IntListIterator intlistiterator3 = ((IntList) int2objectmap_entry.getValue()).iterator();

                                while (intlistiterator3.hasNext()) {
                                    int i1 = (Integer) intlistiterator3.next();

                                    i1 |= i;
                                    Dynamic<?> dynamic4 = this.getBlockEntity(i1);
                                    Dynamic<?> dynamic5 = this.getBlock(i1);

                                    if (dynamic4 != null) {
                                        int j1 = dynamic4.get("color").asInt(0);

                                        if (j1 != 14 && j1 >= 0 && j1 < 16) {
                                            String s4 = ChunkPalettedStorageFix.getProperty(dynamic5, "facing");
                                            String s5 = s4 + ChunkPalettedStorageFix.getProperty(dynamic5, "occupied") + ChunkPalettedStorageFix.getProperty(dynamic5, "part") + j1;

                                            if (ChunkPalettedStorageFix.MappingConstants.BED_BLOCK_MAP.containsKey(s5)) {
                                                this.setBlock(i1, (Dynamic) ChunkPalettedStorageFix.MappingConstants.BED_BLOCK_MAP.get(s5));
                                            }
                                        }
                                    }
                                }
                                break;
                            case 64:
                            case 71:
                            case 193:
                            case 194:
                            case 195:
                            case 196:
                            case 197:
                                IntListIterator intlistiterator4 = ((IntList) int2objectmap_entry.getValue()).iterator();

                                while (intlistiterator4.hasNext()) {
                                    int k1 = (Integer) intlistiterator4.next();

                                    k1 |= i;
                                    Dynamic<?> dynamic6 = this.getBlock(k1);

                                    if (ChunkPalettedStorageFix.getName(dynamic6).endsWith("_door")) {
                                        Dynamic<?> dynamic7 = this.getBlock(k1);

                                        if ("lower".equals(ChunkPalettedStorageFix.getProperty(dynamic7, "half"))) {
                                            int l1 = relative(k1, ChunkPalettedStorageFix.Direction.UP);
                                            Dynamic<?> dynamic8 = this.getBlock(l1);
                                            String s6 = ChunkPalettedStorageFix.getName(dynamic7);

                                            if (s6.equals(ChunkPalettedStorageFix.getName(dynamic8))) {
                                                String s7 = ChunkPalettedStorageFix.getProperty(dynamic7, "facing");
                                                String s8 = ChunkPalettedStorageFix.getProperty(dynamic7, "open");
                                                String s9 = flag ? "left" : ChunkPalettedStorageFix.getProperty(dynamic8, "hinge");
                                                String s10 = flag ? "false" : ChunkPalettedStorageFix.getProperty(dynamic8, "powered");

                                                this.setBlock(k1, (Dynamic) ChunkPalettedStorageFix.MappingConstants.DOOR_MAP.get(s6 + s7 + "lower" + s9 + s8 + s10));
                                                this.setBlock(l1, (Dynamic) ChunkPalettedStorageFix.MappingConstants.DOOR_MAP.get(s6 + s7 + "upper" + s9 + s8 + s10));
                                            }
                                        }
                                    }
                                }
                                break;
                            case 86:
                                IntListIterator intlistiterator5 = ((IntList) int2objectmap_entry.getValue()).iterator();

                                while (intlistiterator5.hasNext()) {
                                    int i2 = (Integer) intlistiterator5.next();

                                    i2 |= i;
                                    Dynamic<?> dynamic9 = this.getBlock(i2);

                                    if ("minecraft:carved_pumpkin".equals(ChunkPalettedStorageFix.getName(dynamic9))) {
                                        String s11 = ChunkPalettedStorageFix.getName(this.getBlock(relative(i2, ChunkPalettedStorageFix.Direction.DOWN)));

                                        if ("minecraft:grass_block".equals(s11) || "minecraft:dirt".equals(s11)) {
                                            this.setBlock(i2, ChunkPalettedStorageFix.MappingConstants.PUMPKIN);
                                        }
                                    }
                                }
                                break;
                            case 110:
                                IntListIterator intlistiterator6 = ((IntList) int2objectmap_entry.getValue()).iterator();

                                while (intlistiterator6.hasNext()) {
                                    int j2 = (Integer) intlistiterator6.next();

                                    j2 |= i;
                                    Dynamic<?> dynamic10 = this.getBlock(j2);

                                    if ("minecraft:mycelium".equals(ChunkPalettedStorageFix.getName(dynamic10))) {
                                        String s12 = ChunkPalettedStorageFix.getName(this.getBlock(relative(j2, ChunkPalettedStorageFix.Direction.UP)));

                                        if ("minecraft:snow".equals(s12) || "minecraft:snow_layer".equals(s12)) {
                                            this.setBlock(j2, ChunkPalettedStorageFix.MappingConstants.SNOWY_MYCELIUM);
                                        }
                                    }
                                }
                                break;
                            case 140:
                                IntListIterator intlistiterator7 = ((IntList) int2objectmap_entry.getValue()).iterator();

                                while (intlistiterator7.hasNext()) {
                                    int k2 = (Integer) intlistiterator7.next();

                                    k2 |= i;
                                    Dynamic<?> dynamic11 = this.removeBlockEntity(k2);

                                    if (dynamic11 != null) {
                                        String s13 = dynamic11.get("Item").asString("");
                                        String s14 = s13 + dynamic11.get("Data").asInt(0);

                                        this.setBlock(k2, (Dynamic) ChunkPalettedStorageFix.MappingConstants.FLOWER_POT_MAP.getOrDefault(s14, (Dynamic) ChunkPalettedStorageFix.MappingConstants.FLOWER_POT_MAP.get("minecraft:air0")));
                                    }
                                }
                                break;
                            case 144:
                                IntListIterator intlistiterator8 = ((IntList) int2objectmap_entry.getValue()).iterator();

                                while (intlistiterator8.hasNext()) {
                                    int l2 = (Integer) intlistiterator8.next();

                                    l2 |= i;
                                    Dynamic<?> dynamic12 = this.getBlockEntity(l2);

                                    if (dynamic12 != null) {
                                        String s15 = String.valueOf(dynamic12.get("SkullType").asInt(0));
                                        String s16 = ChunkPalettedStorageFix.getProperty(this.getBlock(l2), "facing");
                                        String s17;

                                        if (!"up".equals(s16) && !"down".equals(s16)) {
                                            s17 = s15 + s16;
                                        } else {
                                            s17 = s15 + dynamic12.get("Rot").asInt(0);
                                        }

                                        dynamic12.remove("SkullType");
                                        dynamic12.remove("facing");
                                        dynamic12.remove("Rot");
                                        this.setBlock(l2, (Dynamic) ChunkPalettedStorageFix.MappingConstants.SKULL_MAP.getOrDefault(s17, (Dynamic) ChunkPalettedStorageFix.MappingConstants.SKULL_MAP.get("0north")));
                                    }
                                }
                                break;
                            case 175:
                                IntListIterator intlistiterator9 = ((IntList) int2objectmap_entry.getValue()).iterator();

                                while (intlistiterator9.hasNext()) {
                                    int i3 = (Integer) intlistiterator9.next();

                                    i3 |= i;
                                    Dynamic<?> dynamic13 = this.getBlock(i3);

                                    if ("upper".equals(ChunkPalettedStorageFix.getProperty(dynamic13, "half"))) {
                                        Dynamic<?> dynamic14 = this.getBlock(relative(i3, ChunkPalettedStorageFix.Direction.DOWN));

                                        switch (ChunkPalettedStorageFix.getName(dynamic14)) {
                                            case "minecraft:sunflower":
                                                this.setBlock(i3, ChunkPalettedStorageFix.MappingConstants.UPPER_SUNFLOWER);
                                                break;
                                            case "minecraft:lilac":
                                                this.setBlock(i3, ChunkPalettedStorageFix.MappingConstants.UPPER_LILAC);
                                                break;
                                            case "minecraft:tall_grass":
                                                this.setBlock(i3, ChunkPalettedStorageFix.MappingConstants.UPPER_TALL_GRASS);
                                                break;
                                            case "minecraft:large_fern":
                                                this.setBlock(i3, ChunkPalettedStorageFix.MappingConstants.UPPER_LARGE_FERN);
                                                break;
                                            case "minecraft:rose_bush":
                                                this.setBlock(i3, ChunkPalettedStorageFix.MappingConstants.UPPER_ROSE_BUSH);
                                                break;
                                            case "minecraft:peony":
                                                this.setBlock(i3, ChunkPalettedStorageFix.MappingConstants.UPPER_PEONY);
                                        }
                                    }
                                }
                                break;
                            case 176:
                            case 177:
                                IntListIterator intlistiterator10 = ((IntList) int2objectmap_entry.getValue()).iterator();

                                while (intlistiterator10.hasNext()) {
                                    int j3 = (Integer) intlistiterator10.next();

                                    j3 |= i;
                                    Dynamic<?> dynamic15 = this.getBlockEntity(j3);
                                    Dynamic<?> dynamic16 = this.getBlock(j3);

                                    if (dynamic15 != null) {
                                        int k3 = dynamic15.get("Base").asInt(0);

                                        if (k3 != 15 && k3 >= 0 && k3 < 16) {
                                            String s18 = ChunkPalettedStorageFix.getProperty(dynamic16, int2objectmap_entry.getIntKey() == 176 ? "rotation" : "facing");
                                            String s19 = s18 + "_" + k3;

                                            if (ChunkPalettedStorageFix.MappingConstants.BANNER_BLOCK_MAP.containsKey(s19)) {
                                                this.setBlock(j3, (Dynamic) ChunkPalettedStorageFix.MappingConstants.BANNER_BLOCK_MAP.get(s19));
                                            }
                                        }
                                    }
                                }
                        }
                    }
                }
            }

        }

        private @Nullable Dynamic<?> getBlockEntity(int pos) {
            return (Dynamic) this.blockEntities.get(pos);
        }

        private @Nullable Dynamic<?> removeBlockEntity(int pos) {
            return (Dynamic) this.blockEntities.remove(pos);
        }

        public static int relative(int pos, ChunkPalettedStorageFix.Direction direction) {
            int j;

            switch (direction.getAxis().ordinal()) {
                case 0:
                    int k = (pos & 15) + direction.getAxisDirection().getStep();

                    j = k >= 0 && k <= 15 ? pos & -16 | k : -1;
                    break;
                case 1:
                    int l = (pos >> 8) + direction.getAxisDirection().getStep();

                    j = l >= 0 && l <= 255 ? pos & 255 | l << 8 : -1;
                    break;
                case 2:
                    int i1 = (pos >> 4 & 15) + direction.getAxisDirection().getStep();

                    j = i1 >= 0 && i1 <= 15 ? pos & -241 | i1 << 4 : -1;
                    break;
                default:
                    throw new MatchException((String) null, (Throwable) null);
            }

            return j;
        }

        private void setBlock(int pos, Dynamic<?> block) {
            if (pos >= 0 && pos <= 65535) {
                ChunkPalettedStorageFix.Section chunkpalettedstoragefix_section = this.getSection(pos);

                if (chunkpalettedstoragefix_section != null) {
                    chunkpalettedstoragefix_section.setBlock(pos & 4095, block);
                }
            }
        }

        private ChunkPalettedStorageFix.@Nullable Section getSection(int pos) {
            int j = pos >> 12;

            return j < this.sections.length ? this.sections[j] : null;
        }

        public Dynamic<?> getBlock(int pos) {
            if (pos >= 0 && pos <= 65535) {
                ChunkPalettedStorageFix.Section chunkpalettedstoragefix_section = this.getSection(pos);

                return chunkpalettedstoragefix_section == null ? ChunkPalettedStorageFix.MappingConstants.AIR : chunkpalettedstoragefix_section.getBlock(pos & 4095);
            } else {
                return ChunkPalettedStorageFix.MappingConstants.AIR;
            }
        }

        public Dynamic<?> write() {
            Dynamic<?> dynamic = this.level;

            if (this.blockEntities.isEmpty()) {
                dynamic = dynamic.remove("TileEntities");
            } else {
                dynamic = dynamic.set("TileEntities", dynamic.createList(this.blockEntities.values().stream()));
            }

            Dynamic<?> dynamic1 = dynamic.emptyMap();
            List<Dynamic<?>> list = Lists.newArrayList();

            for (ChunkPalettedStorageFix.Section chunkpalettedstoragefix_section : this.sections) {
                if (chunkpalettedstoragefix_section != null) {
                    list.add(chunkpalettedstoragefix_section.write());
                    dynamic1 = dynamic1.set(String.valueOf(chunkpalettedstoragefix_section.y), dynamic1.createIntList(Arrays.stream(chunkpalettedstoragefix_section.update.toIntArray())));
                }
            }

            Dynamic<?> dynamic2 = dynamic.emptyMap();

            dynamic2 = dynamic2.set("Sides", dynamic2.createByte((byte) this.sides));
            dynamic2 = dynamic2.set("Indices", dynamic1);
            return dynamic.set("UpgradeData", dynamic2).set("Sections", dynamic2.createList(list.stream()));
        }
    }

    private static class DataLayer {

        private static final int SIZE = 2048;
        private static final int NIBBLE_SIZE = 4;
        private final byte[] data;

        public DataLayer() {
            this.data = new byte[2048];
        }

        public DataLayer(byte[] data) {
            this.data = data;
            if (data.length != 2048) {
                throw new IllegalArgumentException("ChunkNibbleArrays should be 2048 bytes not: " + data.length);
            }
        }

        public int get(int x, int y, int z) {
            int l = this.getPosition(y << 8 | z << 4 | x);

            return this.isFirst(y << 8 | z << 4 | x) ? this.data[l] & 15 : this.data[l] >> 4 & 15;
        }

        private boolean isFirst(int position) {
            return (position & 1) == 0;
        }

        private int getPosition(int position) {
            return position >> 1;
        }
    }

    public static enum Direction {

        DOWN(ChunkPalettedStorageFix.Direction.AxisDirection.NEGATIVE, ChunkPalettedStorageFix.Direction.Axis.Y), UP(ChunkPalettedStorageFix.Direction.AxisDirection.POSITIVE, ChunkPalettedStorageFix.Direction.Axis.Y), NORTH(ChunkPalettedStorageFix.Direction.AxisDirection.NEGATIVE, ChunkPalettedStorageFix.Direction.Axis.Z), SOUTH(ChunkPalettedStorageFix.Direction.AxisDirection.POSITIVE, ChunkPalettedStorageFix.Direction.Axis.Z), WEST(ChunkPalettedStorageFix.Direction.AxisDirection.NEGATIVE, ChunkPalettedStorageFix.Direction.Axis.X), EAST(ChunkPalettedStorageFix.Direction.AxisDirection.POSITIVE, ChunkPalettedStorageFix.Direction.Axis.X);

        private final ChunkPalettedStorageFix.Direction.Axis axis;
        private final ChunkPalettedStorageFix.Direction.AxisDirection axisDirection;

        private Direction(ChunkPalettedStorageFix.Direction.AxisDirection axisDirection, ChunkPalettedStorageFix.Direction.Axis axis) {
            this.axis = axis;
            this.axisDirection = axisDirection;
        }

        public ChunkPalettedStorageFix.Direction.AxisDirection getAxisDirection() {
            return this.axisDirection;
        }

        public ChunkPalettedStorageFix.Direction.Axis getAxis() {
            return this.axis;
        }

        public static enum Axis {

            X, Y, Z;

            private Axis() {}
        }

        public static enum AxisDirection {

            POSITIVE(1), NEGATIVE(-1);

            private final int step;

            private AxisDirection(int step) {
                this.step = step;
            }

            public int getStep() {
                return this.step;
            }
        }
    }
}
