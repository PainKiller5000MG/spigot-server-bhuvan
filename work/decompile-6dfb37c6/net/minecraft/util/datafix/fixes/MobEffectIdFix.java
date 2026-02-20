package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class MobEffectIdFix extends DataFix {

    private static final Int2ObjectMap<String> ID_MAP = (Int2ObjectMap) Util.make(new Int2ObjectOpenHashMap(), (int2objectopenhashmap) -> {
        int2objectopenhashmap.put(1, "minecraft:speed");
        int2objectopenhashmap.put(2, "minecraft:slowness");
        int2objectopenhashmap.put(3, "minecraft:haste");
        int2objectopenhashmap.put(4, "minecraft:mining_fatigue");
        int2objectopenhashmap.put(5, "minecraft:strength");
        int2objectopenhashmap.put(6, "minecraft:instant_health");
        int2objectopenhashmap.put(7, "minecraft:instant_damage");
        int2objectopenhashmap.put(8, "minecraft:jump_boost");
        int2objectopenhashmap.put(9, "minecraft:nausea");
        int2objectopenhashmap.put(10, "minecraft:regeneration");
        int2objectopenhashmap.put(11, "minecraft:resistance");
        int2objectopenhashmap.put(12, "minecraft:fire_resistance");
        int2objectopenhashmap.put(13, "minecraft:water_breathing");
        int2objectopenhashmap.put(14, "minecraft:invisibility");
        int2objectopenhashmap.put(15, "minecraft:blindness");
        int2objectopenhashmap.put(16, "minecraft:night_vision");
        int2objectopenhashmap.put(17, "minecraft:hunger");
        int2objectopenhashmap.put(18, "minecraft:weakness");
        int2objectopenhashmap.put(19, "minecraft:poison");
        int2objectopenhashmap.put(20, "minecraft:wither");
        int2objectopenhashmap.put(21, "minecraft:health_boost");
        int2objectopenhashmap.put(22, "minecraft:absorption");
        int2objectopenhashmap.put(23, "minecraft:saturation");
        int2objectopenhashmap.put(24, "minecraft:glowing");
        int2objectopenhashmap.put(25, "minecraft:levitation");
        int2objectopenhashmap.put(26, "minecraft:luck");
        int2objectopenhashmap.put(27, "minecraft:unluck");
        int2objectopenhashmap.put(28, "minecraft:slow_falling");
        int2objectopenhashmap.put(29, "minecraft:conduit_power");
        int2objectopenhashmap.put(30, "minecraft:dolphins_grace");
        int2objectopenhashmap.put(31, "minecraft:bad_omen");
        int2objectopenhashmap.put(32, "minecraft:hero_of_the_village");
        int2objectopenhashmap.put(33, "minecraft:darkness");
    });
    private static final Set<String> MOB_EFFECT_INSTANCE_CARRIER_ITEMS = Set.of("minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:tipped_arrow");

    public MobEffectIdFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    private static <T> Optional<Dynamic<T>> getAndConvertMobEffectId(Dynamic<T> obj, String fieldName) {
        Optional optional = obj.get(fieldName).asNumber().result().map((number) -> {
            return (String) MobEffectIdFix.ID_MAP.get(number.intValue());
        });

        Objects.requireNonNull(obj);
        return optional.map(obj::createString);
    }

    private static <T> Dynamic<T> updateMobEffectIdField(Dynamic<T> input, String oldFieldName, Dynamic<T> output, String newFieldName) {
        Optional<Dynamic<T>> optional = getAndConvertMobEffectId(input, oldFieldName);

        return output.replaceField(oldFieldName, newFieldName, optional);
    }

    private static <T> Dynamic<T> updateMobEffectIdField(Dynamic<T> input, String oldFieldName, String newFieldName) {
        return updateMobEffectIdField(input, oldFieldName, input, newFieldName);
    }

    private static <T> Dynamic<T> updateMobEffectInstance(Dynamic<T> input) {
        input = updateMobEffectIdField(input, "Id", "id");
        input = input.renameField("Ambient", "ambient");
        input = input.renameField("Amplifier", "amplifier");
        input = input.renameField("Duration", "duration");
        input = input.renameField("ShowParticles", "show_particles");
        input = input.renameField("ShowIcon", "show_icon");
        Optional<Dynamic<T>> optional = input.get("HiddenEffect").result().map(MobEffectIdFix::updateMobEffectInstance);

        return input.replaceField("HiddenEffect", "hidden_effect", optional);
    }

    private static <T> Dynamic<T> updateMobEffectInstanceList(Dynamic<T> input, String oldField, String newField) {
        Optional<Dynamic<T>> optional = input.get(oldField).asStreamOpt().result().map((stream) -> {
            return input.createList(stream.map(MobEffectIdFix::updateMobEffectInstance));
        });

        return input.replaceField(oldField, newField, optional);
    }

    private static <T> Dynamic<T> updateSuspiciousStewEntry(Dynamic<T> input, Dynamic<T> output) {
        output = updateMobEffectIdField(input, "EffectId", output, "id");
        Optional<Dynamic<T>> optional = input.get("EffectDuration").result();

        return output.replaceField("EffectDuration", "duration", optional);
    }

    private static <T> Dynamic<T> updateSuspiciousStewEntry(Dynamic<T> input) {
        return updateSuspiciousStewEntry(input, input);
    }

    private Typed<?> updateNamedChoice(Typed<?> input, TypeReference typeReference, String name, Function<Dynamic<?>, Dynamic<?>> function) {
        Type<?> type = this.getInputSchema().getChoiceType(typeReference, name);
        Type<?> type1 = this.getOutputSchema().getChoiceType(typeReference, name);

        return input.updateTyped(DSL.namedChoice(name, type), type1, (typed1) -> {
            return typed1.update(DSL.remainderFinder(), function);
        });
    }

    private TypeRewriteRule blockEntityFixer() {
        Type<?> type = this.getInputSchema().getType(References.BLOCK_ENTITY);

        return this.fixTypeEverywhereTyped("BlockEntityMobEffectIdFix", type, (typed) -> {
            typed = this.updateNamedChoice(typed, References.BLOCK_ENTITY, "minecraft:beacon", (dynamic) -> {
                dynamic = updateMobEffectIdField(dynamic, "Primary", "primary_effect");
                return updateMobEffectIdField(dynamic, "Secondary", "secondary_effect");
            });
            return typed;
        });
    }

    private static <T> Dynamic<T> fixMooshroomTag(Dynamic<T> entityTag) {
        Dynamic<T> dynamic1 = entityTag.emptyMap();
        Dynamic<T> dynamic2 = updateSuspiciousStewEntry(entityTag, dynamic1);

        if (!dynamic2.equals(dynamic1)) {
            entityTag = entityTag.set("stew_effects", entityTag.createList(Stream.of(dynamic2)));
        }

        return entityTag.remove("EffectId").remove("EffectDuration");
    }

    private static <T> Dynamic<T> fixArrowTag(Dynamic<T> data) {
        return updateMobEffectInstanceList(data, "CustomPotionEffects", "custom_potion_effects");
    }

    private static <T> Dynamic<T> fixAreaEffectCloudTag(Dynamic<T> data) {
        return updateMobEffectInstanceList(data, "Effects", "effects");
    }

    private static Dynamic<?> updateLivingEntityTag(Dynamic<?> data) {
        return updateMobEffectInstanceList(data, "ActiveEffects", "active_effects");
    }

    private TypeRewriteRule entityFixer() {
        Type<?> type = this.getInputSchema().getType(References.ENTITY);

        return this.fixTypeEverywhereTyped("EntityMobEffectIdFix", type, (typed) -> {
            typed = this.updateNamedChoice(typed, References.ENTITY, "minecraft:mooshroom", MobEffectIdFix::fixMooshroomTag);
            typed = this.updateNamedChoice(typed, References.ENTITY, "minecraft:arrow", MobEffectIdFix::fixArrowTag);
            typed = this.updateNamedChoice(typed, References.ENTITY, "minecraft:area_effect_cloud", MobEffectIdFix::fixAreaEffectCloudTag);
            typed = typed.update(DSL.remainderFinder(), MobEffectIdFix::updateLivingEntityTag);
            return typed;
        });
    }

    private TypeRewriteRule playerFixer() {
        Type<?> type = this.getInputSchema().getType(References.PLAYER);

        return this.fixTypeEverywhereTyped("PlayerMobEffectIdFix", type, (typed) -> {
            return typed.update(DSL.remainderFinder(), MobEffectIdFix::updateLivingEntityTag);
        });
    }

    private static <T> Dynamic<T> fixSuspiciousStewTag(Dynamic<T> tag) {
        Optional<Dynamic<T>> optional = tag.get("Effects").asStreamOpt().result().map((stream) -> {
            return tag.createList(stream.map(MobEffectIdFix::updateSuspiciousStewEntry));
        });

        return tag.replaceField("Effects", "effects", optional);
    }

    private TypeRewriteRule itemStackFixer() {
        OpticFinder<Pair<String, String>> opticfinder = DSL.fieldFinder("id", DSL.named(References.ITEM_NAME.typeName(), NamespacedSchema.namespacedString()));
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<?> opticfinder1 = type.findField("tag");

        return this.fixTypeEverywhereTyped("ItemStackMobEffectIdFix", type, (typed) -> {
            Optional<Pair<String, String>> optional = typed.getOptional(opticfinder);

            if (optional.isPresent()) {
                String s = (String) ((Pair) optional.get()).getSecond();

                if (s.equals("minecraft:suspicious_stew")) {
                    return typed.updateTyped(opticfinder1, (typed1) -> {
                        return typed1.update(DSL.remainderFinder(), MobEffectIdFix::fixSuspiciousStewTag);
                    });
                }

                if (MobEffectIdFix.MOB_EFFECT_INSTANCE_CARRIER_ITEMS.contains(s)) {
                    return typed.updateTyped(opticfinder1, (typed1) -> {
                        return typed1.update(DSL.remainderFinder(), (dynamic) -> {
                            return updateMobEffectInstanceList(dynamic, "CustomPotionEffects", "custom_potion_effects");
                        });
                    });
                }
            }

            return typed;
        });
    }

    protected TypeRewriteRule makeRule() {
        return TypeRewriteRule.seq(this.blockEntityFixer(), new TypeRewriteRule[]{this.entityFixer(), this.playerFixer(), this.itemStackFixer()});
    }
}
