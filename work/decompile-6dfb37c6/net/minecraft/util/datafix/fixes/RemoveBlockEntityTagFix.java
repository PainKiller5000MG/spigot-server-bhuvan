package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.List.ListType;
import java.util.Optional;
import java.util.Set;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class RemoveBlockEntityTagFix extends DataFix {

    private final Set<String> blockEntityIdsToDrop;

    public RemoveBlockEntityTagFix(Schema outputSchema, Set<String> blockEntityIdsToDrop) {
        super(outputSchema, true);
        this.blockEntityIdsToDrop = blockEntityIdsToDrop;
    }

    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<?> opticfinder = type.findField("tag");
        OpticFinder<?> opticfinder1 = opticfinder.type().findField("BlockEntityTag");
        Type<?> type1 = this.getInputSchema().getType(References.ENTITY);
        OpticFinder<?> opticfinder2 = DSL.namedChoice("minecraft:falling_block", this.getInputSchema().getChoiceType(References.ENTITY, "minecraft:falling_block"));
        OpticFinder<?> opticfinder3 = opticfinder2.type().findField("TileEntityData");
        Type<?> type2 = this.getInputSchema().getType(References.STRUCTURE);
        OpticFinder<?> opticfinder4 = type2.findField("blocks");
        OpticFinder<?> opticfinder5 = DSL.typeFinder(((ListType) opticfinder4.type()).getElement());
        OpticFinder<?> opticfinder6 = opticfinder5.type().findField("nbt");
        OpticFinder<String> opticfinder7 = DSL.fieldFinder("id", NamespacedSchema.namespacedString());

        return TypeRewriteRule.seq(this.fixTypeEverywhereTyped("ItemRemoveBlockEntityTagFix", type, (typed) -> {
            return typed.updateTyped(opticfinder, (typed1) -> {
                return this.removeBlockEntity(typed1, opticfinder1, opticfinder7, "BlockEntityTag");
            });
        }), new TypeRewriteRule[]{this.fixTypeEverywhereTyped("FallingBlockEntityRemoveBlockEntityTagFix", type1, (typed) -> {
                    return typed.updateTyped(opticfinder2, (typed1) -> {
                        return this.removeBlockEntity(typed1, opticfinder3, opticfinder7, "TileEntityData");
                    });
                }), this.fixTypeEverywhereTyped("StructureRemoveBlockEntityTagFix", type2, (typed) -> {
                    return typed.updateTyped(opticfinder4, (typed1) -> {
                        return typed1.updateTyped(opticfinder5, (typed2) -> {
                            return this.removeBlockEntity(typed2, opticfinder6, opticfinder7, "nbt");
                        });
                    });
                }), this.convertUnchecked("ItemRemoveBlockEntityTagFix - update block entity type", this.getInputSchema().getType(References.BLOCK_ENTITY), this.getOutputSchema().getType(References.BLOCK_ENTITY))});
    }

    private Typed<?> removeBlockEntity(Typed<?> tag, OpticFinder<?> blockEntityF, OpticFinder<String> blockEntityIdF, String blockEntityFieldName) {
        Optional<? extends Typed<?>> optional = tag.getOptionalTyped(blockEntityF);

        if (optional.isEmpty()) {
            return tag;
        } else {
            String s1 = (String) ((Typed) optional.get()).getOptional(blockEntityIdF).orElse("");

            return !this.blockEntityIdsToDrop.contains(s1) ? tag : Util.writeAndReadTypedOrThrow(tag, tag.getType(), (dynamic) -> {
                return dynamic.remove(blockEntityFieldName);
            });
        }
    }
}
