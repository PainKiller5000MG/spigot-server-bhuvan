package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public class InlineBlockPosFormatFix extends DataFix {

    public InlineBlockPosFormatFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    public TypeRewriteRule makeRule() {
        OpticFinder<?> opticfinder = this.entityFinder("minecraft:vex");
        OpticFinder<?> opticfinder1 = this.entityFinder("minecraft:phantom");
        OpticFinder<?> opticfinder2 = this.entityFinder("minecraft:turtle");
        List<OpticFinder<?>> list = List.of(this.entityFinder("minecraft:item_frame"), this.entityFinder("minecraft:glow_item_frame"), this.entityFinder("minecraft:painting"), this.entityFinder("minecraft:leash_knot"));

        return TypeRewriteRule.seq(this.fixTypeEverywhereTyped("InlineBlockPosFormatFix - player", this.getInputSchema().getType(References.PLAYER), (typed) -> {
            return typed.update(DSL.remainderFinder(), this::fixPlayer);
        }), this.fixTypeEverywhereTyped("InlineBlockPosFormatFix - entity", this.getInputSchema().getType(References.ENTITY), (typed) -> {
            typed = typed.update(DSL.remainderFinder(), this::fixLivingEntity).updateTyped(opticfinder, (typed1) -> {
                return typed1.update(DSL.remainderFinder(), this::fixVex);
            }).updateTyped(opticfinder1, (typed1) -> {
                return typed1.update(DSL.remainderFinder(), this::fixPhantom);
            }).updateTyped(opticfinder2, (typed1) -> {
                return typed1.update(DSL.remainderFinder(), this::fixTurtle);
            });

            for (OpticFinder<?> opticfinder3 : list) {
                typed = typed.updateTyped(opticfinder3, (typed1) -> {
                    return typed1.update(DSL.remainderFinder(), this::fixBlockAttached);
                });
            }

            return typed;
        }));
    }

    private OpticFinder<?> entityFinder(String choiceName) {
        return DSL.namedChoice(choiceName, this.getInputSchema().getChoiceType(References.ENTITY, choiceName));
    }

    private Dynamic<?> fixPlayer(Dynamic<?> tag) {
        tag = this.fixLivingEntity(tag);
        Optional<Number> optional = tag.get("SpawnX").asNumber().result();
        Optional<Number> optional1 = tag.get("SpawnY").asNumber().result();
        Optional<Number> optional2 = tag.get("SpawnZ").asNumber().result();

        if (optional.isPresent() && optional1.isPresent() && optional2.isPresent()) {
            Dynamic<?> dynamic1 = tag.createMap(Map.of(tag.createString("pos"), ExtraDataFixUtils.createBlockPos(tag, ((Number) optional.get()).intValue(), ((Number) optional1.get()).intValue(), ((Number) optional2.get()).intValue())));

            dynamic1 = Dynamic.copyField(tag, "SpawnAngle", dynamic1, "angle");
            dynamic1 = Dynamic.copyField(tag, "SpawnDimension", dynamic1, "dimension");
            dynamic1 = Dynamic.copyField(tag, "SpawnForced", dynamic1, "forced");
            tag = tag.remove("SpawnX").remove("SpawnY").remove("SpawnZ").remove("SpawnAngle").remove("SpawnDimension").remove("SpawnForced");
            tag = tag.set("respawn", dynamic1);
        }

        Optional<? extends Dynamic<?>> optional3 = tag.get("enteredNetherPosition").result();

        if (optional3.isPresent()) {
            tag = tag.remove("enteredNetherPosition").set("entered_nether_pos", tag.createList(Stream.of(tag.createDouble(((Dynamic) optional3.get()).get("x").asDouble(0.0D)), tag.createDouble(((Dynamic) optional3.get()).get("y").asDouble(0.0D)), tag.createDouble(((Dynamic) optional3.get()).get("z").asDouble(0.0D)))));
        }

        return tag;
    }

    private Dynamic<?> fixLivingEntity(Dynamic<?> tag) {
        return ExtraDataFixUtils.fixInlineBlockPos(tag, "SleepingX", "SleepingY", "SleepingZ", "sleeping_pos");
    }

    private Dynamic<?> fixVex(Dynamic<?> tag) {
        return ExtraDataFixUtils.fixInlineBlockPos(tag.renameField("LifeTicks", "life_ticks"), "BoundX", "BoundY", "BoundZ", "bound_pos");
    }

    private Dynamic<?> fixPhantom(Dynamic<?> tag) {
        return ExtraDataFixUtils.fixInlineBlockPos(tag.renameField("Size", "size"), "AX", "AY", "AZ", "anchor_pos");
    }

    private Dynamic<?> fixTurtle(Dynamic<?> tag) {
        tag = tag.remove("TravelPosX").remove("TravelPosY").remove("TravelPosZ");
        tag = ExtraDataFixUtils.fixInlineBlockPos(tag, "HomePosX", "HomePosY", "HomePosZ", "home_pos");
        return tag.renameField("HasEgg", "has_egg");
    }

    private Dynamic<?> fixBlockAttached(Dynamic<?> tag) {
        return ExtraDataFixUtils.fixInlineBlockPos(tag, "TileX", "TileY", "TileZ", "block_pos");
    }
}
