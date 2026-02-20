package net.minecraft.world.level.block.state.properties;

import net.minecraft.util.StringRepresentable;

public enum ChestType implements StringRepresentable {

    SINGLE("single"), LEFT("left"), RIGHT("right");

    private final String name;

    private ChestType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public ChestType getOpposite() {
        ChestType chesttype;

        switch (this.ordinal()) {
            case 0:
                chesttype = ChestType.SINGLE;
                break;
            case 1:
                chesttype = ChestType.RIGHT;
                break;
            case 2:
                chesttype = ChestType.LEFT;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return chesttype;
    }
}
