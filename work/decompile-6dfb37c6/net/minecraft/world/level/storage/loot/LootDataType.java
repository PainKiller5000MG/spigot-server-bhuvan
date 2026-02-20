package net.minecraft.world.level.storage.loot;

import com.mojang.serialization.Codec;
import java.util.stream.Stream;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public record LootDataType<T>(ResourceKey<Registry<T>> registryKey, Codec<T> codec, LootDataType.Validator<T> validator) {

    public static final LootDataType<LootItemCondition> PREDICATE = new LootDataType<LootItemCondition>(Registries.PREDICATE, LootItemCondition.DIRECT_CODEC, createSimpleValidator());
    public static final LootDataType<LootItemFunction> MODIFIER = new LootDataType<LootItemFunction>(Registries.ITEM_MODIFIER, LootItemFunctions.ROOT_CODEC, createSimpleValidator());
    public static final LootDataType<LootTable> TABLE = new LootDataType<LootTable>(Registries.LOOT_TABLE, LootTable.DIRECT_CODEC, createLootTableValidator());

    public void runValidation(ValidationContext rootContext, ResourceKey<T> key, T value) {
        this.validator.run(rootContext, key, value);
    }

    public static Stream<LootDataType<?>> values() {
        return Stream.of(LootDataType.PREDICATE, LootDataType.MODIFIER, LootDataType.TABLE);
    }

    private static <T extends LootContextUser> LootDataType.Validator<T> createSimpleValidator() {
        return (validationcontext, resourcekey, lootcontextuser) -> {
            lootcontextuser.validate(validationcontext.enterElement(new ProblemReporter.RootElementPathElement(resourcekey), resourcekey));
        };
    }

    private static LootDataType.Validator<LootTable> createLootTableValidator() {
        return (validationcontext, resourcekey, loottable) -> {
            loottable.validate(validationcontext.setContextKeySet(loottable.getParamSet()).enterElement(new ProblemReporter.RootElementPathElement(resourcekey), resourcekey));
        };
    }

    @FunctionalInterface
    public interface Validator<T> {

        void run(ValidationContext rootContext, ResourceKey<T> id, T value);
    }
}
