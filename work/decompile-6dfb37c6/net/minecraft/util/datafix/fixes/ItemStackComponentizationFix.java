package net.minecraft.util.datafix.fixes;

import com.google.common.base.Splitter;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.OptionalDynamic;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.ExtraDataFixUtils;
import net.minecraft.util.datafix.LegacyComponentDataFixUtils;
import net.minecraft.util.datafix.schemas.NamespacedSchema;
import org.jspecify.annotations.Nullable;

public class ItemStackComponentizationFix extends DataFix {

    private static final int HIDE_ENCHANTMENTS = 1;
    private static final int HIDE_MODIFIERS = 2;
    private static final int HIDE_UNBREAKABLE = 4;
    private static final int HIDE_CAN_DESTROY = 8;
    private static final int HIDE_CAN_PLACE = 16;
    private static final int HIDE_ADDITIONAL = 32;
    private static final int HIDE_DYE = 64;
    private static final int HIDE_UPGRADES = 128;
    private static final Set<String> POTION_HOLDER_IDS = Set.of("minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:tipped_arrow");
    private static final Set<String> BUCKETED_MOB_IDS = Set.of("minecraft:pufferfish_bucket", "minecraft:salmon_bucket", "minecraft:cod_bucket", "minecraft:tropical_fish_bucket", "minecraft:axolotl_bucket", "minecraft:tadpole_bucket");
    private static final List<String> BUCKETED_MOB_TAGS = List.of("NoAI", "Silent", "NoGravity", "Glowing", "Invulnerable", "Health", "Age", "Variant", "HuntingCooldown", "BucketVariantTag");
    private static final Set<String> BOOLEAN_BLOCK_STATE_PROPERTIES = Set.of("attached", "bottom", "conditional", "disarmed", "drag", "enabled", "extended", "eye", "falling", "hanging", "has_bottle_0", "has_bottle_1", "has_bottle_2", "has_record", "has_book", "inverted", "in_wall", "lit", "locked", "occupied", "open", "persistent", "powered", "short", "signal_fire", "snowy", "triggered", "unstable", "waterlogged", "berries", "bloom", "shrieking", "can_summon", "up", "down", "north", "east", "south", "west", "slot_0_occupied", "slot_1_occupied", "slot_2_occupied", "slot_3_occupied", "slot_4_occupied", "slot_5_occupied", "cracked", "crafting");
    private static final Splitter PROPERTY_SPLITTER = Splitter.on(',');

