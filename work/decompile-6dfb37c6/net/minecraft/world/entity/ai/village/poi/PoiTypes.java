package net.minecraft.world.entity.ai.village.poi;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

public class PoiTypes {

    public static final ResourceKey<PoiType> ARMORER = createKey("armorer");
    public static final ResourceKey<PoiType> BUTCHER = createKey("butcher");
    public static final ResourceKey<PoiType> CARTOGRAPHER = createKey("cartographer");
    public static final ResourceKey<PoiType> CLERIC = createKey("cleric");
    public static final ResourceKey<PoiType> FARMER = createKey("farmer");
    public static final ResourceKey<PoiType> FISHERMAN = createKey("fisherman");
    public static final ResourceKey<PoiType> FLETCHER = createKey("fletcher");
    public static final ResourceKey<PoiType> LEATHERWORKER = createKey("leatherworker");
    public static final ResourceKey<PoiType> LIBRARIAN = createKey("librarian");
    public static final ResourceKey<PoiType> MASON = createKey("mason");
    public static final ResourceKey<PoiType> SHEPHERD = createKey("shepherd");
    public static final ResourceKey<PoiType> TOOLSMITH = createKey("toolsmith");
    public static final ResourceKey<PoiType> WEAPONSMITH = createKey("weaponsmith");
    public static final ResourceKey<PoiType> HOME = createKey("home");
    public static final ResourceKey<PoiType> MEETING = createKey("meeting");
    public static final ResourceKey<PoiType> BEEHIVE = createKey("beehive");
    public static final ResourceKey<PoiType> BEE_NEST = createKey("bee_nest");
    public static final ResourceKey<PoiType> NETHER_PORTAL = createKey("nether_portal");
    public static final ResourceKey<PoiType> LODESTONE = createKey("lodestone");
    public static final ResourceKey<PoiType> LIGHTNING_ROD = createKey("lightning_rod");
    public static final ResourceKey<PoiType> TEST_INSTANCE = createKey("test_instance");
    private static final Set<BlockState> BEDS = (Set) ImmutableList.of(Blocks.RED_BED, Blocks.BLACK_BED, Blocks.BLUE_BED, Blocks.BROWN_BED, Blocks.CYAN_BED, Blocks.GRAY_BED, Blocks.GREEN_BED, Blocks.LIGHT_BLUE_BED, Blocks.LIGHT_GRAY_BED, Blocks.LIME_BED, Blocks.MAGENTA_BED, Blocks.ORANGE_BED, new Block[]{Blocks.PINK_BED, Blocks.PURPLE_BED, Blocks.WHITE_BED, Blocks.YELLOW_BED}).stream().flatMap((block) -> {
        return block.getStateDefinition().getPossibleStates().stream();
    }).filter((blockstate) -> {
        return blockstate.getValue(BedBlock.PART) == BedPart.HEAD;
    }).collect(ImmutableSet.toImmutableSet());
    private static final Set<BlockState> CAULDRONS = (Set) ImmutableList.of(Blocks.CAULDRON, Blocks.LAVA_CAULDRON, Blocks.WATER_CAULDRON, Blocks.POWDER_SNOW_CAULDRON).stream().flatMap((block) -> {
        return block.getStateDefinition().getPossibleStates().stream();
    }).collect(ImmutableSet.toImmutableSet());
    private static final Set<BlockState> LIGHTNING_RODS = (Set) ImmutableList.of(Blocks.LIGHTNING_ROD, Blocks.EXPOSED_LIGHTNING_ROD, Blocks.WEATHERED_LIGHTNING_ROD, Blocks.OXIDIZED_LIGHTNING_ROD, Blocks.WAXED_LIGHTNING_ROD, Blocks.WAXED_EXPOSED_LIGHTNING_ROD, Blocks.WAXED_WEATHERED_LIGHTNING_ROD, Blocks.WAXED_OXIDIZED_LIGHTNING_ROD).stream().flatMap((block) -> {
        return block.getStateDefinition().getPossibleStates().stream();
    }).collect(ImmutableSet.toImmutableSet());
    private static final Map<BlockState, Holder<PoiType>> TYPE_BY_STATE = Maps.newHashMap();

