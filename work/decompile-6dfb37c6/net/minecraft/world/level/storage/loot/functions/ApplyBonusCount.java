package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class ApplyBonusCount extends LootItemConditionalFunction {

    private static final Map<Identifier, ApplyBonusCount.FormulaType> FORMULAS = (Map) Stream.of(ApplyBonusCount.BinomialWithBonusCount.TYPE, ApplyBonusCount.OreDrops.TYPE, ApplyBonusCount.UniformBonusCount.TYPE).collect(Collectors.toMap(ApplyBonusCount.FormulaType::id, Function.identity()));
    private static final Codec<ApplyBonusCount.FormulaType> FORMULA_TYPE_CODEC = Identifier.CODEC.comapFlatMap((identifier) -> {
        ApplyBonusCount.FormulaType applybonuscount_formulatype = (ApplyBonusCount.FormulaType) ApplyBonusCount.FORMULAS.get(identifier);

        return applybonuscount_formulatype != null ? DataResult.success(applybonuscount_formulatype) : DataResult.error(() -> {
            return "No formula type with id: '" + String.valueOf(identifier) + "'";
        });
    }, ApplyBonusCount.FormulaType::id);
    private static final MapCodec<ApplyBonusCount.Formula> FORMULA_CODEC = ExtraCodecs.dispatchOptionalValue("formula", "parameters", ApplyBonusCount.FORMULA_TYPE_CODEC, ApplyBonusCount.Formula::getType, ApplyBonusCount.FormulaType::codec);
    public static final MapCodec<ApplyBonusCount> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).and(instance.group(Enchantment.CODEC.fieldOf("enchantment").forGetter((applybonuscount) -> {
            return applybonuscount.enchantment;
        }), ApplyBonusCount.FORMULA_CODEC.forGetter((applybonuscount) -> {
            return applybonuscount.formula;
        }))).apply(instance, ApplyBonusCount::new);
    });
    private final Holder<Enchantment> enchantment;
    private final ApplyBonusCount.Formula formula;

    private ApplyBonusCount(List<LootItemCondition> predicates, Holder<Enchantment> enchantment, ApplyBonusCount.Formula formula) {
        super(predicates);
        this.enchantment = enchantment;
        this.formula = formula;
    }

    @Override
    public LootItemFunctionType<ApplyBonusCount> getType() {
        return LootItemFunctions.APPLY_BONUS;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.TOOL);
    }

    @Override
    public ItemStack run(ItemStack itemStack, LootContext context) {
        ItemStack itemstack1 = (ItemStack) context.getOptionalParameter(LootContextParams.TOOL);

        if (itemstack1 != null) {
            int i = EnchantmentHelper.getItemEnchantmentLevel(this.enchantment, itemstack1);
            int j = this.formula.calculateNewCount(context.getRandom(), itemStack.getCount(), i);

            itemStack.setCount(j);
        }

        return itemStack;
    }

    public static LootItemConditionalFunction.Builder<?> addBonusBinomialDistributionCount(Holder<Enchantment> enchantment, float probability, int extraRounds) {
        return simpleBuilder((list) -> {
            return new ApplyBonusCount(list, enchantment, new ApplyBonusCount.BinomialWithBonusCount(extraRounds, probability));
        });
    }

    public static LootItemConditionalFunction.Builder<?> addOreBonusCount(Holder<Enchantment> enchantment) {
        return simpleBuilder((list) -> {
            return new ApplyBonusCount(list, enchantment, ApplyBonusCount.OreDrops.INSTANCE);
        });
    }

    public static LootItemConditionalFunction.Builder<?> addUniformBonusCount(Holder<Enchantment> enchantment) {
        return simpleBuilder((list) -> {
            return new ApplyBonusCount(list, enchantment, new ApplyBonusCount.UniformBonusCount(1));
        });
    }

    public static LootItemConditionalFunction.Builder<?> addUniformBonusCount(Holder<Enchantment> enchantment, int bonusMultiplier) {
        return simpleBuilder((list) -> {
            return new ApplyBonusCount(list, enchantment, new ApplyBonusCount.UniformBonusCount(bonusMultiplier));
        });
    }

    private static record FormulaType(Identifier id, Codec<? extends ApplyBonusCount.Formula> codec) {

    }

    private static record BinomialWithBonusCount(int extraRounds, float probability) implements ApplyBonusCount.Formula {

        private static final Codec<ApplyBonusCount.BinomialWithBonusCount> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(Codec.INT.fieldOf("extra").forGetter(ApplyBonusCount.BinomialWithBonusCount::extraRounds), Codec.FLOAT.fieldOf("probability").forGetter(ApplyBonusCount.BinomialWithBonusCount::probability)).apply(instance, ApplyBonusCount.BinomialWithBonusCount::new);
        });
        public static final ApplyBonusCount.FormulaType TYPE = new ApplyBonusCount.FormulaType(Identifier.withDefaultNamespace("binomial_with_bonus_count"), ApplyBonusCount.BinomialWithBonusCount.CODEC);

        @Override
        public int calculateNewCount(RandomSource random, int count, int level) {
            for (int k = 0; k < level + this.extraRounds; ++k) {
                if (random.nextFloat() < this.probability) {
                    ++count;
                }
            }

            return count;
        }

        @Override
        public ApplyBonusCount.FormulaType getType() {
            return ApplyBonusCount.BinomialWithBonusCount.TYPE;
        }
    }

    private static record UniformBonusCount(int bonusMultiplier) implements ApplyBonusCount.Formula {

        public static final Codec<ApplyBonusCount.UniformBonusCount> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(Codec.INT.fieldOf("bonusMultiplier").forGetter(ApplyBonusCount.UniformBonusCount::bonusMultiplier)).apply(instance, ApplyBonusCount.UniformBonusCount::new);
        });
        public static final ApplyBonusCount.FormulaType TYPE = new ApplyBonusCount.FormulaType(Identifier.withDefaultNamespace("uniform_bonus_count"), ApplyBonusCount.UniformBonusCount.CODEC);

        @Override
        public int calculateNewCount(RandomSource random, int count, int level) {
            return count + random.nextInt(this.bonusMultiplier * level + 1);
        }

        @Override
        public ApplyBonusCount.FormulaType getType() {
            return ApplyBonusCount.UniformBonusCount.TYPE;
        }
    }

    private static record OreDrops() implements ApplyBonusCount.Formula {

        public static final ApplyBonusCount.OreDrops INSTANCE = new ApplyBonusCount.OreDrops();
        public static final Codec<ApplyBonusCount.OreDrops> CODEC = MapCodec.unitCodec(ApplyBonusCount.OreDrops.INSTANCE);
        public static final ApplyBonusCount.FormulaType TYPE = new ApplyBonusCount.FormulaType(Identifier.withDefaultNamespace("ore_drops"), ApplyBonusCount.OreDrops.CODEC);

        @Override
        public int calculateNewCount(RandomSource random, int count, int level) {
            if (level > 0) {
                int k = random.nextInt(level + 2) - 1;

                if (k < 0) {
                    k = 0;
                }

                return count * (k + 1);
            } else {
                return count;
            }
        }

        @Override
        public ApplyBonusCount.FormulaType getType() {
            return ApplyBonusCount.OreDrops.TYPE;
        }
    }

    private interface Formula {

        int calculateNewCount(RandomSource random, int count, int level);

        ApplyBonusCount.FormulaType getType();
    }
}