    public ItemStackComponentizationFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    private static void fixItemStack(ItemStackComponentizationFix.ItemStackData itemStack, Dynamic<?> dynamic) {
        int i = itemStack.removeTag("HideFlags").asInt(0);

        itemStack.moveTagToComponent("Damage", "minecraft:damage", dynamic.createInt(0));
        itemStack.moveTagToComponent("RepairCost", "minecraft:repair_cost", dynamic.createInt(0));
        itemStack.moveTagToComponent("CustomModelData", "minecraft:custom_model_data");
        itemStack.removeTag("BlockStateTag").result().ifPresent((dynamic1) -> {
            itemStack.setComponent("minecraft:block_state", fixBlockStateTag(dynamic1));
        });
        itemStack.moveTagToComponent("EntityTag", "minecraft:entity_data");
        itemStack.fixSubTag("BlockEntityTag", false, (dynamic1) -> {
            String s = NamespacedSchema.ensureNamespaced(dynamic1.get("id").asString(""));

            dynamic1 = fixBlockEntityTag(itemStack, dynamic1, s);
            Dynamic<?> dynamic2 = dynamic1.remove("id");

            return dynamic2.equals(dynamic1.emptyMap()) ? dynamic2 : dynamic1;
        });
        itemStack.moveTagToComponent("BlockEntityTag", "minecraft:block_entity_data");
        if (itemStack.removeTag("Unbreakable").asBoolean(false)) {
            Dynamic<?> dynamic1 = dynamic.emptyMap();

            if ((i & 4) != 0) {
                dynamic1 = dynamic1.set("show_in_tooltip", dynamic.createBoolean(false));
            }

            itemStack.setComponent("minecraft:unbreakable", dynamic1);
        }

        fixEnchantments(itemStack, dynamic, "Enchantments", "minecraft:enchantments", (i & 1) != 0);
        if (itemStack.is("minecraft:enchanted_book")) {
            fixEnchantments(itemStack, dynamic, "StoredEnchantments", "minecraft:stored_enchantments", (i & 32) != 0);
        }

        itemStack.fixSubTag("display", false, (dynamic2) -> {
            return fixDisplay(itemStack, dynamic2, i);
        });
        fixAdventureModeChecks(itemStack, dynamic, i);
        fixAttributeModifiers(itemStack, dynamic, i);
        Optional<? extends Dynamic<?>> optional = itemStack.removeTag("Trim").result();

        if (optional.isPresent()) {
            Dynamic<?> dynamic2 = (Dynamic) optional.get();

            if ((i & 128) != 0) {
                dynamic2 = dynamic2.set("show_in_tooltip", dynamic2.createBoolean(false));
            }

            itemStack.setComponent("minecraft:trim", dynamic2);
        }

        if ((i & 32) != 0) {
            itemStack.setComponent("minecraft:hide_additional_tooltip", dynamic.emptyMap());
        }

        if (itemStack.is("minecraft:crossbow")) {
            itemStack.removeTag("Charged");
            itemStack.moveTagToComponent("ChargedProjectiles", "minecraft:charged_projectiles", dynamic.createList(Stream.empty()));
        }

        if (itemStack.is("minecraft:bundle")) {
            itemStack.moveTagToComponent("Items", "minecraft:bundle_contents", dynamic.createList(Stream.empty()));
        }

        if (itemStack.is("minecraft:filled_map")) {
            itemStack.moveTagToComponent("map", "minecraft:map_id");
            Map<? extends Dynamic<?>, ? extends Dynamic<?>> map = (Map) itemStack.removeTag("Decorations").asStream().map(ItemStackComponentizationFix::fixMapDecoration).collect(Collectors.toMap(Pair::getFirst, Pair::getSecond, (dynamic3, dynamic4) -> {
                return dynamic3;
            }));

            if (!map.isEmpty()) {
                itemStack.setComponent("minecraft:map_decorations", dynamic.createMap(map));
            }
        }

        if (itemStack.is(ItemStackComponentizationFix.POTION_HOLDER_IDS)) {
            fixPotionContents(itemStack, dynamic);
        }

        if (itemStack.is("minecraft:writable_book")) {
            fixWritableBook(itemStack, dynamic);
        }

        if (itemStack.is("minecraft:written_book")) {
            fixWrittenBook(itemStack, dynamic);
        }

        if (itemStack.is("minecraft:suspicious_stew")) {
            itemStack.moveTagToComponent("effects", "minecraft:suspicious_stew_effects");
        }

        if (itemStack.is("minecraft:debug_stick")) {
            itemStack.moveTagToComponent("DebugProperty", "minecraft:debug_stick_state");
        }

        if (itemStack.is(ItemStackComponentizationFix.BUCKETED_MOB_IDS)) {
            fixBucketedMobData(itemStack, dynamic);
        }

        if (itemStack.is("minecraft:goat_horn")) {
            itemStack.moveTagToComponent("instrument", "minecraft:instrument");
        }

        if (itemStack.is("minecraft:knowledge_book")) {
            itemStack.moveTagToComponent("Recipes", "minecraft:recipes");
        }

        if (itemStack.is("minecraft:compass")) {
            fixLodestoneTracker(itemStack, dynamic);
        }

        if (itemStack.is("minecraft:firework_rocket")) {
            fixFireworkRocket(itemStack);
        }

        if (itemStack.is("minecraft:firework_star")) {
            fixFireworkStar(itemStack);
        }

        if (itemStack.is("minecraft:player_head")) {
            itemStack.removeTag("SkullOwner").result().ifPresent((dynamic3) -> {
                itemStack.setComponent("minecraft:profile", fixProfile(dynamic3));
            });
        }

    }

    private static Dynamic<?> fixBlockStateTag(Dynamic<?> blockStateTag) {
        Optional optional = blockStateTag.asMapOpt().result().map((stream) -> {
            return (Map) stream.collect(Collectors.toMap(Pair::getFirst, (pair) -> {
                String s = ((Dynamic) pair.getFirst()).asString("");
                Dynamic<?> dynamic1 = (Dynamic) pair.getSecond();

                if (ItemStackComponentizationFix.BOOLEAN_BLOCK_STATE_PROPERTIES.contains(s)) {
                    Optional<Boolean> optional1 = dynamic1.asBoolean().result();

                    if (optional1.isPresent()) {
                        return dynamic1.createString(String.valueOf(optional1.get()));
                    }
                }

                Optional<Number> optional2 = dynamic1.asNumber().result();

                return optional2.isPresent() ? dynamic1.createString(((Number) optional2.get()).toString()) : dynamic1;
            }));
        });

        Objects.requireNonNull(blockStateTag);
        return (Dynamic) DataFixUtils.orElse(optional.map(blockStateTag::createMap), blockStateTag);
    }

