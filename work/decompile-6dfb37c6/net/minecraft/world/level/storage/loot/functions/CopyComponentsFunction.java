package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Util;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootContextArg;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class CopyComponentsFunction extends LootItemConditionalFunction {

    private static final Codec<LootContextArg<DataComponentGetter>> GETTER_CODEC = LootContextArg.createArgCodec((lootcontextarg_argcodecbuilder) -> {
        return lootcontextarg_argcodecbuilder.anyEntity(CopyComponentsFunction.DirectSource::new).anyBlockEntity(CopyComponentsFunction.BlockEntitySource::new).anyItemStack(CopyComponentsFunction.DirectSource::new);
    });
    public static final MapCodec<CopyComponentsFunction> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).and(instance.group(CopyComponentsFunction.GETTER_CODEC.fieldOf("source").forGetter((copycomponentsfunction) -> {
            return copycomponentsfunction.source;
        }), DataComponentType.CODEC.listOf().optionalFieldOf("include").forGetter((copycomponentsfunction) -> {
            return copycomponentsfunction.include;
        }), DataComponentType.CODEC.listOf().optionalFieldOf("exclude").forGetter((copycomponentsfunction) -> {
            return copycomponentsfunction.exclude;
        }))).apply(instance, CopyComponentsFunction::new);
    });
    private final LootContextArg<DataComponentGetter> source;
    private final Optional<List<DataComponentType<?>>> include;
    private final Optional<List<DataComponentType<?>>> exclude;
    private final Predicate<DataComponentType<?>> bakedPredicate;

    private CopyComponentsFunction(List<LootItemCondition> predicates, LootContextArg<DataComponentGetter> source, Optional<List<DataComponentType<?>>> include, Optional<List<DataComponentType<?>>> exclude) {
        super(predicates);
        this.source = source;
        this.include = include.map(List::copyOf);
        this.exclude = exclude.map(List::copyOf);
        List<Predicate<DataComponentType<?>>> list1 = new ArrayList(2);

        exclude.ifPresent((list2) -> {
            list1.add((Predicate) (datacomponenttype) -> {
                return !list2.contains(datacomponenttype);
            });
        });
        include.ifPresent((list2) -> {
            Objects.requireNonNull(list2);
            list1.add(list2::contains);
        });
        this.bakedPredicate = Util.allOf(list1);
    }

    @Override
    public LootItemFunctionType<CopyComponentsFunction> getType() {
        return LootItemFunctions.COPY_COMPONENTS;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(this.source.contextParam());
    }

    @Override
    public ItemStack run(ItemStack itemStack, LootContext context) {
        DataComponentGetter datacomponentgetter = this.source.get(context);

        if (datacomponentgetter != null) {
            if (datacomponentgetter instanceof DataComponentMap) {
                DataComponentMap datacomponentmap = (DataComponentMap) datacomponentgetter;

                itemStack.applyComponents(datacomponentmap.filter(this.bakedPredicate));
            } else {
                Collection<DataComponentType<?>> collection = (Collection) this.exclude.orElse(List.of());

                ((Stream) this.include.map(Collection::stream).orElse(BuiltInRegistries.DATA_COMPONENT_TYPE.listElements().map(Holder::value))).forEach((datacomponenttype) -> {
                    if (!collection.contains(datacomponenttype)) {
                        TypedDataComponent<?> typeddatacomponent = datacomponentgetter.getTyped(datacomponenttype);

                        if (typeddatacomponent != null) {
                            itemStack.set(typeddatacomponent);
                        }

                    }
                });
            }
        }

        return itemStack;
    }

    public static CopyComponentsFunction.Builder copyComponentsFromEntity(ContextKey<? extends Entity> source) {
        return new CopyComponentsFunction.Builder(new CopyComponentsFunction.DirectSource(source));
    }

    public static CopyComponentsFunction.Builder copyComponentsFromBlockEntity(ContextKey<? extends BlockEntity> source) {
        return new CopyComponentsFunction.Builder(new CopyComponentsFunction.BlockEntitySource(source));
    }

    public static class Builder extends LootItemConditionalFunction.Builder<CopyComponentsFunction.Builder> {

        private final LootContextArg<DataComponentGetter> source;
        private Optional<ImmutableList.Builder<DataComponentType<?>>> include = Optional.empty();
        private Optional<ImmutableList.Builder<DataComponentType<?>>> exclude = Optional.empty();

        private Builder(LootContextArg<DataComponentGetter> source) {
            this.source = source;
        }

        public CopyComponentsFunction.Builder include(DataComponentType<?> type) {
            if (this.include.isEmpty()) {
                this.include = Optional.of(ImmutableList.builder());
            }

            ((com.google.common.collect.ImmutableList.Builder) this.include.get()).add(type);
            return this;
        }

        public CopyComponentsFunction.Builder exclude(DataComponentType<?> type) {
            if (this.exclude.isEmpty()) {
                this.exclude = Optional.of(ImmutableList.builder());
            }

            ((com.google.common.collect.ImmutableList.Builder) this.exclude.get()).add(type);
            return this;
        }

        @Override
        protected CopyComponentsFunction.Builder getThis() {
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new CopyComponentsFunction(this.getConditions(), this.source, this.include.map(com.google.common.collect.ImmutableList.Builder::build), this.exclude.map(com.google.common.collect.ImmutableList.Builder::build));
        }
    }

    private static record DirectSource<T extends DataComponentGetter>(ContextKey<? extends T> contextParam) implements LootContextArg.Getter<T, DataComponentGetter> {

        public DataComponentGetter get(T value) {
            return value;
        }
    }

    private static record BlockEntitySource(ContextKey<? extends BlockEntity> contextParam) implements LootContextArg.Getter<BlockEntity, DataComponentGetter> {

        public DataComponentGetter get(BlockEntity blockEntity) {
            return blockEntity.collectComponents();
        }
    }
}
