package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.OptionalDynamic;
import java.util.Arrays;
import java.util.function.Function;

public class EntityProjectileOwnerFix extends DataFix {

    public EntityProjectileOwnerFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    protected TypeRewriteRule makeRule() {
        Schema schema = this.getInputSchema();

        return this.fixTypeEverywhereTyped("EntityProjectileOwner", schema.getType(References.ENTITY), this::updateProjectiles);
    }

    private Typed<?> updateProjectiles(Typed<?> input) {
        input = this.updateEntity(input, "minecraft:egg", this::updateOwnerThrowable);
        input = this.updateEntity(input, "minecraft:ender_pearl", this::updateOwnerThrowable);
        input = this.updateEntity(input, "minecraft:experience_bottle", this::updateOwnerThrowable);
        input = this.updateEntity(input, "minecraft:snowball", this::updateOwnerThrowable);
        input = this.updateEntity(input, "minecraft:potion", this::updateOwnerThrowable);
        input = this.updateEntity(input, "minecraft:llama_spit", this::updateOwnerLlamaSpit);
        input = this.updateEntity(input, "minecraft:arrow", this::updateOwnerArrow);
        input = this.updateEntity(input, "minecraft:spectral_arrow", this::updateOwnerArrow);
        input = this.updateEntity(input, "minecraft:trident", this::updateOwnerArrow);
        return input;
    }

    private Dynamic<?> updateOwnerArrow(Dynamic<?> tag) {
        long i = tag.get("OwnerUUIDMost").asLong(0L);
        long j = tag.get("OwnerUUIDLeast").asLong(0L);

        return this.setUUID(tag, i, j).remove("OwnerUUIDMost").remove("OwnerUUIDLeast");
    }

    private Dynamic<?> updateOwnerLlamaSpit(Dynamic<?> tag) {
        OptionalDynamic<?> optionaldynamic = tag.get("Owner");
        long i = optionaldynamic.get("OwnerUUIDMost").asLong(0L);
        long j = optionaldynamic.get("OwnerUUIDLeast").asLong(0L);

        return this.setUUID(tag, i, j).remove("Owner");
    }

    private Dynamic<?> updateOwnerThrowable(Dynamic<?> tag) {
        String s = "owner";
        OptionalDynamic<?> optionaldynamic = tag.get("owner");
        long i = optionaldynamic.get("M").asLong(0L);
        long j = optionaldynamic.get("L").asLong(0L);

        return this.setUUID(tag, i, j).remove("owner");
    }

    private Dynamic<?> setUUID(Dynamic<?> tag, long mostSignificantBits, long leastSignificantBits) {
        String s = "OwnerUUID";

        return mostSignificantBits != 0L && leastSignificantBits != 0L ? tag.set("OwnerUUID", tag.createIntList(Arrays.stream(createUUIDArray(mostSignificantBits, leastSignificantBits)))) : tag;
    }

    private static int[] createUUIDArray(long mostSignificantBits, long leastSignificantBits) {
        return new int[]{(int) (mostSignificantBits >> 32), (int) mostSignificantBits, (int) (leastSignificantBits >> 32), (int) leastSignificantBits};
    }

    private Typed<?> updateEntity(Typed<?> input, String name, Function<Dynamic<?>, Dynamic<?>> function) {
        Type<?> type = this.getInputSchema().getChoiceType(References.ENTITY, name);
        Type<?> type1 = this.getOutputSchema().getChoiceType(References.ENTITY, name);

        return input.updateTyped(DSL.namedChoice(name, type), type1, (typed1) -> {
            return typed1.update(DSL.remainderFinder(), function);
        });
    }
}