    public PoiTypes() {}

    private static Set<BlockState> getBlockStates(Block block) {
        return ImmutableSet.copyOf(block.getStateDefinition().getPossibleStates());
    }

    private static ResourceKey<PoiType> createKey(String name) {
        return ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, Identifier.withDefaultNamespace(name));
    }

    private static PoiType register(Registry<PoiType> registry, ResourceKey<PoiType> id, Set<BlockState> matchingStates, int maxTickets, int validRange) {
        PoiType poitype = new PoiType(matchingStates, maxTickets, validRange);

        Registry.register(registry, id, poitype);
        registerBlockStates(registry.getOrThrow(id), matchingStates);
        return poitype;
    }

    private static void registerBlockStates(Holder<PoiType> type, Set<BlockState> matchingStates) {
        matchingStates.forEach((blockstate) -> {
            Holder<PoiType> holder1 = (Holder) PoiTypes.TYPE_BY_STATE.put(blockstate, type);

            if (holder1 != null) {
                throw (IllegalStateException) Util.pauseInIde(new IllegalStateException(String.format(Locale.ROOT, "%s is defined in more than one PoI type", blockstate)));
            }
        });
    }

    public static Optional<Holder<PoiType>> forState(BlockState state) {
        return Optional.ofNullable((Holder) PoiTypes.TYPE_BY_STATE.get(state));
    }

    public static boolean hasPoi(BlockState state) {
        return PoiTypes.TYPE_BY_STATE.containsKey(state);
    }

    public static PoiType bootstrap(Registry<PoiType> registry) {
        register(registry, PoiTypes.ARMORER, getBlockStates(Blocks.BLAST_FURNACE), 1, 1);
        register(registry, PoiTypes.BUTCHER, getBlockStates(Blocks.SMOKER), 1, 1);
        register(registry, PoiTypes.CARTOGRAPHER, getBlockStates(Blocks.CARTOGRAPHY_TABLE), 1, 1);
        register(registry, PoiTypes.CLERIC, getBlockStates(Blocks.BREWING_STAND), 1, 1);
        register(registry, PoiTypes.FARMER, getBlockStates(Blocks.COMPOSTER), 1, 1);
        register(registry, PoiTypes.FISHERMAN, getBlockStates(Blocks.BARREL), 1, 1);
        register(registry, PoiTypes.FLETCHER, getBlockStates(Blocks.FLETCHING_TABLE), 1, 1);
        register(registry, PoiTypes.LEATHERWORKER, PoiTypes.CAULDRONS, 1, 1);
        register(registry, PoiTypes.LIBRARIAN, getBlockStates(Blocks.LECTERN), 1, 1);
        register(registry, PoiTypes.MASON, getBlockStates(Blocks.STONECUTTER), 1, 1);
        register(registry, PoiTypes.SHEPHERD, getBlockStates(Blocks.LOOM), 1, 1);
        register(registry, PoiTypes.TOOLSMITH, getBlockStates(Blocks.SMITHING_TABLE), 1, 1);
        register(registry, PoiTypes.WEAPONSMITH, getBlockStates(Blocks.GRINDSTONE), 1, 1);
        register(registry, PoiTypes.HOME, PoiTypes.BEDS, 1, 1);
        register(registry, PoiTypes.MEETING, getBlockStates(Blocks.BELL), 32, 6);
        register(registry, PoiTypes.BEEHIVE, getBlockStates(Blocks.BEEHIVE), 0, 1);
        register(registry, PoiTypes.BEE_NEST, getBlockStates(Blocks.BEE_NEST), 0, 1);
        register(registry, PoiTypes.NETHER_PORTAL, getBlockStates(Blocks.NETHER_PORTAL), 0, 1);
        register(registry, PoiTypes.LODESTONE, getBlockStates(Blocks.LODESTONE), 0, 1);
        register(registry, PoiTypes.TEST_INSTANCE, getBlockStates(Blocks.TEST_INSTANCE_BLOCK), 0, 1);
        return register(registry, PoiTypes.LIGHTNING_ROD, PoiTypes.LIGHTNING_RODS, 0, 1);
    }
}
