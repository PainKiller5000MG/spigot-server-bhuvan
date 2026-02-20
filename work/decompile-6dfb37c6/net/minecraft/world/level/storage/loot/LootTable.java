package net.minecraft.world.level.storage.loot;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.functions.FunctionUserBuilder;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.slf4j.Logger;

public class LootTable {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Codec<ResourceKey<LootTable>> KEY_CODEC = ResourceKey.codec(Registries.LOOT_TABLE);
    public static final ContextKeySet DEFAULT_PARAM_SET = LootContextParamSets.ALL_PARAMS;
    public static final long RANDOMIZE_SEED = 0L;
    public static final Codec<LootTable> DIRECT_CODEC = Codec.lazyInitialized(() -> {
        return RecordCodecBuilder.create((instance) -> {
            return instance.group(LootContextParamSets.CODEC.lenientOptionalFieldOf("type", LootTable.DEFAULT_PARAM_SET).forGetter((loottable) -> {
                return loottable.paramSet;
            }), Identifier.CODEC.optionalFieldOf("random_sequence").forGetter((loottable) -> {
                return loottable.randomSequence;
            }), LootPool.CODEC.listOf().optionalFieldOf("pools", List.of()).forGetter((loottable) -> {
                return loottable.pools;
            }), LootItemFunctions.ROOT_CODEC.listOf().optionalFieldOf("functions", List.of()).forGetter((loottable) -> {
                return loottable.functions;
            })).apply(instance, LootTable::new);
        });
    });
    public static final Codec<Holder<LootTable>> CODEC = RegistryFileCodec.<Holder<LootTable>>create(Registries.LOOT_TABLE, LootTable.DIRECT_CODEC);
    public static final LootTable EMPTY = new LootTable(LootContextParamSets.EMPTY, Optional.empty(), List.of(), List.of());
    private final ContextKeySet paramSet;
    private final Optional<Identifier> randomSequence;
    private final List<LootPool> pools;
    private final List<LootItemFunction> functions;
    private final BiFunction<ItemStack, LootContext, ItemStack> compositeFunction;

    private LootTable(ContextKeySet paramSet, Optional<Identifier> randomSequence, List<LootPool> pools, List<LootItemFunction> functions) {
        this.paramSet = paramSet;
        this.randomSequence = randomSequence;
        this.pools = pools;
        this.functions = functions;
        this.compositeFunction = LootItemFunctions.compose(functions);
    }

    public static Consumer<ItemStack> createStackSplitter(ServerLevel level, Consumer<ItemStack> output) {
        return (itemstack) -> {
            if (itemstack.isItemEnabled(level.enabledFeatures())) {
                if (itemstack.getCount() < itemstack.getMaxStackSize()) {
                    output.accept(itemstack);
                } else {
                    int i = itemstack.getCount();

                    while (i > 0) {
                        ItemStack itemstack1 = itemstack.copyWithCount(Math.min(itemstack.getMaxStackSize(), i));

                        i -= itemstack1.getCount();
                        output.accept(itemstack1);
                    }
                }

            }
        };
    }

    public void getRandomItemsRaw(LootParams params, Consumer<ItemStack> output) {
        this.getRandomItemsRaw((new LootContext.Builder(params)).create(this.randomSequence), output);
    }

    public void getRandomItemsRaw(LootContext context, Consumer<ItemStack> output) {
        LootContext.VisitedEntry<?> lootcontext_visitedentry = LootContext.createVisitedEntry(this);

        if (context.pushVisitedElement(lootcontext_visitedentry)) {
            Consumer<ItemStack> consumer1 = LootItemFunction.decorate(this.compositeFunction, output, context);

            for (LootPool lootpool : this.pools) {
                lootpool.addRandomItems(consumer1, context);
            }

            context.popVisitedElement(lootcontext_visitedentry);
        } else {
            LootTable.LOGGER.warn("Detected infinite loop in loot tables");
        }

    }

    public void getRandomItems(LootParams params, long optionalLootTableSeed, Consumer<ItemStack> output) {
        this.getRandomItemsRaw((new LootContext.Builder(params)).withOptionalRandomSeed(optionalLootTableSeed).create(this.randomSequence), createStackSplitter(params.getLevel(), output));
    }

    public void getRandomItems(LootParams params, Consumer<ItemStack> output) {
        this.getRandomItemsRaw(params, createStackSplitter(params.getLevel(), output));
    }

    public void getRandomItems(LootContext context, Consumer<ItemStack> output) {
        this.getRandomItemsRaw(context, createStackSplitter(context.getLevel(), output));
    }

    public ObjectArrayList<ItemStack> getRandomItems(LootParams params, RandomSource randomSource) {
        return this.getRandomItems((new LootContext.Builder(params)).withOptionalRandomSource(randomSource).create(this.randomSequence));
    }

    public ObjectArrayList<ItemStack> getRandomItems(LootParams params, long optionalLootTableSeed) {
        return this.getRandomItems((new LootContext.Builder(params)).withOptionalRandomSeed(optionalLootTableSeed).create(this.randomSequence));
    }

