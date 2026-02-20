package net.minecraft.world.level.storage.loot.entries;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.world.level.storage.loot.LootContext;

@FunctionalInterface
interface ComposableEntryContainer {

    ComposableEntryContainer ALWAYS_FALSE = (lootcontext, consumer) -> {
        return false;
    };
    ComposableEntryContainer ALWAYS_TRUE = (lootcontext, consumer) -> {
        return true;
    };

    boolean expand(LootContext context, Consumer<LootPoolEntry> output);

    default ComposableEntryContainer and(ComposableEntryContainer other) {
        Objects.requireNonNull(other);
        return (lootcontext, consumer) -> {
            return this.expand(lootcontext, consumer) && other.expand(lootcontext, consumer);
        };
    }

    default ComposableEntryContainer or(ComposableEntryContainer other) {
        Objects.requireNonNull(other);
        return (lootcontext, consumer) -> {
            return this.expand(lootcontext, consumer) || other.expand(lootcontext, consumer);
        };
    }
}
