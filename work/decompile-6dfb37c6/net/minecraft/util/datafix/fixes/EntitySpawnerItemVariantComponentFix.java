package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.ExtraDataFixUtils;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class EntitySpawnerItemVariantComponentFix extends DataFix {

    public EntitySpawnerItemVariantComponentFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    public final TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<Pair<String, String>> opticfinder = DSL.fieldFinder("id", DSL.named(References.ITEM_NAME.typeName(), NamespacedSchema.namespacedString()));
        OpticFinder<?> opticfinder1 = type.findField("components");

        return this.fixTypeEverywhereTyped("ItemStack bucket_entity_data variants to separate components", type, (typed) -> {
            Typed typed1;

            switch ((String) typed.getOptional(opticfinder).map(Pair::getSecond).orElse("")) {
                case "minecraft:salmon_bucket":
                    typed1 = typed.updateTyped(opticfinder1, EntitySpawnerItemVariantComponentFix::fixSalmonBucket);
                    break;
                case "minecraft:axolotl_bucket":
                    typed1 = typed.updateTyped(opticfinder1, EntitySpawnerItemVariantComponentFix::fixAxolotlBucket);
                    break;
                case "minecraft:tropical_fish_bucket":
                    typed1 = typed.updateTyped(opticfinder1, EntitySpawnerItemVariantComponentFix::fixTropicalFishBucket);
                    break;
                case "minecraft:painting":
                    typed1 = typed.updateTyped(opticfinder1, (typed2) -> {
                        return Util.writeAndReadTypedOrThrow(typed2, typed2.getType(), EntitySpawnerItemVariantComponentFix::fixPainting);
                    });
                    break;
                default:
                    typed1 = typed;
            }

            return typed1;
        });
    }

    private static String getBaseColor(int packedVariant) {
        return ExtraDataFixUtils.dyeColorIdToName(packedVariant >> 16 & 255);
    }

    private static String getPatternColor(int packedVariant) {
        return ExtraDataFixUtils.dyeColorIdToName(packedVariant >> 24 & 255);
    }

    private static String getPattern(int packedVariant) {
        String s;

        switch (packedVariant & 65535) {
            case 1:
                s = "flopper";
                break;
            case 256:
                s = "sunstreak";
                break;
            case 257:
                s = "stripey";
                break;
            case 512:
                s = "snooper";
                break;
            case 513:
                s = "glitter";
                break;
            case 768:
                s = "dasher";
                break;
            case 769:
                s = "blockfish";
                break;
            case 1024:
                s = "brinely";
                break;
            case 1025:
                s = "betty";
                break;
            case 1280:
                s = "spotty";
                break;
            case 1281:
                s = "clayfish";
                break;
            default:
                s = "kob";
        }

        return s;
    }

    private static <T> Dynamic<T> fixTropicalFishBucket(Dynamic<T> remainder, Dynamic<T> bucketData) {
        Optional<Number> optional = bucketData.get("BucketVariantTag").asNumber().result();

        if (optional.isEmpty()) {
            return remainder;
        } else {
            int i = ((Number) optional.get()).intValue();
            String s = getPattern(i);
            String s1 = getBaseColor(i);
            String s2 = getPatternColor(i);

            return remainder.update("minecraft:bucket_entity_data", (dynamic2) -> {
                return dynamic2.remove("BucketVariantTag");
            }).set("minecraft:tropical_fish/pattern", remainder.createString(s)).set("minecraft:tropical_fish/base_color", remainder.createString(s1)).set("minecraft:tropical_fish/pattern_color", remainder.createString(s2));
        }
    }

    private static <T> Dynamic<T> fixAxolotlBucket(Dynamic<T> remainder, Dynamic<T> bucketData) {
        Optional<Number> optional = bucketData.get("Variant").asNumber().result();

        if (optional.isEmpty()) {
            return remainder;
        } else {
            String s;

            switch (((Number) optional.get()).intValue()) {
                case 1:
                    s = "wild";
                    break;
                case 2:
                    s = "gold";
                    break;
                case 3:
                    s = "cyan";
                    break;
                case 4:
                    s = "blue";
                    break;
                default:
                    s = "lucy";
            }

            String s1 = s;

            return remainder.update("minecraft:bucket_entity_data", (dynamic2) -> {
                return dynamic2.remove("Variant");
            }).set("minecraft:axolotl/variant", remainder.createString(s1));
        }
    }

    private static <T> Dynamic<T> fixSalmonBucket(Dynamic<T> remainder, Dynamic<T> bucketData) {
        Optional<Dynamic<T>> optional = bucketData.get("type").result();

        return optional.isEmpty() ? remainder : remainder.update("minecraft:bucket_entity_data", (dynamic2) -> {
            return dynamic2.remove("type");
        }).set("minecraft:salmon/size", (Dynamic) optional.get());
    }

    private static <T> Dynamic<T> fixPainting(Dynamic<T> components) {
        Optional<Dynamic<T>> optional = components.get("minecraft:entity_data").result();

        if (optional.isEmpty()) {
            return components;
        } else if (((Dynamic) optional.get()).get("id").asString().result().filter((s) -> {
            return s.equals("minecraft:painting");
        }).isEmpty()) {
            return components;
        } else {
            Optional<Dynamic<T>> optional1 = ((Dynamic) optional.get()).get("variant").result();
            Dynamic<T> dynamic1 = ((Dynamic) optional.get()).remove("variant");

            if (dynamic1.remove("id").equals(dynamic1.emptyMap())) {
                components = components.remove("minecraft:entity_data");
            } else {
                components = components.set("minecraft:entity_data", dynamic1);
            }

            if (optional1.isPresent()) {
                components = components.set("minecraft:painting/variant", (Dynamic) optional1.get());
            }

            return components;
        }
    }

    @FunctionalInterface
    private interface Fixer extends Function<Typed<?>, Typed<?>> {

        default Typed<?> apply(Typed<?> components) {
            return components.update(DSL.remainderFinder(), this::fixRemainder);
        }

        default <T> Dynamic<T> fixRemainder(Dynamic<T> remainder) {
            return (Dynamic) remainder.get("minecraft:bucket_entity_data").result().map((dynamic1) -> {
                return this.fixRemainder(remainder, dynamic1);
            }).orElse(remainder);
        }

        <T> Dynamic<T> fixRemainder(Dynamic<T> remainder, Dynamic<T> bucketData);
    }
}
