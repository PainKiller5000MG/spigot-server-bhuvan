package net.minecraft.world.entity.ai.behavior.declarative;

import com.mojang.datafixers.kinds.App;
import com.mojang.datafixers.kinds.Applicative;
import com.mojang.datafixers.kinds.Const;
import com.mojang.datafixers.kinds.IdF;
import com.mojang.datafixers.kinds.K1;
import com.mojang.datafixers.kinds.OptionalBox;
import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Function4;
import com.mojang.datafixers.util.Unit;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.jspecify.annotations.Nullable;

public class BehaviorBuilder<E extends LivingEntity, M> implements App<BehaviorBuilder.Mu<E>, M> {

    private final BehaviorBuilder.TriggerWithResult<E, M> trigger;

    public static <E extends LivingEntity, M> BehaviorBuilder<E, M> unbox(App<BehaviorBuilder.Mu<E>, M> box) {
        return (BehaviorBuilder) box;
    }

    public static <E extends LivingEntity> BehaviorBuilder.Instance<E> instance() {
        return new BehaviorBuilder.Instance<E>();
    }

    public static <E extends LivingEntity> OneShot<E> create(Function<BehaviorBuilder.Instance<E>, ? extends App<BehaviorBuilder.Mu<E>, Trigger<E>>> builder) {
        final BehaviorBuilder.TriggerWithResult<E, Trigger<E>> behaviorbuilder_triggerwithresult = get((App) builder.apply(instance()));

        return new OneShot<E>() {
            @Override
            public boolean trigger(ServerLevel level, E body, long timestamp) {
                Trigger<E> trigger = behaviorbuilder_triggerwithresult.tryTrigger(level, body, timestamp);

                return trigger == null ? false : trigger.trigger(level, body, timestamp);
            }

            @Override
            public String debugString() {
                return "OneShot[" + behaviorbuilder_triggerwithresult.debugString() + "]";
            }

            public String toString() {
                return this.debugString();
            }
        };
    }

