package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Lists;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.Dynamic;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public class EntityEquipmentToArmorAndHandFix extends DataFix {

    public EntityEquipmentToArmorAndHandFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    public TypeRewriteRule makeRule() {
        return this.cap(this.getInputSchema().getTypeRaw(References.ITEM_STACK), this.getOutputSchema().getTypeRaw(References.ITEM_STACK));
    }

    private <ItemStackOld, ItemStackNew> TypeRewriteRule cap(Type<ItemStackOld> oldItemStackType, Type<ItemStackNew> newItemStackType) {
        Type<Pair<String, Either<List<ItemStackOld>, Unit>>> type2 = DSL.named(References.ENTITY_EQUIPMENT.typeName(), DSL.optional(DSL.field("Equipment", DSL.list(oldItemStackType))));
        Type<Pair<String, Pair<Either<List<ItemStackNew>, Unit>, Pair<Either<List<ItemStackNew>, Unit>, Pair<Either<ItemStackNew, Unit>, Either<ItemStackNew, Unit>>>>>> type3 = DSL.named(References.ENTITY_EQUIPMENT.typeName(), DSL.and(DSL.optional(DSL.field("ArmorItems", DSL.list(newItemStackType))), DSL.optional(DSL.field("HandItems", DSL.list(newItemStackType))), DSL.optional(DSL.field("body_armor_item", newItemStackType)), DSL.optional(DSL.field("saddle", newItemStackType))));

        if (!type2.equals(this.getInputSchema().getType(References.ENTITY_EQUIPMENT))) {
            throw new IllegalStateException("Input entity_equipment type does not match expected");
        } else if (!type3.equals(this.getOutputSchema().getType(References.ENTITY_EQUIPMENT))) {
            throw new IllegalStateException("Output entity_equipment type does not match expected");
        } else {
            return TypeRewriteRule.seq(this.fixTypeEverywhereTyped("EntityEquipmentToArmorAndHandFix - drop chances", this.getInputSchema().getType(References.ENTITY), (typed) -> {
                return typed.update(DSL.remainderFinder(), EntityEquipmentToArmorAndHandFix::fixDropChances);
            }), this.fixTypeEverywhere("EntityEquipmentToArmorAndHandFix - equipment", type2, type3, (dynamicops) -> {
                ItemStackNew itemstacknew = (ItemStackNew) ((Pair) newItemStackType.read((new Dynamic(dynamicops)).emptyMap()).result().orElseThrow(() -> {
                    return new IllegalStateException("Could not parse newly created empty itemstack.");
                })).getFirst();
                Either<ItemStackNew, Unit> either = Either.right(DSL.unit());

                return (pair) -> {
                    return pair.mapSecond((either1) -> {
                        List<ItemStackOld> list = (List) either1.map(Function.identity(), (unit) -> {
                            return List.of();
                        });
                        Either<List<ItemStackNew>, Unit> either2 = Either.right(DSL.unit());
                        Either<List<ItemStackNew>, Unit> either3 = Either.right(DSL.unit());

                        if (!list.isEmpty()) {
                            either2 = Either.left(Lists.newArrayList(new Object[]{list.getFirst(), itemstacknew}));
                        }

                        if (list.size() > 1) {
                            List<ItemStackNew> list1 = Lists.newArrayList(new Object[]{itemstacknew, itemstacknew, itemstacknew, itemstacknew});

                            for (int i = 1; i < Math.min(list.size(), 5); ++i) {
                                list1.set(i - 1, list.get(i));
                            }

                            either3 = Either.left(list1);
                        }

                        return Pair.of(either3, Pair.of(either2, Pair.of(either, either)));
                    });
                };
            }));
        }
    }

    private static Dynamic<?> fixDropChances(Dynamic<?> tag) {
        Optional<? extends Stream<? extends Dynamic<?>>> optional = tag.get("DropChances").asStreamOpt().result();

        tag = tag.remove("DropChances");
        if (optional.isPresent()) {
            Iterator<Float> iterator = Stream.concat(((Stream) optional.get()).map((dynamic1) -> {
                return dynamic1.asFloat(0.0F);
            }), Stream.generate(() -> {
                return 0.0F;
            })).iterator();
            float f = (Float) iterator.next();

            if (tag.get("HandDropChances").result().isEmpty()) {
                Stream stream = Stream.of(f, 0.0F);

                Objects.requireNonNull(tag);
                tag = tag.set("HandDropChances", tag.createList(stream.map(tag::createFloat)));
            }

            if (tag.get("ArmorDropChances").result().isEmpty()) {
                Stream stream1 = Stream.of((Float) iterator.next(), (Float) iterator.next(), (Float) iterator.next(), (Float) iterator.next());

                Objects.requireNonNull(tag);
                tag = tag.set("ArmorDropChances", tag.createList(stream1.map(tag::createFloat)));
            }
        }

        return tag;
    }
}