    private static Dynamic<?> fixDisplay(ItemStackComponentizationFix.ItemStackData itemStack, Dynamic<?> display, int hideFlags) {
        display.get("Name").result().filter(LegacyComponentDataFixUtils::isStrictlyValidJson).ifPresent((dynamic1) -> {
            itemStack.setComponent("minecraft:custom_name", dynamic1);
        });
        OptionalDynamic<?> optionaldynamic = display.get("Lore");

        if (optionaldynamic.result().isPresent()) {
            itemStack.setComponent("minecraft:lore", display.createList(display.get("Lore").asStream().filter(LegacyComponentDataFixUtils::isStrictlyValidJson)));
        }

        Optional<Integer> optional = display.get("color").asNumber().result().map(Number::intValue);
        boolean flag = (hideFlags & 64) != 0;

        if (optional.isPresent() || flag) {
            Dynamic<?> dynamic1 = display.emptyMap().set("rgb", display.createInt((Integer) optional.orElse(10511680)));

            if (flag) {
                dynamic1 = dynamic1.set("show_in_tooltip", display.createBoolean(false));
            }

            itemStack.setComponent("minecraft:dyed_color", dynamic1);
        }

        Optional<String> optional1 = display.get("LocName").asString().result();

        if (optional1.isPresent()) {
            itemStack.setComponent("minecraft:item_name", LegacyComponentDataFixUtils.createTranslatableComponent(display.getOps(), (String) optional1.get()));
        }

        if (itemStack.is("minecraft:filled_map")) {
            itemStack.setComponent("minecraft:map_color", display.get("MapColor"));
            display = display.remove("MapColor");
        }

        return display.remove("Name").remove("Lore").remove("color").remove("LocName");
    }

    private static <T> Dynamic<T> fixBlockEntityTag(ItemStackComponentizationFix.ItemStackData itemStack, Dynamic<T> blockEntity, String id) {
        itemStack.setComponent("minecraft:lock", blockEntity.get("Lock"));
        blockEntity = blockEntity.remove("Lock");
        Optional<Dynamic<T>> optional = blockEntity.get("LootTable").result();

        if (optional.isPresent()) {
            Dynamic<T> dynamic1 = blockEntity.emptyMap().set("loot_table", (Dynamic) optional.get());
            long i = blockEntity.get("LootTableSeed").asLong(0L);

            if (i != 0L) {
                dynamic1 = dynamic1.set("seed", blockEntity.createLong(i));
            }

            itemStack.setComponent("minecraft:container_loot", dynamic1);
            blockEntity = blockEntity.remove("LootTable").remove("LootTableSeed");
        }

        Dynamic dynamic2;

        switch (id) {
            case "minecraft:skull":
                itemStack.setComponent("minecraft:note_block_sound", blockEntity.get("note_block_sound"));
                dynamic2 = blockEntity.remove("note_block_sound");
                break;
            case "minecraft:decorated_pot":
                itemStack.setComponent("minecraft:pot_decorations", blockEntity.get("sherds"));
                Optional<Dynamic<T>> optional1 = blockEntity.get("item").result();

                if (optional1.isPresent()) {
                    itemStack.setComponent("minecraft:container", blockEntity.createList(Stream.of(blockEntity.emptyMap().set("slot", blockEntity.createInt(0)).set("item", (Dynamic) optional1.get()))));
                }

                dynamic2 = blockEntity.remove("sherds").remove("item");
                break;
            case "minecraft:banner":
                itemStack.setComponent("minecraft:banner_patterns", blockEntity.get("patterns"));
                Optional<Number> optional2 = blockEntity.get("Base").asNumber().result();

                if (optional2.isPresent()) {
                    itemStack.setComponent("minecraft:base_color", blockEntity.createString(ExtraDataFixUtils.dyeColorIdToName(((Number) optional2.get()).intValue())));
                }

                dynamic2 = blockEntity.remove("patterns").remove("Base");
                break;
            case "minecraft:shulker_box":
            case "minecraft:chest":
            case "minecraft:trapped_chest":
            case "minecraft:furnace":
            case "minecraft:ender_chest":
            case "minecraft:dispenser":
            case "minecraft:dropper":
            case "minecraft:brewing_stand":
            case "minecraft:hopper":
            case "minecraft:barrel":
            case "minecraft:smoker":
            case "minecraft:blast_furnace":
            case "minecraft:campfire":
            case "minecraft:chiseled_bookshelf":
            case "minecraft:crafter":
                List<Dynamic<T>> list = blockEntity.get("Items").asList((dynamic3) -> {
                    return dynamic3.emptyMap().set("slot", dynamic3.createInt(dynamic3.get("Slot").asByte((byte) 0) & 255)).set("item", dynamic3.remove("Slot"));
                });

                if (!list.isEmpty()) {
                    itemStack.setComponent("minecraft:container", blockEntity.createList(list.stream()));
                }

                dynamic2 = blockEntity.remove("Items");
                break;
            case "minecraft:beehive":
                itemStack.setComponent("minecraft:bees", blockEntity.get("bees"));
                dynamic2 = blockEntity.remove("bees");
                break;
            default:
                dynamic2 = blockEntity;
        }

        return dynamic2;
    }