    public static <E extends LivingEntity> OneShot<E> sequence(Trigger<? super E> first, Trigger<? super E> second) {
        return create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.ifTriggered(first)).apply(behaviorbuilder_instance, (unit) -> {
                Objects.requireNonNull(second);
                return second::trigger;
            });
        });
    }

    public static <E extends LivingEntity> OneShot<E> triggerIf(Predicate<E> predicate, OneShot<? super E> behavior) {
        return sequence(triggerIf(predicate), behavior);
    }

    public static <E extends LivingEntity> OneShot<E> triggerIf(Predicate<E> predicate) {
        return create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.point((Trigger) (serverlevel, livingentity, i) -> {
                return predicate.test(livingentity);
            });
        });
    }

    public static <E extends LivingEntity> OneShot<E> triggerIf(BiPredicate<ServerLevel, E> predicate) {
        return create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.point((Trigger) (serverlevel, livingentity, i) -> {
                return predicate.test(serverlevel, livingentity);
            });
        });
    }

    private static <E extends LivingEntity, M> BehaviorBuilder.TriggerWithResult<E, M> get(App<BehaviorBuilder.Mu<E>, M> box) {
        return unbox(box).trigger;
    }

    private BehaviorBuilder(BehaviorBuilder.TriggerWithResult<E, M> trigger) {
        this.trigger = trigger;
    }

    private static <E extends LivingEntity, M> BehaviorBuilder<E, M> create(BehaviorBuilder.TriggerWithResult<E, M> instanceFactory) {
        return new BehaviorBuilder<E, M>(instanceFactory);
    }

    public static final class Mu<E extends LivingEntity> implements K1 {

        public Mu() {}
    }

    private static final class PureMemory<E extends LivingEntity, F extends K1, Value> extends BehaviorBuilder<E, MemoryAccessor<F, Value>> {

        private PureMemory(final MemoryCondition<F, Value> condition) {
            super(new BehaviorBuilder.TriggerWithResult<E, MemoryAccessor<F, Value>>() {
                @Override
                public @Nullable MemoryAccessor<F, Value> tryTrigger(ServerLevel level, E body, long timestamp) {
                    Brain<?> brain = ((LivingEntity) body).getBrain();
                    Optional<Value> optional = brain.<Value>getMemoryInternal(condition.memory());

                    return optional == null ? null : condition.createAccessor(brain, optional);
                }

                @Override
                public String debugString() {
                    return "M[" + String.valueOf(condition) + "]";
                }

                public String toString() {
                    return this.debugString();
                }
            });
        }
    }

    private static final class Constant<E extends LivingEntity, A> extends BehaviorBuilder<E, A> {

        private Constant(A a) {
            this(a, () -> {
                return "C[" + String.valueOf(a) + "]";
            });
        }

        private Constant(final A a, final Supplier<String> debugString) {
            super(new BehaviorBuilder.TriggerWithResult<E, A>() {
                @Override
                public A tryTrigger(ServerLevel level, E body, long timestamp) {
                    return a;
                }

                @Override
                public String debugString() {
                    return (String) debugString.get();
                }

                public String toString() {
                    return this.debugString();
                }
            });
        }
    }

    private static final class TriggerWrapper<E extends LivingEntity> extends BehaviorBuilder<E, Unit> {

        private TriggerWrapper(final Trigger<? super E> dependentTrigger) {
            super(new BehaviorBuilder.TriggerWithResult<E, Unit>() {
                @Override
                public @Nullable Unit tryTrigger(ServerLevel level, E body, long timestamp) {
                    return dependentTrigger.trigger(level, body, timestamp) ? Unit.INSTANCE : null;
                }

                @Override
                public String debugString() {
                    return "T[" + String.valueOf(dependentTrigger) + "]";
                }
            });
        }
    }

    public static final class Instance<E extends LivingEntity> implements Applicative<BehaviorBuilder.Mu<E>, BehaviorBuilder.Instance.Mu<E>> {

        public Instance() {}

        public <Value> Optional<Value> tryGet(MemoryAccessor<com.mojang.datafixers.kinds.OptionalBox.Mu, Value> box) {
            return OptionalBox.unbox(box.value());
        }

        public <Value> Value get(MemoryAccessor<com.mojang.datafixers.kinds.IdF.Mu, Value> box) {
            return (Value) IdF.get(box.value());
        }

        public <Value> BehaviorBuilder<E, MemoryAccessor<com.mojang.datafixers.kinds.OptionalBox.Mu, Value>> registered(MemoryModuleType<Value> memory) {
            return new BehaviorBuilder.PureMemory(new MemoryCondition.Registered(memory));
        }

        public <Value> BehaviorBuilder<E, MemoryAccessor<com.mojang.datafixers.kinds.IdF.Mu, Value>> present(MemoryModuleType<Value> memory) {
            return new BehaviorBuilder.PureMemory(new MemoryCondition.Present(memory));
        }

        public <Value> BehaviorBuilder<E, MemoryAccessor<Const.Mu<Unit>, Value>> absent(MemoryModuleType<Value> memory) {
            return new BehaviorBuilder.PureMemory(new MemoryCondition.Absent(memory));
        }

        public BehaviorBuilder<E, Unit> ifTriggered(Trigger<? super E> dependentTrigger) {
            return new BehaviorBuilder.TriggerWrapper(dependentTrigger);
        }

        public <A> BehaviorBuilder<E, A> point(A a) {
            return new BehaviorBuilder.Constant<E, A>(a);
        }

        public <A> BehaviorBuilder<E, A> point(Supplier<String> debugString, A a) {
            return new BehaviorBuilder.Constant<E, A>(a, debugString);
        }

        public <A, R> Function<App<BehaviorBuilder.Mu<E>, A>, App<BehaviorBuilder.Mu<E>, R>> lift1(App<BehaviorBuilder.Mu<E>, Function<A, R>> function) {
            return (app1) -> {
                final BehaviorBuilder.TriggerWithResult<E, A> behaviorbuilder_triggerwithresult = BehaviorBuilder.<E, A>get(app1);
                final BehaviorBuilder.TriggerWithResult<E, Function<A, R>> behaviorbuilder_triggerwithresult1 = BehaviorBuilder.<E, Function<A, R>>get(function);

                return BehaviorBuilder.create(new BehaviorBuilder.TriggerWithResult<E, R>() {
                    @Override
                    public R tryTrigger(ServerLevel level, E body, long timestamp) {
                        A a0 = behaviorbuilder_triggerwithresult.tryTrigger(level, body, timestamp);

                        if (a0 == null) {
                            return null;
                        } else {
                            Function<A, R> function1 = behaviorbuilder_triggerwithresult1.tryTrigger(level, body, timestamp);

                            return (R) (function1 == null ? null : function1.apply(a0));
                        }
                    }

                    @Override
                    public String debugString() {
                        String s = behaviorbuilder_triggerwithresult1.debugString();

                        return s + " * " + behaviorbuilder_triggerwithresult.debugString();
                    }

                    public String toString() {
                        return this.debugString();
                    }
                });
            };
        }

        public <T, R> BehaviorBuilder<E, R> map(final Function<? super T, ? extends R> func, App<BehaviorBuilder.Mu<E>, T> ts) {
            final BehaviorBuilder.TriggerWithResult<E, T> behaviorbuilder_triggerwithresult = BehaviorBuilder.<E, T>get(ts);

            return BehaviorBuilder.create(new BehaviorBuilder.TriggerWithResult<E, R>() {
                @Override
                public R tryTrigger(ServerLevel level, E body, long timestamp) {
                    T t0 = behaviorbuilder_triggerwithresult.tryTrigger(level, body, timestamp);

                    return (R) (t0 == null ? null : func.apply(t0));
                }

                @Override
                public String debugString() {
                    String s = behaviorbuilder_triggerwithresult.debugString();

                    return s + ".map[" + String.valueOf(func) + "]";
                }

                public String toString() {
                    return this.debugString();
                }
            });
        }

        public <A, B, R> BehaviorBuilder<E, R> ap2(App<BehaviorBuilder.Mu<E>, BiFunction<A, B, R>> func, App<BehaviorBuilder.Mu<E>, A> a, App<BehaviorBuilder.Mu<E>, B> b) {
            final BehaviorBuilder.TriggerWithResult<E, A> behaviorbuilder_triggerwithresult = BehaviorBuilder.<E, A>get(a);
            final BehaviorBuilder.TriggerWithResult<E, B> behaviorbuilder_triggerwithresult1 = BehaviorBuilder.<E, B>get(b);
            final BehaviorBuilder.TriggerWithResult<E, BiFunction<A, B, R>> behaviorbuilder_triggerwithresult2 = BehaviorBuilder.<E, BiFunction<A, B, R>>get(func);

            return BehaviorBuilder.create(new BehaviorBuilder.TriggerWithResult<E, R>() {
                @Override
                public R tryTrigger(ServerLevel level, E body, long timestamp) {
                    A a0 = behaviorbuilder_triggerwithresult.tryTrigger(level, body, timestamp);

                    if (a0 == null) {
                        return null;
                    } else {
                        B b0 = behaviorbuilder_triggerwithresult1.tryTrigger(level, body, timestamp);

                        if (b0 == null) {
                            return null;
                        } else {
                            BiFunction<A, B, R> bifunction = behaviorbuilder_triggerwithresult2.tryTrigger(level, body, timestamp);

                            return (R) (bifunction == null ? null : bifunction.apply(a0, b0));
                        }
                    }
                }

                @Override
                public String debugString() {
                    String s = behaviorbuilder_triggerwithresult2.debugString();

                    return s + " * " + behaviorbuilder_triggerwithresult.debugString() + " * " + behaviorbuilder_triggerwithresult1.debugString();
                }

                public String toString() {
                    return this.debugString();
                }
            });
        }

        public <T1, T2, T3, R> BehaviorBuilder<E, R> ap3(App<BehaviorBuilder.Mu<E>, Function3<T1, T2, T3, R>> func, App<BehaviorBuilder.Mu<E>, T1> t1, App<BehaviorBuilder.Mu<E>, T2> t2, App<BehaviorBuilder.Mu<E>, T3> t3) {
            final BehaviorBuilder.TriggerWithResult<E, T1> behaviorbuilder_triggerwithresult = BehaviorBuilder.<E, T1>get(t1);
            final BehaviorBuilder.TriggerWithResult<E, T2> behaviorbuilder_triggerwithresult1 = BehaviorBuilder.<E, T2>get(t2);
            final BehaviorBuilder.TriggerWithResult<E, T3> behaviorbuilder_triggerwithresult2 = BehaviorBuilder.<E, T3>get(t3);
            final BehaviorBuilder.TriggerWithResult<E, Function3<T1, T2, T3, R>> behaviorbuilder_triggerwithresult3 = BehaviorBuilder.<E, Function3<T1, T2, T3, R>>get(func);

            return BehaviorBuilder.create(new BehaviorBuilder.TriggerWithResult<E, R>() {
                @Override
                public R tryTrigger(ServerLevel level, E body, long timestamp) {
                    T1 t11 = behaviorbuilder_triggerwithresult.tryTrigger(level, body, timestamp);

                    if (t11 == null) {
                        return null;
                    } else {
                        T2 t21 = behaviorbuilder_triggerwithresult1.tryTrigger(level, body, timestamp);

                        if (t21 == null) {
                            return null;
                        } else {
                            T3 t31 = behaviorbuilder_triggerwithresult2.tryTrigger(level, body, timestamp);

                            if (t31 == null) {
                                return null;
                            } else {
                                Function3<T1, T2, T3, R> function3 = behaviorbuilder_triggerwithresult3.tryTrigger(level, body, timestamp);

                                return (R) (function3 == null ? null : function3.apply(t11, t21, t31));
                            }
                        }
                    }
                }

                @Override
                public String debugString() {
                    String s = behaviorbuilder_triggerwithresult3.debugString();

                    return s + " * " + behaviorbuilder_triggerwithresult.debugString() + " * " + behaviorbuilder_triggerwithresult1.debugString() + " * " + behaviorbuilder_triggerwithresult2.debugString();
                }

                public String toString() {
                    return this.debugString();
                }
            });
        }

        public <T1, T2, T3, T4, R> BehaviorBuilder<E, R> ap4(App<BehaviorBuilder.Mu<E>, Function4<T1, T2, T3, T4, R>> func, App<BehaviorBuilder.Mu<E>, T1> t1, App<BehaviorBuilder.Mu<E>, T2> t2, App<BehaviorBuilder.Mu<E>, T3> t3, App<BehaviorBuilder.Mu<E>, T4> t4) {
            final BehaviorBuilder.TriggerWithResult<E, T1> behaviorbuilder_triggerwithresult = BehaviorBuilder.<E, T1>get(t1);
            final BehaviorBuilder.TriggerWithResult<E, T2> behaviorbuilder_triggerwithresult1 = BehaviorBuilder.<E, T2>get(t2);
            final BehaviorBuilder.TriggerWithResult<E, T3> behaviorbuilder_triggerwithresult2 = BehaviorBuilder.<E, T3>get(t3);
            final BehaviorBuilder.TriggerWithResult<E, T4> behaviorbuilder_triggerwithresult3 = BehaviorBuilder.<E, T4>get(t4);
            final BehaviorBuilder.TriggerWithResult<E, Function4<T1, T2, T3, T4, R>> behaviorbuilder_triggerwithresult4 = BehaviorBuilder.<E, Function4<T1, T2, T3, T4, R>>get(func);

            return BehaviorBuilder.create(new BehaviorBuilder.TriggerWithResult<E, R>() {
                @Override
                public R tryTrigger(ServerLevel level, E body, long timestamp) {
                    T1 t11 = behaviorbuilder_triggerwithresult.tryTrigger(level, body, timestamp);

                    if (t11 == null) {
                        return null;
                    } else {
                        T2 t21 = behaviorbuilder_triggerwithresult1.tryTrigger(level, body, timestamp);

                        if (t21 == null) {
                            return null;
                        } else {
                            T3 t31 = behaviorbuilder_triggerwithresult2.tryTrigger(level, body, timestamp);

                            if (t31 == null) {
                                return null;
                            } else {
                                T4 t41 = behaviorbuilder_triggerwithresult3.tryTrigger(level, body, timestamp);

                                if (t41 == null) {
                                    return null;
                                } else {
                                    Function4<T1, T2, T3, T4, R> function4 = behaviorbuilder_triggerwithresult4.tryTrigger(level, body, timestamp);

                                    return (R) (function4 == null ? null : function4.apply(t11, t21, t31, t41));
                                }
                            }
                        }
                    }
                }

                @Override
                public String debugString() {
                    String s = behaviorbuilder_triggerwithresult4.debugString();

                    return s + " * " + behaviorbuilder_triggerwithresult.debugString() + " * " + behaviorbuilder_triggerwithresult1.debugString() + " * " + behaviorbuilder_triggerwithresult2.debugString() + " * " + behaviorbuilder_triggerwithresult3.debugString();
                }

                public String toString() {
                    return this.debugString();
                }
            });
        }

        private static final class Mu<E extends LivingEntity> implements com.mojang.datafixers.kinds.Applicative.Mu {

            private Mu() {}
        }
    }

    private interface TriggerWithResult<E extends LivingEntity, R> {

        @Nullable
        R tryTrigger(ServerLevel level, E body, long timestamp);

        String debugString();
    }
}
