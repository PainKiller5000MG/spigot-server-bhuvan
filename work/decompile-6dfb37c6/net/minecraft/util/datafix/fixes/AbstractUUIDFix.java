package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public abstract class AbstractUUIDFix extends DataFix {

    protected TypeReference typeReference;

    public AbstractUUIDFix(Schema outputSchema, TypeReference typeReference) {
        super(outputSchema, false);
        this.typeReference = typeReference;
    }

    protected Typed<?> updateNamedChoice(Typed<?> input, String name, Function<Dynamic<?>, Dynamic<?>> function) {
        Type<?> type = this.getInputSchema().getChoiceType(this.typeReference, name);
        Type<?> type1 = this.getOutputSchema().getChoiceType(this.typeReference, name);

        return input.updateTyped(DSL.namedChoice(name, type), type1, (typed1) -> {
            return typed1.update(DSL.remainderFinder(), function);
        });
    }

    protected static Optional<Dynamic<?>> replaceUUIDString(Dynamic<?> tag, String oldKey, String newKey) {
        return createUUIDFromString(tag, oldKey).map((dynamic1) -> {
            return tag.remove(oldKey).set(newKey, dynamic1);
        });
    }

    protected static Optional<Dynamic<?>> replaceUUIDMLTag(Dynamic<?> tag, String oldKey, String newKey) {
        return tag.get(oldKey).result().flatMap(AbstractUUIDFix::createUUIDFromML).map((dynamic1) -> {
            return tag.remove(oldKey).set(newKey, dynamic1);
        });
    }

    protected static Optional<Dynamic<?>> replaceUUIDLeastMost(Dynamic<?> tag, String oldKey, String newKey) {
        String s2 = oldKey + "Most";
        String s3 = oldKey + "Least";

        return createUUIDFromLongs(tag, s2, s3).map((dynamic1) -> {
            return tag.remove(s2).remove(s3).set(newKey, dynamic1);
        });
    }

    protected static Optional<Dynamic<?>> createUUIDFromString(Dynamic<?> tag, String oldKey) {
        return tag.get(oldKey).result().flatMap((dynamic1) -> {
            String s1 = dynamic1.asString((String) null);

            if (s1 != null) {
                try {
                    UUID uuid = UUID.fromString(s1);

                    return createUUIDTag(tag, uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
                } catch (IllegalArgumentException illegalargumentexception) {
                    ;
                }
            }

            return Optional.empty();
        });
    }

    protected static Optional<Dynamic<?>> createUUIDFromML(Dynamic<?> tag) {
        return createUUIDFromLongs(tag, "M", "L");
    }

    protected static Optional<Dynamic<?>> createUUIDFromLongs(Dynamic<?> tag, String mostKey, String leastKey) {
        long i = tag.get(mostKey).asLong(0L);
        long j = tag.get(leastKey).asLong(0L);

        return i != 0L && j != 0L ? createUUIDTag(tag, i, j) : Optional.empty();
    }

    protected static Optional<Dynamic<?>> createUUIDTag(Dynamic<?> tag, long mostSignificantBits, long leastSignificantBits) {
        return Optional.of(tag.createIntList(Arrays.stream(new int[]{(int) (mostSignificantBits >> 32), (int) mostSignificantBits, (int) (leastSignificantBits >> 32), (int) leastSignificantBits})));
    }
}
