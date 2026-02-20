package net.minecraft.world.inventory;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EquipmentSlot;
import org.jspecify.annotations.Nullable;

public class SlotRanges {

    private static final List<SlotRange> SLOTS = (List) Util.make(new ArrayList(), (arraylist) -> {
        addSingleSlot(arraylist, "contents", 0);
        addSlotRange(arraylist, "container.", 0, 54);
        addSlotRange(arraylist, "hotbar.", 0, 9);
        addSlotRange(arraylist, "inventory.", 9, 27);
        addSlotRange(arraylist, "enderchest.", 200, 27);
        addSlotRange(arraylist, "villager.", 300, 8);
        addSlotRange(arraylist, "horse.", 500, 15);
        int i = EquipmentSlot.MAINHAND.getIndex(98);
        int j = EquipmentSlot.OFFHAND.getIndex(98);

        addSingleSlot(arraylist, "weapon", i);
        addSingleSlot(arraylist, "weapon.mainhand", i);
        addSingleSlot(arraylist, "weapon.offhand", j);
        addSlots(arraylist, "weapon.*", i, j);
        i = EquipmentSlot.HEAD.getIndex(100);
        j = EquipmentSlot.CHEST.getIndex(100);
        int k = EquipmentSlot.LEGS.getIndex(100);
        int l = EquipmentSlot.FEET.getIndex(100);
        int i1 = EquipmentSlot.BODY.getIndex(105);

        addSingleSlot(arraylist, "armor.head", i);
        addSingleSlot(arraylist, "armor.chest", j);
        addSingleSlot(arraylist, "armor.legs", k);
        addSingleSlot(arraylist, "armor.feet", l);
        addSingleSlot(arraylist, "armor.body", i1);
        addSlots(arraylist, "armor.*", i, j, k, l, i1);
        addSingleSlot(arraylist, "saddle", EquipmentSlot.SADDLE.getIndex(106));
        addSingleSlot(arraylist, "horse.chest", 499);
        addSingleSlot(arraylist, "player.cursor", 499);
        addSlotRange(arraylist, "player.crafting.", 500, 4);
    });
    public static final Codec<SlotRange> CODEC = StringRepresentable.<SlotRange>fromValues(() -> {
        return (SlotRange[]) SlotRanges.SLOTS.toArray((i) -> {
            return new SlotRange[i];
        });
    });
    private static final Function<String, @Nullable SlotRange> NAME_LOOKUP = StringRepresentable.createNameLookup((SlotRange[]) SlotRanges.SLOTS.toArray((i) -> {
        return new SlotRange[i];
    }));

    public SlotRanges() {}

    private static SlotRange create(String name, int id) {
        return SlotRange.of(name, IntLists.singleton(id));
    }

    private static SlotRange create(String name, IntList ids) {
        return SlotRange.of(name, IntLists.unmodifiable(ids));
    }

    private static SlotRange create(String name, int... ids) {
        return SlotRange.of(name, IntList.of(ids));
    }

    private static void addSingleSlot(List<SlotRange> output, String name, int id) {
        output.add(create(name, id));
    }

    private static void addSlotRange(List<SlotRange> output, String prefix, int offset, int size) {
        IntList intlist = new IntArrayList(size);

        for (int k = 0; k < size; ++k) {
            int l = offset + k;

            output.add(create(prefix + k, l));
            intlist.add(l);
        }

        output.add(create(prefix + "*", intlist));
    }

    private static void addSlots(List<SlotRange> output, String name, int... values) {
        output.add(create(name, values));
    }

    public static @Nullable SlotRange nameToIds(String name) {
        return (SlotRange) SlotRanges.NAME_LOOKUP.apply(name);
    }

    public static Stream<String> allNames() {
        return SlotRanges.SLOTS.stream().map(StringRepresentable::getSerializedName);
    }

    public static Stream<String> singleSlotNames() {
        return SlotRanges.SLOTS.stream().filter((slotrange) -> {
            return slotrange.size() == 1;
        }).map(StringRepresentable::getSerializedName);
    }
}
