package net.minecraft.world.level.block.state.properties;

import java.util.List;
import java.util.Optional;

public final class BooleanProperty extends Property<Boolean> {

    private static final List<Boolean> VALUES = List.of(true, false);
    private static final int TRUE_INDEX = 0;
    private static final int FALSE_INDEX = 1;

    private BooleanProperty(String name) {
        super(name, Boolean.class);
    }

    @Override
    public List<Boolean> getPossibleValues() {
        return BooleanProperty.VALUES;
    }

    public static BooleanProperty create(String name) {
        return new BooleanProperty(name);
    }

    @Override
    public Optional<Boolean> getValue(String name) {
        Optional optional;

        switch (name) {
            case "true":
                optional = Optional.of(true);
                break;
            case "false":
                optional = Optional.of(false);
                break;
            default:
                optional = Optional.empty();
        }

        return optional;
    }

    public String getName(Boolean value) {
        return value.toString();
    }

    public int getInternalIndex(Boolean value) {
        return value ? 0 : 1;
    }
}