    private static void fixEnchantments(ItemStackComponentizationFix.ItemStackData itemStack, Dynamic<?> dynamic, String key, String componentType, boolean hideInTooltip) {
        OptionalDynamic<?> optionaldynamic = itemStack.removeTag(key);
        List<Pair<String, Integer>> list = optionaldynamic.asList(Function.identity()).stream().flatMap((dynamic1) -> {
            return parseEnchantment(dynamic1).stream();
        }).filter((pair) -> {
            return (Integer) pair.getSecond() > 0;
        }).toList();

        if (!list.isEmpty() || hideInTooltip) {
            Dynamic<?> dynamic1 = dynamic.emptyMap();
            Dynamic<?> dynamic2 = dynamic.emptyMap();

            for (Pair<String, Integer> pair : list) {
                dynamic2 = dynamic2.set((String) pair.getFirst(), dynamic.createInt((Integer) pair.getSecond()));
            }

            dynamic1 = dynamic1.set("levels", dynamic2);
            if (hideInTooltip) {
                dynamic1 = dynamic1.set("show_in_tooltip", dynamic.createBoolean(false));
            }

            itemStack.setComponent(componentType, dynamic1);
        }

        if (optionaldynamic.result().isPresent() && list.isEmpty()) {
            itemStack.setComponent("minecraft:enchantment_glint_override", dynamic.createBoolean(true));
        }

    }

    private static Optional<Pair<String, Integer>> parseEnchantment(Dynamic<?> entry) {
        return entry.get("id").asString().apply2stable((s, number) -> {
            return Pair.of(s, Mth.clamp(number.intValue(), 0, 255));
        }, entry.get("lvl").asNumber()).result();
    }

    private static void fixAdventureModeChecks(ItemStackComponentizationFix.ItemStackData itemStack, Dynamic<?> dynamic, int hideFlags) {
        fixBlockStatePredicates(itemStack, dynamic, "CanDestroy", "minecraft:can_break", (hideFlags & 8) != 0);
        fixBlockStatePredicates(itemStack, dynamic, "CanPlaceOn", "minecraft:can_place_on", (hideFlags & 16) != 0);
    }

    private static void fixBlockStatePredicates(ItemStackComponentizationFix.ItemStackData itemStack, Dynamic<?> dynamic, String tag, String componentId, boolean hideInTooltip) {
        Optional<? extends Dynamic<?>> optional = itemStack.removeTag(tag).result();

        if (!optional.isEmpty()) {
            Dynamic<?> dynamic1 = dynamic.emptyMap().set("predicates", dynamic.createList(((Dynamic) optional.get()).asStream().map((dynamic2) -> {
                return (Dynamic) DataFixUtils.orElse(dynamic2.asString().map((s2) -> {
                    return fixBlockStatePredicate(dynamic2, s2);
                }).result(), dynamic2);
            })));

            if (hideInTooltip) {
                dynamic1 = dynamic1.set("show_in_tooltip", dynamic.createBoolean(false));
            }

            itemStack.setComponent(componentId, dynamic1);
        }
    }

    private static Dynamic<?> fixBlockStatePredicate(Dynamic<?> dynamic, String string) {
        int i = string.indexOf(91);
        int j = string.indexOf(123);
        int k = string.length();

        if (i != -1) {
            k = i;
        }

        if (j != -1) {
            k = Math.min(k, j);
        }

        String s1 = string.substring(0, k);
        Dynamic<?> dynamic1 = dynamic.emptyMap().set("blocks", dynamic.createString(s1.trim()));
        int l = string.indexOf(93);

        if (i != -1 && l != -1) {
            Dynamic<?> dynamic2 = dynamic.emptyMap();

            for (String s2 : ItemStackComponentizationFix.PROPERTY_SPLITTER.split(string.substring(i + 1, l))) {
                int i1 = s2.indexOf(61);

                if (i1 != -1) {
                    String s3 = s2.substring(0, i1).trim();
                    String s4 = s2.substring(i1 + 1).trim();

                    dynamic2 = dynamic2.set(s3, dynamic.createString(s4));
                }
            }

            dynamic1 = dynamic1.set("state", dynamic2);
        }

        int j1 = string.indexOf(125);

        if (j != -1 && j1 != -1) {
            dynamic1 = dynamic1.set("nbt", dynamic.createString(string.substring(j, j1 + 1)));
        }

        return dynamic1;
    }

