package net.minecraft.advancements.criterion;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootContext;

public abstract class SimpleCriterionTrigger<T extends SimpleCriterionTrigger.SimpleInstance> implements CriterionTrigger<T> {

    private final Map<PlayerAdvancements, Set<CriterionTrigger.Listener<T>>> players = Maps.newIdentityHashMap();

    public SimpleCriterionTrigger() {}

    @Override
    public final void addPlayerListener(PlayerAdvancements player, CriterionTrigger.Listener<T> listener) {
        ((Set) this.players.computeIfAbsent(player, (playeradvancements1) -> {
            return Sets.newHashSet();
        })).add(listener);
    }

    @Override
    public final void removePlayerListener(PlayerAdvancements player, CriterionTrigger.Listener<T> listener) {
        Set<CriterionTrigger.Listener<T>> set = (Set) this.players.get(player);

        if (set != null) {
            set.remove(listener);
            if (set.isEmpty()) {
                this.players.remove(player);
            }
        }

    }

    @Override
    public final void removePlayerListeners(PlayerAdvancements player) {
        this.players.remove(player);
    }

    protected void trigger(ServerPlayer player, Predicate<T> matcher) {
        PlayerAdvancements playeradvancements = player.getAdvancements();
        Set<CriterionTrigger.Listener<T>> set = (Set) this.players.get(playeradvancements);

        if (set != null && !set.isEmpty()) {
            LootContext lootcontext = EntityPredicate.createContext(player, player);
            List<CriterionTrigger.Listener<T>> list = null;

            for (CriterionTrigger.Listener<T> criteriontrigger_listener : set) {
                T t0 = criteriontrigger_listener.trigger();

                if (matcher.test(t0)) {
                    Optional<ContextAwarePredicate> optional = t0.player();

                    if (optional.isEmpty() || ((ContextAwarePredicate) optional.get()).matches(lootcontext)) {
                        if (list == null) {
                            list = Lists.newArrayList();
                        }

                        list.add(criteriontrigger_listener);
                    }
                }
            }

            if (list != null) {
                for (CriterionTrigger.Listener<T> criteriontrigger_listener1 : list) {
                    criteriontrigger_listener1.run(playeradvancements);
                }
            }

        }
    }

    public interface SimpleInstance extends CriterionTriggerInstance {

        @Override
        default void validate(CriterionValidator validator) {
            validator.validateEntity(this.player(), "player");
        }

        Optional<ContextAwarePredicate> player();
    }
}
