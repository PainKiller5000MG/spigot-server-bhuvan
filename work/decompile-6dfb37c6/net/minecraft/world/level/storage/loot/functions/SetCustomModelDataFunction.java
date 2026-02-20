package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

public class SetCustomModelDataFunction extends LootItemConditionalFunction {

    private static final Codec<NumberProvider> COLOR_PROVIDER_CODEC = Codec.withAlternative(NumberProviders.CODEC, ExtraCodecs.RGB_COLOR_CODEC, ConstantValue::new);
    public static final MapCodec<SetCustomModelDataFunction> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).and(instance.group(ListOperation.StandAlone.codec(NumberProviders.CODEC, Integer.MAX_VALUE).optionalFieldOf("floats").forGetter((setcustommodeldatafunction) -> {
            return setcustommodeldatafunction.floats;
        }), ListOperation.StandAlone.codec(Codec.BOOL, Integer.MAX_VALUE).optionalFieldOf("flags").forGetter((setcustommodeldatafunction) -> {
            return setcustommodeldatafunction.flags;
        }), ListOperation.StandAlone.codec(Codec.STRING, Integer.MAX_VALUE).optionalFieldOf("strings").forGetter((setcustommodeldatafunction) -> {
            return setcustommodeldatafunction.strings;
        }), ListOperation.StandAlone.codec(SetCustomModelDataFunction.COLOR_PROVIDER_CODEC, Integer.MAX_VALUE).optionalFieldOf("colors").forGetter((setcustommodeldatafunction) -> {
            return setcustommodeldatafunction.colors;
        }))).apply(instance, SetCustomModelDataFunction::new);
    });
    private final Optional<ListOperation.StandAlone<NumberProvider>> floats;
    private final Optional<ListOperation.StandAlone<Boolean>> flags;
    private final Optional<ListOperation.StandAlone<String>> strings;
    private final Optional<ListOperation.StandAlone<NumberProvider>> colors;

    public SetCustomModelDataFunction(List<LootItemCondition> predicates, Optional<ListOperation.StandAlone<NumberProvider>> floats, Optional<ListOperation.StandAlone<Boolean>> flags, Optional<ListOperation.StandAlone<String>> strings, Optional<ListOperation.StandAlone<NumberProvider>> colors) {
        super(predicates);
        this.floats = floats;
        this.flags = flags;
        this.strings = strings;
        this.colors = colors;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return (Set) Stream.concat(this.floats.stream(), this.colors.stream()).flatMap((listoperation_standalone) -> {
            return listoperation_standalone.value().stream();
        }).flatMap((numberprovider) -> {
            return numberprovider.getReferencedContextParams().stream();
        }).collect(Collectors.toSet());
    }

    @Override
    public LootItemFunctionType<SetCustomModelDataFunction> getType() {
        return LootItemFunctions.SET_CUSTOM_MODEL_DATA;
    }

    private static <T> List<T> apply(Optional<ListOperation.StandAlone<T>> operation, List<T> current) {
        return (List) operation.map((listoperation_standalone) -> {
            return listoperation_standalone.apply(current);
        }).orElse(current);
    }

    private static <T, E> List<E> apply(Optional<ListOperation.StandAlone<T>> operation, List<E> current, Function<T, E> mapper) {
        return (List) operation.map((listoperation_standalone) -> {
            List<E> list1 = listoperation_standalone.value().stream().map(mapper).toList();

            return listoperation_standalone.operation().apply(current, list1);
        }).orElse(current);
    }

    @Override
    public ItemStack run(ItemStack itemStack, LootContext context) {
        CustomModelData custommodeldata = (CustomModelData) itemStack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);

        itemStack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(apply(this.floats, custommodeldata.floats(), (numberprovider) -> {
            return numberprovider.getFloat(context);
        }), apply(this.flags, custommodeldata.flags()), apply(this.strings, custommodeldata.strings()), apply(this.colors, custommodeldata.colors(), (numberprovider) -> {
            return numberprovider.getInt(context);
        })));
        return itemStack;
    }
}
