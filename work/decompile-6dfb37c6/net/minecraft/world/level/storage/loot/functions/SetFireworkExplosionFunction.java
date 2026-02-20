package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetFireworkExplosionFunction extends LootItemConditionalFunction {

    public static final MapCodec<SetFireworkExplosionFunction> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).and(instance.group(FireworkExplosion.Shape.CODEC.optionalFieldOf("shape").forGetter((setfireworkexplosionfunction) -> {
            return setfireworkexplosionfunction.shape;
        }), FireworkExplosion.COLOR_LIST_CODEC.optionalFieldOf("colors").forGetter((setfireworkexplosionfunction) -> {
            return setfireworkexplosionfunction.colors;
        }), FireworkExplosion.COLOR_LIST_CODEC.optionalFieldOf("fade_colors").forGetter((setfireworkexplosionfunction) -> {
            return setfireworkexplosionfunction.fadeColors;
        }), Codec.BOOL.optionalFieldOf("trail").forGetter((setfireworkexplosionfunction) -> {
            return setfireworkexplosionfunction.trail;
        }), Codec.BOOL.optionalFieldOf("twinkle").forGetter((setfireworkexplosionfunction) -> {
            return setfireworkexplosionfunction.twinkle;
        }))).apply(instance, SetFireworkExplosionFunction::new);
    });
    public static final FireworkExplosion DEFAULT_VALUE = new FireworkExplosion(FireworkExplosion.Shape.SMALL_BALL, IntList.of(), IntList.of(), false, false);
    final Optional<FireworkExplosion.Shape> shape;
    final Optional<IntList> colors;
    final Optional<IntList> fadeColors;
    final Optional<Boolean> trail;
    final Optional<Boolean> twinkle;

    public SetFireworkExplosionFunction(List<LootItemCondition> predicates, Optional<FireworkExplosion.Shape> shape, Optional<IntList> colors, Optional<IntList> fadeColors, Optional<Boolean> hasTrail, Optional<Boolean> hasTwinkle) {
        super(predicates);
        this.shape = shape;
        this.colors = colors;
        this.fadeColors = fadeColors;
        this.trail = hasTrail;
        this.twinkle = hasTwinkle;
    }

    @Override
    protected ItemStack run(ItemStack itemStack, LootContext context) {
        itemStack.update(DataComponents.FIREWORK_EXPLOSION, SetFireworkExplosionFunction.DEFAULT_VALUE, this::apply);
        return itemStack;
    }

    private FireworkExplosion apply(FireworkExplosion original) {
        Optional optional = this.shape;

        Objects.requireNonNull(original);
        FireworkExplosion.Shape fireworkexplosion_shape = (FireworkExplosion.Shape) optional.orElseGet(original::shape);
        Optional optional1 = this.colors;

        Objects.requireNonNull(original);
        IntList intlist = (IntList) optional1.orElseGet(original::colors);
        Optional optional2 = this.fadeColors;

        Objects.requireNonNull(original);
        IntList intlist1 = (IntList) optional2.orElseGet(original::fadeColors);
        Optional optional3 = this.trail;

        Objects.requireNonNull(original);
        boolean flag = (Boolean) optional3.orElseGet(original::hasTrail);
        Optional optional4 = this.twinkle;

        Objects.requireNonNull(original);
        return new FireworkExplosion(fireworkexplosion_shape, intlist, intlist1, flag, (Boolean) optional4.orElseGet(original::hasTwinkle));
    }

    @Override
    public LootItemFunctionType<SetFireworkExplosionFunction> getType() {
        return LootItemFunctions.SET_FIREWORK_EXPLOSION;
    }
}
