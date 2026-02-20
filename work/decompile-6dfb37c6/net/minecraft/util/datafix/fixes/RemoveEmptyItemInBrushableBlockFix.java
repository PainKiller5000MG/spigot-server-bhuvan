package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class RemoveEmptyItemInBrushableBlockFix extends NamedEntityWriteReadFix {

    public RemoveEmptyItemInBrushableBlockFix(Schema outputSchema) {
        super(outputSchema, false, "RemoveEmptyItemInSuspiciousBlockFix", References.BLOCK_ENTITY, "minecraft:brushable_block");
    }

    @Override
    protected <T> Dynamic<T> fix(Dynamic<T> input) {
        Optional<Dynamic<T>> optional = input.get("item").result();

        return optional.isPresent() && isEmptyStack((Dynamic) optional.get()) ? input.remove("item") : input;
    }

    private static boolean isEmptyStack(Dynamic<?> item) {
        String s = NamespacedSchema.ensureNamespaced(item.get("id").asString("minecraft:air"));
        int i = item.get("count").asInt(0);

        return s.equals("minecraft:air") || i == 0;
    }
}