    public ObjectArrayList<ItemStack> getRandomItems(LootParams params) {
        return this.getRandomItems((new LootContext.Builder(params)).create(this.randomSequence));
    }

    private ObjectArrayList<ItemStack> getRandomItems(LootContext context) {
        ObjectArrayList<ItemStack> objectarraylist = new ObjectArrayList();

        Objects.requireNonNull(objectarraylist);
        this.getRandomItems(context, objectarraylist::add);
        return objectarraylist;
    }

    public ContextKeySet getParamSet() {
        return this.paramSet;
    }

    public void validate(ValidationContext context) {
        for (int i = 0; i < this.pools.size(); ++i) {
            ((LootPool) this.pools.get(i)).validate(context.forChild(new ProblemReporter.IndexedFieldPathElement("pools", i)));
        }

        for (int j = 0; j < this.functions.size(); ++j) {
            ((LootItemFunction) this.functions.get(j)).validate(context.forChild(new ProblemReporter.IndexedFieldPathElement("functions", j)));
        }

    }

    public void fill(Container container, LootParams params, long optionalRandomSeed) {
        LootContext lootcontext = (new LootContext.Builder(params)).withOptionalRandomSeed(optionalRandomSeed).create(this.randomSequence);
        ObjectArrayList<ItemStack> objectarraylist = this.getRandomItems(lootcontext);
        RandomSource randomsource = lootcontext.getRandom();
        List<Integer> list = this.getAvailableSlots(container, randomsource);

        this.shuffleAndSplitItems(objectarraylist, list.size(), randomsource);
        ObjectListIterator objectlistiterator = objectarraylist.iterator();

        while (objectlistiterator.hasNext()) {
            ItemStack itemstack = (ItemStack) objectlistiterator.next();

            if (list.isEmpty()) {
                LootTable.LOGGER.warn("Tried to over-fill a container");
                return;
            }

            if (itemstack.isEmpty()) {
                container.setItem((Integer) list.remove(list.size() - 1), ItemStack.EMPTY);
            } else {
                container.setItem((Integer) list.remove(list.size() - 1), itemstack);
            }
        }

    }

    private void shuffleAndSplitItems(ObjectArrayList<ItemStack> result, int availableSlots, RandomSource random) {
        List<ItemStack> list = Lists.newArrayList();
        Iterator<ItemStack> iterator = result.iterator();

        while (((Iterator) iterator).hasNext()) {
            ItemStack itemstack = (ItemStack) iterator.next();

            if (itemstack.isEmpty()) {
                iterator.remove();
            } else if (itemstack.getCount() > 1) {
                list.add(itemstack);
                iterator.remove();
            }
        }

        while (availableSlots - result.size() - ((List) list).size() > 0 && !((List) list).isEmpty()) {
            ItemStack itemstack1 = (ItemStack) list.remove(Mth.nextInt(random, 0, list.size() - 1));
            int j = Mth.nextInt(random, 1, itemstack1.getCount() / 2);
            ItemStack itemstack2 = itemstack1.split(j);

            if (itemstack1.getCount() > 1 && random.nextBoolean()) {
                list.add(itemstack1);
            } else {
                result.add(itemstack1);
            }

            if (itemstack2.getCount() > 1 && random.nextBoolean()) {
                list.add(itemstack2);
            } else {
                result.add(itemstack2);
            }
        }

        result.addAll(list);
        Util.shuffle(result, random);
    }

    private List<Integer> getAvailableSlots(Container container, RandomSource random) {
        ObjectArrayList<Integer> objectarraylist = new ObjectArrayList();

        for (int i = 0; i < container.getContainerSize(); ++i) {
            if (container.getItem(i).isEmpty()) {
                objectarraylist.add(i);
            }
        }

        Util.shuffle(objectarraylist, random);
        return objectarraylist;
    }

    public static LootTable.Builder lootTable() {
        return new LootTable.Builder();
    }

    public static class Builder implements FunctionUserBuilder<LootTable.Builder> {

        private final ImmutableList.Builder<LootPool> pools = ImmutableList.builder();
        private final ImmutableList.Builder<LootItemFunction> functions = ImmutableList.builder();
        private ContextKeySet paramSet;
        private Optional<Identifier> randomSequence;

        public Builder() {
            this.paramSet = LootTable.DEFAULT_PARAM_SET;
            this.randomSequence = Optional.empty();
        }

        public LootTable.Builder withPool(LootPool.Builder pool) {
            this.pools.add(pool.build());
            return this;
        }

        public LootTable.Builder setParamSet(ContextKeySet paramSet) {
            this.paramSet = paramSet;
            return this;
        }

        public LootTable.Builder setRandomSequence(Identifier key) {
            this.randomSequence = Optional.of(key);
            return this;
        }

        @Override
        public LootTable.Builder apply(LootItemFunction.Builder function) {
            this.functions.add(function.build());
            return this;
        }

        @Override
        public LootTable.Builder unwrap() {
            return this;
        }

        public LootTable build() {
            return new LootTable(this.paramSet, this.randomSequence, this.pools.build(), this.functions.build());
        }
    }
}
