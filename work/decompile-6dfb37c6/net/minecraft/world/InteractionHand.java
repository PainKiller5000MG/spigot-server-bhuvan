package net.minecraft.world;

import net.minecraft.world.entity.EquipmentSlot;

public enum InteractionHand {

    MAIN_HAND, OFF_HAND;

    private InteractionHand() {}

    public EquipmentSlot asEquipmentSlot() {
        return this == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }
}
