package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import org.slf4j.Logger;

public class LevelUUIDFix extends AbstractUUIDFix {

    private static final Logger LOGGER = LogUtils.getLogger();

    public LevelUUIDFix(Schema outputSchema) {
        super(outputSchema, References.LEVEL);
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(this.typeReference);
        OpticFinder<?> opticfinder = type.findField("CustomBossEvents");
        OpticFinder<?> opticfinder1 = DSL.typeFinder(DSL.and(DSL.optional(DSL.field("Name", this.getInputSchema().getTypeRaw(References.TEXT_COMPONENT))), DSL.remainderType()));

        return this.fixTypeEverywhereTyped("LevelUUIDFix", type, (typed) -> {
            return typed.update(DSL.remainderFinder(), (dynamic) -> {
                dynamic = this.updateDragonFight(dynamic);
                dynamic = this.updateWanderingTrader(dynamic);
                return dynamic;
            }).updateTyped(opticfinder, (typed1) -> {
                return typed1.updateTyped(opticfinder1, (typed2) -> {
                    return typed2.update(DSL.remainderFinder(), this::updateCustomBossEvent);
                });
            });
        });
    }

    private Dynamic<?> updateWanderingTrader(Dynamic<?> tag) {
        return (Dynamic) replaceUUIDString(tag, "WanderingTraderId", "WanderingTraderId").orElse(tag);
    }

    private Dynamic<?> updateDragonFight(Dynamic<?> tag) {
        return tag.update("DimensionData", (dynamic1) -> {
            return dynamic1.updateMapValues((pair) -> {
                return pair.mapSecond((dynamic2) -> {
                    return dynamic2.update("DragonFight", (dynamic3) -> {
                        return (Dynamic) replaceUUIDLeastMost(dynamic3, "DragonUUID", "Dragon").orElse(dynamic3);
                    });
                });
            });
        });
    }

    private Dynamic<?> updateCustomBossEvent(Dynamic<?> tag) {
        return tag.update("Players", (dynamic1) -> {
            return tag.createList(dynamic1.asStream().map((dynamic2) -> {
                return (Dynamic) createUUIDFromML(dynamic2).orElseGet(() -> {
                    LevelUUIDFix.LOGGER.warn("CustomBossEvents contains invalid UUIDs.");
                    return dynamic2;
                });
            }));
        });
    }
}
