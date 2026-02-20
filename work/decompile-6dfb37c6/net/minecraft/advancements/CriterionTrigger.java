package net.minecraft.advancements;

import com.mojang.serialization.Codec;
import net.minecraft.server.PlayerAdvancements;

public interface CriterionTrigger<T extends CriterionTriggerInstance> {

    void addPlayerListener(PlayerAdvancements player, CriterionTrigger.Listener<T> listener);

    void removePlayerListener(PlayerAdvancements player, CriterionTrigger.Listener<T> listener);

    void removePlayerListeners(PlayerAdvancements player);

    Codec<T> codec();

    default Criterion<T> createCriterion(T instance) {
        return new Criterion<T>(this, instance);
    }

    public static record Listener<T extends CriterionTriggerInstance>(T trigger, AdvancementHolder advancement, String criterion) {

        public void run(PlayerAdvancements player) {
            player.award(this.advancement, this.criterion);
        }
    }
}
