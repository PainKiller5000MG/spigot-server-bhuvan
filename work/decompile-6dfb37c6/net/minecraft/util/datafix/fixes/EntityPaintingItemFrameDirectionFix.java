package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;

public class EntityPaintingItemFrameDirectionFix extends DataFix {

    private static final int[][] DIRECTIONS = new int[][]{{0, 0, 1}, {-1, 0, 0}, {0, 0, -1}, {1, 0, 0}};

    public EntityPaintingItemFrameDirectionFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    private Dynamic<?> doFix(Dynamic<?> input, boolean isPainting, boolean isItemFrame) {
        if ((isPainting || isItemFrame) && input.get("Facing").asNumber().result().isEmpty()) {
            int i;

            if (input.get("Direction").asNumber().result().isPresent()) {
                i = input.get("Direction").asByte((byte) 0) % EntityPaintingItemFrameDirectionFix.DIRECTIONS.length;
                int[] aint = EntityPaintingItemFrameDirectionFix.DIRECTIONS[i];

                input = input.set("TileX", input.createInt(input.get("TileX").asInt(0) + aint[0]));
                input = input.set("TileY", input.createInt(input.get("TileY").asInt(0) + aint[1]));
                input = input.set("TileZ", input.createInt(input.get("TileZ").asInt(0) + aint[2]));
                input = input.remove("Direction");
                if (isItemFrame && input.get("ItemRotation").asNumber().result().isPresent()) {
                    input = input.set("ItemRotation", input.createByte((byte) (input.get("ItemRotation").asByte((byte) 0) * 2)));
                }
            } else {
                i = input.get("Dir").asByte((byte) 0) % EntityPaintingItemFrameDirectionFix.DIRECTIONS.length;
                input = input.remove("Dir");
            }

            input = input.set("Facing", input.createByte((byte) i));
        }

        return input;
    }

    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getChoiceType(References.ENTITY, "Painting");
        OpticFinder<?> opticfinder = DSL.namedChoice("Painting", type);
        Type<?> type1 = this.getInputSchema().getChoiceType(References.ENTITY, "ItemFrame");
        OpticFinder<?> opticfinder1 = DSL.namedChoice("ItemFrame", type1);
        Type<?> type2 = this.getInputSchema().getType(References.ENTITY);
        TypeRewriteRule typerewriterule = this.fixTypeEverywhereTyped("EntityPaintingFix", type2, (typed) -> {
            return typed.updateTyped(opticfinder, type, (typed1) -> {
                return typed1.update(DSL.remainderFinder(), (dynamic) -> {
                    return this.doFix(dynamic, true, false);
                });
            });
        });
        TypeRewriteRule typerewriterule1 = this.fixTypeEverywhereTyped("EntityItemFrameFix", type2, (typed) -> {
            return typed.updateTyped(opticfinder1, type1, (typed1) -> {
                return typed1.update(DSL.remainderFinder(), (dynamic) -> {
                    return this.doFix(dynamic, false, true);
                });
            });
        });

        return TypeRewriteRule.seq(typerewriterule, typerewriterule1);
    }
}
