package net.minecraft.util.datafix;

import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.RewriteResult;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.View;
import com.mojang.datafixers.functions.PointFreeRule;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.BitSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Util;

public class ExtraDataFixUtils {

    public ExtraDataFixUtils() {}

    public static Dynamic<?> fixBlockPos(Dynamic<?> pos) {
        Optional<Number> optional = pos.get("X").asNumber().result();
        Optional<Number> optional1 = pos.get("Y").asNumber().result();
        Optional<Number> optional2 = pos.get("Z").asNumber().result();

        return !optional.isEmpty() && !optional1.isEmpty() && !optional2.isEmpty() ? createBlockPos(pos, ((Number) optional.get()).intValue(), ((Number) optional1.get()).intValue(), ((Number) optional2.get()).intValue()) : pos;
    }

    public static Dynamic<?> fixInlineBlockPos(Dynamic<?> input, String fieldX, String fieldY, String fieldZ, String newField) {
        Optional<Number> optional = input.get(fieldX).asNumber().result();
        Optional<Number> optional1 = input.get(fieldY).asNumber().result();
        Optional<Number> optional2 = input.get(fieldZ).asNumber().result();

        return !optional.isEmpty() && !optional1.isEmpty() && !optional2.isEmpty() ? input.remove(fieldX).remove(fieldY).remove(fieldZ).set(newField, createBlockPos(input, ((Number) optional.get()).intValue(), ((Number) optional1.get()).intValue(), ((Number) optional2.get()).intValue())) : input;
    }

    public static Dynamic<?> createBlockPos(Dynamic<?> dynamic, int x, int y, int z) {
        return dynamic.createIntList(IntStream.of(new int[]{x, y, z}));
    }

    public static <T, R> Typed<R> cast(Type<R> type, Typed<T> typed) {
        return new Typed(type, typed.getOps(), typed.getValue());
    }

    public static <T> Typed<T> cast(Type<T> type, Object value, DynamicOps<?> ops) {
        return new Typed(type, ops, value);
    }

    public static Type<?> patchSubType(Type<?> type, Type<?> find, Type<?> replace) {
        return type.all(typePatcher(find, replace), true, false).view().newType();
    }

    private static <A, B> TypeRewriteRule typePatcher(Type<A> inputEntityType, Type<B> outputEntityType) {
        RewriteResult<A, B> rewriteresult = RewriteResult.create(View.create("Patcher", inputEntityType, outputEntityType, (dynamicops) -> {
            return (object) -> {
                throw new UnsupportedOperationException();
            };
        }), new BitSet());

        return TypeRewriteRule.everywhere(TypeRewriteRule.ifSame(inputEntityType, rewriteresult), PointFreeRule.nop(), true, true);
    }

    @SafeVarargs
    public static <T> Function<Typed<?>, Typed<?>> chainAllFilters(Function<Typed<?>, Typed<?>>... fixers) {
        return (typed) -> {
            for (Function<Typed<?>, Typed<?>> function : fixers) {
                typed = (Typed) function.apply(typed);
            }

            return typed;
        };
    }

    public static Dynamic<?> blockState(String id, Map<String, String> properties) {
        Dynamic<Tag> dynamic = new Dynamic(NbtOps.INSTANCE, new CompoundTag());
        Dynamic<Tag> dynamic1 = dynamic.set("Name", dynamic.createString(id));

        if (!properties.isEmpty()) {
            dynamic1 = dynamic1.set("Properties", dynamic.createMap((Map) properties.entrySet().stream().collect(Collectors.toMap((entry) -> {
                return dynamic.createString((String) entry.getKey());
            }, (entry) -> {
                return dynamic.createString((String) entry.getValue());
            }))));
        }

        return dynamic1;
    }

    public static Dynamic<?> blockState(String id) {
        return blockState(id, Map.of());
    }

    public static Dynamic<?> fixStringField(Dynamic<?> dynamic, String fieldName, UnaryOperator<String> fix) {
        return dynamic.update(fieldName, (dynamic1) -> {
            DataResult dataresult = dynamic1.asString().map(fix);

            Objects.requireNonNull(dynamic);
            return (Dynamic) DataFixUtils.orElse(dataresult.map(dynamic::createString).result(), dynamic1);
        });
    }

    public static String dyeColorIdToName(int id) {
        String s;

        switch (id) {
            case 1:
                s = "orange";
                break;
            case 2:
                s = "magenta";
                break;
            case 3:
                s = "light_blue";
                break;
            case 4:
                s = "yellow";
                break;
            case 5:
                s = "lime";
                break;
            case 6:
                s = "pink";
                break;
            case 7:
                s = "gray";
                break;
            case 8:
                s = "light_gray";
                break;
            case 9:
                s = "cyan";
                break;
            case 10:
                s = "purple";
                break;
            case 11:
                s = "blue";
                break;
            case 12:
                s = "brown";
                break;
            case 13:
                s = "green";
                break;
            case 14:
                s = "red";
                break;
            case 15:
                s = "black";
                break;
            default:
                s = "white";
        }

        return s;
    }

    public static <T> Typed<?> readAndSet(Typed<?> target, OpticFinder<T> optic, Dynamic<?> value) {
        return target.set(optic, Util.readTypedOrThrow(optic.type(), value, true));
    }
}
