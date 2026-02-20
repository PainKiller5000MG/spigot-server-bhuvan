package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class BlockEntityUUIDFix extends AbstractUUIDFix {

    public BlockEntityUUIDFix(Schema outputSchema) {
        super(outputSchema, References.BLOCK_ENTITY);
    }

    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped("BlockEntityUUIDFix", this.getInputSchema().getType(this.typeReference), (typed) -> {
            typed = this.updateNamedChoice(typed, "minecraft:conduit", this::updateConduit);
            typed = this.updateNamedChoice(typed, "minecraft:skull", this::updateSkull);
            return typed;
        });
    }

    private Dynamic<?> updateSkull(Dynamic<?> tag) {
        return (Dynamic) tag.get("Owner").get().map((dynamic1) -> {
            return (Dynamic) replaceUUIDString(dynamic1, "Id", "Id").orElse(dynamic1);
        }).map((dynamic1) -> {
            return tag.remove("Owner").set("SkullOwner", dynamic1);
        }).result().orElse(tag);
    }

    private Dynamic<?> updateConduit(Dynamic<?> tag) {
        return (Dynamic) replaceUUIDMLTag(tag, "target_uuid", "Target").orElse(tag);
    }
}
