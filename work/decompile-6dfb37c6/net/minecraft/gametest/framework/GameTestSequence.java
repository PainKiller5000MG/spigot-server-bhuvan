package net.minecraft.gametest.framework;

import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

public class GameTestSequence {

    private final GameTestInfo parent;
    private final List<GameTestEvent> events = Lists.newArrayList();
    private int lastTick;

    GameTestSequence(GameTestInfo parent) {
        this.parent = parent;
        this.lastTick = parent.getTick();
    }

    public GameTestSequence thenWaitUntil(Runnable assertion) {
        this.events.add(GameTestEvent.create(assertion));
        return this;
    }

    public GameTestSequence thenWaitUntil(long expectedDelay, Runnable assertion) {
        this.events.add(GameTestEvent.create(expectedDelay, assertion));
        return this;
    }

    public GameTestSequence thenIdle(int delta) {
        return this.thenExecuteAfter(delta, () -> {
        });
    }

    public GameTestSequence thenExecute(Runnable assertion) {
        this.events.add(GameTestEvent.create(() -> {
            this.executeWithoutFail(assertion);
        }));
        return this;
    }

    public GameTestSequence thenExecuteAfter(int delta, Runnable after) {
        this.events.add(GameTestEvent.create(() -> {
            if (this.parent.getTick() < this.lastTick + delta) {
                throw new GameTestAssertException(Component.translatable("test.error.sequence.not_completed"), this.parent.getTick());
            } else {
                this.executeWithoutFail(after);
            }
        }));
        return this;
    }

    public GameTestSequence thenExecuteFor(int delta, Runnable check) {
        this.events.add(GameTestEvent.create(() -> {
            if (this.parent.getTick() < this.lastTick + delta) {
                this.executeWithoutFail(check);
                throw new GameTestAssertException(Component.translatable("test.error.sequence.not_completed"), this.parent.getTick());
            }
        }));
        return this;
    }

    public void thenSucceed() {
        List list = this.events;
        GameTestInfo gametestinfo = this.parent;

        Objects.requireNonNull(this.parent);
        list.add(GameTestEvent.create(gametestinfo::succeed));
    }

    public void thenFail(Supplier<GameTestException> e) {
        this.events.add(GameTestEvent.create(() -> {
            this.parent.fail((GameTestException) e.get());
        }));
    }

    public GameTestSequence.Condition thenTrigger() {
        GameTestSequence.Condition gametestsequence_condition = new GameTestSequence.Condition();

        this.events.add(GameTestEvent.create(() -> {
            gametestsequence_condition.trigger(this.parent.getTick());
        }));
        return gametestsequence_condition;
    }

    public void tickAndContinue(int tick) {
        try {
            this.tick(tick);
        } catch (GameTestAssertException gametestassertexception) {
            ;
        }

    }

    public void tickAndFailIfNotComplete(int tick) {
        try {
            this.tick(tick);
        } catch (GameTestAssertException gametestassertexception) {
            this.parent.fail((GameTestException) gametestassertexception);
        }

    }

    private void executeWithoutFail(Runnable assertion) {
        try {
            assertion.run();
        } catch (GameTestAssertException gametestassertexception) {
            this.parent.fail((GameTestException) gametestassertexception);
        }

    }

    private void tick(int tick) {
        Iterator<GameTestEvent> iterator = this.events.iterator();

        while (iterator.hasNext()) {
            GameTestEvent gametestevent = (GameTestEvent) iterator.next();

            gametestevent.assertion.run();
            iterator.remove();
            int j = tick - this.lastTick;
            int k = this.lastTick;

            this.lastTick = tick;
            if (gametestevent.expectedDelay != null && gametestevent.expectedDelay != (long) j) {
                this.parent.fail((GameTestException) (new GameTestAssertException(Component.translatable("test.error.sequence.invalid_tick", (long) k + gametestevent.expectedDelay), tick)));
                break;
            }
        }

    }

    public class Condition {

        private static final int NOT_TRIGGERED = -1;
        private int triggerTime = -1;

        public Condition() {}

        void trigger(int time) {
            if (this.triggerTime != -1) {
                throw new IllegalStateException("Condition already triggered at " + this.triggerTime);
            } else {
                this.triggerTime = time;
            }
        }

        public void assertTriggeredThisTick() {
            int i = GameTestSequence.this.parent.getTick();

            if (this.triggerTime != i) {
                if (this.triggerTime == -1) {
                    throw new GameTestAssertException(Component.translatable("test.error.sequence.condition_not_triggered"), i);
                } else {
                    throw new GameTestAssertException(Component.translatable("test.error.sequence.condition_already_triggered", this.triggerTime), i);
                }
            }
        }
    }
}