    private static void fixAttributeModifiers(ItemStackComponentizationFix.ItemStackData itemStack, Dynamic<?> dynamic, int hideFlags) {
        OptionalDynamic<?> optionaldynamic = itemStack.removeTag("AttributeModifiers");

        if (!optionaldynamic.result().isEmpty()) {
            boolean flag = (hideFlags & 2) != 0;
            List<? extends Dynamic<?>> list = optionaldynamic.asList(ItemStackComponentizationFix::fixAttributeModifier);
            Dynamic<?> dynamic1 = dynamic.emptyMap().set("modifiers", dynamic.createList(list.stream()));

            if (flag) {
                dynamic1 = dynamic1.set("show_in_tooltip", dynamic.createBoolean(false));
            }

            itemStack.setComponent("minecraft:attribute_modifiers", dynamic1);
        }
    }

    private static Dynamic<?> fixAttributeModifier(Dynamic<?> input) {
        Dynamic<?> dynamic1 = input.emptyMap().set("name", input.createString("")).set("amount", input.createDouble(0.0D)).set("operation", input.createString("add_value"));

        dynamic1 = Dynamic.copyField(input, "AttributeName", dynamic1, "type");
        dynamic1 = Dynamic.copyField(input, "Slot", dynamic1, "slot");
        dynamic1 = Dynamic.copyField(input, "UUID", dynamic1, "uuid");
        dynamic1 = Dynamic.copyField(input, "Name", dynamic1, "name");
        dynamic1 = Dynamic.copyField(input, "Amount", dynamic1, "amount");
        dynamic1 = Dynamic.copyAndFixField(input, "Operation", dynamic1, "operation", (dynamic2) -> {
            String s;

            switch (dynamic2.asInt(0)) {
                case 1:
                    s = "add_multiplied_base";
                    break;
                case 2:
                    s = "add_multiplied_total";
                    break;
                default:
                    s = "add_value";
            }

            return dynamic2.createString(s);
        });
        return dynamic1;
    }

    private static Pair<Dynamic<?>, Dynamic<?>> fixMapDecoration(Dynamic<?> decoration) {
        Dynamic<?> dynamic1 = (Dynamic) DataFixUtils.orElseGet(decoration.get("id").result(), () -> {
            return decoration.createString("");
        });
        Dynamic<?> dynamic2 = decoration.emptyMap().set("type", decoration.createString(fixMapDecorationType(decoration.get("type").asInt(0)))).set("x", decoration.createDouble(decoration.get("x").asDouble(0.0D))).set("z", decoration.createDouble(decoration.get("z").asDouble(0.0D))).set("rotation", decoration.createFloat((float) decoration.get("rot").asDouble(0.0D)));

        return Pair.of(dynamic1, dynamic2);
    }

    private static String fixMapDecorationType(int id) {
        String s;

        switch (id) {
            case 1:
                s = "frame";
                break;
            case 2:
                s = "red_marker";
                break;
            case 3:
                s = "blue_marker";
                break;
            case 4:
                s = "target_x";
                break;
            case 5:
                s = "target_point";
                break;
            case 6:
                s = "player_off_map";
                break;
            case 7:
                s = "player_off_limits";
                break;
            case 8:
                s = "mansion";
                break;
            case 9:
                s = "monument";
                break;
            case 10:
                s = "banner_white";
                break;
            case 11:
                s = "banner_orange";
                break;
            case 12:
                s = "banner_magenta";
                break;
            case 13:
                s = "banner_light_blue";
                break;
            case 14:
                s = "banner_yellow";
                break;
            case 15:
                s = "banner_lime";
                break;
            case 16:
                s = "banner_pink";
                break;
            case 17:
                s = "banner_gray";
                break;
            case 18:
                s = "banner_light_gray";
                break;
            case 19:
                s = "banner_cyan";
                break;
            case 20:
                s = "banner_purple";
                break;
            case 21:
                s = "banner_blue";
                break;
            case 22:
                s = "banner_brown";
                break;
            case 23:
                s = "banner_green";
                break;
            case 24:
                s = "banner_red";
                break;
            case 25:
                s = "banner_black";
                break;
            case 26:
                s = "red_x";
                break;
            case 27:
                s = "village_desert";
                break;
            case 28:
                s = "village_plains";
                break;
            case 29:
                s = "village_savanna";
                break;
            case 30:
                s = "village_snowy";
                break;
            case 31:
                s = "village_taiga";
                break;
            case 32:
                s = "jungle_temple";
                break;
            case 33:
                s = "swamp_hut";
                break;
            default:
                s = "player";
        }

        return s;
    }

