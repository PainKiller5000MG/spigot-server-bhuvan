package net.minecraft.world.inventory;

public interface ContainerData {

    int get(int dataId);

    void set(int dataId, int value);

    int getCount();
}
