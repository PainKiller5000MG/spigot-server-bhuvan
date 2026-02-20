package net.minecraft.world.entity;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

public enum EquipmentSlotGroup implements StringRepresentable, Iterable<EquipmentSlot> {

    ANY(0, "any", (equipmentslot) -> {
        return true;
    }), MAINHAND(1, "mainhand", EquipmentSlot.MAINHAND), OFFHAND(2, "offhand", EquipmentSlot.OFFHAND), HAND(3, "hand", (equipmentslot) -> {
        return equipmentslot.getType() == EquipmentSlot.Type.HAND;
    }), FEET(4, "feet", EquipmentSlot.FEET), LEGS(5, "legs", EquipmentSlot.LEGS), CHEST(6, "chest", EquipmentSlot.CHEST), HEAD(7, "head", EquipmentSlot.HEAD), ARMOR(8, "armor", EquipmentSlot::isArmor), BODY(9, "body", EquipmentSlot.BODY), SADDLE(10, "saddle", EquipmentSlot.SADDLE);

    public static final IntFunction<EquipmentSlotGroup> BY_ID = ByIdMap.<EquipmentSlotGroup>continuous((equipmentslotgroup) -> {
        return equipmentslotgroup.id;
    }, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final Codec<EquipmentSlotGroup> CODEC = StringRepresentable.<EquipmentSlotGroup>fromEnum(EquipmentSlotGroup::values);
    public static final StreamCodec<ByteBuf, EquipmentSlotGroup> STREAM_CODEC = ByteBufCodecs.idMapper(EquipmentSlotGroup.BY_ID, (equipmentslotgroup) -> {
        return equipmentslotgroup.id;
    });
    private final int id;
    private final String key;
    private final Predicate<EquipmentSlot> predicate;
    private final List<EquipmentSlot> slots;

    private EquipmentSlotGroup(int id, String key, Predicate<EquipmentSlot> predicate) {
        this.id = id;
        this.key = key;
        this.predicate = predicate;
        this.slots = EquipmentSlot.VALUES.stream().filter(predicate).toList();
    }

    private EquipmentSlotGroup(int id, String key, EquipmentSlot slot) {
        this(id, key, (equipmentslot1) -> {
            return equipmentslot1 == slot;
        });
    }

    public static EquipmentSlotGroup bySlot(EquipmentSlot slot) {
        EquipmentSlotGroup equipmentslotgroup;

        switch (slot) {
            case MAINHAND:
                equipmentslotgroup = EquipmentSlotGroup.MAINHAND;
                break;
            case OFFHAND:
                equipmentslotgroup = EquipmentSlotGroup.OFFHAND;
                break;
            case FEET:
                equipmentslotgroup = EquipmentSlotGroup.FEET;
                break;
            case LEGS:
                equipmentslotgroup = EquipmentSlotGroup.LEGS;
                break;
            case CHEST:
                equipmentslotgroup = EquipmentSlotGroup.CHEST;
                break;
            case HEAD:
                equipmentslotgroup = EquipmentSlotGroup.HEAD;
                break;
            case BODY:
                equipmentslotgroup = EquipmentSlotGroup.BODY;
                break;
            case SADDLE:
                equipmentslotgroup = EquipmentSlotGroup.SADDLE;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return equipmentslotgroup;
    }

    @Override
    public String getSerializedName() {
        return this.key;
    }

    public boolean test(EquipmentSlot slot) {
        return this.predicate.test(slot);
    }

    public List<EquipmentSlot> slots() {
        return this.slots;
    }

    public Iterator<EquipmentSlot> iterator() {
        return this.slots.iterator();
    }
}