    private static void fixPotionContents(ItemStackComponentizationFix.ItemStackData itemStack, Dynamic<?> dynamic) {
        Dynamic<?> dynamic1 = dynamic.emptyMap();
        Optional<String> optional = itemStack.removeTag("Potion").asString().result().filter((s) -> {
            return !s.equals("minecraft:empty");
        });

        if (optional.isPresent()) {
            dynamic1 = dynamic1.set("potion", dynamic.createString((String) optional.get()));
        }

        dynamic1 = itemStack.moveTagInto("CustomPotionColor", dynamic1, "custom_color");
        dynamic1 = itemStack.moveTagInto("custom_potion_effects", dynamic1, "custom_effects");
        if (!dynamic1.equals(dynamic.emptyMap())) {
            itemStack.setComponent("minecraft:potion_contents", dynamic1);
        }

    }

    private static void fixWritableBook(ItemStackComponentizationFix.ItemStackData itemStack, Dynamic<?> dynamic) {
        Dynamic<?> dynamic1 = fixBookPages(itemStack, dynamic);

        if (dynamic1 != null) {
            itemStack.setComponent("minecraft:writable_book_content", dynamic.emptyMap().set("pages", dynamic1));
        }

    }

    private static void fixWrittenBook(ItemStackComponentizationFix.ItemStackData itemStack, Dynamic<?> dynamic) {
        Dynamic<?> dynamic1 = fixBookPages(itemStack, dynamic);
        String s = itemStack.removeTag("title").asString("");
        Optional<String> optional = itemStack.removeTag("filtered_title").asString().result();
        Dynamic<?> dynamic2 = dynamic.emptyMap();

        dynamic2 = dynamic2.set("title", createFilteredText(dynamic, s, optional));
        dynamic2 = itemStack.moveTagInto("author", dynamic2, "author");
        dynamic2 = itemStack.moveTagInto("resolved", dynamic2, "resolved");
        dynamic2 = itemStack.moveTagInto("generation", dynamic2, "generation");
        if (dynamic1 != null) {
            dynamic2 = dynamic2.set("pages", dynamic1);
        }

        itemStack.setComponent("minecraft:written_book_content", dynamic2);
    }

    private static @Nullable Dynamic<?> fixBookPages(ItemStackComponentizationFix.ItemStackData itemStack, Dynamic<?> dynamic) {
        List<String> list = itemStack.removeTag("pages").asList((dynamic1) -> {
            return dynamic1.asString("");
        });
        Map<String, String> map = itemStack.removeTag("filtered_pages").asMap((dynamic1) -> {
            return dynamic1.asString("0");
        }, (dynamic1) -> {
            return dynamic1.asString("");
        });

        if (list.isEmpty()) {
            return null;
        } else {
            List<Dynamic<?>> list1 = new ArrayList(list.size());

            for (int i = 0; i < list.size(); ++i) {
                String s = (String) list.get(i);
                String s1 = (String) map.get(String.valueOf(i));

                list1.add(createFilteredText(dynamic, s, Optional.ofNullable(s1)));
            }

            return dynamic.createList(list1.stream());
        }
    }

    private static Dynamic<?> createFilteredText(Dynamic<?> dynamic, String text, Optional<String> filtered) {
        Dynamic<?> dynamic1 = dynamic.emptyMap().set("raw", dynamic.createString(text));

        if (filtered.isPresent()) {
            dynamic1 = dynamic1.set("filtered", dynamic.createString((String) filtered.get()));
        }

        return dynamic1;
    }

    private static void fixBucketedMobData(ItemStackComponentizationFix.ItemStackData itemStack, Dynamic<?> dynamic) {
        Dynamic<?> dynamic1 = dynamic.emptyMap();

        for (String s : ItemStackComponentizationFix.BUCKETED_MOB_TAGS) {
            dynamic1 = itemStack.moveTagInto(s, dynamic1, s);
        }

        if (!dynamic1.equals(dynamic.emptyMap())) {
            itemStack.setComponent("minecraft:bucket_entity_data", dynamic1);
        }

    }

    private static void fixLodestoneTracker(ItemStackComponentizationFix.ItemStackData itemStack, Dynamic<?> dynamic) {
        Optional<? extends Dynamic<?>> optional = itemStack.removeTag("LodestonePos").result();
        Optional<? extends Dynamic<?>> optional1 = itemStack.removeTag("LodestoneDimension").result();

        if (!optional.isEmpty() || !optional1.isEmpty()) {
            boolean flag = itemStack.removeTag("LodestoneTracked").asBoolean(true);
            Dynamic<?> dynamic1 = dynamic.emptyMap();

            if (optional.isPresent() && optional1.isPresent()) {
                dynamic1 = dynamic1.set("target", dynamic.emptyMap().set("pos", (Dynamic) optional.get()).set("dimension", (Dynamic) optional1.get()));
            }

            if (!flag) {
                dynamic1 = dynamic1.set("tracked", dynamic.createBoolean(false));
            }

            itemStack.setComponent("minecraft:lodestone_tracker", dynamic1);
        }
    }

