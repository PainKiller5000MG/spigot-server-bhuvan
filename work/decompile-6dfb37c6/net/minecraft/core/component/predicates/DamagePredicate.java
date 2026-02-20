package net.minecraft.core.component.predicates;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;

public record DamagePredicate(MinMaxBounds.Ints durability, MinMaxBounds.Ints damage) implements DataComponentPredicate {

    public static final Codec<DamagePredicate> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(MinMaxBounds.Ints.CODEC.optionalFieldOf("durability", MinMaxBounds.Ints.ANY).forGetter(DamagePredicate::durability), MinMaxBounds.Ints.CODEC.optionalFieldOf("damage", MinMaxBounds.Ints.ANY).forGetter(DamagePredicate::damage)).apply(instance, DamagePredicate::new);
    });

    @Override
    public boolean matches(DataComponentGetter components) {
        Integer integer = (Integer) components.get(DataComponents.DAMAGE);

        if (integer == null) {
            return false;
        } else {
            int i = (Integer) components.getOrDefault(DataComponents.MAX_DAMAGE, 0);

            return !this.durability.matches(i - integer) ? false : this.damage.matches(integer);
        }
    }

    public static DamagePredicate durability(MinMaxBounds.Ints range) {
        return new DamagePredicate(range, MinMaxBounds.Ints.ANY);
    }
}
