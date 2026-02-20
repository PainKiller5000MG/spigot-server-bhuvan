package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;

public class BlockEntityBlockStateFix extends NamedEntityFix {

    public BlockEntityBlockStateFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "BlockEntityBlockStateFix", References.BLOCK_ENTITY, "minecraft:piston");
    }

    @Override
    protected Typed<?> fix(Typed<?> entity) {
        Type<?> type = this.getOutputSchema().getChoiceType(References.BLOCK_ENTITY, "minecraft:piston");
        Type<?> type1 = type.findFieldType("blockState");
        OpticFinder<?> opticfinder = DSL.fieldFinder("blockState", type1);
        Dynamic<?> dynamic = (Dynamic) entity.get(DSL.remainderFinder());
        int i = dynamic.get("blockId").asInt(0);

        dynamic = dynamic.remove("blockId");
        int j = dynamic.get("blockData").asInt(0) & 15;

        dynamic = dynamic.remove("blockData");
        Dynamic<?> dynamic1 = BlockStateData.getTag(i << 4 | j);
        Typed<?> typed1 = (Typed) type.pointTyped(entity.getOps()).orElseThrow(() -> {
            return new IllegalStateException("Could not create new piston block entity.");
        });

        return typed1.set(DSL.remainderFinder(), dynamic).set(opticfinder, (Typed) ((Pair) type1.readTyped(dynamic1).result().orElseThrow(() -> {
            return new IllegalStateException("Could not parse newly created block state tag.");
        })).getFirst());
    }
}