    private static void fixFireworkStar(ItemStackComponentizationFix.ItemStackData itemStack) {
        itemStack.fixSubTag("Explosion", true, (dynamic) -> {
            itemStack.setComponent("minecraft:firework_explosion", fixFireworkExplosion(dynamic));
            return dynamic.remove("Type").remove("Colors").remove("FadeColors").remove("Trail").remove("Flicker");
        });
    }

    private static void fixFireworkRocket(ItemStackComponentizationFix.ItemStackData itemStack) {
        itemStack.fixSubTag("Fireworks", true, (dynamic) -> {
            Stream<? extends Dynamic<?>> stream = dynamic.get("Explosions").asStream().map(ItemStackComponentizationFix::fixFireworkExplosion);
            int i = dynamic.get("Flight").asInt(0);

            itemStack.setComponent("minecraft:fireworks", dynamic.emptyMap().set("explosions", dynamic.createList(stream)).set("flight_duration", dynamic.createByte((byte) i)));
            return dynamic.remove("Explosions").remove("Flight");
        });
    }

    private static Dynamic<?> fixFireworkExplosion(Dynamic<?> explosion) {
        String s;

        switch (explosion.get("Type").asInt(0)) {
            case 1:
                s = "large_ball";
                break;
            case 2:
                s = "star";
                break;
            case 3:
                s = "creeper";
                break;
            case 4:
                s = "burst";
                break;
            default:
                s = "small_ball";
        }

        explosion = explosion.set("shape", explosion.createString(s)).remove("Type");
        explosion = explosion.renameField("Colors", "colors");
        explosion = explosion.renameField("FadeColors", "fade_colors");
        explosion = explosion.renameField("Trail", "has_trail");
        explosion = explosion.renameField("Flicker", "has_twinkle");
        return explosion;
    }

    public static Dynamic<?> fixProfile(Dynamic<?> dynamic) {
        Optional<String> optional = dynamic.asString().result();

        if (optional.isPresent()) {
            return isValidPlayerName((String) optional.get()) ? dynamic.emptyMap().set("name", dynamic.createString((String) optional.get())) : dynamic.emptyMap();
        } else {
            String s = dynamic.get("Name").asString("");
            Optional<? extends Dynamic<?>> optional1 = dynamic.get("Id").result();
            Dynamic<?> dynamic1 = fixProfileProperties(dynamic.get("Properties"));
            Dynamic<?> dynamic2 = dynamic.emptyMap();

            if (isValidPlayerName(s)) {
                dynamic2 = dynamic2.set("name", dynamic.createString(s));
            }

            if (optional1.isPresent()) {
                dynamic2 = dynamic2.set("id", (Dynamic) optional1.get());
            }

            if (dynamic1 != null) {
                dynamic2 = dynamic2.set("properties", dynamic1);
            }

            return dynamic2;
        }
    }

    private static boolean isValidPlayerName(String name) {
        return name.length() > 16 ? false : name.chars().filter((i) -> {
            return i <= 32 || i >= 127;
        }).findAny().isEmpty();
    }

    private static @Nullable Dynamic<?> fixProfileProperties(OptionalDynamic<?> dynamic) {
        Map<String, List<Pair<String, Optional<String>>>> map = dynamic.asMap((dynamic1) -> {
            return dynamic1.asString("");
        }, (dynamic1) -> {
            return dynamic1.asList((dynamic2) -> {
                String s = dynamic2.get("Value").asString("");
                Optional<String> optional = dynamic2.get("Signature").asString().result();

                return Pair.of(s, optional);
            });
        });

        return map.isEmpty() ? null : dynamic.createList(map.entrySet().stream().flatMap((entry) -> {
            return ((List) entry.getValue()).stream().map((pair) -> {
                Dynamic<?> dynamic1 = dynamic.emptyMap().set("name", dynamic.createString((String) entry.getKey())).set("value", dynamic.createString((String) pair.getFirst()));
                Optional<String> optional = (Optional) pair.getSecond();

                return optional.isPresent() ? dynamic1.set("signature", dynamic.createString((String) optional.get())) : dynamic1;
            });
        }));
    }

