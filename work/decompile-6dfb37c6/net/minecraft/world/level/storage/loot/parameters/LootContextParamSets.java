package net.minecraft.world.level.storage.loot.parameters;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKeySet;

public class LootContextParamSets {

    private static final BiMap<Identifier, ContextKeySet> REGISTRY = HashBiMap.create();
    public static final Codec<ContextKeySet> CODEC;
    public static final ContextKeySet EMPTY;
    public static final ContextKeySet CHEST;
    public static final ContextKeySet COMMAND;
    public static final ContextKeySet SELECTOR;
    public static final ContextKeySet FISHING;
    public static final ContextKeySet ENTITY;
    public static final ContextKeySet EQUIPMENT;
    public static final ContextKeySet ARCHAEOLOGY;
    public static final ContextKeySet GIFT;
    public static final ContextKeySet PIGLIN_BARTER;
    public static final ContextKeySet VAULT;
    public static final ContextKeySet ADVANCEMENT_REWARD;
    public static final ContextKeySet ADVANCEMENT_ENTITY;
    public static final ContextKeySet ADVANCEMENT_LOCATION;
    public static final ContextKeySet BLOCK_USE;
    public static final ContextKeySet ALL_PARAMS;
    public static final ContextKeySet BLOCK;
    public static final ContextKeySet SHEARING;
    public static final ContextKeySet ENTITY_INTERACT;
    public static final ContextKeySet BLOCK_INTERACT;
    public static final ContextKeySet ENCHANTED_DAMAGE;
    public static final ContextKeySet ENCHANTED_ITEM;
    public static final ContextKeySet ENCHANTED_LOCATION;
    public static final ContextKeySet ENCHANTED_ENTITY;
    public static final ContextKeySet HIT_BLOCK;

    public LootContextParamSets() {}

    private static ContextKeySet register(String name, Consumer<ContextKeySet.Builder> consumer) {
        ContextKeySet.Builder contextkeyset_builder = new ContextKeySet.Builder();

        consumer.accept(contextkeyset_builder);
        ContextKeySet contextkeyset = contextkeyset_builder.build();
        Identifier identifier = Identifier.withDefaultNamespace(name);
        ContextKeySet contextkeyset1 = (ContextKeySet) LootContextParamSets.REGISTRY.put(identifier, contextkeyset);

        if (contextkeyset1 != null) {
            throw new IllegalStateException("Loot table parameter set " + String.valueOf(identifier) + " is already registered");
        } else {
            return contextkeyset;
        }
    }

