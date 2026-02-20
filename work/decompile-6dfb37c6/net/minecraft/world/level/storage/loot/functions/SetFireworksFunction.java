package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetFireworksFunction extends LootItemConditionalFunction {

    public static final MapCodec<SetFireworksFunction> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).and(instance.group(ListOperation.StandAlone.codec(FireworkExplosion.CODEC, 256).optionalFieldOf("explosions").forGetter((setfireworksfunction) -> {
            return setfireworksfunction.explosions;
        }), ExtraCodecs.UNSIGNED_BYTE.optionalFieldOf("flight_duration").forGetter((setfireworksfunction) -> {
            return setfireworksfunction.flightDuration;
        }))).apply(instance, SetFireworksFunction::new);
    });
    public static final Fireworks DEFAULT_VALUE = new Fireworks(0, List.of());
    private final Optional<ListOperation.StandAlone<FireworkExplosion>> explosions;
    private final Optional<Integer> flightDuration;

    protected SetFireworksFunction(List<LootItemCondition> predicates, Optional<ListOperation.StandAlone<FireworkExplosion>> explosions, Optional<Integer> flightDuration) {
        super(predicates);
        this.explosions = explosions;
        this.flightDuration = flightDuration;
    }

    @Override
    protected ItemStack run(ItemStack itemStack, LootContext context) {
        itemStack.update(DataComponents.FIREWORKS, SetFireworksFunction.DEFAULT_VALUE, this::apply);
        return itemStack;
    }

    private Fireworks apply(Fireworks old) {
        Optional optional = this.flightDuration;

        Objects.requireNonNull(old);
        return new Fireworks((Integer) optional.orElseGet(old::flightDuration), (List) this.explosions.map((listoperation_standalone) -> {
            return listoperation_standalone.apply(old.explosions());
        }).orElse(old.explosions()));
    }

    @Override
    public LootItemFunctionType<SetFireworksFunction> getType() {
        return LootItemFunctions.SET_FIREWORKS;
    }
}