    protected TypeRewriteRule makeRule() {
        return this.writeFixAndRead("ItemStack componentization", this.getInputSchema().getType(References.ITEM_STACK), this.getOutputSchema().getType(References.ITEM_STACK), (dynamic) -> {
            Optional<? extends Dynamic<?>> optional = ItemStackComponentizationFix.ItemStackData.read(dynamic).map((itemstackcomponentizationfix_itemstackdata) -> {
                fixItemStack(itemstackcomponentizationfix_itemstackdata, itemstackcomponentizationfix_itemstackdata.tag);
                return itemstackcomponentizationfix_itemstackdata.write();
            });

            return (Dynamic) DataFixUtils.orElse(optional, dynamic);
        });
    }

    private static class ItemStackData {

        private final String item;
        private final int count;
        private Dynamic<?> components;
        private final Dynamic<?> remainder;
        private Dynamic<?> tag;

        private ItemStackData(String item, int count, Dynamic<?> remainder) {
            this.item = NamespacedSchema.ensureNamespaced(item);
            this.count = count;
            this.components = remainder.emptyMap();
            this.tag = remainder.get("tag").orElseEmptyMap();
            this.remainder = remainder.remove("tag");
        }

        public static Optional<ItemStackComponentizationFix.ItemStackData> read(Dynamic<?> dynamic) {
            return dynamic.get("id").asString().apply2stable((s, number) -> {
                return new ItemStackComponentizationFix.ItemStackData(s, number.intValue(), dynamic.remove("id").remove("Count"));
            }, dynamic.get("Count").asNumber()).result();
        }

        public OptionalDynamic<?> removeTag(String key) {
            OptionalDynamic<?> optionaldynamic = this.tag.get(key);

            this.tag = this.tag.remove(key);
            return optionaldynamic;
        }

        public void setComponent(String type, Dynamic<?> value) {
            this.components = this.components.set(type, value);
        }

        public void setComponent(String type, OptionalDynamic<?> optionalValue) {
            optionalValue.result().ifPresent((dynamic) -> {
                this.components = this.components.set(type, dynamic);
            });
        }

        public Dynamic<?> moveTagInto(String fromKey, Dynamic<?> target, String toKey) {
            Optional<? extends Dynamic<?>> optional = this.removeTag(fromKey).result();

            return optional.isPresent() ? target.set(toKey, (Dynamic) optional.get()) : target;
        }

        public void moveTagToComponent(String key, String type, Dynamic<?> defaultValue) {
            Optional<? extends Dynamic<?>> optional = this.removeTag(key).result();

            if (optional.isPresent() && !((Dynamic) optional.get()).equals(defaultValue)) {
                this.setComponent(type, (Dynamic) optional.get());
            }

        }

        public void moveTagToComponent(String key, String type) {
            this.removeTag(key).result().ifPresent((dynamic) -> {
                this.setComponent(type, dynamic);
            });
        }

        public void fixSubTag(String key, boolean dontFixWhenFieldIsMissing, UnaryOperator<Dynamic<?>> function) {
            OptionalDynamic<?> optionaldynamic = this.tag.get(key);

            if (!dontFixWhenFieldIsMissing || !optionaldynamic.result().isEmpty()) {
                Dynamic<?> dynamic = optionaldynamic.orElseEmptyMap();

                dynamic = (Dynamic) function.apply(dynamic);
                if (dynamic.equals(dynamic.emptyMap())) {
                    this.tag = this.tag.remove(key);
                } else {
                    this.tag = this.tag.set(key, dynamic);
                }

            }
        }

        public Dynamic<?> write() {
            Dynamic<?> dynamic = this.tag.emptyMap().set("id", this.tag.createString(this.item)).set("count", this.tag.createInt(this.count));

            if (!this.tag.equals(this.tag.emptyMap())) {
                this.components = this.components.set("minecraft:custom_data", this.tag);
            }

            if (!this.components.equals(this.tag.emptyMap())) {
                dynamic = dynamic.set("components", this.components);
            }

            return mergeRemainder(dynamic, this.remainder);
        }

        private static <T> Dynamic<T> mergeRemainder(Dynamic<T> itemStack, Dynamic<?> remainder) {
            DynamicOps<T> dynamicops = itemStack.getOps();

            return (Dynamic) dynamicops.getMap(itemStack.getValue()).flatMap((maplike) -> {
                return dynamicops.mergeToMap(remainder.convert(dynamicops).getValue(), maplike);
            }).map((object) -> {
                return new Dynamic(dynamicops, object);
            }).result().orElse(itemStack);
        }

        public boolean is(String id) {
            return this.item.equals(id);
        }

        public boolean is(Set<String> ids) {
            return ids.contains(this.item);
        }

        public boolean hasComponent(String id) {
            return this.components.get(id).result().isPresent();
        }
    }
}