    static {
        Codec codec = Identifier.CODEC;
        Function function = (identifier) -> {
            return (DataResult) Optional.ofNullable((ContextKeySet) LootContextParamSets.REGISTRY.get(identifier)).map(DataResult::success).orElseGet(() -> {
                return DataResult.error(() -> {
                    return "No parameter set exists with id: '" + String.valueOf(identifier) + "'";
                });
            });
        };
        BiMap bimap = LootContextParamSets.REGISTRY.inverse();

        Objects.requireNonNull(bimap);
        CODEC = codec.comapFlatMap(function, bimap::get);
        EMPTY = register("empty", (contextkeyset_builder) -> {
        });
        CHEST = register("chest", (contextkeyset_builder) -> {
            contextkeyset_builder.required(LootContextParams.ORIGIN).optional(LootContextParams.THIS_ENTITY);
        });
        COMMAND = register("command", (contextkeyset_builder) -> {
            contextkeyset_builder.required(LootContextParams.ORIGIN).optional(LootContextParams.THIS_ENTITY);
        });
        SELECTOR = register("selector", (contextkeyset_builder) -> {
            contextkeyset_builder.required(LootContextParams.ORIGIN).required(LootContextParams.THIS_ENTITY);
        });
        FISHING = register("fishing", (contextkeyset_builder) -> {
            contextkeyset_builder.required(LootContextParams.ORIGIN).required(LootContextParams.TOOL).optional(LootContextParams.THIS_ENTITY);
        });
        ENTITY = register("entity", (contextkeyset_builder) -> {
            contextkeyset_builder.required(LootContextParams.THIS_ENTITY).required(LootContextParams.ORIGIN).required(LootContextParams.DAMAGE_SOURCE).optional(LootContextParams.ATTACKING_ENTITY).optional(LootContextParams.DIRECT_ATTACKING_ENTITY).optional(LootContextParams.LAST_DAMAGE_PLAYER);
        });
        EQUIPMENT = register("equipment", (contextkeyset_builder) -> {
            contextkeyset_builder.required(LootContextParams.ORIGIN).required(LootContextParams.THIS_ENTITY);
        });
        ARCHAEOLOGY = register("archaeology", (contextkeyset_builder) -> {
            contextkeyset_builder.required(LootContextParams.ORIGIN).required(LootContextParams.THIS_ENTITY).required(LootContextParams.TOOL);
        });
        GIFT = register("gift", (contextkeyset_builder) -> {
            contextkeyset_builder.required(LootContextParams.ORIGIN).required(LootContextParams.THIS_ENTITY);
        });
        PIGLIN_BARTER = register("barter", (contextkeyset_builder) -> {
            contextkeyset_builder.required(LootContextParams.THIS_ENTITY);
        });
        VAULT = register("vault", (contextkeyset_builder) -> {
            contextkeyset_builder.required(LootContextParams.ORIGIN).optional(LootContextParams.THIS_ENTITY).optional(LootContextParams.TOOL);
        });
        ADVANCEMENT_REWARD = register("advancement_reward", (contextkeyset_builder) -> {
            contextkeyset_builder.required(LootContextParams.THIS_ENTITY).required(LootContextParams.ORIGIN);
        });
        ADVANCEMENT_ENTITY = register("advancement_entity", (contextkeyset_builder) -> {
            contextkeyset_builder.required(LootContextParams.THIS_ENTITY).required(LootContextParams.ORIGIN);
        });
        ADVANCEMENT_LOCATION = register("advancement_location", (contextkeyset_builder) -> {
            contextkeyset_builder.required(LootContextParams.THIS_ENTITY).required(LootContextParams.ORIGIN).required(LootContextParams.TOOL).required(LootContextParams.BLOCK_STATE);
        });
        BLOCK_USE = register("block_use", (contextkeyset_builder) -> {
            contextkeyset_builder.required(LootContextParams.THIS_ENTITY).required(LootContextParams.ORIGIN).required(LootContextParams.BLOCK_STATE);
        });
        ALL_PARAMS = register("generic", (contextkeyset_builder) -> {
            contextkeyset_builder.required(LootContextParams.THIS_ENTITY).required(LootContextParams.LAST_DAMAGE_PLAYER).required(LootContextParams.DAMAGE_SOURCE).required(LootContextParams.ATTACKING_ENTITY).required(LootContextParams.DIRECT_ATTACKING_ENTITY).required(LootContextParams.ORIGIN).required(LootContextParams.BLOCK_STATE).required(LootContextParams.BLOCK_ENTITY).required(LootContextParams.TOOL).required(LootContextParams.EXPLOSION_RADIUS);
        });
        BLOCK = register("block", (contextkeyset_builder) -> {
            contextkeyset_builder.required(LootContextParams.BLOCK_STATE).required(LootContextParams.ORIGIN).required(LootContextParams.TOOL).optional(LootContextParams.THIS_ENTITY).optional(LootContextParams.BLOCK_ENTITY).optional(LootContextParams.EXPLOSION_RADIUS);
        });
        SHEARING = register("shearing", (contextkeyset_builder) -> {
            contextkeyset_builder.required(LootContextParams.ORIGIN).required(LootContextParams.THIS_ENTITY).required(LootContextParams.TOOL);
        });
        ENTITY_INTERACT = register("entity_interact", (contextkeyset_builder) -> {
            contextkeyset_builder.required(LootContextParams.TARGET_ENTITY).optional(LootContextParams.INTERACTING_ENTITY).required(LootContextParams.TOOL);
        });
        BLOCK_INTERACT = register("block_interact", (contextkeyset_builder) -> {
            contextkeyset_builder.required(LootContextParams.BLOCK_STATE).optional(LootContextParams.BLOCK_ENTITY).optional(LootContextParams.INTERACTING_ENTITY).optional(LootContextParams.TOOL);
        });
        ENCHANTED_DAMAGE = register("enchanted_damage", (contextkeyset_builder) -> {
            contextkeyset_builder.required(LootContextParams.THIS_ENTITY).required(LootContextParams.ENCHANTMENT_LEVEL).required(LootContextParams.ORIGIN).required(LootContextParams.DAMAGE_SOURCE).optional(LootContextParams.DIRECT_ATTACKING_ENTITY).optional(LootContextParams.ATTACKING_ENTITY);
        });
        ENCHANTED_ITEM = register("enchanted_item", (contextkeyset_builder) -> {
            contextkeyset_builder.required(LootContextParams.TOOL).required(LootContextParams.ENCHANTMENT_LEVEL);
        });
        ENCHANTED_LOCATION = register("enchanted_location", (contextkeyset_builder) -> {
            contextkeyset_builder.required(LootContextParams.THIS_ENTITY).required(LootContextParams.ENCHANTMENT_LEVEL).required(LootContextParams.ORIGIN).required(LootContextParams.ENCHANTMENT_ACTIVE);
        });
        ENCHANTED_ENTITY = register("enchanted_entity", (contextkeyset_builder) -> {
            contextkeyset_builder.required(LootContextParams.THIS_ENTITY).required(LootContextParams.ENCHANTMENT_LEVEL).required(LootContextParams.ORIGIN);
        });
        HIT_BLOCK = register("hit_block", (contextkeyset_builder) -> {
            contextkeyset_builder.required(LootContextParams.THIS_ENTITY).required(LootContextParams.ENCHANTMENT_LEVEL).required(LootContextParams.ORIGIN).required(LootContextParams.BLOCK_STATE);
        });
    }
}
