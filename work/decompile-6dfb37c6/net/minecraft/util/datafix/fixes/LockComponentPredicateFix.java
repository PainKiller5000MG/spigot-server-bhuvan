package net.minecraft.util.datafix.fixes;

import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public class LockComponentPredicateFix extends DataComponentRemainderFix {

    public static final Escaper ESCAPER = Escapers.builder().addEscape('"', "\\\"").addEscape('\\', "\\\\").build();

    public LockComponentPredicateFix(Schema outputSchema) {
        super(outputSchema, "LockComponentPredicateFix", "minecraft:lock");
    }

    @Override
    protected <T> @Nullable Dynamic<T> fixComponent(Dynamic<T> input) {
        return fixLock(input);
    }

    public static <T> @Nullable Dynamic<T> fixLock(Dynamic<T> input) {
        Optional<String> optional = input.asString().result();

        if (optional.isEmpty()) {
            return null;
        } else if (((String) optional.get()).isEmpty()) {
            return null;
        } else {
            Escaper escaper = LockComponentPredicateFix.ESCAPER;
            Dynamic<T> dynamic1 = input.createString("\"" + escaper.escape((String) optional.get()) + "\"");
            Dynamic<T> dynamic2 = input.emptyMap().set("minecraft:custom_name", dynamic1);

            return input.emptyMap().set("components", dynamic2);
        }
    }
}
