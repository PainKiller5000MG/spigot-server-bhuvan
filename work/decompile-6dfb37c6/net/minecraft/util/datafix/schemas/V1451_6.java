package net.minecraft.util.datafix.schemas;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.Hook.HookFunction;
import com.mojang.datafixers.types.templates.TypeTemplate;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.fixes.References;

public class V1451_6 extends NamespacedSchema {

    public static final String SPECIAL_OBJECTIVE_MARKER = "_special";
    protected static final HookFunction UNPACK_OBJECTIVE_ID = new HookFunction() {
        public <T> T apply(DynamicOps<T> ops, T value) {
            Dynamic<T> dynamic = new Dynamic(ops, value);

            return (T) ((Dynamic) DataFixUtils.orElse(dynamic.get("CriteriaName").asString().result().map((s) -> {
                int i = s.indexOf(58);

                if (i < 0) {
                    return Pair.of("_special", s);
                } else {
                    try {
                        Identifier identifier = Identifier.bySeparator(s.substring(0, i), '.');
                        Identifier identifier1 = Identifier.bySeparator(s.substring(i + 1), '.');

                        return Pair.of(identifier.toString(), identifier1.toString());
                    } catch (Exception exception) {
                        return Pair.of("_special", s);
                    }
                }
            }).map((pair) -> {
                return dynamic.set("CriteriaType", dynamic.createMap(ImmutableMap.of(dynamic.createString("type"), dynamic.createString((String) pair.getFirst()), dynamic.createString("id"), dynamic.createString((String) pair.getSecond()))));
            }), dynamic)).getValue();
        }
    };
    protected static final HookFunction REPACK_OBJECTIVE_ID = new HookFunction() {
        public <T> T apply(DynamicOps<T> ops, T value) {
            Dynamic<T> dynamic = new Dynamic(ops, value);
            Optional<Dynamic<T>> optional = dynamic.get("CriteriaType").get().result().flatMap((dynamic1) -> {
                Optional<String> optional1 = dynamic1.get("type").asString().result();
                Optional<String> optional2 = dynamic1.get("id").asString().result();

                if (optional1.isPresent() && optional2.isPresent()) {
                    String s = (String) optional1.get();

                    if (s.equals("_special")) {
                        return Optional.of(dynamic.createString((String) optional2.get()));
                    } else {
                        String s1 = V1451_6.packNamespacedWithDot(s);

                        return Optional.of(dynamic1.createString(s1 + ":" + V1451_6.packNamespacedWithDot((String) optional2.get())));
                    }
                } else {
                    return Optional.empty();
                }
            });

            return (T) ((Dynamic) DataFixUtils.orElse(optional.map((dynamic1) -> {
                return dynamic.set("CriteriaName", dynamic1).remove("CriteriaType");
            }), dynamic)).getValue();
        }
    };

    public V1451_6(int versionKey, Schema parent) {
        super(versionKey, parent);
    }

    public void registerTypes(Schema schema, Map<String, Supplier<TypeTemplate>> entityTypes, Map<String, Supplier<TypeTemplate>> blockEntityTypes) {
        super.registerTypes(schema, entityTypes, blockEntityTypes);
        Supplier<TypeTemplate> supplier = () -> {
            return DSL.compoundList(References.ITEM_NAME.in(schema), DSL.constType(DSL.intType()));
        };

        schema.registerType(false, References.STATS, () -> {
            return DSL.optionalFields("stats", DSL.optionalFields(new Pair[]{Pair.of("minecraft:mined", DSL.compoundList(References.BLOCK_NAME.in(schema), DSL.constType(DSL.intType()))), Pair.of("minecraft:crafted", (TypeTemplate) supplier.get()), Pair.of("minecraft:used", (TypeTemplate) supplier.get()), Pair.of("minecraft:broken", (TypeTemplate) supplier.get()), Pair.of("minecraft:picked_up", (TypeTemplate) supplier.get()), Pair.of("minecraft:dropped", (TypeTemplate) supplier.get()), Pair.of("minecraft:killed", DSL.compoundList(References.ENTITY_NAME.in(schema), DSL.constType(DSL.intType()))), Pair.of("minecraft:killed_by", DSL.compoundList(References.ENTITY_NAME.in(schema), DSL.constType(DSL.intType()))), Pair.of("minecraft:custom", DSL.compoundList(DSL.constType(namespacedString()), DSL.constType(DSL.intType())))}));
        });
        Map<String, Supplier<TypeTemplate>> map2 = createCriterionTypes(schema);

        schema.registerType(false, References.OBJECTIVE, () -> {
            return DSL.hook(DSL.optionalFields("CriteriaType", DSL.taggedChoiceLazy("type", DSL.string(), map2), "DisplayName", References.TEXT_COMPONENT.in(schema)), V1451_6.UNPACK_OBJECTIVE_ID, V1451_6.REPACK_OBJECTIVE_ID);
        });
    }

    protected static Map<String, Supplier<TypeTemplate>> createCriterionTypes(Schema schema) {
        Supplier<TypeTemplate> supplier = () -> {
            return DSL.optionalFields("id", References.ITEM_NAME.in(schema));
        };
        Supplier<TypeTemplate> supplier1 = () -> {
            return DSL.optionalFields("id", References.BLOCK_NAME.in(schema));
        };
        Supplier<TypeTemplate> supplier2 = () -> {
            return DSL.optionalFields("id", References.ENTITY_NAME.in(schema));
        };
        Map<String, Supplier<TypeTemplate>> map = Maps.newHashMap();

        map.put("minecraft:mined", supplier1);
        map.put("minecraft:crafted", supplier);
        map.put("minecraft:used", supplier);
        map.put("minecraft:broken", supplier);
        map.put("minecraft:picked_up", supplier);
        map.put("minecraft:dropped", supplier);
        map.put("minecraft:killed", supplier2);
        map.put("minecraft:killed_by", supplier2);
        map.put("minecraft:custom", (Supplier) () -> {
            return DSL.optionalFields("id", DSL.constType(namespacedString()));
        });
        map.put("_special", (Supplier) () -> {
            return DSL.optionalFields("id", DSL.constType(DSL.string()));
        });
        return map;
    }

    public static String packNamespacedWithDot(String location) {
        Identifier identifier = Identifier.tryParse(location);

        return identifier != null ? identifier.getNamespace() + "." + identifier.getPath() : location;
    }
}
