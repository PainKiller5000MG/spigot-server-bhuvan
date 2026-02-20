package net.minecraft.world.level.block.state.predicate;

import com.google.common.collect.Maps;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

public class BlockStatePredicate implements Predicate<BlockState> {

    public static final Predicate<BlockState> ANY = (blockstate) -> {
        return true;
    };
    private final StateDefinition<Block, BlockState> definition;
    private final Map<Property<?>, Predicate<Object>> properties = Maps.newHashMap();

    private BlockStatePredicate(StateDefinition<Block, BlockState> definition) {
        this.definition = definition;
    }

    public static BlockStatePredicate forBlock(Block block) {
        return new BlockStatePredicate(block.getStateDefinition());
    }

    public boolean test(@Nullable BlockState input) {
        if (input != null && input.getBlock().equals(this.definition.getOwner())) {
            if (this.properties.isEmpty()) {
                return true;
            } else {
                for (Map.Entry<Property<?>, Predicate<Object>> map_entry : this.properties.entrySet()) {
                    if (!this.applies(input, (Property) map_entry.getKey(), (Predicate) map_entry.getValue())) {
                        return false;
                    }
                }

                return true;
            }
        } else {
            return false;
        }
    }

    protected <T extends Comparable<T>> boolean applies(BlockState input, Property<T> key, Predicate<Object> predicate) {
        T t0 = input.getValue(key);

        return predicate.test(t0);
    }

    public <V extends Comparable<V>> BlockStatePredicate where(Property<V> property, Predicate<Object> predicate) {
        if (!this.definition.getProperties().contains(property)) {
            String s = String.valueOf(this.definition);

            throw new IllegalArgumentException(s + " cannot support property " + String.valueOf(property));
        } else {
            this.properties.put(property, predicate);
            return this;
        }
    }
}
