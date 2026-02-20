package net.minecraft.gametest.framework;

import org.jspecify.annotations.Nullable;

class GameTestEvent {

    public final @Nullable Long expectedDelay;
    public final Runnable assertion;

    private GameTestEvent(@Nullable Long expectedDelay, Runnable assertion) {
        this.expectedDelay = expectedDelay;
        this.assertion = assertion;
    }

    static GameTestEvent create(Runnable runnable) {
        return new GameTestEvent((Long) null, runnable);
    }

    static GameTestEvent create(long expectedTick, Runnable runnable) {
        return new GameTestEvent(expectedTick, runnable);
    }
}
